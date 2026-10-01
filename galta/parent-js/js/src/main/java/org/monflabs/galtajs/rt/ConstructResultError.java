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
package org.monflabs.galtajs.rt;

/**
 * An error in the result of a class constructor's [[Construct]] (ECMA-262
 * 10.2.2 steps 10-12): the constructor returned a value that is neither an
 * object nor undefined, or a derived constructor finished without calling
 * super(). The spec throws it after the constructor's execution context is
 * gone, so it belongs to the caller's realm: the body throws this Java
 * exception, and BuiltinClassConstructor.constructObject() turns it into the
 * JavaScript error once back in the caller's context. Not catchable by
 * JavaScript code in the constructor itself, as in the spec.
 */
public final class ConstructResultError extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final boolean referenceError;

	private ConstructResultError(boolean referenceError, String message) {
		super(message, null, false, false);
		this.referenceError = referenceError;
	}

	public static ConstructResultError invalidReturnValue() {
		return new ConstructResultError(false, "Derived constructors may only return object or undefined");
	}

	public static ConstructResultError thisNotInitialized() {
		return new ConstructResultError(true, "Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
	}

	// The JavaScript error, in the current realm
	public JSRuntimeException toJavaScriptError() {
		return referenceError ? RuntimeUtil.referenceError(getMessage()) : RuntimeUtil.typeError(getMessage());
	}
}
