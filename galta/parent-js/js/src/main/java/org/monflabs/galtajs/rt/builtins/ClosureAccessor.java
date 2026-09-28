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

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

// A Closure (e.g. WithClosure) exists purely to override the `this`-binding
// used when it's CALLED directly (Closure.call() ignores its own `_this`
// argument in favor of the value it was constructed with) - it has no
// properties, prototype, or identity of its own beyond that. Every other
// observable characteristic (property access, prototype chain, [[Class]],
// etc.) must behave EXACTLY as if the wrapper didn't exist at all, i.e.
// identical to accessing the same member directly on the wrapped callable -
// confirmed via language/{statements,expressions}/{generators,arrow-function,
// function}/unscopables-with{,-in-nested-fn}.js, where a with-scope-resolved
// function value (e.g. the "assert" harness function, itself carrying static
// properties like .sameValue) gets wrapped in a WithClosure for the direct-
// call case, but a FURTHER property access off of it (`assert.sameValue`)
// previously threw "Unknown object type WithClosure" since Closure had no
// registered accessor at all.
public class ClosureAccessor extends JSAccessor {

	public ClosureAccessor(JSEnvironment env) {
		super(env);
	}

	private static Object unwrap(Object _this) {
		return ((Closure)_this).getCallable();
	}
	private JSAccessor delegate(Object _this) {
		return getEnvironment().getAccessor(unwrap(_this));
	}

	@Override
	public String getClassName(Object _this) {
		Object u = unwrap(_this);
		return delegate(_this).getClassName(u);
	}
	@Override
	public Object getPrototype(Object _this) {
		Object u = unwrap(_this);
		return delegate(_this).getPrototype(u);
	}
	@Override
	public boolean setPrototype(Object _this, Object prototype) {
		Object u = unwrap(_this);
		return delegate(_this).setPrototype(u, prototype);
	}
	@Override
	public boolean isExtensible(Object _this) {
		Object u = unwrap(_this);
		return delegate(_this).isExtensible(u);
	}
	@Override
	public boolean isSealed(Object _this) {
		Object u = unwrap(_this);
		return delegate(_this).isSealed(u);
	}
	@Override
	public boolean isFrozen(Object _this) {
		Object u = unwrap(_this);
		return delegate(_this).isFrozen(u);
	}
	@Override
	public boolean preventExtensions(Object _this) {
		Object u = unwrap(_this);
		return delegate(_this).preventExtensions(u);
	}
	@Override
	public void seal(Object _this) {
		Object u = unwrap(_this);
		delegate(_this).seal(u);
	}
	@Override
	public void freeze(Object _this) {
		Object u = unwrap(_this);
		delegate(_this).freeze(u);
	}

	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		Object u = unwrap(_this);
		return delegate(_this).getOwnPropertyDescriptor(u, member);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		Object u = unwrap(_this);
		return delegate(_this).getOwnPropertyDescriptor(u, index);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, Symbol symbol) {
		Object u = unwrap(_this);
		return delegate(_this).getOwnPropertyDescriptor(u, symbol);
	}

	@Override
	public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
		Object u = unwrap(_this);
		return delegate(_this).getOwnProperty(u, member, defaultValue, receiver==_this ? u : receiver);
	}
	@Override
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		Object u = unwrap(_this);
		return delegate(_this).getOwnProperty(u, index, defaultValue, receiver==_this ? u : receiver);
	}
	@Override
	public Object getOwnProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
		Object u = unwrap(_this);
		return delegate(_this).getOwnProperty(u, symbol, defaultValue, receiver==_this ? u : receiver);
	}

	@Override
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		Object u = unwrap(_this);
		return delegate(_this).setOwnProperty(u, member, value, desc, check, receiver==_this ? u : receiver);
	}
	@Override
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		Object u = unwrap(_this);
		return delegate(_this).setOwnProperty(u, index, value, desc, check, receiver==_this ? u : receiver);
	}
	@Override
	public boolean setOwnProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		Object u = unwrap(_this);
		return delegate(_this).setOwnProperty(u, symbol, value, desc, check, receiver==_this ? u : receiver);
	}

	@Override
	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		Object u = unwrap(_this);
		return delegate(_this).deleteProperty(u, member, check);
	}
	@Override
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		Object u = unwrap(_this);
		return delegate(_this).deleteProperty(u, index, check);
	}
	@Override
	public boolean deleteProperty(Object _this, Symbol symbol, DESC_CHECK check) {
		Object u = unwrap(_this);
		return delegate(_this).deleteProperty(u, symbol, check);
	}

	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		Object u = unwrap(_this);
		return delegate(_this).ownPropertyEntries(u, strings, symbols, enumerableOnly);
	}
}
