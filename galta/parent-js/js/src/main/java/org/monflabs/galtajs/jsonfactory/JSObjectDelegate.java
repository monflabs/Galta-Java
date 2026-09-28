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
package org.monflabs.galtajs.jsonfactory;

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.util.iterators.Iterators;

/**
 * JSObject that delegate to another one.
 */
public interface JSObjectDelegate extends JSObject {
	
	public JSObjectInternal getMembers(boolean autoCreate);

	@Override
	public default Object getPrototype() {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.getPrototype();
		}
		return null;
	}
	@Override
	public default boolean setPrototype(Object prototype) {
		JSObject m = getMembers(true);
		return m.setPrototype(prototype);
	}
	
	@Override
	public default Object getOwnProperty(String key, Object defaultValue, Object receiver) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.getOwnProperty(key,defaultValue, receiver);
		}
		return defaultValue;
	}
	@Override
	public default Object getOwnProperty(Symbol key, Object defaultValue, Object receiver) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.getOwnProperty(key,defaultValue, receiver);
		}
		return defaultValue;
	}
	
	
	@Override
	public default boolean setOwnProperty(String key, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject m = getMembers(true);
		return m.setOwnProperty(key,value,desc,check, receiver);
	}
	@Override
	public default boolean setOwnProperty(Symbol key, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject m = getMembers(true);
		return m.setOwnProperty(key,value,desc,check, receiver);
	}
	

	@Override
	public default PropertyDescriptor getOwnPropertyDescriptor(String member) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.getOwnPropertyDescriptor(member);
		}
		return null;
	}
	@Override
	public default PropertyDescriptor getOwnPropertyDescriptor(Symbol symbol) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.getOwnPropertyDescriptor(symbol);
		}
		return null;
	}
	
	@Override
	public default JSObject getOwnPropertyDescriptors(JSObject descriptors) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.getOwnPropertyDescriptors(descriptors);
		}
		return descriptors;
	}
	
	@Override
	public default boolean deleteProperty(String key, DESC_CHECK check) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.deleteProperty(key,check);
		}
		return true;
	}
	@Override
	public default boolean deleteProperty(Symbol key, DESC_CHECK check) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.deleteProperty(key,check);
		}
		return true;
	}

	@Override
	public default Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		JSObject m = getMembers(false);
		if(m!=null) {
			return m.ownPropertyEntries(strings,symbols,enumerableOnly);
		}
		return Iterators.empty();
	}
//
//	
//	//
//	// JSObject methods
//	//
//	@Override
//	public default boolean jsHas(String member) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsHas(member);
//		}
//		return false;
//	}
//	@Override
//	public default Object jsGet(String member) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsGet(member);
//		}
//		return RuntimeUtil.UNDEFINED;
//	}
//	@Override
//	public default Object jsGet(String member, Object defaultValue) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsGet(member,defaultValue);
//		}
//		return defaultValue;
//	}
//	@Override
//	public default boolean jsPut(String member, Object value) {
//		JSObject m = getMembers(true);
//		return m.jsPut(member,value);
//	}
//	@Override
//	public default boolean jsPut(String member, Object value, PropertyDescriptor descriptor) {
//		JSObject m = getMembers(true);
//		return m.jsPut(member,value,descriptor);
//	}
//
//	@Override
//	public default boolean jsHas(Symbol member) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsHas(member);
//		}
//		return false;
//	}
//	@Override
//	public default Object jsGet(Symbol member) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsGet(member);
//		}
//		return RuntimeUtil.UNDEFINED;
//	}
//	@Override
//	public default Object jsGet(Symbol member, Object defaultValue) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsGet(member,defaultValue);
//		}
//		return defaultValue;
//	}
//	@Override
//	public default boolean jsPut(Symbol member, Object value) {
//		JSObject m = getMembers(true);
//		return m.jsPut(member,value);
//	}
//	@Override
//	public default boolean jsPut(Symbol member, Object value, PropertyDescriptor descriptor) {
//		JSObject m = getMembers(true);
//		return m.jsPut(member,value,descriptor);
//	}
//
//	@Override
//	public default Iterator<String> jsKeys(boolean enumerableOnly) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsKeys(enumerableOnly);
//		}
//		return Iterators.empty();
//	}
//	@Override
//	public default Iterator<Object> jsValues(boolean enumerableOnly) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsValues(enumerableOnly);
//		}
//		return Iterators.empty();
//	}
//	@Override
//	public default Iterator<Map.Entry<String, Object>> jsEntries(boolean enumerableOnly) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsEntries(enumerableOnly);
//		}
//		return Iterators.empty();
//	}
//
//	@Override
//	public default Iterator<Symbol> jsSymbolKeys(boolean enumerableOnly) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsSymbolKeys(enumerableOnly);
//		}
//		return Iterators.empty();
//	}
//	@Override
//	public default Iterator<Object> jsSymbolValues(boolean enumerableOnly) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsSymbolValues(enumerableOnly);
//		}
//		return Iterators.empty();
//	}
//	@Override
//	public default Iterator<Map.Entry<Symbol, Object>> jsSymbolEntries(boolean enumerableOnly) {
//		JSObject m = getMembers(false);
//		if(m!=null) {
//			return m.jsSymbolEntries(enumerableOnly);
//		}
//		return Iterators.empty();
//	}
}
