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

import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import org.monflabs.util.StringFormat;

public class JSArrayList extends JSBaseArray {

	@SuppressWarnings("unchecked")
	public static JSArrayList of(JSEnvironment env, @NonNull List<?> list) {
		return new JSArrayList(env,(List<Object>)list);
	}

	private List<Object> list;
	
	private JSArrayList(JSEnvironment env, @NonNull List<Object> list) {
		super(env);
		this.list = list;
	}

	@Override
	public long arrayLength() {
		return list.size();
	}
	@Override
	public Object arrayGet(long index, Object defaultValue) {
		int size = list.size();
		return index>=0 && index<size ? list.get((int)index) : defaultValue;
	}
	
	@Override
	public boolean arraySetLength(long size, DESC_CHECK check) {
		while(size<list.size()) {
			list.remove(list.size()-1);
		}
		while(size>list.size()) {
			list.add(null);
		}
		return true;
	}
	@Override
	public JSArray arrayAdd(Object value, DESC_CHECK check) {
		int size = list.size();
		if(size<Integer.MAX_VALUE) {
			list.add(value);
			return this;
		}
		throw new IllegalStateException(StringFormat.format("Too many iteams in JSArray {0}", getClass()));
	}
	@Override
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check) {
		int size = list.size();
		if(index==size) {
			list.add(value);
			return this;
		}
		if(index<size) {
			list.add((int)index,value);
			return this;
		}
		throw new IllegalStateException(StringFormat.format("Too many iteams in JSArray {0}", getClass()));
	}
	@Override
	public boolean arraySet(long index, Object value, DESC_CHECK check) {
		int size = list.size();
		if(index<0) index += size;
		if(index<0 || index>=Integer.MAX_VALUE) {
			return false;
		}
		while(index>=list.size()) {
			list.add(null);
		}
		list.set((int)index,value);
		return true;
	}
	@Override
	public boolean arrayDelete(long index, DESC_CHECK check) {
		int size = list.size();
		if(index<0) index += size;
		if(index<0 || index>=size) {
			return false;
		}
		list.set((int)index,RuntimeUtil.UNDEFINED);
		return true;
	}
	@Override
	public boolean arrayRemove(long index, DESC_CHECK check) {
		int size = list.size();
		if(index<0) index += size;
		if(index<0 || index>=size) {
			return false;
		}
		list.remove((int)index);
		return true;
	}
	@Override
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check) {
		// Never validates the comparator, unlike List.sort
		Object[] a = list.toArray();
		BuiltinUtil.mergeSort(a, c);
		for(int i=0; i<a.length; i++) {
			list.set(i, a[i]);
		}
	}
}
