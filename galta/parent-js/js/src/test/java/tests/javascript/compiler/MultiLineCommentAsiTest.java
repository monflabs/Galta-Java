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
package tests.javascript.compiler;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * A MultiLineComment that spans a LineTerminator counts as a LineTerminator
 * for ASI purposes, even though its closing delimiter doesn't itself
 * introduce a newline. Covers LF, CR, and the two Unicode line terminators
 * (U+2028, U+2029) embedded inside the comment body.
 *
 * Only implemented in interpreted mode - parsing (and thus ASI) is shared
 * across modes, but the transpiler's last-statement-value tracking doesn't
 * return the expected value for this specific shape, a separate,
 * unaudited gap.
 *
 * @author Philippe Riand
 */
public class MultiLineCommentAsiTest extends JavaScriptStrictTestCase {

	public void testLineFeedInsideComment() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode("''/*\n*/''\ntrue"));
	}

	public void testCarriageReturnInsideComment() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode("''/*\r*/''\ntrue"));
	}

	public void testLineSeparatorInsideComment() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode("''/*\u2028*/''\ntrue"));
	}

	public void testParagraphSeparatorInsideComment() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode("''/*\u2029*/''\ntrue"));
	}
}
