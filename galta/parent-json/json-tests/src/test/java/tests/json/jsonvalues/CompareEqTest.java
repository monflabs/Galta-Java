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

public class CompareEqTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
{
  "v_null": null,
  "v_int": 33,
  "v_long": 34343434343434,
  "v_double": 34.34,
  "v_bigInteger": 123456789123456789123456789,
  "v_bigDecimal": 4567892345678765478.5657654,
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
	
	public void testEq() throws Exception {
		assertFalse(json.get("v_null").eq((byte)33));
		assertFalse(json.get("v_null").eq((short)33));
		assertFalse(json.get("v_null").eq(33));
		assertFalse(json.get("v_null").eq(34343434343434L));
		assertFalse(json.get("v_null").eq(34.34f));
		assertFalse(json.get("v_null").eq(34.34));
		assertFalse(json.get("v_null").eq(new BigInteger("123456789123456789123456789")));
		assertFalse(json.get("v_null").eq(new BigDecimal("4567892345678765478.5657654")));
		assertFalse(json.get("v_null").eq(true));
		assertFalse(json.get("v_null").eq("abc"));

		assertTrue (json.get("v_int").eq((byte)33));
		assertTrue (json.get("v_int").eq((short)33));
		assertTrue (json.get("v_int").eq(33));
		assertTrue(json.get("v_int").eq(33L));
		assertTrue(json.get("v_int").eq(33.0f));
		assertTrue(json.get("v_int").eq(33.0));
		assertTrue(json.get("v_int").eq(new BigInteger("33")));
		assertTrue(json.get("v_int").eq(new BigDecimal("33")));
		assertFalse(json.get("v_int").eq(34343434343434L));
		assertFalse(json.get("v_int").eq(34.34f));
		assertFalse(json.get("v_int").eq(34.34));
		assertFalse(json.get("v_int").eq(new BigInteger("123456789123456789123456789")));
		assertFalse(json.get("v_int").eq(new BigDecimal("4567892345678765478.5657654")));
		assertFalse(json.get("v_int").eq(true));
		assertFalse(json.get("v_int").eq("abc"));
		assertTrue (json.get("v_int").eq(33));

		assertFalse(json.get("v_long").eq((byte)33));
		assertFalse(json.get("v_long").eq((short)33));
		assertFalse(json.get("v_long").eq(33));
		assertFalse(json.get("v_long").eq(33333333333333L));
		assertFalse(json.get("v_long").eq(34.34f));
		assertFalse(json.get("v_long").eq(34.34));
		assertFalse(json.get("v_long").eq(new BigInteger("123456789123456789123456789")));
		assertFalse(json.get("v_long").eq(new BigDecimal("4567892345678765478.5657654")));
		assertFalse(json.get("v_long").eq(true));
		assertFalse(json.get("v_long").eq("abc"));
		// Exact comparisons: no truncation of the value to the argument's type
		assertFalse(json.get("v_long").eq(875844618));  // the value truncated to an int
		assertTrue(json.get("v_long").eq(34343434343434L));
		assertFalse(json.get("v_long").eq(34343434343434.0f)); // a float can't hold that value
		assertTrue(json.get("v_long").eq(34343434343434.0));
		assertTrue(json.get("v_long").eq(new BigInteger("34343434343434")));
		assertTrue(json.get("v_long").eq(new BigDecimal("34343434343434")));

		assertFalse(json.get("v_double").eq((byte)33));
		assertFalse(json.get("v_double").eq((short)33));
		assertFalse(json.get("v_double").eq(33));
		assertFalse(json.get("v_double").eq(34343434343434L));
		assertFalse(json.get("v_double").eq(3333.0f));
		assertFalse(json.get("v_double").eq(3333.0));
		assertFalse(json.get("v_double").eq(new BigInteger("123456789123456789123456789")));
		assertFalse(json.get("v_double").eq(new BigDecimal("4567892345678765478.5657654")));
		assertFalse(json.get("v_double").eq(true));
		assertFalse(json.get("v_double").eq("abc"));
		// 34.34 is not 34
		assertFalse(json.get("v_double").eq((byte)34));
		assertFalse(json.get("v_double").eq((short)34));
		assertFalse(json.get("v_double").eq(34));
		assertFalse(json.get("v_double").eq(34L));
		assertTrue(json.get("v_double").eq(34.34f));
		assertTrue(json.get("v_double").eq(34.34));
		assertFalse(json.get("v_double").eq(new BigInteger("34")));
		assertTrue(json.get("v_double").eq(new BigDecimal("34.34")));

		assertFalse(json.get("v_bigInteger").eq((byte)33));
		assertFalse(json.get("v_bigInteger").eq((short)33));
		assertFalse(json.get("v_bigInteger").eq(33));
		assertFalse(json.get("v_bigInteger").eq(34343434343434L));
		assertFalse(json.get("v_bigInteger").eq(34.34f));
		assertFalse(json.get("v_bigInteger").eq(34.34));
		assertFalse(json.get("v_bigInteger").eq(new BigInteger("3434")));
		assertFalse(json.get("v_bigInteger").eq(new BigDecimal("3434")));
		assertFalse(json.get("v_bigInteger").eq(true));
		assertFalse(json.get("v_bigInteger").eq("abc"));
		// Exact comparisons: not the value truncated to an int or a long, nor a rounded double
		assertFalse(json.get("v_bigInteger").eq(2080661269));
		assertFalse(json.get("v_bigInteger").eq(-944716198279094507L));
		assertFalse(json.get("v_bigInteger").eq(1.2345678912345679E26));
		assertTrue(json.get("v_bigInteger").eq(new BigInteger("123456789123456789123456789")));
		assertTrue(json.get("v_bigInteger").eq(new BigDecimal("123456789123456789123456789")));

		assertFalse(json.get("v_bigDecimal").eq((byte)33));
		assertFalse(json.get("v_bigDecimal").eq((short)33));
		assertFalse(json.get("v_bigDecimal").eq(33));
		assertFalse(json.get("v_bigDecimal").eq(34343434343434L));
		assertFalse(json.get("v_bigDecimal").eq(34.34f));
		assertFalse(json.get("v_bigDecimal").eq(34.34));
		assertFalse(json.get("v_bigDecimal").eq(new BigInteger("3434")));
		assertFalse(json.get("v_bigDecimal").eq(new BigDecimal("3434")));
		assertFalse(json.get("v_bigDecimal").eq(true));
		assertFalse(json.get("v_bigDecimal").eq("abc"));
		// Exact comparisons: the decimal part counts
		assertFalse(json.get("v_bigDecimal").eq(-60252762));
		assertFalse(json.get("v_bigDecimal").eq(4567892345678765478L));
		assertFalse(json.get("v_bigDecimal").eq(4.5678923456787656E18));
		assertFalse(json.get("v_bigDecimal").eq(new BigInteger("4567892345678765478")));
		assertTrue(json.get("v_bigDecimal").eq(new BigDecimal("4567892345678765478.5657654")));
		
		assertFalse(json.get("v_boolean").eq(33));
		assertFalse(json.get("v_boolean").eq(34343434343434L));
		assertFalse(json.get("v_boolean").eq(34.34));
		assertFalse(json.get("v_boolean").eq(new BigInteger("123456789123456789123456789")));
		assertFalse(json.get("v_boolean").eq(new BigDecimal("4567892345678765478.5657654")));
		assertTrue(json.get("v_boolean").eq(true));
		assertFalse(json.get("v_boolean").eq(false));
		assertFalse(json.get("v_boolean").eq("abc"));
		
		assertFalse(json.get("v_string").eq(33));
		assertFalse(json.get("v_string").eq(34343434343434L));
		assertFalse(json.get("v_string").eq(34.34));
		assertFalse(json.get("v_string").eq(new BigInteger("123456789123456789123456789")));
		assertFalse(json.get("v_string").eq(new BigDecimal("4567892345678765478.5657654")));
		assertFalse(json.get("v_string").eq(true));
		assertTrue(json.get("v_string").eq("abc"));
		assertFalse(json.get("v_string").eq("xy"));

		assertTrue(json.get("v_int").eq((Object)33));
		assertTrue(json.get("v_long").eq((Object)34343434343434L));
		assertTrue(json.get("v_double").eq((Object)34.34));
		assertTrue(json.get("v_bigInteger").eq((Object)new BigInteger("123456789123456789123456789")));
		assertTrue(json.get("v_bigDecimal").eq((Object)new BigDecimal("4567892345678765478.5657654")));
		assertTrue(json.get("v_boolean").eq((Object)true));
		assertTrue(json.get("v_string").eq((Object)"abc"));

		assertTrue(json.get("v_int").eq(JsonValues.of(33)));
		assertTrue(json.get("v_long").eq(JsonValues.of(34343434343434L)));
		assertTrue(json.get("v_double").eq(JsonValues.of(34.34)));
		assertTrue(json.get("v_bigInteger").eq(JsonValues.of(new BigInteger("123456789123456789123456789"))));
		assertTrue(json.get("v_bigDecimal").eq(JsonValues.of(new BigDecimal("4567892345678765478.5657654"))));
		assertTrue(json.get("v_boolean").eq(JsonValues.of(true)));
		assertTrue(json.get("v_string").eq(JsonValues.of("abc")));
	}
	
