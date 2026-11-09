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
package org.monflabs.galtajs.rt.builtins.standard.bigdecimal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

import org.monflabs.galtajs.external.ch_obermuhlner_math_big.BigDecimalMath;
import org.monflabs.util.TypeUtil;
import org.monflabs.util.number.Decimal128;

/**
 * Decimal arithmetic following IEEE 754-2019 Decimal128, as used by the TC39
 * Decimal proposal (https://github.com/tc39/proposal-decimal).
 *
 * <p>A finite, non negative-zero Decimal is a {@link BigDecimal}. A
 * <code>BigDecimal</code> has no NaN, infinities or negative zero, so these
 * values are represented by the matching Number (<code>Double</code>) values:
 * <code>NaN</code>, <code>Infinity</code>, <code>-Infinity</code> and <code>-0</code>.
 * Every operation here accepts such a Number as an operand, and returns one
 * when the result is special:
 * <ul>
 * <li>division by zero gives a signed infinity, <code>0/0</code> gives NaN,</li>
 * <li>a zero result gets its IEEE 754 sign (<code>-1m * 0m</code> is <code>-0</code>),</li>
 * <li>a result beyond the format's exponent range overflows to a signed infinity,
 *     and one below the smallest subnormal underflows to a signed zero.</li>
 * </ul>
 *
 * <p>A {@link Decimal128} operand (when mixed with a BigDecimal, the BigDecimal
 * semantics win) is taken as its BigDecimal value, or as the matching Number when
 * it is NaN, an infinity or -0.
 *
 * <p>The exponent range is the one of the IEEE format matching the precision of the
 * {@link MathContext} (DECIMAL32, DECIMAL64, DECIMAL128); any other precision is
 * treated as unbounded.
 */
public final class DecimalArithmetic {

	private DecimalArithmetic() {
	}

	private static final Double NEGATIVE_ZERO = -0.0;


	//
	// Special values
	//

	/**
	 * True when the number is a Number value that a BigDecimal cannot represent:
	 * NaN, an infinity or -0.
	 */
	public static boolean isSpecial(Number n) {
		if(n instanceof Decimal128 d) {
			return !d.isFinite() || (d.isZero() && d.isNegative());
		}
		if(n instanceof Double || n instanceof Float) {
			double d = n.doubleValue();
			return !Double.isFinite(d) || (d==0 && isNegative(d));
		}
		return false;
	}
	/** A Decimal128 as a BigDecimal, or as the matching Number for NaN, the infinities and -0. */
	private static Number norm(Number n) {
		if(n instanceof Decimal128 d) {
			return isSpecial(d) ? (Number)d.doubleValue() : d.toBigDecimal();
		}
		return n;
	}
	private static boolean isNaN(Number n) {
		return (n instanceof Double || n instanceof Float) && Double.isNaN(n.doubleValue());
	}
	private static boolean isInfinite(Number n) {
		return (n instanceof Double || n instanceof Float) && Double.isInfinite(n.doubleValue());
	}
	private static boolean isNegative(double d) {
		return Double.doubleToRawLongBits(d) < 0;
	}
	/** The IEEE sign bit: true for negative values, -0 and -Infinity. */
	private static boolean signBit(Number n) {
		if(n instanceof Double || n instanceof Float) {
			return isNegative(n.doubleValue());
		}
		return dec(n).signum()<0;
	}
	/** The finite value (-0 is 0). Must not be called on NaN or an infinity. */
	private static BigDecimal dec(Number n) {
		if(n instanceof BigDecimal bd) {
			return bd;
		}
		if((n instanceof Double || n instanceof Float) && n.doubleValue()==0) {
			return BigDecimal.ZERO;
		}
		return TypeUtil.toBigDecimal(n);
	}
	private static Double infinity(boolean negative) {
		return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
	}
	private static Number zero(boolean negative) {
		return negative ? NEGATIVE_ZERO : BigDecimal.ZERO;
	}

