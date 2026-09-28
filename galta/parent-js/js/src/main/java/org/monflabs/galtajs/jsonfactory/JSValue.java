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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.iterators.Iterators;


/**
 * Helper to deal with JavaScript values.
 * This is just a helper to manipulate an untyped value, not meant to be stored anywhere.
 *
 * -> could be a Java record!
 */
public class JSValue {
	
	public static JSValue of(@NonNull JSEnvironment env, Object v) {
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		return of(ctx,v);
	}
	public static JSValue of(@NonNull JSGlobalContext context, Object v) {
		if(v instanceof CharSequence) {
			v = v.toString();
		}
		return new JSValue(context, context.getEnvironment().getAccessor(v), v);
	}


	private JSGlobalContext context;
	private JSAccessor acc;
	private Object value;

    protected JSValue(JSGlobalContext context, JSAccessor acc, Object value) {
    	this.context = context;
    	this.acc = acc;
    	this.value = value;
    }

	public JSGlobalContext getContext() {
		return context;
	}
	
	public JSEnvironment getEnvironment() {
		return context.getEnvironment();
	}

	public JSAccessor getAccessor() {
		return acc;
	}
	
	protected JSValue of(JSAccessor a, Object v) {
		if(v instanceof CharSequence) {
			v = v.toString();
		}
		return new JSValue(context, a, v);
	}
	
	public JSValue of(Object v) {
		return of(getEnvironment().getAccessor(v),v);
	}
	
	public void checkNullOrUndefined(Object v) {
		if(v==null) {
			throw new JSException(null,"Value is null");
		}
		if(v==RuntimeUtil.UNDEFINED) {
			throw new JSException(null,"Value is undefined");
		}
	}

	public boolean isPrimitiveValue() {
		return RuntimeUtil.isPrimitiveValue(getEnvironment(), value);
	}
	
	//
	// Checking value type
	//
	
	public boolean isNull() {
		return value==null;
	}
	public boolean isUndefined() {
		return value==RuntimeUtil.UNDEFINED;
	}
	public boolean isNullOrUndefined() {
		return value==null || value==RuntimeUtil.UNDEFINED;
	}

	public boolean isBoolean() {
		return value instanceof Boolean;
	}
	public boolean isNumber() {
		return value instanceof Number;
	}
	public boolean isString() {
		return value instanceof String;
	}
	public boolean isObject() {
		return value instanceof JSObjectInternal;
	}
	public boolean isArray() {
		return value instanceof JSArray;
	}
	public boolean isSymbol() {
		return value instanceof Symbol;
	}
	
	
	//
	// Accessing the value
	//
	
	public Object value() {
		return value;
	}
	
	public boolean booleanValue() {
		if(value instanceof Boolean b) {
			return b;
		}
		throw new JSException(null, "Value '{0}' is not a boolean", RuntimeUtil.objectTypeName(getEnvironment(), value));
	}
	public Number numberValue() {
		if(value instanceof Number n) {
			return n;
		}
		throw new JSException(null, "Value '{0}' is not a number", RuntimeUtil.objectTypeName(getEnvironment(), value));
	}
	public int intValue() {
		return numberValue().intValue();
	}
	public long longValue() {
		return numberValue().longValue();
	}
	public double doubleValue() {
		return numberValue().longValue();
	}
	public BigInteger bigIntValue() {
		return JsonUtil.toBigInteger(numberValue());
	}
	public BigDecimal bigDecimalValue() {
		return JsonUtil.toBigDecimal(numberValue());
	}
	public String stringValue() {
		if(value instanceof String s) {
			return s;
		}
		throw new JSException(null, "Value '{0}' is not a string", RuntimeUtil.objectTypeName(getEnvironment(), value));
	}
	public JSObject objectValue() {
		if(value instanceof JSObjectInternal o) {
			return o;
		}
		throw new JSException(null, "Value '{0}' is not a JSObject", RuntimeUtil.objectTypeName(getEnvironment(), value));
	}
	public JSArray arrayValue() {
		if(value instanceof JSArray a) {
			return a;
		}
		throw new JSException(null, "Value '{0}' is not a JSArray", RuntimeUtil.objectTypeName(getEnvironment(), value));
	}
	public Symbol symbolValue() {
		if(value instanceof Symbol s) {
			return s;
		}
		throw new JSException(null, "Value '{0}' is not a Symbol", RuntimeUtil.objectTypeName(getEnvironment(), value));
	}

	@Override
	public String toString() {
		return context.with( () -> super.toString() );
	}
	public Number toNumber() {
		return RuntimeUtil.toNumber(getEnvironment(), value);
	}
	public boolean toBoolean() {
		return RuntimeUtil.toBoolean(getEnvironment(), value);
	}
	
	
	//
	// Accessing members
	//
	
