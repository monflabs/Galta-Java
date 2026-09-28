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

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * Identifier characters cover the full Unicode ID_Start/ID_Continue ranges
 * (generated from Character.isUnicodeIdentifierStart/Part), not just the
 * older, much smaller hand-coded snapshot this used to be limited to -
 * including letters/ideographs added in modern Unicode revisions and
 * astral-plane (surrogate-pair) characters.
 *
 * The transpiler's last-bare-statement-value tracking doesn't return the
 * expected value for these shapes (a separate, unaudited gap - see
 * MultiLineCommentAsiTest), so this is interpreter-only.
 *
 * @author Philippe Riand
 */
public class UnicodeIdentifierTest extends JavaScriptNoStrictTestCase {

	public void testModernBmpLetterAsIdentifier() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// U+1885 (MONGOLIAN LETTER ALI GALI BALUDA), Other_ID_Start, Unicode 9.0
		assertEquals(1, executeCode("var ᢅ = 1; ᢅ;"));
	}

	public void testCjkIdeographAsIdentifier() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// U+3400 (CJK UNIFIED IDEOGRAPH EXTENSION A start)
		assertEquals(1, executeCode("var 㐀 = 1; 㐀;"));
	}

	public void testAstralCharacterAsIdentifier() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// U+10480 (OSMANYA LETTER ALEF), a supplementary-plane letter
		String c = new String(Character.toChars(0x10480));
		assertEquals(1, executeCode("var " + c + " = 1; " + c + ";"));
	}
}
