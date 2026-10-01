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
package org.monflabs.galtajs.rt.builtins.errors;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype;

/**
 * @author Philippe Riand
 */
public class Error extends BaseError {

	// Extended property for Errors when a Java Throwable is attached
	public static final String JAVA_EXCEPTION = "__java_exception__";
	
	public static final class PrototypeImpl extends BasePrototype {
		
		public static PrototypeImpl get(JSEnvironment env) {
			PrototypeImpl proto = (PrototypeImpl)env.getRegisteredPrototype(PrototypeImpl.class);
			if(proto==null) {
				proto = new PrototypeImpl(env);
				env.registerPrototype(PrototypeImpl.class,proto);
			}
			return proto;
		}
		
		private PrototypeImpl(JSEnvironment env) {
			super(env);
			setOwnProperty("name",ConstructorImpl.CLASSNAME,PropertyDescriptor.DESC_METHOD);
			setOwnProperty("message","",PropertyDescriptor.DESC_METHOD);
			setOwnMethod(new MethodImpl(env,MethodId.toString,0));

			// Error.prototype.stack (error-stack-accessor proposal, 2026): an
			// accessor property { enumerable: false, configurable: true, get,
			// set } living only on %Error.prototype%, inherited by every
			// native error subtype's prototype chain.
			//
			// Getter: per spec, returns undefined unless `this` has an
			// [[ErrorData]] internal slot - GaltaJS has no internal-slot
			// machinery, so (mirroring Error.isError's own brand check just
			// above) `instanceof BaseError` stands in for the slot check: it's
			// realm-agnostic (any BaseError instance from any JSEnvironment
			// qualifies) and unaffected by prototype-chain tricks (an object
			// merely inheriting from Error.prototype, e.g.
			// Object.create(Error.prototype), is not a BaseError). The
			// returned string is implementation-defined; we reuse BaseError's
			// existing Java toString() ("{name}: {message}"), read directly
			// off the receiver's own name/message properties rather than
			// through the receiver's (possibly user-overridden) JS-level
			// toString method - the same first-line convention other engines
			// use for their own (much richer) stack strings.
			//
			// Setter: SetterThatIgnoresPrototypeProperties, mirrored from
			// BuiltinIteratorConstructor's %IteratorPrototype%.constructor /
			// Symbol.toStringTag accessors (the only other place this
			// algorithm is used) - throws if the receiver IS
			// %Error.prototype% itself (can't shadow the prototype's own
			// accessor by assigning to the prototype), otherwise
			// creates/overwrites an own data property on the receiver so a
			// later read shadows this accessor.
			setOwnProperty("stack", true, false,
					(t,k) -> {
						if(!RuntimeUtil.isObject(env, t)) {
							throw RuntimeUtil.typeError("Method get Error.prototype.stack called on incompatible receiver {0}", t);
						}
						if(!(t instanceof BaseError err)) {
							return RuntimeUtil.UNDEFINED;
						}
						return err.toString();
					},
					(t,k,v) -> {
						if(!RuntimeUtil.isObject(env, t)) {
							throw RuntimeUtil.typeError("Method set Error.prototype.stack called on incompatible receiver {0}", t);
						}
						// Type(v) is String: CharSequence, not specifically
						// java.lang.String - concatenation/other string ops
						// can produce a ConsString, an unrelated CharSequence
						// implementation (see RuntimeUtil's own concat path),
						// so checking `instanceof String` alone wrongly
						// rejects ordinary computed strings. Excludes boxed
						// `new String(...)` wrapper objects (Type Object, not
						// String) via isPrimitiveValue's primitive-property-
						// map check.
						if(!(v instanceof CharSequence) || !RuntimeUtil.isPrimitiveValue(env, v)) {
							throw RuntimeUtil.typeError("Error.prototype.stack setter value must be a string, got {0}", v);
						}
						return setThatIgnoresPrototypeProperties(env, this, t, "stack", v);
					});
		}

		// SetterThatIgnoresPrototypeProperties(V, Home, P) - mirrors the
		// private helper of the same name in BuiltinIteratorConstructor, with
		// two additions needed for full spec fidelity that Iterator's own
		// accessors never happened to exercise:
		//  - the [[Set]] path (existing own property) uses DESC_CHECK.STRICT
		//    too, not the default CHECK, so Set(this,p,v,Throw=true) really
		//    throws for a non-writable own data property or an own accessor
		//    with no setter (setter-non-writable-stack.js, setter-own-
		//    accessor.js);
		//  - both paths' boolean result is checked explicitly, because
		//    ProxyAccessor.setOwnProperty ignores the DESC_CHECK argument
		//    entirely when a defineProperty/set trap is present and simply
		//    returns the trap's (possibly false) result instead of throwing
		//    (setter-proxy-trap-rejects.js).
		private static boolean setThatIgnoresPrototypeProperties(JSEnvironment env, Object home, Object base, Object key, Object value) {
			if(!RuntimeUtil.isObject(env, base)) {
				throw RuntimeUtil.typeError("Cannot set property {0} on a non-object receiver", key);
			}
			if(base==home) {
				throw RuntimeUtil.typeError("Cannot set property {0} on {1}", key, home);
			}
			boolean success;
			if(RuntimeUtil.getOwnPropertyDescriptor(env, base, key)==null) {
				success = env.getAccessor(base).setOwnProperty(base, key, value, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.STRICT, base);
			} else {
				success = RuntimeUtil.setProperty(env, base, key, value, DESC_CHECK.STRICT);
			}
			if(!success) {
				throw RuntimeUtil.typeError("Cannot set property {0} on {1}", key, base);
			}
			return true;
		}

