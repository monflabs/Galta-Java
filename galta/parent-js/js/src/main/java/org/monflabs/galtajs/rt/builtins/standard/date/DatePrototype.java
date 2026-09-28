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
package org.monflabs.galtajs.rt.builtins.standard.date;

import java.util.Date;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Date prototype.
 */
public class DatePrototype extends BasePrototype {

	public static DatePrototype get(JSEnvironment env) {
		DatePrototype proto = (DatePrototype)env.getRegisteredPrototype(DatePrototype.class);
		if(proto==null) {
			proto = new DatePrototype(env);
			env.registerPrototype(DatePrototype.class,proto);
		}
		return proto;
	}

	private DatePrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId.getDate,0));
		setOwnMethod(new Method(env,MethodId.getDay,0));
		setOwnMethod(new Method(env,MethodId.getFullYear,0));
		setOwnMethod(new Method(env,MethodId.getHours,0));
		setOwnMethod(new Method(env,MethodId.getMilliseconds,0));
		setOwnMethod(new Method(env,MethodId.getMinutes,0));
		setOwnMethod(new Method(env,MethodId.getMonth,0));
		setOwnMethod(new Method(env,MethodId.getSeconds,0));
		setOwnMethod(new Method(env,MethodId.getTime,0));
		setOwnMethod(new Method(env,MethodId.getTimezoneOffset,0));
		setOwnMethod(new Method(env,MethodId.getUTCDate,0));
		setOwnMethod(new Method(env,MethodId.getUTCDay,0));
		setOwnMethod(new Method(env,MethodId.getUTCFullYear,0));
		setOwnMethod(new Method(env,MethodId.getUTCHours,0));
		setOwnMethod(new Method(env,MethodId.getUTCMilliseconds,0));
		setOwnMethod(new Method(env,MethodId.getUTCMinutes,0));
		setOwnMethod(new Method(env,MethodId.getUTCMonth,0));
		setOwnMethod(new Method(env,MethodId.getUTCSeconds,0));
		setOwnMethod(new Method(env,MethodId.getYear,0));
		
		setOwnMethod(new Method(env,MethodId.setDate,1));
		setOwnMethod(new Method(env,MethodId.setFullYear,3));
		setOwnMethod(new Method(env,MethodId.setHours,4));
		setOwnMethod(new Method(env,MethodId.setMilliseconds,1));
		setOwnMethod(new Method(env,MethodId.setMinutes,3));
		setOwnMethod(new Method(env,MethodId.setMonth,2));
		setOwnMethod(new Method(env,MethodId.setSeconds,2));
		setOwnMethod(new Method(env,MethodId.setTime,1));
		setOwnMethod(new Method(env,MethodId.setUTCDate,1));
		setOwnMethod(new Method(env,MethodId.setUTCFullYear,3));
		setOwnMethod(new Method(env,MethodId.setUTCHours,4));
		setOwnMethod(new Method(env,MethodId.setUTCMilliseconds,1));
		setOwnMethod(new Method(env,MethodId.setUTCMinutes,3));
		setOwnMethod(new Method(env,MethodId.setUTCMonth,2));
		setOwnMethod(new Method(env,MethodId.setUTCSeconds,2));
		setOwnMethod(new Method(env,MethodId.setYear,1));
		
		setOwnMethod(new Method(env,MethodId.toDateString,0));
		setOwnMethod(new Method(env,MethodId.toISOString,0));
		setOwnMethod(new Method(env,MethodId.toJSON,1));
		setOwnMethod(new Method(env,MethodId.toLocaleDateString,0));
		setOwnMethod(new Method(env,MethodId.toLocaleString,0));
		setOwnMethod(new Method(env,MethodId.toLocaleTimeString,0));
		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.toTimeString,0));
		setOwnMethod(new Method(env,MethodId.toUTCString,0));
		setOwnMethod(new Method(env,MethodId.valueOf,0));
		
		// Deprecated - B.2.4.3: toGMTString is the EXACT SAME function
		// object as toUTCString, not a separately-implemented duplicate
		// (confirmed via toGMTString/value.js: `Date.prototype.toGMTString
		// === Date.prototype.toUTCString`).
		setOwnAlias(MethodId.toUTCString.id,MethodId.toGMTString.id);
		
		// Symbol
		setOwnMethod(new Method(env,MethodId.toPrimitive,1), PropertyDescriptor.DESC_PROP_TOPRIMITIVE);
	}
	
	private static enum MethodId {
		getDate,
		getDay,
		getFullYear,
		getHours,
		getMilliseconds,
		getMinutes,
		getMonth,
		getSeconds,
		getTime,
		getTimezoneOffset,
		getUTCDate,
		getUTCDay,
		getUTCFullYear,
		getUTCHours,
		getUTCMilliseconds,
		getUTCMinutes,
		getUTCMonth,
		getUTCSeconds,
		getYear,

		setDate,
		setFullYear,
		setHours,
		setMilliseconds,
		setMinutes,
		setMonth,
		setSeconds,
		setTime,
		setUTCDate,
		setUTCFullYear,
		setUTCHours,
		setUTCMilliseconds,
		setUTCMinutes,
		setUTCMonth,
		setUTCSeconds,
		setYear,
		toDateString,
		toISOString,
		toJSON,
		toLocaleDateString,
		toLocaleString,
		toLocaleTimeString,
		toString,
		toTimeString,
		toUTCString,
		valueOf,

		toGMTString,
		
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
		public Object call(final Object obj, final Object[] args) {
	    	// Date.prototype[Symbol.toPrimitive] is the ONE method here that
	    	// doesn't require a [[DateValue]] internal slot - it's OrdinaryToPrimitive
	    	// applied to whatever object it's called on (spec: "If Type(O) is
	    	// not Object, throw a TypeError" - no [[DateValue]] check at all).
	    	if(methodId==MethodId.toPrimitive) {
	    		JSEnvironment env = getEnvironment();
	    		if(!RuntimeUtil.isObject(env, obj)) {
	    			throw RuntimeUtil.typeError("Date.prototype[Symbol.toPrimitive] called on a non-object");
	    		}
	    		Object hintArg = param(args, 0, RuntimeUtil.UNDEFINED);
	    		// The hint must be a genuine primitive string VALUE, not merely
	    		// something CharSequence-shaped - a boxed String object (Type
	    		// Object per spec) must be rejected too.
	    		if(!(hintArg instanceof CharSequence) || RuntimeUtil.isBoxedString(env, hintArg)) {
	    			throw RuntimeUtil.typeError("Invalid hint: {0}", hintArg);
	    		}
	    		String hint = hintArg.toString();
	    		if(hint.equals(RuntimeUtil.HINT_STRING) || hint.equals(RuntimeUtil.HINT_DEFAULT)) {
	    			return RuntimeUtil.ordinaryToPrimitive(env, obj, RuntimeUtil.HINT.STRING);
	    		}
	    		if(hint.equals(RuntimeUtil.HINT_NUMBER)) {
	    			return RuntimeUtil.ordinaryToPrimitive(env, obj, RuntimeUtil.HINT.NUMBER);
	    		}
	    		throw RuntimeUtil.typeError("Invalid hint: {0}", hint);
	    	}

	    	// Date.prototype.toJSON is ALSO generic (works on ANY object, not
	    	// just genuine Date instances) - spec: ToObject(this), then
	    	// ToPrimitive(O, Number) to check for non-finite, then Invoke(O,
	    	// "toISOString") - whatever toISOString property O actually has,
	    	// not necessarily this class's own toISOString.
	    	if(methodId==MethodId.toJSON) {
	    		JSEnvironment env = getEnvironment();
	    		Object o = RuntimeUtil.toObject(env, obj);
	    		Object tv = RuntimeUtil.toPrimitive(env, o, RuntimeUtil.HINT.NUMBER);
	    		if(tv instanceof Number n && !Double.isFinite(n.doubleValue())) {
	    			return null;
	    		}
	    		Object m = env.getAccessor(o).getProperty(o, "toISOString", RuntimeUtil.UNDEFINED);
	    		if(!(m instanceof org.monflabs.galtajs.rt.builtins.Callable c)) {
	    			throw RuntimeUtil.typeError("toISOString is not a function");
	    		}
	    		return c.call(o, RuntimeUtil.EMPTY_PARAMS);
	    	}

	    	if(!(obj instanceof Date)) {
	    		// %Date.prototype% itself (e.g. Date.prototype.toString()) has no
	    		// [[DateValue]] slot either - thisTimeValue must throw for it too,
	    		// same as any other non-Date receiver.
	    		throw RuntimeUtil.typeError("Method Date.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			final Date _this = (Date)obj;			
	    	
	    	switch(methodId){
	    		case getDate-> {
	    			return DateUtil.getDate(_this);
	    		}
	    		case getDay-> {
	    			return DateUtil.getDay(_this);
	    		}
	    		case getFullYear-> {
	    			return DateUtil.getFullYear(_this);
	    		}
	    		case getHours-> {
	    			return DateUtil.getHours(_this);
	    		}
	    		case getMilliseconds-> {
	    			return DateUtil.getMilliseconds(_this);
	    		}
	    		case getMinutes-> {
	    			return DateUtil.getMinutes(_this);
	    		}
	    		case getMonth-> {
	    			return DateUtil.getMonth(_this);
	    		}
	    		case getSeconds-> {
	    			return DateUtil.getSeconds(_this);
	    		}
	    		case getTime-> {
	    			return DateUtil.getTime(_this);
	    		}
	    		case getTimezoneOffset-> {
	    			return DateUtil.getTimezoneOffset(_this);
	    		}
	    		case getUTCDate-> {
	    			return DateUtil.getUTCDate(_this);
	    		}
	    		case getUTCDay-> {
	    			return DateUtil.getUTCDay(_this);
	    		}
	    		case getUTCFullYear-> {
	    			return DateUtil.getUTCFullYear(_this);
	    		}
	    		case getUTCHours-> {
	    			return DateUtil.getUTCHours(_this);
	    		}
	    		case getUTCMilliseconds-> {
	    			return DateUtil.getUTCMilliseconds(_this);
	    		}
	    		case getUTCMinutes-> {
	    			return DateUtil.getUTCMinutes(_this);
	    		}
	    		case getUTCMonth-> {
	    			return DateUtil.getUTCMonth(_this);
	    		}
	    		case getUTCSeconds-> {
	    			return DateUtil.getUTCSeconds(_this);
	    		}
	    		case getYear-> {
	    			return DateUtil.getYear(_this);
	    		}
	    		
	    		case setDate-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                        double date = paramDouble(args,0);
                        return DateUtil.setDate(_this,t,date);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setFullYear-> { // (year,month,date)
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double year = paramDouble(args,0);
                        if(has(args,1)) {
                        	double month = paramDouble(args,1);
                            if(has(args,2)) {
                            	double date = paramDouble(args,2);
                            	return DateUtil.setFullYear(_this,t,year,month,date);
                            }
                            return DateUtil.setFullYear(_this,t,year,month);
                        }
                    	return DateUtil.setFullYear(_this,t,year);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setHours-> { // (hour,min,sec,ms)
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double hour = paramDouble(args,0);
                        if(has(args,1)) {
                        	double min = paramDouble(args,1);
                            if(has(args,2)) {
                            	double sec = paramDouble(args,2);
                                if(has(args,3)) {
                                	double ms = paramDouble(args,3);
                                	return DateUtil.setHours(_this,t,hour,min,sec,ms);
                                }
                            	return DateUtil.setHours(_this,t,hour,min,sec);
                            }
                        	return DateUtil.setHours(_this,t,hour,min);
                        }
                    	return DateUtil.setHours(_this,t,hour);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setMilliseconds-> { // (ms)
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double ms = paramDouble(args,0);
                        return DateUtil.setMilliseconds(_this,t,ms);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setMinutes-> { // (min,sec,ms)
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double min = paramDouble(args,0);
                        if(has(args,1)) {
                        	double sec = paramDouble(args,1);
                            if(has(args,2)) {
                            	double ms = paramDouble(args,2);
                            	return DateUtil.setMinutes(_this,t,min,sec,ms);
                            }
                        	return DateUtil.setMinutes(_this,t,min,sec);
                        }
                    	return DateUtil.setMinutes(_this,t,min);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setMonth-> { // (month,date)
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double month = paramDouble(args,0);
                        if(has(args,1)) {
                        	double date = paramDouble(args,1);
                        	return DateUtil.setMonth(_this,t,month,date);
                        }
                        return DateUtil.setMonth(_this,t,month);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setSeconds-> { // (sec,ms)
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double sec = paramDouble(args,0);
                        if(has(args,1)) {
                        	double ms = paramDouble(args,1);
                        	return DateUtil.setSeconds(_this,t,sec,ms);
                        }
                    	return DateUtil.setSeconds(_this,t,sec);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setTime-> {
                    if(args.length>=1) {
                        double time = paramDouble(args,0);
                        return DateUtil.setTime(_this,time);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setUTCDate-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double date = paramDouble(args,0);
                        return DateUtil.setUTCDate(_this,t,date);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setUTCFullYear-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double year = paramDouble(args,0);
                        if(has(args,1)) {
                        	double month = paramDouble(args,1);
                            if(has(args,2)) {
                            	double date = paramDouble(args,2);
                            	return DateUtil.setUTCFullYear(_this,t,year,month,date);
                            }
                            return DateUtil.setUTCFullYear(_this,t,year,month);
                        }
                    	return DateUtil.setUTCFullYear(_this,t,year);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setUTCHours-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double hour = paramDouble(args,0);
                        if(has(args,1)) {
                        	double min = paramDouble(args,1);
                            if(has(args,2)) {
                            	double sec = paramDouble(args,2);
                                if(has(args,3)) {
                                	double ms = paramDouble(args,3);
                                	return DateUtil.setUTCHours(_this,t,hour,min,sec,ms);
                                }
                            	return DateUtil.setUTCHours(_this,t,hour,min,sec);
                            }
                        	return DateUtil.setUTCHours(_this,t,hour,min);
                        }
                    	return DateUtil.setUTCHours(_this,t,hour);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setUTCMilliseconds-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double ms = paramDouble(args,0);
                        return DateUtil.setUTCMilliseconds(_this,t,ms);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setUTCMinutes-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double min = paramDouble(args,0);
                        if(has(args,1)) {
                        	double sec = paramDouble(args,1);
                            if(has(args,2)) {
                            	double ms = paramDouble(args,2);
                            	return DateUtil.setUTCMinutes(_this,t,min,sec,ms);
                            }
                        	return DateUtil.setUTCMinutes(_this,t,min,sec);
                        }
                    	return DateUtil.setUTCMinutes(_this,t,min);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setUTCMonth-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double month = paramDouble(args,0);
                        if(has(args,1)) {
                        	double date = paramDouble(args,1);
                        	return DateUtil.setUTCMonth(_this,t,month,date);
                        }
                        return DateUtil.setUTCMonth(_this,t,month);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setUTCSeconds-> {
                    if(args.length>=1) {
                    	double t = DateUtil.dateToDouble(_this);
                    	double sec = paramDouble(args,0);
                        if(has(args,1)) {
                        	double ms = paramDouble(args,1);
                        	return DateUtil.setUTCSeconds(_this,t,sec,ms);
                        }
                    	return DateUtil.setUTCSeconds(_this,t,sec);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}
	    		case setYear-> {
                    if(args.length>=1) {
                    	// [[DateValue]] must be read BEFORE ToNumber(year) -
                    	// see DateUtil.setYear()'s own "rawT" parameter comment.
                    	double t = DateUtil.dateToDouble(_this);
                    	double year = paramDouble(args,0);
                        return DateUtil.setYear(_this,t,year);
                    }
                    return DateUtil.setTime(_this,Double.NaN);
	    		}

	    		case toDateString-> {
	    			return DateUtil.toDateString(_this);
	    		}
	    		case toISOString-> {
	    			return DateUtil.toISOString(_this);
	    		}
	    		case toLocaleDateString-> {
                    if(args.length>=1) {
                        String loc = paramString(args, 0);
    	    			return DateUtil.toLocaleDateString(_this,loc);
                    }
	    			return DateUtil.toLocaleDateString(_this,null);
	    		}
	    		case toLocaleString-> {
                    if(args.length>=1) {
                        String loc = paramString(args, 0);
    	    			return DateUtil.toLocaleString(_this,loc);
                    }
	    			return DateUtil.toLocaleString(_this,null);
	    		}
	    		case toLocaleTimeString-> {
                    if(args.length>=1) {
                        String loc = paramString(args, 0);
    	    			return DateUtil.toLocaleTimeString(_this,loc);
                    }
	    			return DateUtil.toLocaleTimeString(_this,null);
	    		}
	    		case toString-> {
        			return DateUtil.toString(_this);
	    		}
	    		case toTimeString-> {
        			return DateUtil.toTimeString(_this);
	    		}
	    		case toUTCString-> {
        			return DateUtil.toUTCString(_this);
	    		}
	    		case valueOf-> {
        			return DateUtil.valueOf(_this);
	    		}

	    		//
	    		// Symbol
	    		//
	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}