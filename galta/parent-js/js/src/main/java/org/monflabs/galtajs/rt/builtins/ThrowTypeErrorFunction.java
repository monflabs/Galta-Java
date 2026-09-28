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

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * %ThrowTypeError% (9.2.9.1): a single, frozen, non-extensible, anonymous
 * (length 0, name "") function object per realm - installed as BOTH the
 * getter and setter wherever a "poisoned" accessor throws on access (an
 * unmapped Arguments object's own "callee", per Arguments.java). Every use
 * site must share this EXACT object (test262's unique-per-realm-*.js), so
 * this is looked up via JSEnvironment.getRegisteredPrototype()/
 * registerPrototype()'s existing per-environment singleton mechanism
 * (reused here even though this isn't a prototype - just a convenient
 * existing Class-keyed singleton registry).
 */
public final class ThrowTypeErrorFunction extends BaseCallableObject {

	public static ThrowTypeErrorFunction get(JSEnvironment env) {
		ThrowTypeErrorFunction f = (ThrowTypeErrorFunction)env.getRegisteredPrototype(ThrowTypeErrorFunction.class);
		if(f==null) {
			f = new ThrowTypeErrorFunction(env);
			env.registerPrototype(ThrowTypeErrorFunction.class,f);
		}
		return f;
	}

	private ThrowTypeErrorFunction(JSEnvironment env) {
		super(env);
		// Property insertion order matters (property-order.js): "length"
		// before "name", both { writable:false, enumerable:false,
		// configurable:false }.
		setOwnProperty("length",0,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("name","",PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		preventExtensions();
	}

	@Override
	public Object call(Object _this, @NonNull Object[] parameters) {
		throw RuntimeUtil.typeError("'callee', 'caller', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them");
	}
}
