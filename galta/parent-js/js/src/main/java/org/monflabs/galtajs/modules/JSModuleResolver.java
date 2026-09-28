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

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.rt.JSGlobalContext;

/**
 * JS Mpdule resolver.
 */
public interface JSModuleResolver {

	public JSModuleDescriptor getModule(String name);
	public Stream<JSModuleDescriptor> getModules();

	// Helper...
	public default JSModule loadModule(JSGlobalContext globalContext, String name) {
		JSModuleDescriptor md = getModule(name);
		if(md!=null) {
			JSModule m = md.loadModule(globalContext);
			return m;
		}
		return null;
	}

	// See JSModuleDescriptor.loadModule(globalContext, earlyRegister)'s own
	// doc comment - `earlyRegister` lets the caller cache the module BEFORE
	// its body runs, fixing self-import/circular-import crashes.
	public default JSModule loadModule(JSGlobalContext globalContext, String name, Consumer<JSModule> earlyRegister) {
		JSModuleDescriptor md = getModule(name);
		if(md!=null) {
			JSModule m = md.loadModule(globalContext, earlyRegister);
			return m;
		}
		return null;
	}
}
