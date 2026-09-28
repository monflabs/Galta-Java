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
package org.monflabs.galtajs.modules;

import org.monflabs.galtajs.JSModuleDescriptor;

/**
 *
 */
public abstract class AbstractModuleResolver implements JSModuleResolver {

	/** The default source extension, tried as a fallback by {@link #getModule(String)}. */
	public static final String EXTENSION = ".js";

	private boolean commonJS;

	public AbstractModuleResolver() {
	}

	public boolean isCommonJS() {
		return commonJS;
	}

	public void setCommonJS(boolean commonJS) {
		this.commonJS = commonJS;
	}

	// A bare specifier (e.g. "./module1", Node/bundler style) resolves
	// against the exact name first, so an explicitly-extensioned import
	// (still the common case in this codebase's own examples/tests) never
	// pays for a second lookup - and only falls back to name+".js" when
	// that first lookup misses. Centralized here (the one common ancestor
	// of every resolver - JSPathModuleResolver, JSMemoryModuleResolver,
	// NodeModuleResolver, JSTranspiledModuleResolver) rather than in each
	// resolver, so every resolver type gets this for free and can't
	// individually forget it.
	@Override
	public final JSModuleDescriptor getModule(String name) {
		JSModuleDescriptor md = findModule(name);
		if (md == null && !name.endsWith(EXTENSION)) {
			md = findModule(name + EXTENSION);
		}
		return md;
	}

	/**
	 * Resolves a module by its exact name, with no extension inference -
	 * implemented by each concrete resolver. {@link #getModule(String)} is
	 * the public entry point every caller should use instead.
	 */
	protected abstract JSModuleDescriptor findModule(String name);
}
