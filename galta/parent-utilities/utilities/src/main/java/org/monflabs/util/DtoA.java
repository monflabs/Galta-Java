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

import org.monflabs.util.impl.ryu.RyuDouble;
import org.monflabs.util.impl.ryu.RyuFloat;

/**
 * Number to string conversion.
 * <p>
 * {@link #toStandard(double)} produces exactly what JavaScript's
 * {@code Number.prototype.toString()} (radix 10) produces: the shortest digits that
 * round-trip to the same double, laid out by the ECMA-262 Number::toString rules.
 * Integers below 1e21 are written without exponent ({@code 4611686018427388000} for 2^62),
 * small numbers down to 1e-6 in plain decimal ({@code 0.0001}), and other values in
 * exponential form with an explicit exponent sign ({@code 1e+21}, {@code 1e-7}).
 * {@code -0} is written {@code "0"}, as in JavaScript.
 * <p>
 * JavaScript has no float type: {@link #toStandard(float)} uses the shortest digits that
 * round-trip to the same <em>float</em>, with the same layout rules. So {@code 0.1f} is
 * written {@code "0.1"}, not the digits of the widened double.
 * <p>
 * {@link #toJavaLiteral(double)} and {@link #toJavaLiteral(float)} produce the Java-style
 * form used when generating Java source code, where the number must be a valid Java literal.
 * 
 * @author priand
 */
public class DtoA {

	// Integers of this magnitude are exact, and their exact digits are the shortest ones
	private static final double EXACT_DOUBLE_INT = 0x1p53;
	private static final float EXACT_FLOAT_INT = 0x1p24f;

