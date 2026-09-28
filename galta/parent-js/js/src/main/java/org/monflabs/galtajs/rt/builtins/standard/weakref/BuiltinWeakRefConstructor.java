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
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * 
 */
public class BuiltinWeakRefConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "WeakRef";
	
	public BuiltinWeakRefConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinWeakRefPrototype.get(env),1);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return WeakReference.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		Object o = param(parameters, 0, RuntimeUtil.UNDEFINED);
		if(!RuntimeUtil.canBeHeldWeakly(getEnvironment(), o)) {
			throw RuntimeUtil.typeError("Invalid value used as a WeakRef target");
		}
		return applyNewTargetPrototype(new WeakReference<Object>(o), topConstructor);
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Constructor WeakRef requires 'new'");
	}
	
//	private static enum MethodId {
//		;
//		Object id;
//		MethodId() {
//			this.id = name();
//		}
//		MethodId(Symbol id) {
//			this.id = id;
//		}
//	}
//	private static final class Method extends BaseMethod {
//		private MethodId methodId;
//		
//		private Method(JSEnvironment env, MethodId methodId, int length) {
//			super(env,methodId.id,length);
//			this.methodId = methodId;
//		}
//		
//	    @Override
//		public Object call(final JSRuntimeContext context, final Object obj, final Object[] args) {
//	        switch(methodId){
//	            
//	            default -> {
//	    		    throw new IllegalStateException(); // Should never be here 
//	            }
//	        }
//	    }
//	}	
}
