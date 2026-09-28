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

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * Class bodies are always strict-mode code, even when the enclosing script/environment
 * is not (env.isStrictMode()=false here, and no "use strict" directive anywhere).
 * Regression coverage for a bug where, without that forced strictness, a derived class's
 * constructor had its {@code this} prematurely bound to globalThis before the explicit
 * super() call ran, making super() think it had already been called.
 *
 * @author Philippe Riand
 */
public class InheritanceNoStrictTest extends JavaScriptNoStrictTestCase {

	public void testScript() throws Exception {
		execute();
	}
}
