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
package org.monflabs.json.serialization;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonException;

/**
 * Conversion of JSON numbers to the Java numeric types.
 * <p>
 * The conversions to the integral types are exact: a number with a fraction, or out of the
 * range of the target type, throws a {@link JsonException} instead of being silently truncated.
 * The conversions to <code>float</code>/<code>double</code> round to the nearest value, but a
 * finite number out of their range is rejected.
 */
public final class NumberConverter {
	
	private NumberConverter() {
	}
	
	private static Number checkNumber(Object value, String type) {
		if(value instanceof Number n) {
			return n;
		}
		throw new JsonException(null, "JSON value {0} is not a number, cannot convert it to {1}", value, type);
	}
	
	/**
	 * The maximum number of integer digits of a JSON number converted to a BigInteger. A
	 * number like 1e10000000 is a few bytes of JSON but would materialize ten million digits.
	 */
	public static final int MAX_BIGINTEGER_DIGITS = 10_000;

	/**
	 * The exact integer value of a number, checked against the maximum number of digits of the
	 * target type before it is materialized.
	 */
	private static BigInteger exactInteger(Number n, String type, int maxDigits) {
		if(n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
			return BigInteger.valueOf(n.longValue());
		}
		if(n instanceof BigInteger bi) {
			if(bi.bitLength()>maxDigits*4L) {
				// More than maxDigits decimal digits (log2(10)>3.3)
				throw outOfRange(n, type);
			}
			return bi;
		}
		BigDecimal bd = toBigDecimal(n);
		if(bd.signum()==0) {
			return BigInteger.ZERO;
		}
		// The number of digits before the decimal point (<=0 for a number lower than 1)
		long intDigits = (long)bd.precision() - bd.scale();
		if(intDigits<=0) {
			throw new JsonException(null, "Number {0} has a fraction, cannot convert it to {1}", n, type);
		}
		if(intDigits>maxDigits) {
			throw outOfRange(n, type);
		}
		if(bd.scale()>0 && bd.stripTrailingZeros().scale()>0) {
			throw new JsonException(null, "Number {0} has a fraction, cannot convert it to {1}", n, type);
		}
		try {
			return bd.toBigIntegerExact();
		} catch(ArithmeticException ex) {
			throw new JsonException(null, "Number {0} has a fraction, cannot convert it to {1}", n, type);
		}
	}
	private static JsonException outOfRange(Number n, String type) {
		return new JsonException(null, "Number {0} is out of range for {1}", n, type);
	}

	public static byte toByte(Object value) {
		Number n = checkNumber(value, "byte");
		if(n instanceof Byte b) return b;
		try {
			return exactInteger(n, "byte", 3).byteValueExact();
		} catch(ArithmeticException ex) {
			throw outOfRange(n, "byte");
		}
	}
	public static short toShort(Object value) {
		Number n = checkNumber(value, "short");
		if(n instanceof Short s) return s;
		try {
			return exactInteger(n, "short", 5).shortValueExact();
		} catch(ArithmeticException ex) {
			throw outOfRange(n, "short");
		}
	}
	public static int toInt(Object value) {
		Number n = checkNumber(value, "int");
		if(n instanceof Integer i) return i;
		try {
			return exactInteger(n, "int", 10).intValueExact();
		} catch(ArithmeticException ex) {
			throw outOfRange(n, "int");
		}
	}
	public static long toLong(Object value) {
		Number n = checkNumber(value, "long");
		if(n instanceof Long l) return l;
		if(n instanceof Integer i) return i;
		try {
			return exactInteger(n, "long", 19).longValueExact();
		} catch(ArithmeticException ex) {
			throw outOfRange(n, "long");
		}
	}
	public static BigInteger toBigInteger(Object value) {
		return exactInteger(checkNumber(value, "BigInteger"), "BigInteger", MAX_BIGINTEGER_DIGITS);
	}
	/**
	 * The non finite value named by a JSON string ("NaN", "Infinity", "-Infinity"), or null.
	 * NaN and the infinities are not JSON numbers: they can be written as these strings.
	 */
	public static Double nonFinite(Object value) {
		if(value instanceof String s) {
			switch(s) {
				case "NaN": return Double.NaN;
				case "Infinity": return Double.POSITIVE_INFINITY;
				case "-Infinity": return Double.NEGATIVE_INFINITY;
				default: return null;
			}
		}
		return null;
	}

	/**
	 * Convert a JSON number to a float. The strings "NaN", "Infinity" and "-Infinity" are
	 * also accepted.
	 */
	public static float toFloat(Object value) {
		Double nf = nonFinite(value);
		if(nf!=null) {
			return nf.floatValue();
		}
		Number n = checkNumber(value, "float");
		if(n instanceof Float f) return f;
		double d = n.doubleValue();
		float f = (float)d;
		if(Float.isInfinite(f) && (!Double.isInfinite(d) || n instanceof BigDecimal || n instanceof BigInteger)) {
			throw outOfRange(n, "float");
		}
		return f;
	}
	/**
	 * Convert a JSON number to a double. The strings "NaN", "Infinity" and "-Infinity" are
	 * also accepted.
	 */
	public static double toDouble(Object value) {
		Double nf = nonFinite(value);
		if(nf!=null) {
			return nf;
		}
		Number n = checkNumber(value, "double");
		double d = n.doubleValue();
		if(Double.isInfinite(d) && (n instanceof BigDecimal || n instanceof BigInteger)) {
			throw outOfRange(n, "double");
		}
		return d;
	}
	public static BigDecimal toBigDecimal(Object value) {
		return toBigDecimal(checkNumber(value, "BigDecimal"));
	}
	private static BigDecimal toBigDecimal(Number n) {
		if(n instanceof BigDecimal bd) return bd;
		if(n instanceof BigInteger bi) return new BigDecimal(bi);
		if(n instanceof Double || n instanceof Float) {
			double d = n.doubleValue();
			if(Double.isNaN(d) || Double.isInfinite(d)) {
				throw new JsonException(null, "Number {0} cannot be converted to a decimal number", n);
			}
			// The shortest decimal representation: 0.1 is 0.1, not 0.1000000000000000055...
			return n instanceof Float ? new BigDecimal(Float.toString(n.floatValue())) : BigDecimal.valueOf(d);
		}
		return BigDecimal.valueOf(n.longValue());
	}
}
