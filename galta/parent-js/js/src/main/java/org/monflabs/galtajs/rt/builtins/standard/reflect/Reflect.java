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
package org.monflabs.galtajs.rt.builtins.standard.reflect;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * JSON handling.
 */
public class Reflect extends NativeObject {

	public static final String OBJECTNAME = "Reflect";

	public Reflect(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,OBJECTNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		
		setOwnMethod(new Method(env,MethodId.apply,3));
		setOwnMethod(new Method(env,MethodId.construct,2));
		setOwnMethod(new Method(env,MethodId.defineProperty,3));
		setOwnMethod(new Method(env,MethodId.deleteProperty,2));
		setOwnMethod(new Method(env,MethodId.get,2));
		setOwnMethod(new Method(env,MethodId.getOwnPropertyDescriptor,2));
		setOwnMethod(new Method(env,MethodId.getPrototypeOf,1));
		setOwnMethod(new Method(env,MethodId.has,2));
		setOwnMethod(new Method(env,MethodId.isExtensible,1));
		setOwnMethod(new Method(env,MethodId.ownKeys,1));
		setOwnMethod(new Method(env,MethodId.preventExtensions,1));
		setOwnMethod(new Method(env,MethodId.set,3));
		setOwnMethod(new Method(env,MethodId.setPrototypeOf,2));
	}
	
	@Override
	public String getClassName() {
		return OBJECTNAME;
	}
	
