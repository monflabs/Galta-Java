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

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * 
 */
public class BuiltinMapConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Map";
	
	public BuiltinMapConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinMapPrototype.get(env),0);
		setOwnMethod(new Method(env,MethodId.groupBy,2));

		// get [Symbol.species] () { return this; } - a getter-only accessor
		// (not a static value) so subclasses correctly return themselves.
		setOwnProperty(Symbol.SPECIES, true, false, (base,key) -> base, null);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BuiltinMap.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		JSEnvironment env = getEnvironment();
		BuiltinMap map = new BuiltinMap(env);
		if(parameters.length>0) {
			Object p = parameters[0];
			if(RuntimeUtil.isNotNullOrUndefined(p)) {
				// Optimized for GaltaJS
				if(env.isOptimizedSetAndMapCtor()) {
					Iterator<Object> it = RuntimeUtil.valueIterator(env,p);
					try {
						while(it.hasNext()) {
							Object v = it.next();
							if(RuntimeUtil.isObject(env,v)) {
								JSAccessor a = env.getAccessor(v);
								Object key = a.getOwnProperty(v,0,RuntimeUtil.UNDEFINED,v);
								Object val = a.getOwnProperty(v,1,RuntimeUtil.UNDEFINED,v);
								map.put(key,val);
							} else {
								throw RuntimeUtil.typeError("Iterator value {0} is not an entry object",RuntimeUtil.objectTypeName(env, v));
							}
						}
					} catch(Throwable t) {
						RuntimeUtil.iteratorCloseQuietly(env,it);
						throw t;
					}
				} else {
					Object set = env.getAccessor(map).getProperty(map, "set", null);
					if(set instanceof Callable cb) {
						Iterator<Object> it = RuntimeUtil.valueIterator(env,p);
						try {
							while(it.hasNext()) {
								Object v = it.next();
								if(RuntimeUtil.isObject(env,v)) {
									JSAccessor a = env.getAccessor(v);
									Object key = a.getOwnProperty(v,0,RuntimeUtil.UNDEFINED,v);
									Object val = a.getOwnProperty(v,1,RuntimeUtil.UNDEFINED,v);
									cb.call(map,key,val);
								} else {
									throw RuntimeUtil.typeError("Iterator value {0} is not an entry object",RuntimeUtil.objectTypeName(env, v));
								}
							}
						} catch(Throwable t) {
							RuntimeUtil.iteratorCloseQuietly(env,it);
							throw t;
						}
					} else {
						throw RuntimeUtil.typeError("Map is missing set() method");
					}
				}
			}
		}
		return applyNewTargetPrototype(map, topConstructor);
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Constructor Map requires 'new'");
	}
	
	private static enum MethodId {
		groupBy,
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case groupBy -> {
	        		JSEnvironment env = getEnvironment();
					Object it = param(args,0,RuntimeUtil.UNDEFINED);
	    			Object callback = param(args,1,RuntimeUtil.UNDEFINED);
	    			if(!(callback instanceof Callable cb)) {
	    				throw RuntimeUtil.typeError("Callback is not callable");
	    			}
	        		BuiltinMap res = new BuiltinMap(env);
					if(RuntimeUtil.isNotNullOrUndefined(it)) {
						Iterator<Object> it2 = RuntimeUtil.valueIterator(env,it);
						for(int idx=0; it2.hasNext(); idx++) {
							Object v = it2.next();
							Object grp = cb.call(RuntimeUtil.UNDEFINED,new Object[]{v,idx});
							JSArray a = (JSArray)res.get(grp);
							if(a==null) {
								a = JSArray.create(getEnvironment());
								res.put(grp,a);
							}
							a.arrayAdd(v);
						}
					}
	        		return res;
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
