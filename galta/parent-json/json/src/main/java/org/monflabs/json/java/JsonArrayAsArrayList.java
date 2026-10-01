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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.util.Reducer;
import org.monflabs.util.StringUtil;


/**
 * Json Array implemented as an ArrayLisy.
 */
@SuppressWarnings("serial")
public class JsonArrayAsArrayList extends ArrayList<Object> implements JsonArray {

	private String reference;

	public JsonArrayAsArrayList() {
	}

	public JsonArrayAsArrayList(int initialCapacity) {
		super(initialCapacity);
	}

	@Override
	public JsonArray clone() {
		return (JsonArray)super.clone();
	}
	
	@Override
	public boolean equals(Object o) {
		if(this==o) {
			return true;
		}
		if(o instanceof JsonArray l) {
			return JsonUtil.equalsArray(this, l);
		}
		// Any other List: the List contract, so the equality is symmetric with the JDK lists
		if(o instanceof List<?>) {
			return super.equals(o);
		}
		return false;
	}

	@Override
	public int hashCode() {
		// Consistent with equals(): [1] and [1.0] are equal, so they must hash the same
		return JsonUtil.hashCode(this);
	}

	@Override
	public boolean isObject() {
		return false;
	}

	@Override
	public boolean isArray() {
		return true;
	}	

	@Override
	public JsonValues jsonValues() {
		return JsonValues.of(this);
	}
	
	@Override
	public String getReference() {
		return reference;
	}
	@Override
	public  void setReference(String reference) {
		this.reference = reference;
	}
	
	
	@Override
	public String toString() {
		return stringify(false);
	}
	
	@Override
	public List<Object> toNativeJsonPrimitive() {
		return this;
	}
	
	@Override
	public JavaJsonFactory factory() {
		return JavaJsonFactory.instance;
	}

	@Override
	public Collection<Object> values() {
		return this;
	}

	
	// The is*() tests don't throw for an index out of range: like a missing key of an
	// object, there is no value (isNull() is true, the others false)
	private Object peek(int index) {
		return has(index) ? jsonGet(index) : null;
	}
	@Override
	public boolean isNull(int index) {
		Object v=peek(index);
		return v==null;
	}
	@Override
	public boolean isBoolean(int index) {
		Object v=peek(index);
		return v!=null && v.getClass()==Boolean.class;
	}
	@Override
	public boolean isNumber(int index) {
		Object v=peek(index);
		return v instanceof Number;
	}
	@Override
	public boolean isString(int index) {
		Object v=peek(index);
		return v!=null && v.getClass()==String.class;
	}
	@Override
	public boolean isContainer(int index) {
		Object v=peek(index);
		return v instanceof JsonObject || v instanceof JsonArray;
	}
	@Override
	public boolean isObject(int index) {
		Object v=peek(index);
		return v instanceof JsonObject;
	}
	@Override
	public boolean isArray(int index) {
		Object v=peek(index);
		return v instanceof JsonArray;
	}


	//
	// List methods that are implemented by the base class...
	// Adapt the index to support negative values
	// No need to transform the native values here

	// The java.util.List methods keep the List contract: a negative index throws an
	// IndexOutOfBoundsException. The JSON specific accessors (getInt(-1), set(-1, "x"),
	// addValue(-1, v)...) accept negative indexes, counted from the end.
	@Override
	public Object get(int index) {
		return super.get(index);
	}
    @Override
	public Object set(int index, Object value) {
		return super.set(index,value);
    }
    @Override
	public void add(int index, Object value) {
		super.add(index,value);
    }
    @Override
	public Object remove(int index) {
		return super.remove(index);
    }
    
    // JSON accessors: negative indexes are relative to the end.
    // They go through the List methods, so a subclass (Checked) still sees every change
    protected final Object jsonGet(int index) {
    	return get(actualIndex(index));
    }
    protected final Object jsonSet(int index, Object value) {
    	return set(actualIndex(index), value);
    }
    protected final void jsonAdd(int index, Object value) {
    	add(actualIndex(index), value);
    }


