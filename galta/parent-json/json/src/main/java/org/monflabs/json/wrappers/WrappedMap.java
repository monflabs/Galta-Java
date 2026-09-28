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
package org.monflabs.json.wrappers;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.monflabs.json.JsonObject;

/**
 * 
 * @author priand
 *
 */
public class WrappedMap<T extends JsonWrapper> extends AbstractMap<String,T> implements JsonWrapper {
	
	private JsonObject jsonObject;
	private Function<Object,T> factory;
	
	public WrappedMap(JsonObject jsonObject, Function<Object,T> factory) {
		this.jsonObject = jsonObject;
		this.factory = factory;
	}

	@Override
	public JsonObject wrapped() {
		return jsonObject;
	}

	
	//
	// Required AbstractMap Methods to implement
	//
    @Override
	public Set<Entry<String,T>> entrySet() {
		return new AbstractSet<Map.Entry<String,T>>() {
			@SuppressWarnings("unchecked")
			@Override
			public Iterator<Entry<String, T>> iterator() {
				Iterator<?> it = jsonObject.entrySet().iterator();
				return new JsonObjectEntryIterator<T>((Iterator<Entry<String, Object>>)it, factory);
			}
			@Override
			public int size() {
				return jsonObject.size();
			}
            @Override
            public boolean isEmpty() {
                return jsonObject.isEmpty();
            }
            @Override
            public void clear() {
            	jsonObject.clear();
            }
            @Override
            public boolean contains(Object o) {
            	// An entry (key, wrapper) of this map
            	if(o instanceof Map.Entry<?,?> e && e.getKey() instanceof String k && jsonObject.containsKey(k)) {
            		return valueEquals(jsonObject.get(k), e.getValue());
            	}
                return false;
            }
		};
	}

	
	@Override
	public int size() {
		return jsonObject.size();
    }
	
	// Compare a wrapped value with a value that may be a wrapper
	private static boolean valueEquals(Object wrapped, Object value) {
		if(value instanceof JsonWrapper w) {
			value = w.wrapped();
		}
		return java.util.Objects.equals(wrapped, value);
	}
	
	@Override
	public boolean containsValue(Object value) {
		if(value instanceof JsonWrapper w) {
			return jsonObject.containsValue(w.wrapped());
		}
		return jsonObject.containsValue(value);
	}
	
	@Override
    public boolean containsKey(Object key) {
		if(key instanceof String k) {
			return jsonObject.has(k);
		}
		return false;
	}
	
	@Override
    public T get(Object key) {
		Object o = jsonObject.get(key);
		if(o!=null) {
			return factory.apply(o);
		}
		return null;
    }
	
	@Override
    public T remove(Object key) {
		Object old = jsonObject.remove(key);
		return old!=null ? factory.apply(old) : null;
    }
	
	@Override
    public void clear() {
		jsonObject.clear();
    }
	@Override
    public Set<String> keySet() {
		return jsonObject.keySet();
    }

	
	@Override
    public T put(String key, T value) {
		Object old = jsonObject.put(key,value!=null?value.wrapped():null);
		return old!=null ? factory.apply(old) : null;
    }

	
	public static class JsonObjectEntryIterator<T extends JsonWrapper> implements Iterator<Map.Entry<String, T>> {

		private static class EntryWrapper<T extends JsonWrapper> implements Map.Entry<String, T> {

			private Map.Entry<String, Object> entry;
			private Function<Object,T> factory;

			public EntryWrapper(Map.Entry<String, Object> entry, Function<Object,T> factory) {
				this.entry = entry;
				this.factory = factory;
			}
			
			// Map.Entry contract: key.hashCode() ^ value.hashCode(), a wrapper hashing
			// like the value it wraps
			@Override
		    public int hashCode() {
				return entry.hashCode();
			}

			@Override
			public boolean equals(Object o) {
				if(this==o) {
					return true;
				}
				if(o instanceof Map.Entry<?,?> e) {
					return java.util.Objects.equals(getKey(), e.getKey()) && valueEquals(entry.getValue(), e.getValue());
				}
				return false;
			}

			@Override
			public String getKey() {
				return entry.getKey();
			}

			@Override
			public T getValue() {
				// Like WrappedMap.get(): a null value is not wrapped
				Object v = entry.getValue();
				return v!=null ? factory.apply(v) : null;
			}

			@Override
			public T setValue(T value) {
				T old = getValue();
				if(value!=null) {
					entry.setValue(value.wrapped());
				} else {
					entry.setValue(null);
				}
				return old;
			}

		}

		private Iterator<Map.Entry<String, Object>> it;
		private Function<Object,T> factory;

		public JsonObjectEntryIterator(Iterator<Map.Entry<String, Object>> it, Function<Object,T> factory) {
			this.it = it;
			this.factory = factory;
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public Map.Entry<String, T> next() {
			return new EntryWrapper<T>(it.next(), factory);
		}

		@Override
		public void remove() {
			it.remove();
		}
	}
}
