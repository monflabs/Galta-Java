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
package tests.galtajs;

import java.io.File;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.modules.JSFileModuleResolver;

import tests.ProjectTestCase;

/**
 * Sample test to reproduce issues.
 *  
 * @author Philippe Riand
 */
public class SampleTest extends ProjectTestCase {

	@Override
	protected boolean isDumpAstTree() {
		return true;
	}

	public void testScript() throws Exception {
		execute();
	}
	
	public static class This {
		public void execSync(Runnable r) {
			r.run();
		}
		public String one() {
			return "ONE";
		}
	}
	
	@Override
	protected Object getThis(JSEnvironment environment) {
		return new This();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder envBuilder = super.createEnvironment();

		File root = support.getTestResourcesDirectory("tests/galtajs/modules/resources");
		
		JSFileModuleResolver resolver = new JSFileModuleResolver(root);
		envBuilder.addModuleResolver(resolver);
		
		StaticLibrary sampleLib = new StaticLibrary();
		sampleLib.addStaticGlobal("globaValue", "a value");
		envBuilder.registerLibrary(sampleLib);
		
		return envBuilder;
	}
}
