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
package org.monflabs.galtajs.jsonfactory.internal;

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Wrapping an accessor and a value as a JSObject
 */
public class ObjectAccessorWrapper implements JSObject {
	
	private JSAccessor accessor;
	private Object value;
	
	public ObjectAccessorWrapper(JSAccessor accessor, Object value) {
		this.accessor = accessor;
		this.value = value;
	}
	
	public JSAccessor getAccessor() {
		return accessor;
	}
	
	@Override
	public JSEnvironment getEnvironment() {
		return accessor.getEnvironment();
	}

	public Object getValue() {
		return value;
	}

	@Override
	public Object getPrototype() {
		return accessor.getPrototype(value);
	}
	@Override
	public boolean setPrototype(Object prototype) {
		return accessor.setPrototype(value,prototype);
	}
	
	@Override
	public Object getOwnProperty(String key, Object defaultValue, Object receiver) {
		return accessor.getOwnProperty(value,key,defaultValue, receiver);
	}
	@Override
	public Object getOwnProperty(Symbol key, Object defaultValue, Object receiver) {
		return accessor.getOwnProperty(value,key,defaultValue, receiver);
	}
	
	
	@Override
	public boolean setOwnProperty(String key, Object newValue, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		// The target is the wrapped object (this.value), not the value being set
		return accessor.setOwnProperty(this.value,key,newValue,desc,check, receiver);
	}
	@Override
	public boolean setOwnProperty(Symbol key, Object newValue, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		// The target is the wrapped object (this.value), not the value being set
		return accessor.setOwnProperty(this.value,key,newValue,desc,check, receiver);
	}
	

	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(String member) {
		return accessor.getOwnPropertyDescriptor(value,member);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Symbol symbol) {
		return accessor.getOwnPropertyDescriptor(value,symbol);
	}
	
	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors) {
		return accessor.getOwnPropertyDescriptors(descriptors,value);
	}
	
	@Override
	public boolean deleteProperty(String key, DESC_CHECK check) {
		return accessor.deleteProperty(value,key,check);
	}
	@Override
	public boolean deleteProperty(Symbol key, DESC_CHECK check) {
		return accessor.deleteProperty(value,key,check);
	}

	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		return accessor.ownPropertyEntries(value,strings,symbols,enumerableOnly);
	}
}
