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
import java.util.Date;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.json.JsonObject;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/UserGuide/GettingStarted.md
 */
public class GettingStartedExamples extends __BaseTestCase {

	public void testOverviewSnippet() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object value = env.evaluateScript("""
			const prices = [{ item: 'pen', price: 1.5m }, { item: 'book', price: 12m }];
			prices.*.price[].reduce((a, b) => a + b, 0m)
			""");
		assertEquals(new BigDecimal("13.5"), value);
	}

	public void testFirstProgram() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Captured run = captureOutput(env, """
			const names = ['Ada', 'Linus'];
			for (const n of names) {
				console.log(`Hello ${n}`);
			}
			names.length
			""");
		assertEquals(2L, run.value());   // array lengths are java.lang.Long values
		assertEquals(lines("Hello Ada", "Hello Linus"), run.output());
	}

	public void testExpressionVersusScript() {
		JSEnvironment env = JavaScriptEnvironment.create();

		// evaluateExpression: synchronous, no event loop
		assertEquals(6, (Object)env.evaluateExpression("[1,2,3].reduce((a,b) => a+b, 0)"));

		// evaluateScript: runs with an event loop, so promises settle before it returns
		assertEquals(6, (Object)env.evaluateScript("await Promise.resolve([1,2,3]).then(a => a.reduce((x,y) => x+y, 0))"));

		// Anything asynchronous inside evaluateExpression is an error
		try {
			env.evaluateExpression("Promise.resolve(1).then(x => x)");
			fail("expected an error");
		} catch(JSRuntimeException e) {
			assertTrue(e.getMessage().contains("This executor does not support micro-tasks"));
		}
	}

	public void testValuesAreJavaObjects() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals(Integer.class, (Object)env.evaluateExpression("40 + 2").getClass());
		assertEquals(Double.class,  env.evaluateExpression("0.5 * 3").getClass());
		assertEquals(String.class,  env.evaluateExpression("'a' + 'b'").getClass());
		assertEquals(Boolean.class, (Object)env.evaluateExpression("1 < 2").getClass());
		assertTrue(env.evaluateExpression("new Date(0)") instanceof Date);

		// Objects are JSObject instances, which also implement the JSON library's JsonObject
		Object obj = env.evaluateExpression("({name: 'galta', tags: ['a', 'b']})");
		assertTrue(obj instanceof JSObject);
		assertTrue(obj instanceof JsonObject);
		assertEquals("galta", ((JsonObject)obj).get("name"));

		// Arrays are JSArray instances, which also implement java.util.List
		Object arr = env.evaluateExpression("[1, 'two', true]");
		assertTrue(arr instanceof JSArray);
		assertTrue(arr instanceof List);
		assertEquals(List.of(1, "two", true), list(arr));
	}
}
