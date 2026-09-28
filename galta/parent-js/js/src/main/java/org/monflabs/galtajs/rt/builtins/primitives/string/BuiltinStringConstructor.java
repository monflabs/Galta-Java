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
package org.monflabs.galtajs.rt.builtins.primitives.string;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitiveConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
	
/**
 * Create a String constructor.
 */
public class BuiltinStringConstructor extends BasePrimitiveConstructor {

	public static final String CLASSNAME = "String";

	public BuiltinStringConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinStringPrototype.get(env),1);
		
		setOwnMethod(new Method(env,MethodId.fromCharCode,1));
		setOwnMethod(new Method(env,MethodId.fromCodePoint,1));
		setOwnMethod(new Method(env,MethodId.raw,1));
	}
	
	@Override
	public Class<?> getNativeClass() {
		return String.class;
	}

	@Override
	public String call(Object _this, Object[] parameters) {
		if(parameters.length==0) {
			return RuntimeUtil.toString(getEnvironment(),"");
		}
		// Special case for symbols.
		Object p = parameters[0];
		if(p instanceof Symbol sy && !RuntimeUtil.isBoxedSymbol(getEnvironment(), sy)) {
			return sy.toString();
		}
		return RuntimeUtil.toString(getEnvironment(),p);
	}

	@Override
	public String constructObject(Object[] parameters, Constructor topConstructor) {
		if(parameters.length==0) {
			return applyNewTargetPrototype(RuntimeUtil.primitiveAsObject(getEnvironment(),""), topConstructor);
		}
		Object p = toPrimitive(parameters[0]);
		String v = RuntimeUtil.objectAsPrimitive(getEnvironment(),RuntimeUtil.toString(getEnvironment(),p));
		return applyNewTargetPrototype(RuntimeUtil.primitiveAsObject(getEnvironment(),v), topConstructor);
	}
	
	private Object toPrimitive(Object v) {
		if(!RuntimeUtil.isPrimitiveType(v)) {
			// `new String(value)`'s [[Value]] is ToString(value), which
			// internally calls ToPrimitive with hint "string" - trying
			// toString() BEFORE valueOf() - not the default hint (which
			// tries valueOf() first). Confirmed via S15.5.2.1_A1_T12.js: an
			// object whose toString() throws and whose valueOf() succeeds
			// must still propagate the toString() exception, not silently
			// use valueOf()'s result instead.
			v = RuntimeUtil.toPrimitive(getEnvironment(), v, RuntimeUtil.HINT.STRING);
		}
		return v;
	}
	
	private static enum MethodId {
		fromCharCode,
		fromCodePoint,
		raw,
	}
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case fromCharCode -> {
	        		if(args.length==0) {
	        			return "";
	        		}
	        		if(args.length==1) {
	        			return String.valueOf((char)RuntimeUtil.toUInt16(getEnvironment(),args[0]));
	        		}
	        		char[] cc = new char[args.length];
	        		for(int i=0; i<cc.length; i++) {
	        			cc[i] = (char)RuntimeUtil.toUInt16(getEnvironment(),args[i]);
	        		}
            		return new String(cc);
	        	}
	        	case fromCodePoint -> {
	        		if(args.length==0) {
	        			return "";
	        		}
	        		int length = args.length;
	                int elements[] = new int[length];
	                for (int i=0; i<length; i++) {
	                	// ToNumber(next) must always be called - even for a
	                	// non-Number argument (a poisoned valueOf(), or a
	                	// legitimate numeric string) - not skipped in favor of
	                	// an immediate RangeError.
	                	Number n = RuntimeUtil.toNumber(getEnvironment(), args[i]);
	                	double d = n.doubleValue();
	                	if(Double.isNaN(d) || Double.isInfinite(d) || d!=Math.floor(d)) {
	                        throw RuntimeUtil.rangeError("Invalid code point {0}", args[i]);
	                	}
	                	int cp = elements[i] = (int)d;
	                    if (cp < 0 || cp > 0x10FFFF) {
	                        throw RuntimeUtil.rangeError("Invalid code point {0}", args[i]);
	                    }
	                }
	                return new String(elements, 0, length);
	        	}
	        	case raw -> {
	        		Object strings = RuntimeUtil.toObject(getEnvironment(),param(args, 0));
	        		JSAccessor acc = getEnvironment().getAccessor(strings);
	        		Object raw = acc.getProperty(strings, "raw", RuntimeUtil.NOT_AVAILABLE);
	        		if(raw!=RuntimeUtil.NOT_AVAILABLE) {
	        			JSArray a = RuntimeUtil.getArrayLike(getEnvironment(),raw);
	        			long len = a.arrayLength();
	        			// `int i` compared against a `long` length silently
	        			// overflows to negative around 2^31 instead of
	        			// terminating - an effectively infinite loop for a
	        			// huge "raw" length rather than a quick failure
	        			// (confirmed via js-test-rhino's
	        			// es6/string.js TestRawTooLarge, a REPRODUCED hang:
	        			// `String.raw({raw:{length: 2**31+1}})` must throw
	        			// RangeError immediately, matching real engines'
	        			// refusal to attempt building a string from an
	        			// absurdly large segment count rather than actually
	        			// looping). Guard + widen the loop variable.
	        			if(len>Integer.MAX_VALUE) {
	        				throw RuntimeUtil.rangeError("Invalid array length {0}",len);
	        			}
	        			StringBuilder b = new StringBuilder();
	        			for(long i=0; i<len; i++) {
	        				String s = RuntimeUtil.toString(getEnvironment(),a.getProperty(i));
	        				b.append(s);
	        				// A substitution is only appended for all but the
	        				// LAST raw segment - per spec, the loop stops
	        				// (without consulting any further substitution)
	        				// the moment nextIndex+1 equals literalSegments,
	        				// even if more substitution arguments were
	        				// actually passed (confirmed via
	        				// substitutions-are-limited-to-template-raw-length.js:
	        				// a 3rd substitution whose ToString() throws must
	        				// never be reached at all when raw.length is 3).
	        				if(i+1<len && i+1<args.length) {
	        					String s2 = RuntimeUtil.toString(getEnvironment(),args[(int)(i+1)]);
	        					b.append(s2);
	        				}
	        			}
	        			return b.toString();
	        		} else {
	        			throw RuntimeUtil.typeError("Invalid function call - missing raw array");
	        		}
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	

}