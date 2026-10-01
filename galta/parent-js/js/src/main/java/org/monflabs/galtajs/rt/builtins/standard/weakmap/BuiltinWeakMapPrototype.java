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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Date prototype.
 */
public class BuiltinWeakMapPrototype extends BasePrototype {

	public static BuiltinWeakMapPrototype get(JSEnvironment env) {
		BuiltinWeakMapPrototype proto = (BuiltinWeakMapPrototype)env.getRegisteredPrototype(BuiltinWeakMapPrototype.class);
		if(proto==null) {
			proto = new BuiltinWeakMapPrototype(env);
			env.registerPrototype(BuiltinWeakMapPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinWeakMapPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinWeakMapConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.delete,1));
		setOwnMethod(new Method(env,MethodId.get,1));
		setOwnMethod(new Method(env,MethodId.getOrInsert,2));
		setOwnMethod(new Method(env,MethodId.getOrInsertComputed,2));
		setOwnMethod(new Method(env,MethodId.has,1));
		setOwnMethod(new Method(env,MethodId.set,2));
	}

	@Override
	public String getClassName() {
		return BuiltinWeakMapConstructor.CLASSNAME;
	}
	
	private static enum MethodId {
		delete,
		get,
		getOrInsert,
		getOrInsertComputed,
		has,
		set,
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
	    	if(!(obj instanceof BuiltinWeakMap)) {
	    		throw RuntimeUtil.typeError("Method WeakMap.prototype.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}

	    	// Current Object
			final BuiltinWeakMap _this = (BuiltinWeakMap)obj;			
	    	
	    	switch(methodId) {
	    		case delete-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(_this.has(k)) {
	    				_this.delete(k);
	    				return true;
	    			}
	    			return false;
	    		}
	    		case get-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			return _this.get(k);
	    		}
	    		case getOrInsert-> {
	    			JSEnvironment env = getEnvironment();
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			boolean valid;
	    			if(k instanceof Symbol sb) {
	    				valid = !sb.isGlobal();
	    			} else {
	    				valid = !RuntimeUtil.isPrimitiveValue(env, k);
	    			}
	    			if(!valid) {
	    				throw RuntimeUtil.typeError("Invalid key used in WeakMap");
	    			}
	    			Object v = param(args,1,RuntimeUtil.UNDEFINED);
	    			return _this.getOrInsert(k,v);
	    		}
	    		case getOrInsertComputed-> {
	    			JSEnvironment env = getEnvironment();
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			boolean valid;
	    			if(k instanceof Symbol sb) {
	    				valid = !sb.isGlobal();
	    			} else {
	    				valid = !RuntimeUtil.isPrimitiveValue(env, k);
	    			}
	    			if(!valid) {
	    				throw RuntimeUtil.typeError("Invalid key used in WeakMap");
	    			}
	    			Object v = param(args,1,null);
	    			if(v instanceof Callable cb && cb.isCallable()) {
	    				return _this.getOrInsertComputed(k,() -> cb.call(RuntimeUtil.UNDEFINED,k));
	    			} else {
	    				throw RuntimeUtil.typeError("Argument is not a callable");
	    			}
	    			
	    		}
	    		case has-> {
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
    				return _this.has(k);
	    		}
	    		case set-> {
	    			JSEnvironment env = getEnvironment();
	    			Object k = param(args,0,RuntimeUtil.UNDEFINED);
	    			boolean valid;
	    			if(k instanceof Symbol sb) {
	    				valid = !sb.isGlobal();
	    			} else {
	    				valid = !RuntimeUtil.isPrimitiveValue(env, k);
	    			}
	    			if(!valid) {
	    				throw RuntimeUtil.typeError("Invalid key used in WeakMap");
	    			}
	    			Object v = param(args,1,RuntimeUtil.UNDEFINED);
	    			_this.set(k,v);
	    			return _this;
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}