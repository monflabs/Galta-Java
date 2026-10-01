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
	public Map<String,Object> toNativeJsonPrimitive() {
		return this;
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

	
	///////////////////////////////////////////////////////////////////////////////
	// 
	// Possible optimizations
	//
	///////////////////////////////////////////////////////////////////////////////

	
	@Override
	public boolean isNull(String key) {
		Object v=get(key);
		return v==null;
	}
	@Override
	public boolean isBoolean(String key) {
		Object v=get(key);
		return v!=null && v.getClass()==Boolean.class;
	}
	@Override
	public boolean isNumber(String key) {
		Object v=get(key);
		return v instanceof Number;
	}
	@Override
	public boolean isString(String key) {
		Object v=get(key);
		return v!=null && v.getClass()==String.class;
	}
	@Override
	public boolean isContainer(String key) {
		Object v=get(key);
		return v instanceof JsonObject || v instanceof JsonArray;
	}
	@Override
	public boolean isObject(String key) {
		Object v=get(key);
		return v instanceof JsonObject;
	}
	@Override
	public boolean isArray(String key) {
		Object v=get(key);
		return v instanceof JsonArray;
	}
	
	
	@Override
	public boolean getBoolean(String key) {
		Object v=get(key);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, key);
	}
	@Override
	public Number getNumber(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkNumber(v, key);
	}
	@Override
	public byte getByte(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToByte(o);
		}
		// Generate an error message...
		return JsonUtil.checkByte(v, key);
	}
	@Override
	public short getShort(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToShort(o);
		}
		// Generate an error message...
		return JsonUtil.checkShort(v, key);
	}
	@Override
	public int getInt(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToInt(o);
		}
		// Generate an error message...
		return JsonUtil.checkInt(v, key);
	}
	@Override
	public long getLong(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToLong(o);
		}
		// Generate an error message...
		return JsonUtil.checkLong(v, key);
	}
	@Override
	public float getFloat(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v, key);
	}
	@Override
	public double getDouble(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v, key);
	}
	
	@Override
	public Boolean getBooleanObject(String key) {
		Object v=get(key);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, key);
	}
	@Override
	public Byte getByteObject(String key) {
		Object v=get(key);
		if(v instanceof Byte o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToByte(o);
		}
		// Generate an error message...
		return JsonUtil.checkByte(v, key);
	}
	@Override
	public Short getShortObject(String key) {
		Object v=get(key);
		if(v instanceof Short o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToShort(o);
		}
		// Generate an error message...
		return JsonUtil.checkShort(v, key);
	}
	@Override
	public Integer getIntObject(String key) {
		Object v=get(key);
		if(v instanceof Integer o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToInt(o);
		}
		// Generate an error message...
		return JsonUtil.checkInt(v, key);
	}
	@Override
	public Long getLongObject(String key) {
		Object v=get(key);
		if(v instanceof Long o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToLong(o);
		}
		// Generate an error message...
		return JsonUtil.checkLong(v, key);
	}
	@Override
	public Float getFloatObject(String key) {
		Object v=get(key);
		if(v instanceof Float o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v, key);
	}
	@Override
	public Double getDoubleObject(String key) {
		Object v=get(key);
		if(v instanceof Double o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v, key);
	}
	@Override
	public BigInteger getBigInteger(String key) {
		Object v=get(key);
		if(v instanceof BigInteger o) {
			return o;
		}
		return JsonUtil.checkBigInteger(v, key);
	}	
	@Override
	public BigDecimal getBigDecimal(String key) {
		Object v=get(key);
		if(v instanceof BigDecimal o) {
			return o;
		}
		return JsonUtil.checkBigDecimal(v, key);
	}	
	@Override
	public String getString(String key) {
		Object v=get(key);
		if(v instanceof String o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkString(v, key);
	}
	@Override
	public JsonObject getObject(String key) {
		Object v=get(key);
		if(v instanceof JsonObject o) {
			return o;
		}
		return JsonUtil.checkObject(v, key);
	}
	@Override
	public JsonArray getArray(String key) {
		Object v=get(key);
		if(v instanceof JsonArray a) {
			return a;
		}
		return JsonUtil.checkArray(v, key);
	}

	@Override
	public boolean getBoolean(String key, boolean defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Boolean o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkBoolean(v, key);
		}
		return defaultValue;
	}
	@Override
	public Number getNumber(String key, Number defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkNumber(v, key);
		}
		return defaultValue;
	}
	@Override
	public byte getByte(String key, byte defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return JsonUtil.clampToByte(o);
			}
			// Generate an error message...
			return JsonUtil.checkByte(v, key);
		}
		return defaultValue;
	}
	@Override
	public short getShort(String key, short defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return JsonUtil.clampToShort(o);
			}
			// Generate an error message...
			return JsonUtil.checkShort(v, key);
		}
		return defaultValue;
	}
	@Override
	public int getInt(String key, int defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return JsonUtil.clampToInt(o);
			}
			// Generate an error message...
			return JsonUtil.checkInt(v, key);
		}
		return defaultValue;
	}
	@Override
	public long getLong(String key, long defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return JsonUtil.clampToLong(o);
			}
			// Generate an error message...
			return JsonUtil.checkLong(v, key);
		}
		return defaultValue;
	}
	@Override
	public float getFloat(String key, float defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return o.floatValue();
			}
			// Generate an error message...
			return JsonUtil.checkFloat(v, key);
		}
		return defaultValue;
	}
	@Override
	public double getDouble(String key, double defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return o.doubleValue();
			}
			// Generate an error message...
			return JsonUtil.checkDouble(v, key);
		}
		return defaultValue;
	}

	@Override
	public Boolean getBooleanObject(String key, Boolean defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Boolean o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkBoolean(v, key);
		}
		return defaultValue;
	}
	@Override
	public Byte getByteObject(String key, Byte defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Byte o) {
				return o;
			}
			if(v instanceof Number o) {
				return JsonUtil.clampToByte(o);
			}
			// Generate an error message...
			return JsonUtil.checkByte(v, key);
		}
		return defaultValue;
	}
	@Override
	public Short getShortObject(String key, Short defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Short o) {
				return o;
			}
			if(v instanceof Number o) {
				return JsonUtil.clampToShort(o);
			}
			// Generate an error message...
			return JsonUtil.checkShort(v, key);
		}
		return defaultValue;
	}
	@Override
	public Integer getIntObject(String key, Integer defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Integer o) {
				return o;
			}
			if(v instanceof Number o) {
				return JsonUtil.clampToInt(o);
			}
			// Generate an error message...
			return JsonUtil.checkInt(v, key);
		}
		return defaultValue;
	}
	@Override
	public Long getLongObject(String key, Long defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Long o) {
				return o;
			}
			if(v instanceof Number o) {
				return JsonUtil.clampToLong(o);
			}
			// Generate an error message...
			return JsonUtil.checkLong(v, key);
		}
		return defaultValue;
	}
	@Override
	public Float getFloatObject(String key, Float defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Float o) {
				return o;
			}
			if(v instanceof Number o) {
				return o.floatValue();
			}
			// Generate an error message...
			return JsonUtil.checkFloat(v, key);
		}
		return defaultValue;
	}
	@Override
	public Double getDoubleObject(String key, Double defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Double o) {
				return o;
			}
			if(v instanceof Number o) {
				return o.doubleValue();
			}
			// Generate an error message...
			return JsonUtil.checkDouble(v, key);
		}
		return defaultValue;
	}

	
	@Override
	public BigInteger getBigInteger(String key, BigInteger defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof BigInteger o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkBigInteger(v, key);
		}
		return defaultValue;
	}	
	@Override
	public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof BigDecimal o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkBigDecimal(v, key);
		}
		return defaultValue;
	}	
	@Override
	public String getString(String key, String defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof String o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkString(v, key);
		}
		return defaultValue;
	}
	@Override
	public JsonObject getObject(String key, JsonObject defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof JsonObject o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkObject(v, key);
		}
		return defaultValue;
	}
	@Override
	public JsonArray getArray(String key, JsonArray defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof JsonArray a) {
				return a;
			}
			// Generate an error message...
			return JsonUtil.checkArray(v, key);
		}
		return defaultValue;
	}


	@Override
	public JsonObject putValue(String key, Object value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject putNull(String key) {
		put(key, (Object)null);
		return this;
	}
	@Override
	public JsonObject put(String key, boolean value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, byte value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, short value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, int value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, long value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, float value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, double value) {
		put(key, (Object)value);
		return this;
	}
	
	@Override
	public JsonObject put(String key, Number value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, Boolean value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, String value) {
		put(key, (Object)value);
		return this;
	}
	
	@Override
	public JsonObject put(String key, JsonObject value) {
		put(key, (Object)value);
		return this;
	}
	@Override
	public JsonObject put(String key, JsonArray value) {
		put(key, (Object)value );
		return this;
	}
}
