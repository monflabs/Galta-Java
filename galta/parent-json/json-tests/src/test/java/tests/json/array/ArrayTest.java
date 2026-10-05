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
import java.util.Collection;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class ArrayTest extends ProjectTestCase {

	public void testType() {
		JsonArray o = JsonFactory.get().createArray();
		assertFalse(o.isObject());
		assertTrue(o.isArray());
	}


	public void testGet() {
		JsonArray a = JsonFactory.get().arrayOf("AA","BB","CC");
		assertThrows(IndexOutOfBoundsException.class, () -> a.get(8));
		assertEquals("AA",a.get(0));
		// java.util.List.get(): no negative index (List contract)...
		assertThrows(IndexOutOfBoundsException.class, () -> a.get(-3));
		// ...nor the JSON accessors...
		assertThrows(IndexOutOfBoundsException.class, () -> a.getString(-3));
		assertEquals("x",a.getOrDefault(-1,"x"));
		// ...but the at*() methods count a negative index from the end
		assertEquals("AA",a.at(-3));
		assertEquals("AA",a.atString(-3));
		assertEquals("CC",a.atOrDefault(-1,"x"));
		assertEquals("x",a.atOrDefault(-4,"x"));
		assertEquals("CC",a.jsonValuesAt(-1).stringValue());
	}

	public void testGrowTo() {
		JsonArray a = JsonFactory.get().createArray();
		assertEquals(0,a.size());
		a.growTo(2);
		assertEquals(null,a.get(0));
		assertEquals(null,a.get(1));
		assertEquals(2,a.size());
		a.growTo(1);
		assertEquals(2,a.size());
	}

	public void testContains() {
		JsonArray a = JsonFactory.get().arrayOf(1,true,"abc", JsonFactory.get().of("a","A"), JsonFactory.get().arrayOf(3,4));

		assertTrue(a.contains(1));
		assertTrue(a.contains(true));
		assertTrue(a.contains("abc"));
		assertTrue(a.contains(JsonFactory.get().of("a","A")));
		assertTrue(a.contains(JsonFactory.get().arrayOf(3,4)));
		assertFalse(a.contains(2));

		Collection<Object> values = a.values();
		assertEquals(5, values.size());
		assertTrue(values.contains(1));
		assertTrue(values.contains(true));
		assertTrue(values.contains("abc"));
		assertTrue(values.contains(JsonFactory.get().of("a","A")));
		assertTrue(values.contains(JsonFactory.get().arrayOf(3,4)));
		assertFalse(values.contains(2));
		
		int i=0;
		for(Object o: values) {
			switch(i++) {
				case 0 -> assertEquals(1,o);
				case 1 -> assertEquals(true,o);
				case 2 -> assertEquals("abc",o);
				case 3 -> assertEquals(JsonFactory.get().of("a","A"),o);
				case 4 -> assertEquals(JsonFactory.get().arrayOf(3,4),o);
			}
		}
		assertEquals(5, i);
	}
	
	public void testAddAndSet() {
		JsonArray a = JsonFactory.get().createArray();
		assertTrue(a.isArray());
		assertEquals(0,a.size());
		assertFalse(a.has(0));

		assertFalse(a.hasAt(-1));

		a.add( true);
		a.add( (byte)123);
		a.add( (short)123);
		a.add( 123);
		a.add( 123L);
		a.add( 123.45f);
		a.add( 12.345);
		a.add( Boolean.TRUE );
		a.add( Byte.valueOf((byte)123));
		a.add( Short.valueOf((short)123));
		a.add( Integer.valueOf(123));
		a.add( Long.valueOf(123L));
		a.add( Float.valueOf(123.45f));
		a.add( Double.valueOf(12.345));
		a.add( new BigInteger("789"));
		a.add( new BigDecimal("56.789"));
		a.add( "xyz");
		a.add( LocalDate.of(2020, 2, 20));
		a.add( LocalTime.of(10, 11, 12));
		a.add( LocalDateTime.of(2020, 2, 20, 10, 11, 12));
		a.add( OffsetTime.of(10, 11, 12, 0, ZoneOffset.ofHours(2)));
		a.add( OffsetDateTime.of(2020, 2, 20, 10, 11, 12, 0, ZoneOffset.ofHours(3)));
		a.add( ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, ZoneId.of("US/Eastern")));
		a.add( JsonFactory.get().of("a","AA") );
		a.add( (JsonObject r) -> {
			r.put("b", "BB");
		});
		a.add( JsonFactory.get().arrayOf("a","b") );
		a.add( (JsonArray r) -> {
			r.add("c").add("d");
		});
		a.addNull();

		assertTrue(a.has(0));
		assertTrue(a.has(27));
		assertFalse(a.has(28));

		// The regular methods follow the List contract, the at*() methods count from the end
		assertFalse(a.has(-1));
		assertTrue(a.hasAt(-1));
		assertTrue(a.hasAt(-28));
		assertFalse(a.hasAt(-29));
		assertNull(a.at(-1));
		assertEquals(true, a.atBoolean(-28));

		assertEquals(28,a.size());
		
		assertEquals(true,a.getBoolean(0));
		assertEquals((byte)123,a.getByte(1));
		assertEquals((short)123,a.getShort(2));
		assertEquals(123,a.getInt(3));
		assertEquals(123L,a.getLong(4));
		assertEquals(123.45f,a.getFloat(5));
		assertEquals(12.345,a.getDouble(6));
		assertEquals(true,a.getBoolean(7));
		assertEquals((byte)123,a.getByte(8));
		assertEquals((short)123,a.getShort(9));
		assertEquals(123,a.getInt(10));
		assertEquals(123L,a.getLong(11));
		assertEquals(123.45f,a.getFloat(12));
		assertEquals(12.345,a.getDouble(13));
		assertEquals(new BigInteger("789"),a.getBigInteger(14));
		assertEquals(new BigDecimal("56.789"),a.getBigDecimal(15));
		assertEquals("xyz",a.getString(16));
		assertEquals(LocalDate.of(2020, 2, 20),a.getLocalDate(17));
		assertEquals(LocalTime.of(10, 11, 12),a.getLocalTime(18));
		assertEquals(LocalDateTime.of(2020, 2, 20, 10, 11, 12),a.getLocalDateTime(19));
		assertEquals(OffsetTime.of(10, 11, 12, 0, ZoneOffset.ofHours(2)),a.getOffsetTime(20));
		assertEquals(OffsetDateTime.of(2020, 2, 20, 10, 11, 12, 0, ZoneOffset.ofHours(3)),a.getOffsetDateTime(21));
		assertEquals(ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, ZoneId.of("US/Eastern")),a.getZonedDateTime(22));
		assertEquals(JsonFactory.get().of("a","AA"),a.getObject(23));
		assertEquals(JsonFactory.get().of("b","BB"),a.getObject(24));
		assertEquals(JsonFactory.get().arrayOf("a","b"),a.getArray(25));
		assertEquals(JsonFactory.get().arrayOf("c","d"),a.getArray(26));
		assertEquals(null,a.get(27));
		
		assertEquals(true,a.getBoolean(27,true));
		assertEquals((byte)85,a.getByte(27,(byte)85));
		assertEquals((short)456,a.getShort(27,(short)456));
		assertEquals(567,a.getInt(27,567));
		assertEquals(852L,a.getLong(27,852));
		assertEquals(85.45f,a.getFloat(27,85.45f));
		assertEquals(12.456,a.getDouble(27,12.456));
		assertEquals(new BigInteger("8963"),a.getBigInteger(27,new BigInteger("8963")));
		assertEquals(new BigDecimal("56.988"),a.getBigDecimal(27,new BigDecimal("56.988")));
		assertEquals("jkuy",a.getString(27,"jkuy"));
		assertEquals(LocalDate.of(2014, 5, 20),a.getLocalDate(27,LocalDate.of(2014, 5, 20)));
		assertEquals(LocalTime.of(10, 8, 12),a.getLocalTime(27,LocalTime.of(10, 8, 12)));
		assertEquals(LocalDateTime.of(2015, 2, 20, 8, 11, 12),a.getLocalDateTime(27,LocalDateTime.of(2015, 2, 20, 8, 11, 12)));
		assertEquals(OffsetTime.of(10, 9, 12, 0, ZoneOffset.ofHours(2)),a.getOffsetTime(27,OffsetTime.of(10, 9, 12, 0, ZoneOffset.ofHours(2))));
		assertEquals(OffsetDateTime.of(2010, 2, 20, 10, 11, 10, 0, ZoneOffset.ofHours(3)),a.getOffsetDateTime(27,OffsetDateTime.of(2010, 2, 20, 10, 11, 10, 0, ZoneOffset.ofHours(3))));
		assertEquals(ZonedDateTime.of(2018, 8, 14, 12, 6, 43, 0, ZoneId.of("US/Eastern")),a.getZonedDateTime(27,ZonedDateTime.of(2018, 8, 14, 12, 6, 43, 0, ZoneId.of("US/Eastern"))));
		assertEquals(JsonFactory.get().of("aa","A"),a.getObject(27,JsonFactory.get().of("aa","A")));
		assertEquals(JsonFactory.get().of("ba","B"),a.getObject(27,JsonFactory.get().of("ba","B")));
		assertEquals(JsonFactory.get().arrayOf("aa","bb"),a.getArray(27,JsonFactory.get().arrayOf("aa","bb")));
		assertEquals(JsonFactory.get().arrayOf("cc","dd"),a.getArray(27,JsonFactory.get().arrayOf("cc","dd")));

		assertTrue(a.has(0));
		assertTrue(a.has(4));
		assertFalse(a.has(99));
		
		a.addValue( a.get(7));
		assertEquals(29,a.size());
		assertEquals(12.345,a.getDouble(6));

		boolean added = a.add( (Object)"defg" );
		assertEquals("defg",a.getString(a.size()-1));
		assertEquals(true,added);
		a.remove(a.size()-1);
		
		a.setValue( 1, a.get(6));
		assertEquals(12.345,a.getDouble(6));
		assertEquals(12.345,a.getDouble(1));

		Object old = a.set( 1, (Object)456.36);
		assertEquals(12.345,old);
		assertEquals(456.36,a.getDouble(1));

		a.remove( 3);
		assertEquals(28,a.size());
		assertEquals("xyz",a.getString(15));
		
		a.add( 0, "kok");
		assertEquals(29,a.size());
		assertEquals("kok",a.getString(0));
		assertEquals(true,a.getBoolean(1));
		a.add( 5, "kik");
		assertEquals(30,a.size());
		assertEquals("kik",a.getString(5));
		
		a.clear();
		assertEquals(0,a.size());
		
		a.addNull(0);
		a.add( 0, true);
		a.add( 0, (byte)123);
		a.add( 0, (short)123);
		a.add( 0, 123);
		a.add( 0, 123L);
		a.add( 0, 123.45f);
		a.add( 0, 12.345);
		a.add( 0, Boolean.valueOf(true));
		a.add( 0, Byte.valueOf((byte)123));
		a.add( 0, Short.valueOf((short)123));
		a.add( 0, Integer.valueOf(123));
		a.add( 0, Long.valueOf(123L));
		a.add( 0, Float.valueOf(123.45f));
		a.add( 0, Double.valueOf(12.345));
		a.add( 0, new BigInteger("789"));
		a.add( 0, new BigDecimal("56.789"));
		a.add( 0, "xyz");
		a.add( 0, LocalDate.of(2020, 2, 20));
		a.add( 0, LocalTime.of(10, 11, 12));
		a.add( 0, LocalDateTime.of(2020, 2, 20, 10, 11, 12));
		a.add( 0, OffsetTime.of(10, 11, 12, 0, ZoneOffset.ofHours(2)));
		a.add( 0, OffsetDateTime.of(2020, 2, 20, 10, 11, 12, 0, ZoneOffset.ofHours(3)));
		a.add( 0, ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, ZoneId.of("US/Eastern")));
		a.add( 0, JsonFactory.get().of("a","AA") );
		a.add( 0, (JsonObject r) -> {
			r.put("b", "BB");
		});
		a.add( 0, JsonFactory.get().arrayOf("a","b") );
		a.add( 0, (JsonArray r) -> {
			r.add("c").add("d");
		});
		assertEquals(null,a.get(27));
		assertEquals(true,a.getBoolean(26));
		assertEquals((byte)123,a.getByte(25));
		assertEquals((short)123,a.getShort(24));
		assertEquals(123,a.getInt(23));
		assertEquals(123L,a.getLong(22));
		assertEquals(123.45f,a.getFloat(21));
		assertEquals(12.345,a.getDouble(20));
		assertEquals(true,a.getBoolean(19));
		assertEquals((byte)123,a.getByte(18));
		assertEquals((short)123,a.getShort(17));
		assertEquals(123,a.getInt(16));
		assertEquals(123L,a.getLong(15));
		assertEquals(123.45f,a.getFloat(14));
		assertEquals(12.345,a.getDouble(13));
		assertEquals(new BigInteger("789"),a.getBigInteger(12));
		assertEquals(new BigDecimal("56.789"),a.getBigDecimal(11));
		assertEquals("xyz",a.getString(10));
		assertEquals(LocalDate.of(2020, 2, 20),a.getLocalDate(9));
		assertEquals(LocalTime.of(10, 11, 12),a.getLocalTime(8));
		assertEquals(LocalDateTime.of(2020, 2, 20, 10, 11, 12),a.getLocalDateTime(7));
		assertEquals(OffsetTime.of(10, 11, 12, 0, ZoneOffset.ofHours(2)),a.getOffsetTime(6));
		assertEquals(OffsetDateTime.of(2020, 2, 20, 10, 11, 12, 0, ZoneOffset.ofHours(3)),a.getOffsetDateTime(5));
		assertEquals(ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, ZoneId.of("US/Eastern")),a.getZonedDateTime(4));
		assertEquals(JsonFactory.get().of("a","AA"),a.getObject(3));
		assertEquals(JsonFactory.get().of("b","BB"),a.getObject(2));
		assertEquals(JsonFactory.get().arrayOf("a","b"),a.getArray(1));
		assertEquals(JsonFactory.get().arrayOf("c","d"),a.getArray(0));

		a.clear();
		for(int i=0; i<28; i++) {
			a.add("a string");
		}
		a.set( 0, true);
		a.set( 1, (byte)123);
		a.set( 2, (short)123);
		a.set( 3, 123);
		a.set( 4, 123L);
		a.set( 5, 123.45f);
		a.set( 6, 12.345);
		a.set( 7, Boolean.valueOf(true));
		a.set( 8, Byte.valueOf((byte)123));
		a.set( 9, Short.valueOf((short)123));
		a.set( 10, Integer.valueOf(123));
		a.set( 11, Long.valueOf(123L));
		a.set( 12, Float.valueOf(123.45f));
		a.set( 13, Double.valueOf(12.345));
		a.set( 14, new BigInteger("789"));
		a.set( 15, new BigDecimal("56.789"));
		a.set( 16, "xyz");
		a.set( 17, LocalDate.of(2020, 2, 20));
		a.set( 18, LocalTime.of(10, 11, 12));
		a.set( 19, LocalDateTime.of(2020, 2, 20, 10, 11, 12));
		a.set( 20, OffsetTime.of(10, 11, 12, 0, ZoneOffset.ofHours(2)));
		a.set( 21, OffsetDateTime.of(2020, 2, 20, 10, 11, 12, 0, ZoneOffset.ofHours(3)));
		a.set( 22, ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, ZoneId.of("US/Eastern")));
		a.set( 23, JsonFactory.get().of("a","AA") );
		a.set( 24, (JsonObject r) -> {
			r.put("b", "BB");
		});
		a.set( 25, JsonFactory.get().arrayOf("a","b") );
		a.set( 26, (JsonArray r) -> {
			r.add("c").add("d");
		});
		a.setNull(27);
		assertEquals(true,a.getBoolean(0));
		assertEquals((byte)123,a.getByte(1));
		assertEquals((short)123,a.getShort(2));
		assertEquals(123,a.getInt(3));
		assertEquals(123L,a.getLong(4));
		assertEquals(123.45f,a.getFloat(5));
		assertEquals(12.345,a.getDouble(6));
		assertEquals(true,a.getBoolean(7));
		assertEquals((byte)123,a.getByte(8));
		assertEquals((short)123,a.getShort(9));
		assertEquals(123,a.getInt(10));
		assertEquals(123L,a.getLong(11));
		assertEquals(123.45f,a.getFloat(12));
		assertEquals(12.345,a.getDouble(13));
		assertEquals(new BigInteger("789"),a.getBigInteger(14));
		assertEquals(new BigDecimal("56.789"),a.getBigDecimal(15));
		assertEquals("xyz",a.getString(16));
		assertEquals(LocalDate.of(2020, 2, 20),a.getLocalDate(17));
		assertEquals(LocalTime.of(10, 11, 12),a.getLocalTime(18));
		assertEquals(LocalDateTime.of(2020, 2, 20, 10, 11, 12),a.getLocalDateTime(19));
		assertEquals(OffsetTime.of(10, 11, 12, 0, ZoneOffset.ofHours(2)),a.getOffsetTime(20));
		assertEquals(OffsetDateTime.of(2020, 2, 20, 10, 11, 12, 0, ZoneOffset.ofHours(3)),a.getOffsetDateTime(21));
		assertEquals(ZonedDateTime.of(2022, 8, 14, 14, 6, 43, 0, ZoneId.of("US/Eastern")),a.getZonedDateTime(22));
		assertEquals(JsonFactory.get().of("a","AA"),a.getObject(23));
		assertEquals(JsonFactory.get().of("b","BB"),a.getObject(24));
		assertEquals(JsonFactory.get().arrayOf("a","b"),a.getArray(25));
		assertEquals(JsonFactory.get().arrayOf("c","d"),a.getArray(26));
		assertNull(a.get(27));
		assertTrue(a.has(0));
		assertTrue(a.has(4));
		assertFalse(a.has(99));
	}
	
	public void testAddAndSetNull() {
		JsonArray a = JsonFactory.get().createArray();

		a.addNull();
		a.add( (Boolean)null);
		a.add( (Byte)null);
		a.add( (Short)null);
		a.add( (Integer)null);
		a.add( (Long)null);
		a.add( (Float)null);
		a.add( (Double)null);
		a.add( (BigInteger)null);
		a.add( (BigDecimal)null);
		a.add( (String)null);
		a.add( (LocalDate)null);
		a.add( (LocalTime)null);
		a.add( (LocalDateTime)null);
		a.add( (OffsetTime)null);
		a.add( (OffsetDateTime)null);
		a.add( (ZonedDateTime)null);
		a.add( (JsonObject)null);
		a.add( (JsonArray)null);
		for(int i=0; i<a.size(); i++) {
			assertTrue(a.isNull(i));
		}
		
		a.clear();
		a.addNull(0);
		a.add( 0, (Boolean)null);
		a.add( 0, (Byte)null);
		a.add( 0, (Short)null);
		a.add( 0, (Integer)null);
		a.add( 0, (Long)null);
		a.add( 0, (Float)null);
		a.add( 0, (Double)null);
		a.add( 0, (BigInteger)null);
		a.add( 0, (BigDecimal)null);
		a.add( 0, (String)null);
		a.add( 0, (LocalDate)null);
		a.add( 0, (LocalTime)null);
		a.add( 0, (LocalDateTime)null);
		a.add( 0, (OffsetTime)null);
		a.add( 0, (OffsetDateTime)null);
		a.add( 0, (ZonedDateTime)null);
		a.add( 0, (JsonObject)null);
		a.add( 0, (JsonArray)null);
		for(int i=0; i<a.size(); i++) {
			assertTrue(a.isNull(i));
		}
		
		a.setNull(0);
		a.set( 0, (Boolean)null);
		a.set( 0, (Byte)null);
		a.set( 0, (Short)null);
		a.set( 0, (Integer)null);
		a.set( 0, (Long)null);
		a.set( 0, (Float)null);
		a.set( 0, (Double)null);
		a.set( 0, (BigInteger)null);
		a.set( 0, (BigDecimal)null);
		a.set( 0, (String)null);
		a.set( 0, (LocalDate)null);
		a.set( 0, (LocalTime)null);
		a.set( 0, (LocalDateTime)null);
		a.set( 0, (OffsetTime)null);
		a.set( 0, (OffsetDateTime)null);
		a.set( 0, (ZonedDateTime)null);
		a.set( 0, (JsonObject)null);
		a.set( 0, (JsonArray)null);
		for(int i=0; i<a.size(); i++) {
			assertTrue(a.isNull(i));
		}
	}
	
	public void testArrayNeg() {
		JsonArray a = JsonFactory.get().createArray();
		a.add(JsonArray.create());
		a.add(JsonObject.create());
		a.add(true);
		a.add("str");
		a.add(1);
		assertEquals(5,a.size());

		assertTrue(a.isNumberAt(-1));
		assertTrue(a.isStringAt(-2));
		assertTrue(a.isBooleanAt(-3));
		assertTrue(a.isObjectAt(-4));
		assertTrue(a.isArrayAt(-5));

		assertFalse(a.isNumberAt(-2));
		assertFalse(a.isStringAt(-1));
		assertFalse(a.isBooleanAt(-1));
		assertFalse(a.isObjectAt(-1));
		assertFalse(a.isArrayAt(-1));

		assertTrue(a.hasAt(-1));
		assertTrue(a.hasAt(-3));
		assertFalse(a.hasAt(-6));
		
		assertEquals(1,a.atInt(-1));
		assertEquals("str",a.atString(-2));
		assertEquals(true,a.atBoolean(-3));
		assertEquals(JsonObject.create(),a.atObject(-4));
		assertEquals(JsonArray.create(),a.atArray(-5));
		
		a.setAt(-1, 2);
		assertEquals(5,a.size());
		assertEquals(2,a.getInt(4));
		
		a.addAt(-1, 4);
		assertEquals(6,a.size());
		assertEquals(2,a.getInt(5));
		assertEquals(4,a.getInt(4));
		
		// The regular methods: no negative index (List contract)
		assertFalse(a.has(-1));
		assertFalse(a.isNumber(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> a.getInt(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, 3));
		assertThrows(IndexOutOfBoundsException.class, () -> a.add(-1, 3));
		assertThrows(IndexOutOfBoundsException.class, () -> a.remove(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, (Object)3));
		assertThrows(IndexOutOfBoundsException.class, () -> a.add(-1, (Object)3));
		a.deleteAt(-1);
		assertEquals(5,a.size());
		assertEquals(4,a.getInt(4));
	}
	
	public void testAsMethods() {
		JsonArray o = JsonFactory.get().arrayOf(
			1,
			4L,
			56.6,
			new BigInteger("45"),
			new BigDecimal("45.78"),
			true,
			"xyz"
		);
		
		assertEquals(1,o.asInt(0));
		assertEquals(4,o.asInt(1));
		assertEquals(45,o.asInt(4));
		assertEquals(0,o.asInt(6));
		assertEquals(77,o.asInt(6,77));
		
		assertEquals(1L,o.asLong(0));
		assertEquals(4L,o.asLong(1));
		assertEquals(0L,o.asLong(6));
		assertEquals(77L,o.asLong(6,77L));
		
		assertEquals(1.0,o.asDouble(0));
		assertEquals(56.6,o.asDouble(2,44.6));
		assertEquals(45.78,o.asDouble(4));
		assertEquals(1.0,o.asDouble(5));
		assertEquals(77.36,o.asDouble(6,77.36));
		
		assertEquals(new BigInteger("1"),o.asBigInteger(0));
		assertEquals(new BigInteger("4"),o.asBigInteger(1));
		assertEquals(new BigInteger("45"),o.asBigInteger(3));
		assertEquals(new BigInteger("0"),o.asBigInteger(6));
		assertEquals(new BigInteger("77"),o.asBigInteger(6,new BigInteger("77")));

		assertEquals(new BigDecimal("1"),o.asBigDecimal(0));
		assertEquals(new BigDecimal("56.6"),o.asBigDecimal(2,new BigDecimal("44.6")));
		assertEquals(new BigDecimal("45.78"),o.asBigDecimal(4));
		assertEquals(new BigDecimal("1"),o.asBigDecimal(5));
		assertEquals(new BigDecimal("77.36"),o.asBigDecimal(6,new BigDecimal("77.36")));
	}

}
