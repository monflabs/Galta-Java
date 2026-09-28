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
package tests.javascript.javatranspiler;

import static org.junit.Assert.assertThrows;

import org.monflabs.galtajs.JSModule;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class TranspilerModuleTest extends JavaScriptStrictTestCase {

	public void testTranspile() throws Exception {
		String js1 = 
"""
// Module 1				
export default 'S0';
""";
		JSModule m1 = transpileToJavaModuleAndInit(
						getEnvironment(),
						"Module1",
						null,
						getEnvironment().createScript(js1,"Module1"),
						"m1");
		assertEquals("m1",m1.getDescriptor().getName());
		assertEquals("S0",m1.getDefaultExport());
		assertNull(m1.getNamedExports());
	
		String js2 = 
"""
//Module 				
export const s2 = 'S2';
export const s3 = 'S3', s4 = 'S4';
""";
		JSModule m2 = transpileToJavaModuleAndInit(
						getEnvironment(),
						"Module2",
						null,
						getEnvironment().createScript(js2,"Module2"),
					"m2");
		assertEquals("m2",m2.getDescriptor().getName());
		assertNull(m2.getDefaultExport());
		assertNotNull(m2.getNamedExports());
		assertEquals("S2", m2.getExport("s2"));
		assertEquals("S3", m2.getExport("s3"));
		assertEquals("S4", m2.getExport("s4"));
		assertThrows( Exception.class, () -> m2.getExport("fake"));
	}

}
