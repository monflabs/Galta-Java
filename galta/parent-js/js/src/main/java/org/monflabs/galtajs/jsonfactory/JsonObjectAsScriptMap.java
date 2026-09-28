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
package org.monflabs.galtajs.jsonfactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JavaJsonContainer;
import org.monflabs.json.jsonpath.JsonValues;


/**
 * Json Object implemented as a Map wrapper.
 * 
 * Content copied: from org.monflabs.json.java.JsonObjectAsLinkedMap on top of BaseJsonObjectMap 
 */
public abstract class JsonObjectAsScriptMap extends StringPropertyMap implements JsonObject, JavaJsonContainer, JSObjectInternal {
	
	private String reference;

	public JsonObjectAsScriptMap() {
	}

	@Override
	public JsonObjectAsScriptMap clone() {
		return (JsonObjectAsScriptMap)super.clone();
	}

	@Override
	public boolean equals(Object o) {
		if(this==o) {
			return true;
		}
		if(o instanceof JsonObject jo) {
			return JsonUtil.equalsObject(this, jo);
		}
		return false;
	}

	@Override
	public JsonValues jsonValues() {
		return JsonValues.of(this);
	}
	
	@Override
	public String toString() {
		return stringify(false);
	}
	
	@Override
	public Map<String,Object> toNativeJsonPrimitive() {
		return this;
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
		return JsonUtil.checkBoolean(v);
	}
	@Override
	public Number getNumber(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkNumber(v);
	}
	@Override
	public byte getByte(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.byteValue();
		}
		// Generate an error message...
		return JsonUtil.checkByte(v);
	}
	@Override
	public short getShort(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.shortValue();
		}
		// Generate an error message...
		return JsonUtil.checkShort(v);
	}
	@Override
	public int getInt(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.intValue();
		}
		// Generate an error message...
		return JsonUtil.checkInt(v);
	}
	@Override
	public long getLong(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.longValue();
		}
		// Generate an error message...
		return JsonUtil.checkLong(v);
	}
	@Override
	public float getFloat(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.floatValue();
		}
		// Generate an error message...
		return JsonUtil.checkFloat(v);
	}
	@Override
	public double getDouble(String key) {
		Object v=get(key);
		if(v instanceof Number o) {
			return o.doubleValue();
		}
		// Generate an error message...
		return JsonUtil.checkDouble(v);
	}
	
	@Override
	public Boolean getBooleanObject(String key) {
		Object v=get(key);
		if(v instanceof Boolean o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkBoolean(v);
	}
	@Override
	public Byte getByteObject(String key) {
		Object v=get(key);
		if(v instanceof Byte o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.byteValue();
		}
		// Generate an error message...
		return JsonUtil.checkByte(v);
	}
	@Override
	public Short getShortObject(String key) {
		Object v=get(key);
		if(v instanceof Short o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.shortValue();
		}
		// Generate an error message...
		return JsonUtil.checkShort(v);
	}
	@Override
	public Integer getIntObject(String key) {
		Object v=get(key);
		if(v instanceof Integer o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.intValue();
		}
		// Generate an error message...
		return JsonUtil.checkInt(v);
	}
	@Override
	public Long getLongObject(String key) {
		Object v=get(key);
		if(v instanceof Long o) {
			return o;
		}
		if(v instanceof Number o) {
			return o.longValue();
		}
		// Generate an error message...
		return JsonUtil.checkLong(v);
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
		return JsonUtil.checkFloat(v);
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
		return JsonUtil.checkDouble(v);
	}
	@Override
	public BigInteger getBigInteger(String key) {
		Object v=get(key);
		if(v instanceof BigInteger o) {
			return o;
		}
		return JsonUtil.checkBigInteger(v);
	}	
	@Override
	public BigDecimal getBigDecimal(String key) {
		Object v=get(key);
		if(v instanceof BigDecimal o) {
			return o;
		}
		return JsonUtil.checkBigDecimal(v);
	}	
	@Override
	public String getString(String key) {
		Object v=get(key);
		if(v instanceof String o) {
			return o;
		}
		// Generate an error message...
		return JsonUtil.checkString(v);
	}
	@Override
	public JsonObject getObject(String key) {
		Object v=get(key);
		if(v instanceof JsonObject o) {
			return o;
		}
		return JsonUtil.checkObject(v);
	}
	@Override
	public JsonArray getArray(String key) {
		Object v=get(key);
		if(v instanceof JsonArray a) {
			return a;
		}
		return JsonUtil.checkArray(v);
	}

	@Override
	public boolean getBoolean(String key, boolean defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Boolean o) {
				return o;
			}
			// Generate an error message...
			return JsonUtil.checkBoolean(v);
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
			return JsonUtil.checkNumber(v);
		}
		return defaultValue;
	}
	@Override
	public byte getByte(String key, byte defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return o.byteValue();
			}
			// Generate an error message...
			return JsonUtil.checkByte(v);
		}
		return defaultValue;
	}
	@Override
	public short getShort(String key, short defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return o.shortValue();
			}
			// Generate an error message...
			return JsonUtil.checkShort(v);
		}
		return defaultValue;
	}
	@Override
	public int getInt(String key, int defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return o.intValue();
			}
			// Generate an error message...
			return JsonUtil.checkInt(v);
		}
		return defaultValue;
	}
	@Override
	public long getLong(String key, long defaultValue) {
		Object v=get(key);
		if(v!=null) {
			if(v instanceof Number o) {
				return o.longValue();
			}
			// Generate an error message...
			return JsonUtil.checkLong(v);
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
			return JsonUtil.checkFloat(v);
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
			return JsonUtil.checkDouble(v);
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
			return JsonUtil.checkBoolean(v);
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
				return o.byteValue();
			}
			// Generate an error message...
			return JsonUtil.checkByte(v);
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
				return o.shortValue();
			}
			// Generate an error message...
			return JsonUtil.checkShort(v);
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
				return o.intValue();
			}
			// Generate an error message...
			return JsonUtil.checkInt(v);
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
				return o.longValue();
			}
			// Generate an error message...
			return JsonUtil.checkLong(v);
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
			return JsonUtil.checkFloat(v);
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
			return JsonUtil.checkDouble(v);
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
			return JsonUtil.checkBigInteger(v);
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
			return JsonUtil.checkBigDecimal(v);
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
			return JsonUtil.checkString(v);
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
			return JsonUtil.checkObject(v);
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
			return JsonUtil.checkArray(v);
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
