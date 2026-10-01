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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSValue;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.precompiled.Typescript;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.FileUtil;
import org.monflabs.util.PathUtil;

import tests.TestEnvironment;

public class TypeScriptPracticalExamplesTest extends __BaseTestCase {
	
	JSEnvironment env = TestEnvironment.create();

	public void testInterpreted() throws Exception {
		String code = support.loadText(new File(support.getProjectRoot(),"js/typescript.js"));
		JSInterpretedUnit expr = env.createScript(code,"typescript.js");

		
		File dir = support.getProjectDirectory("js/Practical Examples");
		File[] files = dir.listFiles((f)-> "ts".equals(PathUtil.FILE_AGNOSTIC.getFileExtension(f.getPath())));
		for(int i=0; i<files.length; i++) {
			File f = files[i];

			String tsSource = FileUtil.readContent(f);
		
			InterpretedGlobalRuntimeContext context = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
			expr.executeWithContext(context);
			JSValue ts = context.global("ts");

			JSValue compilerOptions = context.createObject();
			compilerOptions.put("module",ts.get("ModuleKind").get("None"));
			compilerOptions.put("target",ts.get("ScriptTarget").get("ES2020"));

			String result = ts.get("transpile").call(tsSource,compilerOptions).stringValue();
			//Console.log(result);
			support.assertTextResult(result, PathUtil.FILE_AGNOSTIC.removeExtension(f.getName())+".js");
		}

	}
	
	public void testTranspiled() throws Exception {
		Typescript tsCompiler = Typescript.newBuilder()
				.environment(env)
				.build();
		
		// typescript.js is not transpiled by this build yet (see the pom): a
		// clear failure, not a NullPointerException. Once it is, check every
		// example as testInterpreted() does.
		try {
			tsCompiler.execute("let x: number = 1;");
			fail("The precompiled compiler is not available");
		} catch(IllegalStateException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("not available"));
		}
	}
}
