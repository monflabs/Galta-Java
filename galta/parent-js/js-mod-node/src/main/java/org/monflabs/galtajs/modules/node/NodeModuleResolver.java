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
package org.monflabs.galtajs.modules.node;

import java.nio.file.FileSystem;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.modules.NativeModuleDescriptor;
import org.monflabs.galtajs.modules.NativeModuleResolver;
import org.monflabs.galtajs.rt.JSGlobalContext;


public class NodeModuleResolver extends NativeModuleResolver {
	
	public static final String MODULE_NAME = "fs";

	private class Descriptor extends NativeModuleDescriptor {
		private Descriptor() {
			super(MODULE_NAME);
		}
		@Override
		public JSModule loadModule(JSGlobalContext globalContext) {
			return new FsModule(globalContext.getEnvironment(),descriptor,fs);
		}
	}

	private final Descriptor descriptor = new Descriptor();

	private FileSystem fs;
		
	public NodeModuleResolver(FileSystem fs) {
		this.fs = fs;
	}
	
	@Override
	protected JSModuleDescriptor findModule(String name) {
		if(MODULE_NAME.equals(name)) {
			return descriptor;
		}
		return null;
	}
	
	@Override
	public Stream<JSModuleDescriptor> getModules() {
		return Stream.of(descriptor);
	}	
}
