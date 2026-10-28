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
package tests.number;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.monflabs.util.number.Decimal128;
import org.monflabs.util.number.DecimalRoundingMode;

import tests.ProjectTestCase;

/**
 * Decimal128 against the ECMAScript Decimal proposal, and (random operands) against BigDecimal with a 34 digits context.
 */
public class Decimal128Test extends ProjectTestCase {

	private static final MathContext MC34 = new MathContext(34, RoundingMode.HALF_EVEN);
	private static final Decimal128 NAN = Decimal128.NaN;
	private static final Decimal128 INF = Decimal128.POSITIVE_INFINITY;
	private static final Decimal128 NINF = Decimal128.NEGATIVE_INFINITY;
	private static final Decimal128 NZERO = Decimal128.NEGATIVE_ZERO;
	private static final Decimal128 ZERO = Decimal128.ZERO;

	private static Decimal128 d(String s) {
		return Decimal128.of(s);
	}

	private static String zeros(int n) {
		return "0".repeat(n);
	}

	private static String nines(int n) {
		return "9".repeat(n);
	}

	private static void assertDec(String expected, Decimal128 actual) {
		assertEquals(expected, actual.toString());
	}

	/** The result is the same value as parsing its string, hence canonical. */
	private static void assertCanonical(Decimal128 v) {
		assertEquals(v, d(v.toString()));
		assertEquals(v, Decimal128.fromBits(v.highBits(), v.lowBits()));
	}

	// ----------------------------------------------------------------------------------
	// Constants and storage
	// ----------------------------------------------------------------------------------

	public void testConstantsAndBits() throws Exception {
		assertEquals(0x3040000000000000L, ZERO.highBits());
		assertEquals(0L, ZERO.lowBits());
		assertEquals(0xB040000000000000L, NZERO.highBits());
		assertEquals(0x3040000000000000L, Decimal128.ONE.highBits());
		assertEquals(1L, Decimal128.ONE.lowBits());
		assertEquals(0x3042000000000000L, Decimal128.TEN.highBits());
		assertEquals(0x7800000000000000L, INF.highBits());
		assertEquals(0xF800000000000000L, NINF.highBits());
		assertEquals(0x7C00000000000000L, NAN.highBits());
		// The IEEE 754 encodings of the extreme values
		assertEquals(0x5FFFED09BEAD87C0L, Decimal128.MAX_VALUE.highBits());
		assertEquals(0x378D8E63FFFFFFFFL, Decimal128.MAX_VALUE.lowBits());
		assertEquals(0L, Decimal128.MIN_VALUE.highBits());
		assertEquals(1L, Decimal128.MIN_VALUE.lowBits());
		assertDec("9.999999999999999999999999999999999e6144", Decimal128.MAX_VALUE);
		assertDec("1e-6176", Decimal128.MIN_VALUE);
		assertEquals(0xB040000000000000L, d("-1").highBits());
		assertEquals(Decimal128.MAX_VALUE.negate(), d("-9.999999999999999999999999999999999e6144"));
		assertEquals(34, Decimal128.MAX_DIGITS);
		assertEquals(-6176, Decimal128.MIN_EXPONENT);
		assertEquals(6111, Decimal128.MAX_EXPONENT);
	}

	public void testFromBitsNormalizes() throws Exception {
		assertEquals(d("1"), Decimal128.fromBits(0x3040000000000000L, 1));
		// 10 x 10^0 is the value 1 x 10^1
		Decimal128 ten = Decimal128.fromBits(0x3040000000000000L, 10);
		assertEquals(d("10"), ten);
		assertEquals(d("10").highBits(), ten.highBits());
		assertEquals(0x3042000000000000L, ten.highBits());
		// Any NaN is the NaN, a signaling one or with a payload
		assertEquals(NAN, Decimal128.fromBits(0xFC00000000000000L, 7));
		assertEquals(NAN, Decimal128.fromBits(0x7E00000000000000L, 0));
		assertEquals(INF, Decimal128.fromBits(0x7800000000000000L, 5));
		assertEquals(NINF, Decimal128.fromBits(0xF800000000000000L, 0));
		// A coefficient of 10^34 or more reads as a zero
		BigInteger p34 = BigInteger.TEN.pow(34);
		long hi = 0x3040000000000000L | p34.shiftRight(64).longValue();
		assertEquals(ZERO, Decimal128.fromBits(hi, p34.longValue()));
		assertEquals(NZERO, Decimal128.fromBits(hi | (1L << 63), p34.longValue()));
		// The second form of the encoding has a coefficient over 2^113
		assertEquals(ZERO, Decimal128.fromBits(0x6000000000000000L, 12));
		// A zero with any exponent is the zero
		assertEquals(ZERO, Decimal128.fromBits(0x2000000000000000L, 0));
		// The exponent range
		assertEquals(Decimal128.MAX_VALUE, Decimal128.fromBits(0x5FFFED09BEAD87C0L, 0x378D8E63FFFFFFFFL));
		assertDec("1e6144", Decimal128.fromBits(0x5FFE314DC6448D93L, 0x38C15B0A00000000L));
		// A coefficient over 2^63
		Decimal128 big = d("12345678901234567890123456789012");
		assertEquals(big, Decimal128.fromBits(big.highBits(), big.lowBits()));
	}

	// ----------------------------------------------------------------------------------
	// Parsing and strings
	// ----------------------------------------------------------------------------------

	public void testParse() throws Exception {
		assertDec("0", d("0"));
		assertDec("0", d("0000"));
		assertDec("0", d("0.000"));
		assertDec("0", d("0e100"));
		assertDec("-0", d("-0"));
		assertDec("-0", d("-0.0e-5"));
		assertDec("0", d("+0"));
		assertDec("1.5", d("1.5"));
		assertDec("1.5", d("1.50"));
		assertDec("1.5", d("001.5000"));
		assertDec("100", d("100"));
		assertDec("100", d("1e2"));
		assertDec("100", d("1E+2"));
		assertDec("100", d("0.1e3"));
		assertDec("0.5", d(".5"));
		assertDec("1", d("1."));
		assertDec("3", d("+3"));
		assertDec("-3", d("-3"));
		assertDec("123.45", d("12345e-2"));
		assertDec("0.000001", d("0.000001"));
		assertDec("1e-7", d("0.0000001"));
		assertDec("1.5e-7", d("1.5e-7"));
		assertDec("1000000000000000000000000000000000", d("1e33"));
		assertDec("1e34", d("1e34"));
		assertDec("1e34", d("10000000000000000000000000000000000"));
		assertDec(nines(34), d(nines(34)));
		assertDec("9223372036854775807", d("9223372036854775807"));
		assertDec("9223372036854775808", d("9223372036854775808"));
		assertDec("-18446744073709551616", d("-18446744073709551616"));
		assertDec("NaN", d("NaN"));
		assertDec("Infinity", d("Infinity"));
		assertDec("Infinity", d("+Infinity"));
		assertDec("-Infinity", d("-Infinity"));
		assertSame(NAN, d("NaN"));
		assertTrue(d("-0").isNegative());
		assertFalse(d("0").isNegative());
	}

	public void testParseInvalid() throws Exception {
		for (String s : new String[] { "", " ", " 1", "1 ", "abc", "1e", "1e+", "1e-", ".", "e5", ".e5", "--1", "+-1", "1.2.3", "0x10", "1_000",
				"Infinit", "infinity", "+NaN", "-NaN", "nan", "NAN", "1,5", "1e5.5", "١٢", "1f", "- 1" }) {
			try {
				Decimal128.of(s);
				fail("Accepted: \"" + s + "\"");
			} catch (NumberFormatException e) {
				// Expected
			}
		}
		assertThrows(NumberFormatException.class, () -> Decimal128.of((String) null));
	}

	public void testCohortsAreOneValue() throws Exception {
		Decimal128 a = d("1.5");
		for (String s : new String[] { "1.50", "1.500000", "15e-1", "0.15e1", "150e-2", "0001.5", "1.5000000000000000000000000000000000" }) {
			assertEquals(a, d(s));
			assertEquals(a.highBits(), d(s).highBits());
			assertEquals(a.lowBits(), d(s).lowBits());
			assertEquals(a.hashCode(), d(s).hashCode());
		}
		assertEquals(d("100"), d("1e2"));
		assertEquals(d("100"), d("100.00"));
		assertEquals(d("1e6144"), d("10e6143"));
		assertEquals(d("1e6144"), d("1000000000000000000000000000000000e6111"));
		// Beyond the exponent range, the zeros of the coefficient stay
		Decimal128 v = d("1e6140");
		assertEquals(6140.0, v.exponent());
		assertDec("1e6140", v);
		assertCanonical(v);
	}

