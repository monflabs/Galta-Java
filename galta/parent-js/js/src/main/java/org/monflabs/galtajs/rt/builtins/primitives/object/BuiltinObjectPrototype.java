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
package org.monflabs.galtajs.rt.builtins.primitives.object;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitivePrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.json.JsonObject;
import org.monflabs.util.StringFormat;

/**
 * Eqv of the JavaScript Object prototype.
 */
public class BuiltinObjectPrototype extends BasePrimitivePrototype {

	// Spec 20.1.3.6 Object.prototype.toString's builtinTag steps 4-13 only
	// check internal slots for these categories - see the usage in
	// toString(JSEnvironment,Object) below.
	private static final java.util.Set<String> BUILTIN_TAG_CLASS_NAMES = java.util.Set.of(
		"Array", "Arguments", "Function", "Error", "Boolean", "Number", "String", "Date", "RegExp"
	);

	// 20.1.3: the Object prototype object is an "immutable prototype
	// exotic object" - its [[SetPrototypeOf]] succeeds only for a same-
	// value write, returning false (not throwing itself - the __proto__
	// setter/Object.setPrototypeOf callers turn a false return into their
	// own TypeError) for anything else, even a value that would otherwise
	// be a perfectly valid, non-circular prototype (confirmed via
	// setPrototypeOf-with-non-circular-values.js/-__proto__.js).
	@Override
	public boolean setPrototype(Object prototype) {
		return getPrototype()==prototype;
	}

