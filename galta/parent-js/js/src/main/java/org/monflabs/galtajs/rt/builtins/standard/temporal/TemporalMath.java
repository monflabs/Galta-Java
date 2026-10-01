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
 *
 * Portions are derived from the TC39 Temporal proposal reference polyfill,
 * Copyright (c) 2017, 2018, 2019, 2020 Ecma International. All rights
 * reserved. Distributed under the BSD License, see LICENSE.txt in this
 * folder.
 */
package org.monflabs.galtajs.rt.builtins.standard.temporal;

import java.math.BigInteger;

/**
 * Temporal rounding: rounding modes and RoundNumberToIncrement variants
 * (spec 13.x, "unsigned rounding modes").
 */
public final class TemporalMath {

	private TemporalMath() {
	}

	public enum UnsignedRoundingMode { ZERO, INFINITY, HALF_ZERO, HALF_INFINITY, HALF_EVEN }

	public enum RoundingMode {
		CEIL("ceil"),
		FLOOR("floor"),
		EXPAND("expand"),
		TRUNC("trunc"),
		HALF_CEIL("halfCeil"),
		HALF_FLOOR("halfFloor"),
		HALF_EXPAND("halfExpand"),
		HALF_TRUNC("halfTrunc"),
		HALF_EVEN("halfEven");

		public final String jsName;
		RoundingMode(String jsName) {
			this.jsName = jsName;
		}
		public static RoundingMode of(String s) {
			for(RoundingMode m: values()) {
				if(m.jsName.equals(s)) {
					return m;
				}
			}
			return null;
		}
		public RoundingMode negate() {
			switch(this) {
				case CEIL: return FLOOR;
				case FLOOR: return CEIL;
				case HALF_CEIL: return HALF_FLOOR;
				case HALF_FLOOR: return HALF_CEIL;
				default: return this;
			}
		}
		public UnsignedRoundingMode unsigned(boolean negative) {
			switch(this) {
				case CEIL: return negative ? UnsignedRoundingMode.ZERO : UnsignedRoundingMode.INFINITY;
				case FLOOR: return negative ? UnsignedRoundingMode.INFINITY : UnsignedRoundingMode.ZERO;
				case EXPAND: return UnsignedRoundingMode.INFINITY;
				case TRUNC: return UnsignedRoundingMode.ZERO;
				case HALF_CEIL: return negative ? UnsignedRoundingMode.HALF_ZERO : UnsignedRoundingMode.HALF_INFINITY;
				case HALF_FLOOR: return negative ? UnsignedRoundingMode.HALF_INFINITY : UnsignedRoundingMode.HALF_ZERO;
				case HALF_EXPAND: return UnsignedRoundingMode.HALF_INFINITY;
				case HALF_TRUNC: return UnsignedRoundingMode.HALF_ZERO;
				default: return UnsignedRoundingMode.HALF_EVEN;
			}
		}
	}

	// ApplyUnsignedRoundingMode: returns true to pick r2, false for r1
	public static boolean pickUpper(int cmp, boolean evenCardinality, UnsignedRoundingMode mode) {
		switch(mode) {
			case ZERO: return false;
			case INFINITY: return true;
			default:
		}
		if(cmp<0) {
			return false;
		}
		if(cmp>0) {
			return true;
		}
		switch(mode) {
			case HALF_ZERO: return false;
			case HALF_INFINITY: return true;
			default: return !evenCardinality;
		}
	}

	// RoundNumberToIncrement on exact integers (symmetric around zero)
	public static BigInteger roundBigToIncrement(BigInteger quantity, BigInteger increment, RoundingMode mode) {
		BigInteger[] qr = quantity.divideAndRemainder(increment);
		if(qr[1].signum()==0) {
			return quantity;
		}
		boolean negative = quantity.signum()<0;
		BigInteger r1 = qr[0].abs();
		int cmp = qr[1].abs().shiftLeft(1).compareTo(increment);
		boolean even = !r1.testBit(0);
		BigInteger rounded = pickUpper(cmp,even,mode.unsigned(negative)) ? r1.add(BigInteger.ONE) : r1;
		rounded = rounded.multiply(increment);
		return negative ? rounded.negate() : rounded;
	}

	public static long roundLongToIncrement(long quantity, long increment, RoundingMode mode) {
		return roundBigToIncrement(BigInteger.valueOf(quantity), BigInteger.valueOf(increment), mode).longValueExact();
	}

	// RoundNumberToIncrementAsIfPositive: used for epoch nanoseconds, where
	// the rounding direction must not depend on the sign
	public static BigInteger roundAsIfPositive(BigInteger quantity, BigInteger increment, RoundingMode mode) {
		BigInteger[] qr = quantity.divideAndRemainder(increment);
		BigInteger quotient = qr[0];
		BigInteger remainder = qr[1];
		if(remainder.signum()==0) {
			return quantity;
		}
		BigInteger r1, r2;
		if(quantity.signum()<0) {
			r1 = quotient.subtract(BigInteger.ONE);
			r2 = quotient;
		} else {
			r1 = quotient;
			r2 = quotient.add(BigInteger.ONE);
		}
		int cmp = remainder.shiftLeft(1).abs().compareTo(increment) * (quantity.signum()<0 ? -1 : 1);
		boolean even = !r1.testBit(0);
		BigInteger rounded = pickUpper(cmp,even,mode.unsigned(false)) ? r2 : r1;
		return rounded.multiply(increment);
	}
}
