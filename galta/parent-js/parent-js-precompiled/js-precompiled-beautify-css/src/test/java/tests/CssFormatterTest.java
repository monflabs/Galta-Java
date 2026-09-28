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
import org.monflabs.galtajs.precompiled.BeautifyCss;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.js.debugger.ui.SwingDebugger;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.path.FilesUtil;

public class CssFormatterTest extends __BaseTestCase {

	JSEnvironment env = TestEnvironment.create();

	public void testTranspiled() throws Exception {
		BeautifyCss beautifier = BeautifyCss.newBuilder()
				.environment(env)
				.build();
		
		String css = support.loadText("source/sample.css");
		String res = beautifier.execute(css);
		support.assertTextResult(res, "sample.css");
	}
	
	public void testInterpreted() throws Exception {
		InterpretedGlobalRuntimeContext context = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		String code = support.loadText(new File(support.getProjectRoot(),"js/beautify-css.js"));
		JSInterpretedUnit expr = env.createScript(code,"beautify-css.js");
		expr.executeWithContext(context);

		String css = support.loadText("source/sample.css");

		JSValue beautifier = context.global("css_beautify");
		String res = beautifier.call(css).stringValue();
		
		support.assertTextResult(res, "sample.css");
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
		  import 'beautify-css.js';
		  const r = global.css_beautify(`.h1{color:blue}`);
		  console.log(`Result:\\n${r}`);
		""";

		String module = support.loadText(new File(support.getProjectRoot(),"js/beautify-css.js"));

		Files.writeString(fs.resolve("css-example.js"),code,StandardCharsets.UTF_8);
		Files.writeString(fs.resolve("beautify-css.js"),module,StandardCharsets.UTF_8);

		JSInterpretedUnit sc = env.createScript(code,"css-example.js");
		d.debugScript(sc, () -> new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor()));

	}
	public static void main(String[] args) throws IOException {
		(new CssFormatterTest()).runDebugger();
	}
}
