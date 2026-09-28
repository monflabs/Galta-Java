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
package org.monflabs.galtajs.rt.builtins;

/**
 * Constructor tag.
 */
public interface Constructor extends Callable {
	
	public static final String CONSTRUCTOR = "constructor";
	public static final String PROTOTYPE = "prototype";

	public String getClassName();

	public default Constructor getSuperClass() {
		return null;
	}

	// Spec IsConstructor(argument): whether this object genuinely has a
	// [[Construct]] internal method - distinct from merely implementing this
	// Java interface, which some implementers (e.g. a native, non-
	// constructible builtin function, or a Proxy wrapping a non-constructor
	// target) do unconditionally for dispatch convenience while still
	// needing to report false here (Reflect.construct's target/newTarget
	// validation, isConstructor(f)-style detection, etc.).
	public default boolean isConstructor() {
		return true;
	}


	public default Object constructObject(Object[] parameters) {
		Object o = constructObject(parameters, this);
		//if(RuntimeUtil.isObject(getEnvironment(),o) || RuntimeUtil.isUndefined(o)) {
			return o;
		//}
		//throw RuntimeUtil.typeError("Constructor must return an object or undefined ");
	}
	public Object constructObject(Object[] parameters, Constructor topConstructor);

	public Object constructArray(int dimensions, long size);
}
