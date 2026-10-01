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
package org.monflabs.galtajs.library.node;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.library.node.fs.NodeFsModule;
import org.monflabs.galtajs.library.node.fs.NodeFsPromisesModule;
import org.monflabs.galtajs.modules.NativeModuleDescriptor;
import org.monflabs.galtajs.modules.NativeModuleResolver;
import org.monflabs.galtajs.rt.JSGlobalContext;

/**
 * Resolves Node.js-style built-in module specifiers to native GaltaJS modules.
 *
 * <p>Currently supported specifiers:
 * <ul>
 *   <li>{@code "node:fs"} / {@code "fs"} — synchronous fs API
 *   <li>{@code "node:fs/promises"} / {@code "fs/promises"} — Promise-returning fs API
 * </ul>
 */
public class NodeModuleResolver extends NativeModuleResolver {

	private static final List<String> KNOWN = List.of(
			"node:fs", "fs",
			"node:fs/promises", "fs/promises");

	private final FileSystem fs;

	public NodeModuleResolver() {
		this(FileSystems.getDefault());
	}

	/**
	 * @param fs the file system the fs modules resolve paths in, for example
	 *        an in-memory or zip file system to sandbox a script
	 */
	public NodeModuleResolver(FileSystem fs) {
		this.fs = fs;
	}

	@Override
	protected JSModuleDescriptor findModule(String name) {
		if (!KNOWN.contains(name)) return null;
		return new NativeModuleDescriptor(name) {
			@Override
			public JSModule loadModule(JSGlobalContext globalContext) {
				JSEnvironment env = globalContext.getEnvironment();
				String n = getName();
				if ("node:fs".equals(n) || "fs".equals(n)) {
					return new NodeFsModule(env, this, fs);
				}
				return new NodeFsPromisesModule(env, this, fs);
			}
		};
	}

	@Override
	public Stream<JSModuleDescriptor> getModules() {
		return Stream.empty();
	}
}
