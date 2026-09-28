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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class CreateTest extends ProjectTestCase {
	
	public void testCreate() throws Exception {
		JsonValues v_null = JsonValues.of((Object)null);
		JsonValues v_null1 = JsonValues.of((String)null);
		JsonValues v_null2 = JsonValues.of((Number)null);
		assertEquals(JsonValues.of(null),v_null);
		assertEquals(JsonValues.of(null),v_null1);
		assertEquals(JsonValues.of(null),v_null2);
		assertTrue(v_null.isNull());
		assertEquals("null",v_null.toString());
		
		JsonValues v_boolean = JsonValues.of(false);
		assertEquals(false,v_boolean.value());
		assertTrue(v_boolean.isBoolean());
		assertEquals("false",v_boolean.toString());
		
		JsonValues v_string = JsonValues.of("xyz");
		assertEquals("xyz",v_string.value());
		assertTrue(v_string.isString());
		assertEquals("\"xyz\"",v_string.toString());
		
		JsonValues v_stringNull = JsonValues.of((String)null);
		assertEquals(null,v_stringNull.value());
		
		JsonValues v_stringEmpty = JsonValues.of("");
		assertEquals("",v_stringEmpty.value());

		JsonValues v_int = JsonValues.of(1);
		assertEquals(1,v_int.value());
		assertTrue(v_int.isNumber());
		assertEquals("1",v_int.toString());

		JsonValues v_int0 = JsonValues.of(0);
		assertEquals(0,v_int0.value());

		JsonValues v_long = JsonValues.of(1L);
		assertEquals(1L,v_long.value());
		assertTrue(v_long.isNumber());
		assertEquals("1",v_long.toString());

		JsonValues v_long0 = JsonValues.of(0L);
		assertEquals(0L,v_long0.value());

		JsonValues v_double = JsonValues.of(1.1);
		assertEquals(1.1,v_double.value());
		assertTrue(v_double.isNumber());
		assertEquals("1.1",v_double.toString());

		JsonValues v_double0 = JsonValues.of(0.0);
		assertEquals(0.0,v_double0.value());

		JsonValues v_bigInteger = JsonValues.of(BigInteger.TEN);
		assertEquals(BigInteger.TEN,v_bigInteger.value());
		assertTrue(v_bigInteger.isNumber());
		assertEquals("10",v_bigInteger.toString());

		JsonValues v_bigIntegerNull = JsonValues.of((BigInteger)null);
		assertEquals(null,v_bigIntegerNull.value());

		JsonValues v_bigDecimal = JsonValues.of(BigDecimal.TEN);
		assertEquals(BigDecimal.TEN,v_bigDecimal.value());
		assertTrue(v_bigDecimal.isNumber());
		assertEquals("10",v_bigDecimal.toString());

		JsonValues v_bigDecimalNull = JsonValues.of((BigDecimal)null);
		assertEquals(null,v_bigDecimalNull.value());

		JsonValues v_object = JsonValues.of(JsonFactory.get().createObject());
		assertEquals(JsonFactory.get().createObject(),v_object.value());
		assertTrue(v_object.isContainer());
		assertTrue(v_object.isObject());
		assertEquals("{}",v_object.toString());
		
		JsonValues v_objectContainer = JsonValues.of((JsonContainer)JsonFactory.get().createObject());
		assertEquals(JsonFactory.get().createObject(),v_objectContainer.value());

		JsonValues v_objectNull = JsonValues.of((JsonObject)null);
		assertEquals(null,v_objectNull.value());

		JsonValues v_array = JsonValues.of(JsonFactory.get().createArray());
		assertEquals(JsonFactory.get().createArray(),v_array.value());
		assertTrue(v_array.isContainer());
		assertTrue(v_array.isArray());
		assertEquals("[]",v_array.toString());
		
		JsonValues v_arrayContainer = JsonValues.of((JsonContainer)JsonFactory.get().createArray());
		assertEquals(JsonFactory.get().createArray(),v_arrayContainer.value());

		JsonValues v_arrayNull = JsonValues.of((JsonObject)null);
		assertEquals(null,v_arrayNull.value());

		JsonValues v_containerNull = JsonValues.of((JsonContainer)null);
		assertEquals(null,v_containerNull.value());
	}
}
