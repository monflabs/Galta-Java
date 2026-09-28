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
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class AccessIndexTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
[
  null,
  34,
  34343434343434,
  34.34,
  123456789123456789123456789,
  4567892345678765478.5657654,
  true,
  "abc",
  { "s": "xyz" },
  ["def"],
]
""";

	JsonValues jsona;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		jsona = JsonValues.of(JsonArray.parse(JSON));
	}

	public void testEquals() throws Exception {
		Set<Object> all = new HashSet<>(); 
		for( JsonValues v: jsona.flat() ) {
			all.add(v.value());
		}
		Set<Object> all2 = new HashSet<>(); 
		for( Object v: jsona.arrayValue().values() ) {
			all2.add(v);
		}
		assertEquals(all2,all);
	}

	public void testNullA() throws Exception {
		JsonValues v_null = jsona.get(0);

		assertTrue(v_null.isNull());
		assertEquals(null,v_null.value());
	}
	
	public void testIntA() {
		JsonValues v_int = jsona.get(1);

		assertTrue(v_int.isNumber());
		assertEquals(34,v_int.value());
	}

	public void testLongA() {
		JsonValues v_long = jsona.get(2);
		
		assertTrue(v_long.isNumber());
		assertEquals(34343434343434L,v_long.value());
	}
	
	public void testDoubleA() {
		JsonValues v_double = jsona.get(3);

		assertTrue(v_double.isNumber());
		assertEquals(34.34,v_double.value());
	}
	
	public void testBigIntegerA() {
		JsonValues v_bigInteger = jsona.get(4); 
		
		assertTrue(v_bigInteger.isNumber());
		assertEquals(new BigInteger("123456789123456789123456789"),v_bigInteger.value());
	}
	
	public void testBigDecimalA() {
		JsonValues v_bigDecimal = jsona.get(5);
		
		assertTrue(v_bigDecimal.isNumber());
		assertEquals(new BigDecimal("4567892345678765478.5657654"),v_bigDecimal.value());
	}
	
	public void testBooleanA() {
		JsonValues v_boolean = jsona.get(6);
		
		assertTrue(v_boolean.isBoolean());
		assertEquals(true,v_boolean.value());
	}
	
	public void testStringA() {
		JsonValues v_string = jsona.get(7);

		assertTrue(v_string.isString());
		assertEquals("abc",v_string.value());
	}
	
	public void testObject() throws Exception {
		JsonValues v_object = jsona.get(8);

		assertTrue(v_object.isContainer());
		assertTrue(v_object.isObject());
		support.assertJsonEquals(JsonObject.of("s","xyz"), v_object.value());
	}
	
	public void testArray() throws Exception {
		JsonValues v_array = jsona.get(9);

		assertTrue(v_array.isContainer());
		assertTrue(v_array.isArray());	
		support.assertJsonEquals(JsonArray.of("def"), v_array.value());
	}
}
