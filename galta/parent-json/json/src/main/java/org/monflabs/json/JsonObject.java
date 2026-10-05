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
package org.monflabs.json;

import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.json.jsonpath.JsonValues;

/**
 * Helpers to handle JSON values.
 */
public interface JsonObject extends Map<String,Object>, JsonContainer {
	
	/////////////////////////////////////////////////////////////////////////////////////////
	//
	// Static shortcuts that use the default factory
	//
	/////////////////////////////////////////////////////////////////////////////////////////

	public static JsonObject create() {
		return JsonFactory.get().createObject();
	}
	public static JsonObject of(Object...values) {
		return JsonFactory.get().of(values);
	}
	
	public static JsonObject parse(String json) {
		return JsonUtil.parsedAs(JsonFactory.get().parse(json), JsonObject.class);
	}
	public static JsonObject parse(Reader json) {
		return JsonUtil.parsedAs(JsonFactory.get().parse(json), JsonObject.class);
	}
	
	@Override
	public JsonObject clone();
	@Override
	default JsonObject deepClone() {
		return (JsonObject)factory().deepClone(this);
	}

	@Override
	public default boolean isObject() {
		return true;
	}

	@Override
	public default boolean isArray() {
		return false;
	}	

	@Override
	public default JsonObject forEachValue(Consumer<JsonValues> action) {
		values().forEach( (v) -> action.accept(JsonValues.of(v)) );
		return this;
	}
	
	public default JsonArray getValues(String...keys) {
		JsonArray a = factory().createArray();
		for(int i=0; i<keys.length; i++) {
			String key = keys[i];
			if(has(key)) {
				a.add(get(key));
			}
		}
		return a;
	}
	


	
	///////////////////////////////////////////////////////////////////////////////
	// 
	// Possible optimizations
	//
	///////////////////////////////////////////////////////////////////////////////

	
	public default boolean isNull(String key) {
		Object v=get(key);
		return v==null;
	}

	public default boolean isBoolean(String key) {
		Object v=get(key);
		return v!=null && v.getClass()==Boolean.class;
	}

	public default boolean isNumber(String key) {
		Object v=get(key);
		return v instanceof Number;
	}

	public default boolean isString(String key) {
		Object v=get(key);
		return v!=null && v.getClass()==String.class;
	}

	public default boolean isContainer(String key) {
		Object v=get(key);
		return v instanceof JsonObject || v instanceof JsonArray;
	}

	public default boolean isObject(String key) {
		Object v=get(key);
		return v instanceof JsonObject;
	}

	public default boolean isArray(String key) {
		Object v=get(key);
		return v instanceof JsonArray;
	}

	
	
