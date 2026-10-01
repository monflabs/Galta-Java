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
package org.monflabs.json.jsonpath;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.monflabs.util.cache.CacheProvider;

/**
 * Simple JsonPath Factory.
 * <p>
 * The compiled paths are cached (a concurrent, bounded cache: lookups don't lock).
 * {@link #get()} is lenient, {@link #strict()} only accepts RFC 9535 queries.
 * 
 * @author priand
 */
public class JsonPathFactory {
	
	private static JsonPathFactory instance = new JsonPathFactory();
	private static JsonPathFactory strictInstance = new JsonPathFactory(512, true);
	public static JsonPathFactory get() {
		return instance;
	}
	/**
	 * A shared factory parsing the paths in strict RFC 9535 mode.
	 */
	public static JsonPathFactory strict() {
		return strictInstance;
	}

	private static final int DEFAULT_CACHE_SIZE = 512;
	
	private CacheProvider<String, JsonPath> cache;
	private final boolean strict;
	
	public JsonPathFactory() {
		this(DEFAULT_CACHE_SIZE);
	}
	public JsonPathFactory(int cacheSize) {
		this(cacheSize, false);
	}
	public JsonPathFactory(int cacheSize, boolean strict) {
		this.strict = strict;
		if(cacheSize>0) {
			this.cache = new ConcurrentCache<>(cacheSize);
		}
	}
	
	public boolean isStrict() {
		return strict;
	}
	
	public CacheProvider<String, JsonPath> getCacheProvider() {
		return cache;
	}

	public void setCacheProvider(CacheProvider<String, JsonPath> cache) {
		this.cache = cache;
	}

	public JsonPath getJsonPath(String jsonPath) {
		CacheProvider<String, JsonPath> c = cache;
		if(c!=null && jsonPath!=null) {
			return c.get(jsonPath, (path) -> createJsonPath(path) );
		}
		return createJsonPath(jsonPath);
	}
	
	public JsonPath createJsonPath(String jsonPath) {
		return JsonPath.parse(jsonPath,0,false,strict);
	}
	
	public JsonPath getPartialJsonPath(String jsonPath, int start) {
		return JsonPath.parse(jsonPath,start,true,strict);
	}
	
	/**
	 * A concurrent cache with an approximate bound: when it is full, an arbitrary quarter
	 * of the entries is evicted. Lookups never lock.
	 */
	static final class ConcurrentCache<K,V> implements CacheProvider<K,V> {
		private final ConcurrentHashMap<K,V> map = new ConcurrentHashMap<>();
		private final int capacity;
		ConcurrentCache(int capacity) {
			this.capacity = capacity;
		}
		@Override
		public boolean contains(K key) {
			return map.containsKey(key);
		}
		@Override
		public V get(K key) {
			return map.get(key);
		}
		@Override
		public void put(K key, V value) {
			// A null value (negative cache entry) is not supported by ConcurrentHashMap
			if(value==null) {
				map.remove(key);
				return;
			}
			if(map.size()>=capacity) {
				evict();
			}
			map.put(key, value);
		}
		@Override
		public void clear() {
			map.clear();
		}
		@Override
		public void remove(K key) {
			map.remove(key);
		}
		@Override
		public V get(K key, Function<K,V> factory) {
			V v = map.get(key);
			if(v!=null || factory==null) {
				return v;
			}
			v = factory.apply(key);
			if(v==null) {
				return null;
			}
			if(map.size()>=capacity) {
				evict();
			}
			V prev = map.putIfAbsent(key, v);
			return prev!=null ? prev : v;
		}
		private void evict() {
			int toRemove = Math.max(1, capacity/4);
			for(Iterator<K> it=map.keySet().iterator(); it.hasNext() && toRemove>0; toRemove--) {
				it.next();
				it.remove();
			}
		}
		int size() {
			return map.size();
		}
	}
}
