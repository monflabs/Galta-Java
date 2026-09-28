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
package org.monflabs.galtajs;

import java.util.function.Supplier;

import org.monflabs.util.scoped._ScopedValue;

/**
 * Context set shile executing code
 * It can be a providing a design context or a runtime context.
 */
public interface JSContext {

	// Use the ScopedValue emulation until the capability is supported by the JDK
	// Don't use it directly until it uses the proper JDK classes. Until then, use the
	// the methods below.
	// It is preview in JDK 21.
	static _ScopedValue<JSContext> CONTEXT = _ScopedValue.newInstance();

	public default <R> R with(Supplier<R> callable) {
		return _ScopedValue.where(CONTEXT, this).get(callable);
	}
	public default void run(Runnable runnable) {
		_ScopedValue.where(CONTEXT, this).run(runnable);
	}

	public static JSContext get() {
		JSContext ctx = CONTEXT.get();
		if(ctx!=null) {
			return ctx;
		}
		throw new IllegalStateException("JSRuntimeContext is not currently available");
	}
	public static JSContext getUnchecked() {
		JSContext ctx = CONTEXT.get();
		return ctx;
	}

	public JSEnvironment getEnvironment();

	public default boolean isStrictMode() {
		return getEnvironment().isStrictMode();
	}
}