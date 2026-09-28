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
 * A rest parameter may be a destructuring pattern (array or object), not just a plain
 * identifier: {@code function f(...[a, b]) {}} or {@code function f(...{a, b}) {}}.
 *
 * Only implemented in interpreted mode -- the transpiler's codegen for rest parameters
 * only handles a plain identifier target.
 *
 * @author Philippe Riand
 */
public class RestParameterPatternTest extends JavaScriptStrictTestCase {

	public void testArrayPattern() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(1, executeCode("function f(...[a, b, c]) { return a; } f(1, 2, 3);"));
		assertEquals(2, executeCode("function f(...[a, b, c]) { return b; } f(1, 2, 3);"));
		assertEquals(3, executeCode("function f(...[a, b, c]) { return c; } f(1, 2, 3);"));
	}

	public void testArrayPatternWithLeadingParameter() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(6, executeCode("function f(x, ...[a, b]) { return x + a + b; } f(1, 2, 3);"));
	}

	public void testObjectPattern() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(3, executeCode("function f(...[{p: q}, r]) { return q + r; } f({p: 1}, 2);"));
	}

	public void testEmptyArrayPatternStillCollectsArguments() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// The pattern itself binds nothing, but "arguments" still sees every actual argument.
		assertEquals(2, executeCode("function f(...[]) { return arguments.length; } f(1, 2);"));
	}
}
