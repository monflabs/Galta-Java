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

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Compound assignment to a computed member (obj[key] += v): the key expression
 * is evaluated and ToPropertyKey-coerced (e.g. calling a non-primitive key's
 * toString()) before the right-hand side runs, and only once - not once for the
 * read and again for the write. See ASTArrayMember.resolveReference().
 *
 * Interpreted mode only: the transpiler's compound-assignment code generation
 * doesn't share this fast path yet and re-coerces the key for each of the
 * read/write property accesses.
 *
 * @author Philippe Riand
 */
public class AssignComputedKeyOrderTest extends JavaScriptStrictTestCase {

	public void testKeyCoercedOnce() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"let keyCalls = 0;" +
			"const key = { toString() { keyCalls++; return 'prop'; } };" +
			"const obj = { prop: 10 };" +
			"obj[key] += 5;" +
			"obj.prop === 15 && keyCalls === 1;"));
	}

	public void testKeyCoercionThrowsBeforeRightHandSide() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"let rhsEvaluated = false;" +
			"const key = { toString() { throw new Error('keyThrew'); } };" +
			"const obj = {};" +
			"let caught = null;" +
			"try { obj[key] += (rhsEvaluated = true, 1); } catch(e) { caught = e.message; }" +
			"caught === 'keyThrew' && rhsEvaluated === false;"));
	}
}
