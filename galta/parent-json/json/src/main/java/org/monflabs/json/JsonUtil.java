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

import static org.monflabs.json.JsonNumberType.NUMBER_BIGDECIMAL;
import static org.monflabs.json.JsonNumberType.NUMBER_BIGINTEGER;
import static org.monflabs.json.JsonNumberType.NUMBER_BYTE;
import static org.monflabs.json.JsonNumberType.NUMBER_DOUBLE;
import static org.monflabs.json.JsonNumberType.NUMBER_FLOAT;
import static org.monflabs.json.JsonNumberType.NUMBER_INTEGER;
import static org.monflabs.json.JsonNumberType.NUMBER_LONG;
import static org.monflabs.json.JsonNumberType.NUMBER_NAN;
import static org.monflabs.json.JsonNumberType.NUMBER_SHORT;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.util.DtoA;
import org.monflabs.util.StringUtil;
import org.monflabs.util.TypeUtil;

/**
 * Helpers to handle JSON values.
 */
public abstract class JsonUtil extends TypeUtil {

	//
	// Static helpers
	//
	public static boolean isNull(Object value) {
		return value==null;
	}
	public static boolean isBoolean(Object value) {
		return value instanceof Boolean;
	}
	public static boolean isNumber(Object value) {
		return value instanceof Number;		
	}
	public static boolean isString(Object value) {
		return value instanceof String;
	}
	public static boolean isObject(Object value) {
		return value instanceof JsonObject;
	}
	public static boolean isArray(Object value) {
		return value instanceof JsonArray;
	}
	public static boolean isPrimitive(Object value) {
		if(value==null) {
			return false;
		}
		Class<?> c = value.getClass();
		return c==String.class|| c==Boolean.class|| value instanceof Number;
	}
	public static boolean isContainer(Object value) {
		return value instanceof JsonContainer;
	}
	public static boolean isJsonValue(Object value) {
		return JsonType.typeOf(value)!=JsonType.UNKNOWN;
	}
	
	

	
	public static void checkJsonValue(Object object) {
		if(isJsonValue(object)) {
			return;
		}
		throw new JsonException(null,"Value {0} is not a valid JSON value",toDebugString(object));
	}

