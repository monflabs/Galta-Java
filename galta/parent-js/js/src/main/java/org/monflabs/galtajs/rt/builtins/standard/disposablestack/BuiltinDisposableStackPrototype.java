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
package org.monflabs.galtajs.rt.builtins.standard.disposablestack;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.DisposableResource;
import org.monflabs.galtajs.rt.DisposeResourcesUtil;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * DisposableStack.prototype - explicit resource management proposal.
 */
public class BuiltinDisposableStackPrototype extends BasePrototype {

	public static BuiltinDisposableStackPrototype get(JSEnvironment env) {
		BuiltinDisposableStackPrototype proto = (BuiltinDisposableStackPrototype)env.getRegisteredPrototype(BuiltinDisposableStackPrototype.class);
		if(proto==null) {
			proto = new BuiltinDisposableStackPrototype(env);
			env.registerPrototype(BuiltinDisposableStackPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinDisposableStackPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinDisposableStackConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.use,1));
		setOwnMethod(new Method(env,MethodId.adopt,2));
		setOwnMethod(new Method(env,MethodId.defer,1));
		setOwnMethod(new Method(env,MethodId.dispose,0));
		setOwnAlias(MethodId.dispose.id,MethodId.symbolDispose.id);
		setOwnMethod(new Method(env,MethodId.move,0));

		setOwnProperty("disposed",true,false,
				(t,k) -> asStack(t).isDisposed(),
				null);
	}

	@Override
	public String getClassName() {
		return BuiltinDisposableStackConstructor.CLASSNAME;
	}

	private static BuiltinDisposableStack asStack(Object o) {
		if(!(o instanceof BuiltinDisposableStack s)) {
			throw RuntimeUtil.typeError("Method DisposableStack.prototype called on incompatible receiver {0}", o!=null?o.getClass():"null");
		}
		return s;
	}

	private static enum MethodId {
		use,
		adopt,
		defer,
		dispose,
		move,
		symbolDispose(Symbol.DISPOSE),
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
	    	JSEnvironment env = getEnvironment();
	    	BuiltinDisposableStack stack = asStack(obj);

	    	switch(methodId) {
	    		case use -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot use a disposed DisposableStack");
	    			}
	    			Object value = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(!RuntimeUtil.isNullOrUndefined(value)) {
	    				if(!RuntimeUtil.isObject(env,value)) {
	    					throw RuntimeUtil.typeError("Cannot dispose non-object value, {0}", RuntimeUtil.objectTypeName(env,value));
	    				}
	    				Callable m = DisposeResourcesUtil.resolveDisposeMethod(env,value,false);
	    				stack.getResources().add(new DisposableResource(value,m,false));
	    			}
	    			return value;
	    		}
	    		case adopt -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot adopt a value into a disposed DisposableStack");
	    			}
	    			Object value = param(args,0,RuntimeUtil.UNDEFINED);
	    			Object onDispose = param(args,1,RuntimeUtil.UNDEFINED);
	    			if(!(onDispose instanceof Callable cb)) {
	    				throw RuntimeUtil.typeError("onDispose is not a function");
	    			}
	    			Callable closure = (_this,_args) -> cb.call(RuntimeUtil.UNDEFINED,value);
	    			stack.getResources().add(new DisposableResource(RuntimeUtil.UNDEFINED,closure,false));
	    			return value;
	    		}
	    		case defer -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot defer onto a disposed DisposableStack");
	    			}
	    			Object onDispose = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(!(onDispose instanceof Callable cb)) {
	    				throw RuntimeUtil.typeError("onDispose is not a function");
	    			}
	    			stack.getResources().add(new DisposableResource(RuntimeUtil.UNDEFINED,cb,false));
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case dispose, symbolDispose -> {
	    			if(stack.isDisposed()) {
	    				return RuntimeUtil.UNDEFINED;
	    			}
	    			stack.setDisposed(true);
	    			JSRuntimeContext ctx = JSRuntimeContext.get();
	    			Throwable toThrow = DisposeResourcesUtil.dispose(ctx,takeResources(stack.getResources()),null);
	    			if(toThrow!=null) {
	    				if(toThrow instanceof RuntimeException re) {
	    					throw re;
	    				}
	    				if(toThrow instanceof Error e) {
	    					throw e;
	    				}
	    				throw new RuntimeException(toThrow);
	    			}
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case move -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot move a disposed DisposableStack");
	    			}
	    			BuiltinDisposableStack newStack = new BuiltinDisposableStack(env);
	    			newStack.getResources().addAll(stack.getResources());
	    			stack.getResources().clear();
	    			stack.setDisposed(true);
	    			return newStack;
	    		}
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }
	}

	// The spec empties the stack once disposed: keep nothing reachable from it
	private static java.util.List<org.monflabs.galtajs.rt.DisposableResource> takeResources(java.util.List<org.monflabs.galtajs.rt.DisposableResource> resources) {
		java.util.List<org.monflabs.galtajs.rt.DisposableResource> copy = new java.util.ArrayList<>(resources);
		resources.clear();
		return copy;
	}
}
