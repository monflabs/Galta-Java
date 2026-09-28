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
package org.monflabs.galtajs.rt.builtins.standard.weakset;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertiesHolder;
import org.monflabs.galtajs.rt.util.WeakIdentityMap;


/**
 * JavaScript WeakSet
 */
public class BuiltinWeakSet implements PropertiesHolder {
	
	private WeakIdentityMap<Object, Object> map;
	private JSObjectInternal properties;

	public BuiltinWeakSet() {
		this.map = new WeakIdentityMap<>();
	}
	
	public JSEnvironment getEnvironment() {
		return JSEnvironment.getEnvironment();
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
		return new WeakSetAccessor(env);
	}
	
	public void add(Object e) {
		map.put(e,null);
	}
	
	public boolean has(Object o) {
		return map.containsKey(o);
	}
	
	public boolean delete(Object o) {
		if(map.containsKey(o)) {
			map.remove(o);
			return true;
		}
		return false;
	}
}
