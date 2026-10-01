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
import java.util.AbstractMap;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;


/**
 * Base class for a JsonObject adapting a foreign object (a third-party library's object
 * node): the subclass gives access to the native storage (nativeGet(), nativePut()...)
 * and the factory converts the values.
 */
public abstract class AbstractJsonObject extends AbstractMap<String,Object> implements JsonObject {

	public AbstractJsonObject() {
	}

	/**
	 * A shallow copy, with its own native storage. Object.clone() would share the
	 * storage of this object (the copy and the original would see each other's
	 * changes), so the subclass must implement it.
	 */
	@Override
	public abstract JsonObject clone();

	@Override
	public boolean equals(Object o) {
		if(this==o) {
			return true;
		}
		if(o instanceof JsonObject jo) {
			return JsonUtil.equalsObject(this, jo);
		}
		// Any other Map: the Map contract, symmetric with the JDK maps
		if(o instanceof java.util.Map<?,?>) {
			return super.equals(o);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return JsonUtil.hashCode(this);
	}
	
	//
	// Methods to access the native objects
	//
	public abstract Object nativeGet(String key);
	public abstract Object nativePut(String key, Object value);
	public abstract Object nativeRemove(String key);
	/**
	 * Whether the key exists, even with a null value. The default implementation is a
	 * scan of the entries when the value is null: a subclass should override it when
	 * the native storage has a direct lookup.
	 */
	public boolean nativeContainsKey(String key) {
		if(!factory().isNativeNull(nativeGet(key))) {
			return true;
		}
		for(Entry<String,Object> e: entrySet()) {
			if(key.equals(e.getKey())) {
				return true;
			}
		}
		return false;
	}

	// A JSON object key cannot be null
	private static String checkKey(String key) {
		if(key==null) {
			throw new NullPointerException("A JSON object key cannot be null");
		}
		return key;
	}
	
	

	@Override
	public final JsonValues jsonValues() {
		return JsonValues.of(this);
	}

	@Override
	public String toString() {
		return stringify(false);
	}
	
	@Override
	public boolean containsKey(Object key) {
		return key instanceof String s && nativeContainsKey(s);
	}

	@Override
	public Object get(Object key) {
		// Map.get() contract: a key of another type is simply not there
		if(!(key instanceof String)) {
			return null;
		}
		Object v = nativeGet((String)key);
		if(v==null) {
			return null;
		}
		return factory().toJavaPrimitive(v);
	}
	/**
	 * Map.put() contract: returns the previous value (or null), not this object.
	 */
	@Override
    public final Object put(String key, Object value) {
		Object prev = nativePut(checkKey(key), factory().toNativeJsonPrimitive(value));
		return prev!=null ? factory().toJavaPrimitive(prev) : null;
    }
	@Override
    public final Object remove(Object key) {
		if(!(key instanceof String)) {
			return null;
		}
		Object r = nativeRemove((String)key);
		return factory().toJavaPrimitive(r);
    }
	
	
	
	//
	// implementations
	//
	
	@Override
	public boolean isNull(String key) {
		return factory().isNativeNull(nativeGet(key));
	}
	@Override
	public boolean isBoolean(String key) {
		return factory().isNativeBoolean(nativeGet(key));
	}
	@Override
	public boolean isNumber(String key) {
		return factory().isNativeNumber(nativeGet(key));
	}
	@Override
	public boolean isString(String key) {
		return factory().isNativeString(nativeGet(key));
	}
	@Override
	public boolean isContainer(String key) {
		return factory().isNativeContainer(nativeGet(key));
	}
	@Override
	public boolean isObject(String key) {
		return factory().isNativeObject(nativeGet(key));
	}
	@Override
	public boolean isArray(String key) {
		return factory().isNativeArray(nativeGet(key));	
	}
	
	@Override
	public boolean getBoolean(String key) {
		return factory().asBoolean(nativeGet(key));
	}
	@Override
	public byte getByte(String key) {
		return factory().asByte(nativeGet(key));
	}
	@Override
	public short getShort(String key) {
		return factory().asShort(nativeGet(key));
	}
	@Override
	public int getInt(String key) {
		return factory().asInt(nativeGet(key));
	}
	@Override
	public long getLong(String key) {
		return factory().asLong(nativeGet(key));
	}
	@Override
	public float getFloat(String key) {
		return factory().asFloat(nativeGet(key));
	}
	@Override
	public double getDouble(String key) {
		return factory().asDouble(nativeGet(key));
	}

	@Override
	public Boolean getBooleanObject(String key) {
		return factory().asBooleanObject(nativeGet(key));
	}
	@Override
	public Number getNumber(String key) {
		return factory().asNumber(nativeGet(key));
	}
	@Override
	public Byte getByteObject(String key) {
		return factory().asByteObject(nativeGet(key));
	}
	@Override
	public Short getShortObject(String key) {
		return factory().asShortObject(nativeGet(key));
	}
	@Override
	public Integer getIntObject(String key) {
		return factory().asIntObject(nativeGet(key));
	}
	@Override
	public Long getLongObject(String key) {
		return factory().asLongObject(nativeGet(key));
	}
	@Override
	public Float getFloatObject(String key) {
		return factory().asFloatObject(nativeGet(key));
	}
	@Override
	public Double getDoubleObject(String key) {
		return factory().asDoubleObject(nativeGet(key));
	}
	@Override
	public BigInteger getBigInteger(String key) {
		return factory().asBigInteger(nativeGet(key));
	}
	@Override
	public BigDecimal getBigDecimal(String key) {
		return factory().asBigDecimal(nativeGet(key));
	}
	
	@Override
	public String getString(String key) {
		return factory().asString(nativeGet(key));
	}
	@Override
	public JsonObject getObject(String key) {
		return factory().asObject(nativeGet(key));
	}
	@Override
	public JsonArray getArray(String key) {
		return factory().asArray(nativeGet(key));
	}
	
	@Override
	public boolean getBoolean(String key, boolean defaultValue) {
		Object v = nativeGet(key);
		return factory().asBoolean(v,defaultValue);
	}
	@Override
	public byte getByte(String key, byte defaultValue) {
		Object v = nativeGet(key);
		return factory().asByte(v,defaultValue);
	}
	@Override
	public short getShort(String key, short defaultValue) {
		Object v = nativeGet(key);
		return factory().asShort(v,defaultValue);
	}
	@Override
	public int getInt(String key, int defaultValue) {
		Object v = nativeGet(key);
		return factory().asInt(v,defaultValue);
	}
	@Override
	public long getLong(String key, long defaultValue) {
		Object v = nativeGet(key);
		return factory().asLong(v,defaultValue);
	}
	@Override
	public float getFloat(String key, float defaultValue) {
		Object v = nativeGet(key);
		return factory().asFloat(v,defaultValue);
	}
	@Override
	public double getDouble(String key, double defaultValue) {
		Object v = nativeGet(key);
		return factory().asDouble(v,defaultValue);
	}

	@Override
	public Boolean getBooleanObject(String key, Boolean defaultValue) {
		Object v = nativeGet(key);
		return factory().asBooleanObject(v,defaultValue);
	}
	@Override
	public Number getNumber(String key, Number defaultValue) {
		Object v = nativeGet(key);
		return factory().asNumber(v,defaultValue);
	}
	@Override
	public Byte getByteObject(String key, Byte defaultValue) {
		Object v = nativeGet(key);
		return factory().asByteObject(v,defaultValue);
	}
	@Override
	public Short getShortObject(String key, Short defaultValue) {
		Object v = nativeGet(key);
		return factory().asShortObject(v,defaultValue);
	}
	@Override
	public Integer getIntObject(String key, Integer defaultValue) {
		Object v = nativeGet(key);
		return factory().asIntObject(v,defaultValue);
	}
	@Override
	public Long getLongObject(String key, Long defaultValue) {
		Object v = nativeGet(key);
		return factory().asLongObject(v,defaultValue);
	}
	@Override
	public Float getFloatObject(String key, Float defaultValue) {
		Object v = nativeGet(key);
		return factory().asFloatObject(v,defaultValue);
	}
	@Override
	public Double getDoubleObject(String key, Double defaultValue) {
		Object v = nativeGet(key);
		return factory().asDoubleObject(v,defaultValue);
	}

	@Override
	public BigInteger getBigInteger(String key, BigInteger defaultValue) {
		Object v = nativeGet(key);
		return factory().asBigInteger(v,defaultValue);
	}	
	@Override
	public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
		Object v = nativeGet(key);
		return factory().asBigDecimal(v,defaultValue);
	}	
	
