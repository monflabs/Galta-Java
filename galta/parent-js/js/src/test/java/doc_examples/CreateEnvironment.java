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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.tests.__BaseTestCase;

/**
 * Samples for docs/GaltaJS/UserGuide/GettingStarted.md - creating an
 * environment and evaluating the first expressions.
 */
public class CreateEnvironment extends __BaseTestCase {

	//
	// Expressions
	//
	public void testJavaScriptExpression() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object o = env.evaluateExpression("1+Math.abs(-2)");
		// The result is a plain java.lang.Integer - no wrapper type
		assertEquals(3, o);
		assertEquals(Integer.class, o.getClass());
	}

	public void testGaltaJSExpression() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object o = env.evaluateExpression("1m / 3m");
		// 'm' literals are java.math.BigDecimal values, computed with MathContext.DECIMAL128 by default
		assertEquals(BigDecimal.class, o.getClass());
		assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), o);
	}

	public void testGaltaJSWithBuilder() {
		// enableGaltaJSExtensions() only flips configuration flags: the
		// standard objects (Math, JSON, console...) come from StandardLibrary
		JSEnvironment env = JSEnvironment.newBuilder()
				.enableGaltaJSExtensions()
				.registerLibrary(new StandardLibrary())
				.build();
		Object o = env.evaluateExpression("1m / 4m + Math.abs(-1)");
		assertEquals(new BigDecimal("1.25"), o);
	}

	//
	// Script
	//
	public void testJavaScriptScript() {
		JSEnvironment env = JavaScriptEnvironment.create();
		// A script runs with a full event loop, so it can use promises and await
		Object o = env.evaluateScript("const v = await Promise.resolve(1 + Math.abs(-2)); v * 10");
		assertEquals(30, o);
	}
}