	/**
	 * Converts a Number to a Decimal value: a BigDecimal, or the Number itself
	 * when it is NaN, an infinity or -0.
	 */
	public static Number toDecimal(Number n, MathContext mc) {
		n = norm(n);
		if(isSpecial(n)) {
			return n instanceof Float ? (Number)n.doubleValue() : n;
		}
		if(n instanceof BigDecimal bd) {
			return finish(bd, false, mc);
		}
		return finish(dec(n).round(mc), false, mc);
	}

	/**
	 * Parses a Decimal from a string: a decimal number, or NaN, [+-]Infinity, -0.
	 * @throws NumberFormatException when the string is not a number
	 */
	public static Number parse(String s, MathContext mc) {
		String t = s.strip();
		switch(t) {
			case "NaN": return Double.NaN;
			case "Infinity", "+Infinity": return Double.POSITIVE_INFINITY;
			case "-Infinity": return Double.NEGATIVE_INFINITY;
		}
		BigDecimal bd = new BigDecimal(t, mc);
		return finish(bd, bd.signum()==0 && t.startsWith("-"), mc);
	}


	//
	// Exponent range
	//

	/** The largest adjusted exponent of the IEEE format, or 0 when unbounded. */
	private static int emax(MathContext mc) {
		return switch(mc.getPrecision()) {
			case 7 -> 96;
			case 16 -> 384;
			case 34 -> 6144;
			default -> 0;
		};
	}

	/**
	 * Applies the IEEE rules to a rounded BigDecimal result: a zero gets the
	 * given sign, an overflow becomes an infinity, and a value below the
	 * smallest subnormal is rounded to the subnormal range (possibly to zero).
	 * @param negative the sign of the exact result, used when it is (or rounds to) zero
	 */
	public static Number finish(BigDecimal r, boolean negative, MathContext mc) {
		if(r.signum()==0) {
			return zero(negative);
		}
		int emax = emax(mc);
		if(emax>0) {
			long adjusted = (long)r.precision() - r.scale() - 1;
			if(adjusted>emax) {
				return infinity(r.signum()<0);
			}
			int etiny = 1 - emax - (mc.getPrecision()-1);
			if(-(long)r.scale() < etiny) {
				BigDecimal sub = r.setScale(-etiny, mc.getRoundingMode());
				if(sub.signum()==0) {
					return zero(r.signum()<0);
				}
				return sub;
			}
		}
		return r;
	}


	//
	// Arithmetic
	//

	public static Number add(Number a, Number b, MathContext mc) {
		a = norm(a);
		b = norm(b);
		if(isNaN(a) || isNaN(b)) {
			return Double.NaN;
		}
		boolean ia = isInfinite(a), ib = isInfinite(b);
		if(ia || ib) {
			if(ia && ib && signBit(a)!=signBit(b)) {
				return Double.NaN;
			}
			return infinity(signBit(ia ? a : b));
		}
		BigDecimal x = dec(a), y = dec(b);
		BigDecimal r = x.add(y, mc);
		// An exact zero sum is +0, unless both operands are -0
		return finish(r, r.signum()==0 ? signBit(a) && signBit(b) : r.signum()<0, mc);
	}

	public static Number subtract(Number a, Number b, MathContext mc) {
		return add(a, negate(b), mc);
	}

	public static Number multiply(Number a, Number b, MathContext mc) {
		a = norm(a);
		b = norm(b);
		if(isNaN(a) || isNaN(b)) {
			return Double.NaN;
		}
		boolean negative = signBit(a)!=signBit(b);
		boolean ia = isInfinite(a), ib = isInfinite(b);
		if(ia || ib) {
			if((!ia && dec(a).signum()==0) || (!ib && dec(b).signum()==0)) {
				return Double.NaN;
			}
			return infinity(negative);
		}
		return finish(dec(a).multiply(dec(b), mc), negative, mc);
	}

