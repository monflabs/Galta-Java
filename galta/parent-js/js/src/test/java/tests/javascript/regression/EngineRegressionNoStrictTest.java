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
package tests.javascript.regression;

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * Engine regressions that need sloppy mode, run in every execution mode.
 */
public class EngineRegressionNoStrictTest extends JavaScriptNoStrictTestCase {

	// Mapped arguments: the mapping is fixed at call time, arguments.length=... does not break it
	public void testArgumentsMappingIgnoresLength() throws Exception {
		executeCode("""
			function f(a){ arguments.length = 0; a = 5; return arguments[0]; }
			assertEquals(5, f(1));
			function g(a){ arguments.length = 0; arguments[0] = 7; return a; }
			assertEquals(7, g(1));
			""");
	}

	// An unresolvable identifier read-modify-written inside a with is a ReferenceError
	public void testUnresolvableInWith() throws Exception {
		executeCode("""
			assertThrows(ReferenceError, () => { with({}) { undeclaredInWith++ } });
			assertThrows(ReferenceError, () => { with({}) { undeclaredInWith += 1 } });
			""");
	}
}
