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
package tests.sample;

import org.monflabs.util.Console;

import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.executors.IV8Executor;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8Script;

import tests.ProjectTestCase;

/**
 * Check that the V8 engine is properly loaded and works.
 *  
 * @author Philippe Riand
 */
public class HelloWorldTest extends ProjectTestCase {

	private static final String CODE =
"""
	const s = "Hello World";
	s
""";
	

	public void testV8_HelloWorld() throws Exception {
		// To prevent reports of resource leaking
		//V8Host.setLibraryReloadable(true);
		try(V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
		    IV8Executor executor = v8Runtime.getExecutor(CODE);
		    try(V8Script script = executor.compileV8Script()) {
		        V8Value result = script.execute();
		        String sResult = result.toString();
		        Console.log(sResult);
		        assertEquals("Hello World", sResult);
		    }
		} finally {
			//V8Host.getV8Instance().unloadLibrary();		
		}
	}
}
