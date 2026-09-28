/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.javascript.op;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;
import org.monflabs.tests.__BaseTestCase;

/**
 * The expando side tables are split by key type (see JSEnvironment's field
 * comment): boxed Strings, Numbers, Booleans and Symbols each have their own,
 * and Java objects with no storage of their own have a fifth.
 *
 * The split exists for speed - several hot guards, notably the int/int and
 * double/double inline caches in ASTArithmeticOp/ASTComparisonOp, take their
 * fast path only while the relevant table is still null - but it is only
 * sound while every write lands in the table its readers consult. Routing a
 * write to the wrong table would not throw: the value would simply stop being
 * recognised as boxed, silently skipping ToPrimitive and reporting the wrong
 * `instanceof`. These tests pin both halves: the isolation the speed depends
 * on, and the semantics that isolation must not break.
 */
public class BoxedPrimitiveMapsTest extends __BaseTestCase {

	private JSEnvironment env() {
		StaticLibrary lib = new StaticLibrary();
		lib.addStaticGlobal("hostDate", new java.util.Date());
		return JSEnvironment.newBuilder()
				.registerLibrary(new StandardLibrary())
				.registerLibrary(lib)
				.build();
	}

	private static void assertPopulated(String label, PrimitivePropertyMap m) {
		assertNotNull(label + " should be populated", m);
		assertTrue(label + " should hold an entry", m.size() > 0);
	}

	// -----------------------------------------------------------------------
	// Isolation: a write of one kind must not populate any other table
	// -----------------------------------------------------------------------

	public void testBoxingAStringTouchesOnlyTheStringTable() {
		JSEnvironment env = env();
		env.evaluateScript("Object('x');");
		assertPopulated("stringProperties", env.getStringProperties());
		assertNull("numberProperties",  env.getNumberProperties());
		assertNull("booleanProperties", env.getBooleanProperties());
		assertNull("symbolProperties",  env.getSymbolProperties());
		assertNull("objectProperties",  env.getObjectProperties());
	}

	public void testBoxingANumberTouchesOnlyTheNumberTable() {
		JSEnvironment env = env();
		env.evaluateScript("new Number(5);");
		assertPopulated("numberProperties", env.getNumberProperties());
		assertNull("stringProperties",  env.getStringProperties());
		assertNull("booleanProperties", env.getBooleanProperties());
		assertNull("symbolProperties",  env.getSymbolProperties());
		assertNull("objectProperties",  env.getObjectProperties());
	}

	public void testBoxingABooleanTouchesOnlyTheBooleanTable() {
		JSEnvironment env = env();
		env.evaluateScript("new Boolean(false);");
		assertPopulated("booleanProperties", env.getBooleanProperties());
		assertNull("numberProperties", env.getNumberProperties());
		assertNull("stringProperties", env.getStringProperties());
	}

	public void testBoxingASymbolTouchesOnlyTheSymbolTable() {
		JSEnvironment env = env();
		env.evaluateScript("Object(Symbol('s'));");
		assertPopulated("symbolProperties", env.getSymbolProperties());
		assertNull("numberProperties", env.getNumberProperties());
		assertNull("stringProperties", env.getStringProperties());
	}

	// The case the split was built for: an expando on a host Java object must
	// leave every primitive table null, so interop traffic cannot disable the
	// arithmetic and comparison inline caches.
	public void testJavaObjectExpandoTouchesOnlyTheObjectTable() {
		JSEnvironment env = env();
		env.evaluateScript("hostDate.tag = 1;");
		assertPopulated("objectProperties", env.getObjectProperties());
		assertNull("numberProperties",  env.getNumberProperties());
		assertNull("stringProperties",  env.getStringProperties());
		assertNull("booleanProperties", env.getBooleanProperties());
		assertNull("symbolProperties",  env.getSymbolProperties());
	}

	// Sloppy-mode `this` coercion boxes, and it boxes a String.
	public void testSloppyThisCoercionTouchesOnlyTheStringTable() {
		JSEnvironment env = env();
		env.evaluateScript("function f(){ return this } f.call('abc');");
		assertPopulated("stringProperties", env.getStringProperties());
		assertNull("numberProperties", env.getNumberProperties());
	}

	// Reads must never materialise a table - a materialised table is exactly
	// what the fast-path guards test for.
	public void testReadsDoNotMaterialiseAnyTable() {
		JSEnvironment env = env();
		env.evaluateScript("hostDate.getTime(); 'abc'.length; (5).toFixed(1); var q='a'; q.foo = 1;");
		assertNull("objectProperties",  env.getObjectProperties());
		assertNull("stringProperties",  env.getStringProperties());
		assertNull("numberProperties",  env.getNumberProperties());
		assertNull("booleanProperties", env.getBooleanProperties());
		assertNull("symbolProperties",  env.getSymbolProperties());
	}

	// -----------------------------------------------------------------------
	// Semantics: a boxed primitive must still be recognised as an object by
	// the readers that consult its own table
	// -----------------------------------------------------------------------

	public void testBoxedValuesKeepObjectSemantics() {
		JSEnvironment env = env();
		// typeof / strict equality: a wrapper is an object, never === its value
		assertEquals("object",  (Object)env.evaluateScript("typeof Object('x')"));
		assertEquals("object",  (Object)env.evaluateScript("typeof new Number(5)"));
		assertEquals("object",  (Object)env.evaluateScript("typeof new Boolean(false)"));
		assertEquals(false,     (Object)env.evaluateScript("new Number(5) === 5"));
		assertEquals(false,     (Object)env.evaluateScript("Object('x') === 'x'"));
		// ToPrimitive is still consulted, in both directions
		assertEquals(true,      (Object)env.evaluateScript("new Number(5) == 5"));
		assertEquals(10,        (Object)env.evaluateScript("new Number(5) + 5"));
		assertEquals("xy",      (Object)env.evaluateScript("Object('x') + 'y'"));
		// a boxed wrapper carries properties; the raw primitive does not
		assertEquals(7,         (Object)env.evaluateScript("var n = new Number(5); n.extra = 7; n.extra"));
		// an overridden valueOf on a wrapper must win over the boxed value
		assertEquals(99,        (Object)env.evaluateScript(
				"var b = new Number(3); b.valueOf = function(){ return 99 }; b + 0"));
		// a boxed Boolean is truthy even when it wraps false
		assertEquals(true,      (Object)env.evaluateScript("new Boolean(false) ? true : false"));
	}

	// A ConsString (the concatenation rope) can never be boxed, so it must be
	// reported as a primitive without consulting the string table at all.
	public void testConcatenationResultIsAlwaysPrimitive() {
		JSEnvironment env = env();
		assertEquals(true, (Object)env.evaluateScript(
				"Object('x'); var a = 'ab', b = 'cd'; var c = a + b; typeof c === 'string' && c === 'abcd'"));
	}

	// Boxing one kind must not change how another kind compares or computes -
	// this is the isolation above, observed from script.
	public void testBoxingOneKindDoesNotAffectAnother() {
		JSEnvironment env = env();
		assertEquals(true, (Object)env.evaluateScript(
				"Object('x'); hostDate.tag = 1; (2 + 3 === 5) && (2.5 + 2.5 === 5.0) && (1 < 2)"));
	}
}
