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
package org.monflabs.galtajs.library.platform;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.executors.MicroTask;

/**
 * Semi JavaScript standard libraries
 *
 */
public class HostLibrary extends GlobalLibrary {

	public HostLibrary() {
	}

	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		// Timer state is per-configureStandardObjects call, i.e. per env.
		TimerState timers = new TimerState();

		// Global functions
		standardObjects.setOwnMethod(new Method(env,MethodId.setTimeout,1,timers));
		standardObjects.setOwnMethod(new Method(env,MethodId.clearTimeout,1,timers));
		standardObjects.setOwnMethod(new Method(env,MethodId.setInterval,1,timers));
		standardObjects.setOwnMethod(new Method(env,MethodId.clearInterval,1,timers));
		standardObjects.setOwnMethod(new Method(env,MethodId.queueMicrotask,1,timers));
		standardObjects.setOwnMethod(new Method(env,MethodId.atob,1,timers));
		standardObjects.setOwnMethod(new Method(env,MethodId.btoa,1,timers));
	}

	private static enum MethodId {
		setTimeout,
		clearTimeout,
		setInterval,
		clearInterval,
		queueMicrotask,
		atob,
		btoa,
	}

	// Per-environment registry of pending timers.
	private static final class TimerState {
		final AtomicInteger nextId = new AtomicInteger(1);
		final Map<Integer,TimerEntry> entries = new ConcurrentHashMap<>();
	}

	private static final class TimerEntry {
		final int id;
		final Callable callback;
		final Object[] callbackArgs;
		final boolean repeating; // setInterval, as opposed to setTimeout
		final long intervalMs;
		volatile boolean canceled;

		TimerEntry(int id, Callable callback, Object[] callbackArgs, boolean repeating, long intervalMs) {
			this.id = id;
			this.callback = callback;
			this.callbackArgs = callbackArgs;
			this.repeating = repeating;
			this.intervalMs = intervalMs;
		}
	}

	private final class Method extends BaseMethod {
		private final MethodId methodId;
		private final TimerState timers;

		private Method(JSEnvironment env, MethodId methodId, int length, TimerState timers) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
			this.timers = timers;
		}

	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId) {
	        	case setTimeout -> {
	        		return scheduleTimer(args, false);
	        	}
	        	case setInterval -> {
	        		return scheduleTimer(args, true);
	        	}
            	case clearTimeout, clearInterval -> {
            		// Per HTML spec, clearTimeout and clearInterval are interchangeable.
            		if (args.length > 0) {
            			Integer id = coerceId(args[0]);
            			if (id != null) {
            				TimerEntry e = timers.entries.remove(id);
            				if (e != null) {
            					e.canceled = true;
            				}
            			}
            		}
	        		return RuntimeUtil.UNDEFINED;
            	}
            	case queueMicrotask -> {
            		if (args.length == 0 || !(args[0] instanceof Callable cb)) {
            			throw RuntimeUtil.error("queueMicrotask requires a callable argument");
            		}
            		JSRuntimeContext ctx = JSRuntimeContext.get();
            		JSExecutor executor = ctx.getGlobalContext().getExecutor();
            		executor.queueMicrotask(new MicroTask("queueMicrotask", ctx, null) {
            			@Override
            			public void run() {
            				cb.call(null, RuntimeUtil.EMPTY_PARAMS);
            			}
            		});
            		return RuntimeUtil.UNDEFINED;
            	}
            	case atob -> {
            		if (args.length == 0) {
            			throw RuntimeUtil.error("atob requires 1 argument");
            		}
            		String encoded = RuntimeUtil.toString(getEnvironment(), args[0]);
            		return decodeBase64(encoded);
            	}
            	case btoa -> {
            		if (args.length == 0) {
            			throw RuntimeUtil.error("btoa requires 1 argument");
            		}
            		String input = RuntimeUtil.toString(getEnvironment(), args[0]);
            		return encodeBase64(input);
            	}

	            default -> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }

		private Object scheduleTimer(Object[] args, boolean repeating) {
			// Silently ignore calls with no callback: matches WHATWG "if handler
			// is null, return 0" behavior — the id 0 is never used by us and
			// cannot cancel anything.
			if (args.length == 0 || !(args[0] instanceof Callable cb)) {
				return 0;
			}
			long delayMs = args.length > 1 ? RuntimeUtil.toLong(getEnvironment(), args[1]) : 0L;
			if (delayMs < 0) {
				delayMs = 0;
			}
			Object[] extra;
			if (args.length > 2) {
				extra = new Object[args.length - 2];
				System.arraycopy(args, 2, extra, 0, extra.length);
			} else {
				extra = RuntimeUtil.EMPTY_PARAMS;
			}

			TimerEntry entry = new TimerEntry(
					timers.nextId.getAndIncrement(),
					cb,
					extra,
					repeating,
					// a 0 interval still repeats; 1ms keeps it from monopolizing
					// the event loop (browsers clamp nested timers the same way)
					repeating ? Math.max(delayMs, 1L) : 0L);
			timers.entries.put(entry.id, entry);
			schedule(entry, delayMs);
			return entry.id;
		}

		private void schedule(TimerEntry entry, long delayMs) {
			JSRuntimeContext ctx = JSRuntimeContext.get();
			JSExecutor executor = ctx.getGlobalContext().getExecutor();
			long readyAt = System.currentTimeMillis() + delayMs;
			String label = entry.repeating ? "setInterval" : "setTimeout";
			executor.queueMicrotask(new MicroTask(label, ctx, null) {
				@Override
				public void run() {
					if (entry.canceled) {
						return;
					}
					try {
						entry.callback.call(null, entry.callbackArgs);
					} finally {
						if (entry.repeating && !entry.canceled) {
							schedule(entry, entry.intervalMs);
						} else {
							timers.entries.remove(entry.id);
						}
					}
				}
			}, readyAt);
		}

		// btoa: encode a "binary string" (each char code must be 0..255) as Base64.
		private String encodeBase64(String input) {
			byte[] bytes = new byte[input.length()];
			for (int i = 0; i < input.length(); i++) {
				char c = input.charAt(i);
				if (c > 0xFF) {
					throw RuntimeUtil.error("btoa: string contains a character outside of Latin1 range");
				}
				bytes[i] = (byte) c;
			}
			return Base64.getEncoder().encodeToString(bytes);
		}

		// atob: decode a Base64 string into a "binary string" (one char per byte).
		// Follows WHATWG "forgiving-base64": strip whitespace, then tolerate
		// missing "=" padding.
		private String decodeBase64(String encoded) {
			String stripped = encoded.replaceAll("[\\t\\n\\f\\r ]", "");
			int mod = stripped.length() % 4;
			if (mod == 1) {
				throw RuntimeUtil.error("atob: invalid Base64 input");
			}
			if (mod == 2) {
				stripped = stripped + "==";
			} else if (mod == 3) {
				stripped = stripped + "=";
			}
			byte[] bytes;
			try {
				bytes = Base64.getDecoder().decode(stripped);
			} catch (IllegalArgumentException ex) {
				throw RuntimeUtil.error("atob: invalid Base64 input");
			}
			char[] chars = new char[bytes.length];
			for (int i = 0; i < bytes.length; i++) {
				chars[i] = (char) (bytes[i] & 0xFF);
			}
			return new String(chars);
		}

		private Integer coerceId(Object v) {
			if (v == null || v == RuntimeUtil.UNDEFINED) {
				return null;
			}
			if (v instanceof Integer i) {
				return i;
			}
			if (v instanceof Number n) {
				return n.intValue();
			}
			try {
				return RuntimeUtil.toInt32(getEnvironment(), v);
			} catch (Throwable ignore) {
				return null;
			}
		}
	}
}
