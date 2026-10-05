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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;


/**
 * Json Object implemented as a Map wrapper.
 */
@SuppressWarnings("serial")
public class JsonObjectAsLinkedMap extends LinkedHashMap<String, Object> implements JsonObject {
	
	private String reference;
	
	public JsonObjectAsLinkedMap() {
	}

	@Override
	public JsonObject clone() {
		return (JsonObject)super.clone();
	}

	/**
	 * Equality with another JSON object compares the values the JSON way (1 equals 1.0).
	 * Any other Map is compared with the Map contract (Object.equals on the values), so
	 * the equality is symmetric with the JDK maps: jsonObject.equals(hashMap) is
	 * hashMap.equals(jsonObject).
	 */
	@Override
	public boolean equals(Object o) {
		if(this==o) {
			return true;
		}
		if(o instanceof JsonObject jo) {
			return JsonUtil.equalsObject(this, jo);
		}
		if(o instanceof Map<?,?>) {
			return super.equals(o);
		}
		return false;
	}
	
	//
	// JSON object keys cannot be null: every method that can add a key checks it
	//
	protected static String checkKey(String key) {
		if(key==null) {
			throw new NullPointerException("A JSON object key cannot be null");
		}
		return key;
	}
	@Override
	public Object put(String key, Object value) {
		return super.put(checkKey(key), value);
	}
	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		// Through put(), so the subclasses see every value
		for(Map.Entry<? extends String, ? extends Object> e: m.entrySet()) {
			put(e.getKey(), e.getValue());
		}
	}
	@Override
	public Object putIfAbsent(String key, Object value) {
		return super.putIfAbsent(checkKey(key), value);
	}
	@Override
	public Object computeIfAbsent(String key, java.util.function.Function<? super String, ? extends Object> mappingFunction) {
		return super.computeIfAbsent(checkKey(key), mappingFunction);
	}
	@Override
	public Object compute(String key, java.util.function.BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
		return super.compute(checkKey(key), remappingFunction);
	}
	@Override
	public Object merge(String key, Object value, java.util.function.BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
		return super.merge(checkKey(key), value, remappingFunction);
	}

	@Override
	public int hashCode() {
		// Consistent with equals(): {a:1} and {a:1.0} are equal, so they must hash the same
		return JsonUtil.hashCode(this);
	}

	@Override
	public JsonValues jsonValues() {
		return JsonValues.of(this);
	}
	
	@Override
	public String toString() {
		// Pretty, and a circular reference doesn't throw
		return factory().toDisplayString(this);
	}
	

	@Override
	public JavaJsonFactory factory() {
		return JavaJsonFactory.instance;
	}


	// The last entry of the linked map, without iterating over all the values
	@SuppressWarnings("unchecked")
	@Override
	public <T> T lastValue() {
		Map.Entry<String,Object> e = lastEntry();
		if(e==null) {
			throw new JsonException(null,"Collection is empty");
		}
		return (T)e.getValue();
	}
	@SuppressWarnings("unchecked")
	@Override
	public <T> T lastValueOrDefault(T defaultValue) {
		Map.Entry<String,Object> e = lastEntry();
		return e!=null ? (T)e.getValue() : defaultValue;
	}

	@Override
	public String getReference() {
		return reference;
	}
	@Override
	public  void setReference(String reference) {
		this.reference = reference;
	}
}
