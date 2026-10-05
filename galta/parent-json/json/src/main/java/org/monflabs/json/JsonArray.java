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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.util.Reducer;
import org.monflabs.util.StringUtil;

/**
 * Json Array.
 */
public interface JsonArray extends JsonContainer, List<Object> {

	@FunctionalInterface
	public interface EntryConsumer {
		public void process(long index, Object value);
	}
	@FunctionalInterface
	public interface EntryConsumerWhile {
		public boolean process(long index, Object value);
	}
	
	//
	// Static shortcuts that use the default factory
	public static JsonArray create() {
		return JsonFactory.get().createArray();
	}
	public static JsonArray create(int initialCapacity) {
		return JsonFactory.get().createArray(initialCapacity);
	}
	public static JsonArray of(Object...values) {
		return JsonFactory.get().arrayOf(values);
	}
	
	public static JsonArray parse(String json) {
		return JsonUtil.parsedAs(JsonFactory.get().parse(json), JsonArray.class);
	}
	public static JsonArray parse(Reader json) {
		return JsonUtil.parsedAs(JsonFactory.get().parse(json), JsonArray.class);
	}
		
	@Override
	public JsonArray clone();
	
	@Override
	default JsonArray deepClone() {
		return (JsonArray)factory().deepClone(this);
	}

	@Override
	public default boolean isObject() {
		return false;
	}

	@Override
	public default boolean isArray() {
		return true;
	}


	
	// The is*() tests don't throw for an index out of range: like a missing key of an
	// object, there is no value (isNull() is true, the others false)
	// The item at an index for the getters without a default: an index out of range throws
	// an IndexOutOfBoundsException whatever the implementation (a JavaScript array answers
	// undefined to get())
	private Object item(int index) {
		if(!has(index)) {
			throw new IndexOutOfBoundsException("Index "+index+" out of bounds for length "+size());
		}
		return get(index);
	}
	private Object peek(int index) {
		return has(index) ? get(index) : null;
	}

	public default boolean isNull(int index) {
		Object v=peek(index);
		return v==null;
	}

	public default boolean isBoolean(int index) {
		Object v=peek(index);
		return v!=null && v.getClass()==Boolean.class;
	}

	public default boolean isNumber(int index) {
		Object v=peek(index);
		return v instanceof Number;
	}

	public default boolean isString(int index) {
		Object v=peek(index);
		return v!=null && v.getClass()==String.class;
	}

	public default boolean isContainer(int index) {
		Object v=peek(index);
		return v instanceof JsonObject || v instanceof JsonArray;
	}

	public default boolean isObject(int index) {
		Object v=peek(index);
		return v instanceof JsonObject;
	}

	public default boolean isArray(int index) {
		Object v=peek(index);
		return v instanceof JsonArray;
	}

    
    
    
    