	private static enum MethodId {
		apply,
		construct,
		defineProperty,
		deleteProperty,
		get,
		getOwnPropertyDescriptor,
		getPrototypeOf,
		has,
		isExtensible,
		ownKeys,
		preventExtensions,
		set,
		setPrototypeOf,
	}
	
	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId) {
	        
            	case apply -> {
            		Object _target = param(args, 0 );
            		if(!(_target instanceof Callable)) {
						throw RuntimeUtil.typeError("Target must be a callable. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
					} 
            		Object _thisArgument = param(args, 1);
            		Object _argumentsList = param(args, 2);
            		JSArray _argumentsArray = RuntimeUtil.getArrayLike(getEnvironment(),_argumentsList,true);
            		return ((Callable)_target).call(_thisArgument, _argumentsArray.toArray());
            	}
            	case construct -> {
            		Object _target = param(args, 0 );
            		if(!(_target instanceof Constructor tc) || !tc.isConstructor()) {
						throw RuntimeUtil.typeError("Target must be a constructor. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
					}
            		Object _argumentsList = param(args, 1);
            		JSArray _argumentsArray = RuntimeUtil.getArrayLike(getEnvironment(),_argumentsList,true);
            		// newTarget's PRESENCE (not its value) decides defaulting: an
            		// explicit `null`/`undefined` newTarget argument must still be
            		// validated as a constructor and throw, unlike an omitted one.
            		Constructor _newTarget;
            		if(args.length>2) {
            			Object nt = param(args, 2);
            			if(!(nt instanceof Constructor ctor) || !ctor.isConstructor()) {
    						throw RuntimeUtil.typeError("Newtarget argument must be a constructor. {0}", RuntimeUtil.objectTypeName(getEnvironment(), nt));
    					}
            			_newTarget = ctor;
            		} else {
            			_newTarget = (Constructor)_target;
            		}

            		return ((Constructor)_target).constructObject( _argumentsArray.toArray(), _newTarget);
            	}
            	case defineProperty -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		// ToPropertyKey(propertyKey) must happen before validating
            		// attributes, so a poisoned key's exception takes precedence.
            		Object _propertyKey = RuntimeUtil.toPropertyKey(getEnvironment(), param(args, 1));
            		Object _attributes = param(args, 2);
            		if(!(_attributes instanceof JSObject)) {
						throw RuntimeUtil.typeError("Attributes must be an object {0}", RuntimeUtil.objectTypeName(getEnvironment(), _attributes));
					}
            		return RuntimeUtil.defineProperty(getEnvironment(), _target, _propertyKey, (JSObject)_attributes, DESC_CHECK.NO_EXCEPTION);
            	}
            	case deleteProperty -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		Object _propertyKey = param(args, 1);
            		// Reflect.deleteProperty always just reports success/failure, unlike
            		// the plain `delete` operator's DESC_CHECK.CHECK (which throws when
            		// the CALLING code happens to be strict mode).
            		return RuntimeUtil.deleteProperty(getEnvironment(), _target, _propertyKey, DESC_CHECK.NO_EXCEPTION);
            	}
            	case get -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		Object _propertyKey = param(args, 1);
            		Object _receiver = param(args, 2, _target);
            		if(_target==_receiver) {
                		return RuntimeUtil.getProperty(getEnvironment(),_target,_propertyKey,RuntimeUtil.UNDEFINED);
            		}
            		// Emulate the accessor...
            		return getProperty(_target,_propertyKey,RuntimeUtil.UNDEFINED,_receiver);
            	}
            	case getOwnPropertyDescriptor -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		Object _propertyKey = param(args, 1);
            		JSAccessor accessor = getEnvironment().getAccessor(_target);
            		PropertyDescriptor p = accessor.getOwnPropertyDescriptor(_target, _propertyKey);
            		return p!=null ? p.asJSObject(getEnvironment(),()->accessor.getOwnProperty(_target,_propertyKey,RuntimeUtil.NOT_AVAILABLE,_target)) : RuntimeUtil.UNDEFINED;
            	}
            	case getPrototypeOf -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		return RuntimeUtil.getPrototype(getEnvironment(), _target);
            	}
            	case has -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		Object _propertyKey = param(args, 1);
            		JSAccessor accessor = getEnvironment().getAccessor(_target);
            		return accessor.hasProperty(_target, _propertyKey);
            	}
            	case isExtensible -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		JSAccessor accessor = getEnvironment().getAccessor(_target);
            		return accessor.isExtensible(_target);
            	}
            	case ownKeys -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		JSAccessor accessor = getEnvironment().getAccessor(_target);
            		JSArray array = JSArray.create(getEnvironment());
            		// A single combined pass, not separate ownStringEntries()-then-
            		// ownSymbolEntries() calls: for a Proxy, [[OwnPropertyKeys]]'s
            		// result order (interleaved strings/symbols, whatever the trap
            		// returned) must be preserved verbatim - splitting into two
            		// calls always forced symbols after every string regardless of
            		// the trap's actual order.
            		for(Iterator<Entry<Object,Object>> it=accessor.ownEntries(_target,false); it.hasNext(); ) {
            			Entry<Object,Object> e = it.next();
						array.arrayAdd(e.getKey());
					}
            		return array;
            	}
            	case preventExtensions -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		JSAccessor accessor = getEnvironment().getAccessor(_target);
            		return accessor.preventExtensions(_target);
            	}
            	case set -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		Object _propertyKey = RuntimeUtil.toPropertyKey(getEnvironment(), param(args, 1));
            		// Per spec, Reflect.set(target, key, V, receiver) - V is
            		// an ordinary positional parameter, not specially
            		// required: omitting it is exactly as valid as omitting
            		// any other JS function argument (defaults to
            		// undefined), not a TypeError (confirmed via test262's
            		// own module-code/namespace/internals/set.js: `Reflect.
            		// set(ns, 'local1')` with no third argument).
            		Object _value = param(args, 2, RuntimeUtil.UNDEFINED);
            		Object _receiver = param(args, 3, _target);
            		// target.[[Set]](key,value,receiver): a plain virtual dispatch,
            		// receiver included - JSAccessor.setProperty(...,receiver) already
            		// implements OrdinarySet's receiver-aware algorithm generically
            		// (walking target's own descriptor chain, then applying to the
            		// RECEIVER), so any exotic target (a Proxy's "set" trap, or a
            		// TypedArray's Integer-Indexed [[Set]] short-circuit for a
            		// canonical-but-invalid numeric key) is honored correctly even
            		// when receiver differs from target - a hand-rolled reimplementation
            		// here would (and previously did) silently bypass those overrides.
            		JSAccessor accessor = getEnvironment().getAccessor(_target);
            		return accessor.setProperty(_target, _propertyKey, _value, null, DESC_CHECK.NO_EXCEPTION, _receiver);
            	}
            	case setPrototypeOf -> {
            		Object _target = param(args, 0 );
            		if(!RuntimeUtil.isObject(getEnvironment(), _target)) {
            			throw RuntimeUtil.typeError("Target must be an object. {0}", RuntimeUtil.objectTypeName(getEnvironment(), _target));
            		}
            		Object _prototype = param(args, 1);
            		return RuntimeUtil.setPrototype(getEnvironment(), _target, _prototype);
            	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
		private Object getProperty(Object _this, Object key, Object defaultValue, Object receiver) {
			JSAccessor acc = getEnvironment().getAccessor(_this); 
			Object v = acc.getOwnProperty(_this, key, RuntimeUtil.NOT_AVAILABLE, receiver);
			if(v!=RuntimeUtil.NOT_AVAILABLE) {
				return v;
			}
			Object p=acc.getPrototype(_this);
			while(p!=null) {
				if(p instanceof JSObject jo) { // Optimization
					v = jo.getOwnProperty(key, RuntimeUtil.NOT_AVAILABLE, receiver);
					if(v!=RuntimeUtil.NOT_AVAILABLE) {
						return v;
					}
					p = jo.getPrototype();
				} else {
					JSAccessor a = getEnvironment().getAccessor(p); 
					v = a.getOwnProperty(p, key, RuntimeUtil.NOT_AVAILABLE, receiver);
					if(v!=RuntimeUtil.NOT_AVAILABLE) {
						return v;
					}
					p = a.getPrototype(p);
				}
			}
			return defaultValue;
		}
	}
}