	public void testParseRounding() throws Exception {
		// 35 digits: ties go to the even digit
		assertDec("1e34", d("1" + zeros(33) + "5"));
		assertDec("1." + zeros(32) + "2e34", d("1" + zeros(32) + "15"));
		assertDec("1." + zeros(32) + "2e34", d("1" + zeros(32) + "25"));
		assertDec("1." + zeros(32) + "1e34", d("1" + zeros(32) + "14"));
		// Digits far beyond the precision only matter as a sticky digit
		assertSameNumber("sticky 1", new BigDecimal("1" + zeros(32) + "15" + zeros(100) + "1").round(MC34), d("1" + zeros(32) + "15" + zeros(100) + "1"));
		assertSameNumber("tie odd", new BigDecimal("1" + zeros(32) + "15" + zeros(100)).round(MC34), d("1" + zeros(32) + "15" + zeros(100)));
		assertSameNumber("tie even", new BigDecimal("1" + zeros(32) + "25" + zeros(100)).round(MC34), d("1" + zeros(32) + "25" + zeros(100)));
		assertSameNumber("far sticky", new BigDecimal("1" + zeros(32) + "15" + zeros(100) + "1" + zeros(1000)).round(MC34), d("1" + zeros(32) + "15" + zeros(100) + "1" + zeros(1000)));
		assertSameNumber("far tie", new BigDecimal("1" + zeros(33) + "5" + zeros(2000)).round(MC34), d("1" + zeros(33) + "5" + zeros(2000)));
		assertSameNumber("far sticky 2", new BigDecimal("1" + zeros(33) + "5" + zeros(2000) + "1").round(MC34), d("1" + zeros(33) + "5" + zeros(2000) + "1"));
		// Exponents
		assertDec("0.000001", d("10" + zeros(40) + "e-47"));
		assertDec("1e-7", d("10" + zeros(40) + "e-48"));
		assertDec("Infinity", d("0.0001e1000004"));
	}

	public void testParseRange() throws Exception {
		assertDec("9.999999999999999999999999999999999e6144", d(nines(34) + "e6111"));
		assertDec("1e6144", d("1e6144"));
		assertDec("Infinity", d("1e6145"));
		assertDec("Infinity", d("9.9999999999999999999999999999999995e6144"));
		assertDec("9.999999999999999999999999999999999e6144", d("9.99999999999999999999999999999999949e6144"));
		assertDec("-Infinity", d("-1e6145"));
		assertDec("Infinity", d("1e99999999999999999999"));
		assertDec("1e-6176", d("1e-6176"));
		// Underflow: nearest of 0 and 1e-6176
		assertDec("0", d("1e-6177"));
		assertDec("0", d("5e-6177"));
		assertDec("1e-6176", d("5.1e-6177"));
		assertDec("1e-6176", d("6e-6177"));
		assertDec("-0", d("-1e-6177"));
		assertDec("-0", d("-5e-6177"));
		assertDec("-1e-6176", d("-6e-6177"));
		assertDec("0", d("1e-99999999999999999999"));
		assertDec("-0", d("-1e-99999999999999999999"));
		assertDec("0", d("0e99999999999999999999"));
		// Subnormal values keep fewer digits
		assertDec("2e-6176", d("1.5e-6176"));
		assertDec("2e-6176", d("2.5e-6176"));
		assertDec("1.2e-6175", d("1.2e-6175"));
		assertDec("1.2e-6175", d("1.24e-6175"));
	}

	public void testToStringFormats() throws Exception {
		assertDec("0.001", d("0.001"));
		assertDec("123456789012345678901234567890123.4", d("123456789012345678901234567890123.4"));
		assertDec("0.0000012345", d("1.2345e-6"));
		assertDec("1.2345e-7", d("0.00000012345"));
		assertDec("-1.5e-7", d("-0.00000015"));
		assertDec("1e34", d("1e34"));
		assertDec("-1e34", d("-1e34"));
		assertDec("1.5e34", d("15e33"));
		assertDec("1.5e100", d("1.5e100"));
		assertDec("-1.5e-100", d("-1.5e-100"));
		assertDec("1e6144", d("1e6144"));
		assertEquals("0.0000001", d("1e-7").toFixed(7));
	}

	public void testToExponential() throws Exception {
		assertEquals("1.2345e2", d("123.45").toExponential());
		assertEquals("1.23e-3", d("0.00123").toExponential());
		assertEquals("5e0", d("5").toExponential());
		assertEquals("-5e0", d("-5").toExponential());
		assertEquals("1e2", d("100").toExponential());
		assertEquals("1e34", d("1e34").toExponential());
		assertEquals("1.234567890123456789012345678901234e33", d("1234567890123456789012345678901234").toExponential());
		assertEquals("0e+0", ZERO.toExponential());
		assertEquals("-0e+0", NZERO.toExponential());
		assertEquals("NaN", NAN.toExponential());
		assertEquals("Infinity", INF.toExponential());
		assertEquals("-Infinity", NINF.toExponential());
	}

	public void testToFixed() throws Exception {
		assertEquals("1", d("1.2").toFixed());
		assertEquals("2", d("1.5").toFixed());
		assertEquals("2", d("2.5").toFixed());
		assertEquals("1.50", d("1.5").toFixed(2));
		assertEquals("1.00", d("1").toFixed(2));
		assertEquals("100.0", d("1e2").toFixed(1));
		assertEquals("123.46", d("123.455").toFixed(2));
		assertEquals("123.46", d("123.4551").toFixed(2));
		assertEquals("123.44", d("123.445").toFixed(2));
		assertEquals("0.000", d("0.0001").toFixed(3));
		assertEquals("0.001", d("0.0005001").toFixed(3));
		assertEquals("1000", d("999.9").toFixed(0));
		assertEquals("-1.50", d("-1.5").toFixed(2));
		assertEquals("-0.00", d("-0.001").toFixed(2));
		assertEquals("0.00", ZERO.toFixed(2));
		assertEquals("-0.00", NZERO.toFixed(2));
		assertEquals("0", ZERO.toFixed(0));
		assertEquals("-0", NZERO.toFixed(0));
		assertEquals("1" + zeros(30), d("1e30").toFixed());
		assertEquals("1" + zeros(30) + ".00", d("1e30").toFixed(2));
		assertEquals("0.25", d("0.2512").toFixed(2, DecimalRoundingMode.FLOOR));
		assertEquals("0.26", d("0.2512").toFixed(2, DecimalRoundingMode.CEIL));
		assertEquals("-0.26", d("-0.2512").toFixed(2, DecimalRoundingMode.FLOOR));
		assertEquals("-0.25", d("-0.2512").toFixed(2, DecimalRoundingMode.CEIL));
		assertEquals("-0.25", d("-0.2599").toFixed(2, DecimalRoundingMode.TRUNC));
		assertEquals("0.13", d("0.125").toFixed(2, DecimalRoundingMode.HALF_EXPAND));
		assertEquals("0.12", d("0.125").toFixed(2, DecimalRoundingMode.HALF_EVEN));
		assertEquals("NaN", NAN.toFixed(2));
		assertEquals("Infinity", INF.toFixed(2));
		assertEquals("-Infinity", NINF.toFixed());
		assertEquals("12345678901234567890.1234", d("12345678901234567890.1234").toFixed(4));
		assertThrows(IllegalArgumentException.class, () -> d("1").toFixed(-1));
	}

	public void testToPrecision() throws Exception {
		assertEquals("1.2", d("1.234").toPrecision(2));
		assertEquals("1.234", d("1.234").toPrecision(4));
		assertEquals("1.2340", d("1.234").toPrecision(5));
		assertEquals("123", d("123.456").toPrecision(3));
		assertEquals("123.5", d("123.456").toPrecision(4));
		assertEquals("1.2e2", d("123.456").toPrecision(2));
		assertEquals("1e2", d("123.456").toPrecision(1));
		assertEquals("100", d("100").toPrecision(3));
		assertEquals("100.0", d("100").toPrecision(4));
		assertEquals("1.0e2", d("100").toPrecision(2));
		assertEquals("1e2", d("100").toPrecision(1));
		assertEquals("0.00123", d("0.00123").toPrecision(3));
		assertEquals("0.001230", d("0.00123").toPrecision(4));
		assertEquals("0.0012", d("0.00123").toPrecision(2));
		assertEquals("1.2e-7", d("0.00000012").toPrecision(2));
		assertEquals("1.23e-7", d("0.000000123").toPrecision(3));
		assertEquals("0.000001", d("0.000001").toPrecision(1));
		assertEquals("1e3", d("999.9").toPrecision(1));
		assertEquals("1.0e3", d("999.9").toPrecision(2));
		assertEquals("1.00e3", d("999.9").toPrecision(3));
		assertEquals("1000", d("999.99").toPrecision(4));
		assertEquals("-1.5", d("-1.5").toPrecision(2));
		assertEquals("-1.2", d("-1.25").toPrecision(2));
		assertEquals("-1.3", d("-1.25").toPrecision(2, DecimalRoundingMode.HALF_EXPAND));
		assertEquals("-1.3", d("-1.21").toPrecision(2, DecimalRoundingMode.FLOOR));
		assertEquals("-1.2", d("-1.29").toPrecision(2, DecimalRoundingMode.CEIL));
		assertEquals("0", ZERO.toPrecision(1));
		assertEquals("0.00", ZERO.toPrecision(3));
		assertEquals("-0.0", NZERO.toPrecision(2));
		assertEquals("NaN", NAN.toPrecision(3));
		assertEquals("-Infinity", NINF.toPrecision(3));
		assertEquals("1.5", d("1.5").toPrecision());
		assertEquals("1e34", d("1e34").toPrecision());
		assertEquals("12345678901234567890123456789012345" .substring(0, 34), d("1234567890123456789012345678901234").toPrecision(34));
		assertEquals("123" + zeros(31), d("1.23e33").toPrecision(34));
		assertThrows(IllegalArgumentException.class, () -> d("1").toPrecision(0));
	}

	// ----------------------------------------------------------------------------------
	// Conversions
	// ----------------------------------------------------------------------------------

