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
package tests.javascript.executor;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class JSScriptExecutorTest extends JavaScriptStrictTestCase {

	public void testExecutor() throws Exception {
		return;
//		// Skip some environments
//		if(isJavaTranspiler() || isJavaDecompiler()) {
//			return;
//		}
//		
//		JSEnvironment env = new JSDefaultEnvironment();
//		
//		FileSystem memFS = MemoryFileSystem.newBuilder().build();
//
//		Path fs = FilesUtil.getRoot(memFS);
//		FilesUtil.writeString(fs.resolve("script.js"),"import mod from 'mod'; return mod;",StandardCharsets.UTF_8);
//		FilesUtil.writeString(fs.resolve("mod.js"),"export default 49;",StandardCharsets.UTF_8);
//		JSScriptExecutor exec = new JSScriptExecutor(env,fs); 
//		
//		Object r = exec.executeFile("script.js");
//		assertEquals(49, r);
//		
//		
//		Object r2 = exec.execute("mod;");
//		assertEquals(49, r2);
//
//		exec.clearContext();
//		Object r3 = exec.execute("import mod from 'mod'; return mod;");
//		assertEquals(49, r3);
	}
}
