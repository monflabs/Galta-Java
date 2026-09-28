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
package tests.extras;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.monflabs.galtajs.jsonfactory.StringPropertyMap;
import org.monflabs.util.performance.PerformanceWatchCollection;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test GaltaJSMap and ensure it works like LinkedHashMap
 */
public class GaltaJSMapPerformanceTest extends JavaScriptStrictTestCase {
	
	public void testPerformance() {
		if(_ALLTESTS) {
			return;
		}
		
		int count = 3_000_000;
		// Compose a map with the values
		Map<String,Object> m = new HashMap<>();
		for(int i=0; i<count; i++) {
			String key = UUID.randomUUID().toString(); // Collision chances are rare....
			String value = "avalue";
			m.put(key,value);
		}
		checkPerformance(m,5);
	}
	private void checkPerformance(Map<String,Object> source, int repeat) {
		PerformanceWatchCollection jsonMapPerformance = new PerformanceWatchCollection("JsonObjectMap");
		PerformanceWatchCollection linkedHashMapPerformance = new PerformanceWatchCollection("LinkedHashMap");

		for(int i=-1; i<repeat; i++) {
			if(i<0) {
				StringPropertyMap jm = new StringPropertyMap();
				initializeMap(jm, source);
				LinkedHashMap<String,Object> lm = new LinkedHashMap<>();
				initializeMap(lm, source);

				jm.dumpStats();
			} else {
				StringPropertyMap jm = new StringPropertyMap();
				jsonMapPerformance.run( "putAll()", () -> {
					initializeMap(jm,source);
				});
				LinkedHashMap<String,Object> lm = new LinkedHashMap<>();
				linkedHashMapPerformance.run( "putAll()", () -> {
					initializeMap(lm,source);
				});
				
				jsonMapPerformance.run( "get()", () -> {
					accessMap(jm,source);
				});
				linkedHashMapPerformance.run( "get()", () -> {
					accessMap(lm,source);
				});
				
				jsonMapPerformance.run( "put()", () -> {
					putMap(jm,source);
				});
				linkedHashMapPerformance.run( "put()", () -> {
					putMap(lm,source);
				});
				
				jsonMapPerformance.run( "IterateKeys()", () -> {
					iteratesKeys(jm);
				});
				linkedHashMapPerformance.run( "IterateKeys()",  () -> {
					iteratesKeys(lm);
				});
				
				if(i==0) {
					jm.dumpStats();
				}
			}
		}
		
		jsonMapPerformance.dump();
		linkedHashMapPerformance.dump();
	}
	private static void initializeMap(Map<String,Object> map, Map<String,Object> source) {
		map.putAll(source);
	}
	private static void accessMap(Map<String,Object> map, Map<String,Object> source) {
		for(String key: source.keySet()) {
			for(int i=0; i<5; i++) {
				map.get(key);
			}
		}
	}
	private static void putMap(Map<String,Object> map, Map<String,Object> source) {
		for(Map.Entry<String,Object> e: source.entrySet()) {
			String k = e.getKey();
			Object v = e.getValue();
			for(int i=0; i<5; i++) {
				map.put(k,v);
			}
		}
	}
	@SuppressWarnings("unused")
	private static void iteratesKeys(Map<String,Object> map) {
		for(int i=0; i<5; i++) {
			int cpt = 0;
			String s = null;
			for(String k: map.keySet()) {
				cpt++;
				s = k;
			}
		}
	}
}
