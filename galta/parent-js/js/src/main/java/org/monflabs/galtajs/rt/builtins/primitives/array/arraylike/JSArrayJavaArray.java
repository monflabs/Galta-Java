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
package org.monflabs.galtajs.rt.builtins.primitives.array.arraylike;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import org.monflabs.util.StringFormat;

public class JSArrayJavaArray extends JSBaseArray {

	public static JSArrayJavaArray of(JSEnvironment env, @NonNull Object array) {
		return new JSArrayJavaArray(env,array);
	}

	private Object array;
	private int length;
	
	private JSArrayJavaArray(JSEnvironment env, @NonNull Object array) {
		super(env);
		this.array = array;
		this.length = Array.getLength(array);
	}

	@Override
	public long arrayLength() {
    	return length;
	}
	@Override
	public Object arrayGet(long index, Object defaultValue) {
		// We must use Array methods as the array may not contain objects (int[], ...)
		return index>=0 && index<length ? Array.get(array, (int)index) : defaultValue;
	}
	
	@Override
	public boolean arraySetLength(long size, DESC_CHECK check) {
		throw new IllegalArgumentException();
	}
	@Override
	public JSArray arrayAdd(Object value, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Cannot set property on object of type {0}", getClass()));
	}
	@Override
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Cannot set property on object of type {0}", getClass()));
	}
	@Override
	public boolean arraySet(long index, Object value, DESC_CHECK check) {
		if(index>=0 && index<length) {
			Array.set(array,(int)index,value);
			return true;
		}
		return false;
	}
	@Override
	public boolean deleteProperty(long index, DESC_CHECK check) {
		return false;
	}
	@Override
	public boolean arrayDelete(long index, DESC_CHECK check) {
		// Only an Object[] slot can hold undefined; a primitive slot cannot be deleted
		if(index>=0 && index<length && array instanceof Object[]) {
			Array.set(array,(int)index,RuntimeUtil.UNDEFINED);
			return true;
		}
		return false;
	}
	@Override
	public boolean arrayRemove(long index, DESC_CHECK check) {
		return false;
	}
	@Override
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check) {
		// Sort through boxed values so the JS comparator (or the default JS
		// order the caller passes in) applies to every array type alike
		int n = (int)length;
		Object[] boxed = new Object[n];
		for(int i=0; i<n; i++) {
			boxed[i] = Array.get(array,i);
		}
		if(c!=null) {
			// Never validates the comparator, unlike Arrays.sort
			BuiltinUtil.mergeSort(boxed, c);
		} else {
			Arrays.sort(boxed);
		}
		for(int i=0; i<n; i++) {
			Array.set(array,i,boxed[i]);
		}
	}
}