		@Override
		protected Object getDefaultPrototype() {
			// Error.prototype's own [[Prototype]] is %Object.prototype% per
			// spec (20.5.3) - was wrongly wired to Function.prototype (a
			// copy-paste artifact), which leaked Function.prototype's own
			// (non-writable) members - notably "length" - into every Error
			// instance's prototype chain.
			return BuiltinObjectPrototype.get(getEnvironment());
		}
	}

	private static enum MethodId {
		toString,
	}

	private final static class MethodImpl extends BaseMethod {
		private MethodId methodId;
		
		private MethodImpl(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	switch(methodId){
	    		case toString-> {
	    			// Error.prototype.toString works on ANY object (Type(O) is
	    			// Object), not just genuine Error instances - it just reads
	    			// "name"/"message" off whatever receiver it's called with.
	    			JSEnvironment env = getEnvironment();
	    			if(!RuntimeUtil.isObject(env,obj)) {
		    			throw RuntimeUtil.typeError("Method Error.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj);
	    			}
	    			Object nameVal = RuntimeUtil.getProperty(env,obj,"name",RuntimeUtil.UNDEFINED);
	    			String name = nameVal==RuntimeUtil.UNDEFINED ? "Error" : RuntimeUtil.toString(env,nameVal);
	    			Object msgVal = RuntimeUtil.getProperty(env,obj,"message",RuntimeUtil.UNDEFINED);
	    			String msg = msgVal==RuntimeUtil.UNDEFINED ? "" : RuntimeUtil.toString(env,msgVal);
	    			if(name.isEmpty()) {
	    				return msg;
	    			}
	    			if(msg.isEmpty()) {
	    				return name;
	    			}
	    			return name+": "+msg;
	    		}
	            default-> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	}	

	private final static class IsErrorMethod extends BaseMethod {
		private IsErrorMethod(JSEnvironment env) {
			super(env,"isError",1);
		}
		@Override
		protected Object invoke(final Object obj, final Object[] args) {
			// Error.isError(arg): true iff arg has an [[ErrorData]] internal
			// slot - a brand check, not an instanceof/prototype-chain check,
			// so it works across realms and isn't fooled by a fake object
			// with Error.prototype spliced onto its own [[Prototype]].
			return args.length>=1 && args[0] instanceof BaseError;
		}
	}

	public static final class ConstructorImpl extends BaseStandardConstructor {

		public static final String CLASSNAME = "Error";

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,PrototypeImpl.get(env),1);
			setOwnMethod(new IsErrorMethod(env));
		}
		
		@Override
		public Class<?> getNativeClass() {
			return Error.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			Error error = new Error(getEnvironment());
			if(parameters.length>=1 && parameters[0]!=RuntimeUtil.UNDEFINED) {
				error.setOwnProperty("message",RuntimeUtil.toString(getEnvironment(),parameters[0]),PropertyDescriptor.DESC_METHOD);
			}
			if(parameters.length>=2) {
				Object options = parameters[1];
				if(options != null && options != RuntimeUtil.UNDEFINED && RuntimeUtil.isObject(getEnvironment(),options)) {
					// InstallErrorCause: HasProperty must be checked (and its
					// abrupt completion propagated, e.g. via a Proxy "has"
					// trap) before Get - not folded into a single lookup.
					if(RuntimeUtil.hasProperty(getEnvironment(),options,"cause")) {
						Object cause = RuntimeUtil.getProperty(getEnvironment(),options,"cause");
						error.setOwnProperty("cause", cause, PropertyDescriptor.DESC_METHOD);
					}
				}
			}
			// GetPrototypeFromConstructor: `Reflect.construct(Error, args,
			// newTarget)` (or a subclass's super() call) must give the
			// result newTarget.prototype, not always %Error.prototype% -
			// mirrors AggregateError/SuppressedError's constructObject,
			// which already do this; the plain Error/NativeError
			// constructors had been missing it (confirmed by test262's
			// Error/prototype/stack/getter-foreign-new-target.js).
			return applyNewTargetPrototype(error, topConstructor);
		}
	}

    public Error(JSEnvironment env) {
    	super(env);
    }
    
    public Error(JSEnvironment env, String message) {
    	super(env);
		// Set the message
		// Note: this is not a standard property, but it is used by the toString method
		// and it is set by the Error constructor in the standard library.
		// It is not writable, so we use jsPut instead of jsDefineOwnProperty
    	setOwnProperty("message",message,PropertyDescriptor.DESC_METHOD);
    }
    public Error(JSEnvironment env, String message, Object options) {
    	super(env);
    	setOwnProperty("message",message);
    	setOwnProperty("options",options);
    }
    
	@Override
	protected Object getDefaultPrototype() {
		return PrototypeImpl.get(getEnvironment());
	}
}