	public void testFromNumbers() throws Exception {
		assertDec("0", Decimal128.of(0L));
		assertDec("42", Decimal128.of(42L));
		assertDec("-42", Decimal128.of(-42L));
		assertDec("9223372036854775807", Decimal128.of(Long.MAX_VALUE));
		assertDec("-9223372036854775808", Decimal128.of(Long.MIN_VALUE));
		assertEquals(d("1000"), Decimal128.of(1000L));

		assertDec("0.1", Decimal128.of(0.1));
		assertDec("0.30000000000000004", Decimal128.of(0.1 + 0.2));
		assertDec("1.5", Decimal128.of(1.5));
		assertDec("100", Decimal128.of(100.0));
		assertDec("1" + zeros(21), Decimal128.of(1e21));
		assertDec("1e-7", Decimal128.of(1e-7));
		assertDec("-123.456", Decimal128.of(-123.456));
		assertDec("0", Decimal128.of(-0.0));
		assertFalse(Decimal128.of(-0.0).isNegative());
		assertDec("NaN", Decimal128.of(Double.NaN));
		assertDec("Infinity", Decimal128.of(Double.POSITIVE_INFINITY));
		assertDec("-Infinity", Decimal128.of(Double.NEGATIVE_INFINITY));
		assertDec("1.7976931348623157e308", Decimal128.of(Double.MAX_VALUE));
		assertDec("5e-324", Decimal128.of(Double.MIN_VALUE));

		assertDec("123456789012345678901234567890", Decimal128.of(new BigInteger("123456789012345678901234567890")));
		assertDec("-1", Decimal128.of(BigInteger.valueOf(-1)));
		assertDec("1" + zeros(34) .substring(0, 0) + "e40", Decimal128.of(BigInteger.TEN.pow(40)));
		// More than 34 digits
		assertDec("1.234567890123456789012345678901235e39", Decimal128.of(new BigInteger("1234567890123456789012345678901234567890")));
		assertDec("Infinity", Decimal128.of(BigInteger.TEN.pow(6145)));
		assertDec("1e6144", Decimal128.of(BigInteger.TEN.pow(6144)));
		assertDec("-Infinity", Decimal128.of(BigInteger.TEN.pow(6145).negate()));
		assertDec("1.2e40", Decimal128.of(new BigInteger("12" + zeros(39)), DecimalRoundingMode.CEIL));
		assertDec("1.235e39", Decimal128.of(new BigInteger("1235" + zeros(36))));
	}

	public void testBigDecimalConversions() throws Exception {
		assertDec("1.5", Decimal128.of(new BigDecimal("1.5")));
		assertDec("1.5", Decimal128.of(new BigDecimal("1.500")));
		assertDec("100", Decimal128.of(new BigDecimal("1E+2")));
		assertDec("0", Decimal128.of(new BigDecimal("0.00")));
		assertDec("-123.456", Decimal128.of(new BigDecimal("-123.456")));
		assertDec("1e-7", Decimal128.of(new BigDecimal("0.0000001")));
		assertDec("1e-6176", Decimal128.of(new BigDecimal("1E-6176")));
		assertDec("0", Decimal128.of(new BigDecimal("1E-6177")));
		assertDec("Infinity", Decimal128.of(new BigDecimal("1E+6145")));
		assertDec("1e6144", Decimal128.of(new BigDecimal(BigInteger.ONE, -6144)));
		// More than 34 digits
		BigDecimal pi = new BigDecimal("3.14159265358979323846264338327950288419716939937510");
		assertDec("3.141592653589793238462643383279503", Decimal128.of(pi));
		assertDec("3.141592653589793238462643383279502", Decimal128.of(pi, DecimalRoundingMode.FLOOR));
		assertDec("3.141592653589793238462643383279503", Decimal128.of(pi, DecimalRoundingMode.CEIL));
		assertDec("-3.141592653589793238462643383279502", Decimal128.of(pi.negate(), DecimalRoundingMode.TRUNC));
		assertThrows(NullPointerException.class, () -> Decimal128.of(pi, null));

		assertEquals(new BigDecimal("1.5"), d("1.5").toBigDecimal());
		assertEquals(0, new BigDecimal("1.5").compareTo(d("1.50").toBigDecimal()));
		assertEquals(new BigDecimal("100"), d("1e2").toBigDecimal());	// An integer, not 1E+2
		assertEquals("100", d("100").toBigDecimal().toString());
		assertEquals(BigDecimal.ZERO, ZERO.toBigDecimal());
		assertEquals(BigDecimal.ZERO, NZERO.toBigDecimal());
		assertEquals(new BigDecimal("-0.000000123"), d("-1.23e-7").toBigDecimal());
		assertEquals(new BigDecimal(BigInteger.ONE, 6176), Decimal128.MIN_VALUE.toBigDecimal());
		assertEquals(new BigDecimal("12345678901234567890123456789012"), d("12345678901234567890123456789012").toBigDecimal());
		assertEquals(BigInteger.TEN.pow(6144), d("1e6144").toBigDecimal().toBigIntegerExact());
		assertThrows(ArithmeticException.class, () -> NAN.toBigDecimal());
		assertThrows(ArithmeticException.class, () -> INF.toBigDecimal());
		assertThrows(ArithmeticException.class, () -> NINF.toBigDecimal());
	}

	public void testBigIntegerConversion() throws Exception {
		assertEquals(BigInteger.valueOf(42), d("42").toBigInteger());
		assertEquals(BigInteger.valueOf(-4200), d("-42e2").toBigInteger());
		assertEquals(BigInteger.valueOf(5), d("5.0").toBigInteger());
		assertEquals(BigInteger.ZERO, ZERO.toBigInteger());
		assertEquals(BigInteger.ZERO, NZERO.toBigInteger());
		assertEquals(new BigInteger("12345678901234567890123456789012"), d("12345678901234567890123456789012").toBigInteger());
		assertThrows(ArithmeticException.class, () -> d("1.5").toBigInteger());
		assertThrows(ArithmeticException.class, () -> d("1e-5").toBigInteger());
		assertThrows(ArithmeticException.class, () -> NAN.toBigInteger());
		assertThrows(ArithmeticException.class, () -> INF.toBigInteger());
	}

	public void testNumberMethods() throws Exception {
		assertEquals(1.5, d("1.5").doubleValue());
		assertEquals(0.1, d("0.1").doubleValue());
		assertEquals(0.30000000000000004, d("0.30000000000000004").doubleValue());
		assertEquals(-123456789.0, d("-123456789").doubleValue());
		assertEquals(1e22, d("1e22").doubleValue());
		assertEquals(1e23, d("1e23").doubleValue());
		assertEquals(1e-22, d("1e-22").doubleValue());
		assertEquals(1e-23, d("1e-23").doubleValue());
		assertEquals(1e300, d("1e300").doubleValue());
		assertEquals(Double.POSITIVE_INFINITY, d("1e400").doubleValue());
		assertEquals(0.0, d("1e-400").doubleValue());
		assertEquals(Double.MAX_VALUE, d("1.7976931348623157e308").doubleValue());
		assertEquals(Double.MIN_VALUE, d("5e-324").doubleValue());
		assertEquals(Double.doubleToLongBits(-0.0), Double.doubleToLongBits(NZERO.doubleValue()));
		assertEquals(Double.doubleToLongBits(0.0), Double.doubleToLongBits(ZERO.doubleValue()));
		assertTrue(Double.isNaN(NAN.doubleValue()));
		assertEquals(Double.POSITIVE_INFINITY, INF.doubleValue());
		assertEquals(Double.NEGATIVE_INFINITY, NINF.doubleValue());
		// Exact operands of the fast path, rounded only once
		assertEquals(9007199254740993.0, d("9007199254740993").doubleValue());
		assertEquals(new BigDecimal("1234567890123456789012345678901").doubleValue(), d("1234567890123456789012345678901").doubleValue());
		assertEquals(1.5f, d("1.5").floatValue());
		assertEquals(0.1f, d("0.1").floatValue());
		assertTrue(Float.isNaN(NAN.floatValue()));
		assertEquals(Float.NEGATIVE_INFINITY, NINF.floatValue());

		assertEquals(42L, d("42.9").longValue());
		assertEquals(-42L, d("-42.9").longValue());
		assertEquals(0L, d("0.9").longValue());
		assertEquals(0L, d("1e-30").longValue());
		assertEquals(0L, NAN.longValue());
		assertEquals(Long.MAX_VALUE, INF.longValue());
		assertEquals(Long.MIN_VALUE, NINF.longValue());
		assertEquals(Long.MAX_VALUE, d("9223372036854775807").longValue());
		assertEquals(Long.MAX_VALUE, d("9223372036854775808").longValue());
		assertEquals(Long.MIN_VALUE, d("-9223372036854775808").longValue());
		assertEquals(Long.MIN_VALUE, d("-9223372036854775809").longValue());
		assertEquals(Long.MAX_VALUE, d("1e30").longValue());
		assertEquals(1000000000000000000L, d("1e18").longValue());
		assertEquals(Long.MAX_VALUE, d("1e19").longValue());
		assertEquals(123456789012345678L, d("123456789012345678.9876543210987654321").longValue());
		assertEquals(123456789012345678L, d("123456789012345678.98765432109876543").longValue());
		assertEquals(12L, d("12.99999999999999999999999999999999").longValue());
		assertEquals(0L, d("0.9999999999999999999999999999999999").longValue());
		assertEquals(0L, d("0.0000000000000000000000000000000001").longValue());
		assertEquals(42, d("42.9").intValue());
		assertEquals(Integer.MAX_VALUE, d("1e10").intValue());
		assertEquals(Integer.MIN_VALUE, d("-1e10").intValue());
	}