	public static BuiltinObjectPrototype get(JSEnvironment env) {
		BuiltinObjectPrototype proto = (BuiltinObjectPrototype)env.getRegisteredPrototype(BuiltinObjectPrototype.class);
		if(proto==null) {
			proto = new BuiltinObjectPrototype(env);
			env.registerPrototype(BuiltinObjectPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinObjectPrototype(JSEnvironment env) {
		super(env);
		if(env.isDeprecatedApis()) {
			// B.2.2.1.1 set Object.prototype.__proto__: [[Configurable]] is
			// true (only [[Enumerable]] is false) - confirmed via
			// __proto__/prop-desc.js. RequireObjectCoercible(this) must
			// throw for null/undefined `this` before anything else
			// (confirmed via __proto__/set-non-obj-coercible.js). A
			// non-object, non-null value is a silent no-op (spec step 3:
			// "return undefined", not a [[Set]] failure). Only a genuine
			// [[SetPrototypeOf]] failure (cycle, non-extensible, or an
			// immutable-prototype-exotic target like Object.prototype
			// itself) must throw - unconditionally, per "? O.[[SetPrototypeOf]]
			// (proto)" (confirmed via set-cycle.js/set-immutable.js/
			// set-non-extensible.js).
			setOwnProperty("__proto__",true,false,
					(t,k) -> {
						JSAccessor acc = env.getAccessor(t);
						return acc.getPrototype(t);
					},
					(t,k,v) -> {
						if(RuntimeUtil.isNullOrUndefined(t)) {
							throw RuntimeUtil.typeError("Function set __proto__ called on null or undefined");
						}
						// Spec step 3: "If Type(O) is not Object, return
						// undefined" - a primitive `this` (e.g. `x.__proto__ = y`
						// where x is a raw Symbol/Number/boolean/string, whose
						// [[Set]] receiver per PutValue's GetThisValue is the
						// ORIGINAL primitive, not a throwaway boxed object) is a
						// silent no-op, not a [[SetPrototypeOf]] attempt at all
						// (confirmed via es6/proto-property.js).
						if(v==null || RuntimeUtil.isObject(env, v)) {
							if(RuntimeUtil.isObject(env, t)) {
								JSAccessor acc = env.getAccessor(t);
								if(!acc.setPrototype(t, v)) {
									throw RuntimeUtil.typeError("Cannot set prototype");
								}
							}
						}
						return true;
					}
			);
		}

		setOwnMethod(new Method(env,MethodId.hasOwnProperty,1));
		setOwnMethod(new Method(env,MethodId.isPrototypeOf,1));
		setOwnMethod(new Method(env,MethodId.propertyIsEnumerable,1));
		setOwnMethod(new Method(env,MethodId.toLocaleString,0));
		
		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.valueOf,0));
		
		if(env.isDeprecatedApis()) {
			setOwnMethod(new Method(env,MethodId.__defineGetter__,2));
			setOwnMethod(new Method(env,MethodId.__defineSetter__,2));
			setOwnMethod(new Method(env,MethodId.__lookupGetter__,1));
			setOwnMethod(new Method(env,MethodId.__lookupSetter__,1));
		}
	}

	@Override
	protected Object getDefaultPrototype() {
		// This is the only object without a default prototype!
		return null;
	}
	
	@Override
	public Class<?> getNativeClass() {
		return JsonObject.class;
	}
	
	private static enum MethodId {
		hasOwnProperty,
		isPrototypeOf,
		propertyIsEnumerable,
		toLocaleString,
		toString,
		valueOf,
		// Deprecated
		__defineGetter__,
		__defineSetter__,
		__lookupGetter__,
		__lookupSetter__,
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
		public Object call(Object obj, final Object[] args) {
	    	switch(methodId) {
	        	case hasOwnProperty -> {
	        		// Spec: "Let P be ? ToPropertyKey(V)" happens BEFORE "Let O
	        		// be ? ToObject(this value)" - key coercion first
	        		// (confirmed via
	        		// hasOwnProperty/topropertykey_before_toobject.js: a
	        		// poisoned key's exception must be observed even when
	        		// `this` is null/undefined, and the
	        		// symbol_property_*.js siblings: a Symbol.toPrimitive/
	        		// toString/valueOf-derived Symbol key must be used AS a
	        		// Symbol, not subsequently stringified).
	        		Object prop = RuntimeUtil.toPropertyKeyString(getEnvironment(), param(args,0));
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	    	    	}
	        		return RuntimeUtil.getOwnPropertyDescriptor(getEnvironment(), obj, prop)!=null;
	        	}
	        	case isPrototypeOf -> {
	        		// Per spec §20.1.3.5: walk prototype chain of V looking for this (obj)
	        		Object v = param(args, 0, null);
	        		if(RuntimeUtil.isPrimitiveValue(getEnvironment(), v)) {
	        			return false; // V is not an object, return false immediately
	        		}
	        		Object o = RuntimeUtil.toObject(getEnvironment(), obj);
	        		while(true) {
	        			v = RuntimeUtil.getPrototype(getEnvironment(), v);
	        			if(v == null) {
	        				return false;
	        			}
	        			if(o == v) {
	        				return true;
	        			}
	        		}
	        	}
	        	case propertyIsEnumerable -> {
	        		// See hasOwnProperty's matching comment above: ToPropertyKey
	        		// must run first (before the null-`this` check), and a
	        		// Symbol.toPrimitive/toString/valueOf-derived Symbol key
	        		// must be used AS a Symbol - the previous `o instanceof
	        		// Symbol` check only caught an ALREADY-a-Symbol argument,
	        		// not one that becomes a Symbol via coercion.
	        		Object prop = RuntimeUtil.toPropertyKeyString(getEnvironment(), param(args,0));
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	    	    	}
        			JSAccessor a = getEnvironment().getAccessor(obj);
	        		if(prop instanceof Symbol sy) {
	        			PropertyDescriptor dec = a.getOwnPropertyDescriptor(obj, sy);
		        		return dec!=null && dec.isEnumerable();
	        		} else {
	        			PropertyDescriptor dec = a.getOwnPropertyDescriptor(obj, (String)prop);
		        		return dec!=null && dec.isEnumerable();
	        		}
	        	}
	        	case toLocaleString -> {
	        		// Spec: Object.prototype.toLocaleString() is "Let O be ?
	        		// ToObject(this value)" then "Return ? Invoke(O,
	        		// 'toString')" - ToObject throws for null/undefined
	        		// (confirmed via toLocaleString/S15.2.4.3_A12.js/-A13.js),
	        		// it must NOT reproduce Object.prototype.toString's own
	        		// "[object X]" tag format (that was this method's OTHER
	        		// bug: every value without its OWN toLocaleString
	        		// override, e.g. a plain string element inside an
	        		// Array/TypedArray's toLocaleString() join, rendered as
	        		// "[object String]" instead of delegating to its real
	        		// toString()).
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	        		}
	        		JSAccessor a = getEnvironment().getAccessor(obj);
	        		Object toStringMethod = a.getProperty(obj,"toString",RuntimeUtil.NOT_AVAILABLE);
	        		return RuntimeUtil.toString(getEnvironment(), RuntimeUtil.call(getEnvironment(), toStringMethod, obj, RuntimeUtil.EMPTY_PARAMS));
            	}
	        	case toString -> {
	        		return BuiltinObjectPrototype.toString(getEnvironment(),obj);
            	}
	        	case valueOf -> {
	        		// Spec: "Let O be ? ToObject(this value)" - throws for
	        		// null/undefined (confirmed via valueOf/S15.2.4.4_A12.js
	        		// through -A15.js).
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	        		}
	        		if(RuntimeUtil.isPrimitiveValue(getEnvironment(),obj)) {
	        			return RuntimeUtil.primitiveAsObject(getEnvironment(),obj);
	        		}
            		return obj;
            	}
	            //public static boolean defineProperty(JSEnvironment env, Object instance, Object prop, JSObject desc) {

