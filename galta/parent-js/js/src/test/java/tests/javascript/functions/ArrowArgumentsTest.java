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
package tests.javascript.functions;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * An arrow function has no {@code arguments} object of its own: a reference to
 * {@code arguments} inside an arrow resolves lexically, to the nearest enclosing
 * non-arrow function's arguments object.
 *
 * Only implemented in interpreted mode -- the transpiler gives every function, arrow or
 * not, its own arguments object.
 *
 * @author Philippe Riand
 */
public class ArrowArgumentsTest extends JavaScriptStrictTestCase {

	public void testArrowSeesEnclosingArguments() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(42, executeCode(
			"function outer() {" +
			"  const inner = () => arguments[0];" +
			"  return inner();" +
			"}" +
			"outer(42);"));
	}

	public void testArrowSeesEnclosingArgumentsLength() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(3, executeCode(
			"function outer(x) {" +
			"  const inner = () => arguments.length;" +
			"  return inner();" +
			"}" +
			"outer(1, 2, 3);"));
	}

	public void testArrowIgnoresItsOwnCallArguments() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// Called with no arguments, so this function's own arguments[0] is undefined;
		// invoking the returned arrow with its own arguments must have no effect.
		assertEquals(true, executeCode(
			"function makeArrow() {" +
			"  return () => arguments[0];" +
			"}" +
			"makeArrow()(999) === undefined;"));
	}
}
