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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.RegExpEngineJdkJavascript;
import org.monflabs.galtajs.rt.builtins.standard.regexp.joni.RegExpEngineJoni;
import org.monflabs.tests.__BaseTestCase;

/**
 * Samples for docs/GaltaJS/UserGuide/RegularExpressions.md
 */
public class RegExpExamples extends __BaseTestCase {

	public void testSelectingTheEngine() {
		JSEnvironment joni = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJoni.factory()).build();
		JSEnvironment jdk  = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJdkJavascript.factory()).build();

		String script = "const m = /(?<year>\\d{4})-(?<month>\\d{2})/.exec('Released 2026-09'); m.groups.year + '/' + m.groups.month";
		assertEquals("2026/09", (Object)joni.evaluateExpression(script));
		assertEquals("2026/09", (Object)jdk.evaluateExpression(script));

		// Unmatched alternatives are undefined (serialized as null) with both engines
		assertEquals("[\"b\",null,\"b\"]", joni.evaluateExpression("JSON.stringify(/(a)|(b)/.exec('b'))"));
		assertEquals("[\"b\",null,\"b\"]", jdk.evaluateExpression("JSON.stringify(/(a)|(b)/.exec('b'))"));
	}

	public void testUnicodeSetsFlagIsJoniOnly() {
		JSEnvironment joni = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJoni.factory()).build();
		assertEquals(true, (Object)joni.evaluateExpression("/[\\p{L}--[a-z]]/v.test('A')"));

		JSEnvironment jdk = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJdkJavascript.factory()).build();
		try {
			jdk.evaluateExpression("/[\\p{L}--[a-z]]/v.test('A')");
			fail();
		} catch(JSException e) {
			assertTrue(e.getMessage().contains("SyntaxError: Unicode Sets mode (v flag) is not yet fully implemented"));
		}
	}

	public void testPatternModifiers() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals(true, (Object)env.evaluateExpression("/a(?i:b)c/.test('aBc')"));
		assertEquals(false, (Object)env.evaluateExpression("/a(?i:b)c/.test('aBC')"));
		assertEquals(true, (Object)env.evaluateExpression("/(?-i:a)b/i.test('aB')"));
	}

	public void testPropertyNamesAreExact() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals(true, (Object)env.evaluateExpression("/\\p{Script=Greek}/u.test('\u03b1')"));
		for(String invalid : new String[] {"\\\\p{Greek}", "\\\\p{lu}", "\\\\p{ Lu }", "\\\\p{Other_Alphabetic}"}) {
			try {
				// the JS string literal '\\p{Greek}' is the pattern \p{Greek}
				env.evaluateExpression("new RegExp('" + invalid + "', 'u')");
				fail(invalid);
			} catch(JSException e) {
				assertTrue(e.getMessage().contains("SyntaxError"));
			}
		}
	}

	public void testLegacyStaticProperties() {
		JSEnvironment env = JavaScriptEnvironment.create();
		String script = "'Released 2026-09'.replace(/(\\d{4})-(\\d{2})/, '$2/$1');"
				+ "[RegExp.$1, RegExp.$2, RegExp.lastMatch, RegExp.leftContext].join('|')";
		assertEquals("2026|09|2026-09|Released ", (Object)env.evaluateExpression(script));
	}

	public void testDefaultEngine() {
		// The vendored Joni engine ships with the js module, so it is the default
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals(true, (Object)env.evaluateExpression("/^.$/u.test('\\u{1F4A9}')"));
	}
}
