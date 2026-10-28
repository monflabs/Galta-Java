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
package org.monflabs.util.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An immutable IEEE 754-2019 Decimal128 value, following the ECMAScript Decimal proposal
 * (<a href="https://tc39.es/proposal-decimal/">https://tc39.es/proposal-decimal/</a>).
 * <p>
 * A value is NaN, +Infinity, -Infinity, +0, -0, or a finite number {@code n x 10^q} with {@code 0 < n < 10^34} and
 * {@code -6176 <= q <= 6111}. As in the proposal, the members of a cohort (<code>1.5</code> and <code>1.50</code>) are the
 * same value, and there is a single NaN. Results are computed as if with infinite precision and unbounded range, then
 * rounded to the nearest representable value ({@link DecimalRoundingMode#HALF_EVEN} unless stated); a magnitude
 * beyond the range overflows to an infinity whatever the rounding mode.
 * <h2>Storage</h2>
 * A value is two {@code long}s, in the IEEE 754 BID (binary integer decimal) encoding: the sign, a 14 bit biased
 * exponent and the 113 bit coefficient in {@code hi} and {@code lo}; see {@link #highBits()}, {@link #lowBits()} and
 * {@link #fromBits(long, long)}. Values are kept canonical (trailing zeros of the coefficient removed as long as the exponent
 * stays in range), so that the two words identify the value: equality and hashing are word comparisons, and no
 * allocation is needed to compare, negate or test a value.
 * Operations whose operands have a coefficient under 2<sup>63</sup> run on {@code long}s only; the others use {@link BigInteger}.
 * <h2>Differences with the proposal</h2>
 * <ul>
 * <li>{@link #equals(Object)} and {@link #compareTo(Decimal128)} are Java value identity and a total order
 * (like {@link Double}): NaN equals NaN, -0 is below +0. The proposal's comparisons are {@link #equalTo}, {@link #notEquals},
 * {@link #lessThan}, ... and {@link #compare}, which treat -0 and +0 as equal and NaN as unordered.</li>
 * <li>{@code toJSON} and {@code valueOf} (which throw in the proposal) and {@code toLocaleString} are not provided.</li>
 * <li>{@link #toFixed(int, DecimalRoundingMode)} and {@link #toPrecision(int, DecimalRoundingMode)} implement the intent of the
 * Number methods of the same name (a fixed number of fractional digits, a number of significant digits) with the rounding
 * mode applied toward the sign of the value, as {@link #round(int, DecimalRoundingMode)} does, since the steps of the proposal
 * do not yield these results.</li>
 * <li>The rounding of a non-integer to zero by {@link #round} gives +0, as the steps of the proposal specify.</li>
 * </ul>
 */
public final class Decimal128 extends Number implements Comparable<Decimal128> {

	private static final long serialVersionUID = 1L;

	/** The maximum number of significant digits. */
	public static final int MAX_DIGITS = 34;
	/** The smallest exponent {@code q} of a finite value {@code n x 10^q}. */
	public static final int MIN_EXPONENT = -6176;
	/** The largest exponent {@code q} of a finite value {@code n x 10^q}. */
	public static final int MAX_EXPONENT = 6111;
	/** The result of {@link #compare(Decimal128)} when a value is NaN. */
	public static final int UNORDERED = 2;

	private static final int EXP_BIAS = 6176;
	private static final int EXP_SHIFT = 49;
	private static final long SIGN = 1L << 63;
	private static final long COEF_HI_MASK = (1L << EXP_SHIFT) - 1;
	private static final long SPECIAL_MASK = 0x7800000000000000L;
	private static final long NAN_MASK = 0x7C00000000000000L;
	private static final long FORM2_MASK = 0x6000000000000000L;
	/** Exponents are clamped there when parsed or scaled: far beyond what overflows or underflows. */
	private static final long EXP_CAP = 1_000_000_000_000L;
	/** The digits kept from a long literal, the rest being reduced to a sticky digit. */
	private static final int PARSE_DIGITS = 40;
	private static final int BIG_POW10_SIZE = 80;
	private static final double LOG10_2 = 0.30102999566398114;
	private static final long HALF_POW19 = 5_000_000_000_000_000_000L;

	// Tables - they must be initialized before the constants

	private static final long[] P10 = new long[19];
	private static final double[] P10D = new double[23];
	private static final BigInteger[] BIG_P10 = new BigInteger[BIG_POW10_SIZE];
	private static final long[] P10_HI = new long[MAX_DIGITS + 1];
	private static final long[] P10_LO = new long[MAX_DIGITS + 1];
	private static final BigInteger MAX_COEFFICIENT_PLUS_1;
	static {
		P10[0] = 1;
		for (int i = 1; i < P10.length; i++) {
			P10[i] = P10[i - 1] * 10;
		}
		for (int i = 0; i < P10D.length; i++) {
			P10D[i] = Double.parseDouble("1e" + i);
		}
		BIG_P10[0] = BigInteger.ONE;
		for (int i = 1; i < BIG_P10.length; i++) {
			BIG_P10[i] = BIG_P10[i - 1].multiply(BigInteger.TEN);
		}
		for (int i = 0; i <= MAX_DIGITS; i++) {
			BigInteger p = BIG_P10[i];
			P10_HI[i] = p.shiftRight(64).longValue();
			P10_LO[i] = p.longValue();
		}
		MAX_COEFFICIENT_PLUS_1 = BIG_P10[MAX_DIGITS];
	}

	/** NaN. */
	public static final Decimal128 NaN = new Decimal128(NAN_MASK, 0);
	/** +Infinity. */
	public static final Decimal128 POSITIVE_INFINITY = new Decimal128(SPECIAL_MASK, 0);
	/** -Infinity. */
	public static final Decimal128 NEGATIVE_INFINITY = new Decimal128(SIGN | SPECIAL_MASK, 0);
	/** +0. */
	public static final Decimal128 ZERO = new Decimal128((long) EXP_BIAS << EXP_SHIFT, 0);
	/** -0. */
	public static final Decimal128 NEGATIVE_ZERO = new Decimal128(SIGN | ((long) EXP_BIAS << EXP_SHIFT), 0);
	/** 1. */
	public static final Decimal128 ONE = new Decimal128((long) EXP_BIAS << EXP_SHIFT, 1);
	/** 10. */
	public static final Decimal128 TEN = new Decimal128((long) (EXP_BIAS + 1) << EXP_SHIFT, 1);
	/** The largest finite value, 9999999999999999999999999999999999 x 10^6111. */
	public static final Decimal128 MAX_VALUE = packBig(false, MAX_COEFFICIENT_PLUS_1.subtract(BigInteger.ONE), MAX_EXPONENT);
	/** The smallest positive value, 1 x 10^-6176. */
	public static final Decimal128 MIN_VALUE = new Decimal128(0, 1);

	private final long hi;
	private final long lo;

	private Decimal128(long hi, long lo) {
		this.hi = hi;
		this.lo = lo;
	}

	// ======================================================================================
	// Factories
	// ======================================================================================

	/**
	 * Parses a decimal literal as the {@code Decimal} constructor of the proposal does for a string: an optional sign, then
	 * {@code Infinity} or digits with an optional fractional part and an optional exponent ({@code 12}, {@code -1.5e-3},
	 * {@code .5}, {@code 1.}); "NaN" (unsigned). No white space is accepted. The value is rounded to the Decimal128 domain
	 * (half even), so {@code 1e9999} is Infinity.
	 * @throws NumberFormatException (the SyntaxError of the proposal) when the text is not a decimal literal
	 */
	public static Decimal128 of(String text) {
		if (text == null) {
			throw new NumberFormatException("null");
		}
		int n = text.length();
		if (text.equals("NaN")) {
			return NaN;
		}
		int i = 0;
		boolean neg = false;
		if (n > 0) {
			char c = text.charAt(0);
			if (c == '-') {
				neg = true;
				i++;
			} else if (c == '+') {
				i++;
			}
		}
		if (n - i == 8 && text.startsWith("Infinity", i)) {
			return neg ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
		}
		int intStart = i;
		while (i < n && isDigit(text.charAt(i))) {
			i++;
		}
		int intEnd = i;
		int fracStart = i;
		int fracEnd = i;
		if (i < n && text.charAt(i) == '.') {
			fracStart = ++i;
			while (i < n && isDigit(text.charAt(i))) {
				i++;
			}
			fracEnd = i;
		}
		if (intEnd == intStart && fracEnd == fracStart) {
			throw invalid(text);
		}
		long exp = 0;
		if (i < n && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
			i++;
			boolean expNeg = false;
			if (i < n && (text.charAt(i) == '-' || text.charAt(i) == '+')) {
				expNeg = text.charAt(i) == '-';
				i++;
			}
			int expStart = i;
			while (i < n && isDigit(text.charAt(i))) {
				exp = Math.min(exp * 10 + (text.charAt(i) - '0'), EXP_CAP);
				i++;
			}
			if (i == expStart) {
				throw invalid(text);
			}
			if (expNeg) {
				exp = -exp;
			}
		}
		if (i != n) {
			throw invalid(text);
		}
		exp -= fracEnd - fracStart;

		// Up to 18 significant digits fit a long
		long acc = 0;
		int sig = 0;
		boolean tooLong = false;
		for (int p = intStart; p < fracEnd && !tooLong; p++) {
			if (p == intEnd) {
				p = fracStart;
				if (p >= fracEnd) {
					break;
				}
			}
			int d = text.charAt(p) - '0';
			if (sig == 0 && d == 0) {
				continue;
			}
			if (sig == 18) {
				tooLong = true;
			} else {
				acc = acc * 10 + d;
				sig++;
			}
		}
		if (!tooLong) {
			return make(neg, acc, exp);
		}

		// More: keep the leading digits, and a sticky digit for the non-zero ones that follow
		StringBuilder digits = new StringBuilder(fracEnd - intStart);
		for (int p = intStart; p < fracEnd; p++) {
			if (p == intEnd) {
				p = fracStart;
				if (p >= fracEnd) {
					break;
				}
			}
			char c = text.charAt(p);
			if (digits.length() > 0 || c != '0') {
				digits.append(c);
			}
		}
		int len = digits.length();
		if (len > PARSE_DIGITS) {
			boolean sticky = false;
			for (int p = PARSE_DIGITS; p < len && !sticky; p++) {
				sticky = digits.charAt(p) != '0';
			}
			digits.setLength(PARSE_DIGITS);
			exp += len - PARSE_DIGITS;
			if (sticky) {
				digits.append('1');
				exp--;
			}
		}
		return roundBig(neg, new BigInteger(digits.toString()), exp, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * Converts an integer (a BigInt for the proposal).
	 */
	public static Decimal128 of(long value) {
		if (value >= 0) {
			return make(false, value, 0);
		}
		if (value == Long.MIN_VALUE) {
			return packBig(true, BigInteger.valueOf(value).negate(), 0);
		}
		return make(true, -value, 0);
	}

	/**
	 * Converts an integer, rounding it (half even) when it has more than 34 digits and to an infinity when it is too large.
	 */
	public static Decimal128 of(BigInteger value) {
		return of(value, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * Converts an integer, rounding it with the given mode when it has more than 34 digits and to an infinity when it is too large.
	 */
	public static Decimal128 of(BigInteger value, DecimalRoundingMode mode) {
		Objects.requireNonNull(mode);
		return roundBig(value.signum() < 0, value.abs(), 0, mode);
	}

	/**
	 * Converts a {@link BigDecimal}, rounding it (half even) when it has more than 34 digits. The result is never -0.
	 */
	public static Decimal128 of(BigDecimal value) {
		return of(value, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * Converts a {@link BigDecimal}, rounding it with the given mode when it has more than 34 digits.
	 * The result is never -0.
	 */
	public static Decimal128 of(BigDecimal value, DecimalRoundingMode mode) {
		Objects.requireNonNull(mode);
		BigInteger unscaled = value.unscaledValue();
		return roundBig(unscaled.signum() < 0, unscaled.abs(), -(long) value.scale(), mode);
	}

	/**
	 * Converts a {@code double} as the {@code Decimal} constructor of the proposal does for a Number: through its shortest
	 * string that round-trips, so {@code of(0.1)} is {@code 0.1} (not the binary approximation) and -0 is +0.
	 */
	public static Decimal128 of(double value) {
		if (Double.isNaN(value)) {
			return NaN;
		}
		if (Double.isInfinite(value)) {
			return value > 0 ? POSITIVE_INFINITY : NEGATIVE_INFINITY;
		}
		if (value == 0) {
			return ZERO;
		}
		// The shortest digits that identify the double, as Number.prototype.toString. Double.toString has at least 2 digits, so
		// 4.9E-324 and not 5e-324 for the smallest double
		double abs = Math.abs(value);
		BigDecimal digits = new BigDecimal(Double.toString(abs));
		if (digits.precision() == 2) {
			for (RoundingMode mode : new RoundingMode[] { RoundingMode.HALF_EVEN, RoundingMode.DOWN }) {
				BigDecimal shorter = digits.round(new MathContext(1, mode));
				if (shorter.doubleValue() == abs) {
					digits = shorter;
					break;
				}
			}
		}
		return of(value < 0 ? digits.negate() : digits);
	}

	/**
	 * A new instance with the same value. The canonical values (NaN, the infinities, the zeros) are shared instances,
	 * which is a problem for identity-based uses such as the boxing of the GaltaJS engine.
	 */
	public Decimal128 copy() {
		return new Decimal128(hi, lo);
	}

	/**
	 * Builds a value from its IEEE 754 BID encoding. Encodings that are not canonical (a coefficient of 10^34 or more, a NaN
	 * payload, trailing zeros) are normalized, as the proposal has a single NaN and no cohorts.
	 */
	public static Decimal128 fromBits(long hi, long lo) {
		if ((hi & NAN_MASK) == NAN_MASK) {
			return NaN;
		}
		boolean neg = hi < 0;
		if ((hi & SPECIAL_MASK) == SPECIAL_MASK) {
			return neg ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
		}
		if ((hi & FORM2_MASK) == FORM2_MASK) {
			return zero(neg);	// The coefficient would be over 2^113
		}
		long chi = hi & COEF_HI_MASK;
		if (compareU128(chi, lo, P10_HI[MAX_DIGITS], P10_LO[MAX_DIGITS]) >= 0) {
			return zero(neg);	// Not a valid coefficient, which IEEE 754 reads as a zero
		}
		int q = (int) ((hi >>> EXP_SHIFT) & 0x3FFF) - EXP_BIAS;
		if (chi == 0 && lo >= 0) {
			return make(neg, lo, q);
		}
		return packBig(neg, big(chi, lo), q);
	}

	/**
	 * The upper 64 bits of the IEEE 754 BID encoding of the value (sign, exponent, upper bits of the coefficient).
	 */
	public long highBits() {
		return hi;
	}

	/**
	 * The lower 64 bits of the IEEE 754 BID encoding of the value (lower bits of the coefficient).
	 */
	public long lowBits() {
		return lo;
	}

	// ======================================================================================
	// Conversions
	// ======================================================================================

	/**
	 * The value as a {@link BigDecimal}, exactly. -0 gives 0.
	 * @throws ArithmeticException for NaN and the infinities
	 */
	public BigDecimal toBigDecimal() {
		if (special()) {
			throw new ArithmeticException(toString() + " is not finite");
		}
		if (isZeroValue()) {
			return BigDecimal.ZERO;
		}
		BigInteger c = coefficient();
		if (isNeg()) {
			c = c.negate();
		}
		int q = q();
		if (q > 0) {
			return new BigDecimal(c.multiply(pow10(q)));	// An integer, not 1E+2
		}
		return new BigDecimal(c, -q);
	}

	/**
	 * The value as a {@link BigInteger} (the {@code BigInt} function of the proposal). -0 gives 0.
	 * @throws ArithmeticException (the RangeError of the proposal) for NaN, the infinities and the values that are not integers
	 */
	public BigInteger toBigInteger() {
		if (special() || (!isZeroValue() && q() < 0)) {
			throw new ArithmeticException(toString() + " is not an integer");
		}
		return toBigDecimal().toBigIntegerExact();
	}

	/**
	 * The nearest {@code double} (the {@code Number} function of the proposal): the exponential string of the value, parsed.
	 */
	@Override
	public double doubleValue() {
		if (isNaN()) {
			return Double.NaN;
		}
		if (special()) {
			return isNeg() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		}
		if (isZeroValue()) {
			return isNeg() ? -0.0 : 0.0;
		}
		int q = q();
		if (small() && lo <= (1L << 53) && q >= -22 && q <= 22) {
			// Exact operands, one correctly rounded operation
			double v = q < 0 ? (double) lo / P10D[-q] : (double) lo * P10D[q];
			return isNeg() ? -v : v;
		}
		return Double.parseDouble(toExponential());
	}

	@Override
	public float floatValue() {
		if (isNaN()) {
			return Float.NaN;
		}
		if (special()) {
			return isNeg() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
		}
		if (isZeroValue()) {
			return isNeg() ? -0.0f : 0.0f;
		}
		return Float.parseFloat(toExponential());
	}

	/**
	 * The integer part of the value; NaN is 0, and the values out of the range of a {@code long} saturate.
	 */
	@Override
	public long longValue() {
		if (isNaN()) {
			return 0;
		}
		boolean neg = isNeg();
		if (special()) {
			return neg ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
		if (isZeroValue()) {
			return 0;
		}
		int q = q();
		if (small()) {
			if (q < 0) {
				return q >= -18 ? signed(neg, lo / P10[-q]) : 0;
			}
			if (q <= 18) {
				long p = P10[q];
				long r = lo * p;
				if (Math.multiplyHigh(lo, p) == 0 && r >= 0) {
					return signed(neg, r);
				}
			}
			return neg ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
		if (q >= 0) {
			return neg ? Long.MIN_VALUE : Long.MAX_VALUE;	// The coefficient alone is over 2^63
		}
		BigInteger v = q < -MAX_DIGITS ? BigInteger.ZERO : coefficient().divide(pow10(-q));
		if (v.bitLength() > 63) {
			return neg ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
		return signed(neg, v.longValue());
	}

	/**
	 * The integer part of the value; NaN is 0, and the values out of the range of an {@code int} saturate.
	 */
	@Override
	public int intValue() {
		long v = longValue();
		return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : v < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) v;
	}

	private static long signed(boolean neg, long magnitude) {
		return neg ? -magnitude : magnitude;
	}

	// ======================================================================================
	// Properties
	// ======================================================================================

	/** True for NaN. */
	public boolean isNaN() {
		return (hi & NAN_MASK) == NAN_MASK;
	}

	/** True for +Infinity and -Infinity. */
	public boolean isInfinite() {
		return (hi & NAN_MASK) == SPECIAL_MASK;
	}

	/** False for NaN and the infinities. */
	public boolean isFinite() {
		return !special();
	}

	/** True for +0 and -0. */
	public boolean isZero() {
		return isZeroValue();
	}

	/** True for the finite values without a fractional part, zeros included. */
	public boolean isInteger() {
		return !special() && (isZeroValue() || q() >= 0);
	}

	/** True for the values with the sign bit set: negative values, -0 and -Infinity. */
	public boolean isNegative() {
		return hi < 0 && !isNaN();
	}

	/**
	 * The sign of the value: -1, 0 or 1 (0 for the zeros); 0 for NaN too.
	 */
	public int signum() {
		if (isNaN() || isZeroValue()) {
			return 0;
		}
		return isNeg() ? -1 : 1;
	}

	/**
	 * The exponent {@code e} of the value {@code s x 10^e} with {@code 1 <= |s| < 10}, as the proposal's {@code exponent()}: NaN
	 * for NaN, +Infinity for the infinities and -Infinity for the zeros.
	 */
	public double exponent() {
		if (isNaN()) {
			return Double.NaN;
		}
		if (special()) {
			return Double.POSITIVE_INFINITY;
		}
		if (isZeroValue()) {
			return Double.NEGATIVE_INFINITY;
		}
		return adjustedExponent();
	}

	/**
	 * The significand {@code s} of the value {@code s x 10^e} with {@code 1 <= |s| < 10}, with the sign of the value. NaN gives
	 * NaN, the infinities give +Infinity and the zeros are unchanged.
	 */
	public Decimal128 mantissa() {
		if (isNaN()) {
			return NaN;
		}
		if (special()) {
			return POSITIVE_INFINITY;
		}
		if (isZeroValue()) {
			return this;
		}
		int shift = 1 - digitCount();
		if (small()) {
			return make(isNeg(), lo, shift);
		}
		return packBig(isNeg(), coefficient(), shift);
	}

	// ======================================================================================
	// Arithmetic
	// ======================================================================================

	/** The absolute value; NaN stays NaN and -0 gives +0. */
	public Decimal128 abs() {
		return hi < 0 && !isNaN() ? new Decimal128(hi & ~SIGN, lo) : this;
	}

	/** The value with the opposite sign; NaN stays NaN and the zeros and infinities change sign. */
	public Decimal128 negate() {
		return isNaN() ? this : new Decimal128(hi ^ SIGN, lo);
	}

	/** The sum, rounded half even. */
	public Decimal128 add(Decimal128 x) {
		if (special() || x.special()) {
			return addSpecial(x);
		}
		boolean z1 = isZeroValue();
		boolean z2 = x.isZeroValue();
		if (z1 && isNeg()) {
			return x;
		}
		if (z2 && x.isNeg()) {
			return this;
		}
		if (z1) {
			return z2 ? ZERO : x;
		}
		if (z2) {
			return this;
		}
		return addFinite(isNeg(), x, x.isNeg());
	}

	/** The difference, rounded half even. */
	public Decimal128 subtract(Decimal128 x) {
		if (special() || x.special()) {
			return subtractSpecial(x);
		}
		boolean z1 = isZeroValue();
		boolean z2 = x.isZeroValue();
		if (z1 && z2) {
			return isNeg() && !x.isNeg() ? NEGATIVE_ZERO : ZERO;
		}
		if (z2) {
			return this;
		}
		if (z1) {
			return x.negate();
		}
		return addFinite(isNeg(), x, !x.isNeg());
	}

	/** The product, rounded half even. */
	public Decimal128 multiply(Decimal128 x) {
		if (isNaN() || x.isNaN()) {
			return NaN;
		}
		boolean neg = isNeg() != x.isNeg();
		if (special() || x.special()) {
			if (isZeroValue() || x.isZeroValue()) {
				return NaN;
			}
			return neg ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
		}
		if (isZeroValue() || x.isZeroValue()) {
			return zero(neg);
		}
		long q = (long) q() + x.q();
		if (small() && x.small()) {
			long p = lo * x.lo;
			if (Math.multiplyHigh(lo, x.lo) == 0 && p >= 0) {
				return make(neg, p, q);
			}
		}
		return roundBig(neg, coefficient().multiply(x.coefficient()), q, DecimalRoundingMode.DEFAULT);
	}

	/** The quotient, rounded half even. */
	public Decimal128 divide(Decimal128 x) {
		if (isNaN() || x.isNaN()) {
			return NaN;
		}
		boolean neg = isNeg() != x.isNeg();
		if (special()) {
			if (x.special()) {
				return NaN;
			}
			return neg ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
		}
		if (x.special()) {
			return zero(neg);
		}
		boolean z1 = isZeroValue();
		if (x.isZeroValue()) {
			if (z1) {
				return NaN;
			}
			return neg ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
		}
		if (z1) {
			return zero(neg);
		}
		long q = (long) q() - x.q();
		if (small() && x.small() && lo % x.lo == 0) {
			return make(neg, lo / x.lo, q);
		}
		BigInteger c1 = coefficient();
		BigInteger c2 = x.coefficient();
		// A quotient of at least 35 digits, then a sticky digit when it is not exact
		int shift = MAX_DIGITS + 2 + x.digitCount() - digitCount();
		BigInteger[] qr = c1.multiply(pow10(shift)).divideAndRemainder(c2);
		BigInteger quotient = qr[0];
		long exp = q - shift;
		if (qr[1].signum() != 0) {
			quotient = quotient.multiply(BigInteger.TEN).add(BigInteger.ONE);
			exp--;
		}
		return roundBig(neg, quotient, exp, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * The remainder of the division, with the sign of this value (the remainder of {@code Number}, not the IEEE 754 one):
	 * {@code this - x * trunc(this / x)}, which is always exact.
	 */
	public Decimal128 remainder(Decimal128 x) {
		if (isNaN() || x.isNaN() || isInfinite()) {
			return NaN;
		}
		if (x.isInfinite()) {
			return this;
		}
		if (x.isZeroValue()) {
			return NaN;
		}
		if (isZeroValue()) {
			return this;
		}
		boolean neg = isNeg();
		int m = compareMagnitude(x);
		if (m < 0) {
			return this;
		}
		if (m == 0) {
			return zero(neg);
		}
		int q1 = q();
		int q2 = x.q();
		if (q1 == q2 && small() && x.small()) {
			return make(neg, lo % x.lo, q1);
		}
		BigInteger c1 = coefficient();
		BigInteger c2 = x.coefficient();
		BigInteger r;
		int exp;
		if (q1 >= q2) {
			// (c1 * 10^k) mod c2, without building 10^k
			r = c1.mod(c2).multiply(BigInteger.TEN.modPow(BigInteger.valueOf(q1 - q2), c2)).mod(c2);
			exp = q2;
		} else {
			// |this| >= |x|: the exponents are at most 34 digits apart
			r = c1.mod(c2.multiply(pow10(q2 - q1)));
			exp = q1;
		}
		if (r.signum() == 0) {
			return zero(neg);
		}
		return roundBig(neg, r, exp, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * Rounds to a number of fractional digits with the half even mode.
	 * @see #round(int, DecimalRoundingMode)
	 */
	public Decimal128 round(int numFractionalDigits) {
		return round(numFractionalDigits, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * Rounds to the given number of fractional digits. NaN, the infinities and the zeros are unchanged; a value that rounds to zero
	 * gives +0 (as in the proposal).
	 * @throws IllegalArgumentException (the RangeError of the proposal) when {@code numFractionalDigits} is negative
	 */
	public Decimal128 round(int numFractionalDigits, DecimalRoundingMode mode) {
		if (numFractionalDigits < 0) {
			throw new IllegalArgumentException("The number of fractional digits must not be negative: " + numFractionalDigits);
		}
		Objects.requireNonNull(mode);
		if (special() || isZeroValue()) {
			return this;
		}
		Decimal128 r = roundFinite(numFractionalDigits, mode);
		return r.isZeroValue() ? ZERO : r;
	}

	/**
	 * Raises to an integer power, with the special cases of {@code Math.pow}: any value to the power 0 is 1 (NaN too),
	 * NaN otherwise gives NaN, the infinities and the zeros give an infinity or a zero whose sign is negative for a
	 * negative base and an odd exponent. A finite result is computed with 34 digits, half even; as with
	 * {@link BigDecimal#pow(int, MathContext)} the last digit may differ by one or two units from the exact rounding.
	 * An overflow gives an infinity, an underflow a zero.
	 */
	public Decimal128 pow(long n) {
		if (n == 0) {
			return ONE;
		}
		if (isNaN()) {
			return NaN;
		}
		boolean negResult = isNeg() && (n & 1) != 0;
		if (isInfinite() || isZeroValue()) {
			boolean big = isInfinite() == (n > 0);	// infinity ^ positive, 0 ^ negative: an infinity
			return big ? (negResult ? NEGATIVE_INFINITY : POSITIVE_INFINITY) : (negResult ? NEGATIVE_ZERO : ZERO);
		}
		if (n == 1) {
			return this;
		}
		int cmp = abs().compare(ONE);
		if (cmp == 0) {
			return negResult ? ONE.negate() : ONE;
		}
		if (n > 999999999L || n < -999999999L) {
			return (cmp > 0) == (n > 0) ? (negResult ? NEGATIVE_INFINITY : POSITIVE_INFINITY) : (negResult ? NEGATIVE_ZERO : ZERO);
		}
		try {
			return of(toBigDecimal().pow((int) n, POW_CONTEXT));
		} catch (ArithmeticException e) {
			// The exponent of the result does not fit an int
			return (cmp > 0) == (n > 0) ? (negResult ? NEGATIVE_INFINITY : POSITIVE_INFINITY) : (negResult ? NEGATIVE_ZERO : ZERO);
		}
	}
	// A few guard digits: the result is rounded again to 34 by of()
	private static final MathContext POW_CONTEXT = new MathContext(MAX_DIGITS + 6, RoundingMode.HALF_EVEN);

	/**
	 * Multiplies by 10 to the power of {@code n}, rounded half even like any result.
	 */
	public Decimal128 scale10(long n) {
		if (special() || isZeroValue()) {
			return this;
		}
		long shift = Math.max(-EXP_CAP, Math.min(EXP_CAP, n));
		if (small()) {
			return make(isNeg(), lo, q() + shift);
		}
		return roundBig(isNeg(), coefficient(), q() + shift, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * Multiplies by 10 to the power of {@code n}, where {@code n} is a Number as in the proposal: NaN, an infinity or an integer.
	 * @throws IllegalArgumentException (the RangeError of the proposal) when {@code n} is finite and not an integer
	 */
	public Decimal128 scale10(double n) {
		if (!Double.isNaN(n) && !Double.isInfinite(n)) {
			if (n != Math.rint(n)) {
				throw new IllegalArgumentException("The exponent must be an integer: " + n);
			}
			return scale10(n >= 9.2e18 ? Long.MAX_VALUE : n <= -9.2e18 ? Long.MIN_VALUE : (long) n);
		}
		if (special() || isZeroValue()) {
			return this;
		}
		if (Double.isNaN(n)) {
			return NaN;
		}
		if (n > 0) {
			return isNeg() ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
		}
		return zero(isNeg());
	}

	// ======================================================================================
	// Comparisons
	// ======================================================================================

	/**
	 * Compares the mathematical values (the zeros are equal): -1, 0 or 1, or {@link #UNORDERED} when one is NaN.
	 */
	public int compare(Decimal128 x) {
		if (isNaN() || x.isNaN()) {
			return UNORDERED;
		}
		return compareValues(x);
	}

	/** True when the values are equal (+0 equals -0, NaN equals nothing). The {@code equals} of the proposal. */
	public boolean equalTo(Decimal128 x) {
		return compare(x) == 0;
	}

	/** True when the values are different (NaN is different from nothing). The {@code notEquals} of the proposal. */
	public boolean notEquals(Decimal128 x) {
		int c = compare(x);
		return c != 0 && c != UNORDERED;
	}

	/** True when this value is smaller; false when one is NaN. */
	public boolean lessThan(Decimal128 x) {
		return compare(x) < 0;
	}

	/** True when this value is smaller or equal; false when one is NaN. */
	public boolean lessThanOrEqual(Decimal128 x) {
		int c = compare(x);
		return c <= 0;
	}

	/** True when this value is greater; false when one is NaN. */
	public boolean greaterThan(Decimal128 x) {
		int c = compare(x);
		return c > 0 && c != UNORDERED;
	}

	/** True when this value is greater or equal; false when one is NaN. */
	public boolean greaterThanOrEqual(Decimal128 x) {
		int c = compare(x);
		return c >= 0 && c != UNORDERED;
	}

	/**
	 * The total order: -Infinity, negative values, -0, +0, positive values, +Infinity, NaN; consistent with {@link #equals}.
	 */
	@Override
	public int compareTo(Decimal128 o) {
		if (isNaN()) {
			return o.isNaN() ? 0 : 1;
		}
		if (o.isNaN()) {
			return -1;
		}
		int c = compareValues(o);
		if (c == 0 && isZeroValue()) {
			return (isNeg() ? 0 : 1) - (o.isNeg() ? 0 : 1);
		}
		return c;
	}

	/**
	 * True for the same value, NaN included, and -0 being different from +0.
	 */
	@Override
	public boolean equals(Object o) {
		return o instanceof Decimal128 && ((Decimal128) o).hi == hi && ((Decimal128) o).lo == lo;
	}

	@Override
	public int hashCode() {
		long h = hi * 0x9E3779B97F4A7C15L + lo;
		return (int) (h ^ (h >>> 32));
	}

	// ======================================================================================
	// Strings
	// ======================================================================================

	/**
	 * The string of the value: "NaN", "Infinity", "-Infinity", "0", "-0", else the digits in decimal notation without trailing
	 * zeros, or in exponential notation (see {@link #toExponential()}) when the magnitude is under 10<sup>-6</sup> or at
	 * least 10<sup>34</sup>.
	 */
	@Override
	public String toString() {
		if (special()) {
			return specialString();
		}
		if (isZeroValue()) {
			return isNeg() ? "-0" : "0";
		}
		int adj = adjustedExponent();
		if (adj < -6 || adj >= MAX_DIGITS) {
			return exponential(isNeg(), digits(), adj);
		}
		return plain(isNeg(), digits(), q());
	}

	/**
	 * The string in exponential notation: the significand without trailing zeros, "e", the exponent without a plus sign
	 * ({@code 1.5e-7}, {@code 1e34}); "0e+0" and "-0e+0" for the zeros.
	 */
	public String toExponential() {
		if (special()) {
			return specialString();
		}
		if (isZeroValue()) {
			return isNeg() ? "-0e+0" : "0e+0";
		}
		return exponential(isNeg(), digits(), adjustedExponent());
	}

	/**
	 * The string with no fractional digit, rounded half even.
	 */
	public String toFixed() {
		return toFixed(0, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * The string in decimal notation with exactly {@code digits} fractional digits, rounded half even.
	 * @see #toFixed(int, DecimalRoundingMode)
	 */
	public String toFixed(int digits) {
		return toFixed(digits, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * The string in decimal notation with exactly {@code digits} fractional digits (zeros are added), rounded with the mode.
	 * A negative value that rounds to zero keeps its sign ("-0.00").
	 * @throws IllegalArgumentException (the RangeError of the proposal) when {@code digits} is negative
	 */
	public String toFixed(int digits, DecimalRoundingMode mode) {
		if (digits < 0) {
			throw new IllegalArgumentException("The number of fractional digits must not be negative: " + digits);
		}
		Objects.requireNonNull(mode);
		if (special()) {
			return specialString();
		}
		boolean neg = isNeg();
		Decimal128 r = isZeroValue() ? this : roundFinite(digits, mode);
		StringBuilder sb = new StringBuilder();
		if (neg) {
			sb.append('-');
		}
		String intPart;
		String frac = "";
		if (r.isZeroValue()) {
			intPart = "0";
		} else {
			String d = r.digits();
			int q = r.q();
			if (q >= 0) {
				intPart = d + "0".repeat(q);
			} else if (d.length() > -q) {
				intPart = d.substring(0, d.length() + q);
				frac = d.substring(d.length() + q);
			} else {
				intPart = "0";
				frac = "0".repeat(-q - d.length()) + d;
			}
		}
		sb.append(intPart);
		if (digits > 0) {
			sb.append('.').append(frac).append("0".repeat(digits - frac.length()));
		}
		return sb.toString();
	}

	/**
	 * The same as {@link #toString()}.
	 */
	public String toPrecision() {
		return toString();
	}

	/**
	 * The string with the given number of significant digits, rounded half even.
	 * @see #toPrecision(int, DecimalRoundingMode)
	 */
	public String toPrecision(int precision) {
		return toPrecision(precision, DecimalRoundingMode.DEFAULT);
	}

	/**
	 * The string with exactly {@code precision} significant digits (zeros are added), rounded with the mode: in decimal
	 * notation, or in exponential notation (see {@link #toExponential()}) when the exponent is under -6 or at least {@code precision}.
	 * @throws IllegalArgumentException (the RangeError of the proposal) when {@code precision} is less than 1
	 */
	public String toPrecision(int precision, DecimalRoundingMode mode) {
		if (precision < 1) {
			throw new IllegalArgumentException("The precision must be at least 1: " + precision);
		}
		Objects.requireNonNull(mode);
		if (special()) {
			return specialString();
		}
		boolean neg = isNeg();
		if (isZeroValue()) {
			return (neg ? "-0" : "0") + (precision > 1 ? "." + "0".repeat(precision - 1) : "");
		}
		BigInteger c = coefficient();
		int d = digitCount();
		long adj = adjustedExponent();
		if (d > precision) {
			c = divRound(c, d, d - precision, neg, mode);
			if (digitCount(c) > precision) {
				c = c.divide(BigInteger.TEN);	// 99.9 -> 100
				adj++;
			}
		}
		String ds = c.toString();
		ds = ds + "0".repeat(precision - ds.length());
		StringBuilder sb = new StringBuilder();
		if (neg) {
			sb.append('-');
		}
		if (adj < -6 || adj >= precision) {
			sb.append(ds.charAt(0));
			if (precision > 1) {
				sb.append('.').append(ds, 1, precision);
			}
			sb.append('e').append(adj);
		} else if (adj >= 0) {
			sb.append(ds, 0, (int) adj + 1);
			if (precision > adj + 1) {
				sb.append('.').append(ds, (int) adj + 1, precision);
			}
		} else {
			sb.append("0.").append("0".repeat((int) (-adj - 1))).append(ds);
		}
		return sb.toString();
	}

	private String specialString() {
		return isNaN() ? "NaN" : isNeg() ? "-Infinity" : "Infinity";
	}

	/** The significand digits of a finite value (no sign, no zeros removed), as the string of the coefficient. */
	private String digits() {
		return small() ? Long.toString(lo) : coefficient().toString();
	}

	private static String plain(boolean neg, String d, int q) {
		StringBuilder sb = new StringBuilder(d.length() + 8);
		if (neg) {
			sb.append('-');
		}
		if (q >= 0) {
			sb.append(d).append("0".repeat(q));
		} else if (d.length() > -q) {
			sb.append(d, 0, d.length() + q).append('.').append(d, d.length() + q, d.length());
		} else {
			sb.append("0.").append("0".repeat(-q - d.length())).append(d);
		}
		return sb.toString();
	}

	private static String exponential(boolean neg, String d, int adj) {
		int end = d.length();
		while (end > 1 && d.charAt(end - 1) == '0') {	// Only for the coefficients kept beyond the exponent range
			end--;
		}
		d = d.substring(0, end);
		StringBuilder sb = new StringBuilder(d.length() + 8);
		if (neg) {
			sb.append('-');
		}
		sb.append(d.charAt(0));
		if (d.length() > 1) {
			sb.append('.').append(d, 1, d.length());
		}
		return sb.append('e').append(adj).toString();
	}

	// ======================================================================================
	// Representation
	// ======================================================================================

	private boolean special() {
		return (hi & SPECIAL_MASK) == SPECIAL_MASK;
	}

	private boolean isNeg() {
		return hi < 0;
	}

	/** True for a finite zero. */
	private boolean isZeroValue() {
		return (hi & SPECIAL_MASK) != SPECIAL_MASK && (hi & COEF_HI_MASK) == 0 && lo == 0;
	}

	/** True when the coefficient is under 2^63, hence the {@code lo} word. Only for finite values. */
	private boolean small() {
		return (hi & COEF_HI_MASK) == 0 && lo >= 0;
	}

	private int q() {
		return (int) ((hi >>> EXP_SHIFT) & 0x3FFF) - EXP_BIAS;
	}

	private BigInteger coefficient() {
		return big(hi & COEF_HI_MASK, lo);
	}

	/** The number of digits of the coefficient of a finite non-zero value. */
	private int digitCount() {
		return small() ? digitCount(lo) : digitCount128(hi & COEF_HI_MASK, lo);
	}

	/** The exponent of the value in scientific notation, of a finite non-zero value. */
	private int adjustedExponent() {
		return q() + digitCount() - 1;
	}

	private static Decimal128 zero(boolean neg) {
		return neg ? NEGATIVE_ZERO : ZERO;
	}

	private static Decimal128 infinity(boolean neg) {
		return neg ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
	}

	/** A value from a canonical coefficient and an exponent in range. */
	private static Decimal128 pack(boolean neg, long chi, long clo, int q) {
		return new Decimal128((neg ? SIGN : 0) | ((long) (q + EXP_BIAS) << EXP_SHIFT) | chi, clo);
	}

	/**
	 * The value {@code c x 10^q}, rounded half even when it is not representable. {@code c >= 0}.
	 */
	private static Decimal128 make(boolean neg, long c, long q) {
		if (c == 0) {
			return zero(neg);
		}
		if (q > MAX_EXPONENT || q < MIN_EXPONENT) {
			return roundBig(neg, BigInteger.valueOf(c), q, DecimalRoundingMode.DEFAULT);
		}
		while (q <= MAX_EXPONENT - 8 && c % 100000000L == 0) {
			c /= 100000000L;
			q += 8;
		}
		while (q < MAX_EXPONENT && c % 10 == 0) {
			c /= 10;
			q++;
		}
		return pack(neg, 0, c, (int) q);
	}

	/** A value from a coefficient under 10^34 and an exponent in range, which may have trailing zeros. */
	private static Decimal128 packBig(boolean neg, BigInteger c, int q) {
		if (c.signum() == 0) {
			return zero(neg);
		}
		while (q < MAX_EXPONENT && !c.testBit(0)) {
			BigInteger[] qr = c.divideAndRemainder(BigInteger.TEN);
			if (qr[1].signum() != 0) {
				break;
			}
			c = qr[0];
			q++;
		}
		return pack(neg, c.shiftRight(64).longValue(), c.longValue(), q);
	}

	/**
	 * The value {@code mag x 10^exp}, rounded to the domain: 34 digits at most, with the exponent in range; an infinity when too
	 * large. {@code mag >= 0}.
	 */
	private static Decimal128 roundBig(boolean neg, BigInteger mag, long exp, DecimalRoundingMode mode) {
		if (mag.signum() == 0) {
			return zero(neg);
		}
		int d = digitCount(mag);
		long te = Math.max(exp + d - 1 - (MAX_DIGITS - 1), MIN_EXPONENT);	// The exponent of the last digit kept
		if (te > exp) {
			mag = divRound(mag, d, te - exp, neg, mode);
			if (mag.signum() == 0) {
				return zero(neg);
			}
			exp = te;
			d = digitCount(mag);
			if (d > MAX_DIGITS) {
				mag = mag.divide(BigInteger.TEN);	// 10^34 after a carry
				exp++;
				d--;
			}
		}
		if (exp + d - 1 > MAX_EXPONENT + MAX_DIGITS - 1) {
			return infinity(neg);
		}
		if (exp > MAX_EXPONENT) {
			mag = mag.multiply(pow10(exp - MAX_EXPONENT));
			exp = MAX_EXPONENT;
		}
		return packBig(neg, mag, (int) exp);
	}

	/** The finite non-zero value rounded to a number of fractional digits (a zero keeps the sign). */
	private Decimal128 roundFinite(int numFractionalDigits, DecimalRoundingMode mode) {
		long drop = (long) -numFractionalDigits - q();
		if (drop <= 0) {
			return this;
		}
		boolean neg = isNeg();
		if (small()) {
			return make(neg, divRound(lo, (int) Math.min(drop, 20), neg, mode), -numFractionalDigits);
		}
		BigInteger c = divRound(coefficient(), digitCount(), drop, neg, mode);
		return roundBig(neg, c, -numFractionalDigits, mode);
	}

	private Decimal128 addSpecial(Decimal128 x) {
		if (isNaN() || x.isNaN()) {
			return NaN;
		}
		if (equals(POSITIVE_INFINITY) || x.equals(POSITIVE_INFINITY)) {
			return equals(NEGATIVE_INFINITY) || x.equals(NEGATIVE_INFINITY) ? NaN : POSITIVE_INFINITY;
		}
		return NEGATIVE_INFINITY;
	}

	private Decimal128 subtractSpecial(Decimal128 x) {
		if (isNaN() || x.isNaN()) {
			return NaN;
		}
		if (isInfinite()) {
			return equals(x) ? NaN : this;
		}
		return x.isNeg() ? POSITIVE_INFINITY : NEGATIVE_INFINITY;
	}

	/** The sum of two finite non-zero values, with the signs given for the operands. */
	private Decimal128 addFinite(boolean neg1, Decimal128 x, boolean neg2) {
		int q1 = q();
		int q2 = x.q();
		if (small() && x.small()) {
			long c1 = lo;
			long c2 = x.lo;
			boolean fits = true;
			if (q1 > q2) {
				fits = q1 - q2 <= 18;
				if (fits) {
					long p = P10[q1 - q2];
					c1 *= p;
					fits = Math.multiplyHigh(lo, p) == 0 && c1 >= 0;
				}
			} else if (q2 > q1) {
				fits = q2 - q1 <= 18;
				if (fits) {
					long p = P10[q2 - q1];
					c2 *= p;
					fits = Math.multiplyHigh(x.lo, p) == 0 && c2 >= 0;
				}
			}
			if (fits) {
				long a = neg1 ? -c1 : c1;
				long b = neg2 ? -c2 : c2;
				long r = a + b;
				if (((a ^ r) & (b ^ r)) >= 0 && r != Long.MIN_VALUE) {
					int q = Math.min(q1, q2);
					return r == 0 ? ZERO : r < 0 ? make(true, -r, q) : make(false, r, q);
				}
			}
		}
		BigInteger c1 = coefficient();
		BigInteger c2 = x.coefficient();
		long e1 = (long) q1 + digitCount() - 1;
		long e2 = (long) q2 + x.digitCount() - 1;
		// An operand far below the digits of the other one only tells that the result is inexact: a 1 stands for it
		final int far = MAX_DIGITS + 2;
		if (e2 < e1 - far) {
			c2 = BigInteger.ONE;
			q2 = (int) (e1 - far);
		} else if (e1 < e2 - far) {
			c1 = BigInteger.ONE;
			q1 = (int) (e2 - far);
		}
		int q = Math.min(q1, q2);
		BigInteger a = c1.multiply(pow10(q1 - q));
		BigInteger b = c2.multiply(pow10(q2 - q));
		BigInteger sum = (neg1 ? a.negate() : a).add(neg2 ? b.negate() : b);
		if (sum.signum() == 0) {
			return ZERO;
		}
		return roundBig(sum.signum() < 0, sum.abs(), q, DecimalRoundingMode.DEFAULT);
	}

	/** Compares two values that are not NaN. */
	private int compareValues(Decimal128 x) {
		boolean s1 = special();
		boolean s2 = x.special();
		if (s1 || s2) {
			if (s1 && s2) {
				return Boolean.compare(!isNeg(), !x.isNeg());
			}
			return s1 ? (isNeg() ? -1 : 1) : (x.isNeg() ? 1 : -1);
		}
		boolean z1 = isZeroValue();
		boolean z2 = x.isZeroValue();
		if (z1 || z2) {
			return z1 && z2 ? 0 : z1 ? (x.isNeg() ? 1 : -1) : (isNeg() ? -1 : 1);
		}
		boolean neg = isNeg();
		if (neg != x.isNeg()) {
			return neg ? -1 : 1;
		}
		int m = compareMagnitude(x);
		return neg ? -m : m;
	}

	/** Compares the absolute values of two finite non-zero values. */
	private int compareMagnitude(Decimal128 x) {
		int d1 = digitCount();
		int d2 = x.digitCount();
		int q1 = q();
		int q2 = x.q();
		int e1 = q1 + d1;
		int e2 = q2 + d2;
		if (e1 != e2) {
			return e1 < e2 ? -1 : 1;
		}
		// Same magnitude order: the shorter coefficient gets the zeros to compare digit by digit
		if (d1 == d2) {
			return compareU128(hi & COEF_HI_MASK, lo, x.hi & COEF_HI_MASK, x.lo);
		}
		if (small() && x.small() && d1 < 19 && d2 < 19) {
			return d1 < d2 ? Long.compare(lo * P10[d2 - d1], x.lo) : Long.compare(lo, x.lo * P10[d1 - d2]);
		}
		if (d1 < d2) {
			return compareScaled(hi & COEF_HI_MASK, lo, d2 - d1, x.hi & COEF_HI_MASK, x.lo);
		}
		return -compareScaled(x.hi & COEF_HI_MASK, x.lo, d1 - d2, hi & COEF_HI_MASK, lo);
	}

	/** Compares {@code c * 10^k} to {@code d}, knowing that the product is under 10^34. */
	private static int compareScaled(long chi, long clo, int k, long dhi, long dlo) {
		while (k > 0) {
			int s = Math.min(k, 18);
			long m = P10[s];
			long carry = Math.unsignedMultiplyHigh(clo, m);
			chi = chi * m + carry;
			clo *= m;
			k -= s;
		}
		return compareU128(chi, clo, dhi, dlo);
	}

	private static int compareU128(long ahi, long alo, long bhi, long blo) {
		int c = Long.compareUnsigned(ahi, bhi);
		return c != 0 ? c : Long.compareUnsigned(alo, blo);
	}

	// ======================================================================================
	// Integer helpers
	// ======================================================================================

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static NumberFormatException invalid(String text) {
		return new NumberFormatException("Not a decimal number: \"" + text + "\"");
	}

	private static BigInteger big(long chi, long clo) {
		if (chi == 0 && clo >= 0) {
			return BigInteger.valueOf(clo);
		}
		byte[] b = new byte[16];
		for (int i = 0; i < 8; i++) {
			b[i] = (byte) (chi >>> (56 - 8 * i));
			b[8 + i] = (byte) (clo >>> (56 - 8 * i));
		}
		return new BigInteger(1, b);
	}

	private static BigInteger pow10(long n) {
		return n < BIG_POW10_SIZE ? BIG_P10[(int) n] : BigInteger.TEN.pow((int) n);
	}

	/** The number of digits of a positive long. */
	private static int digitCount(long v) {
		int t = ((64 - Long.numberOfLeadingZeros(v)) * 1233) >>> 12;
		return t + (v < P10[t] ? 0 : 1);
	}

	/** The number of digits of a coefficient over 2^63 and under 10^34. */
	private static int digitCount128(long chi, long clo) {
		int bits = chi != 0 ? 128 - Long.numberOfLeadingZeros(chi) : 64 - Long.numberOfLeadingZeros(clo);
		int t = (int) (bits * LOG10_2);
		return t + (compareU128(chi, clo, P10_HI[t], P10_LO[t]) < 0 ? 0 : 1);
	}

	/** The number of digits of a positive integer. */
	private static int digitCount(BigInteger v) {
		int t = (int) (v.bitLength() * LOG10_2);
		return t + (v.compareTo(pow10(t)) < 0 ? 0 : 1);
	}

	/**
	 * Divides a positive integer by 10^drop and rounds the quotient. {@code digits} is the number of digits of {@code mag}.
	 */
	private static BigInteger divRound(BigInteger mag, int digits, long drop, boolean neg, DecimalRoundingMode mode) {
		BigInteger quotient;
		boolean inexact = true;
		int cmpHalf = -1;
		if (drop > digits) {
			quotient = BigInteger.ZERO;	// Under 0.1
		} else {
			BigInteger p = pow10(drop);
			BigInteger[] qr = mag.divideAndRemainder(p);
			quotient = qr[0];
			inexact = qr[1].signum() != 0;
			cmpHalf = qr[1].shiftLeft(1).compareTo(p);
		}
		return roundsUp(quotient.testBit(0), neg, mode, inexact, cmpHalf) ? quotient.add(BigInteger.ONE) : quotient;
	}

	/**
	 * Divides a positive long by 10^drop and rounds the quotient. {@code drop} is 20 for any value above 19.
	 */
	private static long divRound(long c, int drop, boolean neg, DecimalRoundingMode mode) {
		long quotient = 0;
		int cmpHalf = -1;
		if (drop <= 18) {
			long p = P10[drop];
			quotient = c / p;
			long r = c - quotient * p;
			if (r == 0) {
				return quotient;
			}
			cmpHalf = Long.compare(r << 1, p);
		} else if (drop == 19) {
			cmpHalf = Long.compare(c, HALF_POW19);
		}
		return roundsUp((quotient & 1) != 0, neg, mode, true, cmpHalf) ? quotient + 1 : quotient;
	}

	/**
	 * Tells whether the rounding of a quotient adds one to it. {@code cmpHalf} compares the fraction that was dropped to 0.5.
	 */
	private static boolean roundsUp(boolean odd, boolean neg, DecimalRoundingMode mode, boolean inexact, int cmpHalf) {
		if (!inexact) {
			return false;
		}
		switch (mode) {
		case TRUNC:
			return false;
		case FLOOR:
			return neg;
		case CEIL:
			return !neg;
		case HALF_EXPAND:
			return cmpHalf >= 0;
		default:
			return cmpHalf > 0 || (cmpHalf == 0 && odd);
		}
	}
}
