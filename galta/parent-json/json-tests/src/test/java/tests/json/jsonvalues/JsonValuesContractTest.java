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
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.jsonpointer.JsonPointer;

import tests.ProjectTestCase;

/**
 * JsonValues comparisons, conversions and stream-like operations.
 */
public class JsonValuesContractTest extends ProjectTestCase {

	public void testNumericComparisonsDoNotTruncate() {
		JsonValues v = JsonValues.of(1.9);
		assertFalse(v.eq(1));
		assertFalse(v.eq(1L));
		assertFalse(v.eq((short)1));
		assertFalse(v.eq((byte)1));
		assertFalse(v.eq(BigInteger.ONE));
		assertTrue(v.eq(1.9));
		assertTrue(v.eq(new BigDecimal("1.9")));
		assertTrue(v.gt(1));
		assertTrue(v.lt(2));
		assertTrue(v.gte(1L));
		assertFalse(v.lte(1));
		assertFalse(v.in(1));
		assertTrue(v.in(1.9));

		JsonValues half = JsonValues.of(1.5);
		assertTrue(half.gt(1));
		assertFalse(half.lt(1));

		// A long that wraps to a small int is not that int
		JsonValues big = JsonValues.of(4294967297L); // 2^32+1
		assertFalse(big.eq(1));
		assertFalse(big.in(1));
		assertTrue(big.eq(4294967297L));
		assertTrue(big.gt(Integer.MAX_VALUE));

		// Exact comparisons across types
		assertTrue(JsonValues.of(1).eq(1.0));
		assertTrue(JsonValues.of(1.0).eq(1));
		assertTrue(JsonValues.of(new BigInteger("9007199254740993")).eq(9007199254740993L));
		assertFalse(JsonValues.of(9007199254740993L).eq(9007199254740992.0));
	}

	public void testNaNComparisons() {
		JsonValues nan = JsonValues.of(Double.NaN);
		assertFalse(nan.eq(0));
		assertFalse(nan.eq(Double.NaN));
		assertTrue(nan.ne(0));
		assertFalse(nan.lt(0));
		assertFalse(nan.gt(0));
		assertFalse(nan.lte(0.0));
		assertFalse(nan.gte(0.0));
		assertFalse(JsonValues.of(0).lt(Double.NaN));
	}

	public void testInIntegers() {
		JsonValues v = JsonValues.of(2);
		assertTrue(v.in(1,2,3));
		assertFalse(v.in(4,5));
		// A null element is skipped, not unboxed
		assertTrue(v.in(null, 2));
		assertFalse(v.in(new Integer[] {null}));
		// Not a number
		assertFalse(JsonValues.of("2").in(1,2));
	}

	public void testComparisonsWithoutAValue() {
		JsonValues empty = JsonValues.of();
		assertFalse(empty.eq(1));
		assertFalse(empty.eq((Object)1));
		assertFalse(empty.eq((Object)null));
		assertFalse(empty.in("a", 1));
		assertFalse(empty.in((Object)null));
		JsonValues list = JsonValues.of(1, 2);
		assertFalse(list.eq((Object)1));
		assertFalse(list.in((Object)1, 2));
		// eq(JsonValues) compares single values only
		assertTrue(JsonValues.of(1).eq((Object)JsonValues.of(1.0)));
		assertFalse(JsonValues.of(1).eq((Object)JsonValues.of()));
	}

	public void testAsDefaultsOnEmpty() {
		JsonValues empty = JsonValues.of();
		assertEquals(7, empty.asInt(7));
		assertEquals(7L, empty.asLong(7L));
		assertEquals(7.5, empty.asDouble(7.5), 0);
		assertEquals(BigInteger.TEN, empty.asBigInteger(BigInteger.TEN));
		assertEquals(BigDecimal.TEN, empty.asBigDecimal(BigDecimal.TEN));
		assertTrue(empty.asBoolean(true));
		assertEquals("d", empty.asString("d"));
		// A value is converted, the default is only for a missing value
		assertEquals(3, JsonValues.of("3").asInt(7));
		assertEquals(7, JsonValues.of("x").asInt(7));
	}

