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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Date prototype.
 */
public class BuiltinWeakSetPrototype extends BasePrototype {

	public static BuiltinWeakSetPrototype get(JSEnvironment env) {
		BuiltinWeakSetPrototype proto = (BuiltinWeakSetPrototype)env.getRegisteredPrototype(BuiltinWeakSetPrototype.class);
		if(proto==null) {
			proto = new BuiltinWeakSetPrototype(env);
			env.registerPrototype(BuiltinWeakSetPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinWeakSetPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinWeakSetConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.add,1));
		setOwnMethod(new Method(env,MethodId.delete,1));
		setOwnMethod(new Method(env,MethodId.has,1));
	}

	@Override
	public String getClassName() {
		return BuiltinWeakSetConstructor.CLASSNAME;
	}
	
	private static enum MethodId {
		add,
		delete,
		has
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
	    	if(!(obj instanceof BuiltinWeakSet)) {
	    		throw RuntimeUtil.typeError("Method WeakSet.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			final BuiltinWeakSet _this = (BuiltinWeakSet)obj;			
	    	
	    	switch(methodId) {
	    		case add-> {
	    			JSEnvironment env = getEnvironment();
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			boolean valid;
	    			if(k instanceof Symbol sb) {
	    				valid = !sb.isGlobal();
	    			} else {
	    				valid = !RuntimeUtil.isPrimitiveValue(env, k);
	    			}
	    			if(!valid) {
	    				throw RuntimeUtil.typeError("Invalid value used in WeakSet");
	    			}
	    			_this.add(k);
	    			return _this;
	    		}
	    		case delete-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(_this.has(k)) {
	    				_this.delete(k);
	    				return true;
	    			}
	    			return false;
	    		}
	    		case has-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(!RuntimeUtil.isNullOrUndefined(k)) {
	    				return _this.has(k);
	    			}
	    			return false;
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}