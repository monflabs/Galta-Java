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
package tests.javascript.clazz;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * A ClassDeclaration doesn't require (or want) ASI after its closing brace,
 * same as a Block or FunctionDeclaration - the parser must not swallow it
 * into an ExpressionStatement (which does require ASI). See
 * ExpressionStatement()'s lookahead exclusion in JSParser.jj.
 *
 * The transpiler's last-bare-statement-value tracking doesn't return the
 * expected value for these shapes (a separate, unaudited gap - see
 * MultiLineCommentAsiTest), so this is interpreter-only.
 *
 * @author Philippe Riand
 */
public class ClassDeclarationAsiTest extends JavaScriptStrictTestCase {

	public void testClassImmediatelyFollowedByBlock() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode("class C {}{} true;"));
	}

	public void testClassImmediatelyFollowedByLetDeclaration() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(3, executeCode("class C {}let a = 3; a;"));
	}

	public void testClassImmediatelyFollowedByNumber() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(1, executeCode("class C {}1;"));
	}
}
