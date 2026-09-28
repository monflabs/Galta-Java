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
package tests.json.factory;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class NativeCheckTest extends ProjectTestCase {

	String JSON_SOURCE =
"""
{
	x: {
		m: null,
		s: "a string",
		n: 123.4,
		b: true,
		o: {
		  s1: "S1",
		  s2: "S2"
		},
		a: [
		  11,
		  12
		]
	}
}
"""			
;
	

	public void testNativeNull() throws Exception {
		Object[] vv = new Object[] {
			JsonFactory.get().toNativeJsonPrimitive(null),
			JsonFactory.get().toNativeNull(),
			
			JsonFactory.get().toNativeBoolean(null),
			JsonFactory.get().toNativeNumber(null),
			JsonFactory.get().toNativeByte(null),
			JsonFactory.get().toNativeShort(null),
			JsonFactory.get().toNativeInt(null),
			JsonFactory.get().toNativeLong(null),
			JsonFactory.get().toNativeFloat(null),
			JsonFactory.get().toNativeDouble(null),
			JsonFactory.get().toNativeBigInteger(null),
			JsonFactory.get().toNativeBigDecimal(null),
			JsonFactory.get().toNativeString(null),
			JsonFactory.get().toNativeObject(null),
			JsonFactory.get().toNativeArray(null)
		};

		for(int i=0; i<vv.length; i++) {
			Object v = vv[i];
			assertTrue (JsonFactory.get().isNativeNull(v));
			assertFalse(JsonFactory.get().isNativeBoolean(v));
			assertFalse(JsonFactory.get().isNativeNumber(v));
			assertFalse(JsonFactory.get().isNativeString(v));
			assertFalse(JsonFactory.get().isNativeContainer(v));
			assertFalse(JsonFactory.get().isNativeObject(v));
			assertFalse(JsonFactory.get().isNativeArray(v));		
		
			// Conversions...
			assertEquals( true, JsonFactory.get().asBoolean(v,true) );
			assertEquals( (byte)1, JsonFactory.get().asByte(v,(byte)1) );
			assertEquals( (short)1, JsonFactory.get().asShort(v,(short)1) );
			assertEquals( 1, JsonFactory.get().asInt(v,1) );
			assertEquals( 1L, JsonFactory.get().asLong(v,1L) );
			assertEquals( 1.0f, JsonFactory.get().asFloat(v,1.0f) );
			assertEquals( 1.0, JsonFactory.get().asDouble(v,1.0) );
			
			assertEquals( 1L, JsonFactory.get().asNumber(v,1L) );
			assertEquals( Byte.valueOf((byte)1), JsonFactory.get().asByteObject(v,(byte)1) );
			assertEquals( Short.valueOf((short)1), JsonFactory.get().asShortObject(v,(short)1) );
			assertEquals( Integer.valueOf(1), JsonFactory.get().asIntObject(v,1) );
			assertEquals( Long.valueOf(1L), JsonFactory.get().asLongObject(v,1L) );
			assertEquals( 1.0f, JsonFactory.get().asFloatObject(v,1.0f) );
			assertEquals( 1.0, JsonFactory.get().asDoubleObject(v,1.0) );
			assertEquals( BigInteger.ONE, JsonFactory.get().asBigInteger(v,BigInteger.ONE) );
			assertEquals( BigDecimal.ONE, JsonFactory.get().asBigDecimal(v,BigDecimal.ONE) );
			assertEquals( "1", JsonFactory.get().asString(v,"1") );
			assertEquals( JsonFactory.get().createObject(), JsonFactory.get().asObject(v,JsonFactory.get().createObject()) );
			assertEquals( JsonFactory.get().createArray(), JsonFactory.get().asArray(v,JsonFactory.get().createArray()) );
	
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v) );
			
			assertThrows( JsonException.class, () -> JsonFactory.get().asBooleanObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v) );
		}
	}

	public void testNativeBoolean() throws Exception {
		Object[] vv = new Object[] {
			JsonFactory.get().toNativeJsonPrimitive(true),
			JsonFactory.get().toNativeBoolean(true),
			JsonFactory.get().toNativeBoolean(Boolean.TRUE)
		};
		for(int i=0; i<vv.length; i++) {
			Object v = vv[i];
			assertFalse(JsonFactory.get().isNativeNull(v));
			assertTrue (JsonFactory.get().isNativeBoolean(v));
			assertFalse(JsonFactory.get().isNativeNumber(v));
			assertFalse(JsonFactory.get().isNativeString(v));
			assertFalse(JsonFactory.get().isNativeContainer(v));
			assertFalse(JsonFactory.get().isNativeObject(v));
			assertFalse(JsonFactory.get().isNativeArray(v));
		
			// Conversions...
			assertEquals( true, JsonFactory.get().asBoolean(v) );
			assertEquals( true, JsonFactory.get().asBoolean(v,false) );
			
			assertEquals( Boolean.TRUE, JsonFactory.get().asBooleanObject(v) );
			assertEquals( Boolean.TRUE, JsonFactory.get().asBooleanObject(v,false) );

			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v,1.0) );

			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v,1.0) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v,BigInteger.ONE) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v,BigDecimal.ONE) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v,"1") );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v,JsonFactory.get().createObject()) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v,JsonFactory.get().createArray()) );
		}
	}

	public void testNativeNumber() throws Exception {
		Object[] vv = new Object[] {
			JsonFactory.get().toNativeJsonPrimitive((byte)0),
			JsonFactory.get().toNativeJsonPrimitive((short)0),
			JsonFactory.get().toNativeJsonPrimitive(0),
			JsonFactory.get().toNativeJsonPrimitive(0L),
			JsonFactory.get().toNativeJsonPrimitive(0.0f),
			JsonFactory.get().toNativeJsonPrimitive(0.0),
			JsonFactory.get().toNativeJsonPrimitive(BigInteger.ZERO),
			JsonFactory.get().toNativeJsonPrimitive(BigDecimal.ZERO),

			JsonFactory.get().toNativeByte(Byte.valueOf((byte)0)),
			JsonFactory.get().toNativeShort(Short.valueOf((short)0)),
			JsonFactory.get().toNativeInt(Integer.valueOf(0)),
			JsonFactory.get().toNativeLong(Long.valueOf(0L)),
			JsonFactory.get().toNativeFloat(Float.valueOf(0.0f)),
			JsonFactory.get().toNativeDouble(Double.valueOf(0.0)),
			
			JsonFactory.get().toNativeNumber(0),
			JsonFactory.get().toNativeByte((byte)0),
			JsonFactory.get().toNativeShort((short)0),
			JsonFactory.get().toNativeInt(0),
			JsonFactory.get().toNativeLong(0L),
			JsonFactory.get().toNativeFloat(0.0f),
			JsonFactory.get().toNativeDouble(0.0),
			JsonFactory.get().toNativeBigInteger(BigInteger.ZERO),
			JsonFactory.get().toNativeBigDecimal(BigDecimal.ZERO)
		};
		for(int i=0; i<vv.length; i++) {
			Object v = vv[i];
			assertFalse(JsonFactory.get().isNativeNull(v));
			assertFalse(JsonFactory.get().isNativeBoolean(v));
			assertTrue (JsonFactory.get().isNativeNumber(v));
			assertFalse(JsonFactory.get().isNativeString(v));
			assertFalse(JsonFactory.get().isNativeContainer(v));
			assertFalse(JsonFactory.get().isNativeObject(v));
			assertFalse(JsonFactory.get().isNativeArray(v));		
			
			// Conversions...
			assertEquals( (byte)0, JsonFactory.get().asByte(v) );
			assertEquals( (byte)0, JsonFactory.get().asByte(v,(byte)1) );
			assertEquals( (short)0, JsonFactory.get().asShort(v) );
			assertEquals( (short)0, JsonFactory.get().asShort(v,(short)1) );
			assertEquals( 0, JsonFactory.get().asInt(v) );
			assertEquals( 0, JsonFactory.get().asInt(v,1) );
			assertEquals( 0L, JsonFactory.get().asLong(v) );
			assertEquals( 0L, JsonFactory.get().asLong(v,1L) );
			assertEquals( 0.0f, JsonFactory.get().asFloat(v) );
			assertEquals( 0.0f, JsonFactory.get().asFloat(v,1.0f) );
			assertEquals( 0.0, JsonFactory.get().asDouble(v) );
			assertEquals( 0.0, JsonFactory.get().asDouble(v,1.0) );

			assertEquals( 0, JsonFactory.get().asNumber(v).intValue() );
			assertEquals( 0, JsonFactory.get().asNumber(v,1L).intValue() );
			assertEquals( Byte.valueOf((byte)0), JsonFactory.get().asByteObject(v) );
			assertEquals( Byte.valueOf((byte)0), JsonFactory.get().asByteObject(v,(byte)1) );
			assertEquals( Short.valueOf((short)0), JsonFactory.get().asShortObject(v) );
			assertEquals( Short.valueOf((short)0), JsonFactory.get().asShortObject(v,(short)1) );
			assertEquals( Integer.valueOf(0), JsonFactory.get().asIntObject(v) );
			assertEquals( Integer.valueOf(0), JsonFactory.get().asIntObject(v,1) );
			assertEquals( Long.valueOf(0L), JsonFactory.get().asLongObject(v) );
			assertEquals( Long.valueOf(0L), JsonFactory.get().asLongObject(v,1L) );
			assertEquals( 0.0f, JsonFactory.get().asFloatObject(v) );
			assertEquals( 0.0f, JsonFactory.get().asFloatObject(v,1.0f) );
			assertEquals( 0.0, JsonFactory.get().asDoubleObject(v) );
			assertEquals( 0.0, JsonFactory.get().asDoubleObject(v,1.0) );
			// equals() fails comparing 0 to 0.0 with BigDecimal, should use compareTo
			assertEquals( 0, BigInteger.ZERO.compareTo(JsonFactory.get().asBigInteger(v)) );
			assertEquals( 0, BigInteger.ZERO.compareTo(JsonFactory.get().asBigInteger(v,BigInteger.ONE)) );
			assertEquals( 0, BigDecimal.ZERO.compareTo(JsonFactory.get().asBigDecimal(v)) );
			assertEquals( 0, BigDecimal.ZERO.compareTo(JsonFactory.get().asBigDecimal(v,BigDecimal.ONE)) );

			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v,true) );
			
			assertThrows( JsonException.class, () -> JsonFactory.get().asBooleanObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBooleanObject(v,true) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v,"1") );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v,JsonFactory.get().createObject()) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v,JsonFactory.get().createArray()) );
		}
		
	}

	public void testNativeString() throws Exception {
		Object[] vv = new Object[] {
			JsonFactory.get().toNativeJsonPrimitive("xyz"),
			JsonFactory.get().toNativeString("xyz")
		};
		for(int i=0; i<vv.length; i++) {
			Object v = vv[i];
			assertFalse(JsonFactory.get().isNativeNull(v));
			assertFalse(JsonFactory.get().isNativeBoolean(v));
			assertFalse(JsonFactory.get().isNativeNumber(v));
			assertTrue (JsonFactory.get().isNativeString(v));
			assertFalse(JsonFactory.get().isNativeContainer(v));
			assertFalse(JsonFactory.get().isNativeObject(v));
			assertFalse(JsonFactory.get().isNativeArray(v));		
			
			// Conversions...
			assertEquals( "xyz", JsonFactory.get().asString(v) );
			assertEquals( "xyz", JsonFactory.get().asString(v,"1") );
	
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v,true) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v,1.0) );

			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v,1.0) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v,BigInteger.ONE) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v,BigDecimal.ONE) );
