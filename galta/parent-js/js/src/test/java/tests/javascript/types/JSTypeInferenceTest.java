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
package tests.javascript.types;

import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.ASTThis;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.ternaryop.ASTTernaryTest;
import org.monflabs.galtajs.node.unaryop.ASTVoid;
import org.monflabs.galtajs.types.JSType;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Exercises the null/undefined and object/constructor extensions to JSType,
 * and the handful of AST nodes wired to use them (ASTLiteral, ASTVoid,
 * ASTTernaryTest's join, ASTThis) - and confirms ASTIdentifier deliberately
 * stays UNKNOWN, including for the bare "undefined" name.
 */
public class JSTypeInferenceTest extends JavaScriptStrictTestCase {

	private ASTProgram parse(String script) throws Exception {
		return getEnvironment().createScript(script, "JSTypeInferenceTest").getProgram();
	}

	@SuppressWarnings("unchecked")
	private <T extends ASTNode> T findFirst(ASTNode node, Class<T> type) {
		if(type.isInstance(node)) {
			return (T)node;
		}
		for(int i=0; i<node.getChildCount(); i++) {
			ASTNode child = node.getChild(i);
			if(child!=null) {
				T found = findFirst(child, type);
				if(found!=null) {
					return found;
				}
			}
		}
		return null;
	}

	public void testNullLiteral() throws Exception {
		ASTLiteral lit = findFirst(parse("null;"), ASTLiteral.class);
		assertNotNull(lit);
		assertSame(JSType.NULL, lit.getReturnedType());
	}

	public void testVoidIsAlwaysUndefined() throws Exception {
		ASTVoid v = findFirst(parse("void f();"), ASTVoid.class);
		assertNotNull(v);
		assertSame(JSType.UNDEFINED, v.getReturnedType());
	}

	// Deliberately NOT typed as JSType.UNDEFINED - `undefined` is not a
	// reserved word and could be shadowed by a local declaration
	// (getReturnedType() has no context to check for that, unlike the
	// transpiler's own codegen special-case for the same name).
	public void testBareUndefinedIdentifierStaysUnknown() throws Exception {
		ASTIdentifier id = findFirst(parse("undefined;"), ASTIdentifier.class);
		assertNotNull(id);
		assertSame(JSType.UNKNOWN, id.getReturnedType());
	}

	public void testOtherIdentifierStaysUnknown() throws Exception {
		ASTIdentifier id = findFirst(parse("x;"), ASTIdentifier.class);
		assertNotNull(id);
		assertSame(JSType.UNKNOWN, id.getReturnedType());
	}

	public void testTernaryJoinsStringWithUndefined() throws Exception {
		ASTTernaryTest t = findFirst(parse("c ? 'a' : void 0;"), ASTTernaryTest.class);
		assertNotNull(t);
		JSType type = t.getReturnedType();
		assertSame(JSType.STRING, type.baseType());
		assertTrue(type.canBeUndefined());
		assertFalse(type.canBeNull());
	}

	public void testTernaryJoinsStringWithNull() throws Exception {
		ASTTernaryTest t = findFirst(parse("c ? 'a' : null;"), ASTTernaryTest.class);
		assertNotNull(t);
		JSType type = t.getReturnedType();
		assertSame(JSType.STRING, type.baseType());
		assertTrue(type.canBeNull());
		assertFalse(type.canBeUndefined());
	}

	public void testTernaryIncompatibleConcreteBasesStaysUnknown() throws Exception {
		ASTTernaryTest t = findFirst(parse("c ? 'a' : 1;"), ASTTernaryTest.class);
		assertNotNull(t);
		assertSame(JSType.UNKNOWN, t.getReturnedType());
	}

	public void testThisInOrdinaryMethodIsObjectOfClass() throws Exception {
		ASTThis th = findFirst(parse("class Foo { bar() { return this; } }"), ASTThis.class);
		assertNotNull(th);
		JSType type = th.getReturnedType();
		assertTrue(type.isObject());
	}

	public void testThisInNonDerivedConstructorIsObjectOfClass() throws Exception {
		ASTThis th = findFirst(parse("class Foo { constructor() { return this; } }"), ASTThis.class);
		assertNotNull(th);
		assertTrue(th.getReturnedType().isObject());
	}

	public void testThisInDerivedConstructorStaysUnknown() throws Exception {
		ASTThis th = findFirst(parse("class Base {} class Foo extends Base { constructor() { super(); return this; } }"), ASTThis.class);
		assertNotNull(th);
		assertSame(JSType.UNKNOWN, th.getReturnedType());
	}

	public void testThisInStaticMethodStaysUnknown() throws Exception {
		ASTThis th = findFirst(parse("class Foo { static bar() { return this; } }"), ASTThis.class);
		assertNotNull(th);
		assertSame(JSType.UNKNOWN, th.getReturnedType());
	}

	public void testThisInPlainFunctionStaysUnknown() throws Exception {
		ASTThis th = findFirst(parse("function f() { return this; }"), ASTThis.class);
		assertNotNull(th);
		assertSame(JSType.UNKNOWN, th.getReturnedType());
	}
}
