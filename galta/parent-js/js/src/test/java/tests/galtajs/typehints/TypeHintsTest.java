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
package tests.galtajs.typehints;

import tests.galtajs.GaltaJSTestCase;

/**
 * TypeScript-style type hints (JSConfiguration.supportTypeHints(), part of
 * GaltaJSTestCase's enableGaltaJSExtensions() bundle): parsed for syntax
 * only, no compile-time checking, no AST node, no transpiler codegen - see
 * the grammar's own doc comments (JSParser.jj, near isTypeAliasStart()) for
 * the full design.
 *
 * @author Philippe Riand
 */
public class TypeHintsTest extends GaltaJSTestCase {

	public void testScript() throws Exception {
		execute();
	}
}
