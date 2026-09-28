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
package doc_examples;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extensions/JavaTypes.md
 */
public class JavaTypesExamples extends __BaseTestCase {

	public void testDatesAreJavaDates() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object d = env.evaluateExpression("new Date(0)");
		assertTrue(d instanceof Date);
		assertEquals(0L, ((Date)d).getTime());

		// and a java.util.Date coming from Java is a JavaScript Date
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		ctx.getGlobalThis().setOwnProperty("when", new Date(86_400_000L));
		assertEquals("1970-01-02T00:00:00.000Z", env.createScript("when.toISOString()", "d.js").executeWithContext(ctx));
		assertEquals(true, env.createScript("when instanceof Date", "d.js").executeWithContext(ctx));
		// Year zero is handled the ECMAScript way
		assertEquals("0000-01-01T00:00:00.000Z", (Object)env.evaluateExpression("new Date(-62167219200000).toISOString()"));
	}

	public void testIntegersLongsAndBigIntegers() {
		JSEnvironment env = GaltaJSEnvironment.create();
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("big", 1L << 40);
		globals.addStaticGlobal("huge", new BigInteger("123456789012345678901234567890"));
		JSEnvironment env2 = GaltaJSEnvironment.newBuilder().registerLibrary(globals).build();

		assertEquals(Long.class, (Object)env2.evaluateExpression("big + 1").getClass());
		assertEquals("number", (Object)env2.evaluateExpression("typeof big"));
		assertEquals("bigint", (Object)env2.evaluateExpression("typeof huge"));           // BigInteger is BigInt
		assertEquals(new BigInteger("123456789012345678901234567891"), (Object)env2.evaluateExpression("huge + 1n"));
		assertEquals(Integer.class, (Object)env.evaluateExpression("1 + 1").getClass());
	}

	public void testBigDecimalFromJava() {
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("price", new BigDecimal("19.99"));

		JSEnvironment galta = GaltaJSEnvironment.newBuilder().registerLibrary(globals).build();
		assertEquals("decimal", (Object)galta.evaluateExpression("typeof price"));
		assertEquals(new BigDecimal("39.98"), (Object)galta.evaluateExpression("price * 2"));

		// Without supportMixedBigNumber, mixing a Decimal with a number is a TypeError (like BigInt)
		JSEnvironment js = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();
		assertEquals("decimal", (Object)js.evaluateExpression("typeof price"));
		assertEquals("TypeError", (Object)js.evaluateScript("let r; try { price * 2 } catch(e) { r = e.name } r"));
	}

	public void testJavaCollectionsBehaveLikeBuiltIns() {
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("names", List.of("ada", "linus"));
		globals.addStaticGlobal("scores", Map.of("ada", 10));
		globals.addStaticGlobal("tags", Set.of("x"));
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();

		assertEquals(2L, (Object)env.evaluateExpression("names.length"));
		assertEquals("ADA,LINUS", env.evaluateExpression("names.map(n => n.toUpperCase()).join(',')"));
		assertEquals(false, (Object)env.evaluateExpression("Array.isArray(names)"));   // it is a List, not a JS Array
		assertEquals(10, (Object)env.evaluateExpression("scores.get('ada')"));
		assertEquals(true, (Object)env.evaluateExpression("scores instanceof Map && scores.has('ada')"));
		assertEquals(true, (Object)env.evaluateExpression("tags instanceof Set && tags.has('x')"));
	}

	public void testJavaScriptMapsAndSetsAreJavaCollections() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object map = env.evaluateExpression("new Map([['k', 1]])");
		assertTrue(map instanceof Map);
		assertEquals(1, ((Map<?,?>)map).get("k"));
		Object set = env.evaluateExpression("new Set([1, 2])");
		assertTrue(set instanceof Set);
		assertEquals(2, ((Set<?>)set).size());
		assertEquals(List.of(1, 2), list(env.evaluateExpression("[...new Set([1, 2, 2])]")));
	}
}
