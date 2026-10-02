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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class ConvertValueTest extends ProjectTestCase {
	
	public void testEmpty() throws Exception {
		JsonValues v_empty = JsonValues.EMPTY;

		assertTrue(v_empty.isEmpty());
		assertFalse(v_empty.isValue());
		assertFalse(v_empty.isList());
		
		assertFalse(v_empty.isNull());
		assertFalse(v_empty.isNumber());
		assertFalse(v_empty.isBoolean());
		assertFalse(v_empty.isString());
		assertFalse(v_empty.isContainer());
		assertFalse(v_empty.isObject());
		assertFalse(v_empty.isArray());

		assertThrows( JsonException.class, () -> v_empty.value());
		assertThrows( JsonException.class, () -> v_empty.numberValue());
		assertThrows( JsonException.class, () -> v_empty.byteValue());
		assertThrows( JsonException.class, () -> v_empty.shortValue());
		assertThrows( JsonException.class, () -> v_empty.intValue());
		assertThrows( JsonException.class, () -> v_empty.longValue());
		assertThrows( JsonException.class, () -> v_empty.floatValue());
		assertThrows( JsonException.class, () -> v_empty.doubleValue());
		assertThrows( JsonException.class, () -> v_empty.bigIntegerValue());
		assertThrows( JsonException.class, () -> v_empty.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_empty.booleanValue());
		assertThrows( JsonException.class, () -> v_empty.stringValue());
		assertThrows( JsonException.class, () -> v_empty.objectValue());
		assertThrows( JsonException.class, () -> v_empty.arrayValue());
		assertThrows( JsonException.class, () -> v_empty.localDateValue());
		assertThrows( JsonException.class, () -> v_empty.localTimeValue());
		assertThrows( JsonException.class, () -> v_empty.localDateTimeValue());
		assertThrows( JsonException.class, () -> v_empty.offsetTimeValue());
		assertThrows( JsonException.class, () -> v_empty.offsetDateTimeValue());
		assertThrows( JsonException.class, () -> v_empty.zonedDateTimeValue());

		assertEquals(10,v_empty.numberValue(10));
		assertEquals((byte)10,v_empty.byteValue((byte)10));
		assertEquals((short)10,v_empty.shortValue((short)10));
		assertEquals(10,v_empty.intValue(10));
		assertEquals(10L,v_empty.longValue(10L));
		assertEquals(10.0f,v_empty.floatValue(10.0f));
		assertEquals(10.10,v_empty.doubleValue(10.10));
		assertEquals(BigInteger.TEN,v_empty.bigIntegerValue(BigInteger.TEN));
		assertEquals(BigDecimal.TEN,v_empty.bigDecimalValue(BigDecimal.TEN));
		assertEquals(true,v_empty.booleanValue(true));
		assertEquals("xy",v_empty.stringValue("xy"));
		support.assertJsonEquals(JsonObject.of("s","xyz"), v_empty.objectValue(JsonObject.of("s","xyz")));
		support.assertJsonEquals(JsonArray.of("d","e"), v_empty.arrayValue(JsonArray.of("d","e")));
		assertEquals(LocalDate.of(2020, 2, 20),v_empty.localDateValue(LocalDate.of(2020, 2, 20)));
		assertEquals(LocalTime.of(13, 44, 18),v_empty.localTimeValue(LocalTime.of(13, 44, 18)));
		assertEquals(LocalDateTime.of(2020, 2, 20, 13, 44, 18),v_empty.localDateTimeValue(LocalDateTime.of(2020, 2, 20, 13, 44, 18)));
		assertEquals(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2)),v_empty.offsetTimeValue(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertEquals(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2)),v_empty.offsetDateTimeValue(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertEquals(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern")),v_empty.zonedDateTimeValue(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern"))));
	}
	
	public void testNullA() throws Exception {
		JsonValues v_null = JsonValues.of(null);

		assertEquals(null,v_null.value());
		
		assertFalse(v_null.isEmpty());
		assertTrue (v_null.isValue());
		assertFalse(v_null.isList());

		assertTrue (v_null.isNull());
		assertFalse(v_null.isNumber());
		assertFalse(v_null.isBoolean());
		assertFalse(v_null.isString());
		assertFalse(v_null.isContainer());
		assertFalse(v_null.isObject());
		assertFalse(v_null.isArray());

		assertThrows( JsonException.class, () -> v_null.numberValue());
		assertThrows( JsonException.class, () -> v_null.byteValue());
		assertThrows( JsonException.class, () -> v_null.shortValue());
		assertThrows( JsonException.class, () -> v_null.intValue());
		assertThrows( JsonException.class, () -> v_null.longValue());
		assertThrows( JsonException.class, () -> v_null.floatValue());
		assertThrows( JsonException.class, () -> v_null.doubleValue());
		assertThrows( JsonException.class, () -> v_null.bigIntegerValue());
		assertThrows( JsonException.class, () -> v_null.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_null.booleanValue());
		assertThrows( JsonException.class, () -> v_null.stringValue());
		assertThrows( JsonException.class, () -> v_null.objectValue());
		assertThrows( JsonException.class, () -> v_null.arrayValue());
		assertThrows( JsonException.class, () -> v_null.localDateValue());
		assertThrows( JsonException.class, () -> v_null.localTimeValue());
		assertThrows( JsonException.class, () -> v_null.localDateTimeValue());
		assertThrows( JsonException.class, () -> v_null.offsetTimeValue());
		assertThrows( JsonException.class, () -> v_null.offsetDateTimeValue());
		assertThrows( JsonException.class, () -> v_null.zonedDateTimeValue());

		assertThrows( JsonException.class, () -> v_null.numberValue(10));
		assertThrows( JsonException.class, () -> v_null.byteValue((byte)10));
		assertThrows( JsonException.class, () -> v_null.shortValue((short)10));
		assertThrows( JsonException.class, () -> v_null.intValue(10));
		assertThrows( JsonException.class, () -> v_null.longValue(10L));
		assertThrows( JsonException.class, () -> v_null.floatValue(10.0f));
		assertThrows( JsonException.class, () -> v_null.doubleValue(10.10));
		assertThrows( JsonException.class, () -> v_null.bigIntegerValue(BigInteger.TEN));
		assertThrows( JsonException.class, () -> v_null.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_null.booleanValue(true));
		assertThrows( JsonException.class, () -> v_null.stringValue("xy"));
		assertThrows( JsonException.class, () -> v_null.objectValue(JsonObject.of("s","xyz")));
		assertThrows( JsonException.class, () -> v_null.arrayValue(JsonArray.of("d","e")));
		assertThrows( JsonException.class, () -> v_null.localDateValue(LocalDate.of(2020, 2, 20)));
		assertThrows( JsonException.class, () -> v_null.localTimeValue(LocalTime.of(13, 44, 18)));
		assertThrows( JsonException.class, () -> v_null.localDateTimeValue(LocalDateTime.of(2020, 2, 20, 13, 44, 18)));
		assertThrows( JsonException.class, () -> v_null.offsetTimeValue(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertThrows( JsonException.class, () -> v_null.offsetDateTimeValue(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertThrows( JsonException.class, () -> v_null.zonedDateTimeValue(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern"))));
	}
	
	public void testIntA() {
		JsonValues v_int = JsonValues.of(34);

		assertEquals(34,v_int.value());
		
		assertFalse(v_int.isEmpty());
		assertTrue (v_int.isValue());
		assertFalse(v_int.isList());

		assertFalse(v_int.isNull());
		assertTrue(v_int.isNumber());
		assertFalse(v_int.isBoolean());
		assertFalse(v_int.isString());
		assertFalse(v_int.isContainer());
		assertFalse(v_int.isObject());
		assertFalse(v_int.isArray());

		assertEquals(34,v_int.numberValue());
		assertEquals((byte)34,v_int.byteValue());
		assertEquals((short)34,v_int.shortValue());
		assertEquals(34,v_int.intValue());
		assertEquals(34L,v_int.longValue());
		assertEquals(34.0f,v_int.floatValue());
		assertEquals(34.0,v_int.doubleValue());
		assertEquals(new BigInteger("34"),v_int.bigIntegerValue());
		assertEquals(new BigDecimal("34"),v_int.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_int.booleanValue());
		assertThrows( JsonException.class, () -> v_int.stringValue());
		assertThrows( JsonException.class, () -> v_int.objectValue());
		assertThrows( JsonException.class, () -> v_int.arrayValue());
		assertThrows( JsonException.class, () -> v_int.localDateValue());
		assertThrows( JsonException.class, () -> v_int.localTimeValue());
		assertThrows( JsonException.class, () -> v_int.localDateTimeValue());
		assertThrows( JsonException.class, () -> v_int.offsetTimeValue());
		assertThrows( JsonException.class, () -> v_int.offsetDateTimeValue());
		assertThrows( JsonException.class, () -> v_int.zonedDateTimeValue());

		assertThrows( JsonException.class, () -> v_int.stringValue("abc"));
		assertEquals(34,v_int.numberValue(10));
		assertEquals((byte)34,v_int.byteValue((byte)10));
		assertEquals((short)34,v_int.shortValue((short)10));
		assertEquals(34,v_int.intValue(10));
		assertEquals(34L,v_int.longValue(10L));
		assertEquals(34.0f,v_int.floatValue(10.0f));
		assertEquals(34.0,v_int.doubleValue(10.10));
		assertEquals(new BigInteger("34"),v_int.bigIntegerValue(BigInteger.TEN));
		assertEquals(new BigDecimal("34"),v_int.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_int.booleanValue(true));
		assertThrows( JsonException.class, () -> v_int.stringValue("xy"));
		assertThrows( JsonException.class, () -> v_int.objectValue(JsonObject.of("s","xyz")));
		assertThrows( JsonException.class, () -> v_int.arrayValue(JsonArray.of("d","e")));
		assertThrows( JsonException.class, () -> v_int.localDateValue(LocalDate.of(2020, 2, 20)));
		assertThrows( JsonException.class, () -> v_int.localTimeValue(LocalTime.of(13, 44, 18)));
		assertThrows( JsonException.class, () -> v_int.localDateTimeValue(LocalDateTime.of(2020, 2, 20, 13, 44, 18)));
		assertThrows( JsonException.class, () -> v_int.offsetTimeValue(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertThrows( JsonException.class, () -> v_int.offsetDateTimeValue(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertThrows( JsonException.class, () -> v_int.zonedDateTimeValue(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern"))));
	}

	public void testLongA() {
		JsonValues v_long = JsonValues.of(34343434343434L);
		
		assertEquals(34343434343434L,v_long.value());
		
		assertFalse(v_long.isEmpty());
		assertTrue (v_long.isValue());
		assertFalse(v_long.isList());

		assertFalse(v_long.isNull());
		assertTrue (v_long.isNumber());
		assertFalse(v_long.isBoolean());
		assertFalse(v_long.isString());
		assertFalse(v_long.isContainer());
		assertFalse(v_long.isObject());
		assertFalse(v_long.isArray());		
		
		assertEquals(34343434343434L,v_long.numberValue());
		// Saturated, like intValue(): not the wrapped low bits (10 and 21514)
		assertEquals(Byte.MAX_VALUE,v_long.byteValue());
		assertEquals(Short.MAX_VALUE,v_long.shortValue());
		assertEquals(Integer.MAX_VALUE,v_long.intValue()); // saturated, not wrapped
		assertEquals(34343434343434L,v_long.longValue());
		assertEquals(34343434343434.0f,v_long.floatValue());
		assertEquals(34343434343434.0,v_long.doubleValue());
		assertEquals(new BigInteger("34343434343434"),v_long.bigIntegerValue());
		assertEquals(new BigDecimal("34343434343434"),v_long.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_long.booleanValue());
		assertThrows( JsonException.class, () -> v_long.stringValue());
		assertThrows( JsonException.class, () -> v_long.objectValue());
		assertThrows( JsonException.class, () -> v_long.arrayValue());
		assertThrows( JsonException.class, () -> v_long.localDateValue());
		assertThrows( JsonException.class, () -> v_long.localTimeValue());
		assertThrows( JsonException.class, () -> v_long.localDateTimeValue());
		assertThrows( JsonException.class, () -> v_long.offsetTimeValue());
		assertThrows( JsonException.class, () -> v_long.offsetDateTimeValue());
		assertThrows( JsonException.class, () -> v_long.zonedDateTimeValue());

		assertEquals(34343434343434L,v_long.numberValue(10L));
		assertEquals(Byte.MAX_VALUE,v_long.byteValue((byte)10));
		assertEquals(Short.MAX_VALUE,v_long.shortValue((short)12));
		assertEquals(Integer.MAX_VALUE,v_long.intValue(10)); // saturated, not wrapped
		assertEquals(34343434343434L,v_long.longValue(10L));
		assertEquals(34343434343434.0f,v_long.floatValue(10L));
		assertEquals(34343434343434.0,v_long.doubleValue(10.10));
		assertEquals(new BigInteger("34343434343434"),v_long.bigIntegerValue(BigInteger.TEN));
		assertEquals(new BigDecimal("34343434343434"),v_long.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_long.booleanValue(true));
		assertThrows( JsonException.class, () -> v_long.stringValue("xy"));
		assertThrows( JsonException.class, () -> v_long.objectValue(JsonObject.of("s","xyz")));
		assertThrows( JsonException.class, () -> v_long.arrayValue(JsonArray.of("d","e")));
		assertThrows( JsonException.class, () -> v_long.localDateValue(LocalDate.of(2020, 2, 20)));
		assertThrows( JsonException.class, () -> v_long.localTimeValue(LocalTime.of(13, 44, 18)));
		assertThrows( JsonException.class, () -> v_long.localDateTimeValue(LocalDateTime.of(2020, 2, 20, 13, 44, 18)));
		assertThrows( JsonException.class, () -> v_long.offsetTimeValue(OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertThrows( JsonException.class, () -> v_long.offsetDateTimeValue(OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2))));
		assertThrows( JsonException.class, () -> v_long.zonedDateTimeValue(ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern"))));
	}
	
	public void testDoubleA() {
		JsonValues v_double = JsonValues.of(34.34);
		
		assertFalse(v_double.isNull());
		assertTrue(v_double.isNumber());
		assertFalse(v_double.isBoolean());
		assertFalse(v_double.isString());
		assertFalse(v_double.isContainer());
		assertFalse(v_double.isObject());
		assertFalse(v_double.isArray());

		assertEquals(34.34,v_double.value());
		
		assertEquals(34.34,v_double.numberValue());
		assertEquals((byte)34,v_double.byteValue());
		assertEquals((short)34,v_double.shortValue());
		assertEquals(34,v_double.intValue());
		assertEquals(34L,v_double.longValue());
		assertEquals(34.34f,v_double.floatValue());
		assertEquals(34.34,v_double.doubleValue());
		assertEquals(new BigInteger("34"),v_double.bigIntegerValue());
		assertEquals(new BigDecimal("34.34"),v_double.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_double.booleanValue());
		assertThrows( JsonException.class, () -> v_double.stringValue());
		assertThrows( JsonException.class, () -> v_double.objectValue());
		assertThrows( JsonException.class, () -> v_double.arrayValue());

		assertEquals(34.34,v_double.numberValue(10.10));
		assertEquals((byte)34,v_double.byteValue((byte)10));
		assertEquals((short)34,v_double.shortValue((short)10));
		assertEquals(34,v_double.intValue(10));
		assertEquals(34L,v_double.longValue(10L));
		assertEquals(34.34f,v_double.floatValue(10));
		assertEquals(34.34,v_double.doubleValue(10.10));
		assertEquals(new BigInteger("34"),v_double.bigIntegerValue(BigInteger.TEN));
		assertEquals(new BigDecimal("34.34"),v_double.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_double.booleanValue(true));
		assertThrows( JsonException.class, () -> v_double.stringValue("xy"));
		assertThrows( JsonException.class, () -> v_double.objectValue(JsonObject.of("a","AA")));
		assertThrows( JsonException.class, () -> v_double.arrayValue(JsonArray.of("d","e")));
	}
	
	public void testBigIntegerA() {
		JsonValues v_bigInteger = JsonValues.of(new BigInteger("123456789123456789123456789"));
		
		assertFalse(v_bigInteger.isNull());
		assertTrue(v_bigInteger.isNumber());
		assertFalse(v_bigInteger.isBoolean());
		assertFalse(v_bigInteger.isString());
		assertFalse(v_bigInteger.isContainer());
		assertFalse(v_bigInteger.isObject());
		assertFalse(v_bigInteger.isArray());		
		
		assertEquals(new BigInteger("123456789123456789123456789"),v_bigInteger.value());
		
		assertEquals(new BigInteger("123456789123456789123456789"),v_bigInteger.numberValue());
		assertEquals(Byte.MAX_VALUE,v_bigInteger.byteValue());
		assertEquals(Short.MAX_VALUE,v_bigInteger.shortValue());
		assertEquals(Integer.MAX_VALUE,v_bigInteger.intValue()); // saturated, not wrapped
		assertEquals(Long.MAX_VALUE,v_bigInteger.longValue()); // saturated, not wrapped
		assertEquals(1.2345678912345679E26f,v_bigInteger.floatValue());
		assertEquals(1.2345678912345679E26,v_bigInteger.doubleValue());
		assertEquals(new BigInteger("123456789123456789123456789"),v_bigInteger.bigIntegerValue());
		assertEquals(new BigDecimal("123456789123456789123456789"),v_bigInteger.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_bigInteger.booleanValue());
		assertThrows( JsonException.class, () -> v_bigInteger.stringValue());
		assertThrows( JsonException.class, () -> v_bigInteger.objectValue());
		assertThrows( JsonException.class, () -> v_bigInteger.arrayValue());

		assertEquals(new BigInteger("123456789123456789123456789"),v_bigInteger.numberValue(BigInteger.TEN));
		assertEquals(Byte.MAX_VALUE,v_bigInteger.byteValue((byte)10));
		assertEquals(Short.MAX_VALUE,v_bigInteger.shortValue((short)10));
		assertEquals(Integer.MAX_VALUE,v_bigInteger.intValue(10)); // saturated, not wrapped
		assertEquals(Long.MAX_VALUE,v_bigInteger.longValue(10L)); // saturated, not wrapped
		assertEquals(1.2345678912345679E26f,v_bigInteger.floatValue(10.10f));
		assertEquals(1.2345678912345679E26,v_bigInteger.doubleValue(10.10));
		assertEquals(new BigInteger("123456789123456789123456789"),v_bigInteger.bigIntegerValue(BigInteger.TEN));
		assertEquals(new BigDecimal("123456789123456789123456789"),v_bigInteger.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_bigInteger.booleanValue(true));
		assertThrows( JsonException.class, () -> v_bigInteger.stringValue("xy"));
		assertThrows( JsonException.class, () -> v_bigInteger.objectValue(JsonObject.of("a","AA")));
		assertThrows( JsonException.class, () -> v_bigInteger.arrayValue(JsonArray.of("d","e")));
	}
	
	public void testBigDecimalA() {
		JsonValues v_bigDecimal = JsonValues.of(new BigDecimal("4567892345678765478.5657654"));
		
		assertFalse(v_bigDecimal.isNull());
		assertTrue(v_bigDecimal.isNumber());
		assertFalse(v_bigDecimal.isBoolean());
		assertFalse(v_bigDecimal.isString());
		assertFalse(v_bigDecimal.isContainer());
		assertFalse(v_bigDecimal.isObject());
		assertFalse(v_bigDecimal.isArray());

		assertEquals(new BigDecimal("4567892345678765478.5657654"),v_bigDecimal.value());
		
		assertEquals(new BigDecimal("4567892345678765478.5657654"),v_bigDecimal.numberValue());
		assertEquals(Byte.MAX_VALUE,v_bigDecimal.byteValue());
		assertEquals(Short.MAX_VALUE,v_bigDecimal.shortValue());
		assertEquals(Integer.MAX_VALUE,v_bigDecimal.intValue()); // saturated, not wrapped
		assertEquals(4567892345678765478L,v_bigDecimal.longValue());
		assertEquals(4.5678923456787656E18f,v_bigDecimal.floatValue());
		assertEquals(4.5678923456787656E18,v_bigDecimal.doubleValue());
		assertEquals(new BigInteger("4567892345678765478"),v_bigDecimal.bigIntegerValue());
		assertEquals(new BigDecimal("4567892345678765478.5657654"),v_bigDecimal.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_bigDecimal.booleanValue());
		assertThrows( JsonException.class, () -> v_bigDecimal.stringValue());
		assertThrows( JsonException.class, () -> v_bigDecimal.objectValue());
		assertThrows( JsonException.class, () -> v_bigDecimal.arrayValue());

		assertEquals(new BigDecimal("4567892345678765478.5657654"),v_bigDecimal.numberValue(BigDecimal.TEN));
		assertEquals(Byte.MAX_VALUE,v_bigDecimal.byteValue((byte)10));
		assertEquals(Short.MAX_VALUE,v_bigDecimal.shortValue((short)10));
		assertEquals(Integer.MAX_VALUE,v_bigDecimal.intValue(10)); // saturated, not wrapped
		assertEquals(4567892345678765478L,v_bigDecimal.longValue(10L));
		assertEquals(4.5678923456787656E18f,v_bigDecimal.floatValue(10.10f));
		assertEquals(4.5678923456787656E18,v_bigDecimal.doubleValue(10.10));
		assertEquals(new BigInteger("4567892345678765478"),v_bigDecimal.bigIntegerValue(BigInteger.TEN));
		assertEquals(new BigDecimal("4567892345678765478.5657654"),v_bigDecimal.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_bigDecimal.booleanValue(true));
		assertThrows( JsonException.class, () -> v_bigDecimal.stringValue("xy"));
		assertThrows( JsonException.class, () -> v_bigDecimal.objectValue(JsonObject.of("a","AA")));
		assertThrows( JsonException.class, () -> v_bigDecimal.arrayValue(JsonArray.of("d","e")));
	}
	
	public void testBooleanA() {
		JsonValues v_boolean = JsonValues.of(true);
		
		assertFalse(v_boolean.isNull());
		assertFalse(v_boolean.isNumber());
		assertTrue(v_boolean.isBoolean());
		assertFalse(v_boolean.isString());
		assertFalse(v_boolean.isContainer());
		assertFalse(v_boolean.isObject());
		assertFalse(v_boolean.isArray());
		
		assertEquals(true,v_boolean.value());
		
		assertThrows( JsonException.class, () -> v_boolean.numberValue());
		assertThrows( JsonException.class, () -> v_boolean.byteValue());
		assertThrows( JsonException.class, () -> v_boolean.shortValue());
		assertThrows( JsonException.class, () -> v_boolean.intValue());
		assertThrows( JsonException.class, () -> v_boolean.longValue());
		assertThrows( JsonException.class, () -> v_boolean.floatValue());
		assertThrows( JsonException.class, () -> v_boolean.doubleValue());
		assertThrows( JsonException.class, () -> v_boolean.bigIntegerValue());
		assertThrows( JsonException.class, () -> v_boolean.bigDecimalValue());
		assertEquals(true,v_boolean.booleanValue());
		assertThrows( JsonException.class, () -> v_boolean.stringValue());

		assertThrows( JsonException.class, () -> v_boolean.numberValue(10));
		assertThrows( JsonException.class, () -> v_boolean.byteValue((byte)10));
		assertThrows( JsonException.class, () -> v_boolean.shortValue((short)10));
		assertThrows( JsonException.class, () -> v_boolean.intValue(10));
		assertThrows( JsonException.class, () -> v_boolean.longValue(10L));
		assertThrows( JsonException.class, () -> v_boolean.floatValue(10.0f));
		assertThrows( JsonException.class, () -> v_boolean.doubleValue(10.10));
		assertThrows( JsonException.class, () -> v_boolean.bigIntegerValue(BigInteger.TEN));
		assertThrows( JsonException.class, () -> v_boolean.bigDecimalValue(BigDecimal.TEN));
		assertEquals(true,v_boolean.booleanValue(false));
		assertThrows( JsonException.class, () -> v_boolean.stringValue("xy"));
	}
	
	public void testStringA() {
		JsonValues v_string = JsonValues.of("abc");

		assertFalse(v_string.isNull());
		assertFalse(v_string.isNumber());
		assertFalse(v_string.isBoolean());
		assertTrue(v_string.isString());
		assertFalse(v_string.isContainer());
		assertFalse(v_string.isObject());
		assertFalse(v_string.isArray());	
		
		assertEquals("abc",v_string.value());

		assertThrows( JsonException.class, () -> v_string.numberValue());
		assertThrows( JsonException.class, () -> v_string.byteValue());
		assertThrows( JsonException.class, () -> v_string.shortValue());
		assertThrows( JsonException.class, () -> v_string.intValue());
		assertThrows( JsonException.class, () -> v_string.longValue());
		assertThrows( JsonException.class, () -> v_string.floatValue());
		assertThrows( JsonException.class, () -> v_string.doubleValue());
		assertThrows( JsonException.class, () -> v_string.bigIntegerValue());
		assertThrows( JsonException.class, () -> v_string.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_string.booleanValue());
		assertEquals("abc",v_string.stringValue());

		assertThrows( JsonException.class, () -> v_string.numberValue(10));
		assertThrows( JsonException.class, () -> v_string.byteValue((byte)10));
		assertThrows( JsonException.class, () -> v_string.shortValue((short)10));
		assertThrows( JsonException.class, () -> v_string.intValue(10));
		assertThrows( JsonException.class, () -> v_string.longValue(10L));
		assertThrows( JsonException.class, () -> v_string.floatValue(10.10f));
		assertThrows( JsonException.class, () -> v_string.doubleValue(10.10));
		assertThrows( JsonException.class, () -> v_string.bigIntegerValue(BigInteger.TEN));
		assertThrows( JsonException.class, () -> v_string.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_string.booleanValue(true));
		assertEquals("abc",v_string.stringValue("xy"));
	}
	
	public void testObject() throws Exception {
		JsonValues v_object = JsonValues.of(JsonObject.of("s","xyz"));

		assertFalse(v_object.isNull());
		assertFalse(v_object.isNumber());
		assertFalse(v_object.isBoolean());
		assertFalse(v_object.isString());
		assertTrue(v_object.isContainer());
		assertTrue(v_object.isObject());
		assertFalse(v_object.isArray());	
		
		support.assertJsonEquals(JsonObject.of("s","xyz"), v_object.value());

		assertThrows( JsonException.class, () -> v_object.numberValue());
		assertThrows( JsonException.class, () -> v_object.byteValue());
		assertThrows( JsonException.class, () -> v_object.shortValue());
		assertThrows( JsonException.class, () -> v_object.intValue());
		assertThrows( JsonException.class, () -> v_object.longValue());
		assertThrows( JsonException.class, () -> v_object.floatValue());
		assertThrows( JsonException.class, () -> v_object.doubleValue());
		assertThrows( JsonException.class, () -> v_object.bigIntegerValue());
		assertThrows( JsonException.class, () -> v_object.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_object.booleanValue());
		assertThrows( JsonException.class, () -> v_object.stringValue());
		support.assertJsonEquals(JsonObject.of("s","xyz"), v_object.objectValue());
		assertThrows( JsonException.class, () ->  v_object.arrayValue());
		//support.assertJsonEquals(JsonArray.of("def"), v_object.arrayValue());

		assertThrows( JsonException.class, () -> v_object.numberValue(10));
		assertThrows( JsonException.class, () -> v_object.byteValue((byte)10));
		assertThrows( JsonException.class, () -> v_object.shortValue((short)10));
		assertThrows( JsonException.class, () -> v_object.intValue(10));
		assertThrows( JsonException.class, () -> v_object.longValue(10L));
		assertThrows( JsonException.class, () -> v_object.floatValue(10.10f));
		assertThrows( JsonException.class, () -> v_object.doubleValue(10.10));
		assertThrows( JsonException.class, () -> v_object.bigIntegerValue(BigInteger.TEN));
		assertThrows( JsonException.class, () -> v_object.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_object.booleanValue(true));
		assertThrows( JsonException.class, () -> v_object.stringValue("xy"));
		support.assertJsonEquals(JsonObject.of("s","xyz"), v_object.objectValue(JsonObject.of("a","AA")));
		assertThrows( JsonException.class, () -> v_object.arrayValue(JsonArray.of("d","e")));
	}
	
	public void testArray() throws Exception {
		JsonValues v_array = JsonValues.of(JsonArray.of("def"));

		assertFalse(v_array.isNull());
		assertFalse(v_array.isNumber());
		assertFalse(v_array.isBoolean());
		assertFalse(v_array.isString());
		assertTrue(v_array.isContainer());
		assertFalse(v_array.isObject());
		assertTrue(v_array.isArray());	
		
		support.assertJsonEquals(JsonArray.of("def"), v_array.value());

		assertThrows( JsonException.class, () -> v_array.numberValue());
		assertThrows( JsonException.class, () -> v_array.byteValue());
		assertThrows( JsonException.class, () -> v_array.shortValue());
		assertThrows( JsonException.class, () -> v_array.intValue());
		assertThrows( JsonException.class, () -> v_array.longValue());
		assertThrows( JsonException.class, () -> v_array.floatValue());
		assertThrows( JsonException.class, () -> v_array.doubleValue());
		assertThrows( JsonException.class, () -> v_array.bigIntegerValue());
		assertThrows( JsonException.class, () -> v_array.bigDecimalValue());
		assertThrows( JsonException.class, () -> v_array.booleanValue());
		assertThrows( JsonException.class, () -> v_array.stringValue());
		assertThrows( JsonException.class, () -> v_array.objectValue());
		support.assertJsonEquals(JsonArray.of("def"), v_array.arrayValue());

		assertThrows( JsonException.class, () -> v_array.numberValue(10));
		assertThrows( JsonException.class, () -> v_array.byteValue((byte)10));
		assertThrows( JsonException.class, () -> v_array.shortValue((short)10));
		assertThrows( JsonException.class, () -> v_array.intValue(10));
		assertThrows( JsonException.class, () -> v_array.longValue(10L));
		assertThrows( JsonException.class, () -> v_array.floatValue(10.10f));
		assertThrows( JsonException.class, () -> v_array.doubleValue(10.10));
		assertThrows( JsonException.class, () -> v_array.bigIntegerValue(BigInteger.TEN));
		assertThrows( JsonException.class, () -> v_array.bigDecimalValue(BigDecimal.TEN));
		assertThrows( JsonException.class, () -> v_array.booleanValue(true));
		assertThrows( JsonException.class, () -> v_array.stringValue("xy"));
		assertThrows( JsonException.class, () -> v_array.objectValue(JsonObject.of("a","AA")));
		support.assertJsonEquals(JsonArray.of("def"), v_array.arrayValue(JsonArray.of("d","e")));
	}
}
