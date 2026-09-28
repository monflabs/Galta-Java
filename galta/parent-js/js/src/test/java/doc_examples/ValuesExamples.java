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
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSValue;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.json.JsonObject;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/UserGuide/Values.md
 */
public class ValuesExamples extends __BaseTestCase {

	public void testNullAndUndefined() {
		JSEnvironment env = JavaScriptEnvironment.create();
		// JavaScript null is Java null; undefined is a dedicated sentinel
		assertNull(env.evaluateExpression("null"));
		assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("undefined"));
		assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("({}).missing"));
		assertTrue(RuntimeUtil.isNullOrUndefined(env.evaluateExpression("void 0")));
	}

	public void testConversions() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals("1,2", RuntimeUtil.toString(env, List.of(1, 2)));
		assertEquals(12, RuntimeUtil.toNumber(env, "12"));
		assertEquals(false, RuntimeUtil.toBoolean(env, ""));
		assertEquals(true, RuntimeUtil.toBoolean(env, "0"));
		assertEquals("number", RuntimeUtil.typeof(env, 1.5));
		assertEquals("string", RuntimeUtil.typeof(env, "x"));
		assertEquals("undefined", RuntimeUtil.typeof(env, RuntimeUtil.UNDEFINED));
	}

	public void testNavigatingValuesWithJSValue() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		Object data = env.createScript("({user: {name: 'Ada', langs: ['en', 'fr']}, active: true})", "data.js").executeWithContext(ctx);

		JSValue v = ctx.value(data);
		assertTrue(v.isObject());
		assertEquals("Ada", v.get("user").get("name").stringValue());
		assertEquals("fr", v.get("user", "langs", 1).stringValue());   // path form
		assertTrue(v.get("active").booleanValue());
		assertTrue(v.get("nothing").isUndefined());
		assertEquals("dflt", v.getOrDefault("nothing", "dflt").stringValue());
		assertEquals(2, v.get("user").get("langs").get("length").intValue());
	}

	public void testCreatingObjectsAndArraysFromJava() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());

		JSObject obj = JSObject.of(env, "name", "galta", "tags", JSArray.of(env, "a", "b"));
		obj.setProperty("count", 2);
		ctx.getGlobalThis().setOwnProperty("obj", obj);

		assertEquals("galta:a+b:2", env.createScript("obj.name + ':' + obj.tags.join('+') + ':' + obj.count", "o.js").executeWithContext(ctx));

		// The JSValue wrapper offers a fluent alternative
		JSValue fluent = ctx.createObject();
		fluent.put("x", 1);
		fluent.put("list", ctx.createArray().value());
		assertEquals(1, fluent.get("x").intValue());
		assertTrue(fluent.get("list").isArray());
	}

	public void testCallingJavaScriptFunctionsFromJava() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		env.createScript("""
			function add(a, b) { return a + b }
			const counter = { n: 10, bump(by = 1) { this.n += by; return this.n } }
			""", "fn.js").executeWithContext(ctx);

		// Through JSValue
		assertEquals(7, ctx.global("add").call(3, 4).intValue());
		JSValue counter = ctx.global("counter");
		assertEquals(15, counter.get("bump").callThis(counter.value(), 5).intValue());
		assertEquals(16, counter.get("bump").callThis(counter.value()).intValue());

		// Through the low-level Callable interface
		// (a runtime context must be current on the thread, hence ctx.with())
		Callable add = (Callable)ctx.global("add").value();
		assertEquals(9, ctx.with(() -> add.call(null, new Object[] {4, 5})));

		// Varargs
		assertEquals(3, ctx.value(env.evaluateExpression("(...a) => a.length")).call(new Object[] {1, 2, 3}).intValue());
	}

	public void testExposingJavaFunctionsToJavaScript() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.registerLibrary(new GlobalLibrary() {
					@Override
					public void configureStandardObjects(JSEnvironment env, StandardObjects globals) {
						globals.setOwnProperty("shout", new BaseMethod(env, "shout", 1) {
							@Override
							public Object call(Object thisValue, Object[] args) {
								return RuntimeUtil.toString(env, args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED).toUpperCase() + "!";
							}
						});
					}
				})
				.build();
		assertEquals("HELLO!", (Object)env.evaluateExpression("shout('hello')"));
		assertEquals("UNDEFINED!", (Object)env.evaluateExpression("shout()"));
		assertEquals(1, (Object)env.evaluateExpression("shout.length"));
		assertEquals("shout", (Object)env.evaluateExpression("shout.name"));
	}

	public void testJsonIntegration() {
		JSEnvironment env = JavaScriptEnvironment.create();

		// A JavaScript object IS a JsonObject from the Galta JSON library
		JsonObject obj = (JsonObject)env.evaluateExpression("({a: 1, b: [true, null]})");
		assertEquals("{\"a\":1,\"b\":[true,null]}", obj.stringify(true));
		assertEquals(1, obj.get("a"));

		// Parsing JSON from Java with the environment's factory produces JavaScript objects
		Object parsed = env.getJsonFactory().parse("{\"x\": [1, 2, 3]}");
		assertTrue(parsed instanceof JSObject);
		assertEquals(List.of(1, 2, 3), list(((Map<?,?>)parsed).get("x")));

		// ...and JSON.stringify sees Java-built objects
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		ctx.getGlobalThis().setOwnProperty("cfg", JSObject.of(env, "port", 8080));
		assertEquals("{\"port\":8080}", env.createScript("JSON.stringify(cfg)", "j.js").executeWithContext(ctx));
	}
}
