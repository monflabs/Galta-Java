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
package tests.javascript.regression;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.tests.__BaseTestCase;

/**
 * Parser regressions checked through the Java API: the exact source text
 * is only kept by the original parse (not by the decompiled test mode).
 */
public class ParserRegressionJavaTest extends __BaseTestCase {

	// Function.prototype.toString() ends where the function ends, whatever
	// a regular expression literal in it contains
	public void testFunctionToStringWithRegExp() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals("x => /\\)/.test(x)", env.evaluateScript("(x => /\\)/.test(x)).toString()"));
		assertEquals("function g() { return /}/.source }", env.evaluateScript("function g() { return /}/.source }; g.toString()"));
		assertEquals("m(a = /\\(/) { return 1 }", env.evaluateScript("({ m(a = /\\(/) { return 1 } }).m.toString()"));
		assertEquals("async (a) => a / 2", env.evaluateScript("(async (a) => a / 2).toString()"));
		assertEquals("a => a / b", env.evaluateScript("(a => a / b).toString()"));
		assertEquals("(a) => { return a }", env.evaluateScript("((a) => { return a }).toString()"));
	}
}
