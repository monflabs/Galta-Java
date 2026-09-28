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

import java.util.function.Function;

public interface CacheProvider<K,V> {
	
//	public static <K,V> CacheProvider<K,V> empty = new CacheProvider<>() {
//		
//	};
	
 	public boolean contains(K key);
    public V get(K key);
    
    public void put(K key, V value);
    public void clear();
    public void remove(K key);

	/**
	 * Returns the cached value, or creates it with the factory and caches it.
	 * <p>
	 * The factory runs outside of the cache lock, so it doesn't block the other readers. If two
	 * threads create the same value concurrently, the first one cached wins and is returned to
	 * both. A null value is cached too (a negative cache entry). A factory that recursively asks for the
	 * key it is creating gets an IllegalStateException instead of recursing forever.
	 */
	public default V get(K key, Function<K,V> factory) {
		synchronized(this) {
	    	if(contains(key)) {
	    		return get(key);
	    	}
		}
    	if(factory==null) {
    		return null;
    	}
    	V v = CacheProviderSupport.create(this, key, factory);
		synchronized(this) {
	    	if(contains(key)) {
	    		return get(key);
	    	}
    		put(key, v);
    		return v;
		}
    }
}

/**
 * Detects a factory recursively creating the key it is being called for.
 */
final class CacheProviderSupport {
	
	private CacheProviderSupport() {
	}
	
	private record Creating(Object cache, Object key) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Creating c && c.cache==cache && java.util.Objects.equals(c.key,key);
		}
		@Override
		public int hashCode() {
			return System.identityHashCode(cache)*31 + java.util.Objects.hashCode(key);
		}
	}
	private static final ThreadLocal<java.util.Set<Creating>> CREATING = ThreadLocal.withInitial(java.util.HashSet::new);
	
	static <K,V> V create(CacheProvider<K,V> cache, K key, Function<K,V> factory) {
		java.util.Set<Creating> creating = CREATING.get();
		Creating c = new Creating(cache,key);
		if(!creating.add(c)) {
			throw new IllegalStateException("Recursive creation of the cache entry "+key);
		}
		try {
			return factory.apply(key);
		} finally {
			creating.remove(c);
		}
	}
}
