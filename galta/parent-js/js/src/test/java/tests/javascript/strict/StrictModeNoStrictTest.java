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
package tests.javascript.strict;

import java.nio.file.Path;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.javacompiler.factory.MapTargetFactory;

import tests.javascript.JavaScriptNoStrictTestCase;

public class StrictModeNoStrictTest extends JavaScriptNoStrictTestCase {
    
	public void testScript() throws Exception {
		execute();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder envBuilder = super.createEnvironment();
		
		Path root = support.getTestResourcesDirectory("tests/javascript/strict").toPath();
		JSPathModuleResolver resolver = new JSPathModuleResolver(root);
		if(isJavaTranspiler()) {
			resolver.initTranspiler(getTranspilerOptions(), JSEnvironment.class.getClassLoader(), new MapTargetFactory());
		}
		envBuilder.addModuleResolver(resolver);

		return envBuilder;
	}
}
