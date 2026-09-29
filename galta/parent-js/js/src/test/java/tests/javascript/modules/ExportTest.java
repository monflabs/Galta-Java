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
package tests.javascript.modules;

import static org.junit.Assert.assertThrows;

import org.monflabs.galtajs.JSException;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class ExportTest extends JavaScriptStrictTestCase {
	
	public void testExportParser() throws Exception {
		// See: https://developer.mozilla.org/en-US/docs/web/javascript/reference/statements/export
		
		//Exporting declarations
		// (a const declaration always needs an initializer, see "export const name1=1, name2=2" below)
		compileStatement("export var name1, name2;");
		compileStatement("export let name1, name2;");
		compileStatement("export const name1=1, name2=2;");
		compileStatement("export var name1=1, name2=2;");
		compileStatement("export let name1=1, name2=2;");
		compileStatement("function functionName() {}");
		compileStatement("const { name1, name2: bar } = o");
		compileStatement("const [ name1, name2 ] = array");
		
		//Export list
		compileStatement("export { name1, name2 }");
		compileStatement("export { variable1 as name1, variable2 as name2, nameN }");
		compileStatement("export { variable1 as \"string name\" }");
		compileStatement("export { name1 as default}");
		
		//Default exports
		compileStatement("export default expression");
		compileStatement("export default function functionName() {}");
		compileStatement("export default function () {}");
		
		//Aggregating modules
		compileStatement("export * from \"module-name\"");
		compileStatement("export * as name1 from \"module-name\"");
		compileStatement("export { name1, nameN } from \"module-name\"");
		compileStatement("export { import1 as name1, import2 as name2, nameN } from \"module-name\"");
		compileStatement("export { default } from \"module-name\"");

		assertThrows( JSException.class, () -> compileStatement("{export * from \"module-name\"}") );
	}		
	private void compileStatement(String text) {
		getEnvironment().createScript(text,"ExportTest");
	}
}