//			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v) );
//			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v,"1") );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v,JsonFactory.get().createObject()) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v,JsonFactory.get().createArray()) );
		}
	}

	public void testNativeObject() throws Exception {
		Object[] vv = new Object[] {
			JsonFactory.get().toNativeJsonPrimitive(JsonFactory.get().createObject().put("a", 1)),
			JsonFactory.get().toNativeObject(JsonFactory.get().createObject().put("a", 1))
		};
		for(int i=0; i<vv.length; i++) {
			Object v = vv[i];
			assertFalse(JsonFactory.get().isNativeNull(v));
			assertFalse(JsonFactory.get().isNativeBoolean(v));
			assertFalse(JsonFactory.get().isNativeNumber(v));
			assertFalse(JsonFactory.get().isNativeString(v));
			assertTrue (JsonFactory.get().isNativeContainer(v));
			assertTrue (JsonFactory.get().isNativeObject(v));
			assertFalse(JsonFactory.get().isNativeArray(v));		
			
			// Conversions...
			assertEquals( JsonFactory.get().createObject().put("a", 1), JsonFactory.get().asObject(v) );
			assertEquals( JsonFactory.get().createObject().put("a", 1), JsonFactory.get().asObject(v,JsonFactory.get().createObject().put("a", 2)) );
			
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v,true) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v,1.0) );
			
			assertThrows( JsonException.class, () -> JsonFactory.get().asBooleanObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBooleanObject(v,true) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v,1.0) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v,BigInteger.ONE) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v,BigDecimal.ONE) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v,"1") );
