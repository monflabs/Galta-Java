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
 * A genuinely strict-mode function (via its own "use strict" directive, not
 * JSEnvironment.isStrictMode()) gets an unmapped arguments object whose "callee" is a
 * poison-pill accessor: reading or writing it always throws (spec 9.4.4.6).
 *
 * Only implemented in interpreted mode -- the transpiler's arguments object never has a
 * poison-pill callee.
 *
 * @author Philippe Riand
 */
public class ArgumentsCalleeStrictTest extends JavaScriptNoStrictTestCase {

	public void testCalleeGetterThrows() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function f() {" +
			"  'use strict';" +
			"  return arguments;" +
			"}" +
			"var args = f(1, 2, 3);" +
			"var threw = false;" +
			"try { void args.callee; } catch (e) { threw = e instanceof TypeError; }" +
			"threw;"));
	}

	public void testCalleeSetterThrows() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function f() {" +
			"  'use strict';" +
			"  return arguments;" +
			"}" +
			"var args = f(1, 2, 3);" +
			"var threw = false;" +
			"try { args.callee = null; } catch (e) { threw = e instanceof TypeError; }" +
			"threw;"));
	}
}
