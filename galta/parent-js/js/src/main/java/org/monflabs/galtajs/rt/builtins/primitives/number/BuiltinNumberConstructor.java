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

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitiveConstructor;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;

/**
 * Create a String constructor.
 */
public class BuiltinNumberConstructor extends BasePrimitiveConstructor {

	public static final String CLASSNAME = "Number";

	public BuiltinNumberConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinNumberPrototype.get(env),1);

		setOwnProperty("EPSILON",Math.ulp(1.0),PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("MAX_SAFE_INTEGER",0x1FFFFFFFFFFFFFp0,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("MAX_VALUE",Double.MAX_VALUE,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("MIN_SAFE_INTEGER",-0x1FFFFFFFFFFFFFp0,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("MIN_VALUE",Double.MIN_VALUE,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("NaN",Double.NaN,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("NEGATIVE_INFINITY",Double.NEGATIVE_INFINITY,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnProperty("POSITIVE_INFINITY",Double.POSITIVE_INFINITY,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);

		setOwnMethod(new Method(env,MethodId.isFinite,1));
		setOwnMethod(new Method(env,MethodId.isInteger,1));
		setOwnMethod(new Method(env,MethodId.isNaN,1));
		setOwnMethod(new Method(env,MethodId.isSafeInteger,1));
		setOwnMethod(new Method(env,MethodId.parseFloat,1));
		setOwnMethod(new Method(env,MethodId.parseInt,2));
	}
	
	@Override
	public Class<?> getNativeClass() {
		return Number.class;
	}

	@Override
	public Number call(Object _this, Object[] parameters) {
		if(parameters.length==0) {
			switch(getEnvironment().getJsonFactory().defaultInteger()) {
				case INT -> { return 0; } 
				case LONG -> { return 0l; } 
				case BIGINT -> { return BigInteger.ZERO; } 
			}
		}
		// Number(value): unlike the strict ToNumber used in arithmetic
		// contexts (which throws for a BigInt operand), this explicitly
		// allows a BigInt argument via ToNumeric + BigInt::toNumber.
		Number n = RuntimeUtil.toNumeric(getEnvironment(),parameters[0]);
		if(n instanceof java.math.BigInteger || n instanceof java.math.BigDecimal) {
			return n.doubleValue();
		}
		return RuntimeUtil.objectAsPrimitive(getEnvironment(),n);
	}


	@Override
	public Number constructObject(Object[] parameters, Constructor topConstructor) {
		Number v = call(null,parameters);
		return applyNewTargetPrototype(RuntimeUtil.primitiveAsObject(getEnvironment(),v), topConstructor);
	}

	
	private static enum MethodId {
		isFinite,
		isInteger,
		isNaN,
		isSafeInteger,
		parseFloat,
		parseInt,
	}
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    // Type(o) is Number: not a BigInt (or GaltaJS BigDecimal), not a Number object
	    private boolean isNumberValue(Object o) {
	    	return o instanceof Number && !(o instanceof java.math.BigInteger) && !(o instanceof java.math.BigDecimal)
	    			&& !RuntimeUtil.isBoxedNumber(getEnvironment(),o);
	    }

	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case isFinite -> {
	        		Object o = param(args, 0, null);
        			// A boxed Number object (new Number(1)) has Type Object, not
        			// Number - these methods must not coerce, unlike ToNumber-based
        			// paths (e.g. String.fromCodePoint) that legitimately unbox it.
        			if(isNumberValue(o)) {
	        			if(o instanceof Double d) {
	        				return Double.isFinite(d);
	        			}
	        			if(o instanceof Float f) {
	        				return Float.isFinite(f);
	        			}
        				return true;
        			}
	        		return false;
	        	}
	        	case isInteger -> {
	        		Object o = param(args, 0, null);
	        		if(!isNumberValue(o)) {
	        			return false;
	        		}
	        		return RuntimeUtil.isIntegerNumber(o);
	        	}
	        	case isNaN -> {
	        		Object o = param(args, 0, null);
	        		if(o instanceof Number && !RuntimeUtil.isBoxedNumber(getEnvironment(),o)) {
	        			if(o instanceof Double d) {
	        				return Double.isNaN(d);
	        			} else if(o instanceof Float f) {
	        				return Float.isNaN(f);
	        			}
	        		}
	        		return false;
	        	}
	        	case isSafeInteger -> {
	        		Object o = param(args, 0, null);
	        		if(isNumberValue(o)) {
	        			double d = ((Number)o).doubleValue();
	        			if(Double.isFinite(d) && Math.rint(d) == d) {
	        	            return Math.abs(d) <= 0x1FFFFFFFFFFFFFp0;
	        			}
	        		}
	        		return false;
	        	}
	        	case parseFloat -> {
	        		Object o = param(args, 0, null);
	        		if(o!=null) {
	        			// parseFloat always calls ToString first, even on a
	        			// Number argument - e.g. ToString(-0) is "0", so
	        			// parseFloat(-0) must be +0, not -0.
		        		String s = RuntimeUtil.trimLeadingWhiteSpaces(o instanceof CharSequence ? o.toString() : RuntimeUtil.toString(getEnvironment(),o));
        				try {
        					int options = JsonFactory.PARSEINT_IGNOREEXTRACHAR;
	        				return getEnvironment().getJsonFactory().parseFloat(s,options);
        				} catch(JsonException ex) {
        					return Double.NaN;
        				}
	        		}
	        		return Double.NaN;
	        	}
	        	case parseInt -> {
	        		// A missing argument is undefined and an explicit null is "null":
	        		// both go through ToString (parseInt(null,36) is 1112745)
	        		Object o = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(o==null) {
	        			o = "null";
	        		}
	        		{
		        		// Spec: "Let R be ? ToInt32(radix)." - real ToInt32
		        		// (NaN/Infinity -> 0, modulo-2^32 wraparound for
		        		// out-of-32-bit-range values), not paramInt()'s
		        		// clamp-to-Integer.MIN/MAX_VALUE behavior.
		        		// ToString(string) comes before ToInt32(radix)
		        		String s = RuntimeUtil.trimLeadingWhiteSpaces(o instanceof CharSequence ? o.toString() : RuntimeUtil.toString(getEnvironment(),o));
		        		int base = RuntimeUtil.toInt32(getEnvironment(), param(args, 1, RuntimeUtil.UNDEFINED));
		        		if(s.length()>0) {
	        				try {
	        					int options = JsonFactory.PARSEINT_IGNOREEXTRACHAR;
	        					// this is no longer valid
	        					//if(base==0 && getEnvironment().supportParseIntOctal()) {
	        					//	options |= JsonFactory.PARSEINT_SUPPORTOCTALPREFIX;
	        					//}
	        					return getEnvironment().getJsonFactory().parseInt(s,base,options);
	        				} catch(JsonException ex) {}
		        			}
	        		}
	        		return Double.NaN;
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}
}