	// ----------------------------------------------------------------------------------
	// Properties
	// ----------------------------------------------------------------------------------

	public void testKinds() throws Exception {
		assertTrue(NAN.isNaN());
		assertFalse(NAN.isFinite());
		assertFalse(NAN.isInfinite());
		assertTrue(INF.isInfinite());
		assertTrue(NINF.isInfinite());
		assertFalse(INF.isNaN());
		assertFalse(INF.isFinite());
		assertTrue(ZERO.isFinite());
		assertTrue(ZERO.isZero());
		assertTrue(NZERO.isZero());
		assertTrue(d("1").isFinite());
		assertFalse(d("1").isZero());
		assertFalse(d("1").isNaN());
		assertFalse(d("1").isInfinite());
		assertTrue(Decimal128.MAX_VALUE.isFinite());
		assertTrue(Decimal128.MIN_VALUE.isFinite());

		assertTrue(d("5").isInteger());
		assertTrue(d("5e30").isInteger());
		assertTrue(d("1e6144").isInteger());
		assertTrue(ZERO.isInteger());
		assertTrue(NZERO.isInteger());
		assertTrue(d("5.0").isInteger());
		assertFalse(d("5.5").isInteger());
		assertFalse(d("1e-30").isInteger());
		assertFalse(NAN.isInteger());
		assertFalse(INF.isInteger());

		assertEquals(1, d("5").signum());
		assertEquals(-1, d("-5").signum());
		assertEquals(0, ZERO.signum());
		assertEquals(0, NZERO.signum());
		assertEquals(0, NAN.signum());
		assertEquals(1, INF.signum());
		assertEquals(-1, NINF.signum());
		assertTrue(NZERO.isNegative());
		assertTrue(NINF.isNegative());
		assertFalse(NAN.isNegative());
		assertFalse(ZERO.isNegative());
	}

	public void testExponentAndMantissa() throws Exception {
		assertEquals(0.0, d("1").exponent());
		assertEquals(0.0, d("9.99").exponent());
		assertEquals(1.0, d("10").exponent());
		assertEquals(2.0, d("123.45").exponent());
		assertEquals(-3.0, d("0.00123").exponent());
		assertEquals(-6176.0, Decimal128.MIN_VALUE.exponent());
		assertEquals(6144.0, Decimal128.MAX_VALUE.exponent());
		assertEquals(33.0, d(nines(34)).exponent());
		assertEquals(-3.0, d("-0.00123").exponent());
		assertEquals(Double.POSITIVE_INFINITY, INF.exponent());
		assertEquals(Double.POSITIVE_INFINITY, NINF.exponent());
		assertEquals(Double.NEGATIVE_INFINITY, ZERO.exponent());
		assertEquals(Double.NEGATIVE_INFINITY, NZERO.exponent());
		assertTrue(Double.isNaN(NAN.exponent()));

		assertDec("1.2345", d("123.45").mantissa());
		assertDec("-1.2345", d("-123.45").mantissa());
		assertDec("1.23", d("0.00123").mantissa());
		assertDec("1", d("1000").mantissa());
		assertDec("5", d("5").mantissa());
		assertDec("1", d("1e6144").mantissa());
		assertDec("1", d("1e6140").mantissa());
		assertDec("1.234567890123456789012345678901234", d("12345678901234567890123456789012.34").mantissa());
		assertDec("9.999999999999999999999999999999999", Decimal128.MAX_VALUE.mantissa());
		assertDec("1", Decimal128.MIN_VALUE.mantissa());
		assertDec("0", ZERO.mantissa());
		assertDec("-0", NZERO.mantissa());
		assertDec("NaN", NAN.mantissa());
		assertDec("Infinity", INF.mantissa());
		assertDec("Infinity", NINF.mantissa());
	}

	// ----------------------------------------------------------------------------------
	// Arithmetic
	// ----------------------------------------------------------------------------------

	public void testAbsAndNegate() throws Exception {
		assertDec("1.5", d("-1.5").abs());
		assertDec("1.5", d("1.5").abs());
		assertDec("0", NZERO.abs());
		assertFalse(NZERO.abs().isNegative());
		assertDec("Infinity", NINF.abs());
		assertDec("NaN", NAN.abs());
		assertDec("-1.5", d("1.5").negate());
		assertDec("1.5", d("-1.5").negate());
		assertDec("-0", ZERO.negate());
		assertDec("0", NZERO.negate());
		assertDec("-Infinity", INF.negate());
		assertDec("Infinity", NINF.negate());
		assertDec("NaN", NAN.negate());
		assertSame(NAN, NAN.negate());
		Decimal128 big = d("12345678901234567890123456789012");
		assertEquals(big, big.negate().negate());
		assertEquals(big, big.negate().abs());
	}

	public void testAdd() throws Exception {
		assertDec("0.3", d("0.1").add(d("0.2")));
		assertDec("3", d("1").add(d("2")));
		assertDec("10", d("5").add(d("5")));
		assertDec("0.25", d("0.125").add(d("0.125")));
		assertDec("-1", d("1").add(d("-2")));
		assertDec("0", d("1").add(d("-1")));
		assertFalse(d("1").add(d("-1")).isNegative());
		assertDec("123.456", d("123").add(d("0.456")));
		assertDec("1000000000000000000000", d("999999999999999999999").add(d("1")));
		assertDec("9223372036854775808", d("9223372036854775807").add(d("1")));
		assertDec("-9223372036854775809", d("-9223372036854775808").add(d("-1")));
		assertDec("1e1000", d("1e1000").add(d("1")));
		assertDec("1e40", d("1e40").add(d("1e-40")));
		assertDec("1e100", d("1e100").add(d("1")));
		assertDec("1.000000000000000000000000000000001e34", d("1e34").add(d("1e1")));
		assertDec("1e-6176", Decimal128.MIN_VALUE.add(ZERO));
		assertDec("2e-6176", Decimal128.MIN_VALUE.add(Decimal128.MIN_VALUE));
		assertDec("Infinity", Decimal128.MAX_VALUE.add(Decimal128.MAX_VALUE));
		assertDec(Decimal128.MAX_VALUE.toString(), Decimal128.MAX_VALUE.add(d("1")));
		assertDec("-Infinity", Decimal128.MAX_VALUE.negate().add(Decimal128.MAX_VALUE.negate()));
		// Rounding: half even
		assertDec("1e34", d("9999999999999999999999999999999999").add(d("1")));
		assertDec("1" + zeros(32) + "2", d("1" + zeros(32) + "1").add(d("0.5")));
		assertDec("1" + zeros(32) + "2", d("1" + zeros(32) + "2").add(d("0.5")));
		assertDec("1" + zeros(32) + "3", d("1" + zeros(32) + "2").add(d("0.51")));
		// A tiny operand only makes the result inexact
		assertDec("1", d("1").add(d("1e-100")));
		assertDec("100000000000000000000000000000000", d("99999999999999999999999999999999.9999999999").add(d("0.0000000001")).add(ZERO));
		assertDec("1e-6176", d("1e-6176").add(d("1e-100000")));
		assertDec("-1", d("-1").add(d("1e-100")));

		// Zeros
		assertDec("0", ZERO.add(ZERO));
		assertDec("0", ZERO.add(NZERO));
		assertDec("0", NZERO.add(ZERO));
		assertDec("-0", NZERO.add(NZERO));
		assertDec("1.5", ZERO.add(d("1.5")));
		assertDec("1.5", NZERO.add(d("1.5")));
		assertDec("1.5", d("1.5").add(ZERO));
		assertDec("1.5", d("1.5").add(NZERO));
		// Specials
		assertDec("NaN", NAN.add(d("1")));
		assertDec("NaN", d("1").add(NAN));
		assertDec("NaN", NAN.add(INF));
		assertDec("Infinity", INF.add(d("1")));
		assertDec("Infinity", d("1").add(INF));
		assertDec("Infinity", INF.add(INF));
		assertDec("NaN", INF.add(NINF));
		assertDec("NaN", NINF.add(INF));
		assertDec("-Infinity", NINF.add(d("1")));
		assertDec("-Infinity", d("1").add(NINF));
		assertDec("-Infinity", NINF.add(NINF));
		assertDec("Infinity", INF.add(ZERO));
		assertDec("-Infinity", NINF.add(NZERO));
	}

	public void testSubtract() throws Exception {
		assertDec("0.9", d("1").subtract(d("0.1")));
		assertDec("0.1", d("1").subtract(d("0.9")));
		assertDec("-1", d("1").subtract(d("2")));
		assertDec("3", d("1").subtract(d("-2")));
		assertDec("0", d("1.5").subtract(d("1.50")));
		assertFalse(d("1.5").subtract(d("1.5")).isNegative());
		assertDec("-9223372036854775809", d("-9223372036854775808").subtract(d("1")));
		assertDec("9223372036854775808", d("0").subtract(d("-9223372036854775808")));
		assertDec("999999999999999999999999999999999", d("1e33").subtract(d("1")));
		assertDec("1" + zeros(33), d("1e33").subtract(d("1e-100")));
		assertDec(nines(34), d("1e34").subtract(d("1")));
		// Zeros
		assertDec("0", ZERO.subtract(ZERO));
		assertDec("0", NZERO.subtract(NZERO));
		assertDec("-0", NZERO.subtract(ZERO));
		assertDec("0", ZERO.subtract(NZERO));
		assertDec("1.5", d("1.5").subtract(ZERO));
		assertDec("1.5", d("1.5").subtract(NZERO));
		assertDec("-1.5", ZERO.subtract(d("1.5")));
		assertDec("-1.5", NZERO.subtract(d("1.5")));
		assertDec("1.5", ZERO.subtract(d("-1.5")));
		// Specials
		assertDec("NaN", NAN.subtract(d("1")));
		assertDec("NaN", d("1").subtract(NAN));
		assertDec("NaN", INF.subtract(INF));
		assertDec("NaN", NINF.subtract(NINF));
		assertDec("Infinity", INF.subtract(NINF));
		assertDec("-Infinity", NINF.subtract(INF));
		assertDec("Infinity", INF.subtract(d("5")));
		assertDec("-Infinity", NINF.subtract(d("5")));
		assertDec("-Infinity", d("5").subtract(INF));
		assertDec("Infinity", d("5").subtract(NINF));
		assertDec("Infinity", INF.subtract(ZERO));
	}

