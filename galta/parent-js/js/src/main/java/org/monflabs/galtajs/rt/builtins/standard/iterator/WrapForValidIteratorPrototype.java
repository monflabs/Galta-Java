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
package org.monflabs.galtajs.rt.builtins.standard.iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;

/**
 * %WrapForValidIteratorPrototype%: the prototype of objects produced by
 * Iterator.from() when the argument doesn't already descend from
 * %Iterator.prototype%. Its own [[Prototype]] is %Iterator.prototype%.
 */
public class WrapForValidIteratorPrototype extends BasePrototype {

	public static WrapForValidIteratorPrototype get(JSEnvironment env) {
		WrapForValidIteratorPrototype proto = (WrapForValidIteratorPrototype)env.getRegisteredPrototype(WrapForValidIteratorPrototype.class);
		if(proto==null) {
			proto = new WrapForValidIteratorPrototype(env);
			env.registerPrototype(WrapForValidIteratorPrototype.class,proto);
		}
		return proto;
	}

	private WrapForValidIteratorPrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId.next,0));
		setOwnMethod(new Method(env,MethodId.return_,0));
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinIteratorPrototype.get(getEnvironment());
	}

	@Override
	public String getClassName() {
		return BuiltinIteratorConstructor.CLASSNAME;
	}

	private static enum MethodId {
		next("next"),
		return_("return"),
		;
		final String id;
		MethodId(String id) {
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
	    	if(!(obj instanceof BuiltinIteratorWrapper w)) {
	    		throw RuntimeUtil.typeError("Method %WrapForValidIteratorPrototype%.{0} called on incompatible receiver {1}", methodId.toString(), obj!=null?obj.getClass():"null");
	    	}
	    	JSEnvironment env = getEnvironment();
	    	switch(methodId) {
	    		case next-> {
	    			if(w.hasNext()) {
	    				return JSObject.of(env,"value",w.next(),"done",false);
	    			}
	    			return JSObject.of(env,"value",RuntimeUtil.UNDEFINED,"done",true);
	    		}
	    		case return_-> {
	    			Object wrapped = w.getWrapped();
	    			Object returnMethod = env.getAccessor(wrapped).getProperty(wrapped,"return",RuntimeUtil.UNDEFINED);
	    			if(RuntimeUtil.isNullOrUndefined(returnMethod)) {
	    				return JSObject.of(env,"value",RuntimeUtil.UNDEFINED,"done",true);
	    			}
	    			if(!(returnMethod instanceof Callable c)) {
	    				throw RuntimeUtil.typeError("return is not a function");
	    			}
	    			return c.call(wrapped,RuntimeUtil.EMPTY_PARAMS);
	    		}
	            default-> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	}
}
