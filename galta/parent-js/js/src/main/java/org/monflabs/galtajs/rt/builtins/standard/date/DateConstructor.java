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
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * 
 */
public class DateConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Date";
	
	public DateConstructor(JSEnvironment env) {
		super(env,CLASSNAME,DatePrototype.get(env),7);
		
		setOwnMethod(new Method(env,MethodId.now,0));
		setOwnMethod(new Method(env,MethodId.parse,1));
		setOwnMethod(new Method(env,MethodId.UTC,7));

		setOwnProperty(Symbol.SPECIES, this);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return Date.class;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		return DateUtil.toString(new Date());
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		if(parameters.length==0) {
			return applyNewTargetPrototype(new Date(), topConstructor);
		}

		if(parameters.length==1) {
			JSEnvironment env = getEnvironment();
			Object v = parameters[0];
			if(v instanceof Date d) {
				return applyNewTargetPrototype(new Date(d.getTime()), topConstructor);
			}
			// Any other value (including a plain object without a [[DateValue]]
			// slot) goes through ToPrimitive(value) with the DEFAULT hint -
			// not ToNumber directly, which would use the NUMBER hint instead
			// (observable via a custom Symbol.toPrimitive: it must be called
			// with "default", not "number").
			Object prim = RuntimeUtil.toPrimitive(env, v, RuntimeUtil.HINT.DEFAULT);
			if(prim instanceof CharSequence s) {
				return applyNewTargetPrototype(new Date(DateUtil.doubleToLong(DateParser.parseDate(s.toString()))), topConstructor);
			}
			Number n = RuntimeUtil.toNumber(env,prim);
			// spec: tv = TimeClip(ToNumber(value)) - values outside the
			// +-8.64e15 range must become NaN (an invalid Date), not silently
			// wrap/truncate via a raw long cast.
			return applyNewTargetPrototype(new Date(DateUtil.doubleToLong(DateUtil.timeClip(n.doubleValue()))), topConstructor);
		}

        double year = RuntimeUtil.toDouble(getEnvironment(),parameters[0]);
        double month = RuntimeUtil.toDouble(getEnvironment(),parameters[1]);
        double date = parameters.length>=3 ? RuntimeUtil.toDouble(getEnvironment(),parameters[2]) : 1;
        double hours = parameters.length>=4 ? RuntimeUtil.toDouble(getEnvironment(),parameters[3]) : 0;
        double minutes = parameters.length>=5 ? RuntimeUtil.toDouble(getEnvironment(),parameters[4]) : 0;
        double seconds = parameters.length>=6 ? RuntimeUtil.toDouble(getEnvironment(),parameters[5]) : 0;
        double ms = parameters.length>=7 ? RuntimeUtil.toDouble(getEnvironment(),parameters[6]) : 0;
        return applyNewTargetPrototype(new Date(DateUtil.doubleToLong(DateUtil.date(year,month,date,hours,minutes,seconds,ms))), topConstructor);
	}
	
	private static enum MethodId {
		now,
		parse,
		UTC,
	}
	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case now -> {
	        		return System.currentTimeMillis();
	        	}
	        	case parse -> {
                    if(args.length>=1) {
                        String s = paramString( args, 0);
                        return DateParser.parseDate(s);
                    }
                    return RuntimeUtil.UNDEFINED;
	        	}
	        	case UTC -> { // (year, monthIndex, day, hour, minute, second, millisecond)
                    if(args.length>=1) {
                    	double year = paramDouble(args,0);
                        if(has(args,1)) {
                        	double month = paramDouble(args,1);
                            if(has(args,2)) {
                            	double date = paramDouble(args,2);
                                if(has(args,3)) {
                                	double hour = paramDouble(args,3);
                                    if(has(args,4)) {
                                    	double minute = paramDouble(args,4);
                                        if(has(args,5)) {
                                        	double second = paramDouble(args,5);
                                            if(has(args,6)) {
                                            	double ms = paramDouble(args,6);
                                            	return DateUtil.UTC(year,month,date,hour,minute,second,ms);
                                            }
                                        	return DateUtil.UTC(year,month,date,hour,minute,second);
                                        }
                                    	return DateUtil.UTC(year,month,date,hour,minute);
                                    }
                                	return DateUtil.UTC(year,month,date,hour);
                                }
                            	return DateUtil.UTC(year,month,date);
                            }
                            return DateUtil.UTC(year,month);
                        }
                    	return DateUtil.UTC(year);
                    }
                    // year is a required argument: ToNumber(undefined) is NaN.
                    return Double.NaN;
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
