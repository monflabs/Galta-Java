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
package org.monflabs.galtajs.rt.builtins.standard.weakmap;

import java.util.function.Supplier;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertiesHolder;
import org.monflabs.galtajs.rt.util.WeakIdentityMap;


/**
 * JavaScript native Map
 */
public class BuiltinWeakMap implements PropertiesHolder {

	private WeakIdentityMap<Object, Object> map;
	private JSObjectInternal properties;

	public BuiltinWeakMap() {
		this.map = new WeakIdentityMap<>();
	}
	
	@Override
	public JSObjectInternal getPropertiesObject() {
		return properties;
	}
	@Override
	public void setPropertiesObject(JSObjectInternal properties) {
		this.properties = properties;
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new WeakMapAccessor(env);
	}

	public void delete(Object key) {
		map.remove(key);
	}

	public Object get(Object key) {
		return map.getOrDefault(key,RuntimeUtil.UNDEFINED);
	}

	public Object getOrInsert(Object key, Object defaultValue) {
		return map.getOrCreate(key, () -> defaultValue);
	}

	public Object getOrInsertComputed(Object key, Supplier<Object> defaultValue) {
		if(map.containsKey(key)) {
			return map.get(key);
		}
		// The callback may itself set the key: the spec then overwrites that
		// entry with the computed value rather than adding a second one
		Object value = defaultValue.get();
		map.put(key, value);
		return value;
	}

	public boolean has(Object key) {
		return map.containsKey(key);
	}

	public void set(Object key, Object value) {
		map.put(key, value);
	}
}
