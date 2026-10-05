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
import java.util.IdentityHashMap;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;

/**
 * Helpers to handle JSON values.
 */
public class JavaJsonFactory extends JsonFactory {
	
	public static final JavaJsonFactory instance = new JavaJsonFactory();
	
	protected JavaJsonFactory() {
	}
	
	//
	// Object handling
	//
	
	@Override
	public JsonObject createObject() {
		return new JsonObjectAsLinkedMap();
	}


	
	//
	// Array handling
	//

	@Override
	public JsonArray createArray() {
		return new JsonArrayAsArrayList();
	}

	@Override
	public JsonArray createArray(int initialCapacity) {
		return new JsonArrayAsArrayList(initialCapacity);
	}


	
	//
	// Json Clone
	//
	
	/**
	 * A deep copy, made of containers created by this factory. The JSON references
	 * (getReference()) are kept, as clone() does. A container that contains itself
	 * throws a {@link JsonException.CircularReference}.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public  <T> T deepClone(Object value) {
		return (T)_deepClone(value, 0, null);
	}
	// The containers on the current path, tracked once the recursion is deep (a cycle
	// always gets deep): no cost for the usual documents
	private static final int CYCLE_CHECK_DEPTH = 200;
	private Object _deepClone(Object value, int depth, IdentityHashMap<Object,Boolean> path) {
		if(value==null) {
			return null;
		}
		if(value instanceof String || value instanceof Number || value instanceof Boolean) {
			return value;
		}
		if(value instanceof JsonObject || value instanceof JsonArray) {
			if(depth>=CYCLE_CHECK_DEPTH) {
				if(path==null) {
					path = new IdentityHashMap<>();
				}
				if(path.put(value, Boolean.TRUE)!=null) {
					throw new JsonException.CircularReference(null,"Circular reference detected in {0} of type {1}",
							value instanceof JsonArray ? "array" : "object", value.getClass().getName());
				}
			}
			try {
				if(value instanceof JsonObject map) {
					JsonObject result = createObject();
					for(Map.Entry<String,Object> e: ((Map<String,Object>)map).entrySet()) {
						result.put(e.getKey(), _deepClone(e.getValue(), depth+1, path));
					}
					copyReference(map, result);
					return result;
				}
				JsonArray list = (JsonArray)value;
				JsonArray result = createArray(list.size());
				for(Object v: list){
					result.add(_deepClone(v, depth+1, path));
				}
				copyReference(list, result);
				return result;
			} finally {
				if(path!=null) {
					path.remove(value);
				}
			}
		}
		throw new JsonException(null,"Cannot clone object of type {0}",value.getClass());
	}
	private void copyReference(JsonContainer from, JsonContainer to) {
		String ref = from.getReference();
		if(ref!=null) {
			to.setReference(ref);
		}
	}
}
