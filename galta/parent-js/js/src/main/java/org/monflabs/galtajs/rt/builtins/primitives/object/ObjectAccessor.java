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
package org.monflabs.galtajs.rt.builtins.primitives.object;

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.library.java.JSJavaLibrary;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

public class ObjectAccessor extends JSAccessor {
	
	public ObjectAccessor(JSEnvironment env) {
		super(env);
	}
	
	// -----------------------------------------------------------------------------------
	// GaltaJS extension: get the name
	@Override
	public String getClassName(Object _this) {
		JSObject props = (JSObject) _this;
		return props.getClassName();
	}

	
	// -----------------------------------------------------------------------------------
	// [[GetPrototypeOf]]

	@Override
	public Object getPrototype(Object _this) {
		JSObject props = (JSObject) _this;
		return props.getPrototype();
	}	
	
	
	// -----------------------------------------------------------------------------------
	// [[SetPrototypeOf]]
	
	@Override
	public boolean setPrototype(Object _this, Object prototype) {
		JSObject props = (JSObject) _this;
		return props.setPrototype(prototype);
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[IsExtensible]]
	@Override
	public boolean isExtensible(Object _this) {
		JSObject props = (JSObject) _this;
		return props.isExtensible();
	}
	@Override
	public boolean isSealed(Object _this) {
		JSObject props = (JSObject) _this;
		return props.isSealed();
	}
	@Override
	public boolean isFrozen(Object _this) {
		JSObject props = (JSObject) _this;
		return props.isFrozen();
	}

	
	// -----------------------------------------------------------------------------------
	// [[PreventExtensions]]
	@Override
	public boolean preventExtensions(Object _this) {
		JSObject props = (JSObject) _this;
		return props.preventExtensions();
	}
	@Override
	public void seal(Object _this) {
		JSObject props = (JSObject) _this;
		props.seal();
	}
	@Override
	public void freeze(Object _this) {
		JSObject props = (JSObject) _this;
		props.freeze();
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[GetOwnProperty]]
	
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		JSObject props = (JSObject) _this;
		return props.getOwnPropertyDescriptor(member);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		JSObject props = (JSObject) _this;
		return props.getOwnPropertyDescriptor(index);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, Symbol symbol) {
		JSObject props = (JSObject) _this;
		return props.getOwnPropertyDescriptor(symbol);
	}
	
	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors, Object _this) {
		super.getOwnPropertyDescriptors(descriptors,_this);
		JSObject props = (JSObject) _this;
		return props.getOwnPropertyDescriptors(descriptors);
	}


	// -----------------------------------------------------------------------------------
	// [[HasProperty]]
	
	@Override
	public boolean hasProperty(Object _this, String member) {
		JSObject props = (JSObject) _this;
		return props.hasProperty(member);
	}
	@Override
	public boolean hasProperty(Object _this, long index) {
		JSObject props = (JSObject) _this;
		return props.hasProperty(index);
	}
	@Override
	public boolean hasProperty(Object _this, Symbol symbol) {
		JSObject props = (JSObject) _this;
		return props.hasProperty(symbol);
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[GetOwnProperty]]
	
	@Override
	public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
		JSObject c = (JSObject) _this;
		Object v = c.getOwnProperty(member,RuntimeUtil.NOT_AVAILABLE,receiver);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		// Should we only use the methods from the base class, or corresponding interface
		//   ex: RegExp -> JSObject
		// That will hide the implementation details
		// But how to access the Java methods from JsonObject?
		JSEnvironment env = getEnvironment();
		if(env.supportJavaNative() && member!=null&& !member.isEmpty() && member.charAt(0)=='$') {
			JSJavaLibrary lib = env.getCustomLibraries().getJavaLibrary();
			if(lib!=null) {
				// return lib.getAccessor(receiver).getMember(receiver, member, defaultValue);
				// Should we used the native class instead here, to limit to the exposed methods?
				return env.getAccessor(receiver.getClass()).getProperty(receiver, member, defaultValue);
			}
		}
		return defaultValue;
	}
	@Override
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.getOwnProperty(index,defaultValue,receiver);
	}
	@Override
	public Object getOwnProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.getOwnProperty(symbol,defaultValue,receiver);
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[Get]]

	@Override
	public Object getProperty(Object _this, String member, Object defaultValue) {
		JSObject props = (JSObject) _this;
		return props.getProperty(member,defaultValue);
	}
	@Override
	public Object getProperty(Object _this, long index, Object defaultValue) {
		JSObject props = (JSObject) _this;
		return props.getProperty(index,defaultValue);
	}
	@Override
	public Object getProperty(Object _this, Symbol symbol, Object defaultValue) {
		JSObject props = (JSObject) _this;
		return props.getProperty(symbol,defaultValue);
	}

	
	
	// -----------------------------------------------------------------------------------
	// [[Set]]
	
	@Override
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.setOwnProperty(member, value, desc, check, receiver);
	}
	@Override
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.setOwnProperty(Long.toString(index), value, desc, check, receiver);
	}
	@Override
	public boolean setOwnProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.setOwnProperty(symbol, value, desc, check, receiver);
	}

	@Override
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		JSObject c = (JSObject) _this;
		return c.setProperty(member, value, desc, check);
	}
	@Override
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		JSObject c = (JSObject) _this;
		return c.setProperty(index, value, desc, check);
	}
	@Override
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		JSObject c = (JSObject) _this;
		return c.setProperty(symbol, value, desc, check);
	}
	// Receiver-aware overloads too - the base class's default now recurses
	// into a prototype ancestor's own 6-arg setProperty(), which would reach
	// here directly (bypassing the 4-arg-forwarding overrides above) for a
	// plain-object ancestor found mid-chain, or for any direct receiver-aware
	// call (e.g. super-property assignment).
	@Override
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.setProperty(member, value, desc, check, receiver);
	}
	@Override
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.setProperty(index, value, desc, check, receiver);
	}
	@Override
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = (JSObject) _this;
		return c.setProperty(symbol, value, desc, check, receiver);
	}

	
	
	// -----------------------------------------------------------------------------------
	// [[Delete]]
	
	@Override
	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		JSObject props = (JSObject) _this;
		return props.deleteProperty(member,check);
	}
	@Override
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		JSObject props = (JSObject) _this;
		return props.deleteProperty(Long.toString(index),check);
	}
	@Override
	public boolean deleteProperty(Object _this, Symbol member, DESC_CHECK check) {
		JSObject props = (JSObject) _this;
		return props.deleteProperty(member,check);
	}
	
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		JSObject props = (JSObject) _this;
		return props.ownPropertyEntries(strings,symbols,enumerableOnly);
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[OwnPropertyKeys]]
}