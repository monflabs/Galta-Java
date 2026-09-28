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
package tests.javascript.control;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Generator.prototype.next(value) resumes the PREVIOUS yield expression with
 * that value; the first next() call's argument is always discarded, since
 * there is no yield to resolve yet.
 *
 * Only implemented in interpreted mode -- the transpiler does not yet support
 * using a yield expression's result (e.g. {@code const x = yield y}).
 *
 * @author Philippe Riand
 */
public class GeneratorResumeValueTest extends JavaScriptStrictTestCase {

	public void testResumeValue() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function* echo() {" +
			"  const first = yield 'a';" +
			"  const second = yield first;" +
			"  return second;" +
			"}" +
			"const e = echo();" +
			"const v1 = e.next('ignored').value;" +
			"const v2 = e.next(1).value;" +
			"const v3 = e.next(2).value;" +
			"const done = e.next().done;" +
			"v1 === 'a' && v2 === 1 && v3 === 2 && done === true;"));
	}

	// A bare "yield" (no operand) used as an expression, not just a bare statement.
	public void testYieldInArrayLiteral() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function* g() {" +
			"  const arr = [yield];" +
			"  return arr;" +
			"}" +
			"const it = g();" +
			"it.next();" +
			"const result = it.next(5).value;" +
			"result.length === 1 && result[0] === 5;"));
	}

	// yield*'s own expression value (the delegate's final done value).
	public void testYieldStarExpressionValue() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function* inner() {" +
			"  yield 'a';" +
			"  return 'inner-done';" +
			"}" +
			"function* outer() {" +
			"  const v = yield* inner();" +
			"  return 'outer:' + v;" +
			"}" +
			"const g = outer();" +
			"g.next();" +
			"const result = g.next().value;" +
			"result === 'outer:inner-done';"));
	}
}
