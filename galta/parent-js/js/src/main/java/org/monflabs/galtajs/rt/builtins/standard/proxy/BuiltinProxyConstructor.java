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
package org.monflabs.galtajs.rt.builtins.standard.proxy;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionNative;

/**
 * 
 */
public class BuiltinProxyConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Proxy";
	
	public BuiltinProxyConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinProxyPrototype.get(env),2);
		setOwnMethod(new Method(env,MethodId.revocable,2));
		// Proxy exotic objects have no [[Prototype]] internal slot that needs
		// initializing (a proxy instance's prototype comes entirely from its
		// wrapped target, via ProxyAccessor.getPrototype), so unlike every
		// other builtin constructor, %Proxy% itself has no own "prototype"
		// property at all - BaseConstructor's superclass ctor sets one up
		// unconditionally (non-configurable), so force it off here.
		deleteProperty(Constructor.PROTOTYPE, DESC_CHECK.NONE);
	}

	@Override
	public Class<?> getNativeClass() {
		return BuiltinProxy.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		if(parameters.length < 2) {
			throw RuntimeUtil.typeError("Proxy constructor requires at least 2 parameters");
		}
		Object target = parameters[0];
		if(RuntimeUtil.isPrimitiveValue(getEnvironment(), target)) {
			throw RuntimeUtil.typeError("Proxy target must be an object");
		} 
		Object handler = parameters[1];
		if(RuntimeUtil.isPrimitiveValue(getEnvironment(), handler)) {
			throw RuntimeUtil.typeError("Proxy handler must be an object");
		}

		BuiltinProxy proxy = new BuiltinProxy(getEnvironment(), target, handler);
		return proxy;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Constructor Proxy requires 'new'");
	}
	
	
	private static enum MethodId {
		revocable,
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
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case revocable -> {
	        		Object target = args[0];
	        		if(RuntimeUtil.isPrimitiveValue(getEnvironment(), target)) {
	        			throw RuntimeUtil.typeError("Proxy target must be an object");
	        		} 
	        		Object handler = args[1];
	        		if(RuntimeUtil.isPrimitiveValue(getEnvironment(), handler)) {
	        			throw RuntimeUtil.typeError("Proxy handler must be an object");
	        		}
	        		BuiltinProxy proxy = new BuiltinProxy(getEnvironment(), target, handler);
	        		// Spec: "A Proxy revocation function is an anonymous function" - name is "".
	        		BuiltinFunctionNative revoke = new BuiltinFunctionNative(getEnvironment(),"",0) {
	        			@Override
	        			public Object call(Object _this, Object[] parameters, Constructor newTarget) {
	        				proxy.revoke();
	        				return RuntimeUtil.UNDEFINED;
	        			}
	        		};
	        		JSObject revocableObj = JSObject.of(getEnvironment(),"proxy",
	        			proxy, "revoke", 
	        			revoke
	        		);
	        		return revocableObj;
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
