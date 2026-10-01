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
package org.monflabs.galtajs.rt.builtins.standard.promise;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/* ============================================================
 * Promise.prototype (instance methods wrapper)
 * ============================================================ */
public class BuiltinPromisePrototype extends BasePrototype {

	public static BuiltinPromisePrototype get(JSEnvironment env) {
		BuiltinPromisePrototype proto = (BuiltinPromisePrototype)env.getRegisteredPrototype(BuiltinPromisePrototype.class);
		if(proto==null) {
			proto = new BuiltinPromisePrototype(env);
			env.registerPrototype(BuiltinPromisePrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinPromisePrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinPromiseConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId._catch,1));
		setOwnMethod(new Method(env,MethodId._finally,1));
		setOwnMethod(new Method(env,MethodId._then,2));
	}
	
	@Override
	public String getClassName() {
		return BuiltinPromiseConstructor.CLASSNAME;
	}	

	private static enum MethodId {
		_catch("catch"),
		_finally("finally"),
		_then("then"),
		// Symbol
		toStringTag,
		  ;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(String name) {
			this.id = name;
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
	    	// Promise.prototype.catch/finally are GENERIC (spec: "Return ?
	    	// Invoke(promise, 'then', ...)") - unlike .then itself, they don't
	    	// require a real [[PromiseState]] internal slot, and must work on
	    	// ANY object with a "then" property (test262's this-value-*.js:
	    	// a plain object/proxy/poisoned-then "this"). Deliberately no
	    	// native-fast-path exemption for a genuine BuiltinPromise receiver
	    	// here (there used to be one, going straight to _this.catch_()) -
	    	// that bypassed the "then" PROPERTY entirely, silently ignoring an
	    	// overridden Promise.prototype.then or an instance's own .then
	    	// (test262's own rejected-observable-then-calls.js: "finally
	    	// observably calls .then" - exercised via .catch() upstream of
	    	// .finally() in that test). Same class of bug as
	    	// BuiltinPromise.resolvePromise()'s now-removed thenable-adoption
	    	// fast path.
	    	if(methodId==MethodId._catch) {
	    		JSEnvironment env = getEnvironment();
	    		// Invoke -> GetV -> ToObject(V): only null/undefined throw here -
	    		// any other primitive (boolean/number/string/symbol) is a valid,
	    		// object-coercible receiver (property lookup auto-boxes it).
	    		if(obj==null || obj==RuntimeUtil.UNDEFINED) {
	    			throw RuntimeUtil.typeError("Promise.prototype.catch called on null or undefined");
	    		}
	    		Object onRejected = args.length>=1 ? args[0] : RuntimeUtil.UNDEFINED;
	    		Object thenFn = env.getAccessor(obj).getProperty(obj, "then", RuntimeUtil.UNDEFINED);
	    		if(!(thenFn instanceof Callable c)) {
	    			throw RuntimeUtil.typeError("then is not a function");
	    		}
	    		return c.call(obj, new Object[]{RuntimeUtil.UNDEFINED, onRejected});
	    	}

	    	// Unlike catch (which still takes a native fast path for a genuine
	    	// BuiltinPromise receiver), finally is ALWAYS generic - it must
	    	// respect an own "then" override even on a real Promise instance
	    	// (test262's invokes-then-with-function.js).
	    	if(methodId==MethodId._finally) {
	    		if(obj==null || obj==RuntimeUtil.UNDEFINED) {
	    			throw RuntimeUtil.typeError("Promise.prototype.finally called on null or undefined");
	    		}
	    		// Per spec: a non-callable onFinally passes through UNCHANGED as
	    		// both `then` arguments (not coerced to null/undefined) - a
	    		// receiver's own overridden "then" can observe the exact raw
	    		// value (test262's invokes-then-with-non-function.js).
	    		Object onFinallyArg = args.length>=1 ? args[0] : RuntimeUtil.UNDEFINED;
	    		return BuiltinPromise.finally_(getEnvironment(), obj, onFinallyArg);
	    	}

	    	if(!(obj instanceof BuiltinPromise)) {
	    		throw RuntimeUtil.typeError("Method Promise.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			final BuiltinPromise _this = (BuiltinPromise)obj;

	    	switch(methodId){
	    		case _then-> {
	    	        Callable onFulfilled = null;
	    	        if (args.length >= 1 && args[0] instanceof Callable cb) {
	    	            onFulfilled = cb;
	    	        }
	    	        Callable onRejected = null;
	    	        if (args.length >= 2 && args[1] instanceof Callable cb) {
	    	            onRejected = cb;
	    	        }
	    	        return _this.then_(onFulfilled,onRejected);
	    		}
	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	

//    public Promise then_(Object thisArg, Object onFulfilled, Object onRejected) {
//        if (!(thisArg instanceof Promise p)) throw RuntimeUtil.typeError("Receiver is not a Promise");
//        return p.then_(onFulfilled, onRejected);
//    }
//
//    public Promise catch_(Object thisArg, Object onRejected) {
//        if (!(thisArg instanceof Promise p)) throw RuntimeUtil.typeError("Receiver is not a Promise");
//        return p.catch_(onRejected);
//    }
//
//    public Promise finally_(Object thisArg, Object onFinally) {
//        if (!(thisArg instanceof Promise p)) throw RuntimeUtil.typeError("Receiver is not a Promise");
//        return p.finally_(onFinally);
//    }
}