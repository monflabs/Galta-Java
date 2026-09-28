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

public class CompareGtTest extends ProjectTestCase {
	
	
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
	
	public void testGt() throws Exception {
		assertFalse(json.get("v_null").gt((byte)32));
		assertFalse(json.get("v_null").gt((short)32));
		assertFalse(json.get("v_null").gt(32));
		assertFalse(json.get("v_null").gt(32L));
		assertFalse(json.get("v_null").gt(32.0f));
		assertFalse(json.get("v_null").gt(32.0));
		assertFalse(json.get("v_null").gt(new BigInteger("32")));
		assertFalse(json.get("v_null").gt(new BigDecimal("32")));
		assertFalse(json.get("v_null").gt("abc"));

		assertTrue (json.get("v_int").gt((byte)32));
		assertFalse(json.get("v_int").gt((byte)33));
		assertFalse(json.get("v_int").gt((byte)34));
		assertTrue (json.get("v_int").gt((short)32));
		assertFalse(json.get("v_int").gt((short)33));
		assertFalse(json.get("v_int").gt((short)34));
		assertTrue (json.get("v_int").gt(32));
		assertFalse(json.get("v_int").gt(33));
		assertFalse(json.get("v_int").gt(34));
		assertTrue (json.get("v_int").gt(32L));
		assertFalse(json.get("v_int").gt(33L));
		assertFalse(json.get("v_int").gt(34L));
		assertTrue (json.get("v_int").gt(32.0f));
		assertFalse(json.get("v_int").gt(33.0f));
		assertFalse(json.get("v_int").gt(34.0f));
		assertTrue (json.get("v_int").gt(32.0));
		assertFalse(json.get("v_int").gt(33.0));
		assertFalse(json.get("v_int").gt(34.0));
		assertTrue(json.get("v_int").gt(new BigInteger("32")));
		assertFalse(json.get("v_int").gt(new BigInteger("33")));
		assertFalse(json.get("v_int").gt(new BigInteger("34")));
		assertTrue(json.get("v_int").gt(new BigDecimal("32")));
		assertFalse(json.get("v_int").gt(new BigDecimal("33")));
		assertFalse(json.get("v_int").gt(new BigDecimal("34")));
		assertFalse(json.get("v_int").gt("abc"));

		assertTrue (json.get("v_double").gt((byte)32));
		assertFalse(json.get("v_double").gt((byte)33));
		assertFalse(json.get("v_double").gt((byte)34));
		assertTrue (json.get("v_double").gt((short)32));
		assertFalse(json.get("v_double").gt((short)33));
		assertFalse(json.get("v_double").gt((short)34));
		assertTrue (json.get("v_double").gt(32));
		assertFalse(json.get("v_double").gt(33));
		assertFalse(json.get("v_double").gt(34));
		assertTrue (json.get("v_double").gt(32L));
		assertFalse(json.get("v_double").gt(33L));
		assertFalse(json.get("v_double").gt(34L));
		assertTrue (json.get("v_double").gt(32.0f));
		assertFalse(json.get("v_double").gt(33.0f));
		assertFalse(json.get("v_double").gt(34.0f));
		assertTrue (json.get("v_double").gt(32.0));
		assertFalse(json.get("v_double").gt(33.0));
		assertFalse(json.get("v_double").gt(34.0));
		assertTrue(json.get("v_double").gt(new BigInteger("32")));
		assertFalse(json.get("v_double").gt(new BigInteger("33")));
		assertFalse(json.get("v_double").gt(new BigInteger("34")));
		assertTrue(json.get("v_double").gt(new BigDecimal("32")));
		assertFalse(json.get("v_double").gt(new BigDecimal("33")));
		assertFalse(json.get("v_double").gt(new BigDecimal("34")));
		assertFalse(json.get("v_double").gt("abc"));
		
		assertFalse(json.get("v_boolean").gt((byte)32));
		assertFalse(json.get("v_boolean").gt((short)32));
		assertFalse(json.get("v_boolean").gt(32));
		assertFalse(json.get("v_boolean").gt(32L));
		assertFalse(json.get("v_boolean").gt(32.0f));
		assertFalse(json.get("v_boolean").gt(32.0));
		assertFalse(json.get("v_boolean").gt(new BigInteger("32")));
		assertFalse(json.get("v_boolean").gt(new BigDecimal("32")));
		assertFalse(json.get("v_boolean").gt("abc"));
		
		assertFalse(json.get("v_string").gt((byte)32));
		assertFalse(json.get("v_string").gt((short)32));
		assertFalse(json.get("v_string").gt(32));
		assertFalse(json.get("v_string").gt(32L));
		assertFalse(json.get("v_string").gt(32.0f));
		assertFalse(json.get("v_string").gt(32.0));
		assertFalse(json.get("v_string").gt(new BigInteger("32")));
		assertFalse(json.get("v_string").gt(new BigDecimal("32")));
		assertTrue(json.get("v_string").gt("aac"));
		assertFalse(json.get("v_string").gt("abc"));
		assertFalse(json.get("v_string").gt("abd"));
	}
	
