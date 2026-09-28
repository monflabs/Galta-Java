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
package tests.javascript.modules;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.JSNativeModule;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.modules.NativeModuleDescriptor;
import org.monflabs.galtajs.modules.NativeModuleResolver;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.javacompiler.factory.MapTargetFactory;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Source-phase imports (`import source x from`, `import.source()`) and
 * `with { type: "bytes" }` imports, against a file-backed resolver plus a
 * native (Java-implemented) module.
 */
public class ImportSourceTest extends JavaScriptStrictTestCase {

	public void testScript() throws Exception {
		execute();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder envBuilder = super.createEnvironment();
		Path root = support.getTestResourcesDirectory("tests/javascript/modules/resources").toPath();
		JSPathModuleResolver resolver = new JSPathModuleResolver(root);
		if(isJavaTranspiler()) {
			resolver.initTranspiler(getTranspilerOptions(), ImportSourceTest.class.getClassLoader(), new MapTargetFactory());
		}
		envBuilder.addModuleResolver(resolver);
		// A native module: the only module kind (besides a precompiled
		// transpiled one) that has a source-phase representation.
		envBuilder.addModuleResolver(new NativeModuleResolver() {
			@Override
			protected JSModuleDescriptor findModule(String name) {
				if(!"native-lib".equals(name)) {
					return null;
				}
				return new NativeModuleDescriptor(name) {
					@Override
					public JSModule loadModule(JSGlobalContext globalContext) {
						JSEnvironment env = globalContext.getEnvironment();
						return new JSNativeModule(env, this, JSObject.of(env, "hello", "world"), null);
					}
				};
			}
			@Override
			public Stream<JSModuleDescriptor> getModules() {
				return Stream.empty();
			}
		});
		return envBuilder;
	}
}
