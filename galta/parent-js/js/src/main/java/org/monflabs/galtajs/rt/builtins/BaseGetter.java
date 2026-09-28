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
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Contains helpers for callable objects.
 */
public class BaseGetter extends BaseCallableObject {

	@FunctionalInterface
	public interface Getter {
		public Object get(Object base, Object key);
	}

	private Getter getter;
	private Object key;

	public BaseGetter(JSEnvironment env, Object key, Getter getter) {
		super(env);
		this.key = key;
		this.getter = getter;
		String name = key instanceof Symbol sy ? "["+sy.getDescription()+"]" : key.toString();
		setOwnProperty("name","get "+name,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		setOwnProperty("length",0,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
	}

	@Override
	public Object get(Object base, Object key) {
		return getter.get(base, key);
	}

	// Reached only when JS code explicitly extracts this getter function
	// (e.g. via Object.getOwnPropertyDescriptor(...).get) and calls it
	// directly - the internal property-read path calls get(base,key) above,
	// not call(). Standard getter-function calling convention: only `this`
	// matters, using the property key this getter was created for.
	@Override
	public Object call(Object _this, @NonNull Object[] parameters) {
		return get(_this, key);
	}
}
