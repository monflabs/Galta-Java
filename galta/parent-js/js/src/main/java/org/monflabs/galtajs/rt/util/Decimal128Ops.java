package org.monflabs.galtajs.rt.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.monflabs.galtajs.external.ch_obermuhlner_math_big.BigDecimalMath;
import org.monflabs.util.TypeUtil;
import org.monflabs.util.number.Decimal128;

/**
 * Operations of the engine on {@link Decimal128} values, taking any {@link Number} (converted with
 * {@link TypeUtil#toDecimal128(Number)}). Used by the operators, the Math object and the transpiled code.
 * <p>
 * Contrary to {@link BigDecimal}, the results are always rounded to 34 digits, and a division by zero is
 * Infinity or NaN, as the Decimal proposal requires.
 */
public final class Decimal128Ops {

	/** Precision of the intermediate results of the functions computed through {@link BigDecimalMath}. */
	private static final MathContext WORK = new MathContext(45, RoundingMode.HALF_EVEN);

	private Decimal128Ops() {
	}

	/**
	 * An operand as a Decimal128. A Number -0 stays -0: this is the conversion of an operation (as {@code -0m + -0}),
	 * not the one of the {@code Decimal} constructor where -0 is +0.
	 */
	public static Decimal128 of(Number n) {
		if((n instanceof Double || n instanceof Float) && n.doubleValue()==0) {
			return ofIeee(n.doubleValue());
		}
		return TypeUtil.toDecimal128(n);
	}

	/**
	 * A double IEEE result (Math.pow, Math.atan2...) as a Decimal128, keeping -0. Unlike {@link Decimal128#of(double)},
	 * the conversion of the {@code Decimal} constructor where -0 is +0.
	 */
	public static Decimal128 ofIeee(double d) {
		return d==0 && Double.doubleToRawLongBits(d)<0 ? Decimal128.NEGATIVE_ZERO : Decimal128.of(d);
	}

	public static Decimal128 add(Number a, Number b) {
		return of(a).add(of(b));
	}

	public static Decimal128 subtract(Number a, Number b) {
		return of(a).subtract(of(b));
	}

	public static Decimal128 multiply(Number a, Number b) {
		return of(a).multiply(of(b));
	}

	public static Decimal128 divide(Number a, Number b) {
		return of(a).divide(of(b));
	}

	public static Decimal128 remainder(Number a, Number b) {
		return of(a).remainder(of(b));
	}

	public static Decimal128 negate(Number a) {
		return of(a).negate();
	}

	public static Decimal128 increment(Number a) {
		return of(a).add(Decimal128.ONE);
	}

	public static Decimal128 decrement(Number a) {
		return of(a).subtract(Decimal128.ONE);
	}

	/** {@code a ** b} with the Math.pow special cases; integer exponents are exact, the others use {@link BigDecimalMath}. */
	public static Decimal128 pow(Number a, Number b) {
		Decimal128 x = of(a);
		Decimal128 y = of(b);
		if (!x.isFinite() || !y.isFinite() || x.isZero() || y.isZero()) {
			return ofIeee(Math.pow(x.doubleValue(), y.doubleValue()));
		}
		if (y.isInteger() && y.abs().compare(Decimal128.of(Long.MAX_VALUE)) < 0) {
			return x.pow(y.longValue());
		}
		// Out of range results are known without computing them (that would fail or take very long):
		// log10|x^y| = y*log10|x|
		Decimal128 ax = x.abs();
		double magnitude = y.doubleValue() * (ax.exponent() + Math.log10(ax.mantissa().doubleValue()));
		boolean negative = x.isNegative() && y.isInteger() && y.toBigInteger().testBit(0);
		if (magnitude > 6146) {
			return negative ? Decimal128.NEGATIVE_INFINITY : Decimal128.POSITIVE_INFINITY;
		}
		if (magnitude < -6178) {
			return negative ? Decimal128.NEGATIVE_ZERO : Decimal128.ZERO;
		}
		Decimal128 r = fn(() -> BigDecimalMath.pow(x.toBigDecimal(), y.toBigDecimal(), WORK));
		// NaN when BigDecimalMath fails: the double result has the IEEE special case (NaN for a negative base)
		return r.isNaN() ? ofIeee(Math.pow(x.doubleValue(), y.doubleValue())) : r;
	}

	/** Compares two values: -1, 0, 1 or {@link Decimal128#UNORDERED}. */
	public static int compare(Number a, Number b) {
		return of(a).compare(of(b));
	}

	public static boolean isTrue(Number a) {
		Decimal128 d = of(a);
		return !d.isNaN() && !d.isZero();
	}

	/** The result of a {@link BigDecimalMath} computation, rounded to a Decimal128; NaN when the domain is invalid. */
	public static Decimal128 fn(java.util.function.Supplier<BigDecimal> f) {
		try {
			return Decimal128.of(f.get());
		} catch (ArithmeticException e) {
			return Decimal128.NaN;
		}
	}
}
