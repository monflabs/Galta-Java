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

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Undefined accessor.
 */
public class UndefinedAccessor extends JSAccessor {
	
	public UndefinedAccessor(JSEnvironment env) {
		super(env);
	}

	
	// [[GetPrototypeOf]]
	@Override
	public Object getPrototype(Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}	
	
	// [[SetPrototypeOf]]
	@Override
	public boolean setPrototype(Object _this, Object prototype) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	
	// [[IsExtensible]]
	@Override
	public boolean isExtensible(Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	@Override
	public boolean isSealed(Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	@Override
	public boolean isFrozen(Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}

	// [[PreventExtensions]]
	@Override
	public boolean preventExtensions(Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	@Override
	public void seal(Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	@Override
	public void freeze(Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	
	// [[GetOwnProperty]]
	@Override
	public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
		throw RuntimeUtil.typeError("Left part of member {0} is undefined", member);
	}
	@Override
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		throw RuntimeUtil.typeError("Left part of index {0} is undefined", index);
	}
	@Override
	public Object getOwnProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
		throw RuntimeUtil.typeError("Left part of symbol {0} is undefined", symbol);
	}
	
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		throw RuntimeUtil.typeError("Left part of member {0} is undefined", member);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		throw RuntimeUtil.typeError("Left part of index {0} is undefined", index);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, Symbol symbol) {
		throw RuntimeUtil.typeError("Left part of symbol {0} is undefined", symbol);
	}
	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors, Object _this) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	
	// Protected, so the value is first resolved by the method above
	// These are the ones to be overridden by the subclasses
	@Override
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		throw RuntimeUtil.typeError("Left part of member {0} is undefined", member);
	}
	@Override
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		throw RuntimeUtil.typeError("Left part of index {0} is undefined", index);
	}
	@Override
	public boolean setOwnProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		throw RuntimeUtil.typeError("Left part of symbol {0} is undefined", symbol);
	}
	
	// [[HasProperty]]
	@Override
	public boolean hasProperty(Object _this, String key) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	@Override
	public boolean hasProperty(Object _this, long index) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	@Override
	public boolean hasProperty(Object _this, Symbol symbol) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	
	// [[Get]]
	@Override
	public Object getProperty(Object _this, String member, Object defaultValue) {
		throw RuntimeUtil.typeError("Left part of member {0} is undefined", member);
	}
	@Override
	public Object getProperty(Object _this, long index, Object defaultValue) {
		throw RuntimeUtil.typeError("Left part of index {0} is undefined", index);
	}
	@Override
	public Object getProperty(Object _this, Symbol symbol, Object defaultValue) {
		throw RuntimeUtil.typeError("Left part of symbol {0} is undefined", symbol);
	}

	// [[Set]]
	@Override
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		throw RuntimeUtil.typeError("Left part of member {0} is undefined", member);
	}
	@Override
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		throw RuntimeUtil.typeError("Left part of index {0} is undefined", index);
	}
	@Override
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		throw RuntimeUtil.typeError("Left part of symbol {0} is undefined", symbol);
	}
	// Receiver-aware overloads too - the base class's default now recurses
	// into a prototype ancestor's own 6-arg setProperty(), which would reach
	// here directly (bypassing the 5-arg overrides above) if undefined were
	// ever encountered mid-chain.
	@Override
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		throw RuntimeUtil.typeError("Left part of member {0} is undefined", member);
	}
	@Override
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		throw RuntimeUtil.typeError("Left part of index {0} is undefined", index);
	}
	@Override
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		throw RuntimeUtil.typeError("Left part of symbol {0} is undefined", symbol);
	}
	
	
	// [[Delete]]
	@Override
	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		throw RuntimeUtil.typeError("Left part of member {0} is undefined", member);
	}
	@Override
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		throw RuntimeUtil.typeError("Left part of index {0} is undefined", index);
	}
	@Override
	public boolean deleteProperty(Object _this, Symbol symbol, DESC_CHECK check) {
		throw RuntimeUtil.typeError("Left part of symbol {0} is undefined", symbol);
	}
	
	// [[OwnPropertyKeys]]
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		throw RuntimeUtil.typeError("Object is undefined");
	}
	
	// GaltaJS extension: get the name
	@Override
	public String getClassName(Object _this) {
		return "undefined";
	}
}