	        	case __defineGetter__ -> {
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	    	    	}
	        		// Per spec: "If IsCallable(getter) is false, throw a
	        		// TypeError" - checked BEFORE the defineProperty call
	        		// (confirmed via __defineGetter__/getter-non-callable.js).
	        		Object prop = param(args,0);
	        		Object getter = param(args,1,RuntimeUtil.UNDEFINED);
	        		if(!(getter instanceof Callable c && c.isCallable())) {
	        			throw RuntimeUtil.typeError("Getter must be a function");
	        		}
	        		// Per spec, equivalent to Object.defineProperty(obj, prop, { get: func, configurable: true, enumerable: true })
	        		JSObject props = JSObject.of(getEnvironment(),"configurable",
	        			true, "enumerable",
	        			true, "get",
	        			getter
	        		);
	        		// Per spec: "Perform ? DefinePropertyOrThrow" - a false
	        		// result (e.g. an existing non-configurable property, or a
	        		// non-extensible target) must throw, not silently no-op
	        		// (confirmed via __defineGetter__/define-non-configurable.js
	        		// and define-non-extensible.js).
	        		if(!RuntimeUtil.defineProperty(getEnvironment(), obj, prop, props)) {
	        			throw RuntimeUtil.typeError("Cannot define property {0}", prop);
	        		}
        			return RuntimeUtil.UNDEFINED;
	        	}
	        	case __defineSetter__ -> {
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	    	    	}
	        		// See __defineGetter__'s matching comment above.
	        		Object prop = param(args,0);
	        		Object setter = param(args,1,RuntimeUtil.UNDEFINED);
	        		if(!(setter instanceof Callable c && c.isCallable())) {
	        			throw RuntimeUtil.typeError("Setter must be a function");
	        		}
	        		// Per spec, equivalent to Object.defineProperty(obj, prop, { set: func, configurable: true, enumerable: true })
	        		JSObject props = JSObject.of(getEnvironment(),"configurable",
	        			true, "enumerable",
	        			true, "set",
	        			setter
	        		);
	        		if(!RuntimeUtil.defineProperty(getEnvironment(), obj, prop, props)) {
	        			throw RuntimeUtil.typeError("Cannot define property {0}", prop);
	        		}
        			return RuntimeUtil.UNDEFINED;
	        	}
	        	case __lookupGetter__ -> {
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	    	    	}
	        		Object prop = param(args,0);
        			JSAccessor a = getEnvironment().getAccessor(obj);
        			PropertyDescriptor d = a.getPropertyDescriptor(obj, prop);
        			if(d!=null) {
        				Object v = d.getGetter();
        				if(v!=null) {
        					return v;
        				}
        			}
        			return RuntimeUtil.UNDEFINED;
	        	}
	        	case __lookupSetter__ -> {
	        		if(RuntimeUtil.isNullOrUndefined(obj)) {
	        			throw nullThis();
	    	    	}
	        		Object prop = param(args,0);
        			JSAccessor a = getEnvironment().getAccessor(obj);
        			PropertyDescriptor d = a.getPropertyDescriptor(obj, prop);
        			if(d!=null) {
        				Object v = d.getSetter();
        				if(v!=null) {
        					return v;
        				}
        			}
        			return RuntimeUtil.UNDEFINED;
			    }
  	
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}
	
	public static String toString(JSEnvironment env, Object obj) {
		if(obj==null) {
			return "[object Null]";
		} else if(obj==RuntimeUtil.UNDEFINED) { 
			return "[object Undefined]";
		} else {
			JSAccessor a = env.getAccessor(obj);
			// Spec steps 4-14 compute builtinTag (via IsArray/internal slots)
			// BEFORE step 15's "Let tag be ? Get(O, @@toStringTag)" - order
			// matters for a Proxy whose @@toStringTag trap revokes it as a
			// side effect: builtinTag must already be captured from the
			// still-valid proxy before that happens, since a non-string tag
			// result falls back to it without touching the proxy again
			// (confirmed via prototype/toString/proxy-revoked-during-get-call.js).
			// Spec's builtinTag (steps 4-13) only checks internal slots for
			// these 9 categories - everything else (Map, Set, Promise,
			// Generator, TypedArrays, ArrayBuffer, ...) gets its tag SOLELY
			// via @@toStringTag (below); with no tag (deleted or never
			// installed), it must fall through to "Object", not whatever
			// richer getClassName() reports for other purposes like console
			// formatting (confirmed via non-callable-join-string-tag.js's
			// `delete Set.prototype[Symbol.toStringTag]` case).
			String c = a.getClassName(obj);
			// Spec: "Let tag be ? Get(O, @@toStringTag)" - a genuine,
			// prototype-chain-aware [[Get]], not an own-property-only read
			// - a built-in's @@toStringTag is typically installed on its
			// PROTOTYPE (e.g. Date.prototype[Symbol.toStringTag] === "Date"),
			// not as an own property of each instance (confirmed via
			// Array.prototype.toString's non-callable-join-string-tag.js,
			// whose `new Date`/`new Error`/etc. cases all rely on this).
			// Spec step 16: "If Type(tag) is not String, set tag to
			// builtinTag" - a boxed String OBJECT (e.g. `Object("Foo")`,
			// as opposed to the primitive "Foo") does not count and must
			// be ignored, falling through to the builtin tag. GaltaJS has
			// no wrapper type for boxed strings (same java.lang.String as
			// the primitive) - isPrimitiveValue's PrimitivePropertyMap
			// check is what actually distinguishes them.
			Object toStringTag = a.getProperty(obj,Symbol.TO_STRING_TAG,null,obj);
			if(toStringTag instanceof String s && RuntimeUtil.isPrimitiveValue(env,toStringTag)) {
    			return StringFormat.format("[object {0}]",s);
			}
			if(c!=null && BUILTIN_TAG_CLASS_NAMES.contains(c)) {
    			return StringFormat.format("[object {0}]",c);
			}
		}
		return "[object Object]";
	}
}
