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
package org.monflabs.galtajs.rt.builtins.standard.math;

import java.math.BigDecimal;
import org.monflabs.galtajs.rt.util.Decimal128Ops;
import org.monflabs.util.number.Decimal128;
import org.monflabs.util.number.DecimalRoundingMode;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.external.ch_obermuhlner_math_big.BigDecimalMath;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;
import org.monflabs.util.TypeUtil;

/**
 * Math Functions.
 */
public class MathObject extends NativeObject {

	public static final String OBJECTNAME = "Math";

	private MathContext mc;

	public MathObject(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,OBJECTNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		if(env.supportBigNumberMath()) {
			this.mc = env.getMathContext();
			BigDecimal bd_e = BigDecimalMath.e(mc);
			BigDecimal bd_0_5 = new BigDecimal("0.5",mc);
			BigDecimal bd_1 = new BigDecimal("1",mc);
			BigDecimal bd_2 = new BigDecimal("2",mc);
			BigDecimal bd_10 = new BigDecimal("10",mc);
			// Decimal128 values (34 digits) when the environment uses them, BigDecimal otherwise
			boolean d128 = env.useDecimal128();
			setOwnProperty("Em",constant(d128,bd_e),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LN10m",constant(d128,BigDecimalMath.log(bd_10,mc)),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LN2m",constant(d128,BigDecimalMath.log(bd_2,mc)),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LOG10Em",constant(d128,BigDecimalMath.log10(bd_e,mc)),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LOG2Em",constant(d128,bd_1.divide(BigDecimalMath.log(bd_2,mc), mc)),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("PIm",constant(d128,BigDecimalMath.pi(mc)),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("SQRT1_2m",constant(d128,BigDecimalMath.sqrt(bd_0_5,mc)),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("SQRT2m",constant(d128,BigDecimalMath.sqrt(bd_2,mc)),PropertyDescriptor.DESC_STATICFIELDS);
		}

		if(env.forceBigDecimalOperations()) {
			setOwnProperty("E",getOwnProperty("Em"),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LN10",getOwnProperty("LN10m"),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LN2",getOwnProperty("LN2m"),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LOG10E",getOwnProperty("LOG10Em"),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LOG2E",getOwnProperty("LOG2Em"),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("PI",getOwnProperty("PIm"),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("SQRT1_2",getOwnProperty("SQRT1_2m"),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("SQRT2",getOwnProperty("SQRT2m"),PropertyDescriptor.DESC_STATICFIELDS);
		} else {
			setOwnProperty("E",Math.E,PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LN10",Math.log(10d),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LN2",Math.log(2d),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LOG10E",Math.log10(Math.E),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("LOG2E",1d / Math.log(2d),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("PI",Math.PI,PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("SQRT1_2",Math.sqrt(.5d),PropertyDescriptor.DESC_STATICFIELDS);
			setOwnProperty("SQRT2",Math.sqrt(2d),PropertyDescriptor.DESC_STATICFIELDS);
		}

		setOwnMethod(new Method(env,MethodId.abs,1));
		setOwnMethod(new Method(env,MethodId.acos,1));
		setOwnMethod(new Method(env,MethodId.acosh,1));
		setOwnMethod(new Method(env,MethodId.asin,1));
		setOwnMethod(new Method(env,MethodId.asinh,1));
		setOwnMethod(new Method(env,MethodId.atan,1));
		setOwnMethod(new Method(env,MethodId.atan2,2));
		setOwnMethod(new Method(env,MethodId.atanh,1));
		setOwnMethod(new Method(env,MethodId.cbrt,1));
		setOwnMethod(new Method(env,MethodId.ceil,1));
		setOwnMethod(new Method(env,MethodId.clz32,1));
		setOwnMethod(new Method(env,MethodId.cos,1));
		setOwnMethod(new Method(env,MethodId.cosh,1));
		setOwnMethod(new Method(env,MethodId.exp,1));
		setOwnMethod(new Method(env,MethodId.expm1,1));
		setOwnMethod(new Method(env,MethodId.f16round,1));
		setOwnMethod(new Method(env,MethodId.floor,1));
		setOwnMethod(new Method(env,MethodId.fround,1));
		setOwnMethod(new Method(env,MethodId.hypot,2));
		setOwnMethod(new Method(env,MethodId.imul,2));
		setOwnMethod(new Method(env,MethodId.log,1));
		setOwnMethod(new Method(env,MethodId.log10,1));
		setOwnMethod(new Method(env,MethodId.log1p,1));
		setOwnMethod(new Method(env,MethodId.log2,1));
		setOwnMethod(new Method(env,MethodId.max,2));
		setOwnMethod(new Method(env,MethodId.min,2));
		setOwnMethod(new Method(env,MethodId.pow,2));
		setOwnMethod(new Method(env,MethodId.random,0));
		setOwnMethod(new Method(env,MethodId.round,1));
		setOwnMethod(new Method(env,MethodId.sign,1));
		setOwnMethod(new Method(env,MethodId.sin,1));
		setOwnMethod(new Method(env,MethodId.sinh,1));
		setOwnMethod(new Method(env,MethodId.sqrt,1));
		setOwnMethod(new Method(env,MethodId.sumPrecise,1));
		setOwnMethod(new Method(env,MethodId.tan,1));
		setOwnMethod(new Method(env,MethodId.tanh,1));
		setOwnMethod(new Method(env,MethodId.trunc,1));
	}

	@Override
	public String getClassName() {
		return OBJECTNAME;
	}

	private static Number constant(boolean decimal128, BigDecimal bd) {
		return decimal128 ? Decimal128.of(bd) : bd;
	}

	private static enum MethodId {
		abs,
		acos,
		acosh,
		asin,
		asinh,
		atan,
		atan2,
		atanh,
		cbrt,
		ceil,
		clz32,
		cos,
		cosh,
		exp,
		expm1,
		f16round,
		floor,
		fround,
		hypot,
		imul,
		log,
		log10,
		log1p,
		log2,
		max,
		min,
		pow,
		random,
		round,
		sign,
		sin,
		sinh,
		sqrt,
		sumPrecise,
		tan,
		tanh,
		trunc,
	}

	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}

		private static void checkBigNumbers(JSEnvironment env) {
			if(!env.supportBigNumberMath()) {
				throw RuntimeUtil.typeError("Cannot use Math with BigNumbers");
			}
		}

		private static final Object NOT_HANDLED = new Object();
		// Beyond these a result is Infinity or 0 for any Decimal128 (and we avoid computing huge series)
		private static final Decimal128 EXP_OVERFLOW = Decimal128.of(14200), EXP_UNDERFLOW = Decimal128.of(-14400);

		/**
		 * The Math functions on Decimal128 values: computed with BigDecimalMath and rounded to 34 digits. The
		 * non finite values go through the double version, which has the right special cases.
		 */
		private Object decimal128(JSEnvironment env, Object[] args) {
			checkBigNumbers(env);
			Number a0 = RuntimeUtil.toNumber(env, args[0]);
			Decimal128 x = TypeUtil.toDecimal128(a0);
			MathContext mc = new MathContext(45, RoundingMode.HALF_EVEN);
			switch(methodId) {
				case abs: return x.abs();
				case sign: return x.isNaN() || x.isZero() ? x : (x.isNegative() ? Decimal128.ONE.negate() : Decimal128.ONE);
				case ceil: return x.isFinite() ? x.round(0, DecimalRoundingMode.CEIL) : x;
				case floor: return x.isFinite() ? x.round(0, DecimalRoundingMode.FLOOR) : x;
				case trunc: return x.isFinite() ? x.round(0, DecimalRoundingMode.TRUNC) : x;
				case round: {
					// Rounds half toward +Infinity
					if(!x.isFinite()) {
						return x;
					}
					Decimal128 r = x.round(0, DecimalRoundingMode.TRUNC);
					Decimal128 frac = x.subtract(r);
					Decimal128 half = Decimal128.of("0.5");
					if(frac.greaterThanOrEqual(half)) {
						return r.add(Decimal128.ONE);
					}
					if(frac.lessThan(half.negate())) {
						return r.subtract(Decimal128.ONE);
					}
					return r;
				}
				case atan2: case pow: case hypot: {
					if(args.length<2) {
						return Decimal128.NaN;
					}
					Number a1 = RuntimeUtil.toNumber(env, args[1]);
					if(a1 instanceof BigDecimal) {
						return NOT_HANDLED;
					}
					Decimal128 y = TypeUtil.toDecimal128(a1);
					if(methodId==MethodId.pow) {
						return Decimal128Ops.pow(x, y);
					}
					if(!x.isFinite() || !y.isFinite() || x.isZero() || y.isZero()) {
						return Decimal128.of(methodId==MethodId.atan2 ? Math.atan2(x.doubleValue(), y.doubleValue()) : Math.hypot(x.doubleValue(), y.doubleValue()));
					}
					BigDecimal bx = x.toBigDecimal(), by = y.toBigDecimal();
					if(methodId==MethodId.atan2) {
						return Decimal128Ops.fn(() -> BigDecimalMath.atan2(bx, by, mc));
					}
					return Decimal128Ops.fn(() -> BigDecimalMath.sqrt(bx.multiply(bx, mc).add(by.multiply(by, mc), mc), mc));
				}
				case acos: case acosh: case asin: case asinh: case atan: case atanh: case cbrt: case cos: case cosh:
				case exp: case expm1: case log: case log10: case log1p: case log2: case sin: case sinh: case sqrt:
				case tan: case tanh:
					return unary(x, mc);
				default:
					return NOT_HANDLED;
			}
		}

		private Object unary(Decimal128 x, MathContext mc) {
			if(x.isNaN()) {
				return x;
			}
			if(!x.isFinite()) {
				double d = x.doubleValue();
				return Decimal128.of(switch(methodId) {
					case acos -> Math.acos(d); case acosh -> acosh(d); case asin -> Math.asin(d); case asinh -> asinh(d);
					case atan -> Math.atan(d); case atanh -> atanh(d); case cbrt -> Math.cbrt(d); case cos -> Math.cos(d);
					case cosh -> Math.cosh(d); case exp -> Math.exp(d); case expm1 -> Math.expm1(d); case log -> Math.log(d);
					case log10 -> Math.log10(d); case log1p -> Math.log1p(d); case log2 -> log2(d); case sin -> Math.sin(d);
					case sinh -> Math.sinh(d); case sqrt -> Math.sqrt(d); case tan -> Math.tan(d); default -> Math.tanh(d);
				});
			}
			BigDecimal bd = x.toBigDecimal();
			boolean zero = x.isZero();
			switch(methodId) {
				// Exact or special results
				case sqrt: if(x.isNegative() && !zero) return Decimal128.NaN; if(zero) return x; break;
				case cbrt: if(zero) return x; break;
				case sin: case tan: case sinh: case tanh: case asin: case asinh: case atan: case atanh: case expm1: case log1p:
					if(zero) return x; break;
				case cos: case cosh: case exp: if(zero) return Decimal128.ONE; break;
				case log: case log10: case log2: if(zero) return Decimal128.NEGATIVE_INFINITY; break;
				default: break;
			}
			if(methodId==MethodId.log1p) {
				if(x.lessThanOrEqual(Decimal128.ONE.negate())) {
					return x.equalTo(Decimal128.ONE.negate()) ? Decimal128.NEGATIVE_INFINITY : Decimal128.NaN;
				}
			}
			if(methodId==MethodId.exp || methodId==MethodId.expm1) {
				if(x.greaterThan(EXP_OVERFLOW)) {
					return Decimal128.POSITIVE_INFINITY;
				}
				if(x.lessThan(EXP_UNDERFLOW)) {
					return methodId==MethodId.exp ? Decimal128.ZERO : Decimal128.ONE.negate();
				}
			}
			if(methodId==MethodId.cbrt) {
				return Decimal128Ops.fn(() -> {
					BigDecimal r = BigDecimalMath.root(bd.abs(), new BigDecimal(3), mc);
					return bd.signum()<0 ? r.negate() : r;
				});
			}
			return Decimal128Ops.fn(() -> switch(methodId) {
				case acos -> BigDecimalMath.acos(bd, mc);
				case acosh -> BigDecimalMath.acosh(bd, mc);
				case asin -> BigDecimalMath.asin(bd, mc);
				case asinh -> BigDecimalMath.asinh(bd, mc);
				case atan -> BigDecimalMath.atan(bd, mc);
				case atanh -> BigDecimalMath.atanh(bd, mc);
				case cos -> BigDecimalMath.cos(bd, mc);
				case cosh -> BigDecimalMath.cosh(bd, mc);
				case exp -> BigDecimalMath.exp(bd, mc);
				case expm1 -> BigDecimalMath.exp(bd, mc).subtract(BigDecimal.ONE, mc);
				case log -> BigDecimalMath.log(bd, mc);
				case log10 -> BigDecimalMath.log10(bd, mc);
				case log1p -> BigDecimalMath.log(bd.add(BigDecimal.ONE, mc), mc);
				case log2 -> BigDecimalMath.log2(bd, mc);
				case sin -> BigDecimalMath.sin(bd, mc);
				case sinh -> BigDecimalMath.sinh(bd, mc);
				case sqrt -> BigDecimalMath.sqrt(bd, mc);
				case tan -> BigDecimalMath.tan(bd, mc);
				default -> BigDecimalMath.tanh(bd, mc);
			});
		}

		private static Number toNum(JSEnvironment env, Object arg) {
			Number val = RuntimeUtil.toNumber(env, arg);
			if (env.forceBigDecimalOperations() && (val instanceof Double || val instanceof Float)) {
				return env.useDecimal128() ? TypeUtil.toDecimal128(val) : TypeUtil.toBigDecimal(val);
			}
			return val;
		}

	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	JSEnvironment env = getEnvironment();

	    	if(args.length>0 && (args[0] instanceof Decimal128 || (args.length>1 && args[1] instanceof Decimal128))) {
	    		Object r = decimal128(env, args);
	    		if(r!=NOT_HANDLED) {
	    			return r;
	    		}
	    	}

	        switch(methodId) {
            	case abs -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof Byte || val instanceof Short || val instanceof Integer) {
            				// Math.abs(Integer.MIN_VALUE) overflows back to itself
            				int i = val.intValue();
            				return i==Integer.MIN_VALUE ? (Object)(-(long)i) : (Object)Math.abs(i);
            			} else if(val instanceof Long) {
            				long l = val.longValue();
            				return l==Long.MIN_VALUE ? (Object)(-(double)l) : (Object)Math.abs(l);
            			} else if(val instanceof Float || val instanceof Double) {
            				double d = val.doubleValue();
            				if(!Double.isNaN(d)) {
            					return (d == 0.0) ? 0.0 : (d < 0.0) ? -d : d;
            				}
            			} else if(val instanceof BigInteger v) {
            				checkBigNumbers(env);
           					return v.abs();
            			} else if(val instanceof BigDecimal v) {
            				checkBigNumbers(env);
           					return v.abs();
            			}
            		}
            		return Double.NaN;
            	}
            	case acos -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);

            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
        					BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
        					return BigDecimalMath.acos(bd, env.getMathContext());
            			}

            			double d = RuntimeUtil.toDouble(getEnvironment(),val);
        				if(!Double.isNaN(d) && -1.0<=d && d<=1.0) {
        					return Math.acos(d);
        				}
            		}
            		return Double.NaN;
            	}
            	case acosh -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.acosh(bd, env.getMathContext());
            			}
        				double d = RuntimeUtil.toDouble(getEnvironment(),val);
        				if(!Double.isNaN(d)) {
        					return acosh(d);
        				}
            		}
            		return Double.NaN;
            	}
            	case asin -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.asin(bd, env.getMathContext());
            			}
        				double d = RuntimeUtil.toDouble(getEnvironment(),val);
        				if(!Double.isNaN(d) && -1.0<=d && d<=1.0) {
        					return Math.asin(d);
        				}
            		}
            		return Double.NaN;
            	}
            	case asinh -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.asinh(bd, env.getMathContext());
            			}
        				double d = RuntimeUtil.toDouble(getEnvironment(),val);
        				if(!Double.isNaN(d)) {
        					return asinh(d);
        				}
            		}
            		return Double.NaN;
            	}
            	case atan -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.atan(bd, env.getMathContext());
            			}
        				double d = RuntimeUtil.toDouble(getEnvironment(),val);
        				if(!Double.isNaN(d)) {
        					return Math.atan(d);
        				}
            		}
            		return Double.NaN;
            	}
            	case atan2 -> {
            		if(args.length>=2) {
            			Number val = toNum(env, args[0]);
            			Number val2 = toNum(env, args[1]);
            			if(val instanceof BigInteger || val instanceof BigDecimal || val2 instanceof BigInteger || val2 instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				BigDecimal bd2 = RuntimeUtil.toBigDecimal(env, val2);
            				return BigDecimalMath.atan2(bd, bd2, env.getMathContext());
            			}
            			double d = RuntimeUtil.toDouble(getEnvironment(),val);
            			double d2 = RuntimeUtil.toDouble(getEnvironment(),val2);
        				if(!Double.isNaN(d) && !Double.isNaN(d2)) {
        					return Math.atan2(d,d2);
        				}
            		}
            		return Double.NaN;
            	}
            	case atanh -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.atanh(bd, env.getMathContext());
            			}
        				double d = RuntimeUtil.toDouble(getEnvironment(),val);
        				if(!Double.isNaN(d) && -1.0<=d && d<=1.0) {
        					return atanh(d);
        				}
            		}
            		return Double.NaN;
            	}
            	case cbrt -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.root(bd, new BigDecimal("3"), env.getMathContext());
            			}
           				return Math.cbrt(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case ceil -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger v) {
            				checkBigNumbers(env);
            				return v;
            			} else if(val instanceof BigDecimal v) {
            				checkBigNumbers(env);
            				return v.setScale(0, RoundingMode.CEILING).toBigIntegerExact();
            			}
           				return Math.ceil(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case clz32 -> {
            		if(args.length>=1) {
            			Object a = args[0];
            			int i = (a instanceof BigInteger bi) ? bi.intValue()
            			      : (a instanceof BigDecimal bd) ? bd.intValue()
            			      : RuntimeUtil.toInt32(getEnvironment(), a);
           				return Integer.numberOfLeadingZeros(i);
            		}
            		return 32;
            	}
            	case cos -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.cos(bd, env.getMathContext());
            			}
           				return Math.cos(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case cosh -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.cosh(bd, env.getMathContext());
            			}
           				return Math.cosh(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case exp -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.exp(bd, env.getMathContext());
            			}
           				return Math.exp(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case expm1 -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				MathContext mc = env.getMathContext();
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.exp(bd, mc).subtract(BigDecimal.ONE, mc);
            			}
           				return Math.expm1(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case f16round -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			double d = val.doubleValue();
            			if(Double.isNaN(d)) {
            				return Double.NaN;
            			}
            			// doubleToFloat16 (not floatToFloat16) avoids double-rounding:
            			// the argument is a JS Number (double), and rounding it to a
            			// float first can give a different (wrong) result exactly at
            			// a tie between two representable float16 values.
            			return (double)BaseArrayBuffer.float16ToFloat(BaseArrayBuffer.doubleToFloat16(d));
            		}
            		return Double.NaN;
            	}
            	case floor -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger v) {
            				checkBigNumbers(env);
            				return v;
            			} else if(val instanceof BigDecimal v) {
            				checkBigNumbers(env);
            				return v.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
            			}
           				return Math.floor(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case fround -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				return fround(val.doubleValue());
            			}
                        return fround(RuntimeUtil.toDouble(getEnvironment(), val));
            		}
            		return Double.NaN;
            	}
            	case hypot -> {
            		return hypot(args);
            	}
            	case imul -> {
            		if(args.length>=2) {
            			Object a0 = args[0], a1 = args[1];
            			int d  = (a0 instanceof BigInteger bi) ? bi.intValue()
            			       : (a0 instanceof BigDecimal bd) ? bd.intValue()
            			       : RuntimeUtil.toInt32(getEnvironment(), a0);
            			int d2 = (a1 instanceof BigInteger bi) ? bi.intValue()
            			       : (a1 instanceof BigDecimal bd) ? bd.intValue()
            			       : RuntimeUtil.toInt32(getEnvironment(), a1);
                        return d*d2;
            		}
            		return Double.NaN;
            	}
            	case log -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.log(bd, env.getMathContext());
            			}
           				return Math.log(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case log10 -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.log10(bd, env.getMathContext());
            			}
           				return Math.log10(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case log1p -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				MathContext mc = env.getMathContext();
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.log(bd.add(BigDecimal.ONE, mc), mc);
            			}
           				return Math.log1p(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case log2 -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.log2(bd, env.getMathContext());
            			}
           				return log2(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
				case max -> {
					if (args.length == 0)
						return Double.NEGATIVE_INFINITY;
					Number result = toNum(env, args[0]);
					for (int i = 1; i < args.length; i++) {
						Number next = toNum(env, args[i]);
						if (RuntimeUtil.numberType(next) == RuntimeUtil.NUMBER_NAN) {
							result = next;
							continue;
						}
						int pt = RuntimeUtil.promoteNumber(env, RuntimeUtil.numberType(result),RuntimeUtil.numberType(next));
						// PROMOTE[BIGINTEGER][BIGDECIMAL]=NaN — try reverse for asymmetric pairs
						if (pt == RuntimeUtil.NUMBER_NAN || pt == RuntimeUtil.NUMBER_MIXED) {
							pt = RuntimeUtil.promoteNumber(env, RuntimeUtil.numberType(next),RuntimeUtil.numberType(result));
						}
						switch (pt) {
							case RuntimeUtil.NUMBER_INTEGER -> {
								if (next.intValue() > result.intValue())
									result = next;
							}
							case RuntimeUtil.NUMBER_LONG -> {
								if (next.longValue() > result.longValue())
									result = next;
							}
							case RuntimeUtil.NUMBER_DOUBLE -> {
								// Math.max also handles to support -0.0 & +0.0
								result = Math.max(TypeUtil.toDouble(next), TypeUtil.toDouble(result));
							}
							case RuntimeUtil.NUMBER_BIGINTEGER -> {
								checkBigNumbers(env);
								if (TypeUtil.toBigInteger(next).compareTo(TypeUtil.toBigInteger(result)) > 0)
									result = next;
							}
							case RuntimeUtil.NUMBER_BIGDECIMAL -> {
								checkBigNumbers(env);
								if (TypeUtil.toBigDecimal(next).compareTo(TypeUtil.toBigDecimal(result)) > 0)
									result = next;
							}
							case RuntimeUtil.NUMBER_DECIMAL128 -> {
								checkBigNumbers(env);
								Decimal128 dn = TypeUtil.toDecimal128(next), dr = TypeUtil.toDecimal128(result);
								if (dr.isNaN()) {
									result = dr;
								} else if (dn.isNaN()) {
									result = dn;
								} else {
									// The total order has -0 < +0
									result = dn.compareTo(dr) > 0 ? dn : dr;
								}
							}
							default -> {
							}
						}
					}
					return result;
				}
				case min -> {
					if (args.length == 0)
						return Double.POSITIVE_INFINITY;
					Number result = toNum(env, args[0]);
					for (int i = 1; i < args.length; i++) {
						Number next = toNum(env, args[i]);
						if (RuntimeUtil.numberType(next) == RuntimeUtil.NUMBER_NAN) {
							result = next;
							continue;
						}
						int pt = RuntimeUtil.promoteNumber(env, RuntimeUtil.numberType(result),RuntimeUtil.numberType(next));
						// PROMOTE[BIGINTEGER][BIGDECIMAL]=NaN — try reverse for asymmetric pairs
						if (pt == RuntimeUtil.NUMBER_NAN || pt == RuntimeUtil.NUMBER_MIXED) {
							pt = RuntimeUtil.promoteNumber(env, RuntimeUtil.numberType(next),RuntimeUtil.numberType(result));
						}
						switch (pt) {
							case RuntimeUtil.NUMBER_INTEGER -> {
									if (next.intValue() < result.intValue())
										result = next;
								}
							case RuntimeUtil.NUMBER_LONG -> {
								if (next.longValue() < result.longValue())
									result = next;
							}
							case RuntimeUtil.NUMBER_DOUBLE -> {
								// Should use Math.min to support -0.0 & +0.0
								result = Math.min(TypeUtil.toDouble(next), TypeUtil.toDouble(result));
							}
							case RuntimeUtil.NUMBER_BIGINTEGER -> {
								checkBigNumbers(env);
								if (TypeUtil.toBigInteger(next).compareTo(TypeUtil.toBigInteger(result)) < 0)
									result = next;
							}
							case RuntimeUtil.NUMBER_BIGDECIMAL -> {
								checkBigNumbers(env);
								if (TypeUtil.toBigDecimal(next).compareTo(TypeUtil.toBigDecimal(result)) < 0)
									result = next;
							}
							case RuntimeUtil.NUMBER_DECIMAL128 -> {
								checkBigNumbers(env);
								Decimal128 dn = TypeUtil.toDecimal128(next), dr = TypeUtil.toDecimal128(result);
								if (dr.isNaN()) {
									result = dr;
								} else if (dn.isNaN()) {
									result = dn;
								} else {
									// The total order has -0 < +0
									result = dn.compareTo(dr) < 0 ? dn : dr;
								}
							}
							default -> {
							}
						}
					}
					return result;
				}
            	case pow -> {
            		if(args.length>=2) {
            			Number val = toNum(env, args[0]);
            			Number val2 = toNum(env, args[1]);
            			if(val instanceof BigInteger || val instanceof BigDecimal || val2 instanceof BigInteger || val2 instanceof BigDecimal) {
            				checkBigNumbers(env);
            				if(val instanceof BigInteger base && val2 instanceof BigInteger exp && exp.signum() >= 0) {
            					try {
            						return base.pow(exp.intValueExact());
            					} catch(ArithmeticException ignored) {}
            				}
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				BigDecimal bd2 = RuntimeUtil.toBigDecimal(env, val2);
            				return BigDecimalMath.pow(bd, bd2, env.getMathContext());
            			}
                        return Math.pow(RuntimeUtil.toDouble(getEnvironment(),val), RuntimeUtil.toDouble(getEnvironment(),val2));
            		}
            		return Double.NaN;
            	}
            	case random -> {
                    return Math.random();
            	}
            	case round -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger v) {
            				checkBigNumbers(env);
            				return v;
            			} else if(val instanceof BigDecimal v) {
            				checkBigNumbers(env);
            				// JS round: floor(x + 0.5) — rounds half toward +infinity
            				return v.add(new BigDecimal("0.5")).setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
            			}
            			double d = RuntimeUtil.toDouble(getEnvironment(),val);
                        if (d != d || d == 0 || Double.isInfinite(d)) {
                            return d;
                        }
                        if (d > 0 && d < 0.5) {
                            return +0.0;
                        }
                        if (d < 0 && d >= -0.5) {
                            return -0.0;
                        }
                        int exp = Math.getExponent(d);
                        if (exp >= 52) {
                            return d;
                        }
                        return Math.floor(d + 0.5);
            		}
            		return Double.NaN;
            	}
            	case sign -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger v) {
            				checkBigNumbers(env);
            				return v.signum();
            			} else if(val instanceof BigDecimal v) {
            				checkBigNumbers(env);
            				return v.signum();
            			}
           				return Math.signum(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case sin -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.sin(bd, env.getMathContext());
            			}
           				return Math.sin(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case sinh -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.sinh(bd, env.getMathContext());
            			}
           				return Math.sinh(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case sqrt -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.sqrt(bd, env.getMathContext());
            			}
           				return Math.sqrt(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case sumPrecise -> {
            		return sumPrecise(env, param(args, 0, RuntimeUtil.UNDEFINED));
            	}
            	case tan -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.tan(bd, env.getMathContext());
            			}
           				return Math.tan(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case tanh -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger || val instanceof BigDecimal) {
            				checkBigNumbers(env);
            				BigDecimal bd = RuntimeUtil.toBigDecimal(env, val);
            				return BigDecimalMath.tanh(bd, env.getMathContext());
            			}
           				return Math.tanh(RuntimeUtil.toDouble(getEnvironment(),val));
            		}
            		return Double.NaN;
            	}
            	case trunc -> {
            		if(args.length>=1) {
            			Number val = toNum(env, args[0]);
            			if(val instanceof BigInteger v) {
            				checkBigNumbers(env);
            				return v;
            			} else if(val instanceof BigDecimal v) {
            				checkBigNumbers(env);
            				return v.setScale(0, RoundingMode.DOWN).toBigIntegerExact();
            			}
            			double d = RuntimeUtil.toDouble(getEnvironment(),val);
            			return d < 0 ? Math.ceil(d) : Math.floor(d);
            		}
            		return Double.NaN;
            	}

	            default -> {
    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }

    // Hyperbolic inverses. The plain log formulas are kept where they are
    // accurate (they match V8's results there); the edge ranges are handled
    // separately: a tiny argument returns itself (the formula loses it
    // entirely), a huge one uses log(x)+ln2 (x*x would overflow), and asinh
    // is computed on |x| (log(x+sqrt(x*x+1)) cancels for a large negative x).
    private static final double TWO_POW_28 = 268435456.0;
    private static final double TWO_POW_M28 = 1.0/268435456.0;
    private double asinh(double d) {
        if (d == 0.0 || !Double.isFinite(d)) {
            return d;
        }
        double a = Math.abs(d);
        double r;
        if (a < TWO_POW_M28) {
            return d;
        } else if (a > TWO_POW_28) {
            r = Math.log(a) + LN2;
        } else {
            r = Math.log(a + Math.sqrt(a*a + 1.0));
        }
        return d < 0 ? -r : r;
    }
    private double acosh(double d) {
        if (Double.isNaN(d) || d < 1.0) {
            return Double.NaN;
        }
        if (d == 1) {
            return +0.0;
        }
        if (d == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        if (d > TWO_POW_28) {
            return Math.log(d) + LN2;
        }
        return Math.log(d + Math.sqrt(d * d - 1.0));
    }
    private double atanh(double d) {
        if (Double.isNaN(d) || d < -1.0 || d > 1.0) {
            return Double.NaN;
        }
        if (d == -1.0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (d == +1.0) {
            return Double.POSITIVE_INFINITY;
        }
        if (d == 0.0 || Math.abs(d) < TWO_POW_M28) {
            return d;
        }
        return (Math.log(1.0 + d) - Math.log(1.0 - d)) / 2.0;
    }
    // Math.sumPrecise: a maximally-precise (correctly-rounded) sum of an
    // iterable of Numbers. Rather than a hand-rolled Neumaier/Shewchuk
    // compensated-summation algorithm, each finite value is converted to an
    // EXACT BigDecimal (new BigDecimal(double) has no rounding error), summed
    // exactly, then rounded back to the nearest double via doubleValue() -
    // which is itself correctly-rounded, so the overall result matches the
    // spec's intent (and BigDecimal.doubleValue() already returns +-Infinity
    // on overflow, matching the spec's own overflow-to-infinity behavior).
    private Object sumPrecise(JSEnvironment env, Object iterable) {
    	Iterator<Object> it = RuntimeUtil.valueIterator(env, iterable);
    	List<BigDecimal> exact = new ArrayList<>();
    	boolean sawAny = false;
    	boolean allNegativeZero = true;
    	boolean sawNaN = false, sawPosInf = false, sawNegInf = false;
    	try {
    		while(it.hasNext()) {
    			Object v = it.next();
    			if(!(v instanceof Number) || v instanceof BigInteger || v instanceof BigDecimal) {
    				throw RuntimeUtil.typeError("Math.sumPrecise argument is not a Number, {0}", RuntimeUtil.objectTypeName(env,v));
    			}
    			sawAny = true;
    			double d = ((Number)v).doubleValue();
    			if(Double.isNaN(d)) {
    				sawNaN = true;
    			} else if(d==Double.POSITIVE_INFINITY) {
    				sawPosInf = true;
    			} else if(d==Double.NEGATIVE_INFINITY) {
    				sawNegInf = true;
    			} else {
    				if(d!=0.0 || !RuntimeUtil.isNegativeZero(d)) {
    					allNegativeZero = false;
    				}
    				if(d!=0.0) {
    					exact.add(new BigDecimal(d));
    				}
    			}
    		}
    	} catch(Throwable t) {
    		RuntimeUtil.iteratorCloseQuietly(env, it);
    		throw t;
    	}
    	if(sawNaN || (sawPosInf && sawNegInf)) {
    		return Double.NaN;
    	}
    	if(sawPosInf) {
    		return Double.POSITIVE_INFINITY;
    	}
    	if(sawNegInf) {
    		return Double.NEGATIVE_INFINITY;
    	}
    	if(!sawAny || allNegativeZero) {
    		return -0.0;
    	}
    	BigDecimal sum = BigDecimal.ZERO;
    	for(BigDecimal bd : exact) {
    		sum = sum.add(bd);
    	}
    	double result = sum.doubleValue();
    	return result==0.0 ? 0.0 : result;
    }
    private double fround(double d) {
        if (Double.isNaN(d)) {
            return Double.NaN;
        }
        if (d == 0 || Double.isInfinite(d)) {
            return d;
        }
        return (double) (float) d;
    }
    private double hypot(Object... args) {
    	switch(args.length) {
	    	case 0 -> {
	            return +0.0;
	    	}
	    	case 1 -> {
				double d = RuntimeUtil.toDouble(getEnvironment(),args[0]);
	            return Math.abs(d);
	    	}
	    	case 2 -> {
				double d = RuntimeUtil.toDouble(getEnvironment(),args[0]);
				double d2 = RuntimeUtil.toDouble(getEnvironment(),args[1]);
	            return Math.hypot(d, d2);
	    	}
	    	default -> {
	            boolean hasInfinity = false, hasNaN = false;
	            double max = 0;
	            double[] numbers = new double[args.length];
	            for (int i = 0, len = args.length; i < len; ++i) {
	                double v = RuntimeUtil.toDouble(getEnvironment(),args[i]);
	                if (Double.isInfinite(v)) {
	                    hasInfinity = true;
	                } else if (Double.isNaN(v)) {
	                    hasNaN = true;
	                } else {
	                    v = Math.abs(v);
	                    max = Math.max(max, v);
	                    numbers[i] = v;
	                }
	            }
	            if (hasInfinity) {
	                return Double.POSITIVE_INFINITY;
	            } else if (hasNaN) {
	                return Double.NaN;
	            } else if (max == 0.0) {
	                return +0.0;
	            }
	            double result = 0.0, c = 0.0;
	            for (int i = 0, len = numbers.length; i < len; ++i) {
	                double v = numbers[i] / max;
	                double y = v * v - c;
	                double t = result + y;
	                c = (t - result) - y;
	                result = t;
	            }
	            return Math.sqrt(result) * max;
	    	}
    	}
    }

    private static final double LN2 = Math.log(2);
    private static double log2(double d) {
        if (Double.isNaN(d)) {
            return Double.NaN;
        }
        // Exact for powers of two: log(2^29)/LN2 is 29.000000000000004
        if (d > 0 && Double.isFinite(d)) {
            int e = Math.getExponent(d);
            if (e >= Double.MIN_EXPONENT && d == Math.scalb(1.0, e)) {
                return e;
            }
            if (e < Double.MIN_EXPONENT) {
                // Subnormal: normalize first
                double n = d*0x1p54;
                int en = Math.getExponent(n);
                if (n == Math.scalb(1.0, en)) {
                    return en - 54;
                }
            }
        }
        return Math.log(d) / LN2;
    }
	} // end Method
}