//			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v) );
//			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v,JsonFactory.get().createObject()) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v,JsonFactory.get().createArray()) );
		}
	}

	public void testNativeArray() throws Exception {
		Object[] vv = new Object[] {
			JsonFactory.get().toNativeJsonPrimitive(JsonFactory.get().createArray().add(1)),
			JsonFactory.get().toNativeArray(JsonFactory.get().createArray().add(1))
		};
		for(int i=0; i<vv.length; i++) {
			Object v = vv[i];
			assertFalse(JsonFactory.get().isNativeNull(v));
			assertFalse(JsonFactory.get().isNativeBoolean(v));
			assertFalse(JsonFactory.get().isNativeNumber(v));
			assertFalse(JsonFactory.get().isNativeString(v));
			assertTrue (JsonFactory.get().isNativeContainer(v));
			assertFalse(JsonFactory.get().isNativeObject(v));
			assertTrue (JsonFactory.get().isNativeArray(v));		
			
			// Conversions...
			assertEquals( JsonFactory.get().createArray().add(1), JsonFactory.get().asArray(v) );
			assertEquals( JsonFactory.get().createArray().add(1), JsonFactory.get().asArray(v,JsonFactory.get().createArray().add(2)) );
			
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBoolean(v,true) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByte(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShort(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asInt(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLong(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloat(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDouble(v,1.0) );
			
			assertThrows( JsonException.class, () -> JsonFactory.get().asBooleanObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBooleanObject(v,true) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asNumber(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asByteObject(v,(byte)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asShortObject(v,(short)1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asIntObject(v,1) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asLongObject(v,1L) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asFloatObject(v,1.0f) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asDoubleObject(v,1.0) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigInteger(v,BigInteger.ONE) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asBigDecimal(v,BigDecimal.ONE) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asString(v,"1") );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v) );
			assertThrows( JsonException.class, () -> JsonFactory.get().asObject(v,JsonFactory.get().createObject()) );
//			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v) );
//			assertThrows( JsonException.class, () -> JsonFactory.get().asArray(v,JsonFactory.get().createArray()) );
		}
	}

	public void testNativeEquals() throws Exception {
		JsonObject o1 = (JsonObject)JsonFactory.get().parse(JSON_SOURCE);
		JsonObject o2 = (JsonObject)JsonFactory.get().parse(JSON_SOURCE);
		assertTrue( JsonFactory.get().nativeEquals(JsonFactory.get().toNativeJsonPrimitive(o1), JsonFactory.get().toNativeJsonPrimitive(o2)) );
		
		o1.getObject("x").put("n",456);
		assertFalse( JsonFactory.get().nativeEquals(JsonFactory.get().toNativeJsonPrimitive(o1), JsonFactory.get().toNativeJsonPrimitive(o2)) );
	}
	
}
