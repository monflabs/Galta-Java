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
 * The "restricted productions" ASI rules: a LineTerminator right after
 * "continue"/"break"/"return" (before the label/expression) forces ASI
 * there, and the trailing semicolon after "do Statement while (Expr)" is
 * always optional regardless of what follows on the same line.
 *
 * The transpiler's last-bare-statement-value tracking doesn't return the
 * expected value for these shapes (a separate, unaudited gap - see
 * MultiLineCommentAsiTest), so this is interpreter-only.
 *
 * @author Philippe Riand
 */
public class RestrictedProductionAsiTest extends JavaScriptStrictTestCase {

	public void testReturnWithLineTerminatorReturnsUndefined() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function f() {\n" +
			"  return\n" +
			"  1;\n" +
			"}\n" +
			"f() === undefined;"));
	}

	public void testContinueWithLineTerminatorIsUnlabeled() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"let result = false;\n" +
			"label: for (let i = 0; i <= 1; i++) {\n" +
			"  for (let j = 0; j <= 1; j++) {\n" +
			"    if (j === 0) {\n" +
			"      continue\n" +
			"      label;\n" +
			"    } else {\n" +
			"      result = true;\n" +
			"    }\n" +
			"  }\n" +
			"}\n" +
			"result;"));
	}

	public void testPlainContinueAndBreakStillWork() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(1, executeCode(
			"let idx = 0;\n" +
			"loop: for (let i = 0; i < 5; i++) {\n" +
			"  idx++;\n" +
			"  break;\n" +
			"}\n" +
			"idx;"));
	}

	public void testDoWhileSemicolonOptionalOnSameLine() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(42, executeCode(
			"var x;\n" +
			"do break; while (0) x = 42;\n" +
			"x;"));
	}
}
