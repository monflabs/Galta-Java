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
package org.monflabs.galtajs.rt.builtins.standard.iterator;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 *
 */
public class BuiltinIteratorConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Iterator";

	public BuiltinIteratorConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinIteratorPrototype.get(env),0);
		setOwnMethod(new Method(env,MethodId.from,1));
		setOwnMethod(new Method(env,MethodId.concat,0));
		setOwnMethod(new Method(env,MethodId.zip,1));
		setOwnMethod(new Method(env,MethodId.zipKeyed,1));

		// %IteratorPrototype%.constructor / [Symbol.toStringTag]: per spec,
		// both are "weird" accessor properties (each internally described as
		// a SetterThatIgnoresPrototypeProperties pair), NOT ordinary data
		// properties - overrides the plain data property BaseConstructor's
		// own super(...) call above just installed for "constructor". The
		// getter unconditionally returns the fixed value regardless of
		// receiver; the setter, called on an arbitrary object THROUGH THE
		// PROTOTYPE CHAIN (e.g. `Object.create(Iterator.prototype).ctor =
		// x`, spec'd as if writing through a normal writable/configurable
		// own data property of that name), must still write onto the
		// RECEIVER (CreateDataPropertyOrThrow if it has no own property of
		// that name yet, else an ordinary Set) - but writing directly onto
		// %IteratorPrototype% ITSELF must always throw a TypeError, unlike a
		// real non-writable data property (which would silently no-op in
		// sloppy mode) - test262 built-ins/Iterator/prototype/constructor
		// and .../Symbol.toStringTag's prop-desc.js/weird-setter.js.
		BuiltinIteratorPrototype home = BuiltinIteratorPrototype.get(env);
		home.setOwnProperty("constructor", true, false,
				(t,k) -> this,
				(t,k,v) -> setThatIgnoresPrototypeProperties(env, home, t, "constructor", v));
		home.setOwnProperty(Symbol.TO_STRING_TAG, true, false,
				(t,k) -> CLASSNAME,
				(t,k,v) -> setThatIgnoresPrototypeProperties(env, home, t, Symbol.TO_STRING_TAG, v));
	}

	// SetterThatIgnoresPrototypeProperties(V, Home, P): shared setter
	// algorithm for both accessors above.
	private static boolean setThatIgnoresPrototypeProperties(JSEnvironment env, Object home, Object base, Object key, Object value) {
		if(!RuntimeUtil.isObject(env, base)) {
			throw RuntimeUtil.typeError("Cannot set property {0} on a non-object receiver", key);
		}
		if(base==home) {
			throw RuntimeUtil.typeError("Cannot set property {0} on {1}", key, home);
		}
		if(RuntimeUtil.getOwnPropertyDescriptor(env, base, key)==null) {
			env.getAccessor(base).setOwnProperty(base, key, value, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.STRICT, base);
		} else {
			RuntimeUtil.setProperty(env, base, key, value);
		}
		return true;
	}

	@Override
	public Class<?> getNativeClass() {
		return BuiltinIterator.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		// %Iterator% is an abstract base class: calling it directly (not via a
		// subclass's super()) must throw, but `class Foo extends Iterator {}`
		// must work - BuiltinClassConstructor.constructObject() always
		// re-parents the returned object to the subclass's own "prototype"
		// afterwards, so the exact prototype used here doesn't matter.
		if(topConstructor==this) {
			throw RuntimeUtil.typeError("Iterator cannot be created as is");
		}
		return applyNewTargetPrototype(JSObject.createWithPrototype(getEnvironment(), BuiltinIteratorPrototype.get(getEnvironment())), topConstructor);
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Iterator is not a function");
	}

	// GetIteratorFlattenable(obj, reject-primitives): if obj has a
	// Symbol.iterator method, call it (requiring an object result); otherwise
	// treat obj itself as the iterator directly (only valid if obj is an
	// Object - primitives are rejected here). Shared with IteratorZip (zip/
	// zipKeyed use this "reject-primitives" GetIteratorFlattenable semantics
	// for each element of their own "iterables" argument).
	static Object getIteratorFlattenable(JSEnvironment env, Object obj) {
		return getIteratorFlattenable(env, obj, false);
	}

	// iterateStringPrimitives: Iterator.from's own GetIteratorFlattenable(O,
	// iterate-string-primitives) call - a raw String primitive is let
	// through (its Symbol.iterator method is looked up/called via the
	// engine's String accessor, same as any other property access on a
	// string) instead of being rejected outright like every other primitive.
	// Confirmed via test262 Iterator/from/primitives.js: every primitive
	// EXCEPT a string must throw TypeError, `Iterator.from('string')` must
	// not.
	static Object getIteratorFlattenable(JSEnvironment env, Object obj, boolean iterateStringPrimitives) {
		if(!RuntimeUtil.isObject(env,obj) && !(iterateStringPrimitives && obj instanceof String)) {
			throw RuntimeUtil.typeError("Iterable element must be an object");
		}
		Object method = RuntimeUtil.getProperty(env, obj, Symbol.ITERATOR, RuntimeUtil.NOT_AVAILABLE);
		if(method==RuntimeUtil.NOT_AVAILABLE || RuntimeUtil.isNullOrUndefined(method)) {
			return obj;
		}
		if(!(method instanceof Callable c)) {
			throw RuntimeUtil.typeError("Symbol.iterator is not a function");
		}
		Object it = c.call(obj, RuntimeUtil.EMPTY_PARAMS);
		if(!RuntimeUtil.isObject(env,it)) {
			throw RuntimeUtil.typeError("Result of the Symbol.iterator method is not an object");
		}
		return it;
	}

	// Iterator.from(O): if the flattened iterator object already inherits
	// from %Iterator.prototype% (e.g. a real generator, or an existing
	// Iterator-based object), return it directly - no wrapping needed.
	// Otherwise wrap it in a %WrapForValidIteratorPrototype%-based object
	// that forwards next()/return() to the underlying object.
	static Object from(JSEnvironment env, Object o) {
		Object iterated = getIteratorFlattenable(env, o, true);
		Object iterProto = BuiltinIteratorPrototype.get(env);
		for(Object p=iterated; RuntimeUtil.isNotNullOrUndefined(p);) {
			if(p==iterProto) {
				return iterated;
			}
			p = env.getAccessor(p).getPrototype(p);
		}
		return new BuiltinIteratorWrapper(env, iterated);
	}

	private static enum MethodId {
		from,
		concat,
		zip,
		zipKeyed,
		;
		Object id;
		MethodId() {
			this.id = name();
		}
	}
	private static final class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
			JSEnvironment env = getEnvironment();
	        switch(methodId){
	        	case from -> {
	        		return BuiltinIteratorConstructor.from(env, param(args,0,RuntimeUtil.UNDEFINED));
	        	}
	        	case concat -> {
	        		List<Object> iterables = new ArrayList<>();
	        		for(Object a: args) {
	        			iterables.add(a);
	        		}
	        		return IteratorZip.concat(env, iterables);
	        	}
	        	case zip -> {
	        		Object iterables = param(args,0,RuntimeUtil.UNDEFINED);
	        		Object options = param(args,1,RuntimeUtil.UNDEFINED);
	        		return IteratorZip.zip(env, iterables, options);
	        	}
	        	case zipKeyed -> {
	        		Object iterables = param(args,0,RuntimeUtil.UNDEFINED);
	        		Object options = param(args,1,RuntimeUtil.UNDEFINED);
	        		return IteratorZip.zipKeyed(env, iterables, options);
	        	}
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	}
}
