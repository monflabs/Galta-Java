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

	public static Decimal128 of(Number n) {
		return TypeUtil.toDecimal128(n);
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
			return Decimal128.of(Math.pow(x.doubleValue(), y.doubleValue()));
		}
		if (y.isInteger() && y.abs().compare(Decimal128.of(Long.MAX_VALUE)) < 0) {
			return x.pow(y.longValue());
		}
		return fn(() -> BigDecimalMath.pow(x.toBigDecimal(), y.toBigDecimal(), WORK));
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
