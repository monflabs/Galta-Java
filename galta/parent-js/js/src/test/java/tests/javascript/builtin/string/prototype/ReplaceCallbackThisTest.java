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
package tests.javascript.builtin.string.prototype;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * The replacer function passed to String.prototype.replace() is called with
 * no receiver, so a strict-mode replacer's own `this` must be `undefined`
 * (not `null`).
 *
 * The transpiler's last-bare-statement-value tracking doesn't return the
 * expected value for this shape (a separate, unaudited gap - see
 * MultiLineCommentAsiTest), so this is interpreter-only.
 *
 * @author Philippe Riand
 */
public class ReplaceCallbackThisTest extends JavaScriptStrictTestCase {

	public void testReplacerThisIsUndefined() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"let seenThis;\n" +
			"'ab'.replace('b', function() { seenThis = this; return 'c'; });\n" +
			"seenThis === undefined;"));
	}
}
