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

import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extensions/Syntax.md
 */
public class SyntaxExtensionsExamples extends __BaseTestCase {

	private final JSEnvironment env = GaltaJSEnvironment.create();

	public void testElvisOperator() {
		// a ?: b  is  a ? a : b
		assertEquals("dflt", (Object)env.evaluateExpression("null ?: 'dflt'"));
		assertEquals("dflt", (Object)env.evaluateExpression("'' ?: 'dflt'"));
		assertEquals("value", (Object)env.evaluateExpression("'value' ?: 'dflt'"));
	}

	public void testPipelineOperator() {
		assertEquals(List.of(1, 2, 3), list(env.evaluateExpression("[3,1,2] |> (a => a.sort())")));
		assertEquals("HELLO!", (Object)env.evaluateExpression("'hello' |> (s => s.toUpperCase()) |> (s => s + '!')"));
	}

	public void testNumericAndKeywordMemberNames() {
		assertEquals("zero", (Object)env.evaluateExpression("({0: 'zero'}).0"));
		assertEquals("b", (Object)env.evaluateExpression("['a','b'].1"));
		assertEquals(1, (Object)env.evaluateExpression("({for: 1, class: 2}).for"));
	}

	public void testReturnOutsideAFunction() {
		assertEquals(5, (Object)env.evaluateScript("if (true) { return 5 } 6"));
		try {
			JavaScriptEnvironment.create().evaluateScript("return 5");
			fail();
		} catch(JSException e) {
			// standard JavaScript: "return" is only valid inside a function
		}
	}

	public void testSynchronizedStatement() {
		// Java-style synchronized block on any object
		Object r = env.evaluateScript("""
			const lock = {};
			let v = 0;
			synchronized(lock) { v = 1 }
			v
			""");
		assertEquals(1, r);
	}

	public void testGlobalAlias() {
		JSEnvironment aliased = JavaScriptEnvironment.newBuilder().supportGlobalAlias(true).build();
		assertEquals(true, (Object)aliased.evaluateExpression("global === globalThis"));
		assertEquals("undefined", (Object)JavaScriptEnvironment.create().evaluateExpression("typeof global"));
	}

	public void testCurrentItemReference() {
		// @ is the current item in filters and maps; it requires supportIdentifierAtSign
		assertEquals(List.of(2, 3), list(env.evaluateExpression("[1,2,3][?(@ > 1)]")));
		assertEquals(List.of(10, 20), list(env.evaluateExpression("[1,2][*].(@ * 10)")));
	}

	public void testTopLevelObjectLiteral() {
		org.monflabs.galtajs.jsonfactory.JSObject o = (org.monflabs.galtajs.jsonfactory.JSObject)env.evaluateScript("{a: 1}");
		assertEquals(1, o.getProperty("a"));
		assertEquals(1, (Object)JavaScriptEnvironment.create().evaluateScript("{a: 1}"));   // standard: label `a`, expression 1
	}

	public void testStrictModeAndMustDeclare() {
		// GaltaJSEnvironment runs strict: assigning an undeclared variable is an error
		assertEquals("ReferenceError", (Object)env.evaluateScript("let r; try { undeclared = 1 } catch(e) { r = e.name } r"));
		assertEquals(1, (Object)JavaScriptEnvironment.create().evaluateScript("sloppy = 1; sloppy"));
	}
}
