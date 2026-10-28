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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonFactory.DECIMAL;
import org.monflabs.json.JsonFactory.OVERFLOW_DECIMAL;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.json.util.Reducers;
import org.monflabs.util.number.Decimal128;

import tests.ProjectTestCase;

/**
 * The DEC128 number options, and Decimal128 values in the helpers.
 */
public class Decimal128JsonTest extends ProjectTestCase {

	private static class Dec128Factory extends JavaJsonFactory {
		DECIMAL decimal = DECIMAL.DOUBLE;
		OVERFLOW_DECIMAL overflow = OVERFLOW_DECIMAL.DEC128;
		@Override
		public DECIMAL defaultDecimal() { return decimal; }
		@Override
		public OVERFLOW_DECIMAL overflowDecimal() { return overflow; }
	}

	public void testDefaultDecimal() {
		Dec128Factory f = new Dec128Factory();
		f.decimal = DECIMAL.DEC128;
		assertEquals(Decimal128.of("0.1"), f.parse("0.1"));
		assertEquals(Decimal128.of("1.5"), f.parse("1.5"));
		assertEquals(Decimal128.of("1e400"), f.parse("1e400"));
		assertEquals(Decimal128.of("3.14159265358979323846"), f.parse("3.14159265358979323846"));
		// Integers keep their own type
		assertEquals(Integer.valueOf(3), f.parse("3"));
		// More than 34 digits: rounded; out of range: Infinity
		assertEquals(Decimal128.of("1.000000000000000000000000000000000"), f.parse("1.0000000000000000000000000000000001"));
		assertEquals(Decimal128.POSITIVE_INFINITY, f.parse("1e7000"));
		assertEquals(Decimal128.NEGATIVE_INFINITY, f.parse("-1e7000"));
		assertEquals(Decimal128.of(0.5), ((JsonArray)f.parse("[0.5]")).get(0));
	}

	public void testOverflowDecimal() {
		Dec128Factory f = new Dec128Factory();
		// A double holds the value
		assertEquals(3.141592653589793, f.parse("3.141592653589793"));
		assertEquals(0.1, f.parse("0.10000000000000000"));
		// It doesn't: a Decimal128
		assertEquals(Decimal128.of("1e400"), f.parse("1e400"));
		assertEquals(Decimal128.of("-1e-400"), f.parse("-1e-400"));
		assertEquals(Decimal128.of("3.14159265358979323846"), f.parse("3.14159265358979323846"));
		// Beyond Decimal128 too: the double (Infinity or 0), like JSON.parse()
		assertEquals(Double.POSITIVE_INFINITY, f.parse("1e7000"));
		assertEquals(0.0, f.parse("1e-7000"));
	}

	public void testStringify() {
		JsonFactory f = new Dec128Factory();
		JsonArray a = f.createArray();
		a.add(Decimal128.of("0.1"));
		a.add(Decimal128.of("1e400"));
		a.add(Decimal128.of("-0"));
		a.add(Decimal128.of("12"));
		a.add(Decimal128.NaN);
		a.add(Decimal128.POSITIVE_INFINITY);
		a.add(Decimal128.NEGATIVE_INFINITY);
		assertEquals("[0.1,1e400,-0,12,null,null,null]", f.stringify(a, true));
		// Round trip
		Dec128Factory d = new Dec128Factory();
		d.decimal = DECIMAL.DEC128;
		JsonArray b = (JsonArray)d.parse("[0.1,1e400,-0.5]");
		assertEquals(Decimal128.of("0.1"), b.get(0));
		assertEquals(Decimal128.of("1e400"), b.get(1));
		assertEquals("[0.1,1e400,-0.5]", d.stringify(b, true));
	}

