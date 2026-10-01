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

import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;

/**
 * A call in tail position (ECMA-262 15.10 Tail Position Calls) that has not
 * been performed yet: the function returning it has finished, so its frame is
 * gone. {@link BuiltinFunction} performs it, and the ones it produces in
 * turn, in a loop, so a chain of tail calls runs in constant Java stack.
 * Never visible to JavaScript code.
 */
public final class TailCall {

	private final BuiltinFunction function;
	private final Object thisValue;
	private final Object[] arguments;

	public TailCall(BuiltinFunction function, Object thisValue, Object[] arguments) {
		this.function = function;
		this.thisValue = thisValue;
		this.arguments = arguments;
	}

	// The TailCall for calling function in tail position, or null when the
	// call is to be performed at once (not a JavaScript function)
	public static TailCall create(Object function, Object thisValue, Object[] arguments) {
		if(function instanceof BuiltinFunction f) {
			return new TailCall(f, thisValue, arguments);
		}
		// A function bound to a "with" object as this
		if(function instanceof org.monflabs.galtajs.rt.builtins.Closure c && c.getCallable() instanceof BuiltinFunction f) {
			return new TailCall(f, c.getThis(), arguments);
		}
		return null;
	}

	public BuiltinFunction getFunction() {
		return function;
	}

	public Object getThisValue() {
		return thisValue;
	}

	public Object[] getArguments() {
		return arguments;
	}
}
