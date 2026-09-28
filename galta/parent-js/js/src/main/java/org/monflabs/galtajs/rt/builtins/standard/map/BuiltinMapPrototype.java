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
package org.monflabs.galtajs.rt.builtins.standard.map;

import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.util.iterators.Iterators;

/**
 * Date prototype.
 */
public class BuiltinMapPrototype extends BasePrototype {

	public static BuiltinMapPrototype get(JSEnvironment env) {
		BuiltinMapPrototype proto = (BuiltinMapPrototype)env.getRegisteredPrototype(BuiltinMapPrototype.class);
		if(proto==null) {
			proto = new BuiltinMapPrototype(env);
			env.registerPrototype(BuiltinMapPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinMapPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinMapConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.clear,0));
		setOwnMethod(new Method(env,MethodId.delete,1));
		setOwnMethod(new Method(env,MethodId.entries,0));
		setOwnAlias(MethodId.entries.id,MethodId.iterator.id);
		setOwnMethod(new Method(env,MethodId.forEach,1));
		setOwnMethod(new Method(env,MethodId.get,1));
		setOwnMethod(new Method(env,MethodId.getOrInsert,2));
		setOwnMethod(new Method(env,MethodId.getOrInsertComputed,2));
		setOwnMethod(new Method(env,MethodId.has,1));
		setOwnMethod(new Method(env,MethodId.keys,0));
		setOwnMethod(new Method(env,MethodId.set,2));
		setOwnMethod(new Method(env,MethodId.values,0));
		
		setOwnProperty("size",true,false, 
				(t,k) -> asMap(t).size(), 
				null);
	}
	
	// CanonicalizeKeyedCollectionKey: -0 is normalized to +0 (used by
	// getOrInsert/getOrInsertComputed - Map's own set()/get()/has() already
	// treat -0 and +0 as the same key via SameValueZero, but the CALLER-
	// VISIBLE key - what's passed to the callback, and the -0-vs-+0 identity
	// of a newly inserted key - must itself be the canonical +0).
	private static Object canonicalKey(Object key) {
		return RuntimeUtil.canonicalizeKeyedCollectionKey(key);
	}

	@SuppressWarnings("unchecked")
	private static Map<Object,Object> asMap(Object o) {
    	if(o instanceof Map bm) {
    		return bm;
    	}
    	throw RuntimeUtil.typeError("Property Map.size called on incompatible receiver {0}", o!=null?o.getClass():"null");
	}

	@Override
	public String getClassName() {
		return BuiltinMapConstructor.CLASSNAME;
	}
	
	private static enum MethodId {
		clear,
		delete,
		entries,
		forEach,
		get,
		getOrInsert,
		getOrInsertComputed,
		has,
		keys,
		set,
		values,

		iterator(Symbol.ITERATOR),
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
	    	// We exclude JS Object from here
	    	if(!(obj instanceof Map<?,?>) || obj instanceof JSObjectInternal) {
	    		throw RuntimeUtil.typeError("Method Map.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			@SuppressWarnings("unchecked")
			final Map<Object,Object> _this = (Map<Object,Object>)obj;			
	    	
	    	switch(methodId) {
	    		case clear-> {
	    			_this.clear();
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case delete-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(_this.containsKey(k)) {
	    				_this.remove(k);
	    				return true;
	    			}
	    			return false;
	    		}
	    		case entries-> {
	    			return new BuiltinMapIterator(
	    					getEnvironment(),
		    				Iterators.map(_this.entrySet().iterator(), (e) -> {
		    					return JSArray.of(getEnvironment(),e.getKey(),e.getValue());
		    				})
	    			);
	    		}
	    		case forEach-> {
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
    				_this.forEach( (k,v) -> {
    					function.call(thisArg,new Object[] {v,k,_this});
    				});
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case get-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			return _this.getOrDefault(k,RuntimeUtil.UNDEFINED);
	    		}
	    		case getOrInsert-> {
	    			Object k = canonicalKey(param(args,0,RuntimeUtil.UNDEFINED));
	    			if(_this.containsKey(k)) {
	    				return _this.get(k);
	    			}
	    			Object v = param(args,1,RuntimeUtil.UNDEFINED);
	    			_this.put(k,v);
	    			return v;
	    		}
	    		case getOrInsertComputed-> {
	    			// IsCallable(callbackfn) is validated before the key lookup,
	    			// even if the key is already present (and the callback would
	    			// therefore never actually be invoked).
	    			Object cbArg = param(args,1,null);
	    			if(!(cbArg instanceof Callable cb)) {
	    				throw RuntimeUtil.typeError("Argument is not a callable");
	    			}
	    			Object k = canonicalKey(param(args,0,RuntimeUtil.UNDEFINED));
	    			if(_this.containsKey(k)) {
	    				return _this.get(k);
	    			}
	    			// If the callback throws, nothing is inserted for `k` (whatever
	    			// state the callback itself left the map in - even for `k`
	    			// itself - is left untouched); if it succeeds, its result
	    			// unconditionally overwrites any mutation the callback made
	    			// for `k` while running.
	    			Object v = cb.call(RuntimeUtil.UNDEFINED, new Object[] {k});
	    			_this.put(k,v);
	    			return v;
	    		}
	    		case has-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			return _this.containsKey(k);
	    		}
	    		case keys-> {
	    			return new BuiltinMapIterator(
	    					getEnvironment(),
	    					_this.keySet().iterator()
		    			);
	    		}
	    		case set-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			Object v = param(args,1,RuntimeUtil.UNDEFINED);
	    			_this.put(k,v);
	    			return _this;
	    		}
	    		case values-> {
	    			return new BuiltinMapIterator(
	    					getEnvironment(),
	    					_this.values().iterator()
		    			);
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}