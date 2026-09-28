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
package org.monflabs.galtajs.rt.builtins.primitives.number;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitivePrototype;
import org.monflabs.galtajs.rt.util.NumberFormatting;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;

/**
 * Eqv of the JavaScript Number prototype.
 */
public class BuiltinNumberPrototype extends BasePrimitivePrototype {

	public static BuiltinNumberPrototype get(JSEnvironment env) {
		BuiltinNumberPrototype proto = (BuiltinNumberPrototype)env.getRegisteredPrototype(BuiltinNumberPrototype.class);
		if(proto==null) {
			proto = new BuiltinNumberPrototype(env);
			env.registerPrototype(BuiltinNumberPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinNumberPrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId.toExponential,1));
		setOwnMethod(new Method(env,MethodId.toFixed,1));
		setOwnMethod(new Method(env,MethodId.toLocaleString,0));
		setOwnMethod(new Method(env,MethodId.toPrecision,1));
		setOwnMethod(new Method(env,MethodId.toString,1));
		setOwnMethod(new Method(env,MethodId.valueOf,0));
	}
	
	@Override
	public String getClassName() {
		return BuiltinNumberConstructor.CLASSNAME;
	}
	
	@Override
	public Class<?> getNativeClass() {
		return Number.class;
	}
	
	private static enum MethodId {
		toExponential,
		toFixed,
		toLocaleString,
		toPrecision,
		toString,
		valueOf,
	}
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	    	if(RuntimeUtil.isNullOrUndefined(obj)) {
	    		throw nullThis();
	    	}
	    	// %Number.prototype% itself has internal [[NumberData]] = +0
	    	// (spec 21.1.3), so calling these methods directly on it behaves
	    	// exactly like calling them on the primitive 0.
	    	final Number _this;
	    	if(obj instanceof Number n) {
	    		_this = n;
	    	} else if(obj instanceof BuiltinNumberPrototype) {
	    		_this = 0;
	    	} else {
	    		throw RuntimeUtil.typeError("Method Number.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}
	    	
	    	switch(methodId) {
	        	case toExponential -> {
	        		// undefined fractionDigits → no-arg sentinel (spec §21.1.3.3 step 3)
	        		return NumberFormatting.toExponential(_this, paramInt(args, 0, Integer.MIN_VALUE));
	        	}
	        	case toFixed -> {
	        		int prec = paramInt(args, 0, 0);
	        		return NumberFormatting.toFixed(_this,prec);
	        	}
	        	case toLocaleString -> {
	        		return NumberFormatting.numberToString(_this,10);
	        	}
	        	case toPrecision -> {
	        		if(args.length>=1) {
		        		int prec = paramInt(args,0,Integer.MIN_VALUE);
		        		return NumberFormatting.toPrecision(_this,prec);
	        		}
	        		return NumberFormatting.toPrecision(_this,Integer.MIN_VALUE);
	        	}
	        	case toString -> {
	        		int radix = paramInt(args, 0, 10);
	        		if (radix < 2 || radix > 36) {
	                     throw RuntimeUtil.rangeError("Invalid radix {0}", radix);
	                }
	        		return NumberFormatting.numberToString(_this,radix);
	        	}
	        	case valueOf -> {
        			return asPrimitive(getEnvironment(), _this);
	        	}

	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
	public static Number asPrimitive(JSEnvironment env, Number v) {
		PrimitivePropertyMap map = env.getNumberProperties();
		if(map!=null) {
			if(map.containsKey(v)) {
				if(v instanceof Integer n) {
					v = Integer.valueOf(n.intValue());
				} else if(v instanceof Long n) {
					v = Long.valueOf(n.longValue());
				} else if(v instanceof Double n) {
					v = Double.valueOf(n.doubleValue());
				} else if(v instanceof BigInteger n) {
					v = new BigInteger(n.toByteArray());
				} else if(v instanceof BigDecimal n) {
					v = new BigDecimal(n.unscaledValue(), n.scale(),env.getMathContext());
				} else if(v instanceof Byte n) {
					v = Byte.valueOf(n.byteValue());
				} else if(v instanceof Short n) {
					v = Short.valueOf(n.shortValue());
				} else if(v instanceof Float n) {
					v = Float.valueOf(n.floatValue());
				}
			}
		}
		return v;
	}
}