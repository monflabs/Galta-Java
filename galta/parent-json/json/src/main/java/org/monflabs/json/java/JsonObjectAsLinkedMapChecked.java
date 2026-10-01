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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.monflabs.json.JsonUtil;


/**
 * Check JSON object.
 * <p>
 * Every way of storing a value is checked: put(), putAll(), putIfAbsent(), replace(),
 * replaceAll(), compute*(), merge(), and Map.Entry.setValue() on the entry set, and the
 * same methods on the sequencedEntrySet() and reversed() views.
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
	
	// LinkedHashMap.entrySet() is sequencedEntrySet(): both return the checked view
	@Override
	public Set<Map.Entry<String, Object>> entrySet() {
		return sequencedEntrySet();
	}
	@Override
	public SequencedSet<Map.Entry<String, Object>> sequencedEntrySet() {
		return new CheckedEntrySet(super.sequencedEntrySet());
	}
	@Override
	public SequencedMap<String, Object> reversed() {
		return new CheckedReversedMap(super.reversed());
	}

	// An entry set view whose entries check the value given to setValue()
	private static final class CheckedEntrySet extends AbstractSet<Map.Entry<String, Object>> implements SequencedSet<Map.Entry<String, Object>> {
		private final SequencedSet<Map.Entry<String, Object>> entries;
		CheckedEntrySet(SequencedSet<Map.Entry<String, Object>> entries) {
			this.entries = entries;
		}
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
					return new CheckedEntry(it.next());
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
		@Override
		public SequencedSet<Map.Entry<String, Object>> reversed() {
			return new CheckedEntrySet(entries.reversed());
		}
		@Override
		public Map.Entry<String, Object> getFirst() {
			return new CheckedEntry(entries.getFirst());
		}
		@Override
		public Map.Entry<String, Object> getLast() {
			return new CheckedEntry(entries.getLast());
		}
		@Override
		public Map.Entry<String, Object> removeFirst() {
			return entries.removeFirst();
		}
		@Override
		public Map.Entry<String, Object> removeLast() {
			return entries.removeLast();
		}
	}
	private static final class CheckedEntry implements Map.Entry<String, Object> {
		private final Map.Entry<String, Object> e;
		CheckedEntry(Map.Entry<String, Object> e) {
			this.e = e;
		}
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
	}

	// LinkedHashMap's reversed view writes most values through this map (put(),
	// compute()...) and its entrySet() is this sequencedEntrySet() reversed, but its
	// replaceAll() writes the entries directly
	private final class CheckedReversedMap extends AbstractMap<String, Object> implements SequencedMap<String, Object> {
		private final SequencedMap<String, Object> map;
		CheckedReversedMap(SequencedMap<String, Object> map) {
			this.map = map;
		}
		@Override
		public void replaceAll(BiFunction<? super String, ? super Object, ? extends Object> function) {
			map.replaceAll((k,v) -> check(function.apply(k,v)));
		}
		@Override
		public Set<Map.Entry<String, Object>> entrySet() {
			return map.entrySet();
		}
		@Override
		public Set<String> keySet() {
			return map.keySet();
		}
		@Override
		public Collection<Object> values() {
			return map.values();
		}
		@Override
		public int size() {
			return map.size();
		}
		@Override
		public boolean containsKey(Object key) {
			return map.containsKey(key);
		}
		@Override
		public Object get(Object key) {
			return map.get(key);
		}
		@Override
		public Object put(String key, Object value) {
			return map.put(key, value);
		}
		@Override
		public Object remove(Object key) {
			return map.remove(key);
		}
		@Override
		public void clear() {
			map.clear();
		}
		@Override
		public Object putIfAbsent(String key, Object value) {
			return map.putIfAbsent(key, value);
		}
		@Override
		public Object replace(String key, Object value) {
			return map.replace(key, value);
		}
		@Override
		public boolean replace(String key, Object oldValue, Object newValue) {
			return map.replace(key, oldValue, newValue);
		}
		@Override
		public Object computeIfAbsent(String key, Function<? super String, ? extends Object> mappingFunction) {
			return map.computeIfAbsent(key, mappingFunction);
		}
		@Override
		public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
			return map.computeIfPresent(key, remappingFunction);
		}
		@Override
		public Object compute(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
			return map.compute(key, remappingFunction);
		}
		@Override
		public Object merge(String key, Object value, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
			return map.merge(key, value, remappingFunction);
		}
		@Override
		public SequencedMap<String, Object> reversed() {
			return JsonObjectAsLinkedMapChecked.this;
		}
		@Override
		public Map.Entry<String, Object> firstEntry() {
			return map.firstEntry();
		}
		@Override
		public Map.Entry<String, Object> lastEntry() {
			return map.lastEntry();
		}
		@Override
		public Map.Entry<String, Object> pollFirstEntry() {
			return map.pollFirstEntry();
		}
		@Override
		public Map.Entry<String, Object> pollLastEntry() {
			return map.pollLastEntry();
		}
		@Override
		public Object putFirst(String key, Object value) {
			return map.putFirst(key, value);
		}
		@Override
		public Object putLast(String key, Object value) {
			return map.putLast(key, value);
		}
	}
}