	public static Number divide(Number a, Number b, MathContext mc) {
		a = norm(a);
		b = norm(b);
		if(isNaN(a) || isNaN(b)) {
			return Double.NaN;
		}
		boolean negative = signBit(a)!=signBit(b);
		boolean ia = isInfinite(a), ib = isInfinite(b);
		if(ia) {
			return ib ? Double.NaN : infinity(negative);
		}
		if(ib) {
			return zero(negative);
		}
		BigDecimal x = dec(a), y = dec(b);
		if(y.signum()==0) {
			return x.signum()==0 ? Double.NaN : infinity(negative);
		}
		return finish(x.divide(y, mc), negative, mc);
	}

	/**
	 * The truncating remainder, like the JavaScript % operator: the result has
	 * the sign of the dividend.
	 */
	public static Number remainder(Number a, Number b, MathContext mc) {
		a = norm(a);
		b = norm(b);
		if(isNaN(a) || isNaN(b) || isInfinite(a)) {
			return Double.NaN;
		}
		if(isInfinite(b)) {
			return toDecimal(a, mc);
		}
		BigDecimal x = dec(a), y = dec(b);
		if(y.signum()==0) {
			return Double.NaN;
		}
		if(x.signum()==0) {
			return zero(signBit(a));
		}
		// The remainder is computed exactly: BigDecimal.remainder(y,mc) fails when
		// the integer quotient has more digits than the precision
		return finish(x.remainder(y).round(mc), signBit(a), mc);
	}

	public static Number negate(Number a) {
		a = norm(a);
		if(a instanceof Double || a instanceof Float) {
			return -a.doubleValue();
		}
		BigDecimal x = dec(a);
		return x.signum()==0 ? NEGATIVE_ZERO : x.negate();
	}

	public static Number abs(Number a) {
		a = norm(a);
		if(a instanceof Double || a instanceof Float) {
			return Math.abs(a.doubleValue());
		}
		return dec(a).abs();
	}

	public static Number pow(Number a, Number b, MathContext mc) {
		a = norm(a);
		b = norm(b);
		if(isSpecial(a) || isSpecial(b)) {
			// With a special operand, every result is exact: 0, 1, an infinity or NaN
			return fromDouble(Math.pow(a.doubleValue(), b.doubleValue()), mc);
		}
		BigDecimal x = dec(a), y = dec(b);
		if(y.signum()==0) {
			return BigDecimal.ONE;
		}
		boolean oddInteger = isOddInteger(y);
		if(x.signum()==0) {
			// x is +0
			return y.signum()<0 ? (Number)Double.POSITIVE_INFINITY : BigDecimal.ZERO;
		}
		if(x.signum()<0 && !isInteger(y)) {
			return Double.NaN;
		}
		boolean negative = x.signum()<0 && oddInteger;
		BigDecimal ax = x.abs();
		if(ax.compareTo(BigDecimal.ONE)==0) {
			return negative ? BigDecimal.ONE.negate() : BigDecimal.ONE;
		}
		// Out of range results are detected up front, as computing them would take
		// a very long time: log10|x^y| = y*log10|x|
		int emax = emax(mc);
		if(emax>0) {
			long adjusted = (long)ax.precision() - ax.scale() - 1;
			double log10 = adjusted + Math.log10(ax.scaleByPowerOfTen((int)-adjusted).doubleValue());
			double magnitude = y.doubleValue()*log10;
			if(magnitude > emax+2) {
				return infinity(negative);
			}
			if(magnitude < 1-emax-(mc.getPrecision()-1)-2) {
				return zero(negative);
			}
		}
		try {
			return finish(BigDecimalMath.pow(x, y, mc), negative, mc);
		} catch(ArithmeticException ex) {
			return fromDouble(Math.pow(x.doubleValue(), y.doubleValue()), mc);
		}
	}
	private static boolean isInteger(BigDecimal y) {
		return y.signum()==0 || y.scale()<=0 || y.stripTrailingZeros().scale()<=0;
	}
	private static boolean isOddInteger(BigDecimal y) {
		if(!isInteger(y)) {
			return false;
		}
		BigInteger i = y.toBigIntegerExact();
		return i.testBit(0);
	}


