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
package doc_examples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.library.CommonJSLibrary;
import org.monflabs.galtajs.modules.JSFileModuleResolver;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSMemoryModuleResolver;
import org.monflabs.galtajs.modules.JSNativeModule;
import org.monflabs.galtajs.modules.NativeModuleDescriptor;
import org.monflabs.galtajs.modules.NativeModuleResolver;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/UserGuide/Modules.md and docs/GaltaJS/Extending/NativeModules.md
 */
public class ModulesExamples extends __BaseTestCase {

	public void testInMemoryModules() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("math", "export const PI = 3; export function twice(x) { return x * 2 } export default 'math-lib';")
				.put("app",  "import lib, { twice, PI } from 'math'; export const result = `${lib}:${twice(PI)}`;");
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();

		assertEquals("math-lib:6", (Object)env.evaluateScript("import { result } from 'app'; result"));
		assertEquals(3, (Object)env.evaluateScript("import * as m from 'math'; m.PI"));
	}

	public void testFileModules() throws Exception {
		Path root = Files.createTempDirectory("galta-modules");
		Files.writeString(root.resolve("lib.js"), "export const answer = 42;");
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.addModuleResolver(new JSFileModuleResolver(root.toFile()))
				.build();
		assertEquals(42, (Object)env.evaluateScript("import { answer } from './lib.js'; answer"));
		assertEquals(42, (Object)env.evaluateScript("import { answer } from 'lib'; answer"));   // '.js' is implied
	}

	public void testDynamicImport() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver().put("lazy", "export const v = 'loaded';");
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
		assertEquals("loaded", (Object)env.evaluateScript("const m = await import('lazy'); m.v"));
	}

	public void testDeferredImport() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("heavy", "globalThis.heavyLoaded = true; export const v = 1;");
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
		Object r = env.evaluateScript("""
			import defer * as ns from 'heavy';
			const before = globalThis.heavyLoaded;   // module not evaluated yet
			const v = ns.v;                          // first access evaluates it
			[before, globalThis.heavyLoaded, v]
			""");
		assertEquals(List.of(org.monflabs.galtajs.rt.RuntimeUtil.UNDEFINED, true, 1), list(r));
	}

	public void testTopLevelAwaitInModules() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("config", "export const settings = await Promise.resolve({ mode: 'test' });");
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
		// A classic script does not wait for an async module: import it dynamically...
		assertEquals("test", (Object)env.evaluateScript("const m = await import('config'); m.settings.mode"));
		// ...or make the root a module, which links asynchronously like any other module
		JSInterpretedUnit root = env.createScript("import { settings } from 'config'; export const mode = settings.mode;", "root.js", JSEnvironment.SCRIPT_MODULE);
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		assertEquals("test", root.initModule(ctx, false).getExport("mode"));
	}

	public void testCommonJsModules() throws Exception {
		Path root = Files.createTempDirectory("galta-cjs");
		Files.writeString(root.resolve("greeter.js"), "module.exports = { hello: (n) => 'hello ' + n };");
		Files.writeString(root.resolve("app.js"), "const g = require('greeter'); exports.msg = g.hello('cjs');");

		JSFileModuleResolver resolver = new JSFileModuleResolver(root.toFile());
		resolver.setCommonJS(true);   // files under this resolver are CommonJS modules
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.addModuleResolver(resolver)
				.registerLibrary(new CommonJSLibrary())   // provides the global require()
				.build();
		assertEquals("hello cjs", (Object)env.evaluateScript("require('app').msg"));
		// A CommonJS module can also be imported as an ES module
		assertEquals("hello esm", (Object)env.evaluateScript("import g from 'greeter'; g.hello('esm')"));
	}

	public void testNativeModuleImplementedInJava() {
		NativeModuleResolver resolver = new NativeModuleResolver() {
			@Override
			protected JSModuleDescriptor findModule(String name) {
				if(!name.equals("host:config")) {
					return null;
				}
				return new NativeModuleDescriptor(name) {
					@Override
					public JSModule loadModule(JSGlobalContext context) {
						JSEnvironment env = context.getEnvironment();
						Object defaultExport = "config-v1";
						JSObject namedExports = JSObject.of(env, "host", "galta", "version", 7);
						return new JSNativeModule(env, this, defaultExport, namedExports);
					}
				};
			}
			@Override
			public Stream<JSModuleDescriptor> getModules() {
				return Stream.empty();
			}
		};
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(resolver).build();
		assertEquals("galta/7/config-v1", (Object)env.evaluateScript("import d, { host, version } from 'host:config'; `${host}/${version}/${d}`"));
	}

	public void testSourcePhaseAndBytesImports() throws Exception {
		Path root = Files.createTempDirectory("galta-modules");
		Files.writeString(root.resolve("lib.js"), "export const answer = 42;");
		Files.write(root.resolve("logo.bin"), new byte[] { 1, 2, (byte)0xFF });
		NativeModuleResolver nativeResolver = new NativeModuleResolver() {
			@Override
			protected JSModuleDescriptor findModule(String name) {
				if(!name.equals("host:config")) {
					return null;
				}
				return new NativeModuleDescriptor(name) {
					@Override
					public JSModule loadModule(JSGlobalContext context) {
						return new JSNativeModule(context.getEnvironment(), this, "config-v1", null);
					}
				};
			}
			@Override
			public Stream<JSModuleDescriptor> getModules() {
				return Stream.empty();
			}
		};
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.addModuleResolver(new JSFileModuleResolver(root.toFile()))
				.addModuleResolver(nativeResolver)
				.build();
		// A native module's Module Source Object: its placeholder source text, never evaluated
		assertEquals("// native module: host:config\n",
				(Object)env.evaluateScript("import source src from 'host:config'; src.source"));
		assertEquals("[object ModuleSource]",
				(Object)env.evaluateScript("import source src from 'host:config'; Object.prototype.toString.call(src)"));
		// A JavaScript module has no source phase representation (spec): SyntaxError
		assertEquals("SyntaxError",
				(Object)env.evaluateScript("try { await import.source('./lib.js'); 'resolved' } catch(e) { e.name }"));
		// Bytes import: a Uint8Array over an immutable ArrayBuffer holding the file's bytes
		assertEquals("3:1,2,255:true",
				(Object)env.evaluateScript("import data from './logo.bin' with { type: 'bytes' }; `${data.length}:${Array.from(data)}:${data.buffer.immutable}`"));
	}
}