	public static String toStandard(double value) {
		if (value == Double.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (value == Double.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
		if (Double.isNaN(value)) {
			return "NaN";
		}
		if (value == 0.0) {
			return "0";
		}
		if (Math.abs(value) < EXACT_DOUBLE_INT) {
			long l = (long) value;
			if (((double) l) == value) {
				return Long.toString(l);
			}
		}
		String s = RyuDouble.doubleToString(Math.abs(value));
		// Ryu writes at least 2 significant digits for subnormals (4.9e-324): look for a shorter form
		if(Math.abs(value) < Double.MIN_NORMAL) {
			s = shorterDouble(Math.abs(value), s);
		}
		return format(value < 0, s);
	}
	
	public static String toStandard(float value) {
		if (value == Float.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (value == Float.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
		if (Float.isNaN(value)) {
			return "NaN";
		}
		if (value == 0.0f) {
			return "0";
		}
		if (Math.abs(value) < EXACT_FLOAT_INT) {
			long l = (long) value;
			if (((float) l) == value) {
				return Long.toString(l);
			}
		}
		String s = RyuFloat.floatToString(Math.abs(value));
		if(Math.abs(value) < Float.MIN_NORMAL) {
			s = shorterFloat(Math.abs(value), s);
		}
		return format(value < 0, s);
	}

	/**
	 * Java-style representation, suitable for a Java double literal once a suffix is added:
	 * integral values as plain digits, others as {@code 1.5}, {@code 1.0e-7}, {@code 1.0e21}.
	 */
	public static String toJavaLiteral(double value) {
		if (value == Double.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (value == Double.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
		if (Double.isNaN(value)) {
			return "NaN";
		}
		if (value == 0.0) {
			return "0";
		}

		// (long)value saturates at +/-2^63, and 2^63 as a double round-trips through
		// that saturated long, so guard the range before trusting the long path
		if (Math.abs(value) < 0x1p63) {
			long l = (long) value;
			if (((double) l) == value) {
				return Long.toString(l);
			}
		}

		return RyuDouble.doubleToString(value);
	}

	/**
	 * Java-style representation, suitable for a Java float literal once a suffix is added.
	 */
	public static String toJavaLiteral(float value) {
		if (value == Float.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (value == Float.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
		if (Float.isNaN(value)) {
			return "NaN";
		}
		if (value == 0.0f) {
			return "0";
		}

		if (Math.abs(value) < 0x1p63f) {
			long l = (long) value;
			if (((float) l) == value) {
				return Long.toString(l);
			}
		}

		// Float.toString() writes the exponent as 'E'; keep the lower-case 'e' the double path uses
		return Float.toString(value).replace('E', 'e');
	}
	
	
	//
	// ECMA-262 Number::toString layout
	//
	
	/**
	 * Lay out the digits of a (positive) Ryu/Java formatted number following ECMA-262 Number::toString.
	 */
	private static String format(boolean negative, String ryu) {
		// Extract the significant digits and the decimal exponent n, value = 0.digits * 10^n
		// (working on characters: this is on the path of every non integral number output)
		int len = ryu.length();
		char[] digits = new char[len];
		int count = 0;
		int intDigits = -1;
		int i = 0;
		for (; i < len; i++) {
			char c = ryu.charAt(i);
			if (c == '.') {
				intDigits = count;
			} else if (c == 'e' || c == 'E') {
				break;
			} else {
				digits[count++] = c;
			}
		}
		if (intDigits < 0) {
			intDigits = count;
		}
		int exp = 0;
		if (i < len) {
			i++;
			boolean negativeExp = false;
			if (ryu.charAt(i) == '-' || ryu.charAt(i) == '+') {
				negativeExp = ryu.charAt(i) == '-';
				i++;
			}
			for (; i < len; i++) {
				exp = exp * 10 + (ryu.charAt(i) - '0');
			}
			if (negativeExp) {
				exp = -exp;
			}
		}
		int lead = 0;
		while (lead < count - 1 && digits[lead] == '0') {
			lead++;
		}
		int end = count;
		while (end > lead + 1 && digits[end - 1] == '0') {
			end--;
		}
		int n = intDigits - lead + exp;
		int k = end - lead;

		// At most 21 integer digits, 6 leading zeros, 17 significant digits and an exponent
		char[] b = new char[k + 32];
		int p = 0;
		if (negative) {
			b[p++] = '-';
		}
		if (k <= n && n <= 21) {
			System.arraycopy(digits, lead, b, p, k);
			p += k;
			for (int z = k; z < n; z++) {
				b[p++] = '0';
			}
		} else if (0 < n && n <= 21) {
			System.arraycopy(digits, lead, b, p, n);
			p += n;
			b[p++] = '.';
			System.arraycopy(digits, lead + n, b, p, k - n);
			p += k - n;
		} else if (-6 < n && n <= 0) {
			b[p++] = '0';
			b[p++] = '.';
			for (int z = n; z < 0; z++) {
				b[p++] = '0';
			}
			System.arraycopy(digits, lead, b, p, k);
			p += k;
		} else {
			b[p++] = digits[lead];
			if (k > 1) {
				b[p++] = '.';
				System.arraycopy(digits, lead + 1, b, p, k - 1);
				p += k - 1;
			}
			b[p++] = 'e';
			b[p++] = n - 1 >= 0 ? '+' : '-';
			String e = Integer.toString(Math.abs(n - 1));
			e.getChars(0, e.length(), b, p);
			p += e.length();
		}
		return new String(b, 0, p);
	}
	
	private static String shorterDouble(double value, String ryu) {
		java.math.BigDecimal bd = new java.math.BigDecimal(ryu);
		for (int p = 1; p < bd.precision(); p++) {
			// The exact value rounded to p digits is the closest p-digit candidate
			java.math.BigDecimal x = new java.math.BigDecimal(value).round(new java.math.MathContext(p, java.math.RoundingMode.HALF_EVEN));
			if (Double.parseDouble(x.toString()) == value) {
				return x.toString();
			}
		}
		return ryu;
	}

	private static String shorterFloat(float value, String ryu) {
		java.math.BigDecimal bd = new java.math.BigDecimal(ryu);
		for (int p = 1; p < bd.precision(); p++) {
			java.math.BigDecimal x = new java.math.BigDecimal(value).round(new java.math.MathContext(p, java.math.RoundingMode.HALF_EVEN));
			if (Float.parseFloat(x.toString()) == value) {
				return x.toString();
			}
		}
		return ryu;
	}
}
