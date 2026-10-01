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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;

import tests.ProjectTestCase;

/**
 * JsonUtil.eq(), hashCode() and compare(): consistency across number types.
 */
public class NumberEqualityTest extends ProjectTestCase {

	private static void assertEq(Object a, Object b) {
		assertTrue(a+" eq "+b, JsonUtil.eq(a, b));
		assertTrue(b+" eq "+a, JsonUtil.eq(b, a));
		assertEquals(a+" hash "+b, JsonUtil.hashCode(a), JsonUtil.hashCode(b));
		if(a instanceof Number && b instanceof Number) {
			assertEquals(a+" compare "+b, 0, JsonUtil.compare(a, b));
		}
	}
	private static void assertNotEq(Object a, Object b) {
		assertFalse(a+" eq "+b, JsonUtil.eq(a, b));
		assertFalse(b+" eq "+a, JsonUtil.eq(b, a));
		if(a instanceof Number && b instanceof Number) {
			assertTrue(a+" compare "+b, JsonUtil.compare(a, b)!=0);
			assertEquals(a+" antisymmetric "+b, Integer.signum(JsonUtil.compare(a, b)), -Integer.signum(JsonUtil.compare(b, a)));
		}
	}

	public void testSameValueAcrossTypes() {
		assertEq(1, 1L);
		assertEq(1, 1.0);
		assertEq(1, 1.0f);
		assertEq(1, (short)1);
		assertEq(1, (byte)1);
		assertEq(1, BigInteger.ONE);
		assertEq(1, new BigDecimal("1.000"));
		assertEq(0.5, 0.5f);
		assertEq(0.5, new BigDecimal("0.50"));
		assertEq(0.1, new BigDecimal("0.1"));       // a double is its shortest decimal
		assertEq(0.1, 0.1f);                        // both are the decimal 0.1
		assertEq(-7, -7L);
		assertEq(-7, -7.0);
		assertEq(Long.MAX_VALUE, new BigDecimal(Long.MAX_VALUE));
		assertEq(1e20, new BigInteger("100000000000000000000"));
		assertEq(1e300, new BigDecimal("1e300"));
		assertEq(-1e300, new BigDecimal("-1E+300"));
	}

	public void testNegativeZero() {
		assertEq(-0.0, 0.0);
		assertEq(-0.0, 0);
		assertEq(-0.0f, 0L);
		assertEq(-0.0, BigDecimal.ZERO);
		assertEq(JsonArray.of(-0.0), JsonArray.of(0.0));
		assertEq(JsonObject.of("a", -0.0), JsonObject.of("a", 0));
	}

