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

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitiveConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.json.JsonUtil;


/**
 * Create an object constructor.
 */
public class BuiltinObjectConstructor extends BasePrimitiveConstructor {

	public static final String CLASSNAME = "Object";

	public BuiltinObjectConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinObjectPrototype.get(env),1);
		
		setOwnMethod(new Method(env,MethodId.assign,2));
		setOwnMethod(new Method(env,MethodId.create,2));
		setOwnMethod(new Method(env,MethodId.defineProperties,2));
		setOwnMethod(new Method(env,MethodId.defineProperty,3));
		setOwnMethod(new Method(env,MethodId.entries,1));
		setOwnMethod(new Method(env,MethodId.freeze,1));
		setOwnMethod(new Method(env,MethodId.fromEntries,1));
		setOwnMethod(new Method(env,MethodId.getOwnPropertyDescriptor,2));
		setOwnMethod(new Method(env,MethodId.getOwnPropertyDescriptors,1));
		setOwnMethod(new Method(env,MethodId.getOwnPropertyNames,1));
		setOwnMethod(new Method(env,MethodId.getOwnPropertySymbols,1));
		setOwnMethod(new Method(env,MethodId.getPrototypeOf,1));
		setOwnMethod(new Method(env,MethodId.groupBy,2));
		setOwnMethod(new Method(env,MethodId.hasOwn,2));
		setOwnMethod(new Method(env,MethodId.is,2));
		setOwnMethod(new Method(env,MethodId.isExtensible,1));
		setOwnMethod(new Method(env,MethodId.isFrozen,1));
		setOwnMethod(new Method(env,MethodId.isSealed,1));		
		setOwnMethod(new Method(env,MethodId.keys,1));
		setOwnMethod(new Method(env,MethodId.preventExtensions,1));
		setOwnMethod(new Method(env,MethodId.seal,1));
		setOwnMethod(new Method(env,MethodId.setPrototypeOf,2));
		setOwnMethod(new Method(env,MethodId.values,1));
	}
	
	@Override
	public Class<?> getNativeClass() {
		return JSObject.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		// Spec 20.1.1.1 step 1: if NewTarget is neither undefined nor the active
		// function (i.e. this is a `super()` call from an Object subclass, or an
		// explicit Reflect.construct(Object, args, someOtherNewTarget)), the value
		// argument is ignored entirely - just an ordinary object created from
		// newTarget's own "prototype". Only when NewTarget IS Object itself (or
		// absent, i.e. called as a plain function) does the value-wrapping
		// behavior below apply.
		if(topConstructor!=null && topConstructor!=this) {
			return applyNewTargetPrototype(JSObject.create(getEnvironment()), topConstructor);
		}
		if(parameters.length>0) {
			Object p = parameters[0];
			if(p!=null && p!=RuntimeUtil.UNDEFINED) {
				if(RuntimeUtil.isObject(getEnvironment(),p)) {
					return p;
				}
				if(p instanceof Number n) {
					return RuntimeUtil.primitiveAsObject(getEnvironment(),n);
				}
				if(p instanceof CharSequence s) {
					return RuntimeUtil.primitiveAsObject(getEnvironment(),s);
				}
				if(p instanceof Boolean b) {
					return RuntimeUtil.primitiveAsObject(getEnvironment(),b);
				}
				if(p instanceof Symbol s) {
					return RuntimeUtil.primitiveAsObject(getEnvironment(),s);
				}
				return p;
			}
		}
		return JSObject.create(getEnvironment());
	}

	
	private static enum MethodId {
		assign,
		create,
		defineProperties,
		defineProperty,
		entries,
		freeze,
		fromEntries,
		getOwnPropertyDescriptor,
		getOwnPropertyDescriptors,
		getOwnPropertyNames,
		getOwnPropertySymbols,
		getPrototypeOf,
		groupBy,
		hasOwn,
		is,
		isExtensible,
		isFrozen,
		isSealed,
		keys,
		preventExtensions,
		seal,
		setPrototypeOf,
		values,
	}
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case assign -> {
	        		Object target = RuntimeUtil.toObject(getEnvironment(),param(args, 0));
        			if(RuntimeUtil.isNotNullOrUndefined(target)) {
		        		JSAccessor targetAccessor = getEnvironment().getAccessor(target);
	            		int argsCount = args.length;
	            		for(int i=1; i<argsCount; i++) {
	            			Object source = param(args, i);
	            			if(RuntimeUtil.isNotNullOrUndefined(source)) {
	        	        		JSAccessor sourceAccessor = getEnvironment().getAccessor(source);
	        	        		// Per spec §20.1.2.1: copy both string-keyed and symbol-keyed own enumerable properties
	        	        		for(Iterator<Map.Entry<Object, Object>> it=sourceAccessor.ownEntries(source,true); it.hasNext(); ) {
	        	        			Map.Entry<Object, Object> e = it.next();
	        	        			// Spec: Object.assign copies via a plain Set(to, nextKey,
	        	        			// propValue, true) - NOT a defineProperty-style call. Passing
	        	        			// DESC_DEFAULT here (a full, explicit true/true/true
	        	        			// descriptor) made the write look like an attempted
	        	        			// attribute-redefinition, incorrectly rejected once an
	        	        			// existing target property was genuinely non-configurable
	        	        			// (confirmed via
	        	        			// assign/target-is-sealed-existing-data-property.js: a sealed
	        	        			// target's still-WRITABLE existing property must still accept
	        	        			// a new value). `null` preserves the existing property's
	        	        			// shape for an update, and still defaults to full
	        	        			// CreateDataProperty attributes for a brand new one (see
	        	        			// CustomLinkedMap.putEntry's own null-descriptor handling).
	        	        			// Set(to, nextKey, propValue, true) - the trailing "true" is
	        	        			// the spec's Throw flag, hardcoded unconditionally (not gated
	        	        			// by the CALLING code's own strictness), so this must be
	        	        			// STRICT, not CHECK (confirmed via assign/target-set-not-writable.js
	        	        			// and its frozen/sealed/non-extensible siblings: a rejected
	        	        			// write must always throw here).
	        	        			targetAccessor.setProperty(target, e.getKey(), e.getValue(), null, DESC_CHECK.STRICT);
	        	        		}
	            			}
	            		}
        			}
            		return target;
	        	}
	        	case create -> {
	        		// Spec: "If Type(O) is neither Object nor Null, throw a
	        		// TypeError exception" - undefined (and any other
	        		// primitive) is rejected outright, not just "missing".
	        		// Deliberately NOT using param(args,0,NOT_AVAILABLE) here -
	        		// that helper collapses an EXPLICITLY-passed `undefined`
	        		// argument to the same NOT_AVAILABLE default as a truly
	        		// MISSING one, which would silently swallow this case
	        		// (confirmed via create/15.2.3.5-1-1.js: Object.create(undefined)
	        		// must throw, not silently create a prototype-less object).
	        		Object prototype = args.length>=1 ? args[0] : RuntimeUtil.NOT_AVAILABLE;
	        		if(prototype!=RuntimeUtil.NOT_AVAILABLE && prototype!=null && !RuntimeUtil.isObject(getEnvironment(), prototype)) {
	        			throw RuntimeUtil.typeError("Object prototype may only be an Object or null: {0}", prototype);
	        		}
        			JSObjectImpl o = prototype!=RuntimeUtil.NOT_AVAILABLE ?
        					JSObject.createWithPrototype(getEnvironment(),prototype) : JSObject.create(getEnvironment());
	        		// Spec: "If Properties is not undefined" - an explicit or
	        		// omitted undefined skips ObjectDefineProperties entirely
	        		// (rather than ToObject-coercing it, which would throw).
	        		Object props = param(args,1,RuntimeUtil.UNDEFINED);
        			if(props!=RuntimeUtil.UNDEFINED) {
    	        		RuntimeUtil.defineProperties(getEnvironment(),o,props);
        			}
        			return o;
	        	}
	        	case defineProperties -> {
	        		Object o = param(args,0,null);
	        		Object props = param(args,1,RuntimeUtil.UNDEFINED);
	        		RuntimeUtil.defineProperties(getEnvironment(),o,props);
	        		return o;
	        	}
	        	case defineProperty -> {
	        		Object o = param(args,0,null);
	        		Object prop = param(args, 1);
	        		JSObject desc = paramJsonObject(args,2);
	        		if(!RuntimeUtil.defineProperty(getEnvironment(), o, prop, desc)) {
	        			throw RuntimeUtil.typeError("Cannot define property {0}", prop);
	        		}
	        		return o;
	        	}
            	case entries -> {
            		JSArray a = JSArray.create(getEnvironment());
        			// Spec: "Let obj be ? ToObject(O)" - throws for null/undefined
        			// (confirmed via entries/exception-not-object-coercible.js).
        			Object arg0 = RuntimeUtil.toObject(getEnvironment(), param(args, 0));
        			JSAccessor objAccessor = getEnvironment().getAccessor(arg0);
        			// EnumerableOwnProperties: the KEY LIST (ownKeys,
        			// [[OwnPropertyKeys]]) is snapshotted ONCE up front - a
        			// getter that adds a new property as a side effect
        			// mid-enumeration must NOT see that new property included
        			// (confirmed via entries/return-order.js/getter-adding-key.js).
        			// But each key's [[GetOwnProperty]] (existence +
        			// enumerable check) happens FRESH, one at a time, AS the
        			// loop reaches it - an earlier key's getter making a
        			// LATER (already-snapshotted) key non-enumerable or
        			// deleting it must still be observed when that later key
        			// is reached (confirmed via
        			// entries/getter-making-future-key-nonenumerable.js/
        			// getter-removing-future-key.js) - so the snapshot itself
        			// must be the RAW key list (enumerableOnly=false, no
        			// getter triggered), with the enumerable re-check and
        			// value read both happening fresh per key in the second
        			// pass.
        			java.util.List<String> keys = new java.util.ArrayList<>();
    	        	for(var it=objAccessor.ownStringEntries(arg0,false); it.hasNext(); ) {
    	        		keys.add(it.next().getKey());
    	        	}
    	        	for(String key : keys) {
    	        		PropertyDescriptor desc = objAccessor.getOwnPropertyDescriptor(arg0, key);
    	        		if(desc==null || !desc.isEnumerable()) {
    	        			continue;
    	        		}
            			JSArray v = JSArray.create(getEnvironment());
                    	v.arrayAdd(key);
                    	v.arrayAdd(objAccessor.getProperty(arg0, key, RuntimeUtil.UNDEFINED));
            			a.arrayAdd(v);
    	        	}
            		return a;
            	}
	        	case freeze -> {
	        		Object o = param(args, 0);
	        		// Spec (ES2015+): "If Type(O) is not Object, return O" - a
	        		// primitive (including undefined/null) is a silent no-op,
	        		// not an error (confirmed via freeze/15.2.3.9-1-1.js:
	        		// `Object.freeze(undefined)` must return undefined, not
	        		// throw). Mirrors preventExtensions' existing guard below.
	        		if(RuntimeUtil.isObject(getEnvironment(), o)) {
	        			JSAccessor accessor = getEnvironment().getAccessor(o);
	        			// SetIntegrityLevel step 1: "? O.[[PreventExtensions]]()"
	        			// runs FIRST and its failure/throw must propagate - an
	        			// ordinary object's preventExtensions() is a trivial
	        			// always-true flag set (freeze() below sets the same
	        			// flag again, harmlessly), but a Proxy's trap can
	        			// return false or throw, which accessor.freeze(o)'s
	        			// own property-locking loop has no way to observe
	        			// (confirmed via freeze/throws-when-false.js and
	        			// freeze/abrupt-completion.js).
	        			if(!accessor.preventExtensions(o)) {
	        				throw RuntimeUtil.typeError("Object.freeze can't freeze proxy");
	        			}
	            		accessor.freeze(o);
	        		}
	        		return o;
	        	}
            	case fromEntries -> {
            		JSEnvironment env = getEnvironment();
            		JSObject o = JSObject.create(env);
            		Object iterable = param(args, 0);
            		Iterator<?> it=RuntimeUtil.valueIterator(env,iterable);
            		if(it==null) {
            			throw RuntimeUtil.typeError("Object is not iterable, {0}",iterable);
            		}
            		// Spec: each entry is read GENERICALLY via Get(entry,"0")/
            		// Get(entry,"1") - NOT restricted to genuine JSArray
            		// entries (a plain array-like object or a String object
            		// wrapping a 2-char string both qualify, confirmed via
            		// fromEntries/string-entry-object-succeeds.js and
            		// uses-keys-not-iterator.js) - and a non-object entry
            		// (Type(entry) is not Object) must IteratorClose then
            		// throw TypeError (confirmed via
            		// fromEntries/string-entry-primitive-throws.js and the
            		// iterator-closed-for-*.js siblings). The key is
            		// genuinely ToPropertyKey-coerced, so a Symbol key is
            		// preserved as-is, not stringified (confirmed via
            		// fromEntries/supports-symbols.js).
            		while(it.hasNext()) {
            			Object entry = it.next();
            			if(!RuntimeUtil.isObject(env, entry)) {
            				RuntimeUtil.iteratorCloseQuietly(env, it);
            				throw RuntimeUtil.typeError("Value {0} is not an object",JsonUtil.toDebugString(entry));
            			}
            			try {
	            			JSAccessor entryAcc = env.getAccessor(entry);
	            			Object key = entryAcc.getProperty(entry, "0", RuntimeUtil.UNDEFINED);
	            			Object value = entryAcc.getProperty(entry, "1", RuntimeUtil.UNDEFINED);
	            			Object propKey = RuntimeUtil.toPropertyKey(env, key);
	            			if(propKey instanceof Symbol sym) {
	            				o.setOwnProperty(sym, value);
	            			} else {
	            				o.setOwnProperty(RuntimeUtil.toString(env,propKey), value);
	            			}
            			} catch(RuntimeException|Error e) {
            				RuntimeUtil.iteratorCloseQuietly(env, it);
            				throw e;
            			}
            		}
            		return o;
            	}
	        	case getOwnPropertyDescriptor -> {
	        		Object o = param(args, 0);
	        		JSAccessor accessor = getEnvironment().getAccessor(o);
	        		Object prop = param(args, 1);
	        		JSObject p = accessor.getOwnPropertyDescriptorAsJSObject(o,prop);
	        		return p!=null ? p : RuntimeUtil.UNDEFINED;
	        	}
	        	case getOwnPropertyDescriptors -> {
	        		Object o = param(args, 0);
	        		JSAccessor accessor = getEnvironment().getAccessor(o);
	        		return accessor.getOwnPropertyDescriptorsAsJSObject(o);
	        	}
	        	case getOwnPropertyNames -> {
	        		Object o = param(args, 0);
	        		JSAccessor accessor = getEnvironment().getAccessor(o);
	        		JSArray array = JSArray.create(getEnvironment());
	        		for(var it=accessor.ownStringEntries(o,false); it.hasNext(); ) {
	        			String k = it.next().getKey();
	        			array.arrayAdd(k);
	        		}
	        		return array;
	        	}
	        	case getOwnPropertySymbols -> {
	        		Object o = param(args, 0);
	        		JSAccessor accessor = getEnvironment().getAccessor(o);
	        		JSArray array = JSArray.create(getEnvironment());
	        		for(var it=accessor.ownSymbolEntries(o,false); it.hasNext(); ) {
	        			Symbol k = it.next().getKey();
	        			array.arrayAdd(k);
	        		}
	        		return array;
	        	}
	        	case getPrototypeOf -> {
	        		Object o = paramNotNull(args, 0);
	        		return RuntimeUtil.getPrototype(getEnvironment(), o);
	        	}
	        	case groupBy -> {
					// Spec: "Let obj be OrdinaryObjectCreate(null)" - a
					// null-prototype object, not the default Object.prototype
					// (confirmed via groupBy/null-prototype.js).
					JSObject result = JSObject.createWithPrototype(getEnvironment(), null);
					Object thisArg = param(args, 0, null);                    
					Callable function = paramCallableNotNull(args, 1);
					Object[] cbArgs = new Object[2];
					int index = 0;
					Iterator<Object> it=RuntimeUtil.valueIterator(getEnvironment(),thisArg);
            		if(it==null) {
            			throw RuntimeUtil.typeError("Object is not iterable, {0}",thisArg);
            		}
					while(it.hasNext()) {
						Object v = it.next();
					  	cbArgs[0]=v;
					  	cbArgs[1]=index++;
					  	String r = RuntimeUtil.toString(getEnvironment(),function.call(thisArg, cbArgs));
					  	Object group = result.getProperty(r);
					  	if(group==null || group==RuntimeUtil.UNDEFINED) {
					  		group = JSArray.create(getEnvironment());
					    	result.setOwnProperty(r,group);
					  	}
					    ((JSArray)group).arrayAdd(v);
					 }
		  			return result;
		      	}
            	case hasOwn -> {
            		// Spec: "Let obj be ? ToObject(O)" happens BEFORE "Let key
            		// be ? ToPropertyKey(P)" - the OPPOSITE order from
            		// Object.prototype.hasOwnProperty (confirmed via
            		// hasOwn/toobject_before_topropertykey.js). The key must
            		// also genuinely support Symbol keys (a
            		// Symbol.toPrimitive/toString/valueOf-derived Symbol used
            		// AS a Symbol, not stringified) - confirmed via
            		// hasOwn/symbol_own_property.js and its symbol_property_*.js
            		// siblings.
            		Object o = RuntimeUtil.toObject(getEnvironment(), param(args, 0));
            		Object key = RuntimeUtil.toPropertyKeyString(getEnvironment(), param(args, 1));
	        		JSAccessor accessor = getEnvironment().getAccessor(o);
	        		return accessor.getOwnPropertyDescriptor(o, key)!=null;
            	}
	        	case is -> {
	        		// Both arguments simply default to undefined when omitted -
	        		// no ToObject/ToPropertyKey coercion at all (confirmed via
	        		// is/same-value-x-y-undefined.js: `Object.is(undefined)`,
	        		// only 1 argument, must not throw).
	        		Object v1 = param(args, 0, RuntimeUtil.UNDEFINED);
	        		Object v2 = param(args, 1, RuntimeUtil.UNDEFINED);
	        		return RuntimeUtil.eqSameValue(getEnvironment(),v1, v2);
	        	}
	        	case isExtensible -> {
	        		Object o = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(RuntimeUtil.isNotNullOrUndefined(o)) {
	        			JSAccessor accessor = getEnvironment().getAccessor(o);
	        			return accessor.isExtensible(o);
	        		}
	        		return false;
	        	}
	        	case isFrozen -> {
	        		Object o = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(RuntimeUtil.isNotNullOrUndefined(o)) {
	        			return RuntimeUtil.testIntegrityLevel(getEnvironment(), o, true);
	        		}
	        		return true;
	        	}
	        	case isSealed -> {
	        		Object o = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(RuntimeUtil.isNotNullOrUndefined(o)) {
	        			return RuntimeUtil.testIntegrityLevel(getEnvironment(), o, false);
	        		}
	        		return true;
	        	}
            	case keys -> {
	        		// Spec: "Let obj be ? ToObject(O)" - throws for null/undefined.
	        		Object o = RuntimeUtil.toObject(getEnvironment(), param(args, 0));
	        		JSAccessor accessor = getEnvironment().getAccessor(o);
	        		JSArray array = JSArray.create(getEnvironment());
	        		for(var it=accessor.ownStringEntries(o,true); it.hasNext(); ) {
	        			String k = it.next().getKey();
	        			array.arrayAdd(k);
	        		}
	        		return array;
            	}
	        	case preventExtensions -> {
	        		Object o = param(args, 0);
	        		// Spec: "If Type(O) is not Object, return O" - a primitive is a
	        		// silent no-op, not an error.
	        		if(RuntimeUtil.isObject(getEnvironment(), o)) {
	        			JSAccessor accessor = getEnvironment().getAccessor(o);
	        			if(!accessor.preventExtensions(o)) {
	        				throw RuntimeUtil.typeError("Cannot prevent extensions on this object");
	        			}
	        		}
            		return o;
	        	}
	        	case seal -> {
	        		Object o = param(args, 0);
	        		// See freeze's matching guard above.
	        		if(RuntimeUtil.isObject(getEnvironment(), o)) {
	        			JSAccessor accessor = getEnvironment().getAccessor(o);
	        			// See freeze's matching preventExtensions call above.
	        			if(!accessor.preventExtensions(o)) {
	        				throw RuntimeUtil.typeError("Object.seal can't seal proxy");
	        			}
	            		accessor.seal(o);
	        		}
            		return o;
	        	}
	        	case setPrototypeOf -> {
            		Object arg0 = param(args, 0);
            		Object arg1 = param(args, 1);
	        		if(RuntimeUtil.isNullOrUndefined(arg0)) {
		        		throw RuntimeUtil.typeError("Object.setPrototypeOf called on null or undefined");
	        		}
	        		if(arg1!=null && !RuntimeUtil.isValidPrototype(getEnvironment(),arg1)) {
		        		throw RuntimeUtil.typeError("Object is not a valid prototype - JavaScript object");
            		}
	        		// Spec step 4: "If Type(O) is not Object, return O" - a
	        		// primitive receiver is a silent no-op, not a failure
	        		// (confirmed via setPrototypeOf/o-not-obj.js/-bigint.js:
	        		// Object.setPrototypeOf(3, null) must return 3 unchanged,
	        		// not throw).
	        		if(RuntimeUtil.isObject(getEnvironment(), arg0) && !RuntimeUtil.setPrototype(getEnvironment(), arg0, arg1)) {
		        		throw RuntimeUtil.typeError("Failed to set Object prototype");
	        		}
            		return arg0;
	        	}
            	case values -> {
	        		// Spec: "Let obj be ? ToObject(O)" - throws for null/undefined
	        		// (confirmed via values/... siblings of entries/exception-not-object-coercible.js).
	        		Object o = RuntimeUtil.toObject(getEnvironment(), param(args, 0));
	        		JSAccessor accessor = getEnvironment().getAccessor(o);
	        		JSArray array = JSArray.create(getEnvironment());
	        		// See entries' matching comment - the raw key list is
	        		// snapshotted up front, but each key's enumerable check
	        		// (and its value read) happens fresh as the loop reaches
	        		// it (confirmed via values/return-order.js/
	        		// getter-adding-key.js/getter-making-future-key-nonenumerable.js/
	        		// getter-removing-future-key.js).
	        		java.util.List<String> keys = new java.util.ArrayList<>();
	        		for(var it=accessor.ownStringEntries(o,false); it.hasNext(); ) {
	        			keys.add(it.next().getKey());
	        		}
	        		for(String key : keys) {
	        			PropertyDescriptor desc = accessor.getOwnPropertyDescriptor(o, key);
	        			if(desc==null || !desc.isEnumerable()) {
	        				continue;
	        			}
	        			array.arrayAdd(accessor.getProperty(o, key, RuntimeUtil.UNDEFINED));
	        		}
	        		return array;
            	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
