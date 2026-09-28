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
package org.monflabs.galtajs.rt.builtins.standard.weakset;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * 
 */
public class BuiltinWeakSetConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "WeakSet";
	
	public BuiltinWeakSetConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinWeakSetPrototype.get(env),0);
		setOwnMethod(new Method(env,MethodId.species,0));
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BuiltinWeakSet.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		BuiltinWeakSet set = new BuiltinWeakSet();
		if(parameters.length>0) {
			Object p = parameters[0];
			if(RuntimeUtil.isNotNullOrUndefined(p)) {
				// Optimized for GaltaJS
				JSEnvironment env = getEnvironment();
				if(env.isOptimizedSetAndMapCtor()) {
					Iterator<Object> it = RuntimeUtil.valueIterator(env,p);
					try {
						while(it.hasNext()) {
							Object v = it.next();
							set.add(v);
						}
					} catch(Throwable t) {
						RuntimeUtil.iteratorCloseQuietly(env,it);
						throw t;
					}
				} else {
					Object add = env.getAccessor(set).getProperty(set, "add", null);
					if(add instanceof Callable cb) {
						Iterator<Object> it = RuntimeUtil.valueIterator(env,p);
						try {
							while(it.hasNext()) {
								Object v = it.next();
								cb.call(set,v);
							}
						} catch(Throwable t) {
							RuntimeUtil.iteratorCloseQuietly(env,it);
							throw t;
						}
					} else {
						throw RuntimeUtil.typeError("Set is missing add() method");
					}
				}
			}
		}
		return applyNewTargetPrototype(set, topConstructor);
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Constructor WeakSet requires 'new'");
	}
	
	private static enum MethodId {
		species(Symbol.SPECIES),
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
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
