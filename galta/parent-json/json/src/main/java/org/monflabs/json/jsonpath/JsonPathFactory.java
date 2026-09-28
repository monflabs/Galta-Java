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

import org.monflabs.util.cache.CacheProvider;
import org.monflabs.util.cache.LRUCache;

/**
 * Simple JsonPath Factory.
 * 
 * @author priand
 */
public class JsonPathFactory {
	
	private static JsonPathFactory instance = new JsonPathFactory();
	public static JsonPathFactory get() {
		return instance;
	}

	private static final int DEFAULT_CACHE_SIZE = 512;
	
	private CacheProvider<String, JsonPath> cache;
	
	public JsonPathFactory() {
		this(DEFAULT_CACHE_SIZE);
	}
	public JsonPathFactory(int cacheSize) {
		if(cacheSize>0) {
			this.cache = new LRUCache<>(cacheSize);
		}
	}
	
	public CacheProvider<String, JsonPath> getCacheProvider() {
		return cache;
	}

	public void setCacheProvider(CacheProvider<String, JsonPath> cache) {
		this.cache = cache;
	}

	public JsonPath getJsonPath(String jsonPath) {
		if(cache!=null) {
			return cache.get(jsonPath, (path) -> createJsonPath(path) );
		}
		return createJsonPath(jsonPath);
	}
	
	public JsonPath createJsonPath(String jsonPath) {
		return JsonPath.parse(jsonPath,0,false);
	}
	
	public JsonPath getPartialJsonPath(String jsonPath, int start) {
		return JsonPath.parse(jsonPath,start,true);
	}
}
