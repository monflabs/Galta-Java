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
package tests.javascript.builtin.function.constructor;

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * A dynamically-created function (Function()/new Function()) is never
 * lexically nested in the calling scope, so its own strictness must come
 * solely from its own body's directive prologue, never from the calling
 * context. Uses a non-strict-environment test case deliberately - see
 * FunctionConstructorTest (JavaScriptStrictTestCase) for why: that
 * environment's env-wide strict flag is its own separate confound for
 * strict-vs-non-strict boundary assertions like these.
 *
 * @author Philippe Riand
 */
public class FunctionConstructorStrictModeTest extends JavaScriptNoStrictTestCase {

	public void testScript() throws Exception {
		execute();
	}
}