	public void testNaNAndInfinities() {
		assertEq(Double.NaN, Double.NaN);
		assertEq(Double.NaN, Float.NaN);
		assertNotEq(Double.NaN, 0);
		assertEq(Double.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
		assertNotEq(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
		// No finite value equals an infinity, even a huge one
		assertNotEq(BigDecimal.valueOf(Double.MAX_VALUE), Double.POSITIVE_INFINITY);
		assertNotEq(BigInteger.TEN.pow(400), Double.POSITIVE_INFINITY);
		assertTrue(JsonUtil.compare(BigInteger.TEN.pow(400), Double.POSITIVE_INFINITY)<0);
		assertTrue(JsonUtil.compare(BigInteger.TEN.pow(400).negate(), Double.NEGATIVE_INFINITY)>0);
		// NaN sorts after everything
		assertTrue(JsonUtil.compare(Double.NaN, Double.POSITIVE_INFINITY)>0);
		assertTrue(JsonUtil.compare(1, Double.NaN)<0);
	}

	public void testExactComparisons() {
		long big = (1L<<53)+1;
		double d = (double)(1L<<53);
		// Transitivity: 2^53+1 is not the double 2^53
		assertNotEq(big, d);
		assertEq((1L<<53), d);
		assertNotEq(big, (1L<<53));
		assertTrue(JsonUtil.compare(big, d)>0);
		assertNotEq(Long.MAX_VALUE, (double)Long.MAX_VALUE);   // the double is 2^63
		assertNotEq(0.1f, 0.1000001);
		assertNotEq(new BigDecimal("0.1000000000000000000001"), 0.1);
		assertNotEq(1.5, 1);
		assertTrue(JsonUtil.compare(1.5, 1)>0);
		assertTrue(JsonUtil.compare(-1.5, -1L)<0);
	}

	public void testCompareIsATotalOrder() {
		// A random mix of values of all types: compare() must be consistent
		Random r = new Random(42);
		List<Number> values = new ArrayList<>();
		for(int i=0; i<300; i++) {
			long l = r.nextInt(21)-10;
			switch(r.nextInt(8)) {
				case 0 -> values.add((int)l);
				case 1 -> values.add(l*(1L<<50));
				case 2 -> values.add(l/4.0);
				case 3 -> values.add((float)(l/2.0));
				case 4 -> values.add(BigInteger.valueOf(l).shiftLeft(60));
				case 5 -> values.add(BigDecimal.valueOf(l, 1));
				case 6 -> values.add(r.nextBoolean() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
				default -> values.add(l*(1L<<53)+r.nextInt(3));
			}
		}
		Number[] a = values.toArray(new Number[0]);
		Arrays.sort(a, (x,y) -> JsonUtil.compare(x,y)); // TimSort detects contract violations
		for(int i=1; i<a.length; i++) {
			assertTrue(JsonUtil.compare(a[i-1], a[i])<=0);
		}
		// eq and hashCode agree with compare
		for(Number x: a) {
			for(Number y: a) {
				boolean eq = JsonUtil.eq(x, y);
				assertEquals(x+"/"+y, JsonUtil.compare(x, y)==0, eq);
				if(eq) {
					assertEquals(x+"/"+y, JsonUtil.hashCode(x), JsonUtil.hashCode(y));
				}
			}
		}
	}

	public void testHashLikeJdkCollections() {
		// Content made of strings, booleans, null, ints and non integral doubles:
		// the same hash code as the JDK collections holding the same values
		JsonObject o = JsonObject.parse("{\"a\":1,\"b\":\"x\",\"c\":true,\"d\":null,\"e\":-5,\"f\":2.5}");
		Map<String,Object> m = new HashMap<>(o);
		assertEquals(m.hashCode(), o.hashCode());
		JsonArray a = JsonArray.parse("[1,\"x\",false,null,-3,0.25]");
		assertEquals(new ArrayList<>(a).hashCode(), a.hashCode());
	}

	public void testClampToIntAndLong() {
		assertEquals(Integer.MAX_VALUE, JsonUtil.clampToInt(Long.MAX_VALUE));
		assertEquals(Integer.MIN_VALUE, JsonUtil.clampToInt(Long.MIN_VALUE));
		assertEquals(Integer.MAX_VALUE, JsonUtil.clampToInt(1e10));
		assertEquals(Integer.MIN_VALUE, JsonUtil.clampToInt(-1e10));
		assertEquals(Integer.MAX_VALUE, JsonUtil.clampToInt(BigInteger.TEN.pow(30)));
		assertEquals(Integer.MIN_VALUE, JsonUtil.clampToInt(new BigDecimal("-1e30")));
		assertEquals(0, JsonUtil.clampToInt(Double.NaN));
		assertEquals(1, JsonUtil.clampToInt(1.9));
		assertEquals(-1, JsonUtil.clampToInt(-1.9));
		assertEquals(Long.MAX_VALUE, JsonUtil.clampToLong(BigInteger.TEN.pow(30)));
		assertEquals(Long.MIN_VALUE, JsonUtil.clampToLong(BigInteger.TEN.pow(30).negate()));
		assertEquals(42L, JsonUtil.clampToLong(new BigDecimal("42.9")));
		// Typed getters use it
		JsonObject o = JsonObject.of("l", Long.MAX_VALUE, "d", 1e10);
		assertEquals(Integer.MAX_VALUE, o.getInt("l"));
		assertEquals(Integer.MAX_VALUE, o.getInt("d"));
		assertEquals(Integer.MAX_VALUE, JsonArray.of(Long.MAX_VALUE).getInt(0));
	}

	public void testAsIntFromStrings() {
		assertEquals(12, JsonUtil.asInt(" 12 "));
		assertEquals(1, JsonUtil.asInt("1.5"));
		assertEquals(12L, JsonUtil.asLong(" 12 "));
		assertEquals(Integer.MAX_VALUE, JsonUtil.asInt("99999999999"));
		assertEquals(7, JsonUtil.asInt("abc", 7));
		assertEquals(12, JsonUtil.asNumber(" 12 ").intValue());
		assertEquals(7, JsonUtil.asInt(null, 7));
	}

	// eq(a,b) implies hash(a)==hash(b), for the values where a double/float is compared
	// through its shortest decimal (integral values beyond the exact range)
	private static void assertHashContract(Object a, Object b) {
		if(JsonUtil.eq(a, b)) {
			assertEquals(a+" ("+a.getClass().getSimpleName()+") hash "+b+" ("+b.getClass().getSimpleName()+")",
					JsonUtil.hashCode(a), JsonUtil.hashCode(b));
		}
	}
	private static List<Number> equivalents(double d) {
		List<Number> l = new ArrayList<>();
		l.add(d);
		BigDecimal shortest = BigDecimal.valueOf(d);
		l.add(shortest);
		l.add(new BigDecimal(d));   // the exact binary value
		BigInteger bi = shortest.toBigInteger();
		l.add(bi);
		l.add(new BigDecimal(d).toBigInteger());
		if(bi.bitLength()<64) {
			l.add(bi.longValue());
		}
		if(Math.abs(d)<0x1p63) {
			l.add((long)d);
		}
		return l;
	}
	private static List<Number> equivalents(float f) {
		List<Number> l = new ArrayList<>();
		l.add(f);
		l.add((double)f);
		BigDecimal shortest = new BigDecimal(Float.toString(f));
		l.add(shortest);
		l.add(new BigDecimal(f));
		l.add(shortest.toBigInteger());
		if(Math.abs(f)<0x1p63f) {
			l.add((long)f);
			l.add(shortest.toBigInteger().longValue());
		}
		return l;
	}
	private static void assertHashContract(List<Number> values) {
		for(Number x: values) {
			for(Number y: values) {
				assertHashContract(x, y);
			}
		}
	}

	public void testHashContractForLargeIntegralDoubles() {
		// 2^60 as a double is the decimal 1152921504606846980 for eq(), which is the long
		// 1152921504606846980 (not 2^60): the hash must follow
		double d60 = 0x1p60;
		long shortest60 = BigDecimal.valueOf(d60).longValueExact();
		assertEquals(1152921504606847000L, shortest60);
		assertEq(d60, shortest60);
		assertEq(d60, BigInteger.valueOf(shortest60));
		assertEq(d60, new BigDecimal("1.152921504606847E18"));
		assertNotEq(d60, 1L<<60);
		assertEq(0x1p53, 1L<<53);
		assertEq(0x1p55, new BigDecimal(BigDecimal.valueOf(0x1p55).toBigInteger()));
		// Floats beyond 2^24
		assertEq(0x1p40f, 1099511600000L);   // Float.toString(2^40) is 1.0995116E12
		assertNotEq(0x1p40f, 1L<<40);
		assertEq((float)(1<<25), 1<<25);

		double[] doubles = {0x1p53, 0x1p53+2, 0x1p54, 0x1p55, 0x1p55+8, 0x1p60, 0x1p62+1024, 0x1p63, 0x1p64, 1e17, 1e18, 1e19, 1e20, 1e23,
				9007199254740993.0, 123456789012345678.0, -0x1p60, -1e19, Double.MAX_VALUE, 0x1p52+0.5, 4503599627370497.0};
		for(double d: doubles) {
			assertHashContract(equivalents(d));
		}
		float[] floats = {0x1p24f, 0x1p24f+2, 0x1p25f, 0x1p30f, 0x1p40f, 0x1p62f, 0x1p63f, 0x1p70f, 1e10f, 1e20f, 3.4e38f, -0x1p40f, 16777217f, 123456789f};
		for(float f: floats) {
			assertHashContract(equivalents(f));
		}
		// Random integral doubles and floats of every magnitude, with their equivalents
		Random r = new Random(7);
		for(int i=0; i<5000; i++) {
			double d = Math.rint(r.nextDouble()*Math.pow(2, r.nextInt(70)));
			if(r.nextBoolean()) {
				d = -d;
			}
			assertHashContract(equivalents(d));
			float f = (float)d;
			assertHashContract(equivalents(f));
			List<Number> mixed = new ArrayList<>(equivalents(d));
			mixed.addAll(equivalents(f));
			assertHashContract(mixed);
		}
	}
}