	public static boolean checkBoolean(Object object) {
		if(object instanceof Boolean b) {
			return b;
		}
		throw new JsonException(null,"Value {0} is not an boolean",toDebugString(object));
	}
	public static byte checkByte(Object object) {
		if(object instanceof Number n) {
			return toByte(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static short checkShort(Object object) {
		if(object instanceof Number n) {
			return toShort(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static int checkInt(Object object) {
		if(object instanceof Number n) {
			return clampToInt(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static long checkLong(Object object) {
		if(object instanceof Number n) {
			return clampToLong(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static float checkFloat(Object object) {
		if(object instanceof Number n) {
			return toFloat(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static double checkDouble(Object object) {
		if(object instanceof Number n) {
			return toDouble(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static BigInteger checkBigInteger(Object object) {
		if(object instanceof Number n) {
			return toBigInteger(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static BigDecimal checkBigDecimal(Object object) {
		if(object instanceof Number n) {
			return toBigDecimal(n);
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static Number checkNumber(Object object) {
		if(object instanceof Number n) {
			return n;
		}
		throw new JsonException(null,"Value {0} is not a number",toDebugString(object));
	}
	public static String checkString(Object object) {
		if(object instanceof String s) {
			return s;
		}
		throw new JsonException(null,"Value {0} is not a String",toDebugString(object));
	}
	public static JsonObject checkObject(Object object) {
		if(object instanceof JsonObject o) {
			return o;
		}
		throw new JsonException(null,"Value {0} is not an object",toDebugString(object));
	}
	public static JsonArray checkArray(Object object) {
		if(object instanceof JsonArray a) {
			return a;
		}
		throw new JsonException(null,"Value {0} is not an array",toDebugString(object));
	}
	public static LocalDate checkLocalDate(Object value) {
		String s = checkString(value);
		return parseLocalDate(s);
	}
	public static LocalTime checkLocalTime(Object value) {
		String s = checkString(value);
		return parseLocalTime(s);
	}
	public static LocalDateTime checkLocalDateTime(Object value) {
		String s = checkString(value);
		return parseLocalDateTime(s);
	}
	public static OffsetTime checkOffsetTime(Object value) {
		String s = checkString(value);
		return parseOffsetTime(s);
	}
	public static OffsetDateTime checkOffsetDateTime(Object value) {
		String s = checkString(value);
		return parseOffsetDateTime(s);
	}
	public static ZonedDateTime checkZonedDateTime(Object value) {
		String s = checkString(value);
		return parseZonedDateTime(s);
	}

	public static String toDebugString(Object o) {
		if(o==null) {
			return "<null>";
		}
		return MessageFormat.format("{0} (Class={1})", o.toString(), o.getClass());
	}
	
	
	
	//
	// Static Helpers, useful in lambdas for example
	//

	public static boolean parseBoolean(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		if(value.equals("true")) {
			return true;
		}
		if(value.equals("false")) {
			return false;
		}
		throw new JsonException(null,"Cannot parse boolean value {0}", value);
	}
	public static byte parseByte(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return Byte.parseByte(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static short parseShort(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return Short.parseShort(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static int parseInt(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return Integer.parseInt(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static long parseLong(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return Long.parseLong(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static float parseFloat(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return Float.parseFloat(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static double parseDouble(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return Double.parseDouble(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static BigInteger parseBigInteger(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return new BigInteger(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static BigDecimal parseBigDecimal(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return new BigDecimal(value);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static Number parseNumber(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		try {
			return JsonFactory.get().parseNumber(value.strip(),0);
		} catch(Exception e) {
			throw new JsonException(e,"Cannot parse number value {0}", value);
		}
	}
	public static String parseString(String value) {
		return value;
	}
	public static JsonObject parseObject(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		return JsonObject.parse(value);
	}
	public static JsonArray parseArray(String value) {
		if(StringUtil.isEmpty(value)) {
			throw new JsonException(null,"Value is null");
		}
		return JsonArray.parse(value);
	}
	
	
	
	//
	// Date/time helpers
	// Should we use ISO8601?
	//
	
	public static LocalDate parseLocalDate(@NonNull String value) {
		try {
			return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static LocalTime parseLocalTime(@NonNull String value) {
		try {
			return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static LocalDateTime parseLocalDateTime(@NonNull String value) {
		try {
			return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static OffsetTime parseOffsetTime(@NonNull String value) {
		try {
			return OffsetTime.parse(value, DateTimeFormatter.ISO_OFFSET_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static OffsetDateTime parseOffsetDateTime(@NonNull String value) {
		try {
			return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static ZonedDateTime parseZonedDateTime(@NonNull String value) {
		try {
			return ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}

	public static String toString(@NonNull LocalDate value) {
		try {
			return value.format(DateTimeFormatter.ISO_LOCAL_DATE);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static String toString(@NonNull LocalTime value) {
		try {
			return value.format(DateTimeFormatter.ISO_LOCAL_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static String toString(@NonNull LocalDateTime value) {
		try {
			return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static String toString(@NonNull OffsetTime value) {
		try {
			return value.format(DateTimeFormatter.ISO_OFFSET_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static String toString(@NonNull OffsetDateTime value) {
		try {
			return value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	public static String toString(@NonNull ZonedDateTime value) {
		try {
			return value.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	
	
	public static String toStringValue(Object v) {
		// TODO: UNDEFINED
		if(v==null) {
			return "";
		}
		if(v.getClass()==String.class || v.getClass()==Boolean.class) {
			return v.toString();
		}
		if(v instanceof Number n) {
			return toString(n);
		}
		if(v instanceof JsonContainer) {
			return ((JsonContainer)v).stringify(true);
		}
		
		throw new IllegalArgumentException(MessageFormat.format("Invalid JSON data type {0}:{1}",v.getClass(),v.toString()));
	}
	
	
	//
	// Some conversion methods
	// These methods are *never* failing but return the default value in case of
	//

	public static Number asNumber(Object v) {
		return asNumber(v, null);
	}
	public static Number asNumber(Object v, Number defaultValue) {
		if(v instanceof Number n) {
			return n;
		} else if(v instanceof String s) {
			try {
				return JsonUtil.parseNumber(s.trim());
			} catch(Exception e) {}
		} else if(v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		return defaultValue;
	}
	
	public static int asInt(Object v) {
		return asInt(v, 0);
	}
	public static int asInt(Object v, int defaultValue) {
		if(v instanceof Number n) {
			return clampToInt(n);
		} else if(v instanceof String s) {
			// Same parsing as asNumber(): " 12 " is 12, "1.5" is 1
			Number n = asNumber(s.trim(), null);
			if(n!=null) {
				return clampToInt(n);
			}
		} else if(v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		return defaultValue;
	}
	
	public static long asLong(Object v) {
		return asLong(v, 0);
	}
	public static long asLong(Object v, long defaultValue) {
		if(v instanceof Number n) {
			return clampToLong(n);
		} else if(v instanceof String s) {
			// Same parsing as asNumber(): " 12 " is 12, "1.5" is 1
			Number n = asNumber(s.trim(), null);
			if(n!=null) {
				return clampToLong(n);
			}
		} else if(v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		return defaultValue;
	}
	
	public static double asDouble(Object v) {
		return asDouble(v, 0);
	}
	public static double asDouble(Object v, double defaultValue) {
		if(v instanceof Number n) {
			return JsonUtil.toDouble(n);
		} else if(v instanceof String s) {
			try {
				return Double.parseDouble(s);
			} catch(Exception e) {}
		} else if(v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		return defaultValue;
	}
	
	public static BigInteger asBigInteger(Object v) {
		return asBigInteger(v, BigInteger.ZERO);
	}
	public static BigInteger asBigInteger(Object v, BigInteger defaultValue) {
		if(v instanceof Number n) {
			return JsonUtil.toBigInteger(n);
		} else if(v instanceof String s) {
			try {
				return new BigInteger(s);
			} catch(Exception e) {}
		} else if(v instanceof Boolean b) {
			return b ? BigInteger.ONE : BigInteger.ZERO;
		}
		return defaultValue;
	}
	
	public static BigDecimal asBigDecimal(Object v) {
		return asBigDecimal(v, BigDecimal.ZERO);
	}
	public static BigDecimal asBigDecimal(Object v, BigDecimal defaultValue) {
		if(v instanceof Number n) {
			return JsonUtil.toBigDecimal(n);
		} else if(v instanceof String s) {
			try {
				return new BigDecimal(s);
			} catch(Exception e) {}
		} else if(v instanceof Boolean b) {
			return b ? BigDecimal.ONE : BigDecimal.ZERO;
		}
		return defaultValue;
	}
	
	public static boolean asBoolean(Object v) {
		return asBoolean(v, false);
	}
	public static boolean asBoolean(Object v, boolean defaultValue) {
		if(v instanceof Number n) {
			// Not toInt(): 0.5 and 2^32 are truthy, NaN is not
			double d = n.doubleValue();
			return d!=0 && !Double.isNaN(d);
		} else if(v instanceof String s) {
			if(s.length()==0 || s.equals("0") || s.equalsIgnoreCase("false")) {
				return false;
			}
			return true;
		} else if(v instanceof Boolean b) {
			return b;
		}
		return defaultValue;
	}
	
	public static String asString(Object v) {
		return asString(v, "");
	}
	public static String asString(Object v, String defaultValue) {
		if(v instanceof Number n) {
			return JsonUtil.toString(n);
		} else if(v instanceof String s) {
			return s;
		} else if(v instanceof Boolean b) {
			return b.toString();
		}
		return defaultValue;
	}

	
	
	//
	// Number utilities
	//
	
    public static String toString(Number n) {
    	if(n!=null) {
	    	if(n instanceof Double) {
	    		return DtoA.toStandard(n.doubleValue());
	    	}
	    	if(n instanceof Float f) {
	    		return DtoA.toStandard(f.floatValue());
	    	}
	    	return n.toString();
    	}
    	return null;
    }

	public static JsonNumberType promoteNumber(JsonNumberType t1, JsonNumberType t2) {
		boolean i1 = t1.ordinal()<=NUMBER_BIGINTEGER.ordinal();
		boolean i2 = t2.ordinal()<=NUMBER_BIGINTEGER.ordinal();
		if(i1!=i2) {
			if(i1) {
				t1 = promoteIntegerToDecimal(t1);
			}
			if(i2) {
				t2 = promoteIntegerToDecimal(t2);
			}
		}
		return JsonNumberType.values()[Math.max(t1.ordinal(),t2.ordinal())];
	}
	@SuppressWarnings("incomplete-switch")
	public static JsonNumberType promoteIntegerToDecimal(JsonNumberType t) {
		switch(t) {
			case NUMBER_BYTE: 
			case NUMBER_SHORT: 
			case NUMBER_INTEGER: 
			case NUMBER_LONG: // Could be BigDecimal, but we keep it like JS 
				return NUMBER_DOUBLE;
			case NUMBER_NAN:
				return NUMBER_NAN;
		};
		return NUMBER_BIGDECIMAL;
	}
	public static JsonNumberType numberType(Number o) {
		if(o instanceof Integer) {
			return NUMBER_INTEGER;
		}
		if(o instanceof Double d) {
			if(d.isNaN()) {
				return NUMBER_NAN;
			}
			return NUMBER_DOUBLE;
		}
		if(o instanceof Long) {
			return NUMBER_LONG;
		}
		if(o instanceof BigDecimal) {
			return NUMBER_BIGDECIMAL;
		}
		if(o instanceof BigInteger) {
			return NUMBER_BIGINTEGER;
		}
		if(o instanceof Short) {
			return NUMBER_SHORT;
		}
		if(o instanceof Byte) {
			return NUMBER_BYTE;
		}
		if(o instanceof Float f) {
			if(f.isNaN()) {
				return NUMBER_NAN;
			}
			return NUMBER_FLOAT;
		}
		throw new JsonException(null,"Unsupported JSON number type {0}", o.getClass());
	}

	
	//
	// Object comparison
	//
	
	/**
	 * The int value of a number, saturated: a value out of the int range gives
	 * Integer.MIN_VALUE or MAX_VALUE whatever its type (Long.MAX_VALUE doesn't wrap to -1,
	 * as 1e10 doesn't). NaN is 0, a decimal value is truncated.
	 */
	public static int clampToInt(Number n) {
		if(n instanceof Integer || n instanceof Short || n instanceof Byte) {
			return n.intValue();
		}
		long l = clampToLong(n);
		return l>Integer.MAX_VALUE ? Integer.MAX_VALUE : l<Integer.MIN_VALUE ? Integer.MIN_VALUE : (int)l;
	}
	/**
	 * The short value of a number, saturated like {@link #clampToInt(Number)}.
	 */
	public static short clampToShort(Number n) {
		int i = clampToInt(n);
		return i>Short.MAX_VALUE ? Short.MAX_VALUE : i<Short.MIN_VALUE ? Short.MIN_VALUE : (short)i;
	}
	/**
	 * The byte value of a number, saturated like {@link #clampToInt(Number)}.
	 */
	public static byte clampToByte(Number n) {
		int i = clampToInt(n);
		return i>Byte.MAX_VALUE ? Byte.MAX_VALUE : i<Byte.MIN_VALUE ? Byte.MIN_VALUE : (byte)i;
	}
	/**
	 * The long value of a number, saturated like {@link #clampToInt(Number)}.
	 */
	public static long clampToLong(Number n) {
		if(n instanceof BigInteger bi) {
			return bi.bitLength()<64 ? bi.longValue() : (bi.signum()>0 ? Long.MAX_VALUE : Long.MIN_VALUE);
		}
		if(n instanceof BigDecimal bd) {
			return clampToLong(bd.toBigInteger());
		}
		// long, int... as is; double and float saturate (and NaN is 0)
		return n.longValue();
	}

	public static boolean eq(Object o1, Object o2) {
		if(o1==o2) {
			return true;
		}
		if(o1==null || o2==null) {
			return false;
		}
		// Numbers compare by value, whatever their type (and -0.0 is 0)
		if(o1 instanceof Number n1 && o2 instanceof Number n2) {
			return eqNumber(n1, n2);
		}
		if(o1.getClass()==o2.getClass()) {
			if(o1.equals(o2)) {
				return true;
			}
			if(!(o1 instanceof JsonArray || o1 instanceof JsonObject)) {
				return false;
			}
		}
		if(o1 instanceof JsonArray l1 && o2 instanceof JsonArray l2) {
			return equalsArray(l1, l2);
		}
		if(o1 instanceof JsonObject m1 && o2 instanceof JsonObject m2) {
			return equalsObject(m1, m2);
		}
		return false;
	}
	public static boolean equalsArray(JsonArray l1, JsonArray l2) {
    	int s1 = l1.size();
    	int s2 = l2.size();
    	if(s1==s2) {
    		for(int i=0; i<s1; i++) {
    			if(!JsonUtil.eq(l1.get(i), l2.get(i))) {
    				return false;
    			}
    		}
    		return true;
    	}
        return false;
    }
	public static boolean equalsObject(JsonObject m1, JsonObject m2) {
    	int s1 = m1.size();
    	int s2 = m2.size();
    	if(s1==s2) {
    		for(Map.Entry<String,Object> e: m1.entrySet()) {
    			String key = e.getKey();
    			Object value = e.getValue();
    			if(value==null) {
                    if (m2.get(key)!=null || !m2.containsKey(key)) {
                        return false;
                    }
    			} else {
	    			if(!JsonUtil.eq(value, m2.get(key))) {
	    				return false;
	    			}
    			}
    		}
    		return true;
    	}
        return false;
    }
	
	public static boolean ne(Object o1, Object o2) {
		return !eq(o1, o2);
	}

	/**
	 * Hash code consistent with {@link #eq(Object, Object)}: numbers that compare
	 * equal across types (1, 1L, 1.0, BigDecimal.ONE) hash the same, and containers
	 * hash their content the way {@link #equalsArray}/{@link #equalsObject} compare it.
	 * <p>
	 * The formulas are the ones of {@link java.util.List#hashCode()} and
	 * {@link java.util.Map#hashCode()}, and a number hashes like the Integer holding the
	 * same value when it is an integer in the int range, like the Double holding it
	 * otherwise. So a container only made of strings, booleans, nulls, int-range integers
	 * and non integral doubles has the same hash code as the equivalent JDK collection.
	 */
	public static int hashCode(Object o) {
		if(o==null) {
			return 0;
		}
		if(o instanceof Number n) {
			return hashNumber(n);
		}
		if(o instanceof JsonArray a) {
			int h = 1;
			int sz = a.size();
			for(int i=0; i<sz; i++) {
				h = 31*h + hashCode(a.get(i));
			}
			return h;
		}
		if(o instanceof JsonObject jo) {
			// Order independent, like equalsObject()
			int h = 0;
			for(Map.Entry<String,Object> e: jo.entrySet()) {
				h += e.getKey().hashCode() ^ hashCode(e.getValue());
			}
			return h;
		}
		return o.hashCode();
	}
	public static boolean gt(Object o1, Object o2) {
		return compare(o1, o2)>0;
	}
	public static boolean ge(Object o1, Object o2) {
		return compare(o1, o2)>=0;
	}
	public static boolean lt(Object o1, Object o2) {
		return compare(o1, o2)<0;
	}
	public static boolean le(Object o1, Object o2) {
		return compare(o1, o2)<=0;
	}

	public static int compare(Object o1, Object o2) {
		if(o1==o2) {
			return 0;
		}
		JsonType t1 = JsonType.typeOf(o1);
		JsonType t2 = JsonType.typeOf(o2);
		if(t1!=t2) {
			if(t1==JsonType.NULL) {
				return -1;
			}
			if(t2==JsonType.NULL) {
				return 1;
			}
			return t2.ordinal()-t1.ordinal();
		}
		switch(t1) {
			case NULL: {
				return 0;
			}
			case STRING: {
	            return o1.toString().compareTo(o2.toString());
			}
			case NUMBER: {
				Number n1 = (Number)o1;
				Number n2 = (Number)o2;
				return compareNumber(n1, n2);
			}
			case BOOLEAN: {
				return Boolean.compare((Boolean)o1, (Boolean)o2);
			}
			case OBJECT: {
				JsonObject j1 = (JsonObject)o1;
				JsonObject j2 = (JsonObject)o2;
				// Walk the 2 sorted key lists together (the union of the keys, in order): the
				// first key only present in one of the objects makes the other one smaller
				String[] k1 = j1.keySet().toArray(new String[0]);
				String[] k2 = j2.keySet().toArray(new String[0]);
				Arrays.sort(k1);
				Arrays.sort(k2);
				int i1 = 0, i2 = 0;
				while(i1<k1.length && i2<k2.length) {
					int kc = k1[i1].compareTo(k2[i2]);
					if(kc<0) {
						return 1;	// k1[i1] is missing from j2
					}
					if(kc>0) {
						return -1;	// k2[i2] is missing from j1
					}
					int c = compare(j1.get(k1[i1]),j2.get(k2[i2]));
					if(c!=0) {
						return c;
					}
					i1++;
					i2++;
				}
				return i1<k1.length ? 1 : i2<k2.length ? -1 : 0;
			}
			case ARRAY: {
				JsonArray j1 = (JsonArray)o1;
				JsonArray j2 = (JsonArray)o2;
				int sz1 = j1.size();
				int sz2 = j2.size();
				int sz = Math.min(sz1, sz2);
				for(int i=0; i<sz; i++) {
					int cmp = compare(j1.get(i), j2.get(i));
					if(cmp!=0) {
						return cmp;
					}
				}
				if(sz1!=sz2) {
					return sz1-sz2;
				}
				return 0;
			}
			default: {
				// UNKNOWN: native (non JSON) values. Compared by their natural order when
				// they are of the same Comparable class, then by class name and string value
				if(o1.getClass()==o2.getClass() && o1 instanceof Comparable) {
					@SuppressWarnings({ "unchecked", "rawtypes" })
					int c = ((Comparable)o1).compareTo(o2);
					return c;
				}
				int c = o1.getClass().getName().compareTo(o2.getClass().getName());
				if(c!=0) {
					return c;
				}
				return String.valueOf(o1).compareTo(String.valueOf(o2));
			}
		}
	}
	
	/**
	 * Numeric equality, by value and whatever the number types.
	 * <p>
	 * The comparison is exact (2^53+1 as a long is not the double 2^53), a double or a
	 * float having the value of its shortest decimal representation (0.1d is the decimal
	 * 0.1). -0.0 equals 0, NaN only equals NaN, infinities only equal themselves.
	 */
	public static boolean eqNumber(Number n1, Number n2) {
		boolean nan1 = isNaN(n1);
		boolean nan2 = isNaN(n2);
		if(nan1 || nan2) {
			return nan1 && nan2;
		}
		return compareNumber(n1, n2)==0;
	}

	/**
	 * Numeric ordering, consistent with {@link #eqNumber(Number, Number)}.
	 * NaN is greater than any other number (and equal to itself).
	 */
	public static int compareNumber(Number n1, Number n2) {
		boolean nan1 = isNaN(n1);
		boolean nan2 = isNaN(n2);
		if(nan1 || nan2) {
			return nan1 ? (nan2 ? 0 : 1) : -1;
		}
		boolean i1 = isIntegral(n1);
		boolean i2 = isIntegral(n2);
		if(i1 && i2) {
			return Long.compare(n1.longValue(), n2.longValue());
		}
		boolean f1 = n1 instanceof Double || n1 instanceof Float;
		boolean f2 = n2 instanceof Double || n2 instanceof Float;
		if((f1 && f2) && (n1 instanceof Double)==(n2 instanceof Double)) {
			// Same floating type: the natural order, but -0.0 == 0.0
			double d1 = n1.doubleValue();
			double d2 = n2.doubleValue();
			return d1<d2 ? -1 : d1>d2 ? 1 : 0;
		}
		if(f1 || f2) {
			double d1 = n1.doubleValue();
			double d2 = n2.doubleValue();
			// A float/double infinity compares with anything by its sign (a huge
			// BigInteger also has an infinite doubleValue(), but is finite)
			boolean inf1 = f1 && Double.isInfinite(d1);
			boolean inf2 = f2 && Double.isInfinite(d2);
			if(inf1 || inf2) {
				if(inf1 && inf2) {
					return Double.compare(d1, d2);
				}
				if(inf1) {
					return d1>0 ? 1 : -1;
				}
				return d2>0 ? -1 : 1;
			}
			// Integer vs double with an exact double representation
			if((i1 && Math.abs(n1.longValue())<=MAX_EXACT_DOUBLE_INT && n2 instanceof Double)
			|| (i2 && Math.abs(n2.longValue())<=MAX_EXACT_DOUBLE_INT && n1 instanceof Double)) {
				return d1<d2 ? -1 : d1>d2 ? 1 : 0;
			}
		}
		return exactDecimal(n1).compareTo(exactDecimal(n2));
	}
	private static final long MAX_EXACT_DOUBLE_INT = 1L<<53;

	private static boolean isNaN(Number n) {
		return (n instanceof Double d && d.isNaN()) || (n instanceof Float f && f.isNaN());
	}
	private static boolean isIntegral(Number n) {
		return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
	}
	// The decimal value of a finite number: a double is its shortest representation
	private static BigDecimal exactDecimal(Number n) {
		if(n instanceof BigDecimal bd) {
			return bd;
		}
		if(n instanceof BigInteger bi) {
			return new BigDecimal(bi);
		}
		if(n instanceof Double d) {
			return BigDecimal.valueOf(d);
		}
		if(n instanceof Float f) {
			return new BigDecimal(Float.toString(f));
		}
		if(isIntegral(n)) {
			return BigDecimal.valueOf(n.longValue());
		}
		// Other Number implementations (AtomicLong...)
		return new BigDecimal(n.toString());
	}

	private static int hashNumber(Number n) {
		if(isIntegral(n)) {
			return hashIntegral(n.longValue());
		}
		if(n instanceof Double d) {
			double v = d;
			if(Double.isNaN(v) || Double.isInfinite(v) || v!=Math.rint(v)) {
				return Double.hashCode(v); // NaN, infinities and non integers
			}
			if(Math.abs(v)<0x1p63) {
				return hashIntegral((long)v); // also makes -0.0 hash as 0
			}
			// A huge integer: hashed below like the equivalent BigInteger
		}
		if(n instanceof Float f && (f.isNaN() || f.isInfinite())) {
			return Double.hashCode(f.doubleValue());
		}
		BigDecimal bd = exactDecimal(n);
		if(bd.signum()==0) {
			return 0;
		}
		BigDecimal s = bd.stripTrailingZeros();
		if(s.scale()<=0) {
			// An integer
			if(s.compareTo(LONG_MIN)>=0 && s.compareTo(LONG_MAX)<=0) {
				return hashIntegral(s.longValue());
			}
			return s.toBigInteger().hashCode();
		}
		// Hash like the double that has that exact (shortest) decimal value, if any
		double d = s.doubleValue();
		if(!Double.isInfinite(d) && BigDecimal.valueOf(d).compareTo(s)==0) {
			return Double.hashCode(d);
		}
		return s.hashCode();
	}
	private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
	private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
	private static int hashIntegral(long l) {
		// Like Integer.hashCode() in the int range, Long.hashCode() outside
		if(l>=Integer.MIN_VALUE && l<=Integer.MAX_VALUE) {
			return (int)l;
		}
		return Long.hashCode(l);
	}

	
	
	public static final Comparator<Object> jsonComparator = new Comparator<Object>() {
		@Override
		public int compare(Object o1, Object o2) {
			return JsonUtil.compare(o1, o2);
		}
	};

	public static final Comparator<Object> jsonComparatorDesc = new Comparator<Object>() {
		@Override
		public int compare(Object o1, Object o2) {
			return -JsonUtil.compare(o1, o2);
		}
	};
	
	public static ObjectComparator objectComparator() {
		return new ObjectComparator();
	}

	
	private static final class Attribute {
		private String name;
		private boolean asc;
		private Attribute(String name, boolean asc) {
			this.name = name;
			this.asc = asc;
		}
	}
	public static final class ObjectComparator implements Comparator<Object> {
		private List<Attribute> attributes = new ArrayList<>();
		private ObjectComparator() {}
		public ObjectComparator add(String name) {
			return add(name,true);
		}
		public ObjectComparator add(String name, boolean asc) {
			attributes.add(new Attribute(name, asc));
			return this;
		}
		
		@Override
		public int compare(Object v1, Object v2) {
			JsonObject o1 = JsonUtil.checkObject(v1);
			JsonObject o2 = JsonUtil.checkObject(v2);
			for(int i=0; i<attributes.size(); i++) {
				Attribute a = attributes.get(i);
				int r = JsonUtil.compare(o1.get(a.name),o2.get(a.name));
				if(r!=0) {
					return a.asc ? r : -r;
				}
			}
			return 0;
		}
	}

	
	//
	// Parser/Serializer helpers
	//
	// This should be extracted out!
	
	// A JSONPath member-name-shorthand (RFC 9535: ALPHA, '_', non ASCII), plus '$'.
	// '-' is accepted inside a name ("a-b") but not as its first char, so a key like
	// "-1" is always quoted and never mistaken for an index
	public static boolean isIdentifierStart(char ch) {
		return (ch>='a' && ch<='z') || (ch>='A' && ch<='Z') || ch=='_' || ch=='$' || ch>=0x80;  
	}
	public static boolean isIdentifierPart(char ch) {
		return (ch>='0' && ch<='9') || ch=='-' || isIdentifierStart(ch);  
	}
	public static boolean isIdentifier(String s) {
		if(s==null) {
			return false;
		}
		int length = s.length();
		if(length==0 || !isIdentifierStart(s.charAt(0))) {
			return false;
		}
		for(int i=1; i<length; i++) {
			if(!isIdentifierPart(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}
	public static String encodeValue(Object value) {
		if(value instanceof String s) {
			return JsonUtil.encodeString(s,'\"');
		}
		if(value instanceof Number n) {
			return JsonUtil.toString(n);
		}
		return value!=null ? value.toString() : "null";
	}
	public static String encodeString(String s, char quote) {
		if(s!=null) {
	        StringBuilder b = new StringBuilder(s.length()+16);
	        encodeString(b,s,quote);
	        return b.toString();
		}
		return null;
    }	
	public static StringBuilder encodeString(StringBuilder b,String s, char quote) {
    	if(s==null) {
    		if(quote>0) {
    			b.append(quote);
    			b.append(quote);    		
    		}
    		return b;
    	}
        if(quote>0) b.append(quote);
        int length = s.length();
        for( int i=0; i<length; i++ ) {
            char c = s.charAt(i);
            switch(c) {
            	case '\t':  b.append( "\\t" );  break;
                case '\n':  b.append( "\\n" );  break;
                case '\r':  b.append( "\\r" );  break;
                case '\f':  b.append( "\\f" );  break;
                case '\b':  b.append( "\\b" );  break;
                case '\\':  b.append( "\\\\" ); break;
                case '/':   b.append( "/" ); break;
                case '\'': {
                	// Escaping the other quote is not valid JSON ("\'" is rejected by strict parsers)
                	if(quote!='\"') b.append( "\\'" ); else b.append(c);
                } break;
                case '\"': {
                	if(quote!='\'') b.append( "\\\"" ); else b.append(c);
                } break;
                default : {
                    if((c<32) || (c > 126)) {
                    	b.append( "\\u" );
                    	addUnsignedHex(b,c);
                    } else {
                    	b.append(c);
                    }
                }
            }
        }
        if(quote>0) b.append(quote);
        return b;
    }	
   	private static final void addUnsignedHex( StringBuilder b, int value ) {
        for( int i=3; i>=0; i-- ) {
            int v = (value >>> (i*4)) & 0x0F;
            b.append(hexChar(v));
        }
    }
   	private static final char hexChar(int v) {
        return (char)((v>=10) ? (v-10+'A') : (v+'0'));
    }
}
