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
package tests.json.util;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonType;
import org.monflabs.json.JsonUtil;

import tests.ProjectTestCase;

public class JsonUtilTest extends ProjectTestCase {

	public void testNull() {
		Object v = null;
		JsonUtil.checkJsonValue(v);
		assertTrue(JsonUtil.isNull(v));
		assertFalse(JsonUtil.isPrimitive(v));
		assertFalse(JsonUtil.isContainer(v));
		assertTrue(JsonUtil.isJsonValue(v));
	}
	public void testBoolean() {
		Object v = true;
		JsonUtil.checkJsonValue(v);
		assertTrue(JsonUtil.isBoolean(v));
		assertTrue(JsonUtil.isPrimitive(v));
		assertFalse(JsonUtil.isContainer(v));
		assertTrue(JsonUtil.isJsonValue(v));
		assertEquals( v, JsonUtil.checkBoolean(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkBoolean(null) ) ;
	}
	public void testNumber() {
		Object v1 = (byte)1;
		JsonUtil.checkJsonValue(v1);
		assertTrue(JsonUtil.isNumber(v1));
		assertTrue(JsonUtil.isPrimitive(v1));
		assertFalse(JsonUtil.isContainer(v1));
		assertTrue(JsonUtil.isJsonValue(v1));
		assertEquals( v1, JsonUtil.checkNumber(v1) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkNumber(null) ) ;

		Object v2 = (short)2;
		JsonUtil.checkJsonValue(v2);
		assertTrue(JsonUtil.isNumber(v2));
		assertTrue(JsonUtil.isPrimitive(v2));
		assertFalse(JsonUtil.isContainer(v2));
		assertTrue(JsonUtil.isJsonValue(v2));
		assertEquals( v2, JsonUtil.checkShort(v2) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkShort(null) ) ;

		Object v3 = 3;
		JsonUtil.checkJsonValue(v3);
		assertTrue(JsonUtil.isNumber(v3));
		assertTrue(JsonUtil.isPrimitive(v3));
		assertFalse(JsonUtil.isContainer(v3));
		assertTrue(JsonUtil.isJsonValue(v3));
		assertEquals( v3, JsonUtil.checkInt(v3) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkInt(null) ) ;

		Object v4 = 4L;
		JsonUtil.checkJsonValue(v4);
		assertTrue(JsonUtil.isNumber(v4));
		assertTrue(JsonUtil.isPrimitive(v4));
		assertFalse(JsonUtil.isContainer(v4));
		assertTrue(JsonUtil.isJsonValue(v4));
		assertEquals( v4, JsonUtil.checkLong(v4) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLong(null) ) ;

		Object v5 = 5.0F;
		JsonUtil.checkJsonValue(v5);
		assertTrue(JsonUtil.isNumber(v5));
		assertTrue(JsonUtil.isPrimitive(v5));
		assertFalse(JsonUtil.isContainer(v5));
		assertTrue(JsonUtil.isJsonValue(v5));
		assertEquals( v5, JsonUtil.checkFloat(v5) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkFloat(null) ) ;

		Object v6 = 6.0;
		JsonUtil.checkJsonValue(v6);
		assertTrue(JsonUtil.isNumber(v6));
		assertTrue(JsonUtil.isPrimitive(v6));
		assertFalse(JsonUtil.isContainer(v6));
		assertTrue(JsonUtil.isJsonValue(v6));
		assertEquals( v6, JsonUtil.checkDouble(v6) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkDouble(null) ) ;

		Object v7 = new BigInteger("7");
		JsonUtil.checkJsonValue(v7);
		assertTrue(JsonUtil.isNumber(v7));
		assertTrue(JsonUtil.isPrimitive(7));
		assertFalse(JsonUtil.isContainer(v7));
		assertTrue(JsonUtil.isJsonValue(v7));
		assertEquals( v7, JsonUtil.checkBigInteger(v7) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkBigInteger(null) ) ;
		
		Object v8 = new BigDecimal("8");
		JsonUtil.checkJsonValue(v8);
		assertTrue(JsonUtil.isNumber(v8));
		assertTrue(JsonUtil.isPrimitive(v8));
		assertFalse(JsonUtil.isContainer(v8));
		assertTrue(JsonUtil.isJsonValue(v8));
		assertEquals( v8, JsonUtil.checkBigDecimal(v8) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkBigDecimal(null) ) ;
	}
	public void testString() {
		Object v = "str";
		JsonUtil.checkJsonValue(v);
		assertTrue(JsonUtil.isString(v));
		assertTrue(JsonUtil.isPrimitive(v));
		assertFalse(JsonUtil.isContainer(v));
		assertTrue(JsonUtil.isJsonValue(v));
		assertEquals( v, JsonUtil.checkString(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkString(null) ) ;
	}
	public void testObject() {
		Object v = JsonFactory.get().createObject();
		JsonUtil.checkJsonValue(v);
		assertTrue(JsonUtil.isObject(v));
		assertFalse(JsonUtil.isPrimitive(v));
		assertTrue(JsonUtil.isContainer(v));
		assertTrue(JsonUtil.isJsonValue(v));
		assertEquals( v, JsonUtil.checkObject(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkBoolean(null) ) ;
	}
	public void testArray() {
		Object v = JsonFactory.get().createArray();
		JsonUtil.checkJsonValue(v);
		assertTrue(JsonUtil.isArray(v));
		assertFalse(JsonUtil.isPrimitive(v));
		assertTrue(JsonUtil.isContainer(v));
		assertTrue(JsonUtil.isJsonValue(v));
		assertEquals( v, JsonUtil.checkArray(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkArray(null) ) ;
	}
	public void testLocalDate() {
		Object v = "2020-09-24";
		assertEquals( JsonUtil.parseLocalDate("2020-09-24"), JsonUtil.checkLocalDate(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalDate(null) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalDate("") ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalDate("abc") ) ;
	}
	public void testLocalTime() {
		Object v = "20:45:25";
		assertEquals( JsonUtil.parseLocalTime("20:45:25"), JsonUtil.checkLocalTime(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalTime(null) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalTime("") ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalTime("abc") ) ;
	}
	public void testLocalDateTime() {
		Object v = "2020-09-24T21:45:25";
		assertEquals( JsonUtil.parseLocalDateTime("2020-09-24T21:45:25"), JsonUtil.checkLocalDateTime(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalDateTime(null) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalDateTime("") ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkLocalDateTime("abc") ) ;
	}
	public void testOffsetDate() {
		Object v = "20:45:25+05:00";
		assertEquals( JsonUtil.parseOffsetTime("20:45:25+05:00"), JsonUtil.checkOffsetTime(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime(null) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime("") ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime("abc") ) ;
	}
	public void testOffsetTime() {
		Object v = "20:45:25+05:00";
		assertEquals( JsonUtil.parseOffsetTime("20:45:25+05:00"), JsonUtil.checkOffsetTime(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime(null) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime("") ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime("abc") ) ;
	}
	public void testOffsetDateTime() {
		Object v = "2020-09-24T21:45:25+04:00";
		assertEquals( JsonUtil.parseOffsetDateTime("2020-09-24T21:45:25+04:00"), JsonUtil.checkOffsetDateTime(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime(null) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime("") ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkOffsetTime("abc") ) ;
	}
	public void testZonedDateTime() {
		Object v = "2022-08-14T14:06:43-04:00[US/Eastern]";
		assertEquals( JsonUtil.parseZonedDateTime("2022-08-14T14:06:43-04:00[US/Eastern]"), JsonUtil.checkZonedDateTime(v) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkZonedDateTime(null) ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkZonedDateTime("") ) ;
		assertThrows( JsonException.class, () -> JsonUtil.checkZonedDateTime("abc") ) ;
	}

	public void testOf() throws Exception {
		assertEquals( JsonFactory.get().parse("{}"), JsonFactory.get().of() );
		assertEquals( JsonFactory.get().parse("{'a':null}"), JsonFactory.get().of("a",null) );
		// A key without a value, a key that is not a string: an error, not a:null or a ClassCastException
		assertThrows( JsonException.class, () -> JsonFactory.get().of("a") );
		assertThrows( JsonException.class, () -> JsonFactory.get().of(1,2) );
		assertEquals( JsonFactory.get().parse("{'a':123}"), JsonFactory.get().of("a",123) );
		assertEquals( JsonFactory.get().parse("{'a':123, b:456}"), JsonFactory.get().of("a",123,"b",456) );
		assertEquals( JsonFactory.get().parse("{'a':123, b:{c:true}}"), JsonFactory.get().of("a",123,"b",JsonFactory.get().of("c",true)) );
	}

	public void testOfArray() throws Exception {
		assertEquals( JsonFactory.get().parse("[]"), JsonFactory.get().arrayOf() );
		assertEquals( JsonFactory.get().parse("['a']"), JsonFactory.get().arrayOf("a") );
		assertEquals( JsonFactory.get().parse("['a',123]"), JsonFactory.get().arrayOf("a",123) );
		assertEquals( JsonFactory.get().parse("['a',123,456]"), JsonFactory.get().arrayOf("a",123,456) );
		assertEquals( JsonFactory.get().parse("['a',123,{c:true}]"), JsonFactory.get().arrayOf("a",123,JsonFactory.get().of("c",true)) );
	}

	public void testType() throws Exception {
		assertEquals( JsonType.NULL, JsonType.typeOf(null) );
		assertEquals( JsonType.NUMBER, JsonType.typeOf(1) );
		assertEquals( JsonType.NUMBER, JsonType.typeOf(1.2) );
		assertEquals( JsonType.BOOLEAN, JsonType.typeOf(true) );
		assertEquals( JsonType.BOOLEAN, JsonType.typeOf(false) );
		assertEquals( JsonType.STRING, JsonType.typeOf("") );
		assertEquals( JsonType.STRING, JsonType.typeOf("abc") );
		assertEquals( JsonType.OBJECT, JsonType.typeOf(JsonFactory.get().createObject()) );
		assertEquals( JsonType.ARRAY, JsonType.typeOf(JsonFactory.get().createArray()) );
		assertEquals( JsonType.UNKNOWN, JsonType.typeOf('a') );
		
		assertThrows(Exception.class, () -> JsonUtil.checkJsonValue('a') );
		assertThrows(Exception.class, () -> JsonUtil.checkJsonValue(new Object() ) );
	}

	public void testConversion() throws Exception {
		assertEquals( (byte)1, JsonUtil.toByte((byte)1) );
		assertEquals( (byte)1, JsonUtil.toByte((short)1) );
		assertEquals( (byte)1, JsonUtil.toByte(1) );
		assertEquals( (byte)1, JsonUtil.toByte(1L) );
		assertEquals( (byte)1, JsonUtil.toByte(1.1f) );
		assertEquals( (byte)1, JsonUtil.toByte(1.1) );
		assertEquals( (byte)0, JsonUtil.toByte(Double.NaN) );
		assertEquals( (byte)-1, JsonUtil.toByte(Double.POSITIVE_INFINITY) );
		assertEquals( 0, JsonUtil.toByte(Double.NEGATIVE_INFINITY) );
		assertEquals( (byte)0, JsonUtil.toByte(Float.NaN) );
		assertEquals( (byte)-1, JsonUtil.toByte(Float.POSITIVE_INFINITY) );
		assertEquals( 0, JsonUtil.toByte(Float.NEGATIVE_INFINITY) );

		assertEquals( (short)1, JsonUtil.toShort((byte)1) );
		assertEquals( (short)1, JsonUtil.toShort((short)1) );
		assertEquals( (short)1, JsonUtil.toShort(1) );
		assertEquals( (short)1, JsonUtil.toShort(1L) );
		assertEquals( (short)1, JsonUtil.toShort(1.1f) );
		assertEquals( (short)1, JsonUtil.toShort(1.1) );
		assertEquals( (short)0, JsonUtil.toShort(Double.NaN) );
		assertEquals( (short)-1, JsonUtil.toShort(Double.POSITIVE_INFINITY) );
		assertEquals( 0, JsonUtil.toShort(Double.NEGATIVE_INFINITY) );
		assertEquals( (short)0, JsonUtil.toShort(Float.NaN) );
		assertEquals( (short)-1, JsonUtil.toShort(Float.POSITIVE_INFINITY) );
		assertEquals( 0, JsonUtil.toShort(Float.NEGATIVE_INFINITY) );

		assertEquals( 1, JsonUtil.toInt((byte)1) );
		assertEquals( 1, JsonUtil.toInt((short)1) );
		assertEquals( 1, JsonUtil.toInt(1) );
		assertEquals( 1, JsonUtil.toInt(1L) );
		assertEquals( 1, JsonUtil.toInt(1.1f) );
		assertEquals( 1, JsonUtil.toInt(1.1) );
		assertEquals( 0, JsonUtil.toInt(Double.NaN) );
		assertEquals( Integer.MAX_VALUE, JsonUtil.toInt(Double.POSITIVE_INFINITY) );
		assertEquals( Integer.MIN_VALUE, JsonUtil.toInt(Double.NEGATIVE_INFINITY) );
		assertEquals( (int)0, JsonUtil.toInt(Float.NaN) );
		assertEquals( Integer.MAX_VALUE, JsonUtil.toInt(Float.POSITIVE_INFINITY) );
		assertEquals( Integer.MIN_VALUE, JsonUtil.toInt(Float.NEGATIVE_INFINITY) );

		assertEquals( 1L, JsonUtil.toLong((byte)1) );
		assertEquals( 1L, JsonUtil.toLong((short)1) );
		assertEquals( 1L, JsonUtil.toLong(1) );
		assertEquals( 1L, JsonUtil.toLong(1L) );
		assertEquals( 1L, JsonUtil.toLong(1.1f) );
		assertEquals( 1L, JsonUtil.toLong(1.1) );
		assertEquals( 0L, JsonUtil.toLong(Double.NaN) );
		assertEquals( Long.MAX_VALUE, JsonUtil.toLong(Double.POSITIVE_INFINITY) );
		assertEquals( Long.MIN_VALUE, JsonUtil.toLong(Double.NEGATIVE_INFINITY) );
		assertEquals( (long)0, JsonUtil.toLong(Float.NaN) );
		assertEquals( Long.MAX_VALUE, JsonUtil.toLong(Float.POSITIVE_INFINITY) );
		assertEquals( Long.MIN_VALUE, JsonUtil.toLong(Float.NEGATIVE_INFINITY) );

		assertEquals( 1.0f, JsonUtil.toFloat((byte)1) );
		assertEquals( 1.0f, JsonUtil.toFloat((short)1) );
		assertEquals( 1.0f, JsonUtil.toFloat(1) );
		assertEquals( 1.0f, JsonUtil.toFloat(1L) );
		assertEquals( 1.1f, JsonUtil.toFloat(1.1f) );
		assertEquals( 1.1f, JsonUtil.toFloat(1.1) );
		assertEquals( Float.NaN, JsonUtil.toFloat(Double.NaN) );
		assertEquals( Float.POSITIVE_INFINITY, JsonUtil.toFloat(Double.POSITIVE_INFINITY) );
		assertEquals( Float.NEGATIVE_INFINITY, JsonUtil.toFloat(Double.NEGATIVE_INFINITY) );
		assertEquals( Float.NaN, JsonUtil.toFloat(Float.NaN) );
		assertEquals( Float.POSITIVE_INFINITY, JsonUtil.toFloat(Float.POSITIVE_INFINITY) );
		assertEquals( Float.NEGATIVE_INFINITY, JsonUtil.toFloat(Float.NEGATIVE_INFINITY) );

		assertEquals( 1.0, JsonUtil.toDouble((byte)1) );
		assertEquals( 1.0, JsonUtil.toDouble((short)1) );
		assertEquals( 1.0, JsonUtil.toDouble(1) );
		assertEquals( 1.0, JsonUtil.toDouble(1L) );
		assertEquals( 1.5, JsonUtil.toDouble(1.5f) );
		assertEquals( 1.5, JsonUtil.toDouble(1.5) );
		assertEquals( Double.NaN, JsonUtil.toDouble(Double.NaN) );
		assertEquals( Double.POSITIVE_INFINITY, JsonUtil.toDouble(Double.POSITIVE_INFINITY) );
		assertEquals( Double.NEGATIVE_INFINITY, JsonUtil.toDouble(Double.NEGATIVE_INFINITY) );
		assertEquals( Double.NaN, JsonUtil.toDouble(Float.NaN) );
		assertEquals( Double.POSITIVE_INFINITY, JsonUtil.toDouble(Float.POSITIVE_INFINITY) );
		assertEquals( Double.NEGATIVE_INFINITY, JsonUtil.toDouble(Float.NEGATIVE_INFINITY) );

		assertEquals( new BigInteger("1"), JsonUtil.toBigInteger((byte)1) );
		assertEquals( new BigInteger("1"), JsonUtil.toBigInteger((short)1) );
		assertEquals( new BigInteger("1"), JsonUtil.toBigInteger(1) );
		assertEquals( new BigInteger("1"), JsonUtil.toBigInteger(1L) );
		assertEquals( new BigInteger("1"), JsonUtil.toBigInteger(1.1f) );
		assertEquals( new BigInteger("1"), JsonUtil.toBigInteger(1.1) );
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigInteger(Double.NaN));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigInteger(Double.POSITIVE_INFINITY));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigInteger(Double.NEGATIVE_INFINITY));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigInteger(Float.NaN));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigInteger(Float.POSITIVE_INFINITY));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigInteger(Float.NEGATIVE_INFINITY));

		assertEquals( new BigDecimal("1"), JsonUtil.toBigDecimal((byte)1) );
		assertEquals( new BigDecimal("1"), JsonUtil.toBigDecimal((short)1) );
		assertEquals( new BigDecimal("1"), JsonUtil.toBigDecimal(1) );
		assertEquals( new BigDecimal("1"), JsonUtil.toBigDecimal(1L) );
		assertEquals( new BigDecimal("1.5"), JsonUtil.toBigDecimal(1.5f) );
		assertEquals( new BigDecimal("1.1"), JsonUtil.toBigDecimal(1.1) );
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigDecimal(Double.NaN));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigDecimal(Double.POSITIVE_INFINITY));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigDecimal(Double.NEGATIVE_INFINITY));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigDecimal(Float.NaN));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigDecimal(Float.POSITIVE_INFINITY));
		assertThrows(ArithmeticException.class, () -> JsonUtil.toBigDecimal(Float.NEGATIVE_INFINITY));
	}

	public void testEq() throws Exception {
		assertTrue( JsonUtil.eq(null,null) );
		assertTrue( JsonUtil.eq(true,true) );
		assertTrue( JsonUtil.eq(false,false) );
		assertTrue( JsonUtil.eq(1,1) );
		assertTrue( JsonUtil.eq(1L,1) );
		assertTrue( JsonUtil.eq(1.0f,1) );
		assertTrue( JsonUtil.eq(1.0,1) );
		assertTrue( JsonUtil.eq(BigInteger.ONE,1) );
		assertTrue( JsonUtil.eq(BigDecimal.ONE,1) );
		assertTrue( JsonUtil.eq(1,1) );
		assertTrue( JsonUtil.eq("abc","abc") );

		assertFalse( JsonUtil.eq(0,null) );
		assertFalse( JsonUtil.eq(true,false) );
		assertFalse( JsonUtil.eq(1,2) );
		assertFalse( JsonUtil.eq(1L,2.0) );
		assertFalse( JsonUtil.eq("abc","cba") );

		assertTrue( JsonUtil.eq(JsonObject.of("a",BigInteger.ONE),JsonObject.of("a",1)) );
		assertTrue( JsonUtil.eq(JsonObject.of("a",BigInteger.ONE,"b",3),JsonObject.of("a",1,"b",3)) );

		assertTrue( JsonUtil.eq(JsonArray.of(BigInteger.ONE),JsonArray.of(1)) );
		assertTrue( JsonUtil.eq(JsonArray.of(BigInteger.ONE,3),JsonArray.of(1,3)) );
	}

	public void testNe() throws Exception {
		assertFalse( JsonUtil.ne(null,null) );
		assertFalse( JsonUtil.ne(true,true) );
		assertFalse( JsonUtil.ne(false,false) );
		assertFalse( JsonUtil.ne(1,1) );
		assertFalse( JsonUtil.ne(1L,1) );
		assertFalse( JsonUtil.ne(1.0f,1) );
		assertFalse( JsonUtil.ne(1.0,1) );
		assertFalse( JsonUtil.ne(BigInteger.ONE,1) );
		assertFalse( JsonUtil.ne(BigDecimal.ONE,1) );
		assertFalse( JsonUtil.ne(1,1) );
		assertFalse( JsonUtil.ne("abc","abc") );

		assertTrue( JsonUtil.ne(0,null) );
		assertTrue( JsonUtil.ne(true,false) );
		assertTrue( JsonUtil.ne(1,2) );
		assertTrue( JsonUtil.ne(1L,2.0) );
		assertTrue( JsonUtil.ne("abc","cba") );
	}

	public void testLe() throws Exception {
		assertTrue( JsonUtil.le(null,null) );
		assertTrue( JsonUtil.le(true,true) );
		assertTrue( JsonUtil.le(false,false) );
		assertTrue( JsonUtil.le(1,1) );
		assertTrue( JsonUtil.le(1L,1) );
		assertTrue( JsonUtil.le(1.0f,1) );
		assertTrue( JsonUtil.le(1.0,1) );
		assertTrue( JsonUtil.le(BigInteger.ONE,1) );
		assertTrue( JsonUtil.le(BigDecimal.ONE,1) );
		assertTrue( JsonUtil.le(1,1) );
		assertTrue( JsonUtil.le("abc","abc") );

		assertTrue( JsonUtil.le(1,2) );
		assertTrue( JsonUtil.le(1.3,2) );
		assertTrue( JsonUtil.le(false,true) );
		assertTrue( JsonUtil.le("abc","xyz") );
		assertTrue( JsonUtil.le("abc",1) );

		assertFalse( JsonUtil.le(2,1) );
		assertFalse( JsonUtil.le(2,1.3) );
		assertFalse( JsonUtil.le(true,false) );
		assertFalse( JsonUtil.le("xyz","abc") );
		assertFalse( JsonUtil.le(1,"abc") );
		
		assertFalse( JsonUtil.le(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",2)) );
		assertTrue( JsonUtil.le(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",3)) );
		assertTrue( JsonUtil.le(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",4)) );

		assertFalse( JsonUtil.le(JsonArray.of(1,3),JsonArray.of(1,2)) );
		assertTrue( JsonUtil.le(JsonArray.of(1,3),JsonArray.of(1,3)) );
		assertTrue( JsonUtil.le(JsonArray.of(1,3),JsonArray.of(1,4)) );
	}

	public void testLt() throws Exception {
		assertFalse( JsonUtil.lt(null,null) );
		assertFalse( JsonUtil.lt(true,true) );
		assertFalse( JsonUtil.lt(false,false) );
		assertFalse( JsonUtil.lt(1,1) );
		assertFalse( JsonUtil.lt(1L,1) );
		assertFalse( JsonUtil.lt(1.0f,1) );
		assertFalse( JsonUtil.lt(1.0,1) );
		assertFalse( JsonUtil.lt(BigInteger.ONE,1) );
		assertFalse( JsonUtil.lt(BigDecimal.ONE,1) );
		assertFalse( JsonUtil.lt(1,1) );
		assertFalse( JsonUtil.lt("abc","abc") );

		assertTrue( JsonUtil.lt(1,2) );
		assertTrue( JsonUtil.lt(1.3,2) );
		assertTrue( JsonUtil.lt(false,true) );
		assertTrue( JsonUtil.lt("abc","xyz") );
		assertTrue( JsonUtil.lt("abc",1) );

		assertFalse( JsonUtil.lt(2,1) );
		assertFalse( JsonUtil.lt(2,1.3) );
		assertFalse( JsonUtil.lt(true,false) );
		assertFalse( JsonUtil.lt("xyz","abc") );
		assertFalse( JsonUtil.lt(1,"abc") );
		
		assertFalse( JsonUtil.lt(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",2)) );
		assertFalse( JsonUtil.lt(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",3)) );
		assertTrue( JsonUtil.lt(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",4)) );

		assertFalse( JsonUtil.lt(JsonArray.of(1,3),JsonArray.of(1,2)) );
		assertFalse( JsonUtil.lt(JsonArray.of(1,3),JsonArray.of(1,3)) );
		assertTrue( JsonUtil.lt(JsonArray.of(1,3),JsonArray.of(1,4)) );
	}

	public void testGe() throws Exception {
		assertTrue( JsonUtil.ge(null,null) );
		assertTrue( JsonUtil.ge(true,true) );
		assertTrue( JsonUtil.ge(false,false) );
		assertTrue( JsonUtil.ge(1,1) );
		assertTrue( JsonUtil.ge(1L,1) );
		assertTrue( JsonUtil.ge(1.0f,1) );
		assertTrue( JsonUtil.ge(1.0,1) );
		assertTrue( JsonUtil.ge(BigInteger.ONE,1) );
		assertTrue( JsonUtil.ge(BigDecimal.ONE,1) );
		assertTrue( JsonUtil.ge(1,1) );
		assertTrue( JsonUtil.ge("abc","abc") );

		assertFalse( JsonUtil.ge(1,2) );
		assertFalse( JsonUtil.ge(1.3,2) );
		assertFalse( JsonUtil.ge(false,true) );
		assertFalse( JsonUtil.ge("abc","xyz") );
		assertFalse( JsonUtil.ge("abc",1) );

		assertTrue( JsonUtil.ge(2,1) );
		assertTrue( JsonUtil.ge(2,1.3) );
		assertTrue( JsonUtil.ge(true,false) );
		assertTrue( JsonUtil.ge("xyz","abc") );
		assertTrue( JsonUtil.ge(1,"abc") );
		
		assertTrue( JsonUtil.ge(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",2)) );
		assertTrue( JsonUtil.ge(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",3)) );
		assertFalse( JsonUtil.ge(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",4)) );

		assertTrue( JsonUtil.ge(JsonArray.of(1,3),JsonArray.of(1,2)) );
		assertTrue( JsonUtil.ge(JsonArray.of(1,3),JsonArray.of(1,3)) );
		assertFalse( JsonUtil.ge(JsonArray.of(1,3),JsonArray.of(1,4)) );
	}

	public void testGt() throws Exception {
		assertFalse( JsonUtil.gt(null,null) );
		assertFalse( JsonUtil.gt(true,true) );
		assertFalse( JsonUtil.gt(false,false) );
		assertFalse( JsonUtil.gt(1,1) );
		assertFalse( JsonUtil.gt(1L,1) );
		assertFalse( JsonUtil.gt(1.0f,1) );
		assertFalse( JsonUtil.gt(1.0,1) );
		assertFalse( JsonUtil.gt(BigInteger.ONE,1) );
		assertFalse( JsonUtil.gt(BigDecimal.ONE,1) );
		assertFalse( JsonUtil.gt(1,1) );
		assertFalse( JsonUtil.gt("abc","abc") );

		assertFalse( JsonUtil.gt(1,2) );
		assertFalse( JsonUtil.gt(1.3,2) );
		assertFalse( JsonUtil.gt(false,true) );
		assertFalse( JsonUtil.gt("abc","xyz") );
		assertFalse( JsonUtil.gt("abc",1) );

		assertTrue( JsonUtil.gt(2,1) );
		assertTrue( JsonUtil.gt(2,1.3) );
		assertTrue( JsonUtil.gt(true,false) );
		assertTrue( JsonUtil.gt("xyz","abc") );
		assertTrue( JsonUtil.gt(1,"abc") );
		
		assertTrue( JsonUtil.gt(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",2)) );
		assertFalse( JsonUtil.gt(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",3)) );
		assertFalse( JsonUtil.gt(JsonObject.of("a",1,"b",3),JsonObject.of("a",1,"b",4)) );

		assertTrue( JsonUtil.gt(JsonArray.of(1,3),JsonArray.of(1,2)) );
		assertFalse( JsonUtil.gt(JsonArray.of(1,3),JsonArray.of(1,3)) );
		assertFalse( JsonUtil.gt(JsonArray.of(1,3),JsonArray.of(1,4)) );
	}

	public void testCompare() throws Exception {
		assertEquals( 0, JsonUtil.compare(null,null) );
		
		assertEquals( 0, JsonUtil.compare(1,1) );
		assertEquals( -1, JsonUtil.compare(-1,1) );
		assertEquals( 1, JsonUtil.compare(1,-1) );

		Object[] all = new Object[] {
			(byte)1, (short)1, 1, 1L, 1.0f, 1.0, BigInteger.ONE, BigDecimal.ONE
		};
		for(Object o: all) {
			assertEquals( 0, JsonUtil.compare(o,(byte)1) );
			assertEquals( 0, JsonUtil.compare(o,(short)1) );
			assertEquals( 0, JsonUtil.compare(o,1L) );
			assertEquals( 0, JsonUtil.compare(o,1.0f) );
			assertEquals( 0, JsonUtil.compare(o,1.0) );
			assertEquals( 0, JsonUtil.compare(o,BigInteger.ONE) );
			assertEquals( 0, JsonUtil.compare(o,BigDecimal.ONE) );
	
			assertEquals( 1, JsonUtil.compare(o,(byte)-1) );
			assertEquals( 1, JsonUtil.compare(o,(short)-1) );
			assertEquals( 1, JsonUtil.compare(o,-1L) );
			assertEquals( 1, JsonUtil.compare(o,-1.0f) );
			assertEquals( 1, JsonUtil.compare(o,-1.0) );
			assertEquals( 1, JsonUtil.compare(o,new BigInteger("-1")) );
			assertEquals( 1, JsonUtil.compare(o,new BigDecimal("-1.0")) );
	
			assertEquals( -1, JsonUtil.compare(o,(byte)2) );
			assertEquals( -1, JsonUtil.compare(o,(short)2) );
			assertEquals( -1, JsonUtil.compare(o,2L) );
			assertEquals( -1, JsonUtil.compare(o,2.0f) );
			assertEquals( -1, JsonUtil.compare(o,2.0) );
			assertEquals( -1, JsonUtil.compare(o,BigInteger.TWO) );
			assertEquals( -1, JsonUtil.compare(o,new BigDecimal("2")) );
		}
		assertEquals( 0, JsonUtil.compare("b","b") );
		assertEquals( 1, JsonUtil.compare("c","b") );
		assertEquals( -1, JsonUtil.compare("a","b") );

		assertEquals( 0, JsonUtil.compare(true,true) );
		assertEquals( 0, JsonUtil.compare(false,false) );
		assertEquals( 1, JsonUtil.compare(true,false) );
		assertEquals( -1, JsonUtil.compare(false,true) );

		assertEquals( 0, JsonUtil.compare(JsonObject.create(),JsonObject.create()) );
		assertEquals( 0, JsonUtil.compare(JsonObject.of("a",1),JsonObject.of("a",1)) );
		assertEquals( -1, JsonUtil.compare(JsonObject.of("a",1),JsonObject.of("a",2)) );
		assertEquals( 1, JsonUtil.compare(JsonObject.of("a",2),JsonObject.of("a",1)) );
		assertEquals( 1, JsonUtil.compare(JsonObject.of("a",1,"b",2),JsonObject.of("a",1)) );
		assertEquals( -1, JsonUtil.compare(JsonObject.of("a",1),JsonObject.of("a",1,"b",2)) );
		assertEquals( 1, JsonUtil.compare(JsonObject.of("a",1),JsonObject.of("c",1)) );
		assertEquals( -1, JsonUtil.compare(JsonObject.of("c",1),JsonObject.of("1",1)) );

		assertEquals( 0, JsonUtil.compare(JsonArray.create(),JsonArray.create()) );
		assertEquals( 0, JsonUtil.compare(JsonArray.of(1),JsonArray.of(1)) );
		assertEquals( 1, JsonUtil.compare(JsonArray.of(2),JsonArray.of(1)) );
		assertEquals( -1, JsonUtil.compare(JsonArray.of(1),JsonArray.of(2)) );
		assertEquals( 1, JsonUtil.compare(JsonArray.of(1,2),JsonArray.of(1)) );
		assertEquals( -1, JsonUtil.compare(JsonArray.of(1),JsonArray.of(1,2)) );
		
		assertEquals( -1, JsonUtil.compare(1,true) );
	}
	
	public void testParse() throws Exception {
		assertEquals( true, JsonUtil.parseBoolean("true"));
		assertEquals( false, JsonUtil.parseBoolean("false")); // used to return true
		assertThrows( JsonException.class, () -> JsonUtil.parseBoolean(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseBoolean(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseBoolean("abc"));

		
		assertEquals( 64, JsonUtil.parseNumber("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseNumber(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseNumber(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseNumber("abc"));
		

		assertEquals( (byte)64, JsonUtil.parseByte("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseByte(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseByte(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseByte("abc"));
		
		assertEquals( (short)64, JsonUtil.parseShort("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseShort(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseShort(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseShort("abc"));
		
		assertEquals( 64, JsonUtil.parseInt("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseInt(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseInt(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseInt("abc"));
		
		assertEquals( 64L, JsonUtil.parseLong("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseLong(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseLong(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseLong("abc"));
		
		assertEquals( 64.0f, JsonUtil.parseFloat("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseFloat(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseFloat(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseFloat("abc"));
		
		assertEquals( 64.0, JsonUtil.parseDouble("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseDouble(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseDouble(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseDouble("abc"));
		
		assertEquals( new BigInteger("64"), JsonUtil.parseBigInteger("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseBigInteger(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseBigInteger(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseBigInteger("abc"));
		
		assertEquals( new BigDecimal("64"), JsonUtil.parseBigDecimal("64"));
		assertThrows( JsonException.class, () -> JsonUtil.parseBigDecimal(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseBigDecimal(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseBigDecimal("abc"));
		
		assertEquals( JsonFactory.get().createObject(), JsonUtil.parseObject("{}"));
		assertThrows( JsonException.class, () -> JsonUtil.parseObject(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseObject(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseObject("abc"));
		
		assertEquals( JsonFactory.get().createArray(), JsonUtil.parseArray("[]"));
		assertThrows( JsonException.class, () -> JsonUtil.parseArray(null));
		assertThrows( JsonException.class, () -> JsonUtil.parseArray(""));
		assertThrows( JsonException.class, () -> JsonUtil.parseArray("abc"));
	}

	public void testIdentifier() throws Exception {
		assertTrue( JsonUtil.isIdentifierStart('_') );
		assertTrue( JsonUtil.isIdentifierStart('a') );
		assertTrue( JsonUtil.isIdentifierStart('z') );
		assertFalse( JsonUtil.isIdentifierStart('0') );
		assertFalse( JsonUtil.isIdentifierStart('9') );

		assertTrue( JsonUtil.isIdentifierPart('_') );
		assertTrue( JsonUtil.isIdentifierPart('a') );
		assertTrue( JsonUtil.isIdentifierPart('z') );
		assertTrue( JsonUtil.isIdentifierPart('0') );
		assertTrue( JsonUtil.isIdentifierPart('9') );

		assertTrue( JsonUtil.isIdentifier("a") );
		assertTrue( JsonUtil.isIdentifier("abc") );
		assertTrue( JsonUtil.isIdentifier("a0_c$") );

		assertFalse( JsonUtil.isIdentifier("0") );
		assertFalse( JsonUtil.isIdentifier("%ab") );
	}
	

	public void testAsInt() throws Exception {
		assertEquals( 0, JsonUtil.asInt(null));
		assertEquals( 34, JsonUtil.asInt(34));
		assertEquals( 34, JsonUtil.asInt("34"));
		assertEquals( 0, JsonUtil.asInt("34bac"));
		assertEquals( 1, JsonUtil.asInt(true));
		
		assertEquals( 79, JsonUtil.asInt(null,79));
		assertEquals( 34, JsonUtil.asInt(34,79));
		assertEquals( 34, JsonUtil.asInt("34",79));
		assertEquals( 79, JsonUtil.asInt("34bac",79));
		assertEquals( 1, JsonUtil.asInt(true,79));
	}
	
	public void testAsLong() throws Exception {
		assertEquals( 0L, JsonUtil.asLong(null));
		assertEquals( 34L, JsonUtil.asLong(34));
		assertEquals( 34L, JsonUtil.asLong("34"));
		assertEquals( 0L, JsonUtil.asLong("34bac"));
		assertEquals( 1L, JsonUtil.asLong(true));
		
		assertEquals( 79L, JsonUtil.asLong(null,79));
		assertEquals( 34L, JsonUtil.asLong(34,79));
		assertEquals( 34L, JsonUtil.asLong("34",79));
		assertEquals( 79L, JsonUtil.asLong("34bac",79));
		assertEquals( 1L, JsonUtil.asLong(true,79));
	}
	
	public void testAsDouble() throws Exception {
		assertEquals( 0.0, JsonUtil.asDouble(null));
		assertEquals( 34.0, JsonUtil.asDouble(34));
		assertEquals( 34.0, JsonUtil.asDouble("34"));
		assertEquals( 0.0, JsonUtil.asDouble("34bac"));
		assertEquals( 1.0, JsonUtil.asDouble(true));
		
		assertEquals( 79.0, JsonUtil.asDouble(null,79));
		assertEquals( 34.0, JsonUtil.asDouble(34,79));
		assertEquals( 34.0, JsonUtil.asDouble("34",79));
		assertEquals( 79.0, JsonUtil.asDouble("34bac",79));
		assertEquals( 1.0, JsonUtil.asDouble(true,79));
	}
	
	public void testAsBigInteger() throws Exception {
		assertEquals( BigInteger.ZERO, JsonUtil.asBigInteger(null));
		assertEquals( BigInteger.TEN, JsonUtil.asBigInteger(10));
		assertEquals( BigInteger.TEN, JsonUtil.asBigInteger("10"));
		assertEquals( BigInteger.ZERO, JsonUtil.asBigInteger("10bac"));
		assertEquals( BigInteger.ONE, JsonUtil.asBigInteger(true));
		
		assertEquals( BigInteger.ONE, JsonUtil.asBigInteger(null,BigInteger.ONE));
		assertEquals( BigInteger.TEN, JsonUtil.asBigInteger(10,BigInteger.ONE));
		assertEquals( BigInteger.TEN, JsonUtil.asBigInteger("10",BigInteger.ONE));
		assertEquals( BigInteger.ONE, JsonUtil.asBigInteger("10bac",BigInteger.ONE));
		assertEquals( BigInteger.ONE, JsonUtil.asBigInteger(true,BigInteger.TEN));
	}	
	
	public void testAsBigDecimal() throws Exception {
		assertEquals( BigDecimal.ZERO, JsonUtil.asBigDecimal(null));
		assertEquals( BigDecimal.TEN, JsonUtil.asBigDecimal(10));
		assertEquals( BigDecimal.TEN, JsonUtil.asBigDecimal("10"));
		assertEquals( BigDecimal.ZERO, JsonUtil.asBigDecimal("10bac"));
		assertEquals( BigDecimal.ONE, JsonUtil.asBigDecimal(true));
		
		assertEquals( BigDecimal.ONE, JsonUtil.asBigDecimal(null,BigDecimal.ONE));
		assertEquals( BigDecimal.TEN, JsonUtil.asBigDecimal(10,BigDecimal.ONE));
		assertEquals( BigDecimal.TEN, JsonUtil.asBigDecimal("10",BigDecimal.ONE));
		assertEquals( BigDecimal.ONE, JsonUtil.asBigDecimal("10bac",BigDecimal.ONE));
		assertEquals( BigDecimal.ONE, JsonUtil.asBigDecimal(true,BigDecimal.TEN));
	}
	
	public void testAsBoolean() throws Exception {
		assertEquals( false, JsonUtil.asBoolean(null));
		assertEquals( false, JsonUtil.asBoolean(0));
		assertEquals( true, JsonUtil.asBoolean(1));
		assertEquals( false, JsonUtil.asBoolean(""));
		assertEquals( false, JsonUtil.asBoolean("0"));
		assertEquals( true, JsonUtil.asBoolean("1"));
		assertEquals( false, JsonUtil.asBoolean("FALSE"));
		assertEquals( true, JsonUtil.asBoolean("TRUE"));

		assertEquals( true, JsonUtil.asBoolean(null,true));
		assertEquals( false, JsonUtil.asBoolean(0,true));
		assertEquals( true, JsonUtil.asBoolean(1,true));
		assertEquals( false, JsonUtil.asBoolean("",true));
		assertEquals( false, JsonUtil.asBoolean("0",true));
		assertEquals( true, JsonUtil.asBoolean("1",true));
		assertEquals( false, JsonUtil.asBoolean("FALSE",true));
		assertEquals( true, JsonUtil.asBoolean("TRUE",true));
	}
	
	public void testAsString() throws Exception {
		assertEquals( "", JsonUtil.asString(null));
		assertEquals( "abc", JsonUtil.asString("abc"));
		assertEquals( "0", JsonUtil.asString(0));
		assertEquals( "1.2", JsonUtil.asString(1.2));
		assertEquals( "true", JsonUtil.asString(true));
		assertEquals( "false", JsonUtil.asString(false));
		
		assertEquals( "XYV", JsonUtil.asString(null,"XYV"));
	}
}
