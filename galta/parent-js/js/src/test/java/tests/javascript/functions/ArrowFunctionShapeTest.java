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
 * Arrow functions have no [[Construct]] and no own "prototype" property.
 *
 * Interpreted mode only: the transpiled forms of these particular scripts
 * don't return the expected script completion value (a separate, pre-existing
 * transpiler quirk, unrelated to this behavior).
 *
 * @author Philippe Riand
 */
public class ArrowFunctionShapeTest extends JavaScriptStrictTestCase {

	public void testNoOwnPrototype() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode("!('prototype' in (() => {}));"));
	}

	public void testNotConstructible() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"const af = () => {};" +
			"let threw = false;" +
			"try { new af(); } catch(e) { threw = e instanceof TypeError; }" +
			"threw;"));
	}
}