	public default boolean getBoolean(String key) {
		Object v=get(key);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, key);
	}

	public default Number getNumber(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkNumber(v, key);
	}

	public default byte getByte(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToByte(o);
		}
		// Generate an error message...
		return JsonUtil.checkByte(v, key);
	}

	public default short getShort(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToShort(o);
		}
		// Generate an error message...
		return JsonUtil.checkShort(v, key);
	}

	public default int getInt(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToInt(o);
		}
		// Generate an error message...
		return JsonUtil.checkInt(v, key);
	}

	public default long getLong(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return JsonUtil.clampToLong(o);
		}
		// Generate an error message...
		return JsonUtil.checkLong(v, key);
	}

	public default float getFloat(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v, key);
	}

	public default double getDouble(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v, key);
	}

	
	public default Boolean getBooleanObject(String key) {
		Object v=get(key);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, key);
	}

	public default Byte getByteObject(String key) {
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

	public default Short getShortObject(String key) {
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

	public default Integer getIntObject(String key) {
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

	public default Long getLongObject(String key) {
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

	public default Float getFloatObject(String key) {
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

	public default Double getDoubleObject(String key) {
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

	public default BigInteger getBigInteger(String key) {
		Object v=get(key);
		if(v instanceof BigInteger o) {
			return o;
		}
		return JsonUtil.checkBigInteger(v, key);
	}
	
	public default BigDecimal getBigDecimal(String key) {
		Object v=get(key);
		if(v instanceof BigDecimal o) {
			return o;
		}
		return JsonUtil.checkBigDecimal(v, key);
	}
	
	public default String getString(String key) {
		Object v=get(key);
		if(v instanceof String o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkString(v, key);
	}

	public default JsonObject getObject(String key) {
		Object v=get(key);
		if(v instanceof JsonObject o) {
			return o;
		}
		return JsonUtil.checkObject(v, key);
	}

	public default JsonArray getArray(String key) {
		Object v=get(key);
		if(v instanceof JsonArray a) {
			return a;
		}
		return JsonUtil.checkArray(v, key);
	}


	public default boolean getBoolean(String key, boolean defaultValue) {
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

	public default Number getNumber(String key, Number defaultValue) {
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

	public default byte getByte(String key, byte defaultValue) {
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

	public default short getShort(String key, short defaultValue) {
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

	public default int getInt(String key, int defaultValue) {
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

	public default long getLong(String key, long defaultValue) {
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

	public default float getFloat(String key, float defaultValue) {
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

	public default double getDouble(String key, double defaultValue) {
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


	public default Boolean getBooleanObject(String key, Boolean defaultValue) {
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

	public default Byte getByteObject(String key, Byte defaultValue) {
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

	public default Short getShortObject(String key, Short defaultValue) {
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

	public default Integer getIntObject(String key, Integer defaultValue) {
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

	public default Long getLongObject(String key, Long defaultValue) {
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

	public default Float getFloatObject(String key, Float defaultValue) {
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

	public default Double getDoubleObject(String key, Double defaultValue) {
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


	
	public default BigInteger getBigInteger(String key, BigInteger defaultValue) {
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
	
	public default BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
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
	
	public default String getString(String key, String defaultValue) {
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

	public default JsonObject getObject(String key, JsonObject defaultValue) {
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

	public default JsonArray getArray(String key, JsonArray defaultValue) {
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




	public default JsonObject putNull(String key) {
		put(key, (Object)null);
		return this;
	}

	public default JsonObject put(String key, boolean value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, byte value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, short value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, int value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, long value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, float value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, double value) {
		put(key, (Object)value);
		return this;
	}

	
	public default JsonObject put(String key, Number value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, Boolean value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, String value) {
		put(key, (Object)value);
		return this;
	}

	
	public default JsonObject put(String key, JsonObject value) {
		put(key, (Object)value);
		return this;
	}

	public default JsonObject put(String key, JsonArray value) {
		put(key, (Object)value );
		return this;
	}
	

	//
	// Access a typed property and throw an exception when it is null or not of the right type
	public default boolean has(String key) {
		return containsKey(key);
	}
	public default JsonValues jsonValues(String key) {
		return JsonValues.of(get(key));
	}

	public default <T> T getValue(String key) {
		@SuppressWarnings("unchecked")
		T t = (T)get(key);
		if(t==null) {
			throw new JsonException(null,"Value is null for key {0}",key);
		}
		return t;
	}
	public default <T> T getValueOrDefault(String key, T defaultValue) {
		@SuppressWarnings("unchecked")
		T t = (T)get(key);
		if(t==null) {
			return defaultValue;
		}
		return t;
	}


	
	
	public default LocalDate getLocalDate(String key) {
		return JsonUtil.parseLocalDate(getString(key));
	}
	public default LocalTime getLocalTime(String key) {
		return JsonUtil.parseLocalTime(getString(key));
	}
	public default LocalDateTime getLocalDateTime(String key) {
		return JsonUtil.parseLocalDateTime(getString(key));
	}
	public default OffsetTime getOffsetTime(String key) {
		return JsonUtil.parseOffsetTime(getString(key));
	}
	public default OffsetDateTime getOffsetDateTime(String key) {
		return JsonUtil.parseOffsetDateTime(getString(key));
	}
	public default ZonedDateTime getZonedDateTime(String key) {
		return JsonUtil.parseZonedDateTime(getString(key));
	}



	
	
	public default LocalDate getLocalDate(String key, LocalDate defaultValue) {
		String v = getString(key,null);
		return v!=null ? JsonUtil.parseLocalDate(v) : defaultValue;
	}
	public default LocalTime getLocalTime(String key, LocalTime defaultValue) {
		String v = getString(key,null);
		return v!=null ? JsonUtil.parseLocalTime(v) : defaultValue;
	}
	public default LocalDateTime getLocalDateTime(String key, LocalDateTime defaultValue) {
		String v = getString(key,null);
		return v!=null ? JsonUtil.parseLocalDateTime(v) : defaultValue;
	}
	public default OffsetTime getOffsetTime(String key, OffsetTime defaultValue) {
		String v = getString(key,null);
		return v!=null? JsonUtil.parseOffsetTime(v) : defaultValue;
	}
	public default OffsetDateTime getOffsetDateTime(String key, OffsetDateTime defaultValue) {
		String v = getString(key,null);
		return v!=null? JsonUtil.parseOffsetDateTime(v) : defaultValue;
	}
	public default ZonedDateTime getZonedDateTime(String key, ZonedDateTime defaultValue) {
		String v = getString(key,null);
		return v!=null ? JsonUtil.parseZonedDateTime(v) : defaultValue;
	}
		
	public default JsonObject getOrCreateObject(String key) {
		return getOrCreateObject(key,null);
	}
	public default JsonObject getOrCreateObject(String key, ObjectConsumer action) {
		// A missing key or a null value: both create the object
		if(get(key)==null) {
			JsonObject o = factory().createObject();
			if(action!=null) {
				action.accept(o);
			}
			put(key, o);
		}
		return getObject(key);
	}
	public default JsonArray getOrCreateArray(String key) {
		return getOrCreateArray(key,null);
	}
	public default JsonArray getOrCreateArray(String key, ArrayConsumer action) {
		// A missing key or a null value: both create the array
		if(get(key)==null) {
			JsonArray o = factory().createArray();
			if(action!=null) {
				action.accept(o);
			}
			put(key, o);
		}
		return getArray(key);
	}

	
	public default JsonObject putAllValues(Iterator<Map.Entry<String,Object>> it) {
		while(it.hasNext()) {
			Map.Entry<?, ?> e = it.next();
			put((String)e.getKey(), e.getValue());
		}
		return this;
	}
	public default JsonObject putAllValues(JsonObject o) {
		for(Map.Entry<String,Object>e: o.entrySet()) {
			putValue(e.getKey(), e.getValue());
		}
		return this;
	}

	@Override
	public Object put(String key, Object value);
	
	public default JsonObject putValue(String key, Object value) {
		put(key,value);
		return this;
	}

	

	public default JsonObject put(String key, LocalDate value) {
		put(key, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	public default JsonObject put(String key, LocalTime value) {
		put(key, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	public default JsonObject put(String key, LocalDateTime value) {
		put(key, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	public default JsonObject put(String key, OffsetTime value) {
		put(key, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	public default JsonObject put(String key, OffsetDateTime value) {
		put(key, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	public default JsonObject put(String key, ZonedDateTime value) {
		put(key, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonObject put(String key, ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		put(key, jo);
		return this;
	}
	public default JsonObject put(String key, ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		put(key, ja);
		return this;
	}
	
	
	
	//
	// Some conversion methods
	// These methods are *never* failing but return the default value in case of
	//
	
	public default Number asNumber(String key) {
		return JsonUtil.asNumber(get(key));
	}
	public default Number asNumber(String key, int defaultValue) {
		return JsonUtil.asNumber(get(key),defaultValue);
	}
	public default Number asNumber(String key, Number defaultValue) {
		return JsonUtil.asNumber(get(key),defaultValue);
	}
	
	public default int asInt(String key) {
		return JsonUtil.asInt(get(key));
	}
	public default int asInt(String key, int defaultValue) {
		return JsonUtil.asInt(get(key),defaultValue);
	}
	
	public default long asLong(String key) {
		return JsonUtil.asLong(get(key));
	}
	public default long asLong(String key, long defaultValue) {
		return JsonUtil.asLong(get(key),defaultValue);
	}
	
	public default double asDouble(String key) {
		return JsonUtil.asDouble(get(key));
	}
	public default double asDouble(String key, double defaultValue) {
		return JsonUtil.asDouble(get(key),defaultValue);
	}
	
	public default BigInteger asBigInteger(String key) {
		return JsonUtil.asBigInteger(get(key));
	}
	public default BigInteger asBigInteger(String key, BigInteger defaultValue) {
		return JsonUtil.asBigInteger(get(key),defaultValue);
	}
	
	public default BigDecimal asBigDecimal(String key) {
		return JsonUtil.asBigDecimal(get(key));
	}
	public default BigDecimal asBigDecimal(String key, BigDecimal defaultValue) {
		return JsonUtil.asBigDecimal(get(key),defaultValue);
	}
	
	public default boolean asBoolean(String key) {
		return JsonUtil.asBoolean(get(key));
	}
	public default boolean asBoolean(String key, boolean defaultValue) {
		return JsonUtil.asBoolean(get(key),defaultValue);
	}
	
	public default String asString(String key) {
		return JsonUtil.asString(get(key));
	}
	public default String asString(String key, String defaultValue) {
		return JsonUtil.asString(get(key),defaultValue);
	}
	
	
	/////////////////////////////////////////////////////////////////
	//
	// Stream like functions
	//
	/////////////////////////////////////////////////////////////////

    public default <R> R process(Function<JsonObject, R> processor) {
		return processor.apply(this);
	}

    @Override
	public default JsonArray flat() {
        JsonArray r = factory().createArray();
   		r.addAll(values());
        return r;
    }    
}