	public void testDistinctIsJsonEquality() {
		JsonValues v = JsonValues.of(1, 1L, 1.0, new BigDecimal("1.00"), 2, "1", JsonArray.of(1), JsonArray.of(1.0), null, null);
		JsonValues d = v.distinct();
		assertEquals(5, d._size());
		assertEquals(1, d._get(0));   // the first occurrence is kept
		assertEquals(2, d._get(1));
		assertEquals("1", d._get(2));
		assertEquals(JsonArray.of(1), d._get(3));
		assertNull(d._get(4));
	}

	public void testLimit() {
		JsonValues v = JsonValues.of(1,2,3);
		assertThrows(IllegalArgumentException.class, () -> v.limit(-1));
		assertTrue(v.limit(0).isEmpty());
		assertEquals(JsonValues.of(1,2), v.limit(2));
		assertEquals(v, v.limit(10));
	}

	public void testSliceSteps() {
		JsonValues v = JsonValues.of(0,1,2,3,4);
		assertEquals(JsonValues.of(2), v.slice(2));          // the single value at 2
		assertEquals(JsonValues.of(4), v.slice(-1));
		assertTrue(v.slice(9).isEmpty());
		assertEquals(JsonValues.of(2,3,4), v.slice(2, Integer.MAX_VALUE));
		assertEquals(JsonValues.of(1), v.slice(1, 5, Integer.MAX_VALUE));
		assertEquals(JsonValues.of(4), v.slice(4, -10, Integer.MIN_VALUE+1));
		assertEquals(JsonValues.of(4,2,0), v.slice(null, null, -2));
	}

	public void testGetWithNegativeIndexes() {
		JsonValues list = JsonValues.of(JsonArray.of(1, 2, 3), JsonArray.of(4, 5));
		assertEquals(JsonValues.of(3, 5), list.get(-1));
		assertEquals(JsonValues.of(1, 4), list.get(0));
		assertEquals(JsonValues.of(3, 2, 5, 4), list.get(-1, -2));
		assertEquals(JsonValues.of(1), list.get(-3));
		assertEquals(JsonValues.of(3), JsonValues.of(JsonArray.of(1, 2, 3)).get(-1));
	}

	public void testPathPointersOnAList() {
		// The pointers of values read from different elements stay distinct
		JsonValues list = JsonValues.of(JsonObject.parse("{\"a\":1}"), JsonObject.parse("{\"a\":2}"));
		JsonValues r = list.path("$.a", true);
		assertEquals(JsonValues.of(1, 2), r);
		assertEquals(List.of(JsonPointer.of("/0/a"), JsonPointer.of("/1/a")), r.getPointers());

		// A list read with pointers keeps them through a second path
		JsonObject doc = JsonObject.parse("{\"items\":[{\"a\":{\"b\":1}},{\"a\":{\"b\":2}}]}");
		JsonValues items = doc.jsonValues().path("$.items[*].a", true);
		JsonValues b = items.path("$.b", true);
		assertEquals(List.of(JsonPointer.of("/items/0/a/b"), JsonPointer.of("/items/1/a/b")), b.getPointers());

		// An empty list reads nothing
		assertTrue(JsonValues.of().path("$.a", true).isEmpty());
	}

	public void testFilterKeepsPointers() {
		JsonObject doc = JsonObject.parse("{\"items\":[{\"a\":1},{\"a\":2},{\"a\":3}]}");
		JsonValues items = doc.jsonValues().path("$.items[*]", true);
		JsonValues odd = items.filter(v -> v.get("a").intValue() % 2 == 1);
		assertEquals(List.of(JsonPointer.of("/items/0"), JsonPointer.of("/items/2")), odd.getPointers());
		JsonValues even = items.reject(v -> v.get("a").intValue() % 2 == 1);
		assertEquals(List.of(JsonPointer.of("/items/1")), even.getPointers());
	}
}
