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
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Contains helpers for callable objects.
 */
public class BaseSetter extends BaseCallableObject {

	@FunctionalInterface
	public static interface Setter {
		public boolean set(Object base, Object key, Object value);
	}

	private Setter setter;
	private Object key;

	public BaseSetter(JSEnvironment env, Object key, Setter setter) {
		super(env);
		this.key = key;
		this.setter = setter;
		String name = key instanceof Symbol sy ? "["+sy.getDescription()+"]" : key.toString();
		setOwnProperty("name","set "+name,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		setOwnProperty("length",1,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
	}

	@Override
	public boolean set(Object base, Object key, Object value) {
		// A built-in accessor runs in its own realm (JSEnvironment.enterRealm())
		Object realm = JSEnvironment.enterRealm(getEnvironment());
		try {
			return setter.set(base, key, value);
		} finally {
			JSEnvironment.exitRealm(realm);
		}
	}

	// Reached only when JS code explicitly extracts this setter function
	// (e.g. via Object.getOwnPropertyDescriptor(...).set) and calls it
	// directly - the internal property-write path calls set(base,key,value)
	// above, not call(). Standard setter-function calling convention: `this`
	// is the base, the sole argument is the new value, using the property
	// key this setter was created for.
	@Override
	public Object call(Object _this, @NonNull Object[] parameters) {
		Object value = parameters.length>=1 ? parameters[0] : RuntimeUtil.UNDEFINED;
		set(_this, key, value);
		return RuntimeUtil.UNDEFINED;
	}
}
