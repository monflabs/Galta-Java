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
package tests;

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
import org.monflabs.galtajs.precompiled.BeautifyJs;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.js.debugger.ui.SwingDebugger;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.path.FilesUtil;

public class JsFormatterTest extends __BaseTestCase {
	
	JSEnvironment env = TestEnvironment.create();

	public void testTranspiled() throws Exception {
		BeautifyJs beautifier = BeautifyJs.newBuilder()
				.environment(env)
				.build();
		
		String css = support.loadText("source/sample.js");
		String res = beautifier.execute(css);
		support.assertTextResult(res, "sample.js");
	}
	
	public void testInterpreted() throws Exception {
		InterpretedGlobalRuntimeContext context = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		String code = support.loadText(new File(support.getProjectRoot(),"js/beautify.js"));
		JSInterpretedUnit expr = env.createScript(code,"beautify.js");
		expr.executeWithContext(context);

		String js = support.loadText("source/sample.js");
		
		JSValue beautifier = context.global("js_beautify");
		String res = beautifier.call(js).stringValue();
		
		support.assertTextResult(res, "sample.js");
	}

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
		  import 'beautify.js';
		  const r = global.js_beautify(`function f(){a=445; b=99;}`);
		  console.log(`Result:\\n${r}`);
		""";

		String module = support.loadText(new File(support.getProjectRoot(),"js/beautify.js"));

		Files.writeString(fs.resolve("js-example.js"),code,StandardCharsets.UTF_8);
		Files.writeString(fs.resolve("beautify.js"),module,StandardCharsets.UTF_8);

		JSInterpretedUnit sc = env.createScript(code,"js-example.js");
		d.debugScript(sc, () -> new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor()));

	}
	public static void main(String[] args) throws IOException {
		(new JsFormatterTest()).runDebugger();
	}
}
