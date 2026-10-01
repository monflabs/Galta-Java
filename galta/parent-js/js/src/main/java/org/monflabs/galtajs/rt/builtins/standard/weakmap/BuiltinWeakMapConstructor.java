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
package org.monflabs.galtajs.rt.builtins.standard.weakmap;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * 
 */
public class BuiltinWeakMapConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "WeakMap";
	
	public BuiltinWeakMapConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinWeakMapPrototype.get(env),0);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BuiltinWeakMap.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		BuiltinWeakMap map = new BuiltinWeakMap();
		if(parameters.length>0) {
			Object p = parameters[0];
			if(RuntimeUtil.isNotNullOrUndefined(p)) {
				// Optimized for GaltaJS
				if(getEnvironment().isOptimizedSetAndMapCtor()) {
					Iterator<Object> it = RuntimeUtil.valueIterator(getEnvironment(),p);
					try {
						while(it.hasNext()) {
							Object v = it.next();
							if(RuntimeUtil.isObject(getEnvironment(),v)) {
								JSAccessor a = getEnvironment().getAccessor(v);
								Object key = a.getOwnProperty(v,0,RuntimeUtil.UNDEFINED,v);
								Object val = a.getOwnProperty(v,1,RuntimeUtil.UNDEFINED,v);
								map.set(key,val);
							} else {
								throw RuntimeUtil.typeError("Iterator value {0} is not an entry object",RuntimeUtil.objectTypeName(getEnvironment(), v));
							}
						}
					} catch(Throwable t) {
						RuntimeUtil.iteratorCloseQuietly(getEnvironment(),it);
						throw t;
					}
				} else {
					Object set = getEnvironment().getAccessor(map).getProperty(map, "set", null);
					if(set instanceof Callable cb && cb.isCallable()) {
						Iterator<Object> it = RuntimeUtil.valueIterator(getEnvironment(),p);
						try {
							while(it.hasNext()) {
								Object v = it.next();
								if(RuntimeUtil.isObject(getEnvironment(),v)) {
									JSAccessor a = getEnvironment().getAccessor(v);
									Object key = a.getOwnProperty(v,0,RuntimeUtil.UNDEFINED,v);
									Object val = a.getOwnProperty(v,1,RuntimeUtil.UNDEFINED,v);
									cb.call(map,key,val);
								} else {
									throw RuntimeUtil.typeError("Iterator value {0} is not an entry object",RuntimeUtil.objectTypeName(getEnvironment(), v));
								}
							}
						} catch(Throwable t) {
							RuntimeUtil.iteratorCloseQuietly(getEnvironment(),it);
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
		throw RuntimeUtil.typeError("Constructor WeakMap requires 'new'");
	}
}
