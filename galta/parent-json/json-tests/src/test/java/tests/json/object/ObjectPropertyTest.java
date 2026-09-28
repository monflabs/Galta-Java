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
package tests.json.object;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class ObjectPropertyTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
{
  "v_null": null,
  "v_boolean": true,
  "v_string": "abc",
  "v_object": { a: 1 },
  "v_array": [1],
  
  "v_local_date": "2020-02-20",
  "v_local_time": "13:44:18",
  "v_local_datetime": "2020-02-20T13:44:18",
  "v_offset_time": "13:44:18+02:00",
  "v_offset_datetime": "2020-02-20T13:44:18+02:00",
  "v_zoned_datetime": "2020-02-20T13:44:18-05:00[US/Eastern]",
}
""";

	static JsonObject json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		JsonObject a = JsonObject.parse(JSON);
		a.put("v_byte", (byte)32);
		a.put("v_short", (short)78);
		a.put("v_int", (int)56);
		a.put("v_long", (long)98L);
		a.put("v_float", (float)12.5f);
		a.put("v_double", (double)34.34);
		a.put("v_bigInteger", new BigInteger("82"));
		a.put("v_bigDecimal", new BigDecimal("36.53"));
		assertNull(a.get("v_null"));
		assertTrue(a.get("v_byte") instanceof Byte);
		assertTrue(a.get("v_short") instanceof Short);
		assertTrue(a.get("v_int") instanceof Integer);
		assertTrue(a.get("v_long") instanceof Long);
		assertTrue(a.get("v_float") instanceof Float);
		assertTrue(a.get("v_double") instanceof Double);
		assertTrue(a.get("v_bigInteger") instanceof BigInteger);
		assertTrue(a.get("v_bigDecimal") instanceof BigDecimal);
		assertTrue(a.get("v_boolean") instanceof Boolean);
		assertTrue(a.get("v_string") instanceof String);
		json = a;
	}

	public void testIs() throws Exception {
		for(String k: json.keySet()) {
			assertEquals( in(k,"v_null") ? true : false, json.isNull(k));
			assertEquals( in(k,"v_byte","v_short","v_int","v_long","v_float","v_double","v_bigInteger","v_bigDecimal") ? true : false, json.isNumber(k));
			assertEquals( in(k,"v_boolean") ? true : false, json.isBoolean(k));
			assertEquals( in(k,"v_string","v_local_date","v_local_time","v_local_datetime","v_offset_time","v_offset_datetime","v_zoned_datetime") ? true : false, json.isString(k));
			assertEquals( in(k,"v_object","v_array") ? true : false, json.isContainer(k));
			assertEquals( in(k,"v_object") ? true : false, json.isObject(k));
			assertEquals( in(k,"v_array") ? true : false, json.isArray(k));
		}
	}
	private static boolean in(String v, String...values) {
		for(int i=0; i<values.length; i++) {
			if(v.equals(values[i])) {
				return true;
			}
		}
		return false;
	}

	public void testFirst() throws Exception {
		JsonObject o1 = JsonObject.create();
		assertThrows( JsonException.class, () -> o1.firstValue() );
		assertEquals( 123, ((Number)o1.firstValueOrDefault(123)).intValue() );
		JsonObject o2 = JsonObject.of("a",11);
		assertEquals( 11, ((Number)o2.firstValue()).intValue() );
		assertEquals( 11, ((Number)o2.firstValueOrDefault(123)).intValue() );
		JsonObject o3 = JsonObject.of("a",11,"b",22);
		assertEquals( 11, ((Number)o3.firstValue()).intValue() );
		assertEquals( 11, ((Number)o3.firstValueOrDefault(123)).intValue() );
	}
	
	public void testNullPrimitive() throws Exception {
		assertThrows( JsonException.class, () -> json.getValue("v_null") );
		assertThrows( JsonException.class, () -> json.getBoolean("v_null") );
		assertThrows( JsonException.class, () -> json.getByte("v_null"));
		assertThrows( JsonException.class, () -> json.getShort("v_null"));
		assertThrows( JsonException.class, () -> json.getInt("v_null"));
		assertThrows( JsonException.class, () -> json.getLong("v_null"));
		assertThrows( JsonException.class, () -> json.getFloat("v_null"));
		assertThrows( JsonException.class, () -> json.getDouble("v_null"));

		assertEquals( true, ((Boolean)json.getValueOrDefault("v_null",true)).booleanValue() );
		assertEquals( true, json.getBoolean("v_null",true) );
		assertEquals((byte)10,json.getByte("v_null",(byte)10));
		assertEquals((short)10,json.getShort("v_null",(short)10));
		assertEquals(10,json.getInt("v_null",10));
		assertEquals(10L,json.getLong("v_null",10L));
		assertEquals(10.10f,json.getFloat("v_null",10.10f));
		assertEquals(10.10,json.getDouble("v_null",10.10));
	}

	public void testNullObject() throws Exception {
		assertThrows( JsonException.class, () -> json.getBooleanObject("v_null"));
		assertThrows( JsonException.class, () -> json.getNumber("v_null"));
		assertThrows( JsonException.class, () -> json.getByteObject("v_null"));
		assertThrows( JsonException.class, () -> json.getShortObject("v_null"));
		assertThrows( JsonException.class, () -> json.getIntObject("v_null"));
		assertThrows( JsonException.class, () -> json.getLongObject("v_null"));
		assertThrows( JsonException.class, () -> json.getFloatObject("v_null"));
		assertThrows( JsonException.class, () -> json.getDoubleObject("v_null"));
		assertThrows( JsonException.class, () -> json.getBigInteger("v_null"));
		assertThrows( JsonException.class, () -> json.getBigDecimal("v_null"));

		assertThrows( JsonException.class, () -> json.getString("v_null"));
		assertThrows( JsonException.class, () -> json.getObject("v_null"));
		assertThrows( JsonException.class, () -> json.getArray("v_null"));

		assertEquals(Boolean.TRUE,json.getBooleanObject("v_null",true));
		assertEquals(10,json.getNumber("v_null",10));
		assertEquals(Byte.valueOf((byte)10),json.getByteObject("v_null",(byte)10));
		assertEquals(Short.valueOf((short)10),json.getShortObject("v_null",(short)10));
		assertEquals(Integer.valueOf(10),json.getIntObject("v_null",10));
		assertEquals(Long.valueOf(10L),json.getLongObject("v_null",10L));
		assertEquals(10.10f,json.getFloatObject("v_null",10.10f));
		assertEquals(10.10,json.getDoubleObject("v_null",10.10));
		assertEquals(BigInteger.TEN,json.getBigInteger("v_null",BigInteger.TEN));
		assertEquals(BigDecimal.TEN,json.getBigDecimal("v_null",BigDecimal.TEN));

		assertEquals("xy",json.getString("v_null","xy"));
		assertEquals(JsonObject.create(),json.getObject("v_null",JsonObject.create()));
		assertEquals(JsonArray.create(),json.getArray("v_null",JsonArray.create()));
	}

	public void testBytePrimitive() {
		assertThrows( JsonException.class, () -> json.getValue("fake"));
		assertThrows( JsonException.class, () -> json.getByte("fake"));
		assertEquals((byte)67,json.getByte("fake",(byte)67));

		assertEquals((byte)32,(json.getValueOrDefault("v_byte",(byte)89).byteValue()));
		assertThrows( JsonException.class, () -> json.getBoolean("v_byte") );
		assertEquals((byte)32,json.getByte("v_byte"));
		assertEquals((short)32,json.getShort("v_byte"));
		assertEquals(32,json.getInt("v_byte"));
		assertEquals(32L,json.getLong("v_byte"));
		assertEquals(32.0f,json.getFloat("v_byte"));
		assertEquals(32.0,json.getDouble("v_byte"));
		
		assertThrows( JsonException.class, () -> {json.getBoolean("v_byte",true);});
		assertEquals((byte)32,json.getShort("v_byte",(byte)10));
		assertEquals((short)32,json.getShort("v_byte",(short)10));
		assertEquals(32,json.getInt("v_byte",10));
		assertEquals(32L,json.getLong("v_byte",10L));
		assertEquals(32.0f,json.getFloat("v_byte",10.10f));
		assertEquals(32.0,json.getDouble("v_byte",10.10));
		
	}
	public void testByteObject() {
		assertThrows( JsonException.class, () -> json.getByteObject("fake") );
		assertEquals(Byte.valueOf((byte)67),json.getByteObject("fake",(byte)67));

		assertThrows( JsonException.class, () -> json.getBooleanObject("v_byte") );
		
		assertEquals((byte)32,json.getNumber("v_byte"));
		assertEquals(Byte.valueOf((byte)32),json.getByteObject("v_byte"));
		assertEquals(Short.valueOf((short)32),json.getShortObject("v_byte"));
		assertEquals(Integer.valueOf(32),json.getIntObject("v_byte"));
		assertEquals(Long.valueOf(32L),json.getLongObject("v_byte"));
		assertEquals(32.0f,json.getFloatObject("v_byte"));
		assertEquals(32.0,json.getDoubleObject("v_byte"));
		assertEquals(new BigInteger("32"),json.getBigInteger("v_byte"));
		assertEquals(new BigDecimal("32"),json.getBigDecimal("v_byte"));

		assertThrows( JsonException.class, () -> {json.getString("v_byte");});
		assertThrows( JsonException.class, () -> {json.getObject("v_byte");});
		assertThrows( JsonException.class, () -> {json.getArray("v_byte");});

		assertThrows( JsonException.class, () -> json.getBooleanObject("v_byte",false) );
		
		assertEquals((byte)32,json.getNumber("v_byte",(short)10));
		assertEquals(Byte.valueOf((byte)32),json.getByteObject("v_byte",(byte)10));
		assertEquals(Short.valueOf((short)32),json.getShortObject("v_byte"),(short)10);
		assertEquals(Integer.valueOf(32),json.getIntObject("v_byte"),10);
		assertEquals(Long.valueOf(32L),json.getLongObject("v_byte"),10L);
		assertEquals(32.0f,json.getFloatObject("v_byte",12.5f));
		assertEquals(32.0,json.getDoubleObject("v_byte",12.5));
		assertEquals(new BigInteger("32"),json.getBigInteger("v_byte",BigInteger.TEN));
		assertEquals(new BigDecimal("32"),json.getBigDecimal("v_byte",BigDecimal.TEN));

		assertThrows( JsonException.class, () -> {json.getString("v_byte","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_byte",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_byte",JsonArray.create());});
	}

	public void testShortPrimitive() {
		assertThrows( JsonException.class, () -> json.getShort("fake") );
		assertEquals((short)67,json.getShort("fake",(short)67));

		assertThrows( JsonException.class, () -> {json.getBoolean("v_short");});
		assertEquals((byte)78,json.getByte("v_short"));
		assertEquals((short)78,json.getShort("v_short"));
		assertEquals(78,json.getInt("v_short"));
		assertEquals(78L,json.getLong("v_short"));
		assertEquals(78.0f,json.getFloat("v_short"));
		assertEquals(78.0,json.getDouble("v_short"));

		assertThrows( JsonException.class, () -> {json.getBoolean("v_short",true);});		
		assertEquals((byte)78,json.getByte("v_short",(byte)10));
		assertEquals((short)78,json.getShort("v_short",(short)10));
		assertEquals(78,json.getInt("v_short",10));
		assertEquals(78L,json.getLong("v_short",10L));
		assertEquals(78.0f,json.getFloat("v_short",10.10f));
		assertEquals(78.0,json.getDouble("v_short",10.10));
	}
	public void testShortObject() {
		assertThrows( JsonException.class, () -> {json.getShortObject("fake");});
		assertEquals(Short.valueOf((byte)67),json.getShortObject("fake",Short.valueOf((byte)67)));

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_short");});

		assertEquals((short)78,json.getNumber("v_short"));
		assertEquals(Byte.valueOf((byte)78),json.getByteObject("v_short"));
		assertEquals(Short.valueOf((short)78),json.getShortObject("v_short"));
		assertEquals(Integer.valueOf(78),json.getIntObject("v_short"));
		assertEquals(Long.valueOf(78L),json.getLongObject("v_short"));
		assertEquals(78.0f,json.getFloatObject("v_short"));
		assertEquals(78.0,json.getDoubleObject("v_short"));
		assertEquals(new BigInteger("78"),json.getBigInteger("v_short"));
		assertEquals(new BigDecimal("78"),json.getBigDecimal("v_short"));

		assertThrows( JsonException.class, () -> {json.getString("v_short");});
		assertThrows( JsonException.class, () -> {json.getObject("v_short");});
		assertThrows( JsonException.class, () -> {json.getArray("v_short");});

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_short",true);});		
		
		assertEquals((short)78,json.getNumber("v_short",(short)10));
		assertEquals(Byte.valueOf((byte)78),json.getByteObject("v_short",(byte)10));
		assertEquals(Short.valueOf((short)78),json.getShortObject("v_short",(short)10));
		assertEquals(Integer.valueOf(78),json.getIntObject("v_short",10));
		assertEquals(Long.valueOf(78L),json.getLongObject("v_short",10L));
		assertEquals(78.0f,json.getFloatObject("v_short",10.10f));
		assertEquals(78.0,json.getDoubleObject("v_short",10.10));
		assertEquals(new BigInteger("78"),json.getBigInteger("v_short",BigInteger.TEN));
		assertEquals(new BigDecimal("78"),json.getBigDecimal("v_short",BigDecimal.TEN));

		assertThrows( JsonException.class, () -> {json.getString("v_short","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_short",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_short",JsonArray.create());});
	}

	public void testIntPrimitive() {
		assertThrows( JsonException.class, () -> json.getInt("fake") );
		assertEquals(67,json.getInt("fake",67));

		assertThrows( JsonException.class, () -> json.getBoolean("v_int") );
		
		assertEquals((byte)56,json.getByte("v_int"));
		assertEquals((short)56,json.getShort("v_int"));
		assertEquals(56,json.getInt("v_int"));
		assertEquals(56L,json.getLong("v_int"));
		assertEquals(56.0f,json.getFloat("v_int"));
		assertEquals(56.0,json.getDouble("v_int"));

		assertThrows( JsonException.class, () -> json.getBoolean("v_int",true) );
		
		assertEquals((byte)56,json.getByte("v_int",(byte)10));
		assertEquals((short)56,json.getShort("v_int",(short)10));
		assertEquals(56,json.getInt("v_int",10));
		assertEquals(56L,json.getLong("v_int",10L));
		assertEquals(56.0f,json.getFloat("v_int",10.10f));
		assertEquals(56.0,json.getDouble("v_int",10.10));
	}
	
	public void testIntObject() {
		assertThrows( JsonException.class, () -> json.getIntObject("fake") );
		assertEquals( Integer.valueOf(67), json.getIntObject("fake",67));

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_int");});

		assertEquals(56,json.getNumber("v_int"));
		assertEquals(Byte.valueOf((byte)56),json.getByteObject("v_int"));
		assertEquals(Short.valueOf((short)56),json.getShortObject("v_int"));
		assertEquals(Integer.valueOf(56),json.getIntObject("v_int"));
		assertEquals(Long.valueOf(56L),json.getLongObject("v_int"));
		assertEquals(56.0f,json.getFloatObject("v_int"));
		assertEquals(56.0,json.getDoubleObject("v_int"));
		assertEquals(new BigInteger("56"),json.getBigInteger("v_int"));
		assertEquals(new BigDecimal("56"),json.getBigDecimal("v_int"));

		assertThrows( JsonException.class, () -> {json.getString("v_int");});
		assertThrows( JsonException.class, () -> {json.getObject("v_int");});
		assertThrows( JsonException.class, () -> {json.getArray("v_int");});

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_int",true);});

		assertEquals(56,json.getNumber("v_int",(short)10));
		assertEquals(Byte.valueOf((byte)56),json.getByteObject("v_int",(byte)10));
		assertEquals(Short.valueOf((short)56),json.getShortObject("v_int",(short)10));
		assertEquals(Integer.valueOf(56),json.getIntObject("v_int",10));
		assertEquals(Long.valueOf(56L),json.getLongObject("v_int",10L));
		assertEquals(56.0f,json.getFloatObject("v_int",10.10f));
		assertEquals(56.0,json.getDoubleObject("v_int",10.10));
		assertEquals(new BigInteger("56"),json.getBigInteger("v_int",BigInteger.TEN));
		assertEquals(new BigDecimal("56"),json.getBigDecimal("v_int",BigDecimal.TEN));
		
		assertThrows( JsonException.class, () -> {json.getString("v_int","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_int",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_int",JsonArray.create());});
	}

	public void testLongPrimitive() {
		assertThrows( JsonException.class, () -> json.getLong("fake") );
		assertEquals(456L,json.getLong("fake",456L));
		
		assertThrows( JsonException.class, () -> {json.getBoolean("v_long");});

		assertEquals((byte)98,json.getByte("v_long"));
		assertEquals((short)98,json.getShort("v_long"));
		assertEquals(98,json.getInt("v_long"));
		assertEquals(98L,json.getLong("v_long"));
		assertEquals(98.0f,json.getFloat("v_long"));
		assertEquals(98.0,json.getDouble("v_long"));
		
		assertThrows( JsonException.class, () -> {json.getBoolean("v_long",true);});

		assertEquals((byte)98,json.getByte("v_long",(byte)10));
		assertEquals((short)98,json.getShort("v_long",(short)10));
		assertEquals(98,json.getInt("v_long",10));
		assertEquals(98L,json.getLong("v_long",10L));
		assertEquals(98.0f,json.getFloat("v_long",10.10f));
		assertEquals(98.0,json.getDouble("v_long",10.10));
	}
	
	public void testLongObject() {
		assertThrows( JsonException.class, () -> json.getLongObject("fake") );
		assertEquals( Long.valueOf(456L),json.getLongObject("fake",456L));

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_long");});
		
		assertEquals(98L,json.getNumber("v_long"));
		assertEquals(Byte.valueOf((byte)98),json.getByteObject("v_long"));
		assertEquals(Short.valueOf((short)98),json.getShortObject("v_long"));
		assertEquals(Integer.valueOf(98),json.getIntObject("v_long"));
		assertEquals(Long.valueOf(98L),json.getLongObject("v_long"));
		assertEquals(98.0f,json.getFloatObject("v_long"));
		assertEquals(98.0,json.getDoubleObject("v_long"));
		assertEquals(new BigInteger("98"),json.getBigInteger("v_long"));
		assertEquals(new BigDecimal("98"),json.getBigDecimal("v_long"));
		
		assertThrows( JsonException.class, () -> {json.getString("v_long");});
		assertThrows( JsonException.class, () -> {json.getObject("v_long");});
		assertThrows( JsonException.class, () -> {json.getArray("v_long");});

		assertThrows( JsonException.class, () -> {json.getBoolean("v_long",true);});

		assertEquals(98L,json.getNumber("v_long",10L));
		assertEquals(Byte.valueOf((byte)98),json.getByteObject("v_long",(byte)10));
		assertEquals(Short.valueOf((short)98),json.getShortObject("v_long",(short)10));
		assertEquals(Integer.valueOf(98),json.getIntObject("v_long",10));
		assertEquals(Long.valueOf(98L),json.getLongObject("v_long",10L));
		assertEquals(98.0f,json.getFloatObject("v_long",10.10f));
		assertEquals(98.0,json.getDoubleObject("v_long",10.10));
		assertEquals(new BigInteger("98"),json.getBigInteger("v_long",BigInteger.TEN));
		assertEquals(new BigDecimal("98"),json.getBigDecimal("v_long",BigDecimal.TEN));
		
		assertThrows( JsonException.class, () -> {json.getString("v_long","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_long",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_long",JsonArray.create());});
	}

	public void testFloatPrimitive() {
		assertThrows( JsonException.class, () -> json.getFloat("fake") );
		assertEquals(56.0f,json.getFloat("fake",56.0f));
	
		assertThrows( JsonException.class, () -> {json.getBoolean("v_float");});

		assertEquals(12,json.getByte("v_float"));
		assertEquals(12,json.getShort("v_float"));
		assertEquals(12,json.getInt("v_float"));
		assertEquals(12L,json.getLong("v_float"));
		assertEquals(12.5f,json.getFloat("v_float"));
		assertEquals(12.5,json.getDouble("v_float"));

		assertThrows( JsonException.class, () -> {json.getBoolean("v_float",true);});

		assertEquals((byte)12,json.getByte("v_float",(byte)10));
		assertEquals((short)12,json.getShort("v_float",(short)10));
		assertEquals(12,json.getInt("v_float",10));
		assertEquals(12L,json.getLong("v_float",10L));
		assertEquals(12.5f,json.getFloat("v_float",10.10f));
		assertEquals(12.5,json.getDouble("v_float",10.10));
	
	}
	public void testFloatObject() {
		assertThrows( JsonException.class, () -> json.getFloatObject("fake") );
		assertEquals(56.0f,json.getFloatObject("fake",56.0f));

		
		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_float");});

		assertEquals(12.5f,json.getNumber("v_float"));
		assertEquals(Byte.valueOf((byte)12),json.getByteObject("v_float"));
		assertEquals(Short.valueOf((short)12),json.getShortObject("v_float"));
		assertEquals(Integer.valueOf(12),json.getIntObject("v_float"));
		assertEquals(Long.valueOf(12L),json.getLongObject("v_float"));
		assertEquals(12.5f,json.getFloatObject("v_float"));
		assertEquals(12.5,json.getDoubleObject("v_float"));
		assertEquals(new BigInteger("12"),json.getBigInteger("v_float"));
		assertEquals(new BigDecimal("12.5"),json.getBigDecimal("v_float"));
		
		assertThrows( JsonException.class, () -> {json.getString("v_float");});
		assertThrows( JsonException.class, () -> {json.getObject("v_float");});
		assertThrows( JsonException.class, () -> {json.getArray("v_float");});

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_float",true);});

		assertEquals(12.5f,json.getNumber("v_float",10.10f));
		assertEquals(Byte.valueOf((byte)12),json.getByteObject("v_float",(byte)10));
		assertEquals(Short.valueOf((short)12),json.getShortObject("v_float",(short)10));
		assertEquals(Integer.valueOf(12),json.getIntObject("v_float",10));
		assertEquals(Long.valueOf(12L),json.getLongObject("v_float",10L));
		assertEquals(Float.valueOf(12.5f),json.getFloatObject("v_float",10.10f));
		assertEquals(Double.valueOf(12.5),json.getDoubleObject("v_float",10.10));
		assertEquals(new BigInteger("12"),json.getBigInteger("v_float",BigInteger.TEN));
		assertEquals(new BigDecimal("12.5"),json.getBigDecimal("v_float",BigDecimal.TEN));

		assertThrows( JsonException.class, () -> {json.getString("v_float","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_float",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_float",JsonArray.create());});
	}

	public void testDoublePrimitie() {
		assertThrows( JsonException.class, () -> json.getDouble("fake") );
		assertEquals(56.0,json.getDouble("fake",56));
	
		assertThrows( JsonException.class, () -> {json.getBoolean("v_double");});

		assertEquals(34,json.getByte("v_double"));
		assertEquals(34,json.getShort("v_double"));
		assertEquals(34,json.getInt("v_double"));
		assertEquals(34L,json.getLong("v_double"));
		assertEquals(34.34f,json.getFloat("v_double"));
		assertEquals(34.34,json.getDouble("v_double"));

		assertThrows( JsonException.class, () -> {json.getBoolean("v_double",true);});

		assertEquals((byte)34,json.getByte("v_double",(byte)10));
		assertEquals((short)34,json.getShort("v_double",(short)10));
		assertEquals(34,json.getInt("v_double",10));
		assertEquals(34L,json.getLong("v_double",10L));
		assertEquals(34.34f,json.getFloat("v_double",10.10f));
		assertEquals(34.34,json.getDouble("v_double",10.10));
	}
	
	public void testDoubleObject() {
		assertThrows( JsonException.class, () -> json.getDoubleObject("fake") );
		assertEquals(Double.valueOf(56.0),json.getDoubleObject("fake",56.0));

		
		assertEquals(34.34,json.getNumber("v_double"));
		assertEquals(Byte.valueOf((byte)34),json.getByteObject("v_double"));
		assertEquals(Short.valueOf((short)34),json.getShortObject("v_double"));
		assertEquals(Integer.valueOf(34),json.getIntObject("v_double"));
		assertEquals(Long.valueOf(34L),json.getLongObject("v_double"));
		assertEquals(34.34f,json.getFloatObject("v_double"));
		assertEquals(34.34,json.getDoubleObject("v_double"));
		assertEquals(new BigInteger("34"),json.getBigInteger("v_double"));
		assertEquals(new BigDecimal("34.34"),json.getBigDecimal("v_double"));

		assertThrows( JsonException.class, () -> {json.getString("v_double");});
		assertThrows( JsonException.class, () -> {json.getObject("v_double");});
		assertThrows( JsonException.class, () -> {json.getArray("v_double");});

		assertEquals(34.34,json.getNumber("v_double",10.10));
		assertEquals(Byte.valueOf((byte)34),json.getByteObject("v_double",(byte)10));
		assertEquals(Short.valueOf((short)34),json.getShortObject("v_double",(short)10));
		assertEquals(Integer.valueOf(34),json.getIntObject("v_double",10));
		assertEquals(Long.valueOf(34L),json.getLongObject("v_double",10L));
		assertEquals(34.34f,json.getFloatObject("v_double",10.10f));
		assertEquals(34.34,json.getDoubleObject("v_double",10.10));
		assertEquals(new BigInteger("34"),json.getBigInteger("v_double",BigInteger.TEN));
		assertEquals(new BigDecimal("34.34"),json.getBigDecimal("v_double",BigDecimal.TEN));
		
		assertThrows( JsonException.class, () -> {json.getString("v_double","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_double",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_double",JsonArray.create());});
	}

	public void testBigIntegerPrimitive() {
		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigInteger");});

		assertEquals((byte)82,json.getByte("v_bigInteger"));
		assertEquals((short)82,json.getShort("v_bigInteger"));
		assertEquals(82,json.getInt("v_bigInteger"));
		assertEquals(82L,json.getLong("v_bigInteger"));
		assertEquals(82.0f,json.getFloat("v_bigInteger"));
		assertEquals(82.0,json.getDouble("v_bigInteger"));
		
		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigInteger",true);});

		assertEquals((byte)82,json.getByte("v_bigInteger",(byte)10));
		assertEquals((short)82,json.getShort("v_bigInteger",(short)10));
		assertEquals(82,json.getInt("v_bigInteger",10));
		assertEquals(82L,json.getLong("v_bigInteger",10L));
		assertEquals(82.0f,json.getFloat("v_bigInteger",10.10f));
		assertEquals(82.0,json.getDouble("v_bigInteger",10.10));
	}
	public void testBigIntegerObject() {
		assertThrows( JsonException.class, () -> json.getBigInteger("fake") );
		assertEquals(BigInteger.TEN,json.getBigInteger("fake",BigInteger.TEN));

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_bigInteger");});
		
		assertEquals(new BigInteger("82"),json.getNumber("v_bigInteger",BigInteger.TEN));
		assertEquals(Byte.valueOf((byte)82),json.getByteObject("v_bigInteger"));
		assertEquals(Short.valueOf((short)82),json.getShortObject("v_bigInteger"));
		assertEquals(Integer.valueOf(82),json.getIntObject("v_bigInteger"));
		assertEquals(Long.valueOf(82L),json.getLongObject("v_bigInteger"));
		assertEquals(82.0f,json.getFloatObject("v_bigInteger"));
		assertEquals(82.0,json.getDoubleObject("v_bigInteger"));
		assertEquals(new BigInteger("82"),json.getBigInteger("v_bigInteger"));
		assertEquals(new BigDecimal("82"),json.getBigDecimal("v_bigInteger"));

		assertThrows( JsonException.class, () -> {json.getString("v_bigInteger");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigInteger");});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigInteger");});

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_bigInteger",true);});

		assertEquals(new BigInteger("82"),json.getNumber("v_bigInteger"));
		assertEquals(Byte.valueOf((byte)82),json.getByteObject("v_bigInteger",(byte)10));
		assertEquals(Short.valueOf((short)82),json.getShortObject("v_bigInteger",(short)10));
		assertEquals(Integer.valueOf(82),json.getIntObject("v_bigInteger",10));
		assertEquals(Long.valueOf(82L),json.getLongObject("v_bigInteger",10L));
		assertEquals(82.0f,json.getFloatObject("v_bigInteger",10.10f));
		assertEquals(82.0,json.getDoubleObject("v_bigInteger",10.10));
		assertEquals(new BigInteger("82"),json.getBigInteger("v_bigInteger",BigInteger.TEN));
		assertEquals(new BigDecimal("82"),json.getBigDecimal("v_bigInteger",BigDecimal.TEN));

		assertThrows( JsonException.class, () -> {json.getString("v_bigInteger","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigInteger",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigInteger",JsonArray.create());});
	}

	public void testBigDecimalPrimitive() {
		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigDecimal");});

		assertEquals((byte)36,json.getByte("v_bigDecimal"));
		assertEquals((short)36,json.getShort("v_bigDecimal"));
		assertEquals(36,json.getInt("v_bigDecimal"));
		assertEquals(36L,json.getLong("v_bigDecimal"));
		assertEquals(36.53f,json.getFloat("v_bigDecimal"));
		assertEquals(36.53,json.getDouble("v_bigDecimal"));

		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigDecimal",true);});

		assertEquals((byte)36,json.getByte("v_bigDecimal",(byte)10));
		assertEquals((short)36,json.getShort("v_bigDecimal",(short)10));
		assertEquals(36,json.getInt("v_bigDecimal",10));
		assertEquals(36L,json.getLong("v_bigDecimal",10L));
		assertEquals(36.53f,json.getFloat("v_bigDecimal",10.10f));
		assertEquals(36.53,json.getDouble("v_bigDecimal",10.10));
	}
	
	public void testBigDecimalObject() {
		assertThrows( JsonException.class, () -> json.getBigDecimal("fake") );
		assertEquals(BigDecimal.TEN,json.getBigDecimal("fake",BigDecimal.TEN));

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_bigDecimal");});

		assertEquals(new BigDecimal("36.53"),json.getNumber("v_bigDecimal"));
		assertEquals(Byte.valueOf((byte)36),json.getByteObject("v_bigDecimal"));
		assertEquals(Short.valueOf((short)36),json.getShortObject("v_bigDecimal"));
		assertEquals(Integer.valueOf(36),json.getIntObject("v_bigDecimal"));
		assertEquals(Long.valueOf(36L),json.getLongObject("v_bigDecimal"));
		assertEquals(36.53f,json.getFloatObject("v_bigDecimal"));
		assertEquals(36.53,json.getDoubleObject("v_bigDecimal"));
		assertEquals(new BigInteger("36"),json.getBigInteger("v_bigDecimal"));
		assertEquals(new BigDecimal("36.53"),json.getBigDecimal("v_bigDecimal"));

		assertThrows( JsonException.class, () -> {json.getString("v_bigDecimal");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigDecimal");});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigDecimal");});

		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_bigDecimal",true);});

		assertEquals(new BigDecimal("36.53"),json.getNumber("v_bigDecimal",BigDecimal.TEN));
		assertEquals(Byte.valueOf((byte)36),json.getByteObject("v_bigDecimal",(byte)10));
		assertEquals(Short.valueOf((short)36),json.getShortObject("v_bigDecimal",(short)10));
		assertEquals(Integer.valueOf(36),json.getIntObject("v_bigDecimal",10));
		assertEquals(Long.valueOf(36L),json.getLongObject("v_bigDecimal",10L));
		assertEquals(36.53f,json.getFloatObject("v_bigDecimal",10.10f));
		assertEquals(36.53,json.getDoubleObject("v_bigDecimal",10.10));
		assertEquals(new BigInteger("36"),json.getBigInteger("v_bigDecimal",BigInteger.TEN));
		assertEquals(new BigDecimal("36.53"),json.getBigDecimal("v_bigDecimal",BigDecimal.TEN));

		assertThrows( JsonException.class, () -> {json.getString("v_bigDecimal","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigDecimal",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigDecimal",JsonArray.create());});
	}

	public void testBoolean() {
		assertEquals(true,json.getBoolean("v_boolean"));
		assertEquals(true,json.getBoolean("v_boolean",false));
		assertEquals(true,json.getBoolean("fake",true));

		assertThrows( JsonException.class, () -> json.getBoolean("fake") );
		assertThrows( JsonException.class, () -> json.getBooleanObject("fake") );

		assertThrows( JsonException.class, () -> {json.getNumber("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getByte("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getShort("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getInt("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getLong("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getString("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getObject("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getArray("v_boolean");});

		assertThrows( JsonException.class, () -> {json.getNumber("v_boolean",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getByte("v_boolean",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShort("v_boolean",(short)10);});
		assertThrows( JsonException.class, () -> {json.getInt("v_boolean",10);});
		assertThrows( JsonException.class, () -> {json.getLong("v_boolean",10L);});
		assertThrows( JsonException.class, () -> {json.getFloat("v_boolean",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDouble("v_boolean",10.10);});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_boolean",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_boolean",(short)10);});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_boolean",10);});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_boolean",10L);});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_boolean",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_boolean",10.10);});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_boolean",BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_boolean",BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {json.getString("v_boolean","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_boolean",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_boolean",JsonArray.create());});
	}
	
	public void testString() {
		assertEquals("abc",json.getString("v_string"));
		assertEquals("abc",json.getString("v_string","xy"));
		assertEquals("xy",json.getString("fake","xy"));

		assertThrows( JsonException.class, () -> json.getString("fake") );

		assertThrows( JsonException.class, () -> {json.getNumber("v_string");});
		assertThrows( JsonException.class, () -> {json.getByte("v_string");});
		assertThrows( JsonException.class, () -> {json.getShort("v_string");});
		assertThrows( JsonException.class, () -> {json.getInt("v_string");});
		assertThrows( JsonException.class, () -> {json.getLong("v_string");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_string");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_string");});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_string");});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_string");});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_string");});
		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getArray("v_string");});

		assertThrows( JsonException.class, () -> {json.getNumber("v_string",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getByte("v_string",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShort("v_string",(short)10);});
		assertThrows( JsonException.class, () -> {json.getInt("v_string",10);});
		assertThrows( JsonException.class, () -> {json.getLong("v_string",10L);});
		assertThrows( JsonException.class, () -> {json.getFloat("v_string",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDouble("v_string",10.10);});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_string",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_string",(short)10);});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_string",10);});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_string",10L);});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_string",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_string",10.10);});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_string",BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_string",BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_string",true);});
		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_string",true);});
		assertThrows( JsonException.class, () -> {json.getObject("v_string",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_string",JsonArray.create());});
	}
	
	public void testObject() {
		assertEquals(JsonObject.create().put("a", 1),json.getObject("v_object"));
		assertEquals(JsonObject.create().put("a", 1),json.getObject("v_object",JsonObject.create()));
		assertEquals(JsonObject.create(),json.getObject("fake",JsonObject.create()));

		assertThrows( JsonException.class, () -> json.getObject("fake") );

		assertThrows( JsonException.class, () -> {json.getNumber("v_array");});
		assertThrows( JsonException.class, () -> {json.getByte("v_object");});
		assertThrows( JsonException.class, () -> {json.getShort("v_object");});
		assertThrows( JsonException.class, () -> {json.getInt("v_object");});
		assertThrows( JsonException.class, () -> {json.getLong("v_object");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_object");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_object");});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_object");});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_object");});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_object");});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_object");});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_object");});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_object");});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_object");});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_object");});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_object");});
		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_object");});
		assertThrows( JsonException.class, () -> {json.getArray("v_object");});

		assertThrows( JsonException.class, () -> {json.getNumber("v_object",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getByte("v_object",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShort("v_object",(short)10);});
		assertThrows( JsonException.class, () -> {json.getInt("v_object",10);});
		assertThrows( JsonException.class, () -> {json.getLong("v_object",10L);});
		assertThrows( JsonException.class, () -> {json.getFloat("v_object",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDouble("v_object",10.10);});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_object",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_object",(short)10);});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_object",10);});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_object",10L);});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_object",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_object",10.10);});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_object",BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_object",BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_object",true);});
		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_object",true);});
		assertThrows( JsonException.class, () -> {json.getArray("v_object",JsonArray.create());});
	}
	
	public void testArray() {
		assertEquals(JsonArray.create().add(1),json.getArray("v_array"));
		assertEquals(JsonArray.create().add(1),json.getArray("v_array",JsonArray.create()));
		assertEquals(JsonArray.create(),json.getArray("fake",JsonArray.create()));

		assertThrows( JsonException.class, () -> json.getArray("fake") );

		assertThrows( JsonException.class, () -> {json.getNumber("v_array");});
		assertThrows( JsonException.class, () -> {json.getByte("v_array");});
		assertThrows( JsonException.class, () -> {json.getShort("v_array");});
		assertThrows( JsonException.class, () -> {json.getInt("v_array");});
		assertThrows( JsonException.class, () -> {json.getLong("v_array");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_array");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_array");});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_array");});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_array");});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_array");});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_array");});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_array");});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_array");});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_array");});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_array");});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_array");});
		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_array");});
		assertThrows( JsonException.class, () -> {json.getObject("v_array");});

		assertThrows( JsonException.class, () -> {json.getNumber("v_array",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getByte("v_array",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShort("v_array",(short)10);});
		assertThrows( JsonException.class, () -> {json.getInt("v_array",10);});
		assertThrows( JsonException.class, () -> {json.getLong("v_array",10L);});
		assertThrows( JsonException.class, () -> {json.getFloat("v_array",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDouble("v_array",10.10);});
		assertThrows( JsonException.class, () -> {json.getByteObject("v_array",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShortObject("v_array",(short)10);});
		assertThrows( JsonException.class, () -> {json.getIntObject("v_array",10);});
		assertThrows( JsonException.class, () -> {json.getLongObject("v_array",10L);});
		assertThrows( JsonException.class, () -> {json.getFloatObject("v_array",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDoubleObject("v_array",10.10);});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_array",BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_array",BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_array",true);});
		assertThrows( JsonException.class, () -> {json.getBooleanObject("v_array",true);});
		assertThrows( JsonException.class, () -> {json.getObject("v_array",JsonObject.create());});
	}

	public void testLocalDate() {
		assertEquals(LocalDate.of(2020, 2, 20), json.getLocalDate("v_local_date"));
		assertEquals(LocalDate.of(2020, 2, 20), json.getLocalDate("v_local_date", LocalDate.of(2030, 3, 30)));
		assertEquals(LocalDate.of(2030, 3, 30), json.getLocalDate("v_local_date_fake", LocalDate.of(2030, 3, 30)));
	}

	public void testLocalTime() {
		assertEquals(LocalTime.of(13, 44, 18), json.getLocalTime("v_local_time"));
		assertEquals(LocalTime.of(13, 44, 18), json.getLocalTime("v_local_time", LocalTime.of(14, 45, 19)));
		assertEquals(LocalTime.of(14, 45, 19), json.getLocalTime("v_local_time_fake", LocalTime.of(14, 45, 19)));
	}

	public void testLocalDateTime() {
		assertEquals(LocalDateTime.of(2020, 2, 20, 13, 44, 18), json.getLocalDateTime("v_local_datetime"));
		assertEquals(LocalDateTime.of(2020, 2, 20, 13, 44, 18), json.getLocalDateTime("v_local_datetime",LocalDateTime.of(2030, 3, 30, 14, 45, 19)));
		assertEquals(LocalDateTime.of(2030, 3, 30, 14, 45, 19), json.getLocalDateTime("v_local_datetime_fake",LocalDateTime.of(2030, 3, 30, 14, 45, 19)));
	}
	
	public void testOffsetTime() {
		assertEquals(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2)), json.getOffsetTime("v_offset_time"));
		assertEquals(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2)), json.getOffsetTime("v_offset_time",OffsetTime.of(14, 45, 19, 0, ZoneOffset.ofHours(2))));
		assertEquals(OffsetTime.of(14, 45, 19, 0, ZoneOffset.ofHours(2)), json.getOffsetTime("v_offset_time_fake",OffsetTime.of(14, 45, 19, 0, ZoneOffset.ofHours(2))));
	}
	
	public void testOffsetDateTime() {
		assertEquals(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2)), json.getOffsetDateTime("v_offset_datetime"));
		assertEquals(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2)), json.getOffsetDateTime("v_offset_datetime",OffsetDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneOffset.ofHours(2))));
		assertEquals(OffsetDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneOffset.ofHours(2)), json.getOffsetDateTime("v_offset_datetime_fake",OffsetDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneOffset.ofHours(2))));
	}
	
	public void testZonedDateTime() {
		assertEquals(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern")), json.getZonedDateTime("v_zoned_datetime"));
		assertEquals(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern")), json.getZonedDateTime("v_zoned_datetime",ZonedDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneId.of("US/Eastern"))));
		assertEquals(ZonedDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneId.of("US/Eastern")), json.getZonedDateTime("v_zoned_datetime_fake",ZonedDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneId.of("US/Eastern"))));
	}
}
