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
package org.monflabs.galtajs.rt.builtins.standard.decimal128;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;
import org.monflabs.util.number.Decimal128;
import org.monflabs.util.number.DecimalRoundingMode;

/**
 * Decimal128 prototype, following the methods of the Decimal proposal.
 */
public class BuiltinDecimal128Prototype extends BasePrototype {

	public static BuiltinDecimal128Prototype get(JSEnvironment env) {
		BuiltinDecimal128Prototype proto = (BuiltinDecimal128Prototype)env.getRegisteredPrototype(BuiltinDecimal128Prototype.class);
		if(proto==null) {
			proto = new BuiltinDecimal128Prototype(env);
			env.registerPrototype(BuiltinDecimal128Prototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinDecimal128Prototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinDecimal128Constructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		for(MethodId id : MethodId.values()) {
			if(id.id instanceof String) {
				setOwnMethod(new Method(env,id,id.length));
			}
		}
		// Symbol
		setOwnMethod(new Method(env,MethodId.toPrimitive,1), PropertyDescriptor.DESC_PROP_TOPRIMITIVE);
	}
	
	private static enum MethodId {
		isNaN(0), isFinite(0), exponent(0), mantissa(0),
		abs(0), negate(0),
		add(1), subtract(1), multiply(1), divide(1), remainder(1),
		compare(1), equals(1), notEquals(1), lessThan(1), lessThanOrEqual(1), greaterThan(1), greaterThanOrEqual(1),
		round(1), scale10(1),
		toString(0), toLocaleString(0), toExponential(0), toFixed(0), toPrecision(0), toJSON(0), valueOf(0),

		toPrimitive(Symbol.TO_PRIMITIVE),
		;
		Object id;
		int length;
		MethodId(int length) {
			this.id = name();
			this.length = length;
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}
		
		private Decimal128 operand(Object[] args) {
			return BuiltinDecimal128Constructor.convert(RuntimeUtil.toPrimitive(getEnvironment(),param(args, 0, RuntimeUtil.UNDEFINED),RuntimeUtil.HINT.NUMBER));
		}
		
		private DecimalRoundingMode mode(Object[] args, int pos) {
			Object m = param(args, pos, RuntimeUtil.UNDEFINED);
			if(m==RuntimeUtil.UNDEFINED) {
				return DecimalRoundingMode.DEFAULT;
			}
			try {
				return DecimalRoundingMode.fromName(RuntimeUtil.toString(getEnvironment(),m));
			} catch(IllegalArgumentException e) {
				throw RuntimeUtil.rangeError("Invalid rounding mode {0}", m);
			}
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	if(!(obj instanceof Decimal128 _this)) {
	    		throw RuntimeUtil.typeError("Method Decimal128.prototype.{0} called on incompatible receiver {1}", methodId.toString(), RuntimeUtil.objectTypeName(getEnvironment(),obj));
	    	}

	    	switch(methodId) {
	    		case isNaN -> { return _this.isNaN(); }
	    		case isFinite -> { return _this.isFinite(); }
	    		case exponent -> { return _this.exponent(); }
	    		case mantissa -> { return _this.mantissa(); }
	    		case abs -> { return _this.abs(); }
	    		case negate -> { return _this.negate(); }
	    		case add -> { return _this.add(operand(args)); }
	    		case subtract -> { return _this.subtract(operand(args)); }
	    		case multiply -> { return _this.multiply(operand(args)); }
	    		case divide -> { return _this.divide(operand(args)); }
	    		case remainder -> { return _this.remainder(operand(args)); }
	    		case compare -> {
	    			int c = _this.compare(operand(args));
	    			return c==Decimal128.UNORDERED ? (Object)Double.NaN : (Object)c;
	    		}
	    		case equals -> { return _this.equalTo(operand(args)); }
	    		case notEquals -> { return _this.notEquals(operand(args)); }
	    		case lessThan -> { return _this.lessThan(operand(args)); }
	    		case lessThanOrEqual -> { return _this.lessThanOrEqual(operand(args)); }
	    		case greaterThan -> { return _this.greaterThan(operand(args)); }
	    		case greaterThanOrEqual -> { return _this.greaterThanOrEqual(operand(args)); }
	    		case round -> {
	    			int digits = paramInt(args, 0, 0);
	    			return _this.round(digits, mode(args,1));
	    		}
	    		case scale10 -> {
	    			try {
	    				return _this.scale10(paramDouble(args, 0, Double.NaN));
	    			} catch(IllegalArgumentException e) {
	    				throw RuntimeUtil.rangeError("{0}", e.getMessage());
	    			}
	    		}
	    		case toString, toLocaleString, toJSON -> { return _this.toString(); }
	    		case toExponential -> { return _this.toExponential(); }
	    		case toFixed -> {
	    			return args.length>0 && args[0]!=RuntimeUtil.UNDEFINED ? _this.toFixed(rangeInt(args)) : _this.toFixed();
	    		}
	    		case toPrecision -> {
	    			return args.length>0 && args[0]!=RuntimeUtil.UNDEFINED ? _this.toPrecision(rangeInt(args)) : _this.toPrecision();
	    		}
	    		case valueOf, toPrimitive -> { return unboxedPrimitive(_this); }

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	    
	    private int rangeInt(Object[] args) {
	    	int v = paramInt(args, 0, 0);
	    	if(v<0 || v>100) {
	    		throw RuntimeUtil.rangeError("Invalid digits {0}", v);
	    	}
	    	return v;
	    }

	    // valueOf/toPrimitive must return the primitive [[value]], not re-dispatch through
	    // ToPrimitive again (see BuiltinBigDecimalPrototype): hand back a fresh, non-boxed instance
	    // with the same value when this one is registered as boxed.
	    private Object unboxedPrimitive(Decimal128 _this) {
	    	PrimitivePropertyMap map = getEnvironment().getNumberProperties();
	    	if(map!=null && map.containsKey(_this)) {
	    		return _this.copy();
	    	}
	    	return _this;
	    }
	}
}
