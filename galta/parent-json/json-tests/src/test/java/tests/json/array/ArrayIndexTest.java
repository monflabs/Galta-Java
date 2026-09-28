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
package tests.json.array;

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

public class ArrayIndexTest extends ProjectTestCase {
	
	private static final String JSONA = 
"""
[
  null,
  0, 	// byte
  0, 	// short
  0,	// int
  0,	// long
  0,	// float
  0,	// double
  0,	// BigInteger
  0,	// BigDecimal
  true,	// boolean
  "abc",// String
  {"a":1},	// Object
  [1],	// Array
  "2020-02-20",
  "13:44:18",
  "2020-02-20T13:44:18",
  "13:44:18+02:00",
  "2020-02-20T13:44:18+02:00",
  "2020-02-20T13:44:18-05:00[US/Eastern]",
]
""";

	static JsonArray jsona;
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		JsonArray a = JsonArray.parse(JSONA);
		a.setNull(0);
		a.set(1, (byte)32);
		a.set(2, (short)78);
		a.set(3, (int)56);
		a.set(4, (long)98L);
		a.set(5, (float)12.5f);
		a.set(6, (double)34.34);
		a.set(7, new BigInteger("82"));
		a.set(8, new BigDecimal("36.53"));
		assertNull(a.get(0));
		assertTrue(a.get(1) instanceof Byte);
		assertTrue(a.get(2) instanceof Short);
		assertTrue(a.get(3) instanceof Integer);
		assertTrue(a.get(4) instanceof Long);
		assertTrue(a.get(5) instanceof Float);
		assertTrue(a.get(6) instanceof Double);
		assertTrue(a.get(7) instanceof BigInteger);
		assertTrue(a.get(8) instanceof BigDecimal);
		assertTrue(a.get(9) instanceof Boolean);
		assertTrue(a.get(10) instanceof String);
		assertTrue(a.get(11) instanceof JsonObject);
		assertTrue(a.get(12) instanceof JsonArray);
		jsona = a;
	}
	
	public void testIs() throws Exception {
		for(int i=0; i<jsona.size(); i++) {
			assertEquals( i==0 ? true : false, jsona.isNull(i));
			assertEquals( (i>=1 && i<=8) ? true : false, jsona.isNumber(i));
			assertEquals( i==9 ? true : false, jsona.isBoolean(i));
			assertEquals( i==10 || (i>=13 && i<=18) ? true : false, jsona.isString(i));
			assertEquals( i==11 || i==12? true : false, jsona.isContainer(i));
			assertEquals( i==11 ? true : false, jsona.isObject(i));
			assertEquals( i==12 ? true : false, jsona.isArray(i));
		}
	}

	public void testFirst() throws Exception {
		JsonArray a1 = JsonArray.create();
		assertThrows( JsonException.class, () -> a1.firstValue() );
		assertEquals( 123, ((Number)a1.firstValueOrDefault(123)).intValue() );
		JsonArray a2 = JsonArray.of(11);
		assertEquals( 11, ((Number)a2.firstValue()).intValue() );
		assertEquals( 11, ((Number)a2.firstValueOrDefault(123)).intValue() );
		JsonArray a3 = JsonArray.of(11,22);
		assertEquals( 11, ((Number)a3.firstValue()).intValue() );
		assertEquals( 11, ((Number)a3.firstValueOrDefault(123)).intValue() );
	}
	
	public void testNullA() throws Exception {
		assertThrows(JsonException.class, () -> jsona.getNumber(0));
		assertThrows(JsonException.class, () -> jsona.getByte(0));
		assertThrows(JsonException.class, () -> jsona.getShort(0));
		assertThrows(JsonException.class, () -> jsona.getInt(0));
		assertThrows(JsonException.class, () -> jsona.getLong(0));
		assertThrows(JsonException.class, () -> jsona.getFloat(0));
		assertThrows(JsonException.class, () -> jsona.getDouble(0));
		assertThrows(JsonException.class, () -> jsona.getBigInteger(0));
		assertThrows(JsonException.class, () -> jsona.getBigDecimal(0));
		assertThrows(JsonException.class, () -> jsona.getBoolean(0));
		assertThrows(JsonException.class, () -> jsona.getString(0));

		assertEquals(null,((Integer)jsona.getOrDefault(0,10)));
		assertEquals(10,jsona.getNumber(0,10));
		assertEquals((byte)10,jsona.getByte(0,(byte)10));
		assertEquals((short)10,jsona.getShort(0,(short)10));
		assertEquals(10,jsona.getInt(0,10));
		assertEquals(10L,jsona.getLong(0,10L));
		assertEquals(10.10f,jsona.getFloat(0,10.10f));
		assertEquals(10.10,jsona.getDouble(0,10.10));
		assertEquals(BigInteger.TEN,jsona.getBigInteger(0,BigInteger.TEN));
		assertEquals(BigDecimal.TEN,jsona.getBigDecimal(0,BigDecimal.TEN));
		assertEquals(true,jsona.getBoolean(0,true));
		assertEquals("xy",jsona.getString(0,"xy"));
	}

	public void testByteA() {
		assertEquals((byte)32,jsona.getByte(1));
		assertEquals((byte)32,jsona.getByte(1,(byte)10));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getByte(999));
		assertEquals((byte)89,jsona.getByte(999,(byte)89));

		assertEquals((byte)32,jsona.getNumber(1));
		assertEquals((short)32,jsona.getShort(1));
		assertEquals(32,jsona.getInt(1));
		assertEquals(32L,jsona.getLong(1));
		assertEquals(32.0f,jsona.getFloat(1));
		assertEquals(32.0,jsona.getDouble(1));
		assertEquals(new BigInteger("32"),jsona.getBigInteger(1));
		assertEquals(new BigDecimal("32"),jsona.getBigDecimal(1));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(1);});
		assertThrows( JsonException.class, () -> {jsona.getString(1);});
		assertThrows( JsonException.class, () -> {jsona.getObject(1);});
		assertThrows( JsonException.class, () -> {jsona.getArray(1);});

		assertEquals((byte)32,((Byte)jsona.getOrDefault(1,(byte)10)).byteValue());
		assertEquals((byte)32,jsona.getNumber(1,10));
		assertEquals((short)32,jsona.getShort(1,(short)10));
		assertEquals(32,jsona.getInt(1,10));
		assertEquals(32L,jsona.getLong(1,10L));
		assertEquals(32.0f,jsona.getFloat(1,10.10f));
		assertEquals(32.0,jsona.getDouble(1,10.10));
		assertEquals(new BigInteger("32"),jsona.getBigInteger(1,BigInteger.TEN));
		assertEquals(new BigDecimal("32"),jsona.getBigDecimal(1,BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(1,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(1,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(1,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(1,JsonArray.create());});
	}

	public void testShortA() {
		assertEquals((short)78,jsona.getShort(2));
		assertEquals((short)78,jsona.getShort(2,(short)10));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getShort(999));
		assertEquals((short)89,jsona.getShort(999,(short)89));

		assertEquals((short)78,jsona.getNumber(2));
		assertEquals((byte)78,jsona.getByte(2));
		assertEquals(78,jsona.getInt(2));
		assertEquals(78L,jsona.getLong(2));
		assertEquals(78.0f,jsona.getFloat(2));
		assertEquals(78.0,jsona.getDouble(2));
		assertEquals(new BigInteger("78"),jsona.getBigInteger(2));
		assertEquals(new BigDecimal("78"),jsona.getBigDecimal(2));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(2);});
		assertThrows( JsonException.class, () -> {jsona.getString(2);});
		assertThrows( JsonException.class, () -> {jsona.getObject(2);});
		assertThrows( JsonException.class, () -> {jsona.getArray(2);});

		assertEquals((short)78,jsona.getNumber(2,10));
		assertEquals((byte)78,jsona.getByte(2,(byte)10));
		assertEquals(78,jsona.getInt(2,10));
		assertEquals(78L,jsona.getLong(2,10L));
		assertEquals(78.0f,jsona.getFloat(2,10.10f));
		assertEquals(78.0,jsona.getDouble(2,10.10));
		assertEquals(new BigInteger("78"),jsona.getBigInteger(2,BigInteger.TEN));
		assertEquals(new BigDecimal("78"),jsona.getBigDecimal(2,BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(2,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(2,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(2,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(2,JsonArray.create());});
	}

	public void testIntA() {
		assertEquals(56,jsona.getInt(3));
		assertEquals(56,jsona.getInt(3,10));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getInt(999));
		assertEquals(89,jsona.getInt(999,89));

		assertEquals(56,jsona.getNumber(3));
		assertEquals((byte)56,jsona.getByte(3));
		assertEquals((short)56,jsona.getShort(3));
		assertEquals(56L,jsona.getLong(3));
		assertEquals(56.0f,jsona.getFloat(3));
		assertEquals(56.0,jsona.getDouble(3));
		assertEquals(new BigInteger("56"),jsona.getBigInteger(3));
		assertEquals(new BigDecimal("56"),jsona.getBigDecimal(3));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(3);});
		assertThrows( JsonException.class, () -> {jsona.getString(3);});
		assertThrows( JsonException.class, () -> {jsona.getObject(3);});
		assertThrows( JsonException.class, () -> {jsona.getArray(3);});

		assertEquals(56,jsona.getNumber(3,10));
		assertEquals((byte)56,jsona.getByte(3,(byte)10));
		assertEquals((short)56,jsona.getShort(3,(short)10));
		assertEquals(56L,jsona.getLong(3,10L));
		assertEquals(56.0f,jsona.getFloat(3,10.10f));
		assertEquals(56.0,jsona.getDouble(3,10.10));
		assertEquals(new BigInteger("56"),jsona.getBigInteger(3,BigInteger.TEN));
		assertEquals(new BigDecimal("56"),jsona.getBigDecimal(3,BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(3,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(3,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(3,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(3,JsonArray.create());});
	}

	public void testLongA() {
		assertEquals(98L,jsona.getLong(4));
		assertEquals(98L,jsona.getLong(4,10L));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getLong(999));
		assertEquals(34L,jsona.getLong(999,34L));

		assertEquals(98L,jsona.getNumber(4));
		assertEquals((byte)98,jsona.getByte(4));
		assertEquals((short)98L,jsona.getShort(4));
		assertEquals(98L,jsona.getInt(4));
		assertEquals(98f,jsona.getFloat(4));
		assertEquals(98.0,jsona.getDouble(4));
		assertEquals(new BigInteger("98"),jsona.getBigInteger(4));
		assertEquals(new BigDecimal("98"),jsona.getBigDecimal(4));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(4);});
		assertThrows( JsonException.class, () -> {jsona.getString(4);});
		assertThrows( JsonException.class, () -> {jsona.getObject(4);});
		assertThrows( JsonException.class, () -> {jsona.getArray(4);});

		assertEquals(98L,jsona.getNumber(4,10));
		assertEquals((byte)98,jsona.getByte(4,(byte)10));
		assertEquals((short)98,jsona.getShort(4,(short)10));
		assertEquals(98,jsona.getInt(4,10));
		assertEquals(98.0f,jsona.getFloat(4,10.10f));
		assertEquals(98.0,jsona.getDouble(4,10.10));
		assertEquals(new BigInteger("98"),jsona.getBigInteger(4,BigInteger.TEN));
		assertEquals(new BigDecimal("98"),jsona.getBigDecimal(4,BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(4,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(4,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(4,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(4,JsonArray.create());});
	}

	public void testFloatA() {
		assertEquals(12.5f,jsona.getFloat(5));
		assertEquals(12.5f,jsona.getFloat(5,10.10f));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getFloat(999));
		assertEquals(79.0f,jsona.getFloat(999,79.0f));
		
		assertEquals(12.5f,jsona.getNumber(5));
		assertEquals((byte)12,jsona.getByte(5));
		assertEquals((short)12,jsona.getShort(5));
		assertEquals(12,jsona.getInt(5));
		assertEquals(12L,jsona.getLong(5));
		assertEquals(12.5,jsona.getDouble(5));
		assertEquals(new BigInteger("12"),jsona.getBigInteger(5));
		assertEquals(new BigDecimal("12.5"),jsona.getBigDecimal(5));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(5);});
		assertThrows( JsonException.class, () -> {jsona.getString(5);});
		assertThrows( JsonException.class, () -> {jsona.getObject(5);});
		assertThrows( JsonException.class, () -> {jsona.getArray(5);});

		assertEquals(12.5f,jsona.getNumber(5,10));
		assertEquals((byte)12,jsona.getByte(5,(byte)10));
		assertEquals((short)12,jsona.getShort(5,(short)10));
		assertEquals(12,jsona.getInt(5,10));
		assertEquals(12L,jsona.getLong(5,10L));
		assertEquals(12.5,jsona.getDouble(5,10));
		assertEquals(new BigInteger("12"),jsona.getBigInteger(5,BigInteger.TEN));
		assertEquals(new BigDecimal("12.5"),jsona.getBigDecimal(5,BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(5,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(5,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(5,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(5,JsonArray.create());});
	}

	public void testDoubleA() {
		assertEquals(34.34,jsona.getDouble(6));
		assertEquals(34.34,jsona.getDouble(6,10.10));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getDouble(999));
		assertEquals(79.0,jsona.getDouble(999,79.0));
		
		assertEquals(34.34,jsona.getNumber(6));
		assertEquals((byte)34,jsona.getByte(6));
		assertEquals((short)34,jsona.getShort(6));
		assertEquals(34,jsona.getInt(6));
		assertEquals(34L,jsona.getLong(6));
		assertEquals(34.34f,jsona.getFloat(6));
		assertEquals(new BigInteger("34"),jsona.getBigInteger(6));
		assertEquals(new BigDecimal("34.34"),jsona.getBigDecimal(6));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(6);});
		assertThrows( JsonException.class, () -> {jsona.getString(6);});
		assertThrows( JsonException.class, () -> {jsona.getObject(6);});
		assertThrows( JsonException.class, () -> {jsona.getArray(6);});

		assertEquals(34.34,jsona.getNumber(6,10));
		assertEquals((byte)34,jsona.getByte(6,(byte)10));
		assertEquals((short)34,jsona.getShort(6,(short)10));
		assertEquals(34,jsona.getInt(6,10));
		assertEquals(34L,jsona.getLong(6,10L));
		assertEquals(34.34f,jsona.getFloat(6,10L));
		assertEquals(new BigInteger("34"),jsona.getBigInteger(6,BigInteger.TEN));
		assertEquals(new BigDecimal("34.34"),jsona.getBigDecimal(6,BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(6,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(6,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(6,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(6,JsonArray.create());});
	}

	public void testBigIntegerA() {
		assertEquals(new BigInteger("82"),jsona.getBigInteger(7));
		assertEquals(new BigInteger("82"),jsona.getBigInteger(7,BigInteger.TEN));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getBigInteger(999));
		assertEquals(BigInteger.TEN,jsona.getBigInteger(999,BigInteger.TEN));

		assertEquals(new BigInteger("82"),jsona.getNumber(7));
		assertEquals((byte)82,jsona.getByte(7));
		assertEquals((short)82,jsona.getShort(7));
		assertEquals(82,jsona.getInt(7));
		assertEquals(82L,jsona.getLong(7));
		assertEquals(82.0f,jsona.getFloat(7));
		assertEquals(82.0,jsona.getDouble(7));
		assertEquals(new BigDecimal("82"),jsona.getBigDecimal(7));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(7);});
		assertThrows( JsonException.class, () -> {jsona.getString(7);});
		assertThrows( JsonException.class, () -> {jsona.getObject(7);});
		assertThrows( JsonException.class, () -> {jsona.getArray(7);});

		assertEquals(new BigInteger("82"),jsona.getNumber(7,10));
		assertEquals((byte)82,jsona.getByte(7,(byte)10));
		assertEquals((short)82,jsona.getShort(7,(short)10));
		assertEquals(82,jsona.getInt(7,10));
		assertEquals(82L,jsona.getLong(7,10L));
		assertEquals(82.0f,jsona.getFloat(7,10.10f));
		assertEquals(82.0,jsona.getDouble(7,10.10));
		assertEquals(new BigDecimal("82"),jsona.getBigDecimal(7,BigDecimal.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(7,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(7,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(7,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(7,JsonArray.create());});
	}

	public void testBigDecimalA() {
		assertEquals(new BigDecimal("36.53"),jsona.getBigDecimal(8));
		assertEquals(new BigDecimal("36.53"),jsona.getBigDecimal(8,BigDecimal.TEN));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getBigDecimal(999));
		assertEquals(BigDecimal.TEN,jsona.getBigDecimal(999,BigDecimal.TEN));
		
		assertEquals(new BigDecimal("36.53"),jsona.getNumber(8));
		assertEquals((byte)36,jsona.getByte(8));
		assertEquals((short)36,jsona.getShort(8));
		assertEquals(36,jsona.getInt(8));
		assertEquals(36L,jsona.getLong(8));
		assertEquals(36.53f,jsona.getFloat(8));
		assertEquals(36.53,jsona.getDouble(8));
		assertEquals(new BigInteger("36"),jsona.getBigInteger(8));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(8);});
		assertThrows( JsonException.class, () -> {jsona.getString(8);});
		assertThrows( JsonException.class, () -> {jsona.getObject(8);});
		assertThrows( JsonException.class, () -> {jsona.getArray(8);});

		assertEquals(new BigDecimal("36.53"),jsona.getNumber(8,10));
		assertEquals((byte)36,jsona.getByte(8,(byte)10));
		assertEquals((short)36,jsona.getShort(8,(short)10));
		assertEquals(36,jsona.getInt(8,10));
		assertEquals(36,jsona.getLong(8,10L));
		assertEquals(36.53f,jsona.getFloat(8,10.10f));
		assertEquals(36.53,jsona.getDouble(8,10.10));
		assertEquals(new BigInteger("36"),jsona.getBigInteger(8,BigInteger.TEN));
		assertThrows( JsonException.class, () -> {jsona.getBoolean(8,true);});
		assertThrows( JsonException.class, () -> {jsona.getString(8,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(8,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(8,JsonArray.create());});
	}
	
	public void testBooleanA() {
		assertEquals(true,jsona.getBoolean(9));
		assertEquals(true,jsona.getBoolean(9,false));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getBoolean(999));
		assertEquals(true,jsona.getBoolean(999,true));

		assertThrows( JsonException.class, () -> {jsona.getNumber(9);});
		assertThrows( JsonException.class, () -> {jsona.getByte(9);});
		assertThrows( JsonException.class, () -> {jsona.getShort(9);});
		assertThrows( JsonException.class, () -> {jsona.getInt(9);});
		assertThrows( JsonException.class, () -> {jsona.getLong(9);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(9);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(9);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(9);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(9);});
		assertThrows( JsonException.class, () -> {jsona.getString(9);});
		assertThrows( JsonException.class, () -> {jsona.getObject(9);});
		assertThrows( JsonException.class, () -> {jsona.getArray(9);});

		assertThrows( JsonException.class, () -> {jsona.getNumber(9,10);});
		assertThrows( JsonException.class, () -> {jsona.getByte(9,(byte)10);});
		assertThrows( JsonException.class, () -> {jsona.getShort(9,(short)10);});
		assertThrows( JsonException.class, () -> {jsona.getInt(9,10);});
		assertThrows( JsonException.class, () -> {jsona.getLong(9,10L);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(9,10.10f);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(9,10.10);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(9,BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(9,BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getString(9,"xy");});
		assertThrows( JsonException.class, () -> {jsona.getObject(9,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(9,JsonArray.create());});
	}

	public void testStringA() {
		assertEquals("abc",jsona.getString(10));
		assertEquals("abc",jsona.getString(10,"xy"));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getString(999));
		assertEquals("xyz",jsona.getString(999,"xyz"));

		assertThrows( JsonException.class, () -> {jsona.getNumber(10);});
		assertThrows( JsonException.class, () -> {jsona.getByte(10);});
		assertThrows( JsonException.class, () -> {jsona.getShort(10);});
		assertThrows( JsonException.class, () -> {jsona.getInt(10);});
		assertThrows( JsonException.class, () -> {jsona.getLong(10);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(10);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(10);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(10);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(10);});
		assertThrows( JsonException.class, () -> {jsona.getBoolean(10);});
		assertThrows( JsonException.class, () -> {jsona.getObject(10);});
		assertThrows( JsonException.class, () -> {jsona.getArray(10);});

		assertThrows( JsonException.class, () -> {jsona.getNumber(10,10);});
		assertThrows( JsonException.class, () -> {jsona.getByte(10,(byte)10);});
		assertThrows( JsonException.class, () -> {jsona.getShort(10,(short)10);});
		assertThrows( JsonException.class, () -> {jsona.getInt(10,10);});
		assertThrows( JsonException.class, () -> {jsona.getLong(10,10L);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(10,10.10f);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(10,10.10);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(10,BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(10,BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getBoolean(10,true);});
		assertThrows( JsonException.class, () -> {jsona.getObject(10,JsonObject.create());});
		assertThrows( JsonException.class, () -> {jsona.getArray(11,JsonArray.create());});
	}

	public void testObjectA() {
		assertEquals(JsonObject.create().put("a", 1),jsona.getObject(11));
		assertEquals(JsonObject.create().put("a", 1),jsona.getObject(11,JsonObject.create()));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getObject(999));
		assertEquals(JsonObject.create(),jsona.getObject(999,JsonObject.create()));

		assertThrows( JsonException.class, () -> {jsona.getNumber(11);});
		assertThrows( JsonException.class, () -> {jsona.getByte(11);});
		assertThrows( JsonException.class, () -> {jsona.getShort(11);});
		assertThrows( JsonException.class, () -> {jsona.getInt(11);});
		assertThrows( JsonException.class, () -> {jsona.getLong(11);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(11);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(11);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(11);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(11);});
		assertThrows( JsonException.class, () -> {jsona.getBoolean(11);});
		assertThrows( JsonException.class, () -> {jsona.getArray(11);});

		assertThrows( JsonException.class, () -> {jsona.getNumber(11,10);});
		assertThrows( JsonException.class, () -> {jsona.getByte(11,(byte)10);});
		assertThrows( JsonException.class, () -> {jsona.getShort(11,(short)10);});
		assertThrows( JsonException.class, () -> {jsona.getInt(11,10);});
		assertThrows( JsonException.class, () -> {jsona.getLong(11,10L);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(11,10.10f);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(11,10.10);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(11,BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(11,BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getBoolean(11,true);});
		assertThrows( JsonException.class, () -> {jsona.getArray(11);});
	}

	public void testArrayA() {
		assertEquals(JsonArray.create().add(1),jsona.getArray(12));
		assertEquals(JsonArray.create().add(1),jsona.getArray(12,JsonArray.create()));
		assertThrows(IndexOutOfBoundsException.class, () -> jsona.getArray(999));
		assertEquals(JsonArray.create(),jsona.getArray(999,JsonArray.create()));

		assertThrows( JsonException.class, () -> {jsona.getNumber(12);});
		assertThrows( JsonException.class, () -> {jsona.getByte(12);});
		assertThrows( JsonException.class, () -> {jsona.getShort(12);});
		assertThrows( JsonException.class, () -> {jsona.getInt(12);});
		assertThrows( JsonException.class, () -> {jsona.getLong(12);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(12);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(12);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(12);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(12);});
		assertThrows( JsonException.class, () -> {jsona.getBoolean(12);});
		assertThrows( JsonException.class, () -> {jsona.getObject(12);});

		assertThrows( JsonException.class, () -> {jsona.getNumber(12,10);});
		assertThrows( JsonException.class, () -> {jsona.getByte(12,(byte)10);});
		assertThrows( JsonException.class, () -> {jsona.getShort(12,(short)10);});
		assertThrows( JsonException.class, () -> {jsona.getInt(12,10);});
		assertThrows( JsonException.class, () -> {jsona.getLong(12,10L);});
		assertThrows( JsonException.class, () -> {jsona.getFloat(12,10.10f);});
		assertThrows( JsonException.class, () -> {jsona.getDouble(12,10.10);});
		assertThrows( JsonException.class, () -> {jsona.getBigInteger(12,BigInteger.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getBigDecimal(12,BigDecimal.TEN);});
		assertThrows( JsonException.class, () -> {jsona.getBoolean(12,true);});
		assertThrows( JsonException.class, () -> {jsona.getObject(12,JsonObject.create());});
	}
	

	public void testLocalDate() {
		assertEquals(LocalDate.of(2020, 2, 20), jsona.getLocalDate(13));
		assertEquals(LocalDate.of(2020, 2, 20), jsona.getLocalDate(13, LocalDate.of(2030, 3, 30)));
		assertEquals(LocalDate.of(2030, 3, 30), jsona.getLocalDate(999, LocalDate.of(2030, 3, 30)));
	}

	public void testLocalTime() {
		assertEquals(LocalTime.of(13, 44, 18), jsona.getLocalTime(14));
		assertEquals(LocalTime.of(13, 44, 18), jsona.getLocalTime(14, LocalTime.of(14, 45, 19)));
		assertEquals(LocalTime.of(14, 45, 19), jsona.getLocalTime(999, LocalTime.of(14, 45, 19)));
	}

	public void testLocalDateTime() {
		assertEquals(LocalDateTime.of(2020, 2, 20, 13, 44, 18), jsona.getLocalDateTime(15));
		assertEquals(LocalDateTime.of(2020, 2, 20, 13, 44, 18), jsona.getLocalDateTime(15,LocalDateTime.of(2030, 3, 30, 14, 45, 19)));
		assertEquals(LocalDateTime.of(2030, 3, 30, 14, 45, 19), jsona.getLocalDateTime(999,LocalDateTime.of(2030, 3, 30, 14, 45, 19)));
	}
	
	public void testOffsetTime() {
		assertEquals(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2)), jsona.getOffsetTime(16));
		assertEquals(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2)), jsona.getOffsetTime(16,OffsetTime.of(14, 45, 19, 0, ZoneOffset.ofHours(2))));
		assertEquals(OffsetTime.of(14, 45, 19, 0, ZoneOffset.ofHours(2)), jsona.getOffsetTime(999,OffsetTime.of(14, 45, 19, 0, ZoneOffset.ofHours(2))));
	}
	
	public void testOffsetDateTime() {
		assertEquals(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2)), jsona.getOffsetDateTime(17));
		assertEquals(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2)), jsona.getOffsetDateTime(17,OffsetDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneOffset.ofHours(2))));
		assertEquals(OffsetDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneOffset.ofHours(2)), jsona.getOffsetDateTime(999,OffsetDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneOffset.ofHours(2))));
	}
	
	public void testZonedDateTime() {
		assertEquals(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern")), jsona.getZonedDateTime(18));
		assertEquals(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern")), jsona.getZonedDateTime(18,ZonedDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneId.of("US/Eastern"))));
		assertEquals(ZonedDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneId.of("US/Eastern")), jsona.getZonedDateTime(999,ZonedDateTime.of(2030, 3, 30, 14, 45, 19, 0, ZoneId.of("US/Eastern"))));
	}

}