	public default boolean getBoolean(int index) {
		Object v=item(index);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, index);
	}

	public default Number getNumber(int index) {
		Object v=item(index);
		if(v instanceof Number o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkNumber(v, index);
	}

	public default byte getByte(int index) {
		Object v=item(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToByte(o);
		}
		// Generate an error message...
		return JsonUtil.checkByte(v, index);
	}

	public default short getShort(int index) {
		Object v=item(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToShort(o);
		}
		// Generate an error message...
		return JsonUtil.checkShort(v, index);
	}

	public default int getInt(int index) {
		Object v=item(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToInt(o);
		}
		// Generate an error message...
		return JsonUtil.checkInt(v, index);
	}

	public default long getLong(int index) {
		Object v=item(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToLong(o);
		}
		// Generate an error message...
		return JsonUtil.checkLong(v, index);
	}

	public default float getFloat(int index) {
		Object v=item(index);
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v, index);
	}

	public default double getDouble(int index) {
		Object v=item(index);
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v, index);
	}

	
	public default Boolean getBooleanObject(int index) {
		Object v=item(index);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, index);
	}

	public default Byte getByteObject(int index) {
		Object v=item(index);
		if(v instanceof Byte o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToByte(o);
		}
		// Generate an error message...
		return JsonUtil.checkByte(v, index);
	}

	public default Short getShortObject(int index) {
		Object v=item(index);
		if(v instanceof Short o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToShort(o);
		}
		// Generate an error message...
		return JsonUtil.checkShort(v, index);
	}

	public default Integer getIntObject(int index) {
		Object v=item(index);
		if(v instanceof Integer o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToInt(o);
		}
		// Generate an error message...
		return JsonUtil.checkInt(v, index);
	}

	public default Long getLongObject(int index) {
		Object v=item(index);
		if(v instanceof Long o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToLong(o);
		}
		// Generate an error message...
		return JsonUtil.checkLong(v, index);
	}

	public default Float getFloatObject(int index) {
		Object v=item(index);
		if(v instanceof Float o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v, index);
	}

	public default Double getDoubleObject(int index) {
		Object v=item(index);
		if(v instanceof Double o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v, index);
	}

	public default BigInteger getBigInteger(int index) {
		Object v=item(index);
		if(v instanceof BigInteger o) {
			return o;
		}
		return JsonUtil.checkBigInteger(v, index);
	}
	
	public default BigDecimal getBigDecimal(int index) {
		Object v=item(index);
		if(v instanceof BigDecimal o) {
			return o;
		}
		return JsonUtil.checkBigDecimal(v, index);
	}
	
	public default String getString(int index) {
		Object v=item(index);
		if(v instanceof String o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkString(v, index);
	}

	public default JsonObject getObject(int index) {
		Object v=item(index);
		if(v instanceof JsonObject o) {
			return o;
		}
		return JsonUtil.checkObject(v, index);
	}

	public default JsonArray getArray(int index) {
		Object v=item(index);
		if(v instanceof JsonArray a) {
			return a;
		}
		return JsonUtil.checkArray(v, index);
	}

	
	public default LocalDate getLocalDate(int index) {
		return JsonUtil.parseLocalDate(getString(index));
	}

	public default LocalTime getLocalTime(int index) {
		return JsonUtil.parseLocalTime(getString(index));
	}

	public default LocalDateTime getLocalDateTime(int index) {
		return JsonUtil.parseLocalDateTime(getString(index));
	}

	public default OffsetTime getOffsetTime(int index) {
		return JsonUtil.parseOffsetTime(getString(index));
	}

	public default OffsetDateTime getOffsetDateTime(int index) {
		return JsonUtil.parseOffsetDateTime(getString(index));
	}

	public default ZonedDateTime getZonedDateTime(int index) {
		return JsonUtil.parseZonedDateTime(getString(index));
	}



	public default boolean getBoolean(int index, boolean defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Boolean o) {
					return o;
				}
				// Generate an error message...
				return JsonUtil.checkBoolean(v, index);
			}
		}
		return defaultValue;
	}

	public default Number getNumber(int index, Number defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Number o) {
					return o;
				}
				// Generate an error message...
				return JsonUtil.checkNumber(v, index);
			}
		}
		return defaultValue;
	}

	public default byte getByte(int index, byte defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Number o) {
					return JsonUtil.clampToByte(o);
				}
				// Generate an error message...
				return JsonUtil.checkByte(v, index);
			}
		}
		return defaultValue;
	}

	public default short getShort(int index, short defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Number o) {
					return JsonUtil.clampToShort(o);
				}
				// Generate an error message...
				return JsonUtil.checkShort(v, index);
			}
		}
		return defaultValue;
	}

	public default int getInt(int index, int defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Number o) {
					return JsonUtil.clampToInt(o);
				}
				// Generate an error message...
				return JsonUtil.checkInt(v, index);
			}
		}
		return defaultValue;
	}

	public default long getLong(int index, long defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Number o) {
					return JsonUtil.clampToLong(o);
				}
				// Generate an error message...
				return JsonUtil.checkLong(v, index);
			}
		}
		return defaultValue;
	}

	public default float getFloat(int index, float defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Number o) {
					return o.floatValue();
				}
				// Generate an error message...
				return JsonUtil.checkFloat(v, index);
			}
		}
		return defaultValue;
	}

	public default double getDouble(int index, double defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Number o) {
					return o.doubleValue();
				}
				// Generate an error message...
				return JsonUtil.checkDouble(v, index);
			}
		}
		return defaultValue;
	}


	public default Boolean getBooleanObject(int index, Boolean defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Boolean o) {
					return o;
				}
				// Generate an error message...
				return JsonUtil.checkBoolean(v, index);
			}
		}
		return defaultValue;
	}

	public default Byte getByteObject(int index, Byte defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Byte o) {
					return o;
				}
				if(v instanceof Number o) {
					return JsonUtil.clampToByte(o);
				}
				// Generate an error message...
				return JsonUtil.checkByte(v, index);
			}
		}
		return defaultValue;
	}

	public default Short getShortObject(int index, Short defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Short o) {
					return o;
				}
				if(v instanceof Number o) {
					return JsonUtil.clampToShort(o);
				}
				// Generate an error message...
				return JsonUtil.checkShort(v, index);
			}
		}
		return defaultValue;
	}

	public default Integer getIntObject(int index, Integer defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Integer o) {
					return o;
				}
				if(v instanceof Number o) {
					return JsonUtil.clampToInt(o);
				}
				// Generate an error message...
				return JsonUtil.checkInt(v, index);
			}
		}
		return defaultValue;
	}

	public default Long getLongObject(int index, Long defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Long o) {
					return o;
				}
				if(v instanceof Number o) {
					return JsonUtil.clampToLong(o);
				}
				// Generate an error message...
				return JsonUtil.checkLong(v, index);
			}
		}
		return defaultValue;
	}

	public default Float getFloatObject(int index, Float defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Float o) {
					return o;
				}
				if(v instanceof Number o) {
					return o.floatValue();
				}
				// Generate an error message...
				return JsonUtil.checkFloat(v, index);
			}
		}
		return defaultValue;
	}

	public default Double getDoubleObject(int index, Double defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof Double o) {
					return o;
				}
				if(v instanceof Number o) {
					return o.doubleValue();
				}
				// Generate an error message...
				return JsonUtil.checkDouble(v, index);
			}
		}
		return defaultValue;
	}


	
	public default BigInteger getBigInteger(int index, BigInteger defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof BigInteger o) {
					return o;
				}
				// Generate an error message...
				return JsonUtil.checkBigInteger(v, index);
			}
		}
		return defaultValue;
	}
	
	public default BigDecimal getBigDecimal(int index, BigDecimal defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof BigDecimal o) {
					return o;
				}
				// Generate an error message...
				return JsonUtil.checkBigDecimal(v, index);
			}
		}
		return defaultValue;
	}
	
	public default String getString(int index, String defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof String o) {
					return o;
				}
				// Generate an error message...
				return JsonUtil.checkString(v, index);
			}
		}
		return defaultValue;
	}

	public default JsonObject getObject(int index, JsonObject defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof JsonObject o) {
					return o;
				}
				// Generate an error message...
				return JsonUtil.checkObject(v, index);
			}
		}
		return defaultValue;
	}

	public default JsonArray getArray(int index, JsonArray defaultValue) {
		if(has(index)) {
			Object v=get(index);
			if(v!=null) {
				if(v instanceof JsonArray a) {
					return a;
				}
				// Generate an error message...
				return JsonUtil.checkArray(v, index);
			}
		}
		return defaultValue;
	}

	public default LocalDate getLocalDate(int index, LocalDate defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseLocalDate(v) : defaultValue;
	}

	public default LocalTime getLocalTime(int index, LocalTime defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseLocalTime(v) : defaultValue;
	}

	public default LocalDateTime getLocalDateTime(int index, LocalDateTime defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseLocalDateTime(v) : defaultValue;
	}

	public default OffsetTime getOffsetTime(int index, OffsetTime defaultValue) {
		String v = getString(index,null);
		return v!=null? JsonUtil.parseOffsetTime(v) : defaultValue;
	}

	public default OffsetDateTime getOffsetDateTime(int index, OffsetDateTime defaultValue) {
		String v = getString(index,null);
		return v!=null? JsonUtil.parseOffsetDateTime(v) : defaultValue;
	}

	public default ZonedDateTime getZonedDateTime(int index, ZonedDateTime defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseZonedDateTime(v) : defaultValue;
	}


	

	public default JsonArray addNull() {
		add((Object)null);
		return this;
	}

	public default JsonArray add(boolean value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(byte value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(short value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(int value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(long value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(float value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(double value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(Boolean value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(Number value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(String value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(JsonObject value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(JsonArray value) {
		add((Object)value);
		return this;
	}

	public default JsonArray add(LocalDate value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(LocalTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(LocalDateTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(OffsetTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(OffsetDateTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(ZonedDateTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		add(jo);
		return this;
	}

	public default JsonArray add(ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		add(ja);
		return this;
	}
	
	
	public default JsonArray addValue(int index, Object value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray addNull(int index) {
		add(index,(Object)null);
		return this;
	}

	public default JsonArray add(int index, boolean value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, byte value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, short value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, int value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, long value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, float value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, double value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, Boolean value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, Number value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, String value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, JsonObject value) {
		add(index,(Object)value);
		return this;
	}

	public default JsonArray add(int index, JsonArray value) {
		add(index,(Object)value);
		return this;
	}

	
	public default JsonArray add(int index, LocalDate value) {
		add(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(int index, LocalTime value) {
		add(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(int index, LocalDateTime value) {
		add(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(int index, OffsetTime value) {
		add(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(int index, OffsetDateTime value) {
		add(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray add(int index, ZonedDateTime value) {
		add(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	
	public default JsonArray add(int index, ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		add(index,jo);
		return this;
	}

	public default JsonArray add(int index, ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		add(index,ja);
		return this;
	}

	
	public default JsonArray setValue(int index, Object value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray setNull(int index) {
		set(index,(Object)null);
		return this;
	}

	public default JsonArray set(int index, boolean value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, byte value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, short value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, int value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, long value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, float value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, double value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, Boolean value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, Number value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, String value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, JsonObject value) {
		set(index,(Object)value);
		return this;
	}

	public default JsonArray set(int index, JsonArray value) {
		set(index,(Object)value);
		return this;
	}

	
	public default JsonArray set(int index, LocalDate value) {
		set(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray set(int index, LocalTime value) {
		set(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray set(int index, LocalDateTime value) {
		set(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray set(int index, OffsetTime value) {
		set(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray set(int index, OffsetDateTime value) {
		set(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray set(int index, ZonedDateTime value) {
		set(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}

	public default JsonArray set(int index, ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		set(index,jo);
		return this;
	}

	public default JsonArray set(int index, ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		set(index, ja);
		return this;
	}
	

	
	
	/////////////////////////////////////////////////////////////////
	//
	// JavaScript like functions
	//
	/////////////////////////////////////////////////////////////////

	public default String join(char sep) {
		int sz = size();
    	switch(sz) {
    		case 0:		return "";
    		case 1:		return StringUtil.toString(get(0));
    		default: {
    			StringBuilder b = new StringBuilder();
    			for(int i=0; i<sz; i++) {
    				if(i>0) {
    					b.append(sep);
    				}
					b.append(StringUtil.toString(get(i)));
    			}
				return b.toString();
    		}
    	}
	}

	
	/////////////////////////////////////////////////////////////////
	//
	// Stream like functions
	//
	/////////////////////////////////////////////////////////////////

	@Override
	public default JsonArray flat() {
    	int sz = size();
        JsonArray r = factory().createArray();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(value instanceof JsonArray a) {
        		r.addAll(a);
        	} else if(value instanceof JsonObject o) {
        		r.addAll(o.values());
        	} else {
        		r.add(value);
        	}
        }
        return r;
    }
    
    
	public default JsonArray slice(int start, int end) {
		return slice(start, end, 1);
	}

	public default JsonArray slice(Integer start, Integer end) {
		return slice(start, end, 1);
	}

	public default JsonArray slice(Integer start, Integer end, int step) {
		// A negative step walks backwards, so its defaults are the other way round
		int s = start!=null ? start : (step<0 ? size()-1 : 0);
		int e = end!=null ? end : (step<0 ? Integer.MIN_VALUE : Integer.MAX_VALUE);
		return slice(s, e, step);
	}

	public default JsonArray slice(int start, int end, int step) {
		if(step==0) {
			throw new JsonException(null,"Slice step cannot be 0");
		}
		int size = size();
		JsonArray a = factory().createArray();
		int nStart = start<0 ? start+size : start;
		int nEnd = end<0 ? end+size : end;
		if(step>0) {
			int st = Math.max(0, Math.min(size, nStart));
			int ed = Math.max(0, Math.min(size, nEnd));
			// long: i+step must not overflow for a large step
			for(long i=st; i<ed; i+=step) {
				a.add(get((int)i));
			}
		} else {
			int st = Math.max(-1, Math.min(size-1, nStart));
			int ed = Math.max(-1, Math.min(size, nEnd));
			for(long i=st; i>ed; i+=step) {
				a.add(get((int)i));
			}
		}
		return a;
	}


	// Similar to sort but works on a copy
	public default JsonArray sorted() {
		return sorted(true);
	}

	public default JsonArray sorted(boolean asc) {
		return sorted(asc ? JsonUtil.jsonComparator : JsonUtil.jsonComparatorDesc);
	}

	public default JsonArray sorted(Comparator<Object> comp) {
		JsonArray a = factory().createArray();
		a.addAll(this);
		a.sort(comp);
		return a;
	}

	
	public default JsonArray skip(int skip) {
		JsonArray a = factory().createArray();
		int sz = size(); 
		for(int i=Math.max(0,skip); i<sz; i++) {
			a.add(get(i));
		}
		return a;
	}

	
	public default JsonArray limit(int limit) {
		JsonArray a = factory().createArray();
		int sz = size(); 
		int last  = Math.min(limit,sz);
		for(int i=0; i<last; i++) {
			a.add(get(i));
		}
		return a;
	}

	
	public default JsonArray skipLimit(int skip, int limit) {
		JsonArray a = factory().createArray();
		int sz = size(); 
		int first = Math.max(0,skip);
		int last  = (int)Math.min((long)first+limit,sz); // first+limit overflows for Integer.MAX_VALUE
		for(int i=first; i<last; i++) {
			a.add(get(i));
		}
		return a;
	}

	

//	@Override
//	public abstract void forEach(Consumer<Object> action);
	public default JsonArray filter(Predicate<Object> predicate) {
        JsonArray r = factory().createArray();
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(predicate.test(value)) {
        		r.add(value);
        	}
        }
        return r;
    }

	/**
	 * The items that do not match, in a new array: the opposite of {@link #filter}. The
	 * array is unchanged (use {@link #removeIf} to remove the items from it).
	 */
	public default JsonArray reject(Predicate<Object> predicate) {
        JsonArray r = factory().createArray();
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(!predicate.test(value)) {
        		r.add(value);
        	}
        }
        return r;   
    }

	public default JsonArray takeWhile(Predicate<Object> predicate) {
        JsonArray r = factory().createArray();
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(predicate.test(value)) {
        		r.add(value);
        	} else {
        		break;
        	}
        }
        return r;
    }

	public default JsonArray dropWhile(Predicate<Object> predicate) {
        JsonArray r = factory().createArray();
    	int sz = size();
    	boolean drop = true;
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(drop) {
        		if(predicate.test(value)) {
        			continue;
        		}
        		drop = false;
        	}
        	r.add(value);
        }
        return r;
    }

	public default JsonArray map(Function<Object, Object> mapper) {
        JsonArray r = factory().createArray();
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	Object res = mapper.apply(value);
       		r.add(res);
        }
        return r;
    }

	public default JsonArray peek(Consumer<Object> action) {
    	int sz = size();
		for(int i=0; i<sz; i++) {
        	Object value = get(i);
			action.accept(value);
		}
		return this;    
	}

	public default <R> R process(Function<JsonArray, R> processor) {
		return processor.apply(this);
	}


	public default JsonArray distinct() {
    	JsonArray a = factory().createArray();
    	int sz = size();
    	if(sz==1) {
    		a.add(get(0));
    	} else if(sz>1) { 
    		// Hashed with the JSON equality (JsonUtil.eq/hashCode: 1 and 1.0 are the same value)
    		Set<JsonValueKey> seen = new HashSet<>();
	    	for(int i=0; i<sz; i++) {
	    		Object value = get(i);
	    		if(seen.add(new JsonValueKey(value))) {
	    			a.add(value);
	    		}
	    	}
    	}
    	return a;
    }

	public default JsonArray distinct(Comparator<Object> comp) {
    	// We can't use a set because we want to use a JSON like comparator
    	// Could be more optimized than n!...
    	JsonArray a = factory().createArray();
    	int sz = size();
    	if(sz==0) {
    		// nothing
    	} else if(sz==1) {
    		a.add(get(0));
    	} else if(sz>1) { 
	    	for(int i=0; i<sz; i++) {
	    		Object value = get(i);
	    		if(!containsValue(a,value,comp)) {
	    			a.add(value);
	    		}
	    	}
    	}
    	return a;
    }

    
	public default JsonArray distinct(Function<Object,Object> keyValue) {
    	JsonArray a = factory().createArray();
    	int sz = size();
    	if(sz==0) {
    		// nothing
    	} else if(sz==1) {
    		a.add(get(0));
    	} else if(sz>1) { 
        	Set<Object> keys = new HashSet<>();
	    	for(int i=0; i<sz; i++) {
	    		Object value = get(i);
	    		Object key = keyValue.apply(value);
	    		if(!keys.contains(key)) {
	    			a.add(value);
	    			keys.add(key);
	    		}
	    	}
    	}
    	return a;
    }

    
    private static boolean containsValue(JsonArray a, Object value, Comparator<Object> comp) { // should be public?
    	int sz = a.size();
    	for(int i=0; i<sz; i++) {
    		if(comp.compare(a.get(i),value)==0) {
    			return true;
    		}
    	}
    	return false;
    }


	public default Object min() {
    	int sz = size();
    	if(sz>0) {
    		Object v=get(0);
	    	for(int i=1; i<sz; i++) {
	    		Object value = get(i);
	    		if(JsonUtil.compare(value,v)<0) {
	    			v = value;
	    		}
	    	}
	    	return v;
    	}
    	return null;
	}

	public default Object min(Comparator<Object> comp) {
    	int sz = size();
    	if(sz>0) {
    		Object v=get(0);
	    	for(int i=1; i<sz; i++) {
	    		Object value = get(i);
	    		if(comp.compare(value, v)<0) {
	    			v = value;
	    		}
	    	}
	    	return v;
    	}
    	return null;
	}

	public default Object max() {
    	int sz = size();
    	if(sz>0) {
    		Object v=get(0);
	    	for(int i=1; i<sz; i++) {
	    		Object value = get(i);
	    		if(JsonUtil.compare(value, v)>0) {
	    			v = value;
	    		}
	    	}
	    	return v;
    	}
    	return null;
	}

	public default Object max(Comparator<Object> comp) {
    	int sz = size();
    	if(sz>0) {
    		Object v=get(0);
	    	for(int i=1; i<sz; i++) {
	    		Object value = get(i);
	    		if(comp.compare(value, v)>0) {
	    			v = value;
	    		}
	    	}
	    	return v;
    	}
    	return null;
	}
	
	public default boolean anyMatch(Predicate<Object> predicate) {
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(predicate.test(value)) {
        		return true;
        	}
        }
        return false;
	}

	public default boolean allMatch(Predicate<Object> predicate) {
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(!predicate.test(value)) {
        		return false;
        	}
        }
        return true;
	}

	public default boolean noneMatch(Predicate<Object> predicate) {
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(predicate.test(value)) {
        		return false;
        	}
        }
        return true;
	}


    
    //
    // Functions that return a value
    //
	public default <U> U reduce(Reducer<U,Object> reducer) {
		return reduce(reducer.reducer(), reducer.initialValue());
    }

	public default <U> U reduce(BiFunction<U, Object, U> reducer, U initialValue) {
    	U accumulator = initialValue;
    	int sz = size();
		for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	accumulator = reducer.apply(accumulator,value);
		}
		return accumulator;
    }

	
	/**
	 * Whether the index designates an item: 0 to size()-1, as for any {@link List}.
	 * The index of the methods of a JsonArray follows the List contract; the at*()
	 * methods ({@link #at(int)}, {@link #hasAt(int)}...) also accept negative indexes.
	 */
	public default boolean has(int index) {
		return index>=0 && index<size();
	}
	public default JsonValues jsonValues(int index) {
		return JsonValues.of(get(index));
	}

	/**
	 * The item at an index, or the default value for an index out of range (a null item
	 * is returned as is), like {@link java.util.Map#getOrDefault}.
	 */
	@SuppressWarnings("unchecked")
	public default <T> T getOrDefault(int index, T defaultValue) {
		if(has(index)) {
			return (T)get(index);
		}
		return defaultValue;
	}
	/**
	 * The item at an index, which must exist and not be null (a JsonException otherwise),
	 * like {@link JsonObject#getValue(String)}.
	 */
	public default <T> T getValue(int index) {
		@SuppressWarnings("unchecked")
		T t = (T)item(index);
		if(t==null) {
			throw new JsonException(null,"Value is null at index {0}",index);
		}
		return t;
	}
	/**
	 * The item at an index, or the default value when it is null or the index out of
	 * range, like {@link JsonObject#getValueOrDefault(String, Object)}.
	 */
	public default <T> T getValueOrDefault(int index, T defaultValue) {
		@SuppressWarnings("unchecked")
		T t = (T)peek(index);
		if(t==null) {
			return defaultValue;
		}
		return t;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public default <T> T firstValue() {
		if(isEmpty()) {
			throw new JsonException(null,"Collection is empty");
		}
		return (T)get(0);
	}
	@Override
	@SuppressWarnings("unchecked")
	public default <T> T firstValueOrDefault(T defaultValue) {
		if(isEmpty()) {
			return defaultValue;
		}
		return (T)get(0);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public default <T> T lastValue() {
		if(isEmpty()) {
			throw new JsonException(null,"Collection is empty");
		}
		return (T)get(size()-1);
	}
	@Override
	@SuppressWarnings("unchecked")
	public default <T> T lastValueOrDefault(T defaultValue) {
		if(isEmpty()) {
			return defaultValue;
		}
		return (T)get(size()-1);
	}


	

	
	
	
	
	//
	// Some conversion methods
	// These methods are *never* failing but return the default value in case of
	// a missing (out of range) index, a null or an unconvertible value
	//
	
	public default Number asNumber(int index) {
		return JsonUtil.asNumber(getOrDefault(index,null));
	}
	public default Number asNumber(int index, int defaultValue) {
		return JsonUtil.asNumber(getOrDefault(index,null),defaultValue);
	}
	public default Number asNumber(int index, Number defaultValue) {
		return JsonUtil.asNumber(getOrDefault(index,null),defaultValue);
	}

	
	public default int asInt(int index) {
		return JsonUtil.asInt(getOrDefault(index,null));
	}
	public default int asInt(int index, int defaultValue) {
		return JsonUtil.asInt(getOrDefault(index,null),defaultValue);
	}
	
	public default long asLong(int index) {
		return JsonUtil.asLong(getOrDefault(index,null));
	}
	public default long asLong(int index, long defaultValue) {
		return JsonUtil.asLong(getOrDefault(index,null),defaultValue);
	}
	
	public default double asDouble(int index) {
		return JsonUtil.asDouble(getOrDefault(index,null));
	}
	public default double asDouble(int index, double defaultValue) {
		return JsonUtil.asDouble(getOrDefault(index,null),defaultValue);
	}
	
	public default BigInteger asBigInteger(int index) {
		return JsonUtil.asBigInteger(getOrDefault(index,null));
	}
	public default BigInteger asBigInteger(int index, BigInteger defaultValue) {
		return JsonUtil.asBigInteger(getOrDefault(index,null),defaultValue);
	}
	
	public default BigDecimal asBigDecimal(int index) {
		return JsonUtil.asBigDecimal(getOrDefault(index,null));
	}
	public default BigDecimal asBigDecimal(int index, BigDecimal defaultValue) {
		return JsonUtil.asBigDecimal(getOrDefault(index,null),defaultValue);
	}
	
	public default boolean asBoolean(int index) {
		return JsonUtil.asBoolean(getOrDefault(index,null));
	}
	public default boolean asBoolean(int index, boolean defaultValue) {
		return JsonUtil.asBoolean(getOrDefault(index,null),defaultValue);
	}
	
	public default String asString(int index) {
		return JsonUtil.asString(getOrDefault(index,null));
	}
	public default String asString(int index, String defaultValue) {
		return JsonUtil.asString(getOrDefault(index,null),defaultValue);
	}
	
	
	public default JsonArray addAllValues(Iterator<?> it) {
		while(it.hasNext()) {
			add(it.next());
		}
		return this;
	}
	public default JsonArray addAllValues(Iterable<?> it) {
		return addAllValues(it.iterator());
	}
	
	

	public default JsonArray addValue(Object value) {
		add(value);
		return this;
	}

	
	
	

	
	
		
	
	


	/////////////////////////////////////////////////////////////////
	//
	// Negative indexes: the at*() methods
	// An index counts from the end when it is negative: -1 is the last item, -size()
	// the first one. These methods translate the index and call the regular method of
	// the same name (at() for get(), atInt() for getInt(), addAt() for add()...), so they
	// behave exactly the same otherwise. They are not meant to be overridden.
	//
	/////////////////////////////////////////////////////////////////

	/**
	 * The index designated by a possibly negative index, counted from the end: -1 is the
	 * last item. A non negative index is returned as is.
	 */
	private int fromEnd(int index) {
		return index<0 ? index + size() : index;
	}

	public default boolean hasAt(int index) {
		return has(fromEnd(index));
	}
	public default Object at(int index) {
		return get(fromEnd(index));
	}
	public default <T> T atOrDefault(int index, T defaultValue) {
		return getOrDefault(fromEnd(index), defaultValue);
	}
	public default <T> T atValue(int index) {
		return getValue(fromEnd(index));
	}
	public default <T> T atValueOrDefault(int index, T defaultValue) {
		return getValueOrDefault(fromEnd(index), defaultValue);
	}
	public default JsonValues jsonValuesAt(int index) {
		return jsonValues(fromEnd(index));
	}
	/**
	 * Remove the item at an index, possibly negative: {@link List#remove(int)}.
	 * @return the removed item
	 */
	public default Object removeAt(int index) {
		return remove(fromEnd(index));
	}
	public default boolean isNullAt(int index) {
		return isNull(fromEnd(index));
	}
	public default boolean isBooleanAt(int index) {
		return isBoolean(fromEnd(index));
	}
	public default boolean isNumberAt(int index) {
		return isNumber(fromEnd(index));
	}
	public default boolean isStringAt(int index) {
		return isString(fromEnd(index));
	}
	public default boolean isContainerAt(int index) {
		return isContainer(fromEnd(index));
	}
	public default boolean isObjectAt(int index) {
		return isObject(fromEnd(index));
	}
	public default boolean isArrayAt(int index) {
		return isArray(fromEnd(index));
	}

	public default boolean atBoolean(int index) {
		return getBoolean(fromEnd(index));
	}
	public default byte atByte(int index) {
		return getByte(fromEnd(index));
	}
	public default short atShort(int index) {
		return getShort(fromEnd(index));
	}
	public default int atInt(int index) {
		return getInt(fromEnd(index));
	}
	public default long atLong(int index) {
		return getLong(fromEnd(index));
	}
	public default float atFloat(int index) {
		return getFloat(fromEnd(index));
	}
	public default double atDouble(int index) {
		return getDouble(fromEnd(index));
	}
	public default Boolean atBooleanObject(int index) {
		return getBooleanObject(fromEnd(index));
	}
	public default Number atNumber(int index) {
		return getNumber(fromEnd(index));
	}
	public default Byte atByteObject(int index) {
		return getByteObject(fromEnd(index));
	}
	public default Short atShortObject(int index) {
		return getShortObject(fromEnd(index));
	}
	public default Integer atIntObject(int index) {
		return getIntObject(fromEnd(index));
	}
	public default Long atLongObject(int index) {
		return getLongObject(fromEnd(index));
	}
	public default Float atFloatObject(int index) {
		return getFloatObject(fromEnd(index));
	}
	public default Double atDoubleObject(int index) {
		return getDoubleObject(fromEnd(index));
	}
	public default BigInteger atBigInteger(int index) {
		return getBigInteger(fromEnd(index));
	}
	public default BigDecimal atBigDecimal(int index) {
		return getBigDecimal(fromEnd(index));
	}
	public default String atString(int index) {
		return getString(fromEnd(index));
	}
	public default JsonObject atObject(int index) {
		return getObject(fromEnd(index));
	}
	public default JsonArray atArray(int index) {
		return getArray(fromEnd(index));
	}
	public default LocalDate atLocalDate(int index) {
		return getLocalDate(fromEnd(index));
	}
	public default LocalTime atLocalTime(int index) {
		return getLocalTime(fromEnd(index));
	}
	public default LocalDateTime atLocalDateTime(int index) {
		return getLocalDateTime(fromEnd(index));
	}
	public default OffsetTime atOffsetTime(int index) {
		return getOffsetTime(fromEnd(index));
	}
	public default OffsetDateTime atOffsetDateTime(int index) {
		return getOffsetDateTime(fromEnd(index));
	}
	public default ZonedDateTime atZonedDateTime(int index) {
		return getZonedDateTime(fromEnd(index));
	}

	public default boolean atBoolean(int index, boolean defaultValue) {
		return getBoolean(fromEnd(index), defaultValue);
	}
	public default byte atByte(int index, byte defaultValue) {
		return getByte(fromEnd(index), defaultValue);
	}
	public default short atShort(int index, short defaultValue) {
		return getShort(fromEnd(index), defaultValue);
	}
	public default int atInt(int index, int defaultValue) {
		return getInt(fromEnd(index), defaultValue);
	}
	public default long atLong(int index, long defaultValue) {
		return getLong(fromEnd(index), defaultValue);
	}
	public default float atFloat(int index, float defaultValue) {
		return getFloat(fromEnd(index), defaultValue);
	}
	public default double atDouble(int index, double defaultValue) {
		return getDouble(fromEnd(index), defaultValue);
	}
	public default Boolean atBooleanObject(int index, Boolean defaultValue) {
		return getBooleanObject(fromEnd(index), defaultValue);
	}
	public default Number atNumber(int index, Number defaultValue) {
		return getNumber(fromEnd(index), defaultValue);
	}
	public default Byte atByteObject(int index, Byte defaultValue) {
		return getByteObject(fromEnd(index), defaultValue);
	}
	public default Short atShortObject(int index, Short defaultValue) {
		return getShortObject(fromEnd(index), defaultValue);
	}
	public default Integer atIntObject(int index, Integer defaultValue) {
		return getIntObject(fromEnd(index), defaultValue);
	}
	public default Long atLongObject(int index, Long defaultValue) {
		return getLongObject(fromEnd(index), defaultValue);
	}
	public default Float atFloatObject(int index, Float defaultValue) {
		return getFloatObject(fromEnd(index), defaultValue);
	}
	public default Double atDoubleObject(int index, Double defaultValue) {
		return getDoubleObject(fromEnd(index), defaultValue);
	}
	public default BigInteger atBigInteger(int index, BigInteger defaultValue) {
		return getBigInteger(fromEnd(index), defaultValue);
	}
	public default BigDecimal atBigDecimal(int index, BigDecimal defaultValue) {
		return getBigDecimal(fromEnd(index), defaultValue);
	}
	public default String atString(int index, String defaultValue) {
		return getString(fromEnd(index), defaultValue);
	}
	public default JsonObject atObject(int index, JsonObject defaultValue) {
		return getObject(fromEnd(index), defaultValue);
	}
	public default JsonArray atArray(int index, JsonArray defaultValue) {
		return getArray(fromEnd(index), defaultValue);
	}
	public default LocalDate atLocalDate(int index, LocalDate defaultValue) {
		return getLocalDate(fromEnd(index), defaultValue);
	}
	public default LocalTime atLocalTime(int index, LocalTime defaultValue) {
		return getLocalTime(fromEnd(index), defaultValue);
	}
	public default LocalDateTime atLocalDateTime(int index, LocalDateTime defaultValue) {
		return getLocalDateTime(fromEnd(index), defaultValue);
	}
	public default OffsetTime atOffsetTime(int index, OffsetTime defaultValue) {
		return getOffsetTime(fromEnd(index), defaultValue);
	}
	public default OffsetDateTime atOffsetDateTime(int index, OffsetDateTime defaultValue) {
		return getOffsetDateTime(fromEnd(index), defaultValue);
	}
	public default ZonedDateTime atZonedDateTime(int index, ZonedDateTime defaultValue) {
		return getZonedDateTime(fromEnd(index), defaultValue);
	}

	public default Number asNumberAt(int index) {
		return asNumber(fromEnd(index));
	}
	public default Number asNumberAt(int index, int defaultValue) {
		return asNumber(fromEnd(index), defaultValue);
	}
	public default Number asNumberAt(int index, Number defaultValue) {
		return asNumber(fromEnd(index), defaultValue);
	}
	public default int asIntAt(int index) {
		return asInt(fromEnd(index));
	}
	public default int asIntAt(int index, int defaultValue) {
		return asInt(fromEnd(index), defaultValue);
	}
	public default long asLongAt(int index) {
		return asLong(fromEnd(index));
	}
	public default long asLongAt(int index, long defaultValue) {
		return asLong(fromEnd(index), defaultValue);
	}
	public default double asDoubleAt(int index) {
		return asDouble(fromEnd(index));
	}
	public default double asDoubleAt(int index, double defaultValue) {
		return asDouble(fromEnd(index), defaultValue);
	}
	public default BigInteger asBigIntegerAt(int index) {
		return asBigInteger(fromEnd(index));
	}
	public default BigInteger asBigIntegerAt(int index, BigInteger defaultValue) {
		return asBigInteger(fromEnd(index), defaultValue);
	}
	public default BigDecimal asBigDecimalAt(int index) {
		return asBigDecimal(fromEnd(index));
	}
	public default BigDecimal asBigDecimalAt(int index, BigDecimal defaultValue) {
		return asBigDecimal(fromEnd(index), defaultValue);
	}
	public default boolean asBooleanAt(int index) {
		return asBoolean(fromEnd(index));
	}
	public default boolean asBooleanAt(int index, boolean defaultValue) {
		return asBoolean(fromEnd(index), defaultValue);
	}
	public default String asStringAt(int index) {
		return asString(fromEnd(index));
	}
	public default String asStringAt(int index, String defaultValue) {
		return asString(fromEnd(index), defaultValue);
	}

	// A negative insertion index counts from the end too: -1 inserts before the last
	// item, as add(size()-1, value) does
	public default JsonArray addValueAt(int index, Object value) {
		return addValue(fromEnd(index), value);
	}
	public default JsonArray addNullAt(int index) {
		return addNull(fromEnd(index));
	}
	public default JsonArray addAt(int index, boolean value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, byte value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, short value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, int value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, long value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, float value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, double value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, Boolean value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, Number value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, String value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, JsonObject value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, JsonArray value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, LocalDate value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, LocalTime value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, LocalDateTime value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, OffsetTime value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, OffsetDateTime value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, ZonedDateTime value) {
		return add(fromEnd(index), value);
	}
	public default JsonArray addAt(int index, ObjectConsumer action) {
		return add(fromEnd(index), action);
	}
	public default JsonArray addAt(int index, ArrayConsumer action) {
		return add(fromEnd(index), action);
	}

	public default JsonArray setValueAt(int index, Object value) {
		return setValue(fromEnd(index), value);
	}
	public default JsonArray setNullAt(int index) {
		return setNull(fromEnd(index));
	}
	public default JsonArray setAt(int index, boolean value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, byte value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, short value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, int value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, long value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, float value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, double value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, Boolean value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, Number value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, String value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, JsonObject value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, JsonArray value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, LocalDate value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, LocalTime value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, LocalDateTime value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, OffsetTime value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, OffsetDateTime value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, ZonedDateTime value) {
		return set(fromEnd(index), value);
	}
	public default JsonArray setAt(int index, ObjectConsumer action) {
		return set(fromEnd(index), action);
	}
	public default JsonArray setAt(int index, ArrayConsumer action) {
		return set(fromEnd(index), action);
	}

	/////////////////////////////////////////////////////////////////
	//
	// Extended Iterators
	//
	/////////////////////////////////////////////////////////////////
	
	@SuppressWarnings("unchecked")
	public default Iterable<Boolean> booleanIterable() {
		return (Iterable<Boolean>)(Iterable<?>)this;
	}
	
	@SuppressWarnings("unchecked")
	public default Iterable<Number> numberIterable() {
		return (Iterable<Number>)(Iterable<?>)this;
	}
	
	@SuppressWarnings("unchecked")
	public default Iterable<String> stringIterable() {
		return (Iterable<String>)(Iterable<?>)this;
	}
	
	@SuppressWarnings("unchecked")
	public default Iterable<JsonObject> objectIterable() {
		return (Iterable<JsonObject>)(Iterable<?>)this;
	}
	
	@SuppressWarnings("unchecked")
	public default Iterable<JsonArray> arrayIterable() {
		return (Iterable<JsonArray>)(Iterable<?>)this;
	}
	
	

	/////////////////////////////////////////////////////////////////
	//
	// Utilities
	//
	/////////////////////////////////////////////////////////////////
	
	public default JsonArray growTo(int size) {
		while(size()<size) {
			addNull();
		}
		return this;
	}
	
	
	
	/////////////////////////////////////////////////////////////////
	//
	// JavaScript like functions
	//
	/////////////////////////////////////////////////////////////////


	
	/////////////////////////////////////////////////////////////////
	//
	// Stream like functions
	// This functions returns copies of the arrays, so the initial
	// arrays are untouched.
	// Even when the target array equals the source, a copy is made
	// do the target array can be updated without touching the source
	//
	/////////////////////////////////////////////////////////////////



	// Similar to sort but works on a copy
	




}
