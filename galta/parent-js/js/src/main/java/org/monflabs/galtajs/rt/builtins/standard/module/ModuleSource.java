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
package org.monflabs.galtajs.rt.builtins.standard.module;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * A module's Module Source Object (source-phase-imports proposal): the value
 * `import source x from '...'` / `import.source('...')` produce, one per
 * resolved module (see JSGlobalContext.importModuleSource()).
 *
 * Spec-wise this is a concrete Module Source Object: [[ModuleSourceClassName]]
 * is "ModuleSource" and its prototype chain runs ModuleSourcePrototype ->
 * AbstractModuleSourcePrototype -> Object.prototype. Only module kinds that
 * are NOT source text carry one (see JSModuleDescriptor.getModuleSourceText());
 * the text it exposes (`.source`) is the descriptor's own representation of
 * that module - a native module's placeholder comment, or a precompiled
 * transpiled module's retained source.
 */
public class ModuleSource extends NativeObject {

	public static final String CLASSNAME = "ModuleSource";

	private final String moduleName;
	private final String sourceText;

	public ModuleSource(JSEnvironment env, String moduleName, String sourceText) {
		super(env);
		this.moduleName = moduleName;
		this.sourceText = sourceText;
	}

	// The per-context resolution both JSGlobalContext implementations share
	// (see JSGlobalContext.importModuleSource()): `cache` is that context's
	// own resolved-name -> ModuleSource map. Only the module's DESCRIPTOR is
	// consulted - the module itself is never loaded, linked or evaluated.
	public static ModuleSource resolve(JSEnvironment env, java.util.Map<String,ModuleSource> cache, String resolvedName) {
		ModuleSource ms = cache.get(resolvedName);
		if(ms!=null) {
			return ms;
		}
		org.monflabs.galtajs.JSModuleDescriptor descriptor = org.monflabs.galtajs.rt.RuntimeUtil.findModuleDescriptor(env, resolvedName);
		if(descriptor==null) {
			throw org.monflabs.galtajs.rt.RuntimeUtil.typeError("Cannot find module {0}", resolvedName);
		}
		String text = descriptor.getModuleSourceText();
		if(text==null) {
			// Spec 16.2.1.7.2 GetModuleSource of a Source Text Module Record.
			throw org.monflabs.galtajs.rt.RuntimeUtil.syntaxError("Module {0} has no source phase representation", resolvedName);
		}
		ms = new ModuleSource(env, resolvedName, text);
		cache.put(resolvedName, ms);
		return ms;
	}

	public String getModuleName() {
		return moduleName;
	}
	public String getSourceText() {
		return sourceText;
	}
	// [[ModuleSourceClassName]] - what %AbstractModuleSource%.prototype's
	// @@toStringTag getter returns for this object.
	public String getModuleSourceClassName() {
		return CLASSNAME;
	}

	@Override
	public String getClassName() {
		return CLASSNAME;
	}
	@Override
	protected Object getDefaultPrototype() {
		return ModuleSourcePrototype.get(getEnvironment());
	}
}
