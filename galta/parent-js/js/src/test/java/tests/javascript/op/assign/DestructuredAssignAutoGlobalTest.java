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
package tests.javascript.op.assign;

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * In non-strict mode, assigning through a destructuring target to an
 * identifier that isn't declared anywhere creates it as a global - the same
 * behavior a plain `x = value` assignment has. See
 * RuntimeUtil.assignIdentifierOrCreateGlobal(), shared by ASTIdentifier's
 * simple-assignment path and ASTAssign's destructuring-target path.
 *
 * Interpreted mode only: the transpiler's destructuring-assignment code
 * generation doesn't share this path.
 *
 * @author Philippe Riand
 */
public class DestructuredAssignAutoGlobalTest extends JavaScriptNoStrictTestCase {

	public void testArrayTargetCreatesGlobal() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"[undeclaredArrayTarget] = [5];" +
			"undeclaredArrayTarget === 5;"));
	}

	public void testObjectTargetCreatesGlobal() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"({a: undeclaredObjTarget} = {a: 7});" +
			"undeclaredObjTarget === 7;"));
	}
}