	@Override
	public String getString(String key, String defaultValue) {
		Object v = nativeGet(key);
		return factory().asString(v,defaultValue);
	}
	@Override
	public JsonObject getObject(String key, JsonObject defaultValue) {
		Object v = nativeGet(key);
		return factory().asObject(v,defaultValue);
	}
	@Override
	public JsonArray getArray(String key, JsonArray defaultValue) {
		Object v = nativeGet(key);
		return factory().asArray(v,defaultValue);
	}
	

	@Override
	public JsonObject putValue(String key, Object value) {
		//put(key, value);
		nativePut(checkKey(key), factory().toNativeJsonPrimitive(value));
		return this;
	}

	@Override
	public JsonObject putNull(String key) {
		//put(key, null);
		nativePut(checkKey(key), factory().toNativeNull());
		return this;
	}

	@Override
	public JsonObject put(String key, boolean value) {
		nativePut(checkKey(key), factory().toNativeBoolean(value));
		return this;
	}
	@Override
	public JsonObject put(String key, byte value) {
		nativePut(checkKey(key), factory().toNativeByte(value));
		return this;
	}
	@Override
	public JsonObject put(String key, short value) {
		nativePut(checkKey(key), factory().toNativeShort(value));
		return this;
	}
	@Override
	public JsonObject put(String key, int value) {
		nativePut(checkKey(key), factory().toNativeInt(value));
		return this;
	}
	@Override
	public JsonObject put(String key, long value) {
		nativePut(checkKey(key), factory().toNativeLong(value));
		return this;
	}
	@Override
	public JsonObject put(String key, float value) {
		nativePut(checkKey(key), factory().toNativeFloat(value));
		return this;
	}
	@Override
	public JsonObject put(String key, double value) {
		nativePut(checkKey(key), factory().toNativeDouble(value));
		return this;
	}

