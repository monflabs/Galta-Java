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
	
	@Override
	public boolean supportsNaN() {
		return true;
	}

	@Override
	public boolean supportsInfinity() {
		return true;
	}

	@Override
	public boolean supportsReferences() {
		return true;
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
	// Check the native values
	//
	@Override
	public boolean asBoolean(Object nativeValue) {
		if(nativeValue instanceof Boolean b) {
			return b.booleanValue();
		}
		throw new JsonException(null, "Value {0} is not a Boolean", nativeValue);
	}
	@Override
	public Number asNumber(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			return n;
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public byte asByte(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			// Saturated, like JsonObject.getInt(): 1e10 is not wrapped to a negative value
			return JsonUtil.clampToByte(n);
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public short asShort(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			// Saturated, like JsonObject.getInt(): 1e10 is not wrapped to a negative value
			return JsonUtil.clampToShort(n);
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public int asInt(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			// Saturated, like JsonObject.getInt(): 1e10 is not wrapped to a negative value
			return JsonUtil.clampToInt(n);
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public long asLong(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			// Saturated, like JsonObject.getInt(): 1e10 is not wrapped to a negative value
			return JsonUtil.clampToLong(n);
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public float asFloat(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			return n.floatValue();
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public double asDouble(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			return n.doubleValue();
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public BigInteger asBigInteger(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			return JsonUtil.bigIntegerValue(n, true);
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public BigDecimal asBigDecimal(Object nativeValue) {
		if(nativeValue instanceof Number n) {
			return JsonUtil.bigDecimalValue(n, true);
		}
		throw new JsonException(null, "Value {0} is not a Number", nativeValue);
	}
	@Override
	public String asString(Object nativeValue) {
		if(nativeValue instanceof String s) {
			return s;
		}
		throw new JsonException(null, "Value {0} is not a String", nativeValue);
	}
	@Override
	public JsonObject asObject(Object nativeValue) {
		if(nativeValue instanceof JsonObject o) {
			return o;
		}
		throw new JsonException(null, "Value {0} is not an Object", nativeValue);
	}
	@Override
	public JsonArray asArray(Object nativeValue) {
		if(nativeValue instanceof JsonArray a) {
			return a;
		}
		throw new JsonException(null, "Value {0} is not an Array", nativeValue);
	}
	
	@Override
	public Object toJavaPrimitive(Object jsonValue) {
		return jsonValue;
	}
	
	@Override
	public Object toNativeJsonPrimitive(Object javaValue) {
		return javaValue;
	}

	
	@Override
	public Object toNativeNull() {
		return null;
	}
	@Override
	public Object toNativeBoolean(boolean value) {
		return value;
	}
	@Override
	public Object toNativeNumber(Number value) {
		return value;
	}
	@Override
	public Object toNativeByte(byte value) {
		return value;
	}
	@Override
	public Object toNativeShort(short value) {
		return value;
	}
	@Override
	public Object toNativeInt(int value) {
		return value;
	}
	@Override
	public Object toNativeLong(long value) {
		return value;
	}
	@Override
	public Object toNativeFloat(float value) {
		return value;
	}
	@Override
	public Object toNativeDouble(double value) {
		return value;
	}
	@Override
	public Object toNativeBigInteger(BigInteger value) {
		return value;
	}
	@Override
	public Object toNativeBigDecimal(BigDecimal value) {
		return value;
	}
	@Override
	public Object toNativeString(String value) {
		return value;
	}
	@Override
	public Object toNativeObject(JsonObject value) {
		return value;
	}
	@Override
	public Object toNativeArray(JsonArray value) {
		return value;
	}
	
	@Override
	public boolean isNativeNull(Object value) {
		return value==null;
	}
	@Override
	public boolean isNativeBoolean(Object value) {
		return value!=null && value.getClass()==Boolean.class;
	}
	@Override
	public boolean isNativeNumber(Object value) {
		return value instanceof Number;
	}
	@Override
	public boolean isNativeString(Object value) {
		return value!=null && value.getClass()==String.class;
	}
	@Override
	public boolean isNativeContainer(Object value) {
		return value instanceof JsonObject || value instanceof JsonArray;
	}
	@Override
	public boolean isNativeObject(Object value) {
		return value instanceof JsonObject;
	}
	@Override
	public boolean isNativeArray(Object value) {
		return value instanceof JsonArray;
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
		return (T)toJavaPrimitive(_deepClone(toNativeJsonPrimitive(value), 0, null));
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
		if(ref!=null && supportsReferences()) {
			to.setReference(ref);
		}
	}
}
