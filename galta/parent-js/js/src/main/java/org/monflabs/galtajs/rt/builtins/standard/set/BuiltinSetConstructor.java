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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * 
 */
public class BuiltinSetConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Set";
	
	public BuiltinSetConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinSetPrototype.get(env),0);

		// get [Symbol.species] () { return this; } - a getter-only accessor
		// (not a static value) so subclasses correctly return themselves.
		setOwnProperty(Symbol.SPECIES, true, false, (base,key) -> base, null);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BuiltinSet.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		JSEnvironment env = getEnvironment();
		BuiltinSet set = new BuiltinSet(env);
		if(parameters.length>0) {
			Object p = parameters[0];
			if(RuntimeUtil.isNotNullOrUndefined(p)) {
				// Optimized for GaltaJS
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
		throw RuntimeUtil.typeError("Constructor Set requires 'new'");
	}
}
