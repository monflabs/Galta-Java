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
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.util.NumberFormatting;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;

/**
 * BigInt prototype.
 */
public class BuiltinBigIntPrototype extends BasePrototype {

	public static BuiltinBigIntPrototype get(JSEnvironment env) {
		BuiltinBigIntPrototype proto = (BuiltinBigIntPrototype)env.getRegisteredPrototype(BuiltinBigIntPrototype.class);
		if(proto==null) {
			proto = new BuiltinBigIntPrototype(env);
			env.registerPrototype(BuiltinBigIntPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinBigIntPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinBigIntConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.toLocaleString,0));
		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.valueOf,0));
		// Note: unlike Symbol/Date, BigInt.prototype has NO [Symbol.toPrimitive] of its
		// own per spec - ToPrimitive on a boxed BigInt falls through to OrdinaryToPrimitive
		// (valueOf/toString), which is what letting this be absent achieves.
	}

	private static enum MethodId {
		toLocaleString,
		toString,
		valueOf,
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
		public Object call(final Object obj, final Object[] args) {
	    	if(!(obj instanceof BigInteger)) {
	    		throw RuntimeUtil.typeError("Method BigInt.prototype.{0} called on incompatible receiver {1}", methodId.toString(), RuntimeUtil.objectTypeName(getEnvironment(),obj));
	    	}

	    	// Current Object
			final BigInteger _this = (BigInteger)obj;			
	    	
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
	    			// Return the primitive [[BigIntData]] value. `_this` may itself still be
	    			// registered as "boxed" (e.g. called as `Object(1n).valueOf()`) - unlike a
	    			// general ToPrimitive conversion, valueOf must NOT re-dispatch through
	    			// ToPrimitive again (that would recurse back into this same method via
	    			// OrdinaryToPrimitive and stack-overflow); it must simply hand back a
	    			// fresh, non-boxed instance with the same value, exactly like
	    			// BuiltinNumberPrototype.asPrimitive() does for the other Number subtypes.
	    			PrimitivePropertyMap map = getEnvironment().getNumberProperties();
	    			if(map!=null && map.containsKey(_this)) {
	    				// NOT _this.add(BigInteger.ZERO) - the JDK short-circuits that to
	    				// `return this` unchanged, which would hand back the SAME (still
	    				// boxed) identity instead of a genuinely fresh, unboxed one.
	    				return new BigInteger(_this.toByteArray());
	    			}
	    			return _this;
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}