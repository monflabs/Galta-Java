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
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class CompareLtTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
{
  "v_null": null,
  "v_int": 33,
  "v_double": 33.0,
  "v_boolean": true,
  "v_string": "abc",
}
""";

	JsonValues json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonValues.of(JsonObject.parse(JSON));
	}	
	
	public void testLt() throws Exception {
		assertFalse(json.get("v_null").lt(32));
		assertFalse(json.get("v_null").lt(32));
		assertFalse(json.get("v_null").lt(32));
		assertFalse(json.get("v_null").lt(new BigInteger("32")));
		assertFalse(json.get("v_null").lt(new BigDecimal("32")));
		assertFalse(json.get("v_null").lt("abc"));

		assertFalse (json.get("v_int").lt((byte)32));
		assertFalse(json.get("v_int").lt((byte)33));
		assertTrue(json.get("v_int").lt((byte)34));
		assertFalse (json.get("v_int").lt((short)32));
		assertFalse(json.get("v_int").lt((short)33));
		assertTrue(json.get("v_int").lt((short)34));
		assertFalse (json.get("v_int").lt(32));
		assertFalse(json.get("v_int").lt(33));
		assertTrue(json.get("v_int").lt(34));
		assertFalse (json.get("v_int").lt(32L));
		assertFalse(json.get("v_int").lt(33L));
		assertTrue(json.get("v_int").lt(34L));
		assertFalse (json.get("v_int").lt(32.0f));
		assertFalse(json.get("v_int").lt(33.0f));
		assertTrue(json.get("v_int").lt(34.0f));
		assertFalse (json.get("v_int").lt(32.0));
		assertFalse(json.get("v_int").lt(33.0));
		assertTrue(json.get("v_int").lt(34.0));
		assertFalse(json.get("v_int").lt(new BigInteger("32")));
		assertFalse(json.get("v_int").lt(new BigInteger("33")));
		assertTrue(json.get("v_int").lt(new BigInteger("34")));
		assertFalse(json.get("v_int").lt(new BigDecimal("32")));
		assertFalse(json.get("v_int").lt(new BigDecimal("33")));
		assertTrue(json.get("v_int").lt(new BigDecimal("34")));
		assertFalse(json.get("v_int").lt("abc"));

		assertFalse (json.get("v_double").lt((byte)32));
		assertFalse(json.get("v_double").lt((byte)33));
		assertTrue(json.get("v_double").lt((byte)34));
		assertFalse (json.get("v_double").lt((short)32));
		assertFalse(json.get("v_double").lt((short)33));
		assertTrue(json.get("v_double").lt((short)34));
		assertFalse (json.get("v_double").lt(32));
		assertFalse(json.get("v_double").lt(33));
		assertTrue(json.get("v_double").lt(34));
		assertFalse (json.get("v_double").lt(32L));
		assertFalse(json.get("v_double").lt(33L));
		assertTrue(json.get("v_double").lt(34L));
		assertFalse (json.get("v_double").lt(32.0f));
		assertFalse(json.get("v_double").lt(33.0f));
		assertTrue(json.get("v_double").lt(34.0f));
		assertFalse (json.get("v_double").lt(32.0));
		assertFalse(json.get("v_double").lt(33.0));
		assertTrue(json.get("v_double").lt(34.0));
		assertFalse(json.get("v_double").lt(new BigInteger("32")));
		assertFalse(json.get("v_double").lt(new BigInteger("33")));
		assertTrue(json.get("v_double").lt(new BigInteger("34")));
		assertFalse(json.get("v_double").lt(new BigDecimal("32")));
		assertFalse(json.get("v_double").lt(new BigDecimal("33")));
		assertTrue(json.get("v_double").lt(new BigDecimal("34")));
		assertFalse(json.get("v_double").lt("abc"));
		
		assertFalse(json.get("v_boolean").lt((byte)32));
		assertFalse(json.get("v_boolean").lt((short)32));
		assertFalse(json.get("v_boolean").lt(32));
		assertFalse(json.get("v_boolean").lt(32L));
		assertFalse(json.get("v_boolean").lt(32.0f));
		assertFalse(json.get("v_boolean").lt(32.0));
		assertFalse(json.get("v_boolean").lt(new BigInteger("32")));
		assertFalse(json.get("v_boolean").lt(new BigDecimal("32")));
		assertFalse(json.get("v_boolean").lt("abc"));
		
		assertFalse(json.get("v_string").lt((byte)32));
		assertFalse(json.get("v_string").lt((short)32));
		assertFalse(json.get("v_string").lt(32));
		assertFalse(json.get("v_string").lt(32L));
		assertFalse(json.get("v_string").lt(32.0f));
		assertFalse(json.get("v_string").lt(32.0));
		assertFalse(json.get("v_string").lt(new BigInteger("32")));
		assertFalse(json.get("v_string").lt(new BigDecimal("32")));
		assertFalse(json.get("v_string").lt("aac"));
		assertFalse(json.get("v_string").lt("abc"));
		assertTrue(json.get("v_string").lt("abd"));
	}
	
	public void testLte() throws Exception {
		assertFalse(json.get("v_null").lte((byte)32));
		assertFalse(json.get("v_null").lte((short)32));
		assertFalse(json.get("v_null").lte(32));
		assertFalse(json.get("v_null").lte(32L));
		assertFalse(json.get("v_null").lte(32.0f));
		assertFalse(json.get("v_null").lte(32.0));
		assertFalse(json.get("v_null").lte(new BigInteger("32")));
		assertFalse(json.get("v_null").lte(new BigDecimal("32")));
		assertFalse(json.get("v_null").lte("abc"));

		assertFalse (json.get("v_int").lte((byte)32));
		assertTrue(json.get("v_int").lte((byte)33));
		assertTrue(json.get("v_int").lte((byte)34));
		assertFalse (json.get("v_int").lte((short)32));
		assertTrue(json.get("v_int").lte((short)33));
		assertTrue(json.get("v_int").lte((short)34));
		assertFalse (json.get("v_int").lte(32));
		assertTrue(json.get("v_int").lte(33));
		assertTrue(json.get("v_int").lte(34));
		assertFalse (json.get("v_int").lte(32L));
		assertTrue(json.get("v_int").lte(33L));
		assertTrue(json.get("v_int").lte(34L));
		assertFalse (json.get("v_int").lte(32.0f));
		assertTrue(json.get("v_int").lte(33.0f));
		assertTrue(json.get("v_int").lte(34.0f));
		assertFalse (json.get("v_int").lte(32.0));
		assertTrue(json.get("v_int").lte(33.0));
		assertTrue(json.get("v_int").lte(34.0));
		assertFalse(json.get("v_int").lte(new BigInteger("32")));
		assertTrue(json.get("v_int").lte(new BigInteger("33")));
		assertTrue(json.get("v_int").lte(new BigInteger("34")));
		assertFalse(json.get("v_int").lte(new BigDecimal("32")));
		assertTrue(json.get("v_int").lte(new BigDecimal("33")));
		assertTrue(json.get("v_int").lte(new BigDecimal("34")));
		assertFalse(json.get("v_int").lte("abc"));

		assertFalse (json.get("v_double").lte((byte)32));
		assertTrue(json.get("v_double").lte((byte)33));
		assertTrue(json.get("v_double").lte((byte)34));
		assertFalse (json.get("v_double").lte((short)32));
		assertTrue(json.get("v_double").lte((short)33));
		assertTrue(json.get("v_double").lte((short)34));
		assertFalse (json.get("v_double").lte(32));
		assertTrue(json.get("v_double").lte(33));
		assertTrue(json.get("v_double").lte(34));
		assertFalse (json.get("v_double").lte(32L));
		assertTrue(json.get("v_double").lte(33L));
		assertTrue(json.get("v_double").lte(34L));
		assertFalse (json.get("v_double").lte(32.0f));
		assertTrue(json.get("v_double").lte(33.0f));
		assertTrue(json.get("v_double").lte(34.0f));
		assertFalse (json.get("v_double").lte(32.0));
		assertTrue(json.get("v_double").lte(33.0));
		assertTrue(json.get("v_double").lte(34.0));
		assertFalse(json.get("v_double").lte(new BigInteger("32")));
		assertTrue(json.get("v_double").lte(new BigInteger("33")));
		assertTrue(json.get("v_double").lte(new BigInteger("34")));
		assertFalse(json.get("v_double").lte(new BigDecimal("32")));
		assertTrue(json.get("v_double").lte(new BigDecimal("33")));
		assertTrue(json.get("v_double").lte(new BigDecimal("34")));
		assertFalse(json.get("v_double").lte("abc"));
		
		assertFalse(json.get("v_boolean").lte((byte)32));
		assertFalse(json.get("v_boolean").lte((short)32));
		assertFalse(json.get("v_boolean").lte(32));
		assertFalse(json.get("v_boolean").lte(32L));
		assertFalse(json.get("v_boolean").lte(32.0f));
		assertFalse(json.get("v_boolean").lte(32.0));
		assertFalse(json.get("v_boolean").lte(new BigInteger("32")));
		assertFalse(json.get("v_boolean").lte(new BigDecimal("32")));
		assertFalse(json.get("v_boolean").lte("abc"));
		
		assertFalse(json.get("v_string").lte((byte)32));
		assertFalse(json.get("v_string").lte((short)32));
		assertFalse(json.get("v_string").lte(32));
		assertFalse(json.get("v_string").lte(32L));
		assertFalse(json.get("v_string").lte(32.0f));
		assertFalse(json.get("v_string").lte(32.0));
		assertFalse(json.get("v_string").lte(new BigInteger("32")));
		assertFalse(json.get("v_string").lte(new BigDecimal("32")));
		assertFalse(json.get("v_string").lte("aac"));
		assertTrue(json.get("v_string").lte("abc"));
		assertTrue(json.get("v_string").lte("abd"));
	}
}
