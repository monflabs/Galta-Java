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
package tests.extras;

import java.io.PrintStream;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.TranspiledModuleDescriptor;
import org.monflabs.galtajs.rt.JSUnitContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext;
import org.monflabs.util.Console;
import org.monflabs.util.io.NullOutputStream;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class JavaTranspilerPerfTest extends JavaScriptStrictTestCase {
	
	public static final String SCRIPT = 
"""
function calculateSum(n) {
	let sum = 0L

 	for (let i = 0; i < n; i++) {
   		if (i % 3 === 0 || i % 5 === 0) {
     		sum += i
   		}
   	}
 
 	return sum
}

console.log(`Starting execution Interpreted...`);
const start = Java.type("java.lang.System").currentTimeMillis();

let sum = 0L;
for(let i=0; i<2000; i++) {
	sum += calculateSum(10000);
}

const end = Java.type("java.lang.System").currentTimeMillis();

console.log(`   ... ${sum} Execution time: ${end-start} ms`);
""";


	public class InterpretedClass {
	
		public Void runValue(InterpretedGlobalRuntimeContext __context__) throws Exception {
			executeCode(SCRIPT);
			return null;
		}
	}

	public class CompiledClass extends JSTranspiledUnit {
		
		JSEnvironment env;
		
		public CompiledClass(JSEnvironment env) {
			super(env,new TranspiledModuleDescriptor(JSUnitContext.DEFAULT_PROGRAM_NAME));
			this.env = env;
		}

		@Override
		protected void _runValue(JSTranspiledRuntimeContext __context) throws Exception {
			JSInterpretedUnit expr = __context.getEnvironment().createScript(SCRIPT,"TranspilerTest");
			transpilerExecute(env,"TestClass",null, expr);
		}
	}

	
	public class ManualCompiledClassOptimizedFull extends JSTranspiledUnit {
		
		public ManualCompiledClassOptimizedFull(JSEnvironment env) {
			super(env,new TranspiledModuleDescriptor(JSUnitContext.DEFAULT_PROGRAM_NAME));
		}
		
		@Override
		protected void _runValue(JSTranspiledRuntimeContext __context) throws Exception {
			Function<Object[],Object> f_calculateSum = (params) -> {
				int n = RuntimeUtil.toInt32(__context.getEnvironment(),params[0]);
				int sum = 0;
				for(int i=0; i<n; i++) {
			   		if (i % 3 == 0 || i % 5 == 0) {
			     		sum += i;
			   		}
				}
				return sum;
			};

			Console.log("Starting execution {0}", getClass());
			final long start = java.lang.System.currentTimeMillis();
			
			long sum = 0L;
			for(int i=0; i<2000; i++) {
				sum += RuntimeUtil.toInt32(__context.getEnvironment(),f_calculateSum.apply(new Object[] {10000}));
			}
			
			final long end =java.lang.System.currentTimeMillis();
			
			Console.log("... {0} Execution time: {1} ms",sum,end-start);
		}
	}
	
	
	public void testCompiledCode() throws Exception {
		// Just an individual test
		if(_ALLTESTS) {
			return;
		}
		
		int WARM_LOOPS = 2;
		PrintStream originalOut = System.out;
		System.setOut(new PrintStream(NullOutputStream.instance));
		for( int i=0; i<=WARM_LOOPS; i++) {
			if(i==WARM_LOOPS) {
				System.setOut(originalOut);
			}
			JSEnvironment env = createEnvironment().build();
			{
				JSTranspiledRuntimeContext ctx = new TranspiledGlobalRuntimeContext(env,env.createProgramExecutor());
				ManualCompiledClassOptimizedFull co = new ManualCompiledClassOptimizedFull(env);
				co.runValue(ctx);
			}
			
			{
				InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(), getEnvironment().createProgramExecutor());
				InterpretedClass ic = new InterpretedClass();
				ic.runValue(ctx);
			}
			
			{
				JSTranspiledRuntimeContext ctx = new TranspiledGlobalRuntimeContext(env,env.createProgramExecutor());
				CompiledClass ic = new CompiledClass(env);
				ic.runValue(ctx);
			}
		}
	}
}
