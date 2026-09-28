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
package org.monflabs.galtajs;

import java.util.function.Consumer;

import org.monflabs.galtajs.rt.JSGlobalContext;

/**
 * .
 */
public interface JSModuleDescriptor {

	//
	// Module metadata
	// Note that the name is normalized(), thus it might be different from the name used
	// when retrieving the module
	//  ex: getModule("/script.js") can return a descriptor named "script"
	//
	public String getName();
	public boolean isScript();
	public String getScript();

	//
	// Load a script unit to be executed
	//
	public default JSModule loadScript(JSEnvironment env) {
		throw new IllegalStateException("Unit is a module and cannot be loaded as a script");
	}

	//
	// Load a script unit to be executed
	//
	public default JSModule loadModule(JSGlobalContext globalContext) {
		throw new IllegalStateException("Unit is a script and cannot be loaded as a module");
	}

	// Like loadModule(globalContext), but gives the caller a chance to
	// register the freshly-CONSTRUCTED module object (before its own
	// top-level body runs) into the caller's own module cache -
	// `earlyRegister` is invoked with the module as soon as it exists as a
	// JSModule instance, strictly before evaluation. Without this, a module
	// that imports ITSELF (directly, or transitively through a cycle) mid-
	// load finds no cached entry yet and recurses into loading a SECOND,
	// separate instance of the same module from scratch - previously a hard
	// crash (see KnownGaps.md's module live-bindings entry). Default
	// implementation falls back to the old, non-early-registering behavior
	// unchanged - only a resolver that's been updated to actually construct
	// the module object before running its body (see
	// JSSourceModuleResolver's interpreted path) can honor `earlyRegister`
	// meaningfully.
	public default JSModule loadModule(JSGlobalContext globalContext, Consumer<JSModule> earlyRegister) {
		return loadModule(globalContext);
	}

	//
	// Source-phase import (`import source x from '...'`, `import.source(...)`)
	// The text the module's Module Source Object exposes, or null when the
	// module has no source-phase representation. Per spec a Source Text
	// Module Record never has one (GetModuleSource throws a SyntaxError), so
	// a script descriptor (isScript()) returns null; the other module kinds
	// are host-defined: a native (Java-implemented) module exposes the
	// placeholder comment below, a precompiled transpiled module its
	// retained source when it has one (see JSTranspiledModuleResolver).
	//
	public default String getModuleSourceText() {
		return isScript() ? null : nativeModuleSourceText(getName());
	}
	public static String nativeModuleSourceText(String name) {
		return "// native module: " + name + "\n";
	}

	//
	// `import x from '...' with { type: "bytes" }`: the module's raw bytes.
	// Default: the UTF-8 encoding of the script text (or, for a module
	// kind that has no script, of getModuleSourceText()); a file-backed
	// resolver overrides this to read the file as-is (JSPathModuleResolver).
	//
	public default byte[] getBytes() {
		String text = isScript() ? getScript() : getModuleSourceText();
		return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}
}
