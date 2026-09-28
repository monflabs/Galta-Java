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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSModuleDescriptor;

/**
 * Static in memory module resolver.
 */
public class JSMemoryModuleResolver extends JSSourceModuleResolver {
	
	private class Descriptor extends BaseDescriptor {
		
		private String source;
		
		private Descriptor(String name, String source) {
			super(name);
			this.source = source;
		}
	
		@Override
		public boolean isScript() {
			return true;
		}
	
		@Override
		public String getScript() {
			return source;
		}
	}


	private Map<String, JSModuleDescriptor> moduleSources = new HashMap<>();
	
	public JSMemoryModuleResolver() {
	}
	
	public Map<String, JSModuleDescriptor> getModuleSources() {
		return moduleSources;
	}

	public JSMemoryModuleResolver put(String name, String text) {
		moduleSources.put(name, new Descriptor(name, text));
		return this;
	}
	
	@Override
	protected JSModuleDescriptor findModule(String name) {
		return moduleSources.get(name);
	}
	
	@Override
	public Stream<JSModuleDescriptor> getModules() {
		return moduleSources.values().stream();
	}
}
