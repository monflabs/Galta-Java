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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.JSNativeModule;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
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
		super(env, descriptor);
		JSObject api = JSObject.create(env);
		// Sync API
		api.setOwnMethod(new Method(env, MethodId.readFileSync,   1));
		api.setOwnMethod(new Method(env, MethodId.writeFileSync,  2));
		api.setOwnMethod(new Method(env, MethodId.appendFileSync, 2));
		api.setOwnMethod(new Method(env, MethodId.existsSync,     1));
		api.setOwnMethod(new Method(env, MethodId.statSync,       1));
		api.setOwnMethod(new Method(env, MethodId.mkdirSync,      1));
		api.setOwnMethod(new Method(env, MethodId.rmSync,         1));
		api.setOwnMethod(new Method(env, MethodId.readdirSync,    1));
		api.setOwnMethod(new Method(env, MethodId.unlinkSync,     1));
		api.setOwnMethod(new Method(env, MethodId.renameSync,     2));
		api.setOwnMethod(new Method(env, MethodId.copyFileSync,   2));
		api.setOwnMethod(new Method(env, MethodId.realpathSync,   1));
		api.setOwnMethod(new Method(env, MethodId.accessSync,     1));
		// Callback-async API
		api.setOwnMethod(new Method(env, MethodId.readFile,   2));
		api.setOwnMethod(new Method(env, MethodId.writeFile,  3));
		api.setOwnMethod(new Method(env, MethodId.appendFile, 3));
		api.setOwnMethod(new Method(env, MethodId.exists,     2));
		api.setOwnMethod(new Method(env, MethodId.stat,       2));
		api.setOwnMethod(new Method(env, MethodId.mkdir,      2));
		api.setOwnMethod(new Method(env, MethodId.rm,         2));
		api.setOwnMethod(new Method(env, MethodId.readdir,    2));
		api.setOwnMethod(new Method(env, MethodId.unlink,     2));
		api.setOwnMethod(new Method(env, MethodId.rename,     3));
		api.setOwnMethod(new Method(env, MethodId.copyFile,   3));
		api.setOwnMethod(new Method(env, MethodId.realpath,   2));
		api.setOwnMethod(new Method(env, MethodId.access,     2));
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

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env, methodId.name(), length);
			this.env = env;
			this.methodId = methodId;
		}

		@Override
		public Object call(final Object obj, final Object[] args) {
			switch (methodId) {
				case readFileSync -> {
					return NodeFsOps.readFile(NodeFsOps.pathOf(arg(args, 0)), arg(args, 1));
				}
				case writeFileSync -> {
					NodeFsOps.writeFile(NodeFsOps.pathOf(arg(args, 0)), arg(args, 1), arg(args, 2));
					return null;
				}
				case appendFileSync -> {
					NodeFsOps.appendFile(NodeFsOps.pathOf(arg(args, 0)), arg(args, 1), arg(args, 2));
					return null;
				}
				case existsSync -> {
					return NodeFsOps.exists(NodeFsOps.pathOf(arg(args, 0)));
				}
				case statSync -> {
					return NodeFsOps.stat(env, NodeFsOps.pathOf(arg(args, 0)));
				}
				case mkdirSync -> {
					NodeFsOps.mkdir(NodeFsOps.pathOf(arg(args, 0)), arg(args, 1));
					return null;
				}
				case rmSync -> {
					NodeFsOps.rm(NodeFsOps.pathOf(arg(args, 0)), arg(args, 1));
					return null;
				}
				case readdirSync -> {
					return NodeFsOps.readdir(env, NodeFsOps.pathOf(arg(args, 0)));
				}
				case unlinkSync -> {
					NodeFsOps.unlink(NodeFsOps.pathOf(arg(args, 0)));
					return null;
				}
				case renameSync -> {
					NodeFsOps.rename(NodeFsOps.pathOf(arg(args, 0)), NodeFsOps.pathOf(arg(args, 1)));
					return null;
				}
				case copyFileSync -> {
					NodeFsOps.copyFile(NodeFsOps.pathOf(arg(args, 0)), NodeFsOps.pathOf(arg(args, 1)));
					return null;
				}
				case realpathSync -> {
					return NodeFsOps.realpath(NodeFsOps.pathOf(arg(args, 0)));
				}
				case accessSync -> {
					NodeFsOps.access(NodeFsOps.pathOf(arg(args, 0)));
					return null;
				}
				case readFile -> {
					// readFile(path, cb) or readFile(path, options, cb)
					int last = args.length - 1;
					final Object path = arg(args, 0);
					final Object options = last >= 2 ? args[1] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> NodeFsOps.readFile(NodeFsOps.pathOf(path), options));
					return null;
				}
				case writeFile -> {
					// writeFile(path, data, cb) or writeFile(path, data, options, cb)
					int last = args.length - 1;
					final Object path = arg(args, 0);
					final Object data = arg(args, 1);
					final Object options = last >= 3 ? args[2] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.writeFile(NodeFsOps.pathOf(path), data, options); return null; });
					return null;
				}
				case appendFile -> {
					int last = args.length - 1;
					final Object path = arg(args, 0);
					final Object data = arg(args, 1);
					final Object options = last >= 3 ? args[2] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.appendFile(NodeFsOps.pathOf(path), data, options); return null; });
					return null;
				}
				case exists -> {
					// exists(path, cb) — legacy signature: cb(exists) — no error arg.
					final Object path = arg(args, 0);
					Callable cb = extractCallback(arg(args, 1));
					runAsyncExists(cb, () -> NodeFsOps.exists(NodeFsOps.pathOf(path)));
					return null;
				}
				case stat -> {
					final Object path = arg(args, 0);
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> NodeFsOps.stat(env, NodeFsOps.pathOf(path)));
					return null;
				}
				case mkdir -> {
					int last = args.length - 1;
					final Object path = arg(args, 0);
					final Object options = last >= 2 ? args[1] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.mkdir(NodeFsOps.pathOf(path), options); return null; });
					return null;
				}
				case rm -> {
					int last = args.length - 1;
					final Object path = arg(args, 0);
					final Object options = last >= 2 ? args[1] : null;
					Callable cb = extractCallback(arg(args, last));
					runAsync(cb, () -> { NodeFsOps.rm(NodeFsOps.pathOf(path), options); return null; });
					return null;
				}
				case readdir -> {
					final Object path = arg(args, 0);
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> NodeFsOps.readdir(env, NodeFsOps.pathOf(path)));
					return null;
				}
				case unlink -> {
					final Object path = arg(args, 0);
					Callable cb = extractCallback(arg(args, 1));
					runAsync(cb, () -> { NodeFsOps.unlink(NodeFsOps.pathOf(path)); return null; });
					return null;
				}
				case rename -> {
					final Object src = arg(args, 0);
					final Object dst = arg(args, 1);
					Callable cb = extractCallback(arg(args, 2));
					runAsync(cb, () -> { NodeFsOps.rename(NodeFsOps.pathOf(src), NodeFsOps.pathOf(dst)); return null; });
					return null;
				}
				case copyFile -> {
					final Object src = arg(args, 0);
					final Object dst = arg(args, 1);
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> { NodeFsOps.copyFile(NodeFsOps.pathOf(src), NodeFsOps.pathOf(dst)); return null; });
					return null;
				}
				case realpath -> {
					final Object path = arg(args, 0);
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> NodeFsOps.realpath(NodeFsOps.pathOf(path)));
					return null;
				}
				case access -> {
					final Object path = arg(args, 0);
					Callable cb = extractCallback(arg(args, args.length - 1));
					runAsync(cb, () -> { NodeFsOps.access(NodeFsOps.pathOf(path)); return null; });
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
				} catch (Throwable t) {
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
				} catch (Throwable t) {
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
