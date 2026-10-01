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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * A Temporal time duration: an exact count of nanoseconds, bounded by
 * ±(2^53 s - 1 ns), the spec's [[Time]] of an internal duration record.
 */
public final class TimeDuration implements Comparable<TimeDuration> {

	public static final BigInteger MAX = new BigInteger("9007199254740991999999999");
	public static final BigInteger NS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
	public static final BigInteger NS_PER_DAY = BigInteger.valueOf(86_400_000_000_000L);
	public static final TimeDuration ZERO = new TimeDuration(BigInteger.ZERO);

	private final BigInteger totalNs;

	private TimeDuration(BigInteger totalNs) {
		this.totalNs = totalNs;
	}

	private static TimeDuration validate(BigInteger totalNs) {
		if(totalNs.abs().compareTo(MAX)>0) {
			throw RuntimeUtil.rangeError("{0}", "Duration time units cannot exceed "+MAX+" ns");
		}
		return new TimeDuration(totalNs);
	}

	// An epoch nanoseconds difference always fits, no validation
	public static TimeDuration of(BigInteger totalNs) {
		return new TimeDuration(totalNs);
	}

	public static TimeDuration fromEpochNsDiff(BigInteger ns1, BigInteger ns2) {
		return new TimeDuration(ns1.subtract(ns2));
	}

	public static TimeDuration fromComponents(double h, double min, double s, double ms, double us, double ns) {
		BigInteger total = big(ns)
				.add(big(us).multiply(BigInteger.valueOf(1000L)))
				.add(big(ms).multiply(BigInteger.valueOf(1_000_000L)))
				.add(big(s).multiply(NS_PER_SECOND))
				.add(big(min).multiply(BigInteger.valueOf(60_000_000_000L)))
				.add(big(h).multiply(BigInteger.valueOf(3_600_000_000_000L)));
		return validate(total);
	}

	public static BigInteger big(double d) {
		if(d==0) {
			return BigInteger.ZERO;
		}
		if(Math.abs(d)<9.0e18) {
			return BigInteger.valueOf((long)d);
		}
		return new BigDecimal(d).toBigInteger();
	}

	public BigInteger getTotalNs() {
		return totalNs;
	}

	// Truncated seconds and signed sub-second nanoseconds
	public BigInteger sec() {
		return totalNs.divide(NS_PER_SECOND);
	}
	public long subsec() {
		return totalNs.remainder(NS_PER_SECOND).longValue();
	}

	public TimeDuration abs() {
		return new TimeDuration(totalNs.abs());
	}
	public TimeDuration negate() {
		return new TimeDuration(totalNs.negate());
	}
	public TimeDuration add(TimeDuration other) {
		return validate(totalNs.add(other.totalNs));
	}
	public TimeDuration add24HourDays(long days) {
		return validate(totalNs.add(BigInteger.valueOf(days).multiply(NS_PER_DAY)));
	}
	public TimeDuration add24HourDays(double days) {
		return validate(totalNs.add(big(days).multiply(NS_PER_DAY)));
	}
	public TimeDuration subtract(TimeDuration other) {
		return validate(totalNs.subtract(other.totalNs));
	}
	public BigInteger addToEpochNs(BigInteger epochNs) {
		return epochNs.add(totalNs);
	}
	public int sign() {
		return totalNs.signum();
	}
	public boolean isZero() {
		return totalNs.signum()==0;
	}
	@Override
	public int compareTo(TimeDuration other) {
		return totalNs.compareTo(other.totalNs);
	}

	// Truncating quotient
	public long divideToLong(BigInteger n) {
		return totalNs.divide(n).longValue();
	}

	public double fdiv(BigInteger n) {
		return new BigDecimal(totalNs).divide(new BigDecimal(n), new MathContext(60)).doubleValue();
	}

	public TimeDuration round(BigInteger increment, TemporalMath.RoundingMode mode) {
		return validate(TemporalMath.roundBigToIncrement(totalNs, increment, mode));
	}
}