	public void testNe() throws Exception {
		// ne is just !eq, so we don't need to test all cases
		assertFalse(json.get("v_int").ne((byte)33));
		assertFalse(json.get("v_int").ne((short)33));
		assertFalse(json.get("v_int").ne(33));
		assertFalse(json.get("v_long").ne(34343434343434L));
		assertFalse(json.get("v_double").ne(34.34f));
		assertFalse(json.get("v_double").ne(34.34));
		assertFalse(json.get("v_bigInteger").ne(new BigInteger("123456789123456789123456789")));
		assertFalse(json.get("v_bigDecimal").ne(new BigDecimal("4567892345678765478.5657654")));
		assertFalse(json.get("v_boolean").ne(true));
		assertFalse(json.get("v_string").ne("abc"));
	}
	
	public void testIn() throws Exception {
		assertTrue(json.get("v_int").in(33,34,35));
		assertTrue(json.get("v_int").in(33,34,"35"));
		assertFalse(json.get("v_int").in(32,34,35));
		assertFalse(json.get("v_int").in(32,34,"35"));

		assertTrue(json.get("v_string").in("a","abc","b"));
		assertFalse(json.get("v_string").in("a","abcd","b"));
		assertTrue(json.get("v_string").in("a","abc",13));
		assertFalse(json.get("v_string").in("a","abcd",13));
	}

}
