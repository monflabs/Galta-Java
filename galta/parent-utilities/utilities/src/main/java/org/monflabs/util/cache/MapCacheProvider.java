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
package org.monflabs.util.cache;

import java.util.Map;

/**
 * CacheProvider using a map behind the scene.
 */
public class MapCacheProvider<K,V> implements CacheProvider<K,V> {

    private Map<K, V> map;

	public MapCacheProvider(Map<K, V> map) {
    	this.map = map;
    }
	
	/**
	 * A snapshot of the cached entries, in the iteration order of the underlying map
	 * (least-recently-used first for an LRUCache). The underlying map itself was
	 * returned, which let callers read or change it without the cache lock.
	 */
	public synchronized Map<K, V> getMap() {
		return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(map));
	}

    @Override
	public synchronized boolean contains(K key) {
        return map.containsKey(key);
    }

    @Override
	public synchronized V get(K key) {
   		return map.get(key);
    }
    
    @Override
	public synchronized void put(K key, V value) {
    	map.put(key, value);
    }

    @Override
	public synchronized void clear() {
    	map.clear();
    }
    
    @Override
	public synchronized void remove(K key) {
    	map.remove(key);
    }
} 
