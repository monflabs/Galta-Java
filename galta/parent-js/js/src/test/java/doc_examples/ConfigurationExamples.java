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
import java.math.MathContext;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.tests.__BaseTestCase;

/**
 * Samples for docs/GaltaJS/UserGuide/Configuration.md
 */
public class ConfigurationExamples extends __BaseTestCase {

	public void testPrebuiltEnvironments() {
		// Plain ECMAScript: no GaltaJS extension, so a Decimal literal is a syntax error
		JSEnvironment js = JavaScriptEnvironment.create();
		assertFalse(js.supportBigDecimalLiteral());
		try {
			js.evaluateExpression("1m");
			fail();
		} catch(JSParseException e) {
			// 'm' is not a valid suffix in plain ECMAScript
		}

		// GaltaJS: extensions + StandardLibrary + JavaLibrary
		JSEnvironment galta = GaltaJSEnvironment.create();
		assertTrue(galta.supportBigDecimalLiteral());
		assertTrue(galta.supportSequenceExtensions());
		assertTrue(galta.supportJavaNative());
		assertEquals(new BigDecimal("1"), (Object)galta.evaluateExpression("1m"));
		assertEquals("function", (Object)galta.evaluateExpression("typeof Java.type"));
	}

	public void testBuilderOptions() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.strictMode(true)             // scripts run as if they started with "use strict"
				.supportGlobalAlias(true)     // adds `global` as an alias of globalThis
				.supportLongPromotion(true)   // int overflow promotes to Long instead of Double
				.build();
		assertEquals("ReferenceError", (Object)env.evaluateScript("let r; try { undeclared = 1 } catch(e) { r = e.name } r"));
		assertEquals("object", (Object)env.evaluateExpression("typeof global"));
		assertEquals(2147483648L, (Object)env.evaluateExpression("2147483647 + 1"));

		// The defaults of a bare builder
		JSEnvironment defaults = JavaScriptEnvironment.create();
		assertFalse(defaults.isStrictMode());
		assertFalse(defaults.supportGlobalAlias());
		assertEquals(2147483648.0, (Object)defaults.evaluateExpression("2147483647 + 1"));
	}

	public void testConfigureWithAConsumer() {
		JSEnvironment env = JSEnvironment.newBuilder()
				.configure(b -> {
					b.registerLibrary(new StandardLibrary());
					b.supportBigDecimalLiteral(true);
					b.supportBigDecimal(true);
				})
				.build();
		assertEquals("decimal", (Object)env.evaluateExpression("typeof 1.5m"));
	}

	public void testBuilderIsSingleUse() {
		JSEnvironment.Builder builder = JavaScriptEnvironment.newBuilder();
		builder.build();
		try {
			builder.strictMode(true);
			fail();
		} catch(IllegalStateException e) {
			assertEquals("Builder has been used and cannot be updated", e.getMessage());
		}
	}

	public void testEnableGaltaJSExtensionsRegistersNoLibrary() {
		// The flags are on, but there is no Math, JSON, console... without StandardLibrary
		JSEnvironment env = JSEnvironment.newBuilder().enableGaltaJSExtensions().build();
		assertEquals(new BigDecimal("0.25"), (Object)env.evaluateExpression("1m/4m"));
		try {
			env.evaluateExpression("Math.abs(-1)");
			fail();
		} catch(JSRuntimeException e) {
			assertTrue(e.getMessage().contains("ReferenceError: Unknown identifier Math"));
		}
	}

	public void testCustomProperties() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.putProperty("app.name", "demo")
				.putProperty("app.debug", true)
				.build();
		assertEquals("demo", env.getPropertyString("app.name"));
		assertTrue(env.getPropertyBoolean("app.debug"));
		assertNull(env.getProperty("missing"));
	}

	public void testDecimalPrecision() {
		JSEnvironment env = GaltaJSEnvironment.newBuilder()
				.mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64")
				.build();
		assertEquals(new BigDecimal("0.3333333333333333"), (Object)env.evaluateExpression("1m/3m"));
	}

	public void testScriptCache() {
		// Caches are disabled unless a size is given
		JSEnvironment noCache = JavaScriptEnvironment.create();
		JSInterpretedUnit a = noCache.createScript("1 + 1", "cached.js");
		JSInterpretedUnit b = noCache.createScript("1 + 1", "cached.js");
		assertNotSame(a.getProgram(), b.getProgram());

		JSEnvironment cached = JavaScriptEnvironment.newBuilder().scriptCacheSize(64).build();
		JSInterpretedUnit c = cached.createScript("1 + 1", "cached.js");
		JSInterpretedUnit d = cached.createScript("1 + 1", "cached.js");
		assertSame(c.getProgram(), d.getProgram());   // the parsed program is shared
		assertNotSame(c, d);                          // each call still gets its own unit
		assertEquals(2, d.execute());
	}
}
