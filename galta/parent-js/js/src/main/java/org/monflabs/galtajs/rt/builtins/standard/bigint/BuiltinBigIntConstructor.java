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
package org.monflabs.galtajs.rt.builtins.standard.bigint;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.util.TypeUtil;

/**
 * 
 */
public class BuiltinBigIntConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "BigInt";
	
	public BuiltinBigIntConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinBigIntPrototype.get(env),1);
		setOwnMethod(new Method(env,MethodId.asIntN,2));
		setOwnMethod(new Method(env,MethodId.asUintN,2));
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BigInteger.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		throw RuntimeUtil.typeError("BigInt is not a constructor");
	}
	
	@Override
	public Object call(Object _this, Object[] parameters) {
		// BigInt(value): 1. (NewTarget check happens via constructObject().)
		// 2. Let prim be ? ToPrimitive(value, number).
		// 3. If Type(prim) is Number, return ? NumberToBigInt(prim).
		// 4. Otherwise, return ? ToBigInt(prim).
		Object v = param(parameters, 0, RuntimeUtil.UNDEFINED);
		Object prim = RuntimeUtil.toPrimitive(getEnvironment(),v,RuntimeUtil.HINT.NUMBER);
		if(prim instanceof Number n) {
			return numberToBigInt(n);
		}
		return toBigInt(getEnvironment(),prim);
	}

	// NumberToBigInt(number): throws RangeError unless number is an
	// integral (finite, no fractional part) value.
	static BigInteger numberToBigInt(Number n) {
		if(n instanceof BigInteger bi) {
			return bi;
		}
		double d = n.doubleValue();
		if(Double.isNaN(d) || Double.isInfinite(d) || Math.floor(d)!=d) {
			throw RuntimeUtil.rangeError("The number {0} cannot be converted to a BigInt because it is not an integer",n);
		}
		return TypeUtil.toBigInteger(n);
	}

	// ToBigInt(argument): argument must already be a primitive value (i.e.
	// ToPrimitive already applied by the caller where the spec requires it).
	// Public: also used by Atomics (value/expected/replacement coercion on
	// BigInt64Array/BigUint64Array).
	public static BigInteger toBigInt(JSEnvironment env, Object prim) {
		if(prim instanceof BigInteger bi) {
			return bi;
		}
		if(prim instanceof Boolean b) {
			return b ? BigInteger.ONE : BigInteger.ZERO;
		}
		if(prim instanceof CharSequence s) {
			return stringToBigInt(s.toString());
		}
		// Number, Symbol, null, undefined: ToBigInt throws TypeError (unlike
		// the BigInt() constructor, which special-cases Number via
		// NumberToBigInt before ever calling ToBigInt).
		throw RuntimeUtil.typeError("Cannot convert {0} to a BigInt",RuntimeUtil.objectTypeName(env,prim));
	}

	// StringToBigInt, throwing SyntaxError for an unparseable string (unlike
	// RuntimeUtil.stringToBigInt(), which returns null for ==/</> comparisons).
	static BigInteger stringToBigInt(String s) {
		BigInteger v = RuntimeUtil.stringToBigInt(s);
		if(v==null) {
			throw RuntimeUtil.syntaxError("Cannot convert {0} to a BigInt",s);
		}
		return v;
	}


	private static enum MethodId {
		asIntN(),
		asUintN(),
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId) {
	        
	        	case asIntN -> {
	                // BigInt.asIntN(bits, bigint): 1. bits = ToIndex(bits).
	                // 2. bigint = ToBigInt(bigint). Evaluated in this order.
	                long bits = RuntimeUtil.toIndex(getEnvironment(),param(args,0,RuntimeUtil.UNDEFINED));
	                Object prim = RuntimeUtil.toPrimitive(getEnvironment(),param(args,1,RuntimeUtil.UNDEFINED),RuntimeUtil.HINT.NUMBER);
	                BigInteger bigint = toBigInt(getEnvironment(),prim);
	                if (bits >= Integer.MAX_VALUE) {
	                    return bigint;
	                }
	                if (bits == 0) {
	                    return BigInteger.ZERO;
	                }
	                BigInteger m = BigInteger.valueOf(2).pow((int) bits);
	                BigInteger mod = bigint.mod(m);
 	                if (mod.compareTo(m.shiftRight(1)) >= 0) {
	                    return mod.subtract(m);
 	                }
	                return mod;
	        	}

	        	case asUintN -> {
	                long bits = RuntimeUtil.toIndex(getEnvironment(),param(args,0,RuntimeUtil.UNDEFINED));
	                Object prim = RuntimeUtil.toPrimitive(getEnvironment(),param(args,1,RuntimeUtil.UNDEFINED),RuntimeUtil.HINT.NUMBER);
	                BigInteger bigint = toBigInt(getEnvironment(),prim);
	                if (bits >= Integer.MAX_VALUE) {
	                    return bigint;
	                }
	                BigInteger m = BigInteger.valueOf(2).pow((int) bits);
	                return bigint.mod(m);
	        	}

	        	default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
