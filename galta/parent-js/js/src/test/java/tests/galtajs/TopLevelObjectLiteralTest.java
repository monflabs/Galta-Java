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
package tests.galtajs;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.tests.__BaseTestCase;

/**
 * JSConfiguration.supportTopLevelObjectLiteral(): a program whose entire
 * text is an object literal evaluates to that object - a GaltaJS extension,
 * off by default and enabled by enableGaltaJSExtensions(). Without it the
 * same text is the block / labeled statement ECMA-262 specifies.
 */
public class TopLevelObjectLiteralTest extends __BaseTestCase {

	public void testEnabledByGaltaJSExtensions() {
		JSEnvironment env = GaltaJSEnvironment.create();
		assertTrue(env.supportTopLevelObjectLiteral());
		Object o = env.evaluateScript("{a: 1}");
		assertTrue(o instanceof JSObject);
		assertEquals(1, ((JSObject)o).getProperty("a"));
		o = env.evaluateScript("{\"s\": \"x\"}");
		assertEquals("x", ((JSObject)o).getProperty("s"));
		assertTrue(env.evaluateScript("{}") instanceof JSObject);
		// Only the ENTIRE program qualifies - a leading block is still a block
		assertEquals(2, (Object)env.evaluateScript("{a: 1} 2"));
	}

	public void testOffByDefault() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertFalse(env.supportTopLevelObjectLiteral());
		// A labeled statement whose body is the expression 1
		assertEquals(1, (Object)env.evaluateScript("{a: 1}"));
		// An empty block
		assertFalse(env.evaluateScript("{}") instanceof JSObject);
	}

	public void testExplicitOptIn() {
		JSEnvironment env = JSEnvironment.newBuilder().supportTopLevelObjectLiteral(true).build();
		Object o = env.evaluateScript("{a: 1}");
		assertTrue(o instanceof JSObject);
		assertEquals(1, ((JSObject)o).getProperty("a"));
	}
}
