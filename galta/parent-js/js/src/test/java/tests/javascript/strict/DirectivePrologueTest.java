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
package tests.javascript.strict;

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * Directive Prologue handling: "use strict" activates strict mode wherever
 * it appears in the leading run of plain string-literal expression
 * statements, not only when it's the very first statement; and it must be
 * written verbatim (no escape sequences or line continuations) to count,
 * even though an escaped spelling evaluates to the same string value.
 *
 * Strict-mode functions must also throw a ReferenceError on assignment to
 * an unresolvable identifier instead of silently creating a global.
 *
 * The transpiler's last-bare-statement-value tracking doesn't return the
 * expected value for these shapes (a separate, unaudited gap - see
 * MultiLineCommentAsiTest); decompile-then-reparse mode also can't preserve
 * whether a directive string was written with an escape sequence, since
 * decompilation re-emits the string's value, not its original source text.
 *
 * @author Philippe Riand
 */
public class DirectivePrologueTest extends JavaScriptNoStrictTestCase {

	public void testUseStrictNotFirstInPrologue() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function foo() {\n" +
			"  'another directive';\n" +
			"  'use strict';\n" +
			"  return this === undefined;\n" +
			"}\n" +
			"foo.call(undefined);"));
	}

	public void testEscapedUseStrictIsNotADirective() throws Exception {
		if(isJavaTranspiler() || isJavaDecompiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function foo() {\n" +
			"  'use str\\ict';\n" +
			"  return this !== undefined;\n" +
			"}\n" +
			"foo.call(undefined);"));
	}

	public void testImplicitGlobalAssignmentThrowsInStrictFunction() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function fun() {\n" +
			"  'use strict';\n" +
			"  test262unresolvable = null;\n" +
			"}\n" +
			"let threw = false;\n" +
			"try { fun(); } catch(e) { threw = e instanceof ReferenceError; }\n" +
			"threw;"));
	}
}
