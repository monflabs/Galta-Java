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

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class AccessPropertyTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
{
  "v_null": null,
  "v_int": 34,
  "v_long": 34343434343434,
  "v_double": 34.34,
  "v_bigInteger": 123456789123456789123456789,
  "v_bigDecimal": 4567892345678765478.5657654,
  "v_boolean": true,
  "v_string": "abc",
  "v_object": { "s": "xyz" },
  "v_array": ["def"],
}
""";

	JsonValues json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonValues.of(JsonObject.parse(JSON));
	}	
	
	public void testNull() throws Exception {
		JsonValues v_null = json.get("v_null");
		assertEquals(null,v_null.value());
		
		assertTrue(v_null.isNull());
		assertEquals(null,v_null.value());
	}
	
	public void testInt() throws Exception {
		JsonValues v_int = json.get("v_int");

		assertTrue(v_int.isNumber());
		assertEquals(34,v_int.value());
	}

	public void testLong() throws Exception {
		JsonValues v_long = json.get("v_long");
		
		assertTrue(v_long.isNumber());
		assertEquals(34343434343434L,v_long.value());
	}
	
	public void testDouble() throws Exception {
		JsonValues v_double = json.get("v_double");
		
		assertTrue(v_double.isNumber());
		assertEquals(34.34,v_double.value());
	}
	
	public void testBigInteger() throws Exception {
		JsonValues v_bigInteger = json.get("v_bigInteger");
		
		assertTrue(v_bigInteger.isNumber());
		assertEquals(new BigInteger("123456789123456789123456789"),v_bigInteger.value());
	}
	
	public void testBigDecimal() throws Exception {
		JsonValues v_bigDecimal = json.get("v_bigDecimal");
		
		assertTrue(v_bigDecimal.isNumber());
		assertEquals(new BigDecimal("4567892345678765478.5657654"),v_bigDecimal.value());
	}
	
	public void testBoolean() throws Exception {
		JsonValues v_boolean = json.get("v_boolean");
		
		assertTrue(v_boolean.isBoolean());
		assertEquals(true,v_boolean.value());
	}
	
	public void testString() throws Exception {
		JsonValues v_string = json.get("v_string");

		assertTrue(v_string.isString());
		assertEquals("abc",v_string.value());
	}
	
	public void testObject() throws Exception {
		JsonValues v_object = json.get("v_object");

		assertTrue(v_object.isContainer());
		assertTrue(v_object.isObject());
		support.assertJsonEquals(JsonObject.of("s","xyz"), v_object.value());
	}
	
	public void testArray() throws Exception {
		JsonValues v_array = json.get("v_array");

		assertTrue(v_array.isContainer());
		assertTrue(v_array.isArray());	
		support.assertJsonEquals(JsonArray.of("def"), v_array.value());
	}
}
