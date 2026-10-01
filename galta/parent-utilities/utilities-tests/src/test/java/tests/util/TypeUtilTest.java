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
package tests.util;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.util.TypeUtil;

import tests.ProjectTestCase;

public class TypeUtilTest extends ProjectTestCase {

	public void testNonFiniteToBigDecimal() {
		// NaN used to become 0 and the infinities +-Double.MAX_VALUE
		for (Number n : new Number[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
			assertThrows(ArithmeticException.class, () -> TypeUtil.toBigDecimal(n));
		}
		assertEquals( BigDecimal.valueOf(Double.MAX_VALUE), TypeUtil.toBigDecimal(Double.MAX_VALUE) );
	}

	public void testNonFiniteToBigInteger() {
		// NaN used to become 0 and the infinities Long.MIN_VALUE/MAX_VALUE
		for (Number n : new Number[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
			assertThrows(ArithmeticException.class, () -> TypeUtil.toBigInteger(n));
		}
		assertEquals( new BigDecimal(Double.MAX_VALUE).toBigInteger(), TypeUtil.toBigInteger(Double.MAX_VALUE) );
	}

	public void testPrimitiveTargets() {
		assertEquals(0, TypeUtil.toInt(Double.NaN));
		assertEquals(Long.MIN_VALUE, TypeUtil.toLong(Double.NEGATIVE_INFINITY));
		assertEquals((short)-25536, TypeUtil.toShort(40000));
		assertEquals(1.5f, TypeUtil.toFloat(1.5d));
		assertEquals(3.0, TypeUtil.toDouble(BigInteger.valueOf(3)));
		assertEquals(BigDecimal.valueOf(7), TypeUtil.toBigDecimal(BigInteger.valueOf(7)));
		assertEquals(BigDecimal.valueOf(-3), TypeUtil.toBigDecimal((byte)-3));
		assertEquals(BigInteger.valueOf(-2), TypeUtil.toBigInteger(-2.9f));
	}

	private static void assertThrows(Class<? extends Throwable> c, org.junit.function.ThrowingRunnable r) {
		org.junit.Assert.assertThrows(c, r);
	}

	@SuppressWarnings("serial")
	private static class TextNumber extends Number {
		private final String text;
		TextNumber(String text) { this.text = text; }
		@Override public int intValue() { return (int)longValue(); }
		@Override public long longValue() { return new java.math.BigDecimal(text).longValue(); }
		@Override public float floatValue() { return Float.parseFloat(text); }
		@Override public double doubleValue() { return Double.parseDouble(text); }
		@Override public String toString() { return text; }
	}

	public void testLargeIntegerOfUnknownNumberClass() {
		// An integral value of an unknown Number class (e.g. Gson's LazilyParsedNumber)
		// went through longValue() and overflowed
		assertEquals(new java.math.BigInteger("123456789012345678901234567890"),
				org.monflabs.util.TypeUtil.toBigInteger(new TextNumber("123456789012345678901234567890")));
		assertEquals(java.math.BigInteger.valueOf(12), org.monflabs.util.TypeUtil.toBigInteger(new TextNumber("12.7")));
	}

	public void testFloatToBigDecimal() {
		// Widening the float to a double first exposed its binary error (0.10000000149011612)
		assertEquals(new BigDecimal("0.1"), TypeUtil.toBigDecimal(0.1f));
		assertEquals(new BigDecimal("3.4028235E+38"), TypeUtil.toBigDecimal(Float.MAX_VALUE));
		assertEquals(new BigDecimal("-2.5"), TypeUtil.toBigDecimal(-2.5f));
		assertEquals(new BigDecimal("0.1"), TypeUtil.toBigDecimal(0.1d));
	}
}
