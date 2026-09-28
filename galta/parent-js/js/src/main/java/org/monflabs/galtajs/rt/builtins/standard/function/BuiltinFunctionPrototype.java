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
package org.monflabs.galtajs.rt.builtins.standard.function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.ThrowTypeErrorFunction;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.util.StringFormat;

/**
 * Eqv of the JavaScript Function prototype.
 */
public class BuiltinFunctionPrototype extends BuiltinFunction {
	
	public static BuiltinFunctionPrototype get(JSEnvironment env) {
		BuiltinFunctionPrototype proto = (BuiltinFunctionPrototype)env.getRegisteredPrototype(BuiltinFunctionPrototype.class);
		if(proto==null) {
			proto = new BuiltinFunctionPrototype(env);
			env.registerPrototype(BuiltinFunctionPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinFunctionPrototype(JSEnvironment env) {
		super(env,"",0,0);
		setOwnMethod(new Method(env,MethodId.apply,2), PropertyDescriptor.DESC_OLD_METHOD);
		setOwnMethod(new Method(env,MethodId.bind,1), PropertyDescriptor.DESC_OLD_METHOD);
		setOwnMethod(new Method(env,MethodId.call,1), PropertyDescriptor.DESC_OLD_METHOD);
		setOwnMethod(new Method(env,MethodId.toString,0));

		setOwnMethod(new Method(env,MethodId.hasInstance,1), PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);

		// Function.prototype.arguments/.caller (spec 20.2.3.13/14): real,
		// shared accessor properties whose [[Get]] AND [[Set]] are both the
		// SAME %ThrowTypeError% intrinsic (test262's unique-per-realm-*.js
		// requires literal object identity with the one already used for an
		// unmapped Arguments object's poisoned "callee" - see Arguments.java).
		// { enumerable:false, configurable:true } per spec - NOT installed
		// per-instance on every function (that was the old, pre-ES2015 model;
		// modern spec calls AddRestrictedFunctionProperties only once, here,
		// for the realm's %Function.prototype% - see restricted-properties.js
		// test family: ordinary/strict/generator/arrow/method/bound functions
		// all correctly report hasOwnProperty('caller')===false and rely
		// purely on inheriting THIS accessor to throw on access). A function
		// that DOES need its own restricted properties (still poisoned, but
		// with no real own property - see BuiltinFunction.poisonsCallerArguments())
		// is handled separately via BuiltinFunction.getOwnProperty()'s direct
		// throw, so the two mechanisms compose correctly through the
		// prototype chain without either one needing to know about the other.
		ThrowTypeErrorFunction thrower = ThrowTypeErrorFunction.get(env);
		setOwnProperty(Arguments.ARGUMENTS, RuntimeUtil.NOT_AVAILABLE, PropertyDescriptor.of(false,true,false,thrower,thrower));
		setOwnProperty("caller", RuntimeUtil.NOT_AVAILABLE, PropertyDescriptor.of(false,true,false,thrower,thrower));
	}

	
	@Override
	public String getClassName() {
		return BuiltinFunctionConstructor.CLASSNAME;
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinObjectPrototype.get(getEnvironment());
	}

	@Override
	protected Object call(Object _this, Object[] parameters, Constructor newTarget) {
		return RuntimeUtil.UNDEFINED;
	}

	// Function.prototype is itself a callable Function object but, per spec
	// (CreateDefaultConstructor / 20.2.3), has no [[Construct]] internal
	// method at all - unlike an ordinary function, `new Function.prototype`
	// must throw TypeError. See test262
	// built-ins/Function/prototype/S15.3.4_A5.js.
	@Override
	public boolean isConstructor() {
		return false;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		throw RuntimeUtil.typeError("Function.prototype is not a constructor");
	}

	private static enum MethodId {
		apply,
		bind,
		call,	
		toString,
		// Symbol
		hasInstance(Symbol.HAS_INSTANCE),
		// deprecated
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
	    	switch(methodId){
        		case apply -> {
        	    	if(!(obj instanceof Callable)) {
        	    		throw RuntimeUtil.typeError("Object {0} is not a Callable", obj!=null ? obj.getClass() : "null");
        	    	}
        			final Callable _this = (Callable)obj;
        			Object pthis=RuntimeUtil.UNDEFINED;
        			Object[] params=RuntimeUtil.EMPTY_PARAMS;
    				if(args.length>0) {
    					pthis = args[0]; 
        				if(args.length>1) {
        					Object a = param(args,1);
        					if(RuntimeUtil.isNotNullOrUndefined(a)) {
        						JSArray array = RuntimeUtil.getArrayLike(getEnvironment(),a,true);
       							params = array.toArray();
        					}
        				}
    				}
        			return RuntimeUtil.call(getEnvironment(), _this, pthis, params);
        		}
        		case bind -> {
        			Object boundedThis=param(args, 0, RuntimeUtil.UNDEFINED);
    				Object[] boundedArgs;
    				if(args.length>1) {
    					boundedArgs = new Object[args.length-1];
    					System.arraycopy(args, 1, boundedArgs, 0, args.length-1);
    				} else {
    					boundedArgs = RuntimeUtil.EMPTY_PARAMS;
    				}
    				
    				if(obj instanceof BuiltinFunction boundedFunction) {
    					return new BuiltinFunctionBind(getEnvironment(),boundedFunction,boundedThis,boundedArgs);
    				}
    				if(obj instanceof Callable boundedCallable) {
       					return new BuiltinFunctionBind(getEnvironment(),boundedCallable,boundedThis,boundedArgs);
    				}
    	    		throw RuntimeUtil.typeError("Object {0} is not a Callable", obj!=null ? obj.getClass() : "null");
        		}
        		case call -> {
        	    	if(!(obj instanceof Callable)) {
        	    		throw RuntimeUtil.typeError("Object {0} is not a Callable", obj!=null ? obj.getClass() : "null");
        	    	}
        			final Callable _this = (Callable)obj;
        			Object pthis=RuntimeUtil.UNDEFINED;
        			Object[] params=RuntimeUtil.EMPTY_PARAMS;
        			if(args!=null) {
        				if(args.length>0) {
        					pthis = args[0]; 
            				if(args.length>1) {
            					params = new Object[args.length-1];
            					System.arraycopy(args, 1, params, 0, params.length);
            				}
        				}
        			}
        			return RuntimeUtil.call(getEnvironment(), _this, pthis, params);
        		}
        		case toString -> {
        			// A raw `instanceof Callable` alone is true for EVERY BuiltinProxy
        			// regardless of its target (Proxy always implements Callable at the
        			// Java level, for dispatch convenience) - isCallable() is the real,
        			// target-aware check (spec's IsCallable), needed so a Proxy
        			// wrapping a non-callable target still gets rejected. See test262
        			// built-ins/Function/prototype/toString/proxy-non-callable-throws.js.
        			if(!(obj instanceof Callable c) || !c.isCallable()) {
        				throw RuntimeUtil.typeError("Function.prototype.toString called on incompatible receiver {0}", obj!=null ? obj.getClass() : "null");
        			}
        			// A script function (interpreted or transpiled) returns its
        			// exact original source text, or a shape-preserving
        			// `function name() { [unavailable] }` placeholder when that
        			// source isn't retained - never the [native code] form below,
        			// which is reserved for genuine built-ins.
        			if(obj instanceof BuiltinFunctionInterpreter fi) {
        				return fi.getOriginalSource();
        			}
        			if(obj instanceof BuiltinFunctionTranspiler ft) {
        				return ft.getOriginalSource();
        			}
    				Object _name = RuntimeUtil.getProperty(getEnvironment(),obj,"name","");
        			String nameStr = RuntimeUtil.isNullOrUndefined(_name)?"":RuntimeUtil.toString(getEnvironment(),_name);
        			// The NativeFunction grammar (spec's Function.prototype.toString
        			// "implementation-dependent" clause still requires this exact
        			// shape) is `function NativeFunctionAccessor? IdentifierName?
        			// ( FormalParameters ) { [ native code ] }` - there's no bracket-
        			// string form for a non-identifier name, unlike ordinary source
        			// preservation. A legacy accessor like RegExp.$&'s getter has a
        			// `.name` of "get $&", which contains "&" - not a valid
        			// IdentifierName - so the whole name must be omitted (both parts
        			// are independently optional) rather than emitted verbatim into
        			// a syntax the grammar can't express. See test262 built-ins/
        			// Function/prototype/toString/built-in-function-object.js.
        			if(!isValidNativeFunctionName(nameStr)) {
        				nameStr = "";
        			}
        			return StringFormat.format("function {0}() {\n    [native code]\n}",nameStr);
        		}
        		
        		case hasInstance -> {
        			Object v = param(args, 0, RuntimeUtil.UNDEFINED);
        			// OrdinaryHasInstance: if O is not an object, return false - this must
        			// be checked before (and instead of) validating C's own prototype.
        			if(RuntimeUtil.isObject(getEnvironment(), v)) {
        				// OrdinaryHasInstance step 2: a bound function has no own
        				// "prototype" at all - it delegates entirely to its
        				// [[BoundTargetFunction]] (recursively, via the full
        				// InstanceofOperator so a custom @@hasInstance further down
        				// the bind chain is still honored), rather than walking a
        				// prototype chain off of `obj` itself.
        				if(obj instanceof BuiltinFunctionBind bound) {
        					return RuntimeUtil.instanceOf(getEnvironment(), v, bound.getBoundedCallable());
        				}
	        			Object funcPrototype = getEnvironment().getAccessor(obj).getProperty(obj, Constructor.PROTOTYPE, null);
	        			if(RuntimeUtil.isObject(getEnvironment(),funcPrototype)) {
	        				// Any 'Callable' is a Function!
	        				if(funcPrototype==BuiltinFunctionPrototype.get(getEnvironment())) {
	        					return v instanceof Callable;
	        				}
	        				while(RuntimeUtil.isNotNullOrUndefined(v)) {
			        			JSAccessor acc = getEnvironment().getAccessor(v);
			        			Object proto = acc.getPrototype(v);
			        			if(proto==funcPrototype) {
			        				return true;
			        			}
			        			v = proto;
		        			}
	        			} else {
	        				throw RuntimeUtil.typeError("Function has non-object prototype '{0}' in instanceof check", RuntimeUtil.objectTypeName(getEnvironment(), funcPrototype));
	        			}
        			}
        			return false;
        		}
        		
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	}
	// Whether `name` is a legal NativeFunction-grammar name: either empty (no
	// name at all - both NativeFunctionAccessor and IdentifierName are
	// independently optional), or an optional "get "/"set " accessor prefix
	// followed by a valid IdentifierName (same ID_Start/ID_Continue/$/_ rule
	// already used for identifier validation elsewhere, e.g.
	// RegExpEngineJoni's \p{ID_Start}/\p{ID_Continue} check).
	private static boolean isValidNativeFunctionName(String name) {
		if(name.isEmpty()) {
			return true;
		}
		String id = name;
		if(id.startsWith("get ") || id.startsWith("set ")) {
			id = id.substring(4);
		}
		if(id.isEmpty()) {
			return false;
		}
		int cp = id.codePointAt(0);
		if(!(Character.isUnicodeIdentifierStart(cp) || cp=='$' || cp=='_')) {
			return false;
		}
		for(int i=Character.charCount(cp); i<id.length(); ) {
			int c = id.codePointAt(i);
			if(!(Character.isUnicodeIdentifierPart(c) || c=='$' || c=='_')) {
				return false;
			}
			i += Character.charCount(c);
		}
		return true;
	}
}