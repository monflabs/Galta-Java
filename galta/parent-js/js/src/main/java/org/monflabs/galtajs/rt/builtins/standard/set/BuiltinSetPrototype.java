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
package org.monflabs.galtajs.rt.builtins.standard.set;

import java.util.Iterator;
import java.util.Set;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.util.iterators.Iterators;

/**
 * Set prototype.
 */
public class BuiltinSetPrototype extends BasePrototype {

	public static BuiltinSetPrototype get(JSEnvironment env) {
		BuiltinSetPrototype proto = (BuiltinSetPrototype)env.getRegisteredPrototype(BuiltinSetPrototype.class);
		if(proto==null) {
			proto = new BuiltinSetPrototype(env);
			env.registerPrototype(BuiltinSetPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinSetPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinSetConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		
		setOwnMethod(new Method(env,MethodId.add,1));
		setOwnMethod(new Method(env,MethodId.clear,0));
		setOwnMethod(new Method(env,MethodId.delete,1));
		setOwnMethod(new Method(env,MethodId.difference,1));
		setOwnMethod(new Method(env,MethodId.entries,0));
		setOwnMethod(new Method(env,MethodId.forEach,1));
		setOwnMethod(new Method(env,MethodId.has,1));
		setOwnMethod(new Method(env,MethodId.intersection,1));
		setOwnMethod(new Method(env,MethodId.isDisjointFrom,1));
		setOwnMethod(new Method(env,MethodId.isSubsetOf,1));
		setOwnMethod(new Method(env,MethodId.isSupersetOf,1));
		// "values" is the canonical definition (its own .name is "values");
		// "keys" and Symbol.iterator are aliases of the SAME function object.
		setOwnMethod(new Method(env,MethodId.values,0));
		setOwnAlias(MethodId.values.id,MethodId.keys.id);
		setOwnAlias(MethodId.values.id,MethodId.iterator.id);
		setOwnMethod(new Method(env,MethodId.symmetricDifference,1));
		setOwnMethod(new Method(env,MethodId.union,1));
		
		setOwnProperty("size",true,false, 
				(t,k) -> asSet(t).size(), 
				null);
	}
	@SuppressWarnings("unchecked")
	private static Set<Object> asSet(Object o) {
    	if(o instanceof Set<?> bm) {
    		return (Set<Object>) bm;
    	}
    	throw RuntimeUtil.typeError("Property Set.size called on incompatible receiver {0}", o!=null?o.getClass():"null");
	}

	@Override
	public String getClassName() {
		return BuiltinSetConstructor.CLASSNAME;
	}

	// GetSetRecord (spec): [[Set]]/[[Size]]/[[Has]]/[[Keys]], with [[Size]]
	// fetched/validated (ToNumber, throw TypeError if NaN) exactly once up
	// front - even for methods (union/symmetricDifference) whose own
	// algorithm never branches on the size value, since a "size" getter can
	// have observable side effects that must not fire twice.
	private record SetRecord(SetLike setLike, long size) {}
	private static SetRecord getSetRecord(JSEnvironment env, Object p0) {
		SetLike s0 = RuntimeUtil.getSetLike(env,p0);
		return new SetRecord(s0, s0.jsSize());
	}

	private static Object normalizeZero(Object v) {
		if(v instanceof Double d && RuntimeUtil.isNegativeZero(d)) {
			return 0.0;
		}
		return v;
	}
	
	private static enum MethodId {
		add,
		clear,
		delete,
		difference,
		entries,
		forEach,
		has,
		intersection,
		isDisjointFrom,
		isSubsetOf,
		isSupersetOf,
		keys,
		symmetricDifference,
		union,
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
	    	if(!(obj instanceof Set<?>)) {
	    		throw RuntimeUtil.typeError("Method Set.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			@SuppressWarnings("unchecked")
			final Set<Object> _this = (Set<Object>)obj;			
	    	
	    	switch(methodId) {
	    		case add-> {
	    			_this.add(param(args,0,RuntimeUtil.UNDEFINED));
	    			return _this;
	    		}
	    		case clear-> {
	    			_this.clear();
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case delete-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(_this.contains(k)) {
	    				_this.remove(k);
	    				return true;
	    			}
	    			return false;
	    		}
	    		case difference-> {
	    			JSEnvironment env = getEnvironment();
	    			Object p0 = param(args,0);
    				SetRecord rec = getSetRecord(env,p0);
    				SetLike s0 = rec.setLike();
    				BuiltinSet set = new BuiltinSet(env);
    				for(Object v: _this) {
    					set.add(v);
    				}
    				long thisSize = _this.size();
    				long otherSize = rec.size();
    				if(thisSize<=otherSize) {
    					// Iterate `this` and query the argument's has() - cheaper
    					// when `this` is the smaller set.
    					for(Object v: _this) {
    						if(s0.jsHas(v)) {
    							set.remove(v);
    						}
    					}
    				} else {
    					// Iterate the argument's keys() instead - never calls
    					// has() at all in this branch (observable to set-like
    					// test objects that count/forbid has() calls).
    					Iterator<Object> keysIter = s0.jsKeys();
    					while(keysIter.hasNext()) {
    						Object next = normalizeZero(keysIter.next());
    						set.remove(next);
    					}
    				}
    				return set;
	    		}
	    		case entries-> {
	    			return new BuiltinSetIterator(getEnvironment(),
	    				Iterators.map(_this.iterator(), (e) -> {
	    					return JSArray.of(getEnvironment(),e,e);
	    				})
	    			);
	    		}
	    		case forEach-> {
                    Callable function = paramCallableNotNull(args, 0);
                    Object thisArg = param(args, 1, RuntimeUtil.UNDEFINED);  
    				_this.forEach( (k) -> {
    					function.call(thisArg,new Object[] {k,k,_this});
    				});
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case has-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			return _this.contains(k);
	    		}
	    		case intersection-> {
	    			JSEnvironment env = getEnvironment();
	    			BuiltinSet set = new BuiltinSet(env);
	    			Object p0 = param(args,0);
    				SetRecord rec = getSetRecord(env,p0);
    				SetLike s0 = rec.setLike();
    				long thisSize = _this.size();
    				long otherSize = rec.size();
    				if(thisSize<=otherSize) {
    					for(Object v: _this) {
    						if(s0.jsHas(v)) {
    							set.add(v);
    						}
    					}
    				} else {
    					Iterator<Object> keysIter = s0.jsKeys();
    					while(keysIter.hasNext()) {
    						Object next = normalizeZero(keysIter.next());
    						if(!set.contains(next) && _this.contains(next)) {
    							set.add(next);
    						}
    					}
    				}
    				return set;
	    		}
	    		case isDisjointFrom-> {
	    			JSEnvironment env = getEnvironment();
	    			Object p0 = param(args,0);
    				SetRecord rec = getSetRecord(env,p0);
    				SetLike s0 = rec.setLike();
    				long thisSize = _this.size();
    				long otherSize = rec.size();
    				if(thisSize<=otherSize) {
    					for(Object v: _this) {
    						if(s0.jsHas(v)) {
    							return false;
    						}
    					}
    				} else {
    					Iterator<Object> keysIter = s0.jsKeys();
    					while(keysIter.hasNext()) {
    						Object next = normalizeZero(keysIter.next());
    						if(_this.contains(next)) {
    							RuntimeUtil.iteratorClose(env,keysIter);
    							return false;
    						}
    					}
    				}
    				return true;
	    		}
	    		case isSubsetOf-> {
	    			JSEnvironment env = getEnvironment();
	    			Object p0 = param(args,0);
    				SetRecord rec = getSetRecord(env,p0);
    				SetLike s0 = rec.setLike();
    				long thisSize = _this.size();
    				long otherSize = rec.size();
    				if(thisSize>otherSize) {
    					return false;
    				}
    				for(Object v: _this) {
    					if(!s0.jsHas(v)) {
    						return false;
    					}
    				}
    				return true;
	    		}
	    		case isSupersetOf-> {
	    			JSEnvironment env = getEnvironment();
	    			Object p0 = param(args,0);
    				SetRecord rec = getSetRecord(env,p0);
    				SetLike s0 = rec.setLike();
    				long thisSize = _this.size();
    				long otherSize = rec.size();
    				if(thisSize<otherSize) {
    					return false;
    				}
    				Iterator<Object> keysIter = s0.jsKeys();
    				while(keysIter.hasNext()) {
    					Object next = normalizeZero(keysIter.next());
    					if(!_this.contains(next)) {
    						RuntimeUtil.iteratorClose(env,keysIter);
    						return false;
    					}
    				}
    				return true;
	    		}
	    		case keys, values -> {
	    			return new BuiltinSetIterator(getEnvironment(),_this.iterator());
	    		}
	    		case symmetricDifference-> {
	    			JSEnvironment env = getEnvironment();
	    			Object p0 = param(args,0);
    				SetLike s0 = getSetRecord(env,p0).setLike();
	    			BuiltinSet set = new BuiltinSet(env);
    				for(Object v: _this) {
    					set.add(v);
    				}
    				Iterator<Object> keysIter = s0.jsKeys();
    				while(keysIter.hasNext()) {
    					Object next = normalizeZero(keysIter.next());
    					if(_this.contains(next)) {
    						set.remove(next);
    					} else if(!set.contains(next)) {
    						set.add(next);
    					}
    				}
    				return set;
	    		}
	    		case union-> {
	    			JSEnvironment env = getEnvironment();
	    			Object p0 = param(args,0);
    				SetLike s0 = getSetRecord(env,p0).setLike();
	    			BuiltinSet set = new BuiltinSet(env);
    				for(Object v: _this) {
   						set.add(v);
    				}
    				Iterator<Object> keysIter = s0.jsKeys();
    				while(keysIter.hasNext()) {
    					Object next = normalizeZero(keysIter.next());
   						set.add(next);
    				}
    				return set;
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}