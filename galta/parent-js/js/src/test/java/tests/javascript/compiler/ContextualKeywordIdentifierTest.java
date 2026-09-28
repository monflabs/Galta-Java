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
package tests.javascript.compiler;

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * "synchronized" was removed from the reserved word list in ES5 and is
 * usable as an ordinary identifier in any mode.
 *
 * @author Philippe Riand
 */
public class ContextualKeywordIdentifierTest extends JavaScriptNoStrictTestCase {

	public void testSynchronizedAsIdentifier() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(1, executeCode("var synchronized = 1; synchronized;"));
	}
}
