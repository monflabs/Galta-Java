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

/**
 * Helpers to handle some Java types.
 */
public abstract class TypeUtil {
	
	//
	// Number conversion
	// Based on the JavaScript specification
	// Are these needed?? Should be removed!!
	//
	public static byte toByte(Number n) {
		// Not needed
//		if(n instanceof Double d) {
//			if(d.isNaN()) {
//				return 0;
//			}
//			if(d.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Byte.MIN_VALUE : Byte.MAX_VALUE;
//			}
//			return d.byteValue();
//		}
//		if(n instanceof Float f) {
//			if(f.isNaN()) {
//				return 0;
//			}
//			if(f.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Byte.MIN_VALUE : Byte.MAX_VALUE;
//			}
//			return f.byteValue();
//		}
		return n.byteValue();
	}
	public static short toShort(Number n) {
//		if(n instanceof Double d) {
//			if(d.isNaN()) {
//				return 0;
//			}
//			if(d.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Short.MIN_VALUE : Short.MAX_VALUE;
//			}
//			return d.shortValue();
//		}
//		if(n instanceof Float f) {
//			if(f.isNaN()) {
//				return 0;
//			}
//			if(f.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Short.MIN_VALUE : Short.MAX_VALUE;
//			}
//			return f.shortValue();
//		}
		return n.shortValue();
	}
	public static int toInt(Number n) {
		// Do we need this?		
//		if(n instanceof Double d) {
//			if(d.isNaN()) {
//				return 0;
//			}
//			if(d.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : Integer.MAX_VALUE;
//			}
//			return d.intValue();
//		}
//		if(n instanceof Float f) {
//			if(f.isNaN()) {
//				return 0;
//			}
//			if(f.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : Integer.MAX_VALUE;
//			}
//			return f.intValue();
//		}
		return n.intValue();
	}
	public static long toLong(Number n) {
		// Do we need this?		
//		if(n instanceof Double d) {
//			if(d.isNaN()) {
//				return 0;
//			}
//			if(d.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Long.MIN_VALUE : Long.MAX_VALUE;
//			}
//			return d.longValue();
//		}
//		if(n instanceof Float f) {
//			if(f.isNaN()) {
//				return 0;
//			}
//			if(f.isInfinite()) {
//				return n.doubleValue()==Double.NEGATIVE_INFINITY ? Long.MIN_VALUE : Long.MAX_VALUE;
//			}
//			return f.longValue();
//		}
		return n.longValue();
	}
	public static float toFloat(Number n) {
		return n.floatValue();
	}
	public static double toDouble(Number n) {
		return n.doubleValue();
	}
	public static BigDecimal toBigDecimal(Number n) {
		// Some libraries, like GSON, return their own number class (LazilyParsedNumber)
		if(n instanceof BigDecimal) {
			return (BigDecimal)n;
		}
		if(n instanceof BigInteger) {
			return new BigDecimal((BigInteger)n);
		}
		if(n instanceof Float f) {
			if(f.isNaN()) {
				return BigDecimal.ZERO;
			}
			if(f.isInfinite()) {
				return BigDecimal.valueOf(f.floatValue()==Float.NEGATIVE_INFINITY ? -Double.MAX_VALUE : Double.MAX_VALUE);
			}
			// Via the float's own shortest decimal form: widening to double first
			// exposes the binary error (0.1f became 0.10000000149011612)
			return new BigDecimal(Float.toString(f));
		}
		if(n instanceof Double d) {
			if(d.isNaN()) {
				return BigDecimal.ZERO;
			}
			if(d.isInfinite()) {
				return BigDecimal.valueOf(d.doubleValue()==Double.NEGATIVE_INFINITY ? -Double.MAX_VALUE : Double.MAX_VALUE);
			}
			return BigDecimal.valueOf(n.doubleValue());
		}
		if(n instanceof Integer || n instanceof Long || n instanceof Byte || n instanceof Short) {
			return BigDecimal.valueOf(n.longValue());
		}
		return new BigDecimal(n.toString());
	}	
	public static BigInteger toBigInteger(Number n) {
		if(n instanceof BigInteger) {
			return (BigInteger)n;
		}
		if(n instanceof BigDecimal) {
			return ((BigDecimal)n).toBigInteger();
		}
		if(n instanceof Float f) {
			if(f.isNaN()) {
				return BigInteger.ZERO;
			}
			if(f.isInfinite()) {
				return BigInteger.valueOf(f.floatValue()==Float.NEGATIVE_INFINITY ? Long.MIN_VALUE : Long.MAX_VALUE);
			}
			return new BigDecimal(n.floatValue()).toBigInteger();
		}
		if(n instanceof Double d) {
			if(d.isNaN()) {
				return BigInteger.ZERO;
			}
			if(d.isInfinite()) {
				return BigInteger.valueOf(d.doubleValue()==Double.NEGATIVE_INFINITY ? Long.MIN_VALUE : Long.MAX_VALUE);
			}
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


}
