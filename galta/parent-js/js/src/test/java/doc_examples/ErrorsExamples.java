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
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSValue;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.errors.TypeError;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/UserGuide/Errors.md
 */
public class ErrorsExamples extends __BaseTestCase {

	public void testParseErrors() {
		JSEnvironment env = JavaScriptEnvironment.create();
		try {
			env.createScript("let x = ;", "bad.js");
			fail();
		} catch(JSParseException e) {
			// The message embeds the offending source line
			assertTrue(e.getMessage().contains("Encountered ;"));
			assertTrue(e.getMessage().contains("let x = ;"));
		}
	}

	public void testRuntimeErrorsCarryTheJavaScriptValue() {
		JSEnvironment env = JavaScriptEnvironment.create();
		try {
			env.evaluateScript("function f() { throw new TypeError('bad ' + 1) }\nf()");
			fail();
		} catch(JSRuntimeException e) {
			Object jsError = e.getJavascriptException();
			assertTrue(jsError instanceof TypeError);
			assertEquals("TypeError", JSValue.of(env, jsError).get("name").stringValue());
			assertEquals("bad 1", JSValue.of(env, jsError).get("message").stringValue());
			assertTrue(e.getMessage().startsWith("\nTypeError: bad 1"));
			assertTrue(e.getMessage().contains("At script line 2"));   // stack trace, innermost last
		}
	}

	public void testThrowingNonErrorValues() {
		JSEnvironment env = JavaScriptEnvironment.create();
		try {
			env.evaluateScript("throw { code: 7 }");
			fail();
		} catch(JSRuntimeException e) {
			assertEquals(7, JSValue.of(env, e.getJavascriptException()).get("code").intValue());
		}
	}

	public void testJavaExceptionsSurfaceInJavaScript() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.registerLibrary(new GlobalLibrary() {
					@Override
					public void configureStandardObjects(JSEnvironment env, StandardObjects globals) {
						globals.setOwnProperty("boom", new BaseMethod(env, "boom", 0) {
							@Override public Object call(Object t, Object[] a) { throw new IllegalStateException("java failure"); }
						});
						globals.setOwnProperty("needsNumber", new BaseMethod(env, "needsNumber", 1) {
							@Override public Object call(Object t, Object[] a) {
								if(a.length == 0 || !(a[0] instanceof Number)) {
									throw RuntimeUtil.typeError("needsNumber() expects a number, got {0}", a.length == 0 ? "nothing" : a[0]);
								}
								return ((Number)a[0]).intValue() * 2;
							}
						});
					}
				})
				.build();

		// An arbitrary Java exception becomes a JavaScript Error
		Object r = env.evaluateScript("let r; try { boom() } catch(e) { r = [e instanceof Error, e.name, e.message] } r");
		assertEquals(List.of(true, "Error", "Java Exception: java failure"), list(r));

		// RuntimeUtil.typeError() (and rangeError, syntaxError, error) produce the matching JavaScript error type
		r = env.evaluateScript("let r; try { needsNumber('x') } catch(e) { r = [e instanceof TypeError, e.message] } r");
		assertEquals(List.of(true, "needsNumber() expects a number, got x"), list(r));
		assertEquals(8, (Object)env.evaluateScript("needsNumber(4)"));

		// Uncaught, the original Java exception is the cause
		try {
			env.evaluateScript("boom()");
			fail();
		} catch(JSRuntimeException e) {
			assertTrue(e.getCause() instanceof IllegalStateException);
		}
	}

	public void testJavaExceptionObjectIsAvailableInJavaScript() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const Integer = Java.type('java.lang.Integer');
			let r;
			try { Integer.parseInt('abc') } catch(e) { r = e.__java_exception__.getCause().getClass().getSimpleName() }
			r
			""");
		assertEquals("NumberFormatException", r);
	}
}