	public JSValue get(Object member) {
		return context.with( () -> {
			checkNullOrUndefined(value);
			return of(acc.getProperty(value, member,RuntimeUtil.UNDEFINED));
		});
	}
	public JSValue get(Object... members) {
		return context.with( () -> {
			Object v = value;
			JSAccessor a = acc;
			for(int i=0; i<members.length; i++) {
				checkNullOrUndefined(v);
				v = a.getProperty(v, members[i], RuntimeUtil.UNDEFINED);
				if(v==RuntimeUtil.UNDEFINED) {
					return of(RuntimeUtil.UNDEFINED);
				}
				a = getEnvironment().getAccessor(v);
			}
			return of(v);
		});
	}	
	public JSValue getOrDefault(Object member, Object defaultValue) {
		return context.with( () -> {
			checkNullOrUndefined(value);
			return of(acc.getProperty(value, member, defaultValue));
		});
	}
	
	
	//
	// Updating a member
	//
	
	public JSValue put(Object member, Object v) {
		return context.with( () -> {
			checkNullOrUndefined(value);
			acc.setProperty(value, member, resolveValue(v), null, DESC_CHECK.CHECK);
			return this;
		});
	}
	private Object resolveValue(Object v) {
		if(v instanceof JSValue jv) {
			return jv.value();
		}
		return v;
	}

	
	//
	// Calling a function
	//
	
	public final JSValue call() {
		return _call(RuntimeUtil.EMPTY_PARAMS);
	}
	public final JSValue call(Object p1) {
		return _call(new Object[] {resolveValue(p1)});
	}
	public final JSValue call(Object p1, Object p2) {
		return _call(new Object[] {resolveValue(p1),resolveValue(p2)});
	}
	public final JSValue call(Object p1, Object p2, Object p3) {
		return _call(new Object[] {resolveValue(p1),resolveValue(p2),resolveValue(p3)});
	}
	public final JSValue call(Object p1, Object p2, Object p3, Object p4) {
		return _call(new Object[] {resolveValue(p1),resolveValue(p2),resolveValue(p3),resolveValue(p4)});
	}
	public final JSValue call(Object p1, Object p2, Object p3, Object p4,Object p5) {
		return _call(new Object[] {resolveValue(p1),resolveValue(p2),resolveValue(p3),resolveValue(p4),resolveValue(p5)});
	}
	public final JSValue call(Object... parameters) {
		Object[] raw = new Object[parameters.length];
		for(int i=0; i<raw.length; i++) {
			raw[i] = resolveValue(parameters[i]);
		}
		return _call(raw);
	}

	protected JSValue _call(Object... parameters) {
		return context.with( () -> {
			if(value instanceof Callable c) {
				return of(c.call(null, parameters));
			}
			throw new JSException(null, "Value '{0}' is not a Callable", RuntimeUtil.objectTypeName(getEnvironment(), value));
		});
	}

	
	//
	// Calling a method
	//
	
	public final JSValue callThis(Object _this) {
		return callThis(_this, RuntimeUtil.EMPTY_PARAMS);
	}
	public final JSValue callThis(Object _this, Object p1) {
		return _callThis(_this, new Object[] {resolveValue(p1)});
	}
	public final JSValue callThis(Object _this, Object p1, Object p2) {
		return _callThis(_this, new Object[] {resolveValue(p1),resolveValue(p2)});
	}
	public final JSValue callThis(Object _this, Object p1, Object p2, Object p3) {
		return _callThis(_this, new Object[] {resolveValue(p1),resolveValue(p2),resolveValue(p3)});
	}
	public final JSValue callThis(Object _this, Object p1, Object p2, Object p3, Object p4) {
		return _callThis(_this, new Object[] {resolveValue(p1),resolveValue(p2),resolveValue(p3),resolveValue(p4)});
	}
	public final JSValue callThis(Object _this, Object p1, Object p2, Object p3, Object p4,Object p5) {
		return _callThis(_this, new Object[] {resolveValue(p1),resolveValue(p2),resolveValue(p3),resolveValue(p4),resolveValue(p5)});
	}
	public final JSValue callThis(Object _this, Object... parameters) {
		Object[] raw = new Object[parameters.length];
		for(int i=0; i<raw.length; i++) {
			raw[i] = resolveValue(parameters[i]);
		}
		return _callThis(_this, raw);
	}
	protected JSValue _callThis(Object _this, Object... parameters) {
		return context.with( () -> {
			if(value instanceof Callable c) {
				return of(c.call(_this, parameters));
			}
			throw new JSException(null, "Value '{0}' is not a Callable", RuntimeUtil.objectTypeName(getEnvironment(), value));
		});
	}
	
	
	//
	// Iterators
	//
	public Iterator<Map.Entry<Object,JSValue>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		Iterator<Map.Entry<Object,Object>> it = acc.ownPropertyEntries(value, strings, symbols, enumerableOnly);
		return Iterators.map(it, (e) -> JSAccessor.newEntry(e.getKey(), of(e.getValue())));
	}
	public Iterator<Map.Entry<String, JSValue>> ownStringEntries(boolean enumerableOnly) {
		Iterator<Map.Entry<String, Object>> it = acc.ownStringEntries(value, enumerableOnly);
		return Iterators.map(it, (e) -> JSAccessor.newEntry(e.getKey(), of(e.getValue())));
	}
	public Iterator<Map.Entry<Symbol, JSValue>> ownSymbolEntries(boolean enumerableOnly) {
		Iterator<Map.Entry<Symbol, Object>> it = acc.ownSymbolEntries(value, enumerableOnly);
		return Iterators.map(it, (e) -> JSAccessor.newEntry(e.getKey(), of(e.getValue())));
	}
}