	public void testGte() throws Exception {
		assertFalse(json.get("v_null").gte((byte)32));
		assertFalse(json.get("v_null").gte((short)32));
		assertFalse(json.get("v_null").gte(32));
		assertFalse(json.get("v_null").gte(32L));
		assertFalse(json.get("v_null").gte(32.0f));
		assertFalse(json.get("v_null").gte(32.0));
		assertFalse(json.get("v_null").gte(new BigInteger("32")));
		assertFalse(json.get("v_null").gte(new BigDecimal("32")));
		assertFalse(json.get("v_null").gte("abc"));

		assertTrue (json.get("v_int").gte((byte)32));
		assertTrue(json.get("v_int").gte((byte)33));
		assertFalse(json.get("v_int").gte((byte)34));
		assertTrue (json.get("v_int").gte((short)32));
		assertTrue(json.get("v_int").gte((short)33));
		assertFalse(json.get("v_int").gte((short)34));
		assertTrue (json.get("v_int").gte(32));
		assertTrue(json.get("v_int").gte(33));
		assertFalse(json.get("v_int").gte(34));
		assertTrue (json.get("v_int").gte(32L));
		assertTrue(json.get("v_int").gte(33L));
		assertFalse(json.get("v_int").gte(34L));
		assertTrue (json.get("v_int").gte(32.0f));
		assertTrue(json.get("v_int").gte(33.0f));
		assertFalse(json.get("v_int").gte(34.0f));
		assertTrue (json.get("v_int").gte(32.0));
		assertTrue(json.get("v_int").gte(33.0));
		assertFalse(json.get("v_int").gte(34.0));
		assertTrue(json.get("v_int").gte(new BigInteger("32")));
		assertTrue(json.get("v_int").gte(new BigInteger("33")));
		assertFalse(json.get("v_int").gte(new BigInteger("34")));
		assertTrue(json.get("v_int").gte(new BigDecimal("32")));
		assertTrue(json.get("v_int").gte(new BigDecimal("33")));
		assertFalse(json.get("v_int").gte(new BigDecimal("34")));
		assertFalse(json.get("v_int").gte("abc"));

		assertTrue (json.get("v_double").gte((byte)32));
		assertTrue(json.get("v_double").gte((byte)33));
		assertFalse(json.get("v_double").gte((byte)34));
		assertTrue (json.get("v_double").gte((short)32));
		assertTrue(json.get("v_double").gte((short)33));
		assertFalse(json.get("v_double").gte((short)34));
		assertTrue (json.get("v_double").gte(32));
		assertTrue(json.get("v_double").gte(33));
		assertFalse(json.get("v_double").gte(34));
		assertTrue (json.get("v_double").gte(32L));
		assertTrue(json.get("v_double").gte(33L));
		assertFalse(json.get("v_double").gte(34L));
		assertTrue (json.get("v_double").gte(32.0f));
		assertTrue(json.get("v_double").gte(33.0f));
		assertFalse(json.get("v_double").gte(34.0f));
		assertTrue (json.get("v_double").gte(32.0));
		assertTrue(json.get("v_double").gte(33.0));
		assertFalse(json.get("v_double").gte(34.0));
		assertTrue(json.get("v_double").gte(new BigInteger("32")));
		assertTrue(json.get("v_double").gte(new BigInteger("33")));
		assertFalse(json.get("v_double").gte(new BigInteger("34")));
		assertTrue(json.get("v_double").gte(new BigDecimal("32")));
		assertTrue(json.get("v_double").gte(new BigDecimal("33")));
		assertFalse(json.get("v_double").gte(new BigDecimal("34")));
		assertFalse(json.get("v_double").gte("abc"));
		
		assertFalse(json.get("v_boolean").gte((byte)32));
		assertFalse(json.get("v_boolean").gte((short)32));
		assertFalse(json.get("v_boolean").gte(32));
		assertFalse(json.get("v_boolean").gte(32L));
		assertFalse(json.get("v_boolean").gte(32.0f));
		assertFalse(json.get("v_boolean").gte(32.0));
		assertFalse(json.get("v_boolean").gte(new BigInteger("32")));
		assertFalse(json.get("v_boolean").gte(new BigDecimal("32")));
		assertFalse(json.get("v_boolean").gte("abc"));
		
		assertFalse(json.get("v_string").gte((byte)32));
		assertFalse(json.get("v_string").gte((short)32));
		assertFalse(json.get("v_string").gte(32));
		assertFalse(json.get("v_string").gte(32L));
		assertFalse(json.get("v_string").gte(32.0f));
		assertFalse(json.get("v_string").gte(32.0));
		assertFalse(json.get("v_string").gte(new BigInteger("32")));
		assertFalse(json.get("v_string").gte(new BigDecimal("32")));
		assertTrue(json.get("v_string").gte("aac"));
		assertTrue(json.get("v_string").gte("abc"));
		assertFalse(json.get("v_string").gte("abd"));
	}
}
