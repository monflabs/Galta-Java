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
package org.monflabs.galtajs.rt.builtins.standard.set.setlike;

import java.util.Iterator;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.set.BuiltinSetIterator;
import org.monflabs.galtajs.rt.protocols.iterator.JavaIterator;


// GetSetRecord (spec sec-getsetrecord): [[Size]]/[[Has]]/[[Keys]] are fetched
// and validated exactly once, in this exact order (size, then has, then
// keys) - test262's set-like-class-order.js observes this via property
// getters with logging side effects.
public class JSSetJSObject extends JSBaseSet {

	public static JSSetJSObject of(@NonNull JSEnvironment env, @NonNull JSAccessor accessor, @NonNull Object object) {
		return new JSSetJSObject(env,accessor,object);
	}

	private JSAccessor accessor;
	private Object object;

	private long size;
	private Callable hm;
	private Callable km;

	private JSSetJSObject(@NonNull JSEnvironment env, @NonNull JSAccessor accessor, @NonNull Object object) {
		super(env);
		this.accessor = accessor;
		this.object = object;

		// rawSize = Get(obj,"size"); numSize = ToNumber(rawSize) (undefined ->
		// NaN); if NaN, throw TypeError (covers a missing "size" property, a
		// BigInt size, or a non-numeric string too).
		Object rawSize = accessor.getProperty(object,"size",RuntimeUtil.UNDEFINED);
		Number n = RuntimeUtil.toNumber(env, rawSize);
		double d = n.doubleValue();
		if(Double.isNaN(d)) {
			throw RuntimeUtil.typeError("Cannot convert size {0} to a set record", rawSize);
		}
		double intSize = Double.isInfinite(d) ? d : (d<0 ? Math.ceil(d) : Math.floor(d));
		if(intSize<0) {
			throw RuntimeUtil.rangeError("Invalid set-like size {0}",intSize);
		}
		this.size = intSize>JSArray.MAX_ARRAY_SIZE ? JSArray.MAX_ARRAY_SIZE : (long)intSize;

		Object hasMethod = accessor.getProperty(object,"has",RuntimeUtil.NOT_AVAILABLE);
		if(hasMethod instanceof Callable hm && hm.isCallable()) {
			this.hm = hm;
		} else {
			throw RuntimeUtil.typeError("Missing 'has' function");
		}
		Object keysMethod = accessor.getProperty(object,"keys",RuntimeUtil.NOT_AVAILABLE);
		if(keysMethod instanceof Callable km && km.isCallable()) {
			this.km = km;
		} else {
			throw RuntimeUtil.typeError("Missing 'keys' function");
		}
	}

	@Override
	public long jsSize() {
		return size;
	}

	@Override
	public boolean jsHas(Object v) {
		return RuntimeUtil.toBoolean(getEnvironment(), hm.call(object, v));
	}

	@Override
	public Iterator<Object> jsKeys() {
		// GetKeysIterator: keys() returns the iterator directly (unlike a plain
		// iterable, its result is NOT re-wrapped via Symbol.iterator).
		Object v = km.call(object, RuntimeUtil.EMPTY_PARAMS);
		if(v instanceof Iterator it) {
			return new BuiltinSetIterator(getEnvironment(),it);
		}
		if(v instanceof JSObject jso) {
			return new BuiltinSetIterator(getEnvironment(), new JavaIterator(getEnvironment(),jso));
		}
		throw RuntimeUtil.typeError("'keys' method does not return an iterator");
	}
}
