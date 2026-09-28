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
package org.monflabs.galtajs.rt.builtins.primitives;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.BaseConstructor;
import org.monflabs.galtajs.rt.builtins.BaseInternalObject;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype;

/**
 * 
 */
public abstract class BasePrimitiveConstructor extends BaseConstructor {
	
	protected BasePrimitiveConstructor(JSEnvironment env, String name, BaseInternalObject prototypeOfConstructed, int ctorLength) {
		super(env, name, prototypeOfConstructed, ctorLength);
	}

	// Only for the object ctor
	protected BasePrimitiveConstructor(JSEnvironment env, String name, BuiltinObjectPrototype proto, int ctorLength) {
		super(env, name, proto, ctorLength);
	}
	
	// Primitive and Object are the same here
	// Can be constructed with a function call or a new keyword
	@Override
	public Object call(Object _this, Object[] parameters) {
		// Make the function call equivalent to the ctor
		return constructObject(parameters);
	}

}
