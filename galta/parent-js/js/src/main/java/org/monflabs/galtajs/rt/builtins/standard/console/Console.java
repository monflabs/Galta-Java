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
package org.monflabs.galtajs.rt.builtins.standard.console;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.StringFormat;

/**
 * console object.
 */
public class Console extends NativeObject {

	// Should these be per environment??
	private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> timers = new ConcurrentHashMap<>();

	public static final String OBJECTNAME = "console";

	public Console(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,OBJECTNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.assert_,"assert",0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.clear,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.count,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.countReset,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.debug,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.dir,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.dirxml,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.error,0),PropertyDescriptor.DESC_DEFAULT);
		// Alias for error() but removed
		//setOwnMethod(new Method(env,MethodId.exception,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.group,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.groupCollapsed,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.groupEnd,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.info,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.log,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.profile,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.profileEnd,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.table,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.time,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.timeEnd,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.timeEnd,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.timeLog,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.timeStamp,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.trace,0),PropertyDescriptor.DESC_DEFAULT);
		setOwnMethod(new Method(env,MethodId.warn,0),PropertyDescriptor.DESC_DEFAULT);
		
		setOwnMethod(new Method(env,MethodId.context,1),PropertyDescriptor.DESC_DEFAULT);
	}
	
	@Override
	public String getClassName() {
		return OBJECTNAME;
	}
	
	private static enum MethodId {
		assert_,
		clear,
		count,
		countReset,
		debug,
		dir,
		dirxml,
		error,
		exception,
		group,
		groupCollapsed,
		groupEnd,
		info,
		log,
		profile,
		profileEnd,
		table,
		time,
		timeEnd,
		timeLog,
		timeStamp,
		trace,
		warn,
		
		// Javet specific...
		context,
	}
	
	/**
	 * Writes a console line to the output stream. The console always ends a line with '\n',
	 * like a browser or Node does, and never with the platform line separator that
	 * {@code PrintStream.println()} would use: script output must not depend on the OS.
	 */
	private static void printOut(Object s) {
		JSRuntimeContext.get().getGlobalContext().getOutStream().print(s + "\n");
	}

	/** Same as {@link #printOut(Object)}, on the error stream. */
	private static void printErr(Object s) {
		JSRuntimeContext.get().getGlobalContext().getErrStream().print(s + "\n");
	}

	private final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		private Method(JSEnvironment env, MethodId methodId, String name, int length) {
			super(env,name,length);
			this.methodId = methodId;
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case assert_ -> {
	        		boolean assertion = paramBoolean(args, 0, false);
	        		if(!assertion) {
	        			if(args.length>=2) {
			        		String s = composeLog(args, 1);
			        		printErr(s);
	        			} else {
	        				printErr("Assertion error");
	        			}
	        		}
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case clear -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case count -> {
	        		String label = paramString(args,0,"default");
	        		int count = counters.computeIfAbsent(label, l -> new AtomicInteger(0)).incrementAndGet();
	        		printOut(StringFormat.format("{0}: {1}",label,count));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case countReset -> {
	        		String label = paramString(args,0,"default");
        	        counters.remove(label);
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case debug -> {
	        		String s = composeLog(args, 0);
	        		printOut(s);
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case dir -> {
	        		Object o = param(args,0,RuntimeUtil.UNDEFINED);
                    //String s = ConsoleToString.toString(getEnvironment(), o);
	        		printOut(o!=null?o:"null");
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case dirxml -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case error, exception -> {
	        		String s = composeLog(args, 0);
	        		printErr(s);
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case group -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case groupCollapsed -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case groupEnd -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case info -> {
	        		String s = composeLog(args, 0);
	        		printOut(s);
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case log -> {
	        		String s = composeLog(args, 0);
	        		printOut(s); 
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case profile -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case profileEnd -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case table -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case time -> {
	        		String label = paramString(args,0,"default");
	        	    Long start = timers.get(label);
	        	    if (start != null) {
	        	    	printOut(StringFormat.format("Timer {0} already exists", label));
	        	    } else { 
	        	    	timers.put(label, System.nanoTime());
	        		}
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case timeEnd -> {
	        		String label = paramString(args,0,"default");
	        	    Long start = timers.get(label);
	        	    if (start == null) {
	        	    	printOut(StringFormat.format("Timer {0} does not exist", label));
	        	    } else { 
	        	    	printOut(StringFormat.format("{0}: {1}ms", label, nano2Milli(System.nanoTime() - start) ));
	        	    	timers.remove(label);
	        		}
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case timeLog -> {
	        		String label = paramString(args,0,"default");
	        	    Long start = timers.get(label);
	        	    if (start == null) {
	        	    	printOut(StringFormat.format("Timer {0} does not exist", label));
	        	    } else {
	        	    	StringBuilder b = new StringBuilder();
	    				StringFormat.format(b, "{0}: {1}ms", label, nano2Milli(System.nanoTime() - start) );
	    				for(int i=1; i<args.length; i++) {
	    					b.append(" ");
	    					b.append(RuntimeUtil.toString(getEnvironment(), args[i]));
	    				}
	    				printOut(b.toString());
	        		}
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case timeStamp -> {
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case trace -> {
	        		// Could be done in interpreted mode?
	        		// Should works like the debugger gathering the stack
	        		printOut(StringFormat.format("Console method {0} is not implemented", getId()));
    				return RuntimeUtil.UNDEFINED;
	        	}
	        	case warn -> {
	        		String s = composeLog(args, 0);
	        		printOut(s);
    				return RuntimeUtil.UNDEFINED;
	        	}

	        	case context -> {
	        		throw RuntimeUtil.deprecated("context");
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	    
	    private double nano2Milli(Long nano) {
	        return nano / 1000000D;
	    }	    
		
		private static final Pattern FMT_REG = Pattern.compile("%[sfdioOc%]");
		
		public String composeLog(Object[] args, int start) {
	        if (start>=args.length) {
	            return "";
	        }

	        StringBuilder b = new StringBuilder();
	        int argIndex = start;

	        Object first = args[start];
	        if (first instanceof CharSequence) {
	        	String msg = first.toString();
	            Matcher matcher = FMT_REG.matcher(msg);

	            argIndex = start+1;
	            while (matcher.find()) {
	                String placeHolder = matcher.group();
	                String replaceArg;

	                if (placeHolder.equals("%%")) {
	                    replaceArg = "%";
	                } else if (argIndex >= args.length) {
	                    replaceArg = placeHolder;
	                    argIndex++;
	                } else {
	                    Object val = args[argIndex];
	                    switch (placeHolder) {
	                        case "%s" ->  { // String
	                        	if(val instanceof Symbol sy) {
	                        		replaceArg = sy.toString();
	                        	} else {
	                        		replaceArg = RuntimeUtil.toString(getEnvironment(), val);
	                        	}
	                        }

	                        case "%d", "%i" -> { // Integer
	                        	Number n = val instanceof Symbol ? Double.NaN : RuntimeUtil.toNumber(getEnvironment(), val);
	                        	if(n instanceof Double && !Double.isFinite(n.doubleValue())) {
	                        		// Like Node: NaN and Infinity are printed as such, not as a long
	                        		replaceArg = RuntimeUtil.toString(getEnvironment(), n);
	                        	} else if(n instanceof BigInteger bi) {
		                            replaceArg = bi.toString();
	                        	} else if(n instanceof BigDecimal bd) {
	                        		replaceArg = JsonUtil.toBigInteger(bd).toString();
	                        	} else {
	                        		if(n instanceof Double || n instanceof Float) {
		                        		double d = n.doubleValue();
		                        		if(d<Long.MIN_VALUE || d>Long.MAX_VALUE) {
			                        		replaceArg = JsonUtil.toBigInteger(n).toString();
		                        		} else {
		                        			replaceArg = Long.toString(n.longValue());
		                        		}
	                        		} else {
	                        			replaceArg = Long.toString(n.longValue());
	                        		}
	                        	}
	                        }

	                        case "%f" -> {
	                        	Number n = val instanceof Symbol ? Double.NaN : RuntimeUtil.toNumber(getEnvironment(), val);
	                            replaceArg = RuntimeUtil.toString(getEnvironment(), n);
	                        }

	                        case "%o", "%O"-> {
	                            replaceArg = ConsoleToString.toString(getEnvironment(), val);
	                        }
	                        
	                        // case "%c" // ignore!
	                        default -> {
	                            replaceArg = "";
	                        }
	                    }
	                    argIndex++;
	                }

	                matcher.appendReplacement(b, Matcher.quoteReplacement(replaceArg));
	            }
	            matcher.appendTail(b);
	        }

	        for (int i = argIndex; i < args.length; i++) {
	            if (b.length() > 0) {
	                b.append(' ');
	            }

	            Object val = args[i];
            	if(val instanceof Symbol sy) {
            		b.append(sy.toString());
            	} else {
               		b.append(ConsoleToString.toString(getEnvironment(), val));
	            }
	        }

	        return b.toString();
	    }
	
	}
}