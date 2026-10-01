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
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.util.Reducer;

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

	public boolean isNull(int index);
	public boolean isBoolean(int index);
	public boolean isNumber(int index);
	public boolean isString(int index);
	public boolean isContainer(int index);
	public boolean isObject(int index);
	public boolean isArray(int index);

	
	public default boolean has(int index) {
		if(index<0) index += size();
		return index>=0 && index<size();
	}
	public default int actualIndex(int index) {
		return index<0 ? index + size() : index;
	}
	public default JsonValues jsonValues(int index) {
		return JsonValues.of(get(actualIndex(index)));
	}

	@SuppressWarnings("unchecked")
	public default <T> T getOrDefault(int index, T defaultValue) {
		if(has(index)) {
			return (T)get(actualIndex(index));
		}
		return defaultValue;
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


	public boolean getBoolean(int index);
	public byte getByte(int index);
	public short getShort(int index);
	public int getInt(int index);
	public long getLong(int index);
	public float getFloat(int index);
	public double getDouble(int index);
	public Boolean getBooleanObject(int index);
	public Number getNumber(int index);
	public Byte getByteObject(int index);
	public Short getShortObject(int index);
	public Integer getIntObject(int index);
	public Long getLongObject(int index);
	public Float getFloatObject(int index);
	public Double getDoubleObject(int index);
	public BigInteger getBigInteger(int index);
	public BigDecimal getBigDecimal(int index);
	public String getString(int index);
	public JsonObject getObject(int index);
	public JsonArray getArray(int index);
	
	public LocalDate getLocalDate(int index);
	public LocalTime getLocalTime(int index);
	public LocalDateTime getLocalDateTime(int index);
	public OffsetTime getOffsetTime(int index);
	public OffsetDateTime getOffsetDateTime(int index);
	public ZonedDateTime getZonedDateTime(int index);

	public boolean getBoolean(int index, boolean defaultValue);
	public byte getByte(int index, byte defaultValue);
	public short getShort(int index, short defaultValue);
	public int getInt(int index, int defaultValue);
	public long getLong(int index, long defaultValue);
	public float getFloat(int index, float defaultValue);
	public double getDouble(int index, double defaultValue);
	public Boolean getBooleanObject(int index, Boolean defaultValue);
	public Number getNumber(int index, Number defaultValue);
	public Byte getByteObject(int index, Byte defaultValue);
	public Short getShortObject(int index, Short defaultValue);
	public Integer getIntObject(int index, Integer defaultValue);
	public Long getLongObject(int index, Long defaultValue);
	public Float getFloatObject(int index, Float defaultValue);
	public Double getDoubleObject(int index, Double defaultValue);
	public BigInteger getBigInteger(int index, BigInteger defaultValue);
	public BigDecimal getBigDecimal(int index, BigDecimal defaultValue);
	public String getString(int index, String defaultValue);
	public JsonObject getObject(int index, JsonObject defaultValue);
	public JsonArray getArray(int index, JsonArray defaultValue);
	
	public LocalDate getLocalDate(int index, LocalDate defaultValue);
	public LocalTime getLocalTime(int index, LocalTime defaultValue);
	public LocalDateTime getLocalDateTime(int index, LocalDateTime defaultValue);
	public OffsetTime getOffsetTime(int index, OffsetTime defaultValue);
	public OffsetDateTime getOffsetDateTime(int index, OffsetDateTime defaultValue);
	public ZonedDateTime getZonedDateTime(int index, ZonedDateTime defaultValue);
	
	
	
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
	public JsonArray addNull();
	public JsonArray add(boolean value);
	public JsonArray add(byte value);
	public JsonArray add(short value);
	public JsonArray add(int value);
	public JsonArray add(long value);
	public JsonArray add(float value);
	public JsonArray add(double value);

	public JsonArray add(Boolean value);
	public JsonArray add(Number value);
	public JsonArray add(String value);
	public JsonArray add(JsonObject value);
	public JsonArray add(JsonArray value);
	
	public JsonArray add(LocalDate value);
	public JsonArray add(LocalTime value);
	public JsonArray add(LocalDateTime value);
	public JsonArray add(OffsetTime value);
	public JsonArray add(OffsetDateTime value);
	public JsonArray add(ZonedDateTime value);
	
	public JsonArray add(ObjectConsumer action);
	public JsonArray add(ArrayConsumer action);
	

	public JsonArray addValue(int index, Object value);
	public JsonArray addNull(int index);
	public JsonArray add(int index, boolean value);
	public JsonArray add(int index, byte value);
	public JsonArray add(int index, short value);
	public JsonArray add(int index, int value);
	public JsonArray add(int index, long value);
	public JsonArray add(int index, float value);
	public JsonArray add(int index, double value);
	public JsonArray add(int index, Boolean value);
	public JsonArray add(int index, Number value);
	public JsonArray add(int index, String value);
	public JsonArray add(int index, JsonObject value);
	public JsonArray add(int index, JsonArray value);
	
	public JsonArray add(int index, LocalDate value);
	public JsonArray add(int index, LocalTime value);
	public JsonArray add(int index, LocalDateTime value);
	public JsonArray add(int index, OffsetTime value);
	public JsonArray add(int index, OffsetDateTime value);
	public JsonArray add(int index, ZonedDateTime value);
	
	public JsonArray add(int index, ObjectConsumer action);
	public JsonArray add(int index, ArrayConsumer action);
		
	public JsonArray setValue(int index, Object value);
	public JsonArray setNull(int index);
	public JsonArray set(int index, boolean value);
	public JsonArray set(int index, byte value);
	public JsonArray set(int index, short value);
	public JsonArray set(int index, int value);
	public JsonArray set(int index, long value);
	public JsonArray set(int index, float value);
	public JsonArray set(int index, double value);
	public JsonArray set(int index, Boolean value);
	public JsonArray set(int index, Number value);
	public JsonArray set(int index, String value);
	public JsonArray set(int index, JsonObject value);
	public JsonArray set(int index, JsonArray value);
	
	public JsonArray set(int index, LocalDate value);
	public JsonArray set(int index, LocalTime value);
	public JsonArray set(int index, LocalDateTime value);
	public JsonArray set(int index, OffsetTime value);
	public JsonArray set(int index, OffsetDateTime value);
	public JsonArray set(int index, ZonedDateTime value);
	public JsonArray set(int index, ObjectConsumer action);
	public JsonArray set(int index, ArrayConsumer action);
	

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

	public String join(char sep);

	
	/////////////////////////////////////////////////////////////////
	//
	// Stream like functions
	// This functions returns copies of the arrays, so the initial
	// arrays are untouched.
	// Even when the target array equals the source, a copy is made
	// do the target array can be updated without touching the source
	//
	/////////////////////////////////////////////////////////////////

    @Override
	public JsonArray flat();

    public JsonArray slice(int start, int end);
	public JsonArray slice(Integer start, Integer end);
	public JsonArray slice(Integer start, Integer end, int step);
	public JsonArray slice(int start, int end, int step);

	// Similar to sort but works on a copy
	public JsonArray sorted();
	public JsonArray sorted(boolean asc);
	public JsonArray sorted(Comparator<Object> comp);
	
	public JsonArray skip(int skip);
	public JsonArray limit(int limit);
	public JsonArray skipLimit(int skip, int limit);

    public JsonArray filter(Predicate<Object> predicate);
    public JsonArray remove(Predicate<Object> predicate);
    public JsonArray takeWhile(Predicate<Object> predicate);
    public JsonArray dropWhile(Predicate<Object> predicate);
    public JsonArray map(Function<Object, Object> mapper);
    public JsonArray peek(Consumer<Object> action);
    public <R> R process(Function<JsonArray, R> processsor);

    public JsonArray distinct();
    public JsonArray distinct(Comparator<Object> comp);
    public JsonArray distinct(Function<Object,Object> keyValue);

	public Object min();
	public Object min(Comparator<Object> comp);
	public Object max();
	public Object max(Comparator<Object> comp);
	public boolean anyMatch(Predicate<Object> predicate);
	public boolean allMatch(Predicate<Object> predicate);
	public boolean noneMatch(Predicate<Object> predicate);

    public <U> U reduce(Reducer<U,Object> reducer);
    public <U> U reduce(BiFunction<U, Object, U> reducer, U initialValue);
}