	//
	// Math functions
	//

	/** Converts a double result (typically a special value) to a Decimal value. */
	public static Number fromDouble(double d, MathContext mc) {
		if(!Double.isFinite(d) || d==0) {
			return d==0 && !isNegative(d) ? BigDecimal.ZERO : d;
		}
		return toDecimal(d, mc);
	}

	/**
	 * Computes a Math function on a finite Decimal. The BigDecimal implementation
	 * throws an ArithmeticException outside of its domain (log(0), sqrt(-1),
	 * acos(2)...): the IEEE result (NaN, an infinity...) is then the one of the
	 * double function.
	 * @param odd true for an odd function (f(-x)==-f(x)), whose result has the
	 *        sign of the argument when it is zero
	 */
	public static Number math(BigDecimal x, Supplier<BigDecimal> f, DoubleUnaryOperator d, boolean odd, MathContext mc) {
		try {
			return finish(f.get(), odd && x.signum()<0, mc);
		} catch(ArithmeticException ex) {
			return fromDouble(d.applyAsDouble(x.doubleValue()), mc);
		}
	}

	/** Math.exp(), guarded against arguments whose result is out of range. */
	public static Number exp(BigDecimal x, MathContext mc) {
		int emax = emax(mc);
		if(emax>0) {
			// ln(10)*(emax+precision) bounds both the overflow and the underflow
			double limit = 2.31*(emax+mc.getPrecision()+2);
			if(x.doubleValue()>limit) {
				return Double.POSITIVE_INFINITY;
			}
			if(x.doubleValue()<-limit) {
				return BigDecimal.ZERO;
			}
		}
		return math(x, () -> BigDecimalMath.exp(x, mc), Math::exp, false, mc);
	}

	/** Rounds to an integral Decimal: Math.floor/ceil/trunc/round. */
	public static Number toIntegral(BigDecimal x, java.math.RoundingMode mode) {
		if(x.scale()<=0) {
			return x;
		}
		BigDecimal r;
		if(mode==null) {
			// Math.round: halfway cases round toward +Infinity
			r = x.add(new BigDecimal("0.5")).setScale(0, java.math.RoundingMode.FLOOR);
		} else {
			r = x.setScale(0, mode);
		}
		return r.signum()==0 ? zero(x.signum()<0) : r;
	}

	/** Math.max of two values, one being a Decimal: NaN wins, and +0 is larger than -0. */
	public static Number max(Number a, Number b) {
		a = norm(a);
		b = norm(b);
		Integer c = compare(a, b);
		if(c==null) {
			return Double.NaN;
		}
		if(c==0) {
			return signBit(a) ? b : a;
		}
		return c>0 ? a : b;
	}

	/** Math.min of two values, one being a Decimal: NaN wins, and -0 is smaller than +0. */
	public static Number min(Number a, Number b) {
		a = norm(a);
		b = norm(b);
		Integer c = compare(a, b);
		if(c==null) {
			return Double.NaN;
		}
		if(c==0) {
			return signBit(a) ? a : b;
		}
		return c<0 ? a : b;
	}

	/**
	 * Compares a Decimal to a Number that may be special. Returns null when
	 * one is NaN (unordered).
	 */
	public static Integer compare(Number a, Number b) {
		a = norm(a);
		b = norm(b);
		if(isNaN(a) || isNaN(b)) {
			return null;
		}
		boolean ia = isInfinite(a), ib = isInfinite(b);
		if(ia || ib) {
			double da = ia ? a.doubleValue() : 0, db = ib ? b.doubleValue() : 0;
			if(ia && ib) {
				return Double.compare(da, db);
			}
			return ia ? (da>0 ? 1 : -1) : (db>0 ? -1 : 1);
		}
		return dec(a).compareTo(dec(b));
	}
}
