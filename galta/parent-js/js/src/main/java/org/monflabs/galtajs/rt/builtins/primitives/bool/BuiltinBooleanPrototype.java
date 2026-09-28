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
package org.monflabs.galtajs.rt.builtins.primitives.bool;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitivePrototype;

/**
 * Eqv of the JavaScript Boolean prototype.
 */
public class BuiltinBooleanPrototype extends BasePrimitivePrototype {

	public static BuiltinBooleanPrototype get(JSEnvironment env) {
		BuiltinBooleanPrototype proto = (BuiltinBooleanPrototype)env.getRegisteredPrototype(BuiltinBooleanPrototype.class);
		if(proto==null) {
			proto = new BuiltinBooleanPrototype(env);
			env.registerPrototype(BuiltinBooleanPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinBooleanPrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.valueOf,0));
	}
	
	@Override
	public String getClassName() {
		return BuiltinBooleanConstructor.CLASSNAME;
	}

	@Override
	public Class<?> getNativeClass() {
		return Boolean.class;
	}
	
	private static enum MethodId {
		toString,
		valueOf,
	}
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	    	if(RuntimeUtil.isNullOrUndefined(obj)) {
	    		throw nullThis();
	    	}
	    	if(!(obj instanceof Boolean)) {
	    		// Specific cases
	    		if(obj instanceof BuiltinBooleanPrototype) {
		    		if(methodId==MethodId.valueOf) {
		    			return false;
		    		}
		    		if(methodId==MethodId.toString) {
		    			return "false";
		    		}
	    		}
	    		throw RuntimeUtil.typeError("Method Boolean.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}
	    	
			final Boolean _this = (Boolean)obj;
	    	
	    	switch(methodId) {
	        	case toString -> {
	        		return _this ? "true" : "false";
	        	}
	        	case valueOf -> {
	        		return _this ? Boolean.TRUE : Boolean.FALSE;
	        	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
