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
import java.nio.file.Path;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.JSNativeModule;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.executors.MicroTask;

/**
 * The {@code node:fs} module: filesystem operations with sync and Node-style
 * callback-async APIs on a single module object.
 *
 * <p>Exports the API both as the module's default export and as individually
 * named exports:
 * <pre>
 *   import fs from 'node:fs';
 *   fs.readFileSync(p);
 *   fs.readFile(p, (err, data) => { ... });
 *
 *   import { readFile, readFileSync } from 'node:fs';
 * </pre>
 *
 * <p>Callback-async methods run their IO on the async worker pool then queue
 * a microtask on the event loop to invoke the user callback as
 * {@code callback(err, result)} (Node convention — {@code err} is {@code null}
 * on success). {@code exists(path, cb)} is the historical exception: it calls
 * {@code cb(exists)} with a single boolean.
 */
public final class NodeFsModule extends JSNativeModule {

	public NodeFsModule(JSEnvironment env, JSModuleDescriptor descriptor) {
		this(env, descriptor, FileSystems.getDefault());
	}

	/**
	 * @param fs the file system the module's paths are resolved in
	 */
	public NodeFsModule(JSEnvironment env, JSModuleDescriptor descriptor, FileSystem fs) {
		super(env, descriptor);
		JSObject api = JSObject.create(env);
		// Sync API
		api.setOwnMethod(new Method(env, fs, MethodId.readFileSync,   1));
		api.setOwnMethod(new Method(env, fs, MethodId.writeFileSync,  2));
		api.setOwnMethod(new Method(env, fs, MethodId.appendFileSync, 2));
		api.setOwnMethod(new Method(env, fs, MethodId.existsSync,     1));
		api.setOwnMethod(new Method(env, fs, MethodId.statSync,       1));
		api.setOwnMethod(new Method(env, fs, MethodId.mkdirSync,      1));
		api.setOwnMethod(new Method(env, fs, MethodId.rmSync,         1));
		api.setOwnMethod(new Method(env, fs, MethodId.readdirSync,    1));
		api.setOwnMethod(new Method(env, fs, MethodId.unlinkSync,     1));
		api.setOwnMethod(new Method(env, fs, MethodId.renameSync,     2));
		api.setOwnMethod(new Method(env, fs, MethodId.copyFileSync,   2));
		api.setOwnMethod(new Method(env, fs, MethodId.realpathSync,   1));
		api.setOwnMethod(new Method(env, fs, MethodId.accessSync,     1));
		// Callback-async API
		api.setOwnMethod(new Method(env, fs, MethodId.readFile,   2));
		api.setOwnMethod(new Method(env, fs, MethodId.writeFile,  3));
		api.setOwnMethod(new Method(env, fs, MethodId.appendFile, 3));
		api.setOwnMethod(new Method(env, fs, MethodId.exists,     2));
		api.setOwnMethod(new Method(env, fs, MethodId.stat,       2));
		api.setOwnMethod(new Method(env, fs, MethodId.mkdir,      2));
		api.setOwnMethod(new Method(env, fs, MethodId.rm,         2));
		api.setOwnMethod(new Method(env, fs, MethodId.readdir,    2));
		api.setOwnMethod(new Method(env, fs, MethodId.unlink,     2));
		api.setOwnMethod(new Method(env, fs, MethodId.rename,     3));
		api.setOwnMethod(new Method(env, fs, MethodId.copyFile,   3));
		api.setOwnMethod(new Method(env, fs, MethodId.realpath,   2));
		api.setOwnMethod(new Method(env, fs, MethodId.access,     2));
		setDefaultExport(api);
		setNamedExports(api);
	}

