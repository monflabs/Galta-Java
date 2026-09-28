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

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.monflabs.json.JsonUtil;


/**
 * Check JSON object.
 * <p>
 * Every way of storing a value is checked: put(), putAll(), putIfAbsent(), replace(),
 * replaceAll(), compute*(), merge() and Map.Entry.setValue() on the entry set.
 */
@SuppressWarnings("serial")
public class JsonObjectAsLinkedMapChecked extends JsonObjectAsLinkedMap {
	
	public JsonObjectAsLinkedMapChecked() {
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

	@Override
	public Object put(String key, Object value) {
		return super.put(key,check(value));
	}
	@Override
	public Object putIfAbsent(String key, Object value) {
		return super.putIfAbsent(key,check(value));
	}
	@Override
	public Object replace(String key, Object value) {
		return super.replace(key,check(value));
	}
	@Override
	public boolean replace(String key, Object oldValue, Object newValue) {
		return super.replace(key,oldValue,check(newValue));
	}
	@Override
	public void replaceAll(BiFunction<? super String, ? super Object, ? extends Object> function) {
		super.replaceAll((k,v) -> check(function.apply(k,v)));
	}
	@Override
	public Object computeIfAbsent(String key, Function<? super String, ? extends Object> mappingFunction) {
		return super.computeIfAbsent(key, k -> check(mappingFunction.apply(k)));
	}
	@Override
	public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
		return super.computeIfPresent(key, (k,v) -> check(remappingFunction.apply(k,v)));
	}
	@Override
	public Object compute(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
		return super.compute(key, (k,v) -> check(remappingFunction.apply(k,v)));
	}
	@Override
	public Object merge(String key, Object value, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
		return super.merge(key, check(value), (v1,v2) -> check(remappingFunction.apply(v1,v2)));
	}
	
	@Override
	public Set<Map.Entry<String, Object>> entrySet() {
		Set<Map.Entry<String, Object>> entries = super.entrySet();
		// A view whose entries check the value given to setValue()
		return new AbstractSet<Map.Entry<String, Object>>() {
			@Override
			public Iterator<Map.Entry<String, Object>> iterator() {
				Iterator<Map.Entry<String, Object>> it = entries.iterator();
				return new Iterator<Map.Entry<String, Object>>() {
					@Override
					public boolean hasNext() {
						return it.hasNext();
					}
					@Override
					public Map.Entry<String, Object> next() {
						Map.Entry<String, Object> e = it.next();
						return new Map.Entry<String, Object>() {
							@Override
							public String getKey() {
								return e.getKey();
							}
							@Override
							public Object getValue() {
								return e.getValue();
							}
							@Override
							public Object setValue(Object value) {
								return e.setValue(check(value));
							}
							@Override
							public boolean equals(Object o) {
								return e.equals(o);
							}
							@Override
							public int hashCode() {
								return e.hashCode();
							}
							@Override
							public String toString() {
								return e.toString();
							}
						};
					}
					@Override
					public void remove() {
						it.remove();
					}
				};
			}
			@Override
			public int size() {
				return entries.size();
			}
			@Override
			public boolean contains(Object o) {
				return entries.contains(o);
			}
			@Override
			public boolean remove(Object o) {
				return entries.remove(o);
			}
			@Override
			public void clear() {
				entries.clear();
			}
		};
	}
}
