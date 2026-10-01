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
package org.monflabs.galtajs.library.node.fs;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.JSNativeModule;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.executors.JSExecutor;

/**
 * The {@code node:fs/promises} module: async, Promise-returning filesystem
 * operations. Each method schedules its blocking IO on the environment's
 * async executor via {@link JSExecutor#asyncFunction} so pending-async
 * bookkeeping stays balanced with the event loop.
 */
public final class NodeFsPromisesModule extends JSNativeModule {

	public NodeFsPromisesModule(JSEnvironment env, JSModuleDescriptor descriptor) {
		this(env, descriptor, FileSystems.getDefault());
	}

	/**
	 * @param fs the file system the module's paths are resolved in
	 */
	public NodeFsPromisesModule(JSEnvironment env, JSModuleDescriptor descriptor, FileSystem fs) {
		super(env, descriptor);
		JSObject api = JSObject.create(env);
		api.setOwnMethod(new Method(env, fs, MethodId.readFile,   1));
		api.setOwnMethod(new Method(env, fs, MethodId.writeFile,  2));
		api.setOwnMethod(new Method(env, fs, MethodId.appendFile, 2));
		api.setOwnMethod(new Method(env, fs, MethodId.stat,       1));
		api.setOwnMethod(new Method(env, fs, MethodId.access,     1));
		api.setOwnMethod(new Method(env, fs, MethodId.realpath,   1));
		api.setOwnMethod(new Method(env, fs, MethodId.mkdir,      1));
		api.setOwnMethod(new Method(env, fs, MethodId.rm,         1));
		api.setOwnMethod(new Method(env, fs, MethodId.readdir,    1));
		api.setOwnMethod(new Method(env, fs, MethodId.unlink,     1));
		api.setOwnMethod(new Method(env, fs, MethodId.rename,     2));
		api.setOwnMethod(new Method(env, fs, MethodId.copyFile,   2));
		// Default export mirrors named exports so both work:
		//   import fsp from 'node:fs/promises'
		//   import { readFile } from 'node:fs/promises'
		setDefaultExport(api);
		setNamedExports(api);
	}

	private static enum MethodId {
		readFile,
		writeFile,
		appendFile,
		stat,
		access,
		realpath,
		mkdir,
		rm,
		readdir,
		unlink,
		rename,
		copyFile,
	}

	private final static class Method extends BaseMethod {
		private final MethodId methodId;
		private final JSEnvironment env;
		private final FileSystem fs;

		private Method(JSEnvironment env, FileSystem fs, MethodId methodId, int length) {
			super(env, methodId.name(), length);
			this.env = env;
			this.fs = fs;
			this.methodId = methodId;
		}

		@Override
		protected Object invoke(final Object obj, final Object[] args) {
			final Object a0 = arg(args, 0), a1 = arg(args, 1), a2 = arg(args, 2);
			JSExecutor executor = JSRuntimeContext.get().getGlobalContext().getExecutor();
			switch (methodId) {
				case readFile -> {
					return executor.asyncFunction(() -> NodeFsOps.readFile(NodeFsOps.pathOf(fs, a0), a1));
				}
				case writeFile -> {
					return executor.asyncFunction(() -> { NodeFsOps.writeFile(NodeFsOps.pathOf(fs, a0), a1, a2); return null; });
				}
				case appendFile -> {
					return executor.asyncFunction(() -> { NodeFsOps.appendFile(NodeFsOps.pathOf(fs, a0), a1, a2); return null; });
				}
				case stat -> {
					return executor.asyncFunction(() -> NodeFsOps.stat(env, NodeFsOps.pathOf(fs, a0)));
				}
				case access -> {
					return executor.asyncFunction(() -> { NodeFsOps.access(NodeFsOps.pathOf(fs, a0)); return null; });
				}
				case realpath -> {
					return executor.asyncFunction(() -> NodeFsOps.realpath(NodeFsOps.pathOf(fs, a0)));
				}
				case mkdir -> {
					return executor.asyncFunction(() -> { NodeFsOps.mkdir(NodeFsOps.pathOf(fs, a0), a1); return null; });
				}
				case rm -> {
					return executor.asyncFunction(() -> { NodeFsOps.rm(NodeFsOps.pathOf(fs, a0), a1); return null; });
				}
				case readdir -> {
					return executor.asyncFunction(() -> NodeFsOps.readdir(env, NodeFsOps.pathOf(fs, a0)));
				}
				case unlink -> {
					return executor.asyncFunction(() -> { NodeFsOps.unlink(NodeFsOps.pathOf(fs, a0)); return null; });
				}
				case rename -> {
					return executor.asyncFunction(() -> { NodeFsOps.rename(NodeFsOps.pathOf(fs, a0), NodeFsOps.pathOf(fs, a1)); return null; });
				}
				case copyFile -> {
					return executor.asyncFunction(() -> { NodeFsOps.copyFile(NodeFsOps.pathOf(fs, a0), NodeFsOps.pathOf(fs, a1)); return null; });
				}
				default -> {
					throw new IllegalStateException();
				}
			}
		}

		private static Object arg(Object[] args, int i) {
			return (args != null && i < args.length) ? args[i] : null;
		}
	}
}
