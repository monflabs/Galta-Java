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
package org.monflabs.json.java;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.UnaryOperator;

import org.monflabs.json.JsonUtil;

/**
 * Json Array checking its content.
 * <p>
 * Every way of storing a value is checked: add(), set(), addAll(), replaceAll(), and the
 * same methods on a subList() or through a listIterator().
 */
@SuppressWarnings("serial")
public class JsonArrayAsArrayListChecked extends JsonArrayAsArrayList {
	
	public JsonArrayAsArrayListChecked() {
	}

	public JsonArrayAsArrayListChecked(int initialCapacity) {
		super(initialCapacity);
	}

	// The containers created from this one (getOrCreateObject(), put(key, lambda), deepClone()...)
	// must be checked as well
	@Override
	public JavaJsonFactoryChecked factory() {
		return JavaJsonFactoryChecked.instance;
	}

	private static <T> T check(T value) {
		JsonUtil.checkJsonValue(value);
		return value;
	}
	private static void checkAll(Collection<?> c) {
		for(Object o: c) {
			JsonUtil.checkJsonValue(o);
		}
	}

	@Override
	public final boolean add(Object nativeValue) {
		return super.add(check(nativeValue));
	}
	@Override
	public final void add(int index, Object nativeValue) {
		super.add(index,check(nativeValue));
	}
	@Override
	public final Object set(int index, Object nativeValue) {
		return super.set(index,check(nativeValue));
	}
	@Override
	public boolean addAll(Collection<? extends Object> c) {
		checkAll(c);
		return super.addAll(c);
	}
	@Override
	public boolean addAll(int index, Collection<? extends Object> c) {
		checkAll(c);
		return super.addAll(index, c);
	}
	@Override
	public void replaceAll(UnaryOperator<Object> operator) {
		super.replaceAll(v -> check(operator.apply(v)));
	}
	
	// ArrayList's sub list writes the backing array directly: wrap it
	@Override
	public List<Object> subList(int fromIndex, int toIndex) {
		return new CheckedList(super.subList(fromIndex, toIndex));
	}
	
	private static final class CheckedList extends AbstractList<Object> implements RandomAccess {
		private final List<Object> list;
		CheckedList(List<Object> list) {
			this.list = list;
		}
		@Override
		public Object get(int index) {
			return list.get(index);
		}
		@Override
		public int size() {
			return list.size();
		}
		@Override
		public Object set(int index, Object element) {
			return list.set(index, check(element));
		}
		@Override
		public void add(int index, Object element) {
			list.add(index, check(element));
		}
		@Override
		public Object remove(int index) {
			return list.remove(index);
		}
		@Override
		protected void removeRange(int fromIndex, int toIndex) {
			list.subList(fromIndex, toIndex).clear();
		}
		@Override
		public List<Object> subList(int fromIndex, int toIndex) {
			return new CheckedList(list.subList(fromIndex, toIndex));
		}
	}
}