	public void testEqualityAndOrder() {
		Decimal128 half = Decimal128.of("0.5");
		assertTrue(JsonUtil.eq(half, 0.5));
		assertTrue(JsonUtil.eq(0.5, half));
		assertTrue(JsonUtil.eq(half, new BigDecimal("0.50")));
		assertTrue(JsonUtil.eq(Decimal128.of(3), 3));
		assertTrue(JsonUtil.eq(Decimal128.of(3), BigInteger.valueOf(3)));
		assertTrue(JsonUtil.eq(Decimal128.of("-0"), 0));
		assertEquals(JsonUtil.hashCode(half), JsonUtil.hashCode(0.5));
		assertEquals(JsonUtil.hashCode(Decimal128.of("-0")), JsonUtil.hashCode(0));
		assertEquals(JsonUtil.hashCode(Decimal128.of(7)), JsonUtil.hashCode(7L));
		assertEquals(JsonUtil.hashCode(Decimal128.NaN), JsonUtil.hashCode(Double.NaN));
		assertTrue(JsonUtil.eq(Decimal128.NaN, Double.NaN));
		assertFalse(JsonUtil.eq(Decimal128.NaN, 1));
		assertTrue(JsonUtil.eq(Decimal128.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
		assertFalse(JsonUtil.eq(Decimal128.POSITIVE_INFINITY, Decimal128.NEGATIVE_INFINITY));
		assertFalse(JsonUtil.eq(Decimal128.POSITIVE_INFINITY, new BigDecimal("1e400")));

		assertTrue(JsonUtil.compareNumber(Decimal128.of(1), Decimal128.of(2)) < 0);
		assertTrue(JsonUtil.compareNumber(Decimal128.POSITIVE_INFINITY, new BigDecimal("1e5000")) > 0);
		assertTrue(JsonUtil.compareNumber(new BigDecimal("-1e5000"), Decimal128.NEGATIVE_INFINITY) > 0);
		assertTrue(JsonUtil.compareNumber(1, Decimal128.POSITIVE_INFINITY) < 0);
		assertTrue(JsonUtil.compareNumber(Decimal128.NaN, Decimal128.POSITIVE_INFINITY) > 0);
		assertEquals(0, JsonUtil.compareNumber(Decimal128.of("-0"), Decimal128.ZERO));
	}

	public void testConversions() {
		Decimal128 d = Decimal128.of("12.75");
		assertEquals(new BigDecimal("12.75"), JsonUtil.asBigDecimal(d));
		assertEquals(BigInteger.valueOf(12), JsonUtil.asBigInteger(d));
		assertEquals(d, JsonUtil.asDecimal128(new BigDecimal("12.75")));
		assertEquals(Decimal128.of(5), JsonUtil.asDecimal128(5));
		assertEquals(Decimal128.of(0.1), JsonUtil.asDecimal128(0.1));
		assertEquals(Decimal128.ONE, JsonUtil.asDecimal128("x", Decimal128.ONE));
		assertEquals(Decimal128.of("1.5"), JsonUtil.parseDecimal128("1.5"));
		assertThrows(JsonException.class, () -> JsonUtil.parseDecimal128("abc"));
		assertThrows(JsonException.class, () -> JsonUtil.checkDecimal128("x"));
		// NaN and the infinities have no BigDecimal/BigInteger
		assertThrows(JsonException.class, () -> JsonUtil.checkBigDecimal(Decimal128.NaN));
		assertThrows(JsonException.class, () -> JsonUtil.checkBigInteger(Decimal128.POSITIVE_INFINITY));
		assertEquals(BigDecimal.ZERO, JsonUtil.asBigDecimal(Decimal128.NaN));
		assertEquals(Long.MAX_VALUE, JsonUtil.clampToLong(Decimal128.POSITIVE_INFINITY));
		assertEquals(0L, JsonUtil.clampToLong(Decimal128.NaN));
		assertEquals(12L, JsonUtil.clampToLong(d));
		assertTrue(JsonUtil.asBoolean(d));
		assertFalse(JsonUtil.asBoolean(Decimal128.ZERO));
		assertFalse(JsonUtil.asBoolean(Decimal128.of("-0")));
		assertFalse(JsonUtil.asBoolean(Decimal128.NaN));
		assertTrue(JsonUtil.asBoolean(Decimal128.of("1e-400")));
		assertTrue(JsonUtil.asBoolean(Decimal128.NEGATIVE_INFINITY));
	}

	public void testTypedAccess() {
		JsonFactory f = new Dec128Factory();
		JsonObject o = f.createObject();
		o.put("a", Decimal128.of("1.25"));
		o.put("b", 3);
		o.put("c", "text");
		assertEquals(Decimal128.of("1.25"), o.getDecimal128("a"));
		assertEquals(Decimal128.of(3), o.getDecimal128("b"));
		assertThrows(JsonException.class, () -> o.getDecimal128("c"));
		assertEquals(Decimal128.TEN, o.getDecimal128("missing", Decimal128.TEN));
		assertEquals(Decimal128.of("1.25"), o.asDecimal128("a"));
		assertEquals(Decimal128.ZERO, o.asDecimal128("c"));

		JsonArray a = f.createArray();
		a.add(Decimal128.of("2.5"));
		a.add(4);
		assertEquals(Decimal128.of("2.5"), a.getDecimal128(0));
		assertEquals(Decimal128.of(4), a.atDecimal128(-1));
		assertEquals(Decimal128.ONE, a.getDecimal128(5, Decimal128.ONE));
		assertEquals(Decimal128.of(4), a.asDecimal128At(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> a.getDecimal128(9));
	}

	public void testReducer() {
		JsonFactory f = new Dec128Factory();
		JsonArray a = f.createArray();
		a.add(Decimal128.of("0.1"));
		a.add(Decimal128.of("0.2"));
		a.add(1);
		assertEquals(Decimal128.of("1.3"), a.reduce(Reducers.<Object>sumDecimal128()));
	}
}
