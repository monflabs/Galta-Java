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
package tests.json.jsonvalues;

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
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class GetterPropertyTest extends ProjectTestCase {
	
	
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

	static JsonValues json;
	
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
		json = JsonValues.of(a);
	}
	
	public void testIs() throws Exception {
		for(String k: json.objectValue().keySet()) {
			JsonValues v = json.get(k);
			assertEquals( in(k,"v_null")? true : false, v.isNull());
			assertEquals( in(k,"v_byte","v_short","v_int","v_long","v_float","v_double","v_bigInteger","v_bigDecimal") ? true : false, v.isNumber());
			assertEquals( in(k,"v_boolean")? true : false, v.isBoolean());
			assertEquals( in(k,"v_string","v_local_date","v_local_time","v_local_datetime","v_offset_time","v_offset_datetime","v_zoned_datetime") ? true : false, v.isString());
			assertEquals( in(k,"v_object") ? true : false, v.isObject());
			assertEquals( in(k,"v_array")? true : false, v.isArray());
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
		JsonValues v = JsonValues.of();
		assertTrue( v.firstValue().isEmpty() );
		JsonValues v0 = JsonValues.of(JsonArray.of());
		assertTrue( v0.firstValue().isEmpty() );
		JsonValues v1 = JsonValues.of(JsonArray.of(1));
		assertEquals( 1, v1.firstValue().intValue() );
		JsonValues v2 = JsonValues.of(JsonArray.of(1,2));
		assertEquals( 1, v2.firstValue().intValue() );

		JsonValues w0 = JsonValues.of(JsonArray.of(),JsonArray.of());
		assertTrue( w0.firstValue().isEmpty() );
		JsonValues w1 = JsonValues.of(JsonArray.of(1,2,3),JsonArray.of(4,5,6));
		assertEquals( JsonValues.of(1,4), w1.firstValue() );
	}
	
	public void testLast() throws Exception {
		JsonValues v = JsonValues.of();
		assertTrue( v.lastValue().isEmpty() );
		JsonValues v0 = JsonValues.of(JsonArray.of());
		assertTrue( v0.lastValue().isEmpty() );
		JsonValues v1 = JsonValues.of(JsonArray.of(1));
		assertEquals( 1, v1.lastValue().intValue() );
		JsonValues v2 = JsonValues.of(JsonArray.of(1,2));
		assertEquals( 2, v2.lastValue().intValue() );

		JsonValues w0 = JsonValues.of(JsonArray.of(),JsonArray.of());
		assertTrue( w0.lastValue().isEmpty() );
		JsonValues w1 = JsonValues.of(JsonArray.of(1,2,3),JsonArray.of(4,5,6));
		assertEquals( JsonValues.of(3,6), w1.lastValue() );
	}


	public void testInvalidMemberAccess() {
		for(String k: json.objectValue().keySet()) {
			JsonValues v = json.get(k);
			if(!in(k,"v_null","v_object")) {
				
				assertThrows( JsonException.class, () -> {v.getNumber("xy");});
				assertThrows( JsonException.class, () -> {v.getByte("xy");});
				assertThrows( JsonException.class, () -> {v.getShort("xy");});
				assertThrows( JsonException.class, () -> {v.getInt("xy");});
				assertThrows( JsonException.class, () -> {v.getLong("xy");});
				assertThrows( JsonException.class, () -> {v.getDouble("xy");});
				assertThrows( JsonException.class, () -> {v.getFloat("xy");});
				assertThrows( JsonException.class, () -> {v.getBigInteger("xy");});
				assertThrows( JsonException.class, () -> {v.getBigDecimal("xy");});
				assertThrows( JsonException.class, () -> {v.getString("xy");});
				assertThrows( JsonException.class, () -> {v.getBoolean("xy");});
				assertThrows( JsonException.class, () -> {v.getObject("xy");});
				assertThrows( JsonException.class, () -> {v.getArray("xy");});
				assertThrows( JsonException.class, () -> {v.getLocalDate("xy");});
				assertThrows( JsonException.class, () -> {v.getLocalTime("xy");});
				assertThrows( JsonException.class, () -> {v.getLocalDateTime("xy");});
				assertThrows( JsonException.class, () -> {v.getOffsetTime("xy");});
				assertThrows( JsonException.class, () -> {v.getOffsetDateTime("xy");});
				assertThrows( JsonException.class, () -> {v.getZonedDateTime("xy");});

				assertThrows( JsonException.class, () -> {v.getNumber("xy",10);});
				assertThrows( JsonException.class, () -> {v.getByte("xy",(byte)10);});
				assertThrows( JsonException.class, () -> {v.getShort("xy",(short)10);});
				assertThrows( JsonException.class, () -> {v.getInt("xy",10);});
				assertThrows( JsonException.class, () -> {v.getLong("xy",10L);});
				assertThrows( JsonException.class, () -> {v.getFloat("xy",10.10f);});
				assertThrows( JsonException.class, () -> {v.getDouble("xy",10.10);});
				assertThrows( JsonException.class, () -> {v.getBigInteger("xy",BigInteger.TEN);});
				assertThrows( JsonException.class, () -> {v.getBigDecimal("xy",BigDecimal.TEN);});
				assertThrows( JsonException.class, () -> {v.getString("xy","abc");});
				assertThrows( JsonException.class, () -> {v.getBoolean("xy",true);});
				assertThrows( JsonException.class, () -> {v.getObject("xy",null);});
				assertThrows( JsonException.class, () -> {v.getArray("xy",null);});
				assertThrows( JsonException.class, () -> {v.getLocalDate("xy",null);});
				assertThrows( JsonException.class, () -> {v.getLocalTime("xy",null);});
				assertThrows( JsonException.class, () -> {v.getLocalDateTime("xy",null);});
				assertThrows( JsonException.class, () -> {v.getOffsetTime("xy",null);});
				assertThrows( JsonException.class, () -> {v.getOffsetDateTime("xy",null);});
				assertThrows( JsonException.class, () -> {v.getZonedDateTime("xy",null);});
			}
		}
	}

	public void testNull() throws Exception {
		assertThrows(JsonException.class, () -> json.getNumber(0));
		assertThrows(JsonException.class, () -> json.getByte(0));
		assertThrows(JsonException.class, () -> json.getShort(0));
		assertThrows(JsonException.class, () -> json.getInt(0));
		assertThrows(JsonException.class, () -> json.getLong(0));
		assertThrows(JsonException.class, () -> json.getFloat(0));
		assertThrows(JsonException.class, () -> json.getDouble(0));
		assertThrows(JsonException.class, () -> json.getBigInteger(0));
		assertThrows(JsonException.class, () -> json.getBigDecimal(0));
		assertThrows(JsonException.class, () -> json.getBoolean(0));
		assertThrows(JsonException.class, () -> json.getString(0));

		assertEquals(10,json.getNumber("v_null",10));
		assertEquals((byte)10,json.getByte("v_null",(byte)10));
		assertEquals((short)10,json.getShort("v_null",(short)10));
		assertEquals(10,json.getInt("v_null",10));
		assertEquals(10L,json.getLong("v_null",10L));
		assertEquals(10.10f,json.getFloat("v_null",10.10f));
		assertEquals(10.10,json.getDouble("v_null",10.10));
		assertEquals(BigInteger.TEN,json.getBigInteger("v_null",BigInteger.TEN));
		assertEquals(BigDecimal.TEN,json.getBigDecimal("v_null",BigDecimal.TEN));
		assertEquals(true,json.getBoolean("v_null",true));
		assertEquals("xy",json.getString("v_null","xy"));
		assertEquals(JsonObject.create(),json.getObject("v_null",JsonObject.create()));
		assertEquals(JsonArray.create(),json.getArray("v_null",JsonArray.create()));
	}

	public void testByte() {
		assertEquals((byte)32,json.getNumber("v_byte"));
		assertEquals((byte)32,json.getNumber("v_byte",(short)10));

		assertEquals((byte)32,json.getByte("v_byte"));
		assertEquals((byte)32,json.getByte("v_byte",(byte)10));
		assertEquals((byte)67,json.getByte("fake",(byte)67));

		assertThrows( JsonException.class, () -> {json.getByte("fake");} );

		assertEquals((short)32,json.getShort("v_byte"));
		assertEquals(32,json.getInt("v_byte"));
		assertEquals(32L,json.getLong("v_byte"));
		assertEquals(32.0f,json.getFloat("v_byte"));
		assertEquals(32.0,json.getDouble("v_byte"));
		assertEquals(new BigInteger("32"),json.getBigInteger("v_byte"));
		assertEquals(new BigDecimal("32"),json.getBigDecimal("v_byte"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_byte");});
		assertThrows( JsonException.class, () -> {json.getString("v_byte");});
		assertThrows( JsonException.class, () -> {json.getObject("v_byte");});
		assertThrows( JsonException.class, () -> {json.getArray("v_byte");});
		
		assertEquals((short)32,json.getShort("v_byte",(short)10));
		assertEquals(32,json.getInt("v_byte",10));
		assertEquals(32L,json.getLong("v_byte",10L));
		assertEquals(32.0f,json.getFloat("v_byte",10.10f));
		assertEquals(32.0,json.getDouble("v_byte",10.10));
		assertEquals(new BigInteger("32"),json.getBigInteger("v_byte",BigInteger.TEN));
		assertEquals(new BigDecimal("32"),json.getBigDecimal("v_byte",BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_byte",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_byte","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_byte",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_byte",JsonArray.create());});
	}

	public void testShort() {
		assertEquals((short)78,json.getNumber("v_short"));
		assertEquals((short)78,json.getNumber("v_short",(short)10));

		assertEquals((short)78,json.getShort("v_short"));
		assertEquals((short)78,json.getShort("v_short",(short)10));
		assertEquals((short)67,json.getShort("fake",(short)67));
		
		assertThrows( JsonException.class, () -> {json.getShort("fake");} );

		assertEquals((byte)78,json.getByte("v_short"));
		assertEquals(78,json.getInt("v_short"));
		assertEquals(78L,json.getLong("v_short"));
		assertEquals(78.0f,json.getFloat("v_short"));
		assertEquals(78.0,json.getDouble("v_short"));
		assertEquals(new BigInteger("78"),json.getBigInteger("v_short"));
		assertEquals(new BigDecimal("78"),json.getBigDecimal("v_short"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_short");});
		assertThrows( JsonException.class, () -> {json.getString("v_short");});
		assertThrows( JsonException.class, () -> {json.getObject("v_short");});
		assertThrows( JsonException.class, () -> {json.getArray("v_short");});

		assertEquals((byte)78,json.getByte("v_short",(byte)10));
		assertEquals(78,json.getInt("v_short",10));
		assertEquals(78L,json.getLong("v_short",10L));
		assertEquals(78.0f,json.getFloat("v_short",10.10f));
		assertEquals(78.0,json.getDouble("v_short",10.10));
		assertEquals(new BigInteger("78"),json.getBigInteger("v_short",BigInteger.TEN));
		assertEquals(new BigDecimal("78"),json.getBigDecimal("v_short",BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_short",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_short","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_short",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_short",JsonArray.create());});
	}

	public void testInt() {
		assertEquals(56,json.getNumber("v_int"));
		assertEquals(56,json.getNumber("v_int",10));

		assertEquals(56,json.getInt("v_int"));
		assertEquals(56,json.getInt("v_int",10));
		assertEquals(67,json.getInt("fake",67));
		
		assertThrows( JsonException.class, () -> {json.getInt("fake");} );

		assertEquals((byte)56,json.getByte("v_int"));
		assertEquals((short)56,json.getShort("v_int"));
		assertEquals(56L,json.getLong("v_int"));
		assertEquals(56.0f,json.getFloat("v_int"));
		assertEquals(56.0,json.getDouble("v_int"));
		assertEquals(new BigInteger("56"),json.getBigInteger("v_int"));
		assertEquals(new BigDecimal("56"),json.getBigDecimal("v_int"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_int");});
		assertThrows( JsonException.class, () -> {json.getString("v_int");});
		assertThrows( JsonException.class, () -> {json.getObject("v_int");});
		assertThrows( JsonException.class, () -> {json.getArray("v_int");});

		assertEquals((byte)56,json.getByte("v_int",(byte)10));
		assertEquals((short)56,json.getShort("v_int",(short)10));
		assertEquals(56L,json.getLong("v_int",10L));
		assertEquals(56.0f,json.getFloat("v_int",10.10f));
		assertEquals(56.0,json.getDouble("v_int",10.10));
		assertEquals(new BigInteger("56"),json.getBigInteger("v_int",BigInteger.TEN));
		assertEquals(new BigDecimal("56"),json.getBigDecimal("v_int",BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_int",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_int","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_int",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_int",JsonArray.create());});
	}

	public void testLong() {
		assertEquals(98L,json.getNumber("v_long"));
		assertEquals(98L,json.getNumber("v_long",10L));

		assertEquals(98L,json.getLong("v_long"));
		assertEquals(98L,json.getLong("v_long",10L));
		assertEquals(456L,json.getLong("fake",456L));
		
		assertThrows( JsonException.class, () -> {json.getLong("fake");} );

		assertEquals((byte)98,json.getByte("v_long"));
		assertEquals((short)98,json.getShort("v_long"));
		assertEquals(98,json.getInt("v_long"));
		assertEquals(98.0f,json.getFloat("v_long"));
		assertEquals(98.0,json.getDouble("v_long"));
		assertEquals(new BigInteger("98"),json.getBigInteger("v_long"));
		assertEquals(new BigDecimal("98"),json.getBigDecimal("v_long"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_long");});
		assertThrows( JsonException.class, () -> {json.getString("v_long");});
		assertThrows( JsonException.class, () -> {json.getObject("v_long");});
		assertThrows( JsonException.class, () -> {json.getArray("v_long");});

		assertEquals((byte)98,json.getByte("v_long",(byte)10));
		assertEquals((short)98,json.getShort("v_long",(short)10));
		assertEquals(98,json.getInt("v_long",10));
		assertEquals(98.0f,json.getFloat("v_long",10.10f));
		assertEquals(98.0,json.getDouble("v_long",10.10));
		assertEquals(new BigInteger("98"),json.getBigInteger("v_long",BigInteger.TEN));
		assertEquals(new BigDecimal("98"),json.getBigDecimal("v_long",BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_long",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_long","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_long",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_long",JsonArray.create());});
	}

	public void testFloat() {
		assertEquals(12.5f,json.getNumber("v_float"));
		assertEquals(12.5f,json.getNumber("v_float",10.10f));

		assertEquals(12.5f,json.getFloat("v_float"));
		assertEquals(12.5f,json.getFloat("v_float",10.10f));
		assertEquals(56.0f,json.getFloat("fake",56.0f));
		
		assertThrows( JsonException.class, () -> {json.getFloat("fake");} );

		assertEquals(12,json.getByte("v_float"));
		assertEquals(12,json.getShort("v_float"));
		assertEquals(12,json.getInt("v_float"));
		assertEquals(12L,json.getLong("v_float"));
		assertEquals(12.5,json.getDouble("v_float"));
		assertEquals(new BigInteger("12"),json.getBigInteger("v_float"));
		assertEquals(new BigDecimal("12.5"),json.getBigDecimal("v_float"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_float");});
		assertThrows( JsonException.class, () -> {json.getString("v_float");});
		assertThrows( JsonException.class, () -> {json.getObject("v_float");});
		assertThrows( JsonException.class, () -> {json.getArray("v_float");});

		assertEquals((byte)12,json.getByte("v_float",(byte)10));
		assertEquals((short)12,json.getShort("v_float",(short)10));
		assertEquals(12,json.getInt("v_float",10));
		assertEquals(12L,json.getLong("v_float",10L));
		assertEquals(12.5,json.getDouble("v_float",10.10));
		assertEquals(new BigInteger("12"),json.getBigInteger("v_float",BigInteger.TEN));
		assertEquals(new BigDecimal("12.5"),json.getBigDecimal("v_float",BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_float",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_float","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_float",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_float",JsonArray.create());});
	}

	public void testDouble() {
		assertEquals(34.34,json.getNumber("v_double"));
		assertEquals(34.34,json.getNumber("v_double",10.10));

		assertEquals(34.34,json.getDouble("v_double"));
		assertEquals(34.34,json.getDouble("v_double",10.10));
		assertEquals(56.0,json.getDouble("fake",56));
		
		assertThrows( JsonException.class, () -> {json.getDouble("fake");} );

		assertEquals(34,json.getByte("v_double"));
		assertEquals(34,json.getShort("v_double"));
		assertEquals(34,json.getInt("v_double"));
		assertEquals(34L,json.getLong("v_double"));
		assertEquals(34.34f,json.getFloat("v_double"));
		assertEquals(new BigInteger("34"),json.getBigInteger("v_double"));
		assertEquals(new BigDecimal("34.34"),json.getBigDecimal("v_double"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_double");});
		assertThrows( JsonException.class, () -> {json.getString("v_double");});
		assertThrows( JsonException.class, () -> {json.getObject("v_double");});
		assertThrows( JsonException.class, () -> {json.getArray("v_double");});

		assertEquals((byte)34,json.getByte("v_double",(byte)10));
		assertEquals((short)34,json.getShort("v_double",(short)10));
		assertEquals(34,json.getInt("v_double",10));
		assertEquals(34L,json.getLong("v_double",10L));
		assertEquals(34.34f,json.getFloat("v_double",10.10f));
		assertEquals(new BigInteger("34"),json.getBigInteger("v_double",BigInteger.TEN));
		assertEquals(new BigDecimal("34.34"),json.getBigDecimal("v_double",BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_double",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_double","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_double",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_double",JsonArray.create());});
	}

	public void testBigInteger() {
		assertEquals(new BigInteger("82"),json.getNumber("v_bigInteger"));
		assertEquals(new BigInteger("82"),json.getNumber("v_bigInteger",BigInteger.TEN));

		assertEquals(new BigInteger("82"),json.getBigInteger("v_bigInteger"));
		assertEquals(new BigInteger("82"),json.getBigInteger("v_bigInteger",BigInteger.TEN));
		assertEquals(BigInteger.TEN,json.getBigInteger("fake",BigInteger.TEN));
		
		assertThrows( JsonException.class, () -> {json.getBigInteger("fake");} );

		assertEquals((byte)82,json.getByte("v_bigInteger"));
		assertEquals((short)82,json.getShort("v_bigInteger"));
		assertEquals(82,json.getInt("v_bigInteger"));
		assertEquals(82L,json.getLong("v_bigInteger"));
		assertEquals(82.0f,json.getFloat("v_bigInteger"));
		assertEquals(82.0,json.getDouble("v_bigInteger"));
		assertEquals(new BigDecimal("82"),json.getBigDecimal("v_bigInteger"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigInteger");});
		assertThrows( JsonException.class, () -> {json.getString("v_bigInteger");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigInteger");});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigInteger");});

		assertEquals((byte)82,json.getByte("v_bigInteger",(byte)10));
		assertEquals((short)82,json.getShort("v_bigInteger",(short)10));
		assertEquals(82,json.getInt("v_bigInteger",10));
		assertEquals(82L,json.getLong("v_bigInteger",10L));
		assertEquals(82.0f,json.getFloat("v_bigInteger",10.10f));
		assertEquals(82.0,json.getDouble("v_bigInteger",10.10));
		assertEquals(new BigDecimal("82"),json.getBigDecimal("v_bigInteger",BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigInteger",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_bigInteger","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigInteger",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigInteger",JsonArray.create());});
	}

	public void testBigDecimal() {
		assertEquals(new BigDecimal("36.53"),json.getNumber("v_bigDecimal"));
		assertEquals(new BigDecimal("36.53"),json.getNumber("v_bigDecimal",BigDecimal.TEN));

		assertEquals(new BigDecimal("36.53"),json.getBigDecimal("v_bigDecimal"));
		assertEquals(new BigDecimal("36.53"),json.getBigDecimal("v_bigDecimal",BigDecimal.TEN));
		assertEquals(BigDecimal.TEN,json.getBigDecimal("fake",BigDecimal.TEN));
		
		assertThrows( JsonException.class, () -> {json.getBigDecimal("fake");} );

		assertEquals((byte)36,json.getByte("v_bigDecimal"));
		assertEquals((short)36,json.getShort("v_bigDecimal"));
		assertEquals(36,json.getInt("v_bigDecimal"));
		assertEquals(36L,json.getLong("v_bigDecimal"));
		assertEquals(36.53f,json.getFloat("v_bigDecimal"));
		assertEquals(36.53,json.getDouble("v_bigDecimal"));
		assertEquals(new BigInteger("36"),json.getBigInteger("v_bigDecimal"));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigDecimal");});
		assertThrows( JsonException.class, () -> {json.getString("v_bigDecimal");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigDecimal");});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigDecimal");});

		assertEquals((byte)36,json.getByte("v_bigDecimal",(byte)10));
		assertEquals((short)36,json.getShort("v_bigDecimal",(short)10));
		assertEquals(36,json.getInt("v_bigDecimal",10));
		assertEquals(36L,json.getLong("v_bigDecimal",10L));
		assertEquals(36.53f,json.getFloat("v_bigDecimal",10.10f));
		assertEquals(36.53,json.getDouble("v_bigDecimal",10.10));
		assertEquals(new BigInteger("36"),json.getBigInteger("v_bigDecimal",BigInteger.TEN));
		assertThrows( JsonException.class, () -> {json.getBoolean("v_bigDecimal",true);});
		assertThrows( JsonException.class, () -> {json.getString("v_bigDecimal","xy");});
		assertThrows( JsonException.class, () -> {json.getObject("v_bigDecimal",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_bigDecimal",JsonArray.create());});
	}

	public void testBoolean() {
		assertEquals(true,json.getBoolean("v_boolean"));
		assertEquals(true,json.getBoolean("v_boolean",false));
		assertEquals(true,json.getBoolean("fake",true));

		assertThrows( JsonException.class, () -> {json.getBoolean("fake");} );

		assertThrows( JsonException.class, () -> {json.getNumber("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getByte("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getShort("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getInt("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getLong("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_boolean");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_boolean");});
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
		
		assertThrows( JsonException.class, () -> {json.getString("fake");} );

		assertThrows( JsonException.class, () -> {json.getNumber("v_string");});
		assertThrows( JsonException.class, () -> {json.getByte("v_string");});
		assertThrows( JsonException.class, () -> {json.getShort("v_string");});
		assertThrows( JsonException.class, () -> {json.getInt("v_string");});
		assertThrows( JsonException.class, () -> {json.getLong("v_string");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_string");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_string");});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_string");});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_string");});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_string");});
		assertThrows( JsonException.class, () -> {json.getObject("v_string");});
		assertThrows( JsonException.class, () -> {json.getArray("v_string");});

		assertThrows( JsonException.class, () -> {json.getNumber("v_string",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getByte("v_string",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShort("v_string",(short)10);});
		assertThrows( JsonException.class, () -> {json.getInt("v_string",10);});
		assertThrows( JsonException.class, () -> {json.getLong("v_string",10L);});
		assertThrows( JsonException.class, () -> {json.getFloat("v_string",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDouble("v_string",10.10);});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_string",BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_string",BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_string",true);});
		assertThrows( JsonException.class, () -> {json.getObject("v_string",JsonObject.create());});
		assertThrows( JsonException.class, () -> {json.getArray("v_string",JsonArray.create());});
	}
	
	public void testObject() {
		assertEquals(JsonObject.create().put("a", 1),json.getObject("v_object"));
		assertEquals(JsonObject.create().put("a", 1),json.getObject("v_object",JsonObject.create()));
		assertEquals(JsonObject.create(),json.getObject("fake",JsonObject.create()));
		
		assertThrows( JsonException.class, () -> {json.getObject("fake");} );

		assertThrows( JsonException.class, () -> {json.getNumber("v_array");});
		assertThrows( JsonException.class, () -> {json.getByte("v_object");});
		assertThrows( JsonException.class, () -> {json.getShort("v_object");});
		assertThrows( JsonException.class, () -> {json.getInt("v_object");});
		assertThrows( JsonException.class, () -> {json.getLong("v_object");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_object");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_object");});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_object");});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_object");});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_object");});
		assertThrows( JsonException.class, () -> {json.getArray("v_object");});

		assertThrows( JsonException.class, () -> {json.getNumber("v_object",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getByte("v_object",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShort("v_object",(short)10);});
		assertThrows( JsonException.class, () -> {json.getInt("v_object",10);});
		assertThrows( JsonException.class, () -> {json.getLong("v_object",10L);});
		assertThrows( JsonException.class, () -> {json.getFloat("v_object",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDouble("v_object",10.10);});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_object",BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_object",BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_object",true);});
		assertThrows( JsonException.class, () -> {json.getArray("v_object",JsonArray.create());});
	}
	
	public void testArray() {
		assertEquals(JsonArray.create().add(1),json.getArray("v_array"));
		assertEquals(JsonArray.create().add(1),json.getArray("v_array",JsonArray.create()));
		assertEquals(JsonArray.create(),json.getArray("fake",JsonArray.create()));
		
		assertThrows( JsonException.class, () -> {json.getArray("fake");} );

		assertThrows( JsonException.class, () -> {json.getNumber("v_array");});
		assertThrows( JsonException.class, () -> {json.getByte("v_array");});
		assertThrows( JsonException.class, () -> {json.getShort("v_array");});
		assertThrows( JsonException.class, () -> {json.getInt("v_array");});
		assertThrows( JsonException.class, () -> {json.getLong("v_array");});
		assertThrows( JsonException.class, () -> {json.getFloat("v_array");});
		assertThrows( JsonException.class, () -> {json.getDouble("v_array");});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_array");});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_array");});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_array");});
		assertThrows( JsonException.class, () -> {json.getObject("v_array");});

		assertThrows( JsonException.class, () -> {json.getNumber("v_array",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getByte("v_array",(byte)10);});
		assertThrows( JsonException.class, () -> {json.getShort("v_array",(short)10);});
		assertThrows( JsonException.class, () -> {json.getInt("v_array",10);});
		assertThrows( JsonException.class, () -> {json.getLong("v_array",10L);});
		assertThrows( JsonException.class, () -> {json.getFloat("v_array",10.10f);});
		assertThrows( JsonException.class, () -> {json.getDouble("v_array",10.10);});
		assertThrows( JsonException.class, () -> {json.getBigInteger("v_array",BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {json.getBigDecimal("v_array",BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {json.getBoolean("v_array",true);});
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
