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
package tests.javascript.api;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Sample script invocation.
 * @author Philippe Riand
 */
public class InvocationTests extends JavaScriptStrictTestCase {
	
	//
	// Environment & Library objects should be created and cached to void subsequent initialization time
	//
	
	
	public void testRun() throws Exception {
		// Create the execution environment
		// This environment holds the libraries to use
		//	- standard (print, println..)
		//  - JavaJre (access to Java Object using reflection)
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.registerLibrary(new JavaLibrary())
				.build();

		// The context can also take extra parameters:
		//		this
		//		library to use for this context
		// Note that the context contains the global variables being created by the script
		// and can also be reused, with these values, while executing another script
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());

		// Compile the script or get it from the cache
		JSInterpretedUnit expr = ctx.getEnvironment().createScript("1+4","InvocationTest");
		
		// Execute and get the result
		Object result = expr.executeWithContext(ctx);

		assertEquals(5, result);
	}
}
