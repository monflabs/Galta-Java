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
package tests.debugger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.js.debugger.ui.SwingDebugger;
import org.monflabs.util.path.FilesUtil;

public class VisualDebuggerTest {

	public static final String SCRIPT =
"""
import "Script1.js"
import "Script2.js"

function f() {
  for(let i=0; i<5; i++) {
    console.log("    "+i);
  }
}
for(let i=0; i<5; i++) {
  console.log("Main: "+i);
  f();
}
""";

	public static void main(String[] args) throws IOException {
		SwingDebugger d = new SwingDebugger();
		d.setVisible(true);


		FileSystem memFS = MemoryFileSystem.newBuilder()
				.build();
		Path root = FilesUtil.getRoot(memFS);
		FilesUtil.writeString(root.resolve("DebuggerTest.js"),SCRIPT,StandardCharsets.UTF_8);
		FilesUtil.writeString(root.resolve("Script1.js"),"console.log('sc1');\n",StandardCharsets.UTF_8);
		FilesUtil.writeString(root.resolve("Script2.js"),"console.log('sc2');\n",StandardCharsets.UTF_8);

		JSPathModuleResolver resolver = new JSPathModuleResolver(root);

		JSEnvironment env = GlobalTestEnvironment.newBuilder()
				.debug(true)
				.addModuleResolver(resolver)
				.build();
		JSInterpretedUnit sc = env.createScript(SCRIPT,"DebuggerTest.js");


		d.debugScript(sc, () -> new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor()));
	}
}