    // Original methods
	protected final Object _get(int index) {
		return super.get(index);
	}
	protected final Object _set(int index, Object value) {
		return super.set(index,value);
    }
	protected final void _add(int index, Object value) {
		super.add(index,value);
    }
	protected final Object _remove(int index) {
		return super.remove(index);
    }
    
    
    
    
	@Override
	public boolean getBoolean(int index) {
		Object v=jsonGet(index);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, index);
	}
	@Override
	public Number getNumber(int index) {
		Object v=jsonGet(index);
		if(v instanceof Number o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkNumber(v, index);
	}
	@Override
	public byte getByte(int index) {
		Object v=jsonGet(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToByte(o);
		}
		// Generate an error message...
		return JsonUtil.checkByte(v, index);
	}
	@Override
	public short getShort(int index) {
		Object v=jsonGet(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToShort(o);
		}
		// Generate an error message...
		return JsonUtil.checkShort(v, index);
	}
	@Override
	public int getInt(int index) {
		Object v=jsonGet(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToInt(o);
		}
		// Generate an error message...
		return JsonUtil.checkInt(v, index);
	}
	@Override
	public long getLong(int index) {
		Object v=jsonGet(index);
		if(v instanceof Number o) {
			return JsonUtil.clampToLong(o);
		}
		// Generate an error message...
		return JsonUtil.checkLong(v, index);
	}
	@Override
	public float getFloat(int index) {
		Object v=jsonGet(index);
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v, index);
	}
	@Override
	public double getDouble(int index) {
		Object v=jsonGet(index);
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v, index);
	}
	
	@Override
	public Boolean getBooleanObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v, index);
	}
	@Override
	public Byte getByteObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof Byte o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToByte(o);
		}
		// Generate an error message...
		return JsonUtil.checkByte(v, index);
	}
	@Override
	public Short getShortObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof Short o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToShort(o);
		}
		// Generate an error message...
		return JsonUtil.checkShort(v, index);
	}
	@Override
	public Integer getIntObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof Integer o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToInt(o);
		}
		// Generate an error message...
		return JsonUtil.checkInt(v, index);
	}
	@Override
	public Long getLongObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof Long o) {
			return o;
		}
		if(v instanceof Number o) {
			return JsonUtil.clampToLong(o);
		}
		// Generate an error message...
		return JsonUtil.checkLong(v, index);
	}
	@Override
	public Float getFloatObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof Float o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v, index);
	}
	@Override
	public Double getDoubleObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof Double o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v, index);
	}
	@Override
	public BigInteger getBigInteger(int index) {
		Object v=jsonGet(index);
		if(v instanceof BigInteger o) {
			return o;
		}
		return JsonUtil.checkBigInteger(v, index);
	}	
	@Override
	public BigDecimal getBigDecimal(int index) {
		Object v=jsonGet(index);
		if(v instanceof BigDecimal o) {
			return o;
		}
		return JsonUtil.checkBigDecimal(v, index);
	}	
	@Override
	public String getString(int index) {
		Object v=jsonGet(index);
		if(v instanceof String o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkString(v, index);
	}
	@Override
	public JsonObject getObject(int index) {
		Object v=jsonGet(index);
		if(v instanceof JsonObject o) {
			return o;
		}
		return JsonUtil.checkObject(v, index);
	}
	@Override
	public JsonArray getArray(int index) {
		Object v=jsonGet(index);
		if(v instanceof JsonArray a) {
			return a;
		}
		return JsonUtil.checkArray(v, index);
	}
	
	@Override
	public LocalDate getLocalDate(int index) {
		return JsonUtil.parseLocalDate(getString(index));
	}
	@Override
	public LocalTime getLocalTime(int index) {
		return JsonUtil.parseLocalTime(getString(index));
	}
	@Override
	public LocalDateTime getLocalDateTime(int index) {
		return JsonUtil.parseLocalDateTime(getString(index));
	}
	@Override
	public OffsetTime getOffsetTime(int index) {
		return JsonUtil.parseOffsetTime(getString(index));
	}
	@Override
	public OffsetDateTime getOffsetDateTime(int index) {
		return JsonUtil.parseOffsetDateTime(getString(index));
	}
	@Override
	public ZonedDateTime getZonedDateTime(int index) {
		return JsonUtil.parseZonedDateTime(getString(index));
	}


	@Override
	public boolean getBoolean(int index, boolean defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public Number getNumber(int index, Number defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public byte getByte(int index, byte defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public short getShort(int index, short defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public int getInt(int index, int defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public long getLong(int index, long defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public float getFloat(int index, float defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public double getDouble(int index, double defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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

	@Override
	public Boolean getBooleanObject(int index, Boolean defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public Byte getByteObject(int index, Byte defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public Short getShortObject(int index, Short defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public Integer getIntObject(int index, Integer defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public Long getLongObject(int index, Long defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public Float getFloatObject(int index, Float defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public Double getDoubleObject(int index, Double defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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

	
	@Override
	public BigInteger getBigInteger(int index, BigInteger defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public BigDecimal getBigDecimal(int index, BigDecimal defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public String getString(int index, String defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public JsonObject getObject(int index, JsonObject defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public JsonArray getArray(int index, JsonArray defaultValue) {
		if(has(index)) {
			Object v=jsonGet(index);
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
	@Override
	public LocalDate getLocalDate(int index, LocalDate defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseLocalDate(v) : defaultValue;
	}
	@Override
	public LocalTime getLocalTime(int index, LocalTime defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseLocalTime(v) : defaultValue;
	}
	@Override
	public LocalDateTime getLocalDateTime(int index, LocalDateTime defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseLocalDateTime(v) : defaultValue;
	}
	@Override
	public OffsetTime getOffsetTime(int index, OffsetTime defaultValue) {
		String v = getString(index,null);
		return v!=null? JsonUtil.parseOffsetTime(v) : defaultValue;
	}
	@Override
	public OffsetDateTime getOffsetDateTime(int index, OffsetDateTime defaultValue) {
		String v = getString(index,null);
		return v!=null? JsonUtil.parseOffsetDateTime(v) : defaultValue;
	}
	@Override
	public ZonedDateTime getZonedDateTime(int index, ZonedDateTime defaultValue) {
		String v = getString(index,null);
		return v!=null ? JsonUtil.parseZonedDateTime(v) : defaultValue;
	}

	
	@Override
	public JsonArray addValue(Object value) {
        add((Object)value);
		return this;
	}
	@Override
	public JsonArray addNull() {
		add((Object)null);
		return this;
	}
	@Override
	public JsonArray add(boolean value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(byte value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(short value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(int value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(long value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(float value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(double value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(Boolean value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(Number value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(String value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(JsonObject value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(JsonArray value) {
		add((Object)value);
		return this;
	}
	@Override
	public JsonArray add(LocalDate value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(LocalTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(LocalDateTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(OffsetTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(OffsetDateTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(ZonedDateTime value) {
		add(value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		add(jo);
		return this;
	}
	@Override
	public JsonArray add(ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		add(ja);
		return this;
	}	
	
	@Override
	public JsonArray addValue(int index, Object value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray addNull(int index) {
		jsonAdd(index,(Object)null);
		return this;
	}
	@Override
	public JsonArray add(int index, boolean value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, byte value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, short value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, int value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, long value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, float value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, double value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, Boolean value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, Number value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, String value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, JsonObject value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray add(int index, JsonArray value) {
		jsonAdd(index,(Object)value);
		return this;
	}
	
	@Override
	public JsonArray add(int index, LocalDate value) {
		jsonAdd(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(int index, LocalTime value) {
		jsonAdd(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(int index, LocalDateTime value) {
		jsonAdd(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray add(int index, OffsetTime value) {
		jsonAdd(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public  JsonArray add(int index, OffsetDateTime value) {
		jsonAdd(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public  JsonArray add(int index, ZonedDateTime value) {
		jsonAdd(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	
	@Override
	public JsonArray add(int index, ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		jsonAdd(index,jo);
		return this;
	}
	@Override
	public JsonArray add(int index, ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		jsonAdd(index,ja);
		return this;
	}
	
	@Override
	public JsonArray setValue(int index, Object value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray setNull(int index) {
		jsonSet(index,(Object)null);
		return this;
	}
	@Override
	public JsonArray set(int index, boolean value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, byte value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, short value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, int value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, long value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, float value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, double value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, Boolean value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, Number value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, String value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, JsonObject value) {
		jsonSet(index,(Object)value);
		return this;
	}
	@Override
	public JsonArray set(int index, JsonArray value) {
		jsonSet(index,(Object)value);
		return this;
	}
	
	@Override
	public JsonArray set(int index, LocalDate value) {
		jsonSet(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray set(int index, LocalTime value) {
		jsonSet(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray set(int index, LocalDateTime value) {
		jsonSet(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray set(int index, OffsetTime value) {
		jsonSet(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray set(int index, OffsetDateTime value) {
		jsonSet(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray set(int index, ZonedDateTime value) {
		jsonSet(index, value!=null ? JsonUtil.toString(value) : null);
		return this;
	}
	@Override
	public JsonArray set(int index, ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		jsonSet(index,jo);
		return this;
	}
	@Override
	public JsonArray set(int index, ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		jsonSet(index, ja);
		return this;
	}	

	
	
	/////////////////////////////////////////////////////////////////
	//
	// JavaScript like functions
	//
	/////////////////////////////////////////////////////////////////

	@Override
	public String join(char sep) {
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
	public JsonArray flat() {
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
    
	@Override
	public JsonArray slice(int start, int end) {
		return slice(start, end, 1);
	}
	@Override
	public JsonArray slice(Integer start, Integer end) {
		return slice(start, end, 1);
	}
	@Override
	public JsonArray slice(Integer start, Integer end, int step) {
		// A negative step walks backwards, so its defaults are the other way round
		int s = start!=null ? start : (step<0 ? size()-1 : 0);
		int e = end!=null ? end : (step<0 ? Integer.MIN_VALUE : Integer.MAX_VALUE);
		return slice(s, e, step);
	}
	@Override
	public JsonArray slice(int start, int end, int step) {
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
	@Override
	public JsonArray sorted() {
		return sorted(true);
	}
	@Override
	public JsonArray sorted(boolean asc) {
		return sorted(asc ? JsonUtil.jsonComparator : JsonUtil.jsonComparatorDesc);
	}
	@Override
	public JsonArray sorted(Comparator<Object> comp) {
		JsonArray a = factory().createArray();
		a.addAll(this);
		a.sort(comp);
		return a;
	}
	
	@Override
	public JsonArray skip(int skip) {
		JsonArray a = factory().createArray();
		int sz = size(); 
		for(int i=Math.max(0,skip); i<sz; i++) {
			a.add(get(i));
		}
		return a;
	}
	
	@Override
	public  JsonArray limit(int limit) {
		JsonArray a = factory().createArray();
		int sz = size(); 
		int last  = Math.min(limit,sz);
		for(int i=0; i<last; i++) {
			a.add(get(i));
		}
		return a;
	}
	
	@Override
	public JsonArray skipLimit(int skip, int limit) {
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
    @Override
	public JsonArray filter(Predicate<Object> predicate) {
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
    @Override
	public JsonArray remove(Predicate<Object> predicate) {
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
    @Override
	public JsonArray takeWhile(Predicate<Object> predicate) {
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
    @Override
	public JsonArray dropWhile(Predicate<Object> predicate) {
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
    @Override
	public JsonArray map(Function<Object, Object> mapper) {
        JsonArray r = factory().createArray();
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	Object res = mapper.apply(value);
       		r.add(res);
        }
        return r;
    }
    @Override
	public JsonArray peek(Consumer<Object> action) {
    	int sz = size();
		for(int i=0; i<sz; i++) {
        	Object value = get(i);
			action.accept(value);
		}
		return this;    
	}
    @Override
	public <R> R process(Function<JsonArray, R> processsor) {
		return processsor.apply(this);
	}

    @Override
	public JsonArray distinct() {
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
    /**
     * A value compared with the JSON equality, for hashed collections.
     */
    private static final class JsonValueKey {
    	private final Object value;
    	private final int hash;
    	JsonValueKey(Object value) {
    		this.value = value;
    		this.hash = JsonUtil.hashCode(value);
    	}
    	@Override
    	public int hashCode() {
    		return hash;
    	}
    	@Override
    	public boolean equals(Object o) {
    		return o instanceof JsonValueKey k && hash==k.hash && JsonUtil.eq(value, k.value);
    	}
    }
    @Override
	public JsonArray distinct(Comparator<Object> comp) {
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
    
    @Override
	public JsonArray distinct(Function<Object,Object> keyValue) {
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

	@Override
	public Object min() {
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
	@Override
	public Object min(Comparator<Object> comp) {
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
	@Override
	public Object max() {
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
	@Override
	public Object max(Comparator<Object> comp) {
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
	@Override
	public boolean anyMatch(Predicate<Object> predicate) {
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(predicate.test(value)) {
        		return true;
        	}
        }
        return false;
	}
	@Override
	public boolean allMatch(Predicate<Object> predicate) {
    	int sz = size();
        for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	if(!predicate.test(value)) {
        		return false;
        	}
        }
        return true;
	}
	@Override
	public boolean noneMatch(Predicate<Object> predicate) {
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
    @Override
	public <U> U reduce(Reducer<U,Object> reducer) {
		return reduce(reducer.reducer(), reducer.initialValue());
    }
    @Override
	public <U> U reduce(BiFunction<U, Object, U> reducer, U initialValue) {
    	U accumulator = initialValue;
    	int sz = size();
		for(int i=0; i<sz; i++) {
        	Object value = get(i);
        	accumulator = reducer.apply(accumulator,value);
		}
		return accumulator;
    }
    
    
	
}