	public void testMultiply() throws Exception {
		assertDec("3", d("1.5").multiply(d("2")));
		assertDec("0.02", d("0.1").multiply(d("0.2")));
		assertDec("-6", d("2").multiply(d("-3")));
		assertDec("6", d("-2").multiply(d("-3")));
		assertDec("1" + zeros(20), d("1e10").multiply(d("1e10")));
		assertDec("1e-20", d("1e-10").multiply(d("1e-10")));
		assertDec("8.50705917302346158473969077842325e37", d("9223372036854775807").multiply(d("9223372036854775807")));
		assertSameNumber("square", new BigDecimal("123456789012345678").pow(2).round(MC34), d("123456789012345678").multiply(d("123456789012345678")));
		assertDec("1e6144", d("1e3000").multiply(d("1e3144")));
		assertDec("Infinity", d("1e3000").multiply(d("1e3145")));
		assertDec("-Infinity", d("-1e3000").multiply(d("1e3145")));
		assertDec("1e-6176", d("1e-3000").multiply(d("1e-3176")));
		assertDec("0", d("1e-3000").multiply(d("1e-3177")));
		assertDec("-0", d("-1e-3000").multiply(d("1e-3177")));
		assertDec("9.999999999999999999999999999999998e67", d(nines(34)).multiply(d(nines(34))));
		assertDec("4", d("2").multiply(d("2")));
		assertDec("1e34", d("1e17").multiply(d("1e17")));
		// Zeros
		assertDec("0", ZERO.multiply(d("5")));
		assertDec("-0", ZERO.multiply(d("-5")));
		assertDec("-0", NZERO.multiply(d("5")));
		assertDec("0", NZERO.multiply(d("-5")));
		assertDec("0", ZERO.multiply(ZERO));
		assertDec("-0", ZERO.multiply(NZERO));
		assertDec("0", NZERO.multiply(NZERO));
		// Specials
		assertDec("NaN", NAN.multiply(d("1")));
		assertDec("NaN", d("0").multiply(NAN));
		assertDec("NaN", INF.multiply(ZERO));
		assertDec("NaN", ZERO.multiply(INF));
		assertDec("NaN", NINF.multiply(NZERO));
		assertDec("NaN", NZERO.multiply(NINF));
		assertDec("Infinity", INF.multiply(d("2")));
		assertDec("-Infinity", INF.multiply(d("-2")));
		assertDec("-Infinity", NINF.multiply(d("2")));
		assertDec("Infinity", NINF.multiply(d("-2")));
		assertDec("Infinity", INF.multiply(INF));
		assertDec("-Infinity", INF.multiply(NINF));
		assertDec("Infinity", NINF.multiply(NINF));
	}

	public void testDivide() throws Exception {
		assertDec("0.5", d("1").divide(d("2")));
		assertDec("0.125", d("1").divide(d("8")));
		assertDec("2.5", d("10").divide(d("4")));
		assertDec("0." + "3".repeat(34), d("1").divide(d("3")));
		assertDec("0." + "6".repeat(33) + "7", d("2").divide(d("3")));
		assertDec("-0." + "3".repeat(34), d("-1").divide(d("3")));
		assertDec("0." + "3".repeat(34), d("-1").divide(d("-3")));
		assertDec("3.333333333333333333333333333333333", d("10").divide(d("3")));
		assertDec("333.3333333333333333333333333333333", d("1000").divide(d("3")));
		assertDec("1", d("1.5").divide(d("1.5")));
		assertDec("0.1", d("1").divide(d("10")));
		assertDec("1" + zeros(10), d("1e20").divide(d("1e10")));
		assertDec("1e-10", d("1").divide(d("1e10")));
		assertDec("12345", d("12345e50").divide(d("1e50")));
		assertDec("3", d("9223372036854775809").divide(d("3074457345618258603")));
		assertDec("0.0001", d("1").divide(d("10000")));
		assertDec("1.428571428571428571428571428571429", d("10").divide(d("7")));
		assertDec("1e6144", d("1e6144").divide(d("1")));
		assertDec("Infinity", d("1e6144").divide(d("0.1")));
		assertDec("1e-6176", d("1e-6176").divide(d("1")));
		assertDec("0", d("1e-6176").divide(d("2")));			// 0.5 ties to the even 0
		assertDec("2e-6176", d("3e-6176").divide(d("2")));		// 1.5 ties to the even 2
		assertDec("-0", d("-1e-6176").divide(d("3")));
		assertDec(nines(34), d(nines(34)).divide(d("1")));
		assertDec("1", d(nines(34)).divide(d(nines(34))));
		// Zeros
		assertDec("0", ZERO.divide(d("5")));
		assertDec("-0", ZERO.divide(d("-5")));
		assertDec("-0", NZERO.divide(d("5")));
		assertDec("0", NZERO.divide(d("-5")));
		assertDec("Infinity", d("5").divide(ZERO));
		assertDec("-Infinity", d("-5").divide(ZERO));
		assertDec("-Infinity", d("5").divide(NZERO));
		assertDec("Infinity", d("-5").divide(NZERO));
		assertDec("NaN", ZERO.divide(ZERO));
		assertDec("NaN", NZERO.divide(ZERO));
		assertDec("NaN", ZERO.divide(NZERO));
		// Specials
		assertDec("NaN", NAN.divide(d("1")));
		assertDec("NaN", d("1").divide(NAN));
		assertDec("NaN", INF.divide(INF));
		assertDec("NaN", INF.divide(NINF));
		assertDec("NaN", NINF.divide(INF));
		assertDec("Infinity", INF.divide(d("2")));
		assertDec("-Infinity", INF.divide(d("-2")));
		assertDec("-Infinity", NINF.divide(d("2")));
		assertDec("Infinity", NINF.divide(d("-2")));
		assertDec("Infinity", INF.divide(ZERO));
		assertDec("0", d("5").divide(INF));
		assertDec("-0", d("-5").divide(INF));
		assertDec("-0", d("5").divide(NINF));
		assertDec("0", d("-5").divide(NINF));
		assertDec("0", ZERO.divide(INF));
	}

	public void testRemainder() throws Exception {
		assertDec("2", d("5").remainder(d("3")));
		assertDec("-2", d("-5").remainder(d("3")));
		assertDec("2", d("5").remainder(d("-3")));
		assertDec("-2", d("-5").remainder(d("-3")));
		assertDec("6", d("46").remainder(d("10")));		// Not the IEEE 754 -4
		assertDec("1.5", d("5.5").remainder(d("2")));
		assertDec("0", d("0.3").remainder(d("0.1")));
		assertDec("0.1", d("0.7").remainder(d("0.3")));
		assertDec("0", d("4").remainder(d("2")));
		assertDec("-0", d("-4").remainder(d("2")));
		assertDec("-0", d("-4").remainder(d("-2")));
		assertDec("0", d("4").remainder(d("-2")));
		assertDec("3", d("3").remainder(d("5")));
		assertDec("-3", d("-3").remainder(d("5")));
		assertDec("0.5", d("0.5").remainder(d("1e10")));
		assertDec("0", d("1e20").remainder(d("1e10")));
		assertDec("1", d("1e6000").remainder(d("7")));		// 10^6 = 1 mod 7
		assertDec("3", d("1e6001").remainder(d("7")));
		assertDec("1e-34", d("1e-34").remainder(d("1")));
		assertDec("0.5", d("12345678901234567890123456789012.5").remainder(d("1")));
		assertDec("123456789012345678901234", d("1234567890123456789012345678901234").remainder(d("1e25")));
		assertDec("0.00001", d("100.00001").remainder(d("100")));
		assertDec("1e-6176", d("3e-6176").remainder(d("2e-6176")));
		assertDec("-1e-6176", d("-3e-6176").remainder(d("2e-6176")));
		// Zeros
		assertDec("NaN", d("5").remainder(ZERO));
		assertDec("NaN", d("5").remainder(NZERO));
		assertDec("NaN", ZERO.remainder(ZERO));
		assertDec("0", ZERO.remainder(d("5")));
		assertDec("-0", NZERO.remainder(d("5")));
		assertDec("-0", NZERO.remainder(d("-5")));
		// Specials
		assertDec("NaN", NAN.remainder(d("5")));
		assertDec("NaN", d("5").remainder(NAN));
		assertDec("NaN", INF.remainder(d("5")));
		assertDec("NaN", NINF.remainder(d("5")));
		assertDec("NaN", INF.remainder(INF));
		assertDec("5", d("5").remainder(INF));
		assertDec("-5", d("-5").remainder(NINF));
		assertDec("0", ZERO.remainder(INF));
		assertDec("-0", NZERO.remainder(INF));
	}

	public void testRound() throws Exception {
		// Half even is the default
		assertDec("2", d("1.5").round(0));
		assertDec("2", d("2.5").round(0));
		assertDec("4", d("3.5").round(0));
		assertDec("-2", d("-2.5").round(0));
		assertDec("1", d("1.4999").round(0));
		assertDec("2", d("1.5001").round(0));
		assertDec("1.23", d("1.2345").round(2));
		assertDec("1.24", d("1.235").round(2));
		assertDec("1.24", d("1.245").round(2));
		assertDec("1.2345", d("1.2345").round(4));
		assertDec("1.2345", d("1.2345").round(10));
		assertDec("1.2345", d("1.2345").round(6000));
		assertDec("100", d("100").round(2));
		assertDec("1" + zeros(30), d("1e30").round(0));
		assertDec("1000", d("999.9").round(0));
		assertDec("1000", d("999.99").round(1));
		assertDec("1000000000000000000000000000000000", d("999999999999999999999999999999999.9").round(0));
		assertDec(nines(34), d(nines(34)).round(0));
		assertDec("12345678901234567890123456789012", d("12345678901234567890123456789012.4").round(0));
		assertDec("12345678901234567890123456789013", d("12345678901234567890123456789012.6").round(0));
		assertDec("12345678901234567890123456789012.3", d("12345678901234567890123456789012.34").round(1));
		assertDec("9223372036854775808", d("9223372036854775807.5").round(0));
		assertDec("0.001", d("0.0005001").round(3));
		assertDec("0", d("0.0004999").round(3));
		assertDec("0", d("1e-6000").round(10));
		assertDec("1e-10", d("1e-10").round(10));
		assertDec("0", d("0.5").round(0));
		assertDec("1", d("0.5000000000000000000000000000000001").round(0));
		assertDec("2", d("1.5").round(0, DecimalRoundingMode.HALF_EVEN));
		// Each mode, on both signs
		assertDec("2", d("1.2").round(0, DecimalRoundingMode.CEIL));
		assertDec("-1", d("-1.2").round(0, DecimalRoundingMode.CEIL));
		assertDec("1", d("1.8").round(0, DecimalRoundingMode.FLOOR));
		assertDec("-2", d("-1.2").round(0, DecimalRoundingMode.FLOOR));
		assertDec("1", d("1.9").round(0, DecimalRoundingMode.TRUNC));
		assertDec("-1", d("-1.9").round(0, DecimalRoundingMode.TRUNC));
		assertDec("3", d("2.5").round(0, DecimalRoundingMode.HALF_EXPAND));
		assertDec("-3", d("-2.5").round(0, DecimalRoundingMode.HALF_EXPAND));
		assertDec("2", d("2.4").round(0, DecimalRoundingMode.HALF_EXPAND));
		assertDec("1.24", d("1.2345").round(2, DecimalRoundingMode.CEIL));
		assertDec("-1.23", d("-1.2345").round(2, DecimalRoundingMode.CEIL));
		assertDec("1.23", d("1.2345").round(2, DecimalRoundingMode.FLOOR));
		assertDec("-1.24", d("-1.2345").round(2, DecimalRoundingMode.FLOOR));
		assertDec("1", d("1").round(0, DecimalRoundingMode.CEIL));
		assertDec("1", d("1.0").round(0, DecimalRoundingMode.FLOOR));
		assertDec("1.5", d("1.5").round(3, DecimalRoundingMode.CEIL));
		assertDec("1", d("0.0000000000000000000000000000000000001").round(0, DecimalRoundingMode.CEIL));
		assertDec("-1", d("-0.0000000000000000000000000000000000001").round(0, DecimalRoundingMode.FLOOR));
		assertDec("1", d("1e-6000").round(0, DecimalRoundingMode.CEIL));
		assertDec("9223372036854775809", d("9223372036854775808.1").round(0, DecimalRoundingMode.CEIL));
		// A value that rounds to zero is +0, as the proposal says
		assertDec("0", d("0.4").round(0));
		assertDec("0", d("-0.4").round(0));
		assertFalse(d("-0.4").round(0).isNegative());
		assertDec("0", d("-0.4").round(0, DecimalRoundingMode.CEIL));
		// The rest is unchanged
		assertDec("NaN", NAN.round(2));
		assertDec("Infinity", INF.round(2));
		assertDec("-Infinity", NINF.round(2));
		assertDec("0", ZERO.round(2));
		assertDec("-0", NZERO.round(2));
		assertThrows(IllegalArgumentException.class, () -> d("1").round(-1));
		assertThrows(NullPointerException.class, () -> d("1").round(1, null));
	}

