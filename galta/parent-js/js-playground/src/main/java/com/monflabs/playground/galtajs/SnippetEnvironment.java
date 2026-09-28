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
package com.monflabs.playground.galtajs;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.UnitTestLibrary;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.library.platform.HostLibrary;
import org.monflabs.galtajs.library.rhino.RhinoShellLibrary;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.playground.PlaygroundConfiguration;


public class SnippetEnvironment {

	public static JSEnvironment.Builder newBuilder(boolean galtaJS, boolean strictMode) {
		return JSEnvironment.newBuilder()
				.registerLibrary(new StandardLibrary())
				.registerLibrary(new SnippetLibrary())
				.registerLibrary(new UnitTestLibrary())
				.registerLibrary(new JavaLibrary())
				.registerLibrary(new HostLibrary())
				.registerLibrary(new RhinoShellLibrary())
				.addModuleResolver(new JSPathModuleResolver(PlaygroundConfiguration.get().getSnippetFactory().getRoot()))
				.configure( (b) -> { if(galtaJS) b.enableGaltaJSExtensions(); } )
				.strictMode(strictMode)
				// Annex B APIs (escape, substr, ...) exist whatever the mode, as in browsers and Node:
				// set after strictMode(), which would otherwise turn them off in strict mode
				.deprecatedApis(true)
				.supportFloat16Array(true);
	}
	public static JSEnvironment create(boolean galtaJS, boolean strictMode) {
		return newBuilder(galtaJS, strictMode).build();
	}
	
	// Should not be used to evaluate snippets, but only for other operations
	public static JSEnvironment staticValue = create(true,false);
}
