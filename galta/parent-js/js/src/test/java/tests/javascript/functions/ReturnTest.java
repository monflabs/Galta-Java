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

import org.monflabs.galtajs.JSEnvironment;

import tests.javascript.JavaScriptStrictTestCase;
import util.GlobalTestEnvironment;

/**
 * Test retunrn in or outside a function
 * @author Philippe Riand
 */
public class ReturnTest extends JavaScriptStrictTestCase {

	public void testReturnAllowedStatement() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.newBuilder() 
				.supportReturnOutsideFunction(true)
				.build();
		String js = "return;";
		env.createScript(js,"return");
	}	
	
	public void testReturnNotAllowedStatement() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.newBuilder() 
				.supportReturnOutsideFunction(false)
				.build();
		String js = "return;";
		try {
			env.createScript(js,"return");
			fail();
		} catch(Exception ex) {
		}
	}	

}
