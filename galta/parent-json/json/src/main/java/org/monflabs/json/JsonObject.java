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
		return (JsonObject)JsonFactory.get().parse(json);
	}
	public static JsonObject parse(Reader json) {
		return (JsonObject)JsonFactory.get().parse(json);
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
	

	public boolean isNull(String key);
	public boolean isBoolean(String key);
	public boolean isNumber(String key);
	public boolean isString(String key);
	public boolean isContainer(String key);
	public boolean isObject(String key);
	public boolean isArray(String key);
	

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

	public boolean getBoolean(String key);
	public byte getByte(String key);
	public short getShort(String key);
	public int getInt(String key);
	public long getLong(String key);
	public float getFloat(String key);
	public double getDouble(String key);

	public Boolean getBooleanObject(String key);
	public Number getNumber(String key);
	public Byte getByteObject(String key);
	public Short getShortObject(String key);
	public Integer getIntObject(String key);
	public Long getLongObject(String key);
	public Float getFloatObject(String key);
	public Double getDoubleObject(String key);
	public BigInteger getBigInteger(String key);
	public BigDecimal getBigDecimal(String key);
	
	public String getString(String key);
	public JsonObject getObject(String key);
	public JsonArray getArray(String key);
	
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


	public boolean getBoolean(String key, boolean defaultValue);
	public byte getByte(String key, byte defaultValue);
	public short getShort(String key, short defaultValue);
	public int getInt(String key, int defaultValue);
	public long getLong(String key, long defaultValue);
	public float getFloat(String key, float defaultValue);
	public double getDouble(String key, double defaultValue);

	public Boolean getBooleanObject(String key, Boolean defaultValue);
	public Number getNumber(String key, Number defaultValue);
	public Byte getByteObject(String key, Byte defaultValue);
	public Short getShortObject(String key, Short defaultValue);
	public Integer getIntObject(String key, Integer defaultValue);
	public Long getLongObject(String key, Long defaultValue);
	public Float getFloatObject(String key, Float defaultValue);
	public Double getDoubleObject(String key, Double defaultValue);
	public BigInteger getBigInteger(String key, BigInteger defaultValue);
	public BigDecimal getBigDecimal(String key, BigDecimal defaultValue);
	
	public String getString(String key, String defaultValue);
	public JsonObject getObject(String key, JsonObject defaultValue);
	public JsonArray getArray(String key, JsonArray defaultValue);
	
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
		if(!containsKey(key)) {
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
		if(!containsKey(key)) {
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
	
	// To be removed!
	public default JsonObject putValue(String key, Object value) {
		put(key,value);
		return this;
	}
	public JsonObject putNull(String key);
	public JsonObject put(String key, boolean value);
	public JsonObject put(String key, byte value);
	public JsonObject put(String key, short value);
	public JsonObject put(String key, int value);
	public JsonObject put(String key, long value);
	public JsonObject put(String key, float value);
	public JsonObject put(String key, double value);

	public JsonObject put(String key, Boolean value);
	public JsonObject put(String key, Number value);
	public JsonObject put(String key, String value);
	
	public JsonObject put(String key, JsonObject value);
	public JsonObject put(String key, JsonArray value);

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

    public default <R> R process(Function<JsonObject, R> processsor) {
		return processsor.apply(this);
	}

    @Override
	public default JsonArray flat() {
        JsonArray r = factory().createArray();
   		r.addAll(values());
        return r;
    }    
}
