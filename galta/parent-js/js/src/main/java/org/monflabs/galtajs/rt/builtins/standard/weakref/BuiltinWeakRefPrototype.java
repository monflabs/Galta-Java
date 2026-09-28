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
package org.monflabs.galtajs.rt.builtins.standard.weakref;

import java.lang.ref.WeakReference;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Date prototype.
 */
public class BuiltinWeakRefPrototype extends BasePrototype {

	public static BuiltinWeakRefPrototype get(JSEnvironment env) {
		BuiltinWeakRefPrototype proto = (BuiltinWeakRefPrototype)env.getRegisteredPrototype(BuiltinWeakRefPrototype.class);
		if(proto==null) {
			proto = new BuiltinWeakRefPrototype(env);
			env.registerPrototype(BuiltinWeakRefPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinWeakRefPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinWeakRefConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.deref,0));
	}

	@Override
	public String getClassName() {
		return BuiltinWeakRefConstructor.CLASSNAME;
	}
	
	private static enum MethodId {
		deref,
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
	    	if(!(obj instanceof WeakReference<?>)) {
	    		throw RuntimeUtil.typeError("Method WeakRef.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			@SuppressWarnings("unchecked")
			final WeakReference<Object> _this = (WeakReference<Object>)obj;			
	    	
	    	switch(methodId) {
	    		case deref-> {
	    			// A collected target is undefined, not null
	    			Object target = _this.get();
	    			return target!=null ? target : RuntimeUtil.UNDEFINED;
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}