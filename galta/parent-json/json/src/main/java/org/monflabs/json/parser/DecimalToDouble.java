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
package org.monflabs.json.parser;

import java.math.BigInteger;

/**
 * Converts a decimal number w * 10^q, with w an exact integer of at most 19 digits, to the
 * correctly rounded double (the value Double.parseDouble() returns), with the Eisel-Lemire
 * algorithm: one or two 64x128 bit multiplications by a truncated power of five.
 * <p>
 * D. Lemire, "Number Parsing at a Gigabyte per Second", Software: Practice and Experience
 * 51 (8), 2021, and N. Mushtak, D. Lemire, "Fast Number Parsing Without Fallback", 2023,
 * which shows that the result is always correct for an exact w. This is a port of
 * compute_float() of the fast_float library (Apache License 2.0 / MIT).
 * <p>
 * The results this class doesn't produce are left to Double.parseDouble(): {@link #toDouble}
 * returns NaN for them (a subnormal, an underflow to zero or an overflow to infinity).
 */
final class DecimalToDouble {

	private DecimalToDouble() {
	}

	private static final int SMALLEST_POWER_OF_TEN = -342;
	private static final int LARGEST_POWER_OF_TEN = 308;
	private static final int MANTISSA_EXPLICIT_BITS = 52;
	private static final int MINIMUM_EXPONENT = -1023;
	private static final int INFINITE_POWER = 0x7FF;
	private static final int MIN_EXPONENT_ROUND_TO_EVEN = -4;
	private static final int MAX_EXPONENT_ROUND_TO_EVEN = 23;
	// 55 bits of precision (the mantissa, plus 3)
	private static final long PRECISION_MASK = 0xFFFFFFFFFFFFFFFFL >>> (MANTISSA_EXPLICIT_BITS + 3);

	// 128 bit approximations of the powers of five from 5^-342 to 5^308, as (high, low)
	// pairs, normalized so the most significant bit is set: truncated for the positive
	// powers, rounded up for the negative ones (the table of fast_float)
	private static final long[] POWER_OF_FIVE_128 = powersOfFive();

	private static long[] powersOfFive() {
		long[] t = new long[2*(LARGEST_POWER_OF_TEN-SMALLEST_POWER_OF_TEN+1)];
		BigInteger two128 = BigInteger.ONE.shiftLeft(128);
		BigInteger two127 = BigInteger.ONE.shiftLeft(127);
		int i = 0;
		for(int q=SMALLEST_POWER_OF_TEN; q<0; q++) {
			BigInteger power5 = BigInteger.valueOf(5).pow(-q);
			// z: the smallest such as 2^z >= 5^-q
			int z = power5.bitLength();
			if(BigInteger.ONE.shiftLeft(z-1).compareTo(power5)>=0) {
				z--;
			}
			BigInteger c;
			if(q>=-27) {
				c = BigInteger.ONE.shiftLeft(z+127).divide(power5).add(BigInteger.ONE);
			} else {
				c = BigInteger.ONE.shiftLeft(2*z+2*64).divide(power5).add(BigInteger.ONE);
				while(c.compareTo(two128)>=0) {
					c = c.shiftRight(1);
				}
			}
			t[i++] = c.shiftRight(64).longValue();
			t[i++] = c.longValue();
		}
		for(int q=0; q<=LARGEST_POWER_OF_TEN; q++) {
			BigInteger power5 = BigInteger.valueOf(5).pow(q);
			while(power5.compareTo(two127)<0) {
				power5 = power5.shiftLeft(1);
			}
			while(power5.compareTo(two128)>=0) {
				power5 = power5.shiftRight(1);
			}
			t[i++] = power5.shiftRight(64).longValue();
			t[i++] = power5.longValue();
		}
		return t;
	}

	/**
	 * The double nearest to w * 10^q (ties to even), w being an unsigned integer of at most
	 * 19 decimal digits, not zero. NaN when the result is a subnormal, zero or infinite: the
	 * caller then uses Double.parseDouble().
	 */
	static double toDouble(long w, int q, boolean negative) {
		if(w==0 || q<SMALLEST_POWER_OF_TEN || q>LARGEST_POWER_OF_TEN) {
			return Double.NaN;
		}
		int lz = Long.numberOfLeadingZeros(w);
		w <<= lz;

		// The product of w and the 128 bit approximation of 5^q: its 128 high bits
		int index = 2*(q-SMALLEST_POWER_OF_TEN);
		long high = Math.unsignedMultiplyHigh(w, POWER_OF_FIVE_128[index]);
		long low = w*POWER_OF_FIVE_128[index];
		if((high & PRECISION_MASK)==PRECISION_MASK) {
			// The lower bits could change the result: add the next 64 bits of the product
			long secondHigh = Math.unsignedMultiplyHigh(w, POWER_OF_FIVE_128[index+1]);
			low += secondHigh;
			if(Long.compareUnsigned(secondHigh, low)>0) {
				high++;
			}
		}

		int upperbit = (int)(high>>>63);
		int shift = upperbit + 64 - MANTISSA_EXPLICIT_BITS - 3;
		long mantissa = high>>>shift;
		int power2 = power(q) + upperbit - lz - MINIMUM_EXPONENT;
		if(power2<=0) {
			// A subnormal (or zero): left to Double.parseDouble()
			return Double.NaN;
		}
		// The value is exactly between two doubles: round to even
		if(Long.compareUnsigned(low, 1)<=0 && q>=MIN_EXPONENT_ROUND_TO_EVEN && q<=MAX_EXPONENT_ROUND_TO_EVEN
				&& (mantissa & 3)==1) {
			if((mantissa<<shift)==high) {
				mantissa &= ~1L;
			}
		}
		mantissa += (mantissa & 1);
		mantissa >>>= 1;
		if(mantissa>=(2L<<MANTISSA_EXPLICIT_BITS)) {
			mantissa = 1L<<MANTISSA_EXPLICIT_BITS;
			power2++;
		}
		mantissa &= ~(1L<<MANTISSA_EXPLICIT_BITS);
		if(power2>=INFINITE_POWER) {
			return Double.NaN;
		}
		long bits = mantissa | ((long)power2<<MANTISSA_EXPLICIT_BITS);
		if(negative) {
			bits |= 1L<<63;
		}
		return Double.longBitsToDouble(bits);
	}

	// floor(log2(10^q)) + 63, for q in [-342, 308]
	private static int power(int q) {
		return (((152170 + 65536) * q) >> 16) + 63;
	}
}
