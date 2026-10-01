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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.util.NumberFormatting;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;

/**
 * BigDecimal prototype.
 */
public class BuiltinBigDecimalPrototype extends BasePrototype {

	public static BuiltinBigDecimalPrototype get(JSEnvironment env) {
		BuiltinBigDecimalPrototype proto = (BuiltinBigDecimalPrototype)env.getRegisteredPrototype(BuiltinBigDecimalPrototype.class);
		if(proto==null) {
			proto = new BuiltinBigDecimalPrototype(env);
			env.registerPrototype(BuiltinBigDecimalPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinBigDecimalPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinBigDecimalConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.toLocaleString,0));
		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.valueOf,0));

		// Symbol
		setOwnMethod(new Method(env,MethodId.toPrimitive,1), PropertyDescriptor.DESC_PROP_TOPRIMITIVE);
	}
	
	private static enum MethodId {
		toLocaleString,
		toString,
		valueOf,

		toPrimitive(Symbol.TO_PRIMITIVE),
		;
		Object id;
		MethodId() {
			this.id = name();
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
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	if(!(obj instanceof BigDecimal)) {
	    		throw RuntimeUtil.typeError("Method Decimal.prototype.{0} called on incompatible receiver {1}", methodId.toString(), RuntimeUtil.objectTypeName(getEnvironment(),obj));
	    	}

	    	// Current Object
			final BigDecimal _this = (BigDecimal)obj;			
	    	
	    	switch(methodId) {
	    	
	    		case toLocaleString-> { // No local options for now
	        		return NumberFormatting.numberToString(_this,10);
	    		}
	    		case toString -> {
	        		int radix = paramInt(args, 0, 10);
	        		if (radix < 2 || radix > 36) {
	                     throw RuntimeUtil.rangeError("Invalid radix {0}", radix);
	                }
	        		return NumberFormatting.numberToString(_this,radix);
	    		}
	    		case valueOf-> {
	    			return unboxedPrimitive(_this);
	    		}

	    		case toPrimitive-> {
	    			return unboxedPrimitive(_this);
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }

	    // valueOf/toPrimitive must return the primitive [[value]], not re-dispatch through
	    // ToPrimitive again - `_this` may still be registered as "boxed" (e.g. called as
	    // `Object(1.0m).valueOf()`), and calling RuntimeUtil.objectAsPrimitive() on it would
	    // recurse straight back into this same method via OrdinaryToPrimitive/[Symbol.toPrimitive]
	    // and stack-overflow. Hand back a fresh, non-boxed instance with the same value instead,
	    // matching BuiltinNumberPrototype.asPrimitive()'s pattern for the other Number subtypes.
	    private Object unboxedPrimitive(BigDecimal _this) {
	    	PrimitivePropertyMap map = getEnvironment().getNumberProperties();
	    	if(map!=null && map.containsKey(_this)) {
	    		return new BigDecimal(_this.unscaledValue(), _this.scale(), getEnvironment().getMathContext());
	    	}
	    	return _this;
	    }
	}
}