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
package org.monflabs.util;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.util.number.Decimal128;

/**
 * Helpers to handle some Java types.
 */
public abstract class TypeUtil {
	
	//
	// Number conversion: the Java narrowing conversions (used by GaltaJS)
	//
	public static byte toByte(Number n) {
		return n.byteValue();
	}
	public static short toShort(Number n) {
		return n.shortValue();
	}
	public static int toInt(Number n) {
		return n.intValue();
	}
	public static long toLong(Number n) {
		return n.longValue();
	}
	public static float toFloat(Number n) {
		return n.floatValue();
	}
	public static double toDouble(Number n) {
		return n.doubleValue();
	}
	// NaN and the infinities have no BigDecimal/BigInteger value: they used to become 0
	// (NaN) or saturate (to +-Double.MAX_VALUE for a BigDecimal, but to the long range
	// for a BigInteger)
	private static void checkFinite(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d)) {
			throw new ArithmeticException(d + " cannot be converted to an exact number");
		}
	}

	/**
	 * Converts a number to a BigDecimal: exactly for the integral types, through the
	 * shortest decimal representation for a float or a double.
	 * @throws ArithmeticException for NaN and the infinities
	 */
	public static BigDecimal toBigDecimal(Number n) {
		// Some libraries, like GSON, return their own number class (LazilyParsedNumber)
		if(n instanceof BigDecimal) {
			return (BigDecimal)n;
		}
		if(n instanceof Decimal128 dec) {
			return dec.toBigDecimal();	// ArithmeticException for NaN and the infinities
		}
		if(n instanceof BigInteger) {
			return new BigDecimal((BigInteger)n);
		}
		if(n instanceof Float f) {
			checkFinite(f.doubleValue());
			// Via the float's own shortest decimal form: widening to double first
			// exposes the binary error (0.1f became 0.10000000149011612)
			return new BigDecimal(Float.toString(f));
		}
		if(n instanceof Double d) {
			checkFinite(d);
			return BigDecimal.valueOf(n.doubleValue());
		}
		if(n instanceof Integer || n instanceof Long || n instanceof Byte || n instanceof Short) {
			return BigDecimal.valueOf(n.longValue());
		}
		return new BigDecimal(n.toString());
	}	
	/**
	 * Converts a number to a BigInteger, truncating any fractional part.
	 * @throws ArithmeticException for NaN and the infinities
	 */
	public static BigInteger toBigInteger(Number n) {
		if(n instanceof BigInteger) {
			return (BigInteger)n;
		}
		if(n instanceof BigDecimal) {
			return ((BigDecimal)n).toBigInteger();
		}
		if(n instanceof Decimal128 dec) {
			return dec.toBigDecimal().toBigInteger();	// ArithmeticException for NaN and the infinities
		}
		if(n instanceof Float f) {
			checkFinite(f.doubleValue());
			return new BigDecimal(n.floatValue()).toBigInteger();
		}
		if(n instanceof Double d) {
			checkFinite(d);
			return new BigDecimal(n.doubleValue()).toBigInteger();
		}
		if(n instanceof Integer || n instanceof Long || n instanceof Byte || n instanceof Short) {
			return BigInteger.valueOf(n.longValue());
		}
		String s = n.toString();
		try {
			if(s.indexOf('.')>=0 || s.indexOf('e')>=0 || s.indexOf('E')>=0) {
				return (new BigDecimal(s)).toBigInteger();
			}
			// Parse the text rather than longValue(), which overflows for large integers
			return new BigInteger(s);
		} catch(NumberFormatException ex) {
			return BigInteger.valueOf(n.longValue());
		}
	}

	/**
	 * Converts a number to a Decimal128: exactly for the integral types and BigInteger/BigDecimal when they fit in
	 * 34 digits (otherwise rounded half even, and beyond the range an infinity or zero), through the shortest decimal
	 * representation for a float or a double. NaN and the infinities are kept.
	 */
	public static Decimal128 toDecimal128(Number n) {
		if(n instanceof Decimal128 dec) {
			return dec;
		}
		if(n instanceof BigDecimal bd) {
			return Decimal128.of(bd);
		}
		if(n instanceof BigInteger bi) {
			return Decimal128.of(bi);
		}
		if(n instanceof Float f) {
			// Via the float's own shortest decimal form (see toBigDecimal)
			if(f.isNaN() || f.isInfinite()) {
				return Decimal128.of(f.doubleValue());
			}
			return Decimal128.of(Float.toString(f));
		}
		if(n instanceof Double d) {
			return Decimal128.of(d.doubleValue());
		}
		if(n instanceof Integer || n instanceof Long || n instanceof Byte || n instanceof Short) {
			return Decimal128.of(n.longValue());
		}
		try {
			return Decimal128.of(n.toString());
		} catch(RuntimeException ex) {
			return Decimal128.of(n.doubleValue());
		}
	}

	/**
	 * Whether a number is NaN: a Double, a Float or a Decimal128.
	 */
	public static boolean isNaN(Number n) {
		if(n instanceof Double d) {
			return d.isNaN();
		}
		if(n instanceof Float f) {
			return f.isNaN();
		}
		if(n instanceof Decimal128 dec) {
			return dec.isNaN();
		}
		return false;
	}

	/**
	 * Whether a number is an infinity: a Double, a Float or a Decimal128.
	 */
	public static boolean isInfinite(Number n) {
		if(n instanceof Double d) {
			return d.isInfinite();
		}
		if(n instanceof Float f) {
			return f.isInfinite();
		}
		if(n instanceof Decimal128 dec) {
			return dec.isInfinite();
		}
		return false;
	}
}
