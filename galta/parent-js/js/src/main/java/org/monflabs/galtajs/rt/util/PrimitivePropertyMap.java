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
package org.monflabs.galtajs.rt.util;


import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;

public class PrimitivePropertyMap extends ConcurrentWeakIdentitytMap<Object, JSObjectImpl>{

	private JSEnvironment env;
	// The JSEnvironment.PROPERTIES_* kind of values this map is for
	private final int kind;
	
	public PrimitivePropertyMap(JSEnvironment env) {
		this(env, -1);
    }

	public PrimitivePropertyMap(JSEnvironment env, int kind) {
		this.env = env;
		this.kind = kind;
	}

	// A value (a boxed primitive, a Java object) boxed or given a prototype
	// by another realm linked to this one keeps that realm's state: a miss
	// here looks in the same map of the linked realms
	// (JSEnvironment.linkRealms())
	@Override
	public JSObjectImpl get(Object key) {
		JSObjectImpl v = super.get(key);
		if(v==null && kind>=0) {
			v = env.findInLinkedRealms(kind, key);
		}
		return v;
	}

	@Override
	public boolean containsKey(Object key) {
		return get(key)!=null;
	}

	@Override
	public JSObjectImpl getOrCreate(Object key, java.util.function.Supplier<JSObjectImpl> supplier) {
		if(kind>=0 && super.get(key)==null) {
			JSObjectImpl v = env.findInLinkedRealms(kind, key);
			if(v!=null) {
				return v;
			}
		}
		return super.getOrCreate(key, supplier);
	}

	// This realm's own entry only
	public JSObjectImpl getLocal(Object key) {
		return super.get(key);
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}
	
    public void create(Object key, Object prototype) {
        create(key,() -> JSObject.createWithPrototype(env,prototype));
    }
}