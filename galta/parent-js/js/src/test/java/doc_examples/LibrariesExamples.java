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

import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSEnvironment.Builder;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.modules.JSMemoryModuleResolver;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extending/Libraries.md
 */
public class LibrariesExamples extends __BaseTestCase {

	/** A library contributing a global function, a global object and a module. */
	public static class GreetingLibrary extends GlobalLibrary {

		@Override
		public void configureEnvironment(Builder builder) {
			// Runs once per builder, before the environment exists: flags and module resolvers go here
			builder.supportGlobalAlias(true);
			builder.addModuleResolver(new JSMemoryModuleResolver()
					.put("greeting/format", "export const exclaim = s => s + '!';"));
		}

		@Override
		public void configureStandardObjects(JSEnvironment env, StandardObjects globals) {
			// Runs once per environment, after all built-ins are installed
			globals.setOwnProperty("greet", new BaseMethod(env, "greet", 1) {
				@Override
				public Object call(Object thisValue, Object[] args) {
					String who = args.length > 0 ? RuntimeUtil.toString(env, args[0]) : "world";
					return "Hello " + who;
				}
			});
			globals.setOwnProperty("greeting", JSObject.of(env, "version", 2, "languages", List.of("en", "fr")));
		}
	}

	public void testCustomLibrary() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new GreetingLibrary()).build();
		assertEquals("Hello Ada", (Object)env.evaluateExpression("greet('Ada')"));
		assertEquals("Hello world", (Object)env.evaluateExpression("greet()"));
		assertEquals(2, (Object)env.evaluateExpression("greeting.version"));
		assertEquals("object", (Object)env.evaluateExpression("typeof global"));
		assertEquals("Hello Ada!", (Object)env.evaluateScript("import { exclaim } from 'greeting/format'; exclaim(greet('Ada'))"));
	}

	public void testStaticLibrary() {
		// The ready-made way to expose Java values and functions, no subclassing needed
		StaticLibrary lib = new StaticLibrary();
		lib.addStaticGlobal("VERSION", "1.2.3");
		lib.addStaticGlobal("limits", JSObject.of(JavaScriptEnvironment.create(), "max", 10));
		lib.addFunction(new BaseMethod(null, "twice", 1) {
			@Override
			public Object call(Object thisValue, Object[] args) {
				return ((Number)args[0]).intValue() * 2;
			}
		});
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(lib).build();
		assertEquals("1.2.3", (Object)env.evaluateExpression("VERSION"));
		assertEquals(10, (Object)env.evaluateExpression("limits.max"));
		assertEquals(8, (Object)env.evaluateExpression("twice(4)"));
	}

	public void testLibrariesAreRegisteredInOrder() {
		StaticLibrary first = new StaticLibrary();
		first.addStaticGlobal("who", "first");
		StaticLibrary second = new StaticLibrary();
		second.addStaticGlobal("who", "second");
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(first).registerLibrary(second).build();
		assertEquals("second", (Object)env.evaluateExpression("who"));   // last registration wins
	}
}