	@Override
	public JsonObject put(String key, Boolean value) {
		nativePut(checkKey(key), value!=null ? factory().toNativeBoolean(value) : factory().toNativeNull());
		return this;
	}
	@Override
	public JsonObject put(String key, String value) {
		nativePut(checkKey(key), value!=null ? factory().toNativeString(value) : factory().toNativeNull());
		return this;
	}
	@Override
	public JsonObject put(String key, Number value) {
		nativePut(checkKey(key), value!=null ? factory().toNativeNumber(value) : factory().toNativeNull());
		return this;
	}
	@Override
	public JsonObject put(String key, JsonObject value) {
		nativePut(checkKey(key), value!=null ? factory().toNativeObject(value) : factory().toNativeNull());
		return this;
	}
	@Override
	public JsonObject put(String key, JsonArray value) {
		nativePut(checkKey(key), value!=null ? factory().toNativeArray(value) : factory().toNativeNull());
		return this;
	}

	@Override
	public JsonObject put(String key, ObjectConsumer action) {
		JsonObject jo = factory().createObject();
		action.accept(jo);
		nativePut(checkKey(key), jo.toNativeJsonPrimitive());
		return this;
	}
	@Override
	public JsonObject put(String key, ArrayConsumer action) {
		JsonArray ja = factory().createArray();
		action.accept(ja);
		nativePut(checkKey(key), ja.toNativeJsonPrimitive());
		return this;
	}
	
}