	private static enum MethodId {
		// Sync
		readFileSync,
		writeFileSync,
		appendFileSync,
		existsSync,
		statSync,
		mkdirSync,
		rmSync,
		readdirSync,
		unlinkSync,
		renameSync,
		copyFileSync,
		realpathSync,
		accessSync,
		// Callback-async
		readFile,
		writeFile,
		appendFile,
		exists,
		stat,
		mkdir,
		rm,
		readdir,
		unlink,
		rename,
		copyFile,
		realpath,
		access,
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
			switch (methodId) {
				case readFileSync -> {
					return NodeFsOps.readFile(NodeFsOps.pathOf(fs, arg(args, 0)), arg(args, 1));
				}
				case writeFileSync -> {
					NodeFsOps.writeFile(NodeFsOps.pathOf(fs, arg(args, 0)), arg(args, 1), arg(args, 2));
					return null;
				}
				case appendFileSync -> {
					NodeFsOps.appendFile(NodeFsOps.pathOf(fs, arg(args, 0)), arg(args, 1), arg(args, 2));
					return null;
				}
				case existsSync -> {
					return NodeFsOps.exists(NodeFsOps.pathOf(fs, arg(args, 0)));
				}
				case statSync -> {
					return NodeFsOps.stat(env, NodeFsOps.pathOf(fs, arg(args, 0)));
				}
				case mkdirSync -> {
					NodeFsOps.mkdir(NodeFsOps.pathOf(fs, arg(args, 0)), arg(args, 1));
					return null;
				}
				case rmSync -> {
					NodeFsOps.rm(NodeFsOps.pathOf(fs, arg(args, 0)), arg(args, 1));
					return null;
				}
				case readdirSync -> {
					return NodeFsOps.readdir(env, NodeFsOps.pathOf(fs, arg(args, 0)));
				}
				case unlinkSync -> {
					NodeFsOps.unlink(NodeFsOps.pathOf(fs, arg(args, 0)));
					return null;
				}
				case renameSync -> {
					NodeFsOps.rename(NodeFsOps.pathOf(fs, arg(args, 0)), NodeFsOps.pathOf(fs, arg(args, 1)));
					return null;
				}
				case copyFileSync -> {
					NodeFsOps.copyFile(NodeFsOps.pathOf(fs, arg(args, 0)), NodeFsOps.pathOf(fs, arg(args, 1)));
					return null;
				}
				case realpathSync -> {
					return NodeFsOps.realpath(NodeFsOps.pathOf(fs, arg(args, 0)));
				}
				case accessSync -> {
					NodeFsOps.access(NodeFsOps.pathOf(fs, arg(args, 0)));
					return null;
				}
				case readFile -> {
					// readFile(path, cb) or readFile(path, options, cb)
					int last = args.length - 1;
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					final Object options = last >= 2 ? args[1] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> NodeFsOps.readFile(path, options));
					return null;
				}
				case writeFile -> {
					// writeFile(path, data, cb) or writeFile(path, data, options, cb)
					int last = args.length - 1;
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					final Object data = arg(args, 1);
					final Object options = last >= 3 ? args[2] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.writeFile(path, data, options); return null; });
					return null;
				}
				case appendFile -> {
					int last = args.length - 1;
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					final Object data = arg(args, 1);
					final Object options = last >= 3 ? args[2] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.appendFile(path, data, options); return null; });
					return null;
				}
				case exists -> {
					// exists(path, cb) — legacy signature: cb(exists) — no error arg.
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					Callable cb = extractCallback(arg(args, 1));
					runAsyncExists(cb, () -> NodeFsOps.exists(path));
					return null;
				}
				case stat -> {
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> NodeFsOps.stat(env, path));
					return null;
				}
				case mkdir -> {
					int last = args.length - 1;
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					final Object options = last >= 2 ? args[1] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.mkdir(path, options); return null; });
					return null;
				}
				case rm -> {
					int last = args.length - 1;
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					final Object options = last >= 2 ? args[1] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.rm(path, options); return null; });
					return null;
				}
				case readdir -> {
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> NodeFsOps.readdir(env, path));
					return null;
				}
				case unlink -> {
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					Callable cb = extractCallback(arg(args, 1));
					runAsync(cb, () -> { NodeFsOps.unlink(path); return null; });
					return null;
				}
				case rename -> {
					final Path src = NodeFsOps.pathOf(fs, arg(args, 0));
					final Path dst = NodeFsOps.pathOf(fs, arg(args, 1));
					Callable cb = extractCallback(arg(args, 2));
					runAsync(cb, () -> { NodeFsOps.rename(src, dst); return null; });
					return null;
				}
				case copyFile -> {
					final Path src = NodeFsOps.pathOf(fs, arg(args, 0));
					final Path dst = NodeFsOps.pathOf(fs, arg(args, 1));
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> { NodeFsOps.copyFile(src, dst); return null; });
					return null;
				}
				case realpath -> {
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> NodeFsOps.realpath(path));
					return null;
				}
				case access -> {
					final Path path = NodeFsOps.pathOf(fs, arg(args, 0));
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> { NodeFsOps.access(path); return null; });
					return null;
				}
				default -> {
					throw new IllegalStateException();
				}
			}
		}

		private static Object arg(Object[] args, int i) {
			return (args != null && i < args.length) ? args[i] : null;
		}

		private static Callable extractCallback(Object o) {
			if (!(o instanceof Callable c) || !c.isCallable()) {
				throw RuntimeUtil.typeError("Callback must be a function");
			}
			return c;
		}

		// Runs body on the async worker pool, then queues a microtask on the
		// event loop to invoke cb(err, result) per Node convention. Uses
		// executor.asyncFunction so pendingAsync bookkeeping keeps the event
		// loop alive until the callback microtask has been queued.
		private static void runAsync(Callable cb, java.util.concurrent.Callable<Object> body) {
			JSRuntimeContext ctx = JSRuntimeContext.get();
			JSExecutor executor = ctx.getGlobalContext().getExecutor();
			executor.asyncFunction(() -> {
				Object result = null;
				Object err = null;
				try {
					result = body.call();
				} catch (JSRuntimeUncatchableException t) {
					// a stop request: not an error for the callback
					throw t;
				} catch (Exception t) {
					err = JSRuntimeException.exceptionObject(t);
				}
				final Object fErr = err;
				final Object fResult = result;
				executor.queueMicrotask(new MicroTask("fs-callback", ctx, null) {
					@Override
					public void run() {
						cb.call(null, new Object[] { fErr, fResult });
					}
				});
				return null;
			});
		}

		// exists(path, cb) — legacy Node signature invokes cb(exists) with a
		// single boolean argument, no error slot.
		private static void runAsyncExists(Callable cb, java.util.concurrent.Callable<Object> body) {
			JSRuntimeContext ctx = JSRuntimeContext.get();
			JSExecutor executor = ctx.getGlobalContext().getExecutor();
			executor.asyncFunction(() -> {
				Object result;
				try {
					result = body.call();
				} catch (JSRuntimeUncatchableException t) {
					throw t;
				} catch (Exception t) {
					result = Boolean.FALSE;
				}
				final Object fResult = result;
				executor.queueMicrotask(new MicroTask("fs.exists-callback", ctx, null) {
					@Override
					public void run() {
						cb.call(null, new Object[] { fResult });
					}
				});
				return null;
			});
		}
	}
}