	public void testScale10() throws Exception {
		assertDec("1500", d("1.5").scale10(3));
		assertDec("0.0015", d("1.5").scale10(-3));
		assertDec("1e-7", d("1").scale10(-7));
		assertDec("-1" + zeros(7), d("-1").scale10(7));
		assertDec("1.5", d("1.5").scale10(0));
		assertDec("1e6144", d("1").scale10(6144));
		assertDec("Infinity", d("1").scale10(6145));
		assertDec("-Infinity", d("-1").scale10(6145));
		assertDec("1e-6176", d("1").scale10(-6176));
		assertDec("0", d("1").scale10(-6177));
		assertDec("-0", d("-1").scale10(-6177));
		assertDec("1e-6176", d("10").scale10(-6177));
		assertDec("0", d("4").scale10(-6177));			// Under half of the smallest value
		assertDec("1e-6176", d("6").scale10(-6177));
		assertDec("Infinity", d("1").scale10(Long.MAX_VALUE));
		assertDec("0", d("1").scale10(Long.MIN_VALUE));
		assertDec("1e6144", d("1e-6144").scale10(12288));
		assertDec("9.999999999999999999999999999999999e6144", d(nines(34)).scale10(6111));
		assertDec("Infinity", d(nines(34)).scale10(6112));
		assertDec("1.234567890123456789012345678901234e49", d("12345678901234567890123456789012.34").scale10(18));
		assertDec("0", ZERO.scale10(5));
		assertDec("-0", NZERO.scale10(-5));
		assertDec("NaN", NAN.scale10(5));
		assertDec("Infinity", INF.scale10(-5));
		assertDec("-Infinity", NINF.scale10(5));

		// A Number as the exponent
		assertDec("1500", d("1.5").scale10(3.0));
		assertDec("1.5", d("1.5").scale10(-0.0));
		assertDec("NaN", d("1.5").scale10(Double.NaN));
		assertDec("Infinity", d("1.5").scale10(Double.POSITIVE_INFINITY));
		assertDec("-Infinity", d("-1.5").scale10(Double.POSITIVE_INFINITY));
		assertDec("0", d("1.5").scale10(Double.NEGATIVE_INFINITY));
		assertDec("-0", d("-1.5").scale10(Double.NEGATIVE_INFINITY));
		assertDec("Infinity", d("1.5").scale10(1e300));
		assertDec("0", d("1.5").scale10(-1e300));
		assertDec("NaN", NAN.scale10(Double.POSITIVE_INFINITY));
		assertDec("0", ZERO.scale10(Double.NaN));
		assertDec("-0", NZERO.scale10(Double.POSITIVE_INFINITY));
		assertDec("Infinity", INF.scale10(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> d("1").scale10(1.5));
	}

	// ----------------------------------------------------------------------------------
	// Comparisons
	// ----------------------------------------------------------------------------------

	public void testComparisons() throws Exception {
		assertEquals(0, d("1").compare(d("1.0")));
		assertEquals(0, d("1").compare(d("1")));
		assertEquals(-1, d("1").compare(d("2")));
		assertEquals(1, d("2").compare(d("1")));
		assertEquals(-1, d("-2").compare(d("1")));
		assertEquals(1, d("1").compare(d("-2")));
		assertEquals(1, d("-1").compare(d("-2")));
		assertEquals(-1, d("-2").compare(d("-1")));
		assertEquals(-1, d("0.9").compare(d("1")));
		assertEquals(1, d("10").compare(d("9.99")));
		assertEquals(-1, d("1e5").compare(d("1e6")));
		assertEquals(1, d("1e-5").compare(d("1e-6")));
		assertEquals(0, d("1e2").compare(d("100")));
		assertEquals(-1, d("99").compare(d("100")));
		assertEquals(1, d("123456789012345678901234567890.1").compare(d("123456789012345678901234567890")));
		assertEquals(-1, d("1.000000000000000000000000000000001").compare(d("1.000000000000000000000000000000002")));
		assertEquals(0, d("1.0000000000000000000000000000000001").compare(d("1.0000000000000000000000000000000002")));
		assertEquals(-1, d("9223372036854775807").compare(d("9223372036854775808")));
		assertEquals(1, d("9223372036854775808").compare(d("9223372036854775807")));
		assertEquals(1, d("9223372036854775808.5").compare(d("9223372036854775808")));
		assertEquals(0, d("1.5e-6176").compare(d("2e-6176")));
		assertEquals(-1, d("1.4e-6175").compare(d("2e-6175")));
		assertEquals(-1, Decimal128.MIN_VALUE.compare(Decimal128.MAX_VALUE));
		assertEquals(1, Decimal128.MAX_VALUE.compare(Decimal128.MIN_VALUE));
		assertEquals(1, d("1e6144").compare(d("9.99e6143")));
		assertEquals(1, d("5000000000").compare(d("4999999999")));
		assertEquals(1, d("0.5").compare(d("0.45")));
		assertEquals(-1, d("0.45").compare(d("0.5")));
		assertEquals(-1, d("4.999999999999999999999999999999999").compare(d("5")));
		// Zeros
		assertEquals(0, ZERO.compare(NZERO));
		assertEquals(0, NZERO.compare(ZERO));
		assertEquals(-1, ZERO.compare(d("1e-6176")));
		assertEquals(1, ZERO.compare(d("-1e-6176")));
		assertEquals(1, d("1e-6176").compare(NZERO));
		assertEquals(-1, d("-1e-6176").compare(NZERO));
		// Specials
		assertEquals(Decimal128.UNORDERED, NAN.compare(d("1")));
		assertEquals(Decimal128.UNORDERED, d("1").compare(NAN));
		assertEquals(Decimal128.UNORDERED, NAN.compare(NAN));
		assertEquals(Decimal128.UNORDERED, NAN.compare(INF));
		assertEquals(0, INF.compare(INF));
		assertEquals(0, NINF.compare(NINF));
		assertEquals(1, INF.compare(NINF));
		assertEquals(-1, NINF.compare(INF));
		assertEquals(1, INF.compare(Decimal128.MAX_VALUE));
		assertEquals(-1, Decimal128.MAX_VALUE.compare(INF));
		assertEquals(-1, NINF.compare(Decimal128.MAX_VALUE.negate()));
		assertEquals(1, Decimal128.MAX_VALUE.negate().compare(NINF));
		assertEquals(1, INF.compare(ZERO));
	}

	public void testPredicates() throws Exception {
		Decimal128 one = d("1");
		Decimal128 two = d("2");
		assertTrue(one.equalTo(d("1.00")));
		assertFalse(one.equalTo(two));
		assertTrue(ZERO.equalTo(NZERO));
		assertTrue(INF.equalTo(INF));
		assertFalse(INF.equalTo(NINF));
		assertFalse(NAN.equalTo(NAN));
		assertFalse(NAN.equalTo(one));
		assertFalse(one.equalTo(NAN));

		assertFalse(one.notEquals(d("1.00")));
		assertTrue(one.notEquals(two));
		assertFalse(ZERO.notEquals(NZERO));
		assertFalse(INF.notEquals(INF));
		assertTrue(INF.notEquals(NINF));
		assertTrue(INF.notEquals(one));
		assertFalse(NAN.notEquals(NAN));
		assertFalse(NAN.notEquals(one));
		assertFalse(one.notEquals(NAN));

		assertTrue(one.lessThan(two));
		assertFalse(two.lessThan(one));
		assertFalse(one.lessThan(one));
		assertFalse(ZERO.lessThan(NZERO));
		assertTrue(NINF.lessThan(one));
		assertFalse(INF.lessThan(one));
		assertTrue(one.lessThan(INF));
		assertFalse(NINF.lessThan(NINF));
		assertFalse(NAN.lessThan(one));
		assertFalse(one.lessThan(NAN));

		assertTrue(one.lessThanOrEqual(two));
		assertTrue(one.lessThanOrEqual(d("1.0")));
		assertFalse(two.lessThanOrEqual(one));
		assertTrue(ZERO.lessThanOrEqual(NZERO));
		assertTrue(INF.lessThanOrEqual(INF));
		assertFalse(INF.lessThanOrEqual(one));
		assertTrue(NINF.lessThanOrEqual(NINF));
		assertFalse(NAN.lessThanOrEqual(NAN));
		assertFalse(one.lessThanOrEqual(NAN));

		assertTrue(two.greaterThan(one));
		assertFalse(one.greaterThan(two));
		assertFalse(one.greaterThan(one));
		assertFalse(NZERO.greaterThan(ZERO));
		assertTrue(INF.greaterThan(one));
		assertFalse(INF.greaterThan(INF));
		assertTrue(one.greaterThan(NINF));
		assertFalse(NAN.greaterThan(one));
		assertFalse(one.greaterThan(NAN));

		assertTrue(two.greaterThanOrEqual(one));
		assertTrue(one.greaterThanOrEqual(d("1.0")));
		assertFalse(one.greaterThanOrEqual(two));
		assertTrue(NZERO.greaterThanOrEqual(ZERO));
		assertTrue(INF.greaterThanOrEqual(INF));
		assertFalse(NINF.greaterThanOrEqual(one));
		assertFalse(NAN.greaterThanOrEqual(NAN));
		assertFalse(one.greaterThanOrEqual(NAN));
	}

	public void testEqualsAndTotalOrder() throws Exception {
		assertEquals(d("1.5"), d("1.50"));
		assertEquals(NAN, d("NaN"));
		assertFalse(ZERO.equals(NZERO));
		assertFalse(d("1").equals(d("2")));
		assertFalse(d("1").equals("1"));
		assertFalse(d("1").equals(null));
		assertEquals(NAN.hashCode(), NAN.hashCode());
		assertTrue(ZERO.hashCode() != NZERO.hashCode() || ZERO.highBits() != NZERO.highBits());

		List<Decimal128> sorted = new ArrayList<>();
		for (String s : new String[] { "-Infinity", "-1e6144", "-5", "-1.5", "-1e-6176", "-0", "0", "1e-6176", "0.5", "1", "1.5", "10", "1e34", "1e6144", "Infinity", "NaN" }) {
			sorted.add(d(s));
		}
		List<Decimal128> shuffled = new ArrayList<>(sorted);
		Collections.shuffle(shuffled, new Random(7));
		Collections.sort(shuffled);
		assertEquals(sorted, shuffled);
		for (int i = 0; i < sorted.size(); i++) {
			for (int j = 0; j < sorted.size(); j++) {
				assertEquals(Integer.signum(Integer.compare(i, j)), Integer.signum(sorted.get(i).compareTo(sorted.get(j))));
			}
		}
		assertEquals(0, NAN.compareTo(d("NaN")));
		assertTrue(NZERO.compareTo(ZERO) < 0);
		assertTrue(ZERO.compareTo(NZERO) > 0);
	}

	public void testSerializable() throws Exception {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(out)) {
			oos.writeObject(d("12345678901234567890123456789012.34"));
			oos.writeObject(NAN);
		}
		try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(out.toByteArray()))) {
			assertEquals(d("12345678901234567890123456789012.34"), ois.readObject());
			assertEquals(NAN, ois.readObject());
		}
	}

	public void testRoundingModeNames() throws Exception {
		assertEquals(DecimalRoundingMode.HALF_EVEN, DecimalRoundingMode.DEFAULT);
		for (DecimalRoundingMode m : DecimalRoundingMode.values()) {
			assertEquals(m, DecimalRoundingMode.fromName(m.getName()));
			assertEquals(m, DecimalRoundingMode.from(m.toRoundingMode()));
		}
		assertEquals(DecimalRoundingMode.CEIL, DecimalRoundingMode.fromName("ceil"));
		assertEquals(DecimalRoundingMode.FLOOR, DecimalRoundingMode.fromName("floor"));
		assertEquals(DecimalRoundingMode.TRUNC, DecimalRoundingMode.fromName("trunc"));
		assertEquals(DecimalRoundingMode.HALF_EXPAND, DecimalRoundingMode.fromName("halfExpand"));
		assertEquals(DecimalRoundingMode.HALF_EVEN, DecimalRoundingMode.fromName("halfEven"));
		assertThrows(IllegalArgumentException.class, () -> DecimalRoundingMode.fromName("up"));
		assertThrows(IllegalArgumentException.class, () -> DecimalRoundingMode.fromName("HALF_EVEN"));
		assertThrows(IllegalArgumentException.class, () -> DecimalRoundingMode.from(RoundingMode.UP));
		assertThrows(IllegalArgumentException.class, () -> DecimalRoundingMode.from(RoundingMode.HALF_DOWN));
		assertThrows(IllegalArgumentException.class, () -> DecimalRoundingMode.from(RoundingMode.UNNECESSARY));
	}

	// ----------------------------------------------------------------------------------
	// Random operands against BigDecimal
	// ----------------------------------------------------------------------------------

	private static BigDecimal randomBig(Random r) {
		int digits;
		switch (r.nextInt(4)) {
		case 0:
			digits = 1 + r.nextInt(6);
			break;
		case 1:
			digits = 1 + r.nextInt(18);
			break;
		default:
			digits = 1 + r.nextInt(34);
			break;
		}
		StringBuilder sb = new StringBuilder();
		sb.append((char) ('1' + r.nextInt(9)));
		for (int i = 1; i < digits; i++) {
			// Many 0s and 9s to hit the carries and the trailing zeros
			int k = r.nextInt(10);
			sb.append(k < 3 ? '0' : k < 5 ? '9' : (char) ('0' + r.nextInt(10)));
		}
		int scale;
		switch (r.nextInt(5)) {
		case 0:
			scale = r.nextInt(80) - 40;
			break;
		case 1:
			scale = r.nextInt(10) - 5;
			break;
		case 2:
			scale = r.nextInt(400) - 200;
			break;
		default:
			scale = digits - 1 - (r.nextInt(2) == 0 ? r.nextInt(3) : 0);
			break;
		}
		BigDecimal v = new BigDecimal(new BigInteger(sb.toString()), scale);
		return r.nextBoolean() ? v : v.negate();
	}

	private static void assertSameNumber(String message, BigDecimal expected, Decimal128 actual) {
		assertTrue(message + ": " + actual, actual.isFinite());
		assertEquals(message + ": " + actual + " instead of " + expected, 0, expected.compareTo(actual.toBigDecimal()));
		assertCanonical(actual);
	}

	public void testRandomArithmetic() throws Exception {
		Random r = new Random(20260628);
		for (int i = 0; i < 20000; i++) {
			BigDecimal ba = randomBig(r);
			BigDecimal bb = r.nextInt(8) == 0 ? ba.add(randomBig(r).movePointLeft(r.nextInt(40))) : randomBig(r);
			Decimal128 a = Decimal128.of(ba);
			Decimal128 b = Decimal128.of(bb);
			BigDecimal ea = a.toBigDecimal();
			BigDecimal eb = b.toBigDecimal();
			assertEquals(0, ba.round(MC34).compareTo(ea));
			String ctx = ea + " op " + eb;

			assertSameNumber("add " + ctx, ea.add(eb, MC34), a.add(b));
			assertSameNumber("subtract " + ctx, ea.subtract(eb, MC34), a.subtract(b));
			assertSameNumber("multiply " + ctx, ea.multiply(eb, MC34), a.multiply(b));
			assertSameNumber("divide " + ctx, ea.divide(eb, MC34), a.divide(b));
			assertSameNumber("remainder " + ctx, ea.remainder(eb), a.remainder(b));

			assertEquals("compare " + ctx, ea.compareTo(eb), a.compare(b));
			assertEquals("compareTo " + ctx, ea.compareTo(eb), a.compareTo(b));
			assertEquals(ea.compareTo(eb) < 0, a.lessThan(b));
			assertEquals(ea.compareTo(eb) <= 0, a.lessThanOrEqual(b));
			assertEquals(ea.compareTo(eb) > 0, a.greaterThan(b));
			assertEquals(ea.compareTo(eb) >= 0, a.greaterThanOrEqual(b));
			assertEquals(ea.compareTo(eb) == 0, a.equalTo(b));
			assertEquals(ea.compareTo(eb) == 0, a.equals(b));

			assertSameNumber("negate", ea.negate(), a.negate());
			assertSameNumber("abs", ea.abs(), a.abs());
			assertEquals(ea.signum(), a.signum());
			assertEquals(ea.doubleValue(), a.doubleValue());
			assertEquals(ea.floatValue(), a.floatValue());
			assertEquals(ea.toBigInteger().compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0 ? Long.MIN_VALUE
					: ea.toBigInteger().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : ea.longValue(), a.longValue());
			assertEquals(ea.precision() - ea.scale() - 1, (int) a.exponent());
			assertSameNumber("mantissa", ea.movePointLeft(ea.precision() - ea.scale() - 1), a.mantissa());
			assertEquals(a, Decimal128.of(ea));
			assertEquals(a, Decimal128.fromBits(a.highBits(), a.lowBits()));
		}
	}

	public void testRandomRounding() throws Exception {
		Random r = new Random(4242);
		DecimalRoundingMode[] modes = DecimalRoundingMode.values();
		for (int i = 0; i < 20000; i++) {
			Decimal128 a = Decimal128.of(randomBig(r));
			BigDecimal ea = a.toBigDecimal();
			DecimalRoundingMode mode = modes[r.nextInt(modes.length)];
			int n = r.nextInt(r.nextBoolean() ? 6 : 45);
			String ctx = ea + " round(" + n + ", " + mode + ")";

			BigDecimal expected = ea.scale() > n ? ea.setScale(n, mode.toRoundingMode()) : ea;
			assertSameNumber(ctx, expected, a.round(n, mode));

			// Fixed number of fractional digits
			String fixed = expected.setScale(n, RoundingMode.UNNECESSARY).toPlainString();
			if (a.isNegative() && expected.signum() == 0) {
				fixed = "-" + fixed;
			}
			assertEquals("toFixed " + ctx, fixed, a.toFixed(n, mode));

			// Number of significant digits
			int p = 1 + r.nextInt(r.nextBoolean() ? 6 : 40);
			BigDecimal rounded = ea.round(new MathContext(p, mode.toRoundingMode()));
			String precision = a.toPrecision(p, mode);
			ctx = ea + " toPrecision(" + p + ", " + mode + ") = " + precision;
			assertEquals(ctx, 0, rounded.compareTo(new BigDecimal(precision)));
		}
	}

	public void testRandomParseAndString() throws Exception {
		Random r = new Random(99);
		for (int i = 0; i < 20000; i++) {
			BigDecimal ba = randomBig(r);
			// More digits than the precision
			if (r.nextInt(4) == 0) {
				ba = ba.multiply(new BigDecimal(new BigInteger(1 + r.nextInt(30), r).add(BigInteger.ONE)), new MathContext(70)).movePointLeft(r.nextInt(30));
			}
			String text = ba.toString();
			Decimal128 a = d(text);
			assertSameNumber(text, ba.round(MC34), a);
			assertEquals(a, d(a.toString()));
			assertEquals(a, d(a.toExponential()));
			assertEquals(0, new BigDecimal(a.toString()).compareTo(a.toBigDecimal()));
			assertEquals(0, new BigDecimal(a.toExponential()).compareTo(a.toBigDecimal()));
			assertEquals(a, d(ba.toPlainString()));
			assertEquals(a, d(ba.toEngineeringString()));
			assertEquals(a, d((r.nextBoolean() && !text.startsWith("-") ? "+" : "") + text.toLowerCase()));
		}
	}

	public void testRandomBigIntegerAndLong() throws Exception {
		Random r = new Random(5);
		for (int i = 0; i < 5000; i++) {
			long l = r.nextLong() >> r.nextInt(64);
			Decimal128 a = Decimal128.of(l);
			assertEquals(BigInteger.valueOf(l), a.toBigInteger());
			assertEquals(l, a.longValue());
			assertEquals(Long.toString(l), a.toBigInteger().toString());
			BigInteger bi = new BigInteger(1 + r.nextInt(200), r);
			Decimal128 b = Decimal128.of(bi);
			assertEquals(0, new BigDecimal(bi).round(MC34).compareTo(b.toBigDecimal()));
		}
	}

	public void testRandomNearLimits() throws Exception {
		// Operands close to the ends of the exponent range
		Random r = new Random(321);
		for (int i = 0; i < 5000; i++) {
			BigDecimal ba = new BigDecimal(new BigInteger(1 + r.nextInt(33), r).add(BigInteger.ONE), r.nextInt(30) - 6176 + (r.nextBoolean() ? 6170 + 6110 : 0));
			BigDecimal bb = new BigDecimal(new BigInteger(1 + r.nextInt(33), r).add(BigInteger.ONE), -(r.nextInt(30) + 6100));
			Decimal128 a = Decimal128.of(ba);
			Decimal128 b = Decimal128.of(bb);
			if (!a.isFinite() || !b.isFinite()) {
				continue;
			}
			BigDecimal expectedSum = a.toBigDecimal().add(b.toBigDecimal(), MC34);
			Decimal128 sum = a.add(b);
			if (expectedSum.precision() - expectedSum.scale() - 1 > 6144) {
				assertDec("Infinity", sum);
			} else {
				assertSameNumber("sum " + a + " " + b, expectedSum, sum);
			}
			assertCanonical(a.multiply(b));
			assertCanonical(a.divide(b));
			assertCanonical(b.divide(a));
		}
	}

	public void testPow() {
		assertEquals(Decimal128.of("1024"), Decimal128.of(2).pow(10));
		assertEquals(Decimal128.of("0.125"), Decimal128.of(2).pow(-3));
		assertEquals(Decimal128.of("-8"), Decimal128.of(-2).pow(3));
		assertEquals(Decimal128.of("16"), Decimal128.of(-2).pow(4));
		assertEquals(Decimal128.of("1.21"), Decimal128.of("1.1").pow(2));
		assertEquals(Decimal128.ONE, Decimal128.NaN.pow(0));
		assertEquals(Decimal128.ONE, Decimal128.ZERO.pow(0));
		assertEquals(Decimal128.NaN, Decimal128.NaN.pow(2));
		assertEquals(Decimal128.POSITIVE_INFINITY, Decimal128.POSITIVE_INFINITY.pow(2));
		assertEquals(Decimal128.NEGATIVE_INFINITY, Decimal128.NEGATIVE_INFINITY.pow(3));
		assertEquals(Decimal128.POSITIVE_INFINITY, Decimal128.NEGATIVE_INFINITY.pow(2));
		assertEquals(Decimal128.NEGATIVE_ZERO, Decimal128.NEGATIVE_INFINITY.pow(-3));
		assertEquals(Decimal128.POSITIVE_INFINITY, Decimal128.ZERO.pow(-1));
		assertEquals(Decimal128.NEGATIVE_INFINITY, Decimal128.NEGATIVE_ZERO.pow(-1));
		assertEquals(Decimal128.NEGATIVE_ZERO, Decimal128.NEGATIVE_ZERO.pow(3));
		// Overflow, underflow
		assertEquals(Decimal128.POSITIVE_INFINITY, Decimal128.of("1e4000").pow(2));
		assertEquals(Decimal128.ZERO, Decimal128.of("1e-4000").pow(2));
		assertEquals(Decimal128.POSITIVE_INFINITY, Decimal128.TEN.pow(Long.MAX_VALUE));
		assertEquals(Decimal128.ZERO, Decimal128.TEN.pow(Long.MIN_VALUE));
		assertEquals(Decimal128.ONE, Decimal128.ONE.pow(Long.MAX_VALUE));
		assertEquals(Decimal128.ONE.negate(), Decimal128.ONE.negate().pow(Long.MAX_VALUE));
	}
}
