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
package org.monflabs.galtajs.rt.builtins.primitives.symbol;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;

/**
 * Symbol prototype.
 */
public class BuiltinSymbolPrototype extends BasePrototype {

	public static BuiltinSymbolPrototype get(JSEnvironment env) {
		BuiltinSymbolPrototype proto = (BuiltinSymbolPrototype)env.getRegisteredPrototype(BuiltinSymbolPrototype.class);
		if(proto==null) {
			proto = new BuiltinSymbolPrototype(env);
			env.registerPrototype(BuiltinSymbolPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinSymbolPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinSymbolConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.valueOf,0));

		setOwnMethod(new Method(env,MethodId.toPrimitive,1),PropertyDescriptor.DESC_PROP_TOPRIMITIVE);
		
		setOwnProperty("description",true,false, 
			(t,k) -> {
				if(t instanceof Symbol sy) {
					return sy.getDescription();
				}
		    	throw RuntimeUtil.typeError("Property Symbol.description called on incompatible receiver {0}", t!=null?t.getClass():"null");
			}, 
			null
		);
	}
	
	private static enum MethodId {
		toString,
		valueOf,
		
		toPrimitive(Symbol.TO_PRIMITIVE),
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
		protected Object invoke(final Object obj, final Object[] args) {
	    	if(!(obj instanceof Symbol)) {
	    		throw RuntimeUtil.typeError("Method Symbol.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			final Symbol _this = (Symbol)obj;			
	    	
	    	switch(methodId){
	    		case toString-> {
	    			return _this.toString();
	    		}
	    		case valueOf-> {
	    			return _this.getPrimitive();
	    		}

	    		//
	    		// Symbols
	    		//
	    		case toPrimitive-> {
	    			return _this.getPrimitive();
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}