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
package tests.precompiled;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSValue;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.precompiled.Typescript;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.Console;
import org.monflabs.util.path.FilesUtil;

import org.monflabs.js.debugger.ui.SwingDebugger;

import tests.TestEnvironment;

public class TypeScriptTest extends __BaseTestCase {

	public void testTranspiled() throws Exception {
		JSEnvironment env = TestEnvironment.create();

		Typescript tsCompiler = Typescript.newBuilder()
				.environment(env)
				.build();

		String tsSource = support.loadText("source/sample.ts");
		// typescript.js is not transpiled by this build yet (see the pom): a
		// clear failure, not a NullPointerException
		try {
			tsCompiler.execute(tsSource);
			fail("The precompiled compiler is not available");
		} catch(IllegalStateException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("not available"));
		}
	}
	
	public void testInterpreted() throws Exception {
		JSEnvironment env = TestEnvironment.create();
		
		InterpretedGlobalRuntimeContext context = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		String code = support.loadText(new File(support.getProjectRoot(),"js/typescript.js"));
		JSInterpretedUnit expr = env.createScript(code,"typescript.js");
		expr.executeWithContext(context);
		
		JSValue ts = context.global("ts");

		JSValue compilerOptions = context.createObject();
		compilerOptions.put("module",ts.get("ModuleKind").get("None"));
		compilerOptions.put("target",ts.get("ScriptTarget").get("ES2020"));
		
		String tsSource = support.loadText("source/sample.ts");
		
	    String result = ts.get("transpile").call(tsSource,compilerOptions).stringValue();
		Console.log(result);
		support.assertTextResult(result, "sample.js");
	}

	// Application that launches the debugger!
	private void runDebugger() throws IOException {
		FileSystem memFs = MemoryFileSystem.newBuilder().build();
		Path fs = FilesUtil.getRoot(memFs);

		JSEnvironment env = TestEnvironment.newBuilder()
				.debug(true)
				.addModuleResolver(new JSPathModuleResolver(fs))
				.build();

		SwingDebugger d = new SwingDebugger();
		d.setVisible(true);

	    String code = """
	  	      //import {ts} from 'typescript.js';
	  	      const ts = require('typescript.js');
	  	      const options = {
	  	    	 module: ts.ModuleKind.None,
	  	    	 target: ts.ScriptTarget.ES2020,
	  	      }
	  	      const code =
	  	      `
	  	      function greet(name: string): string {
	  	        return "Hello, " + name + ", from TypeScript!";
	  	      }

	  	      const message:string = greet("World");
	  	      console.log(message);
	  	      `;

	  	      const res = ts.transpile(code,options);
	  	      console.log(res);

	  	      eval(res)
	  	    """;

		String module = support.loadText(new File(support.getProjectRoot(),"js/typescript.js"));

		Files.writeString(fs.resolve("ts-example.js"),code,StandardCharsets.UTF_8);
		Files.writeString(fs.resolve("typescript.js"),module,StandardCharsets.UTF_8);

		JSInterpretedUnit sc = env.createScript(code,"ts-example.js");
		d.debugScript(sc, () -> new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor()));

	}

	public static void main(String[] args) throws IOException {
		(new TypeScriptTest()).runDebugger();
	}
}
