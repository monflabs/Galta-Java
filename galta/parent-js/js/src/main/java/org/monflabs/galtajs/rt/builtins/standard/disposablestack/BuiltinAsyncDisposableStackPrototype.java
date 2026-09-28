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
 * AsyncDisposableStack.prototype - explicit resource management proposal.
 */
public class BuiltinAsyncDisposableStackPrototype extends BasePrototype {

	public static BuiltinAsyncDisposableStackPrototype get(JSEnvironment env) {
		BuiltinAsyncDisposableStackPrototype proto = (BuiltinAsyncDisposableStackPrototype)env.getRegisteredPrototype(BuiltinAsyncDisposableStackPrototype.class);
		if(proto==null) {
			proto = new BuiltinAsyncDisposableStackPrototype(env);
			env.registerPrototype(BuiltinAsyncDisposableStackPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinAsyncDisposableStackPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinAsyncDisposableStackConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.use,1));
		setOwnMethod(new Method(env,MethodId.adopt,2));
		setOwnMethod(new Method(env,MethodId.defer,1));
		setOwnMethod(new Method(env,MethodId.disposeAsync,0));
		setOwnAlias(MethodId.disposeAsync.id,MethodId.symbolAsyncDispose.id);
		setOwnMethod(new Method(env,MethodId.move,0));

		setOwnProperty("disposed",true,false,
				(t,k) -> asStack(t).isDisposed(),
				null);
	}

	@Override
	public String getClassName() {
		return BuiltinAsyncDisposableStackConstructor.CLASSNAME;
	}

	private static BuiltinAsyncDisposableStack asStack(Object o) {
		if(!(o instanceof BuiltinAsyncDisposableStack s)) {
			throw RuntimeUtil.typeError("Method AsyncDisposableStack.prototype called on incompatible receiver {0}", o!=null?o.getClass():"null");
		}
		return s;
	}

	private static enum MethodId {
		use,
		adopt,
		defer,
		disposeAsync,
		move,
		symbolAsyncDispose(Symbol.ASYNC_DISPOSE),
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
	    	JSEnvironment env = getEnvironment();

	    	// disposeAsync/@@asyncDispose alone always returns a Promise, even
	    	// when `obj` is the wrong receiver type - RequireInternalSlot's
	    	// failure is funnelled into a REJECTED promise (spec step 3), not a
	    	// synchronous throw, unlike every other method here.
	    	if(methodId==MethodId.disposeAsync || methodId==MethodId.symbolAsyncDispose) {
	    		JSRuntimeContext ctx = JSRuntimeContext.get();
	    		if(!(obj instanceof BuiltinAsyncDisposableStack stack)) {
	    			return ctx.getGlobalContext().getExecutor().asyncFunction(() -> {
	    				throw RuntimeUtil.typeError("Method AsyncDisposableStack.prototype.disposeAsync called on incompatible receiver {0}", obj!=null?obj.getClass():"null");
	    			});
	    		}
	    		if(stack.isDisposed()) {
	    			return ctx.getGlobalContext().getExecutor().asyncFunction(() -> RuntimeUtil.UNDEFINED);
	    		}
	    		// [[AsyncDisposableState]] is set to disposed synchronously,
	    		// before the actual (possibly async) disposal work runs - a
	    		// second disposeAsync() call made while the first is still in
	    		// flight must see the stack as already disposed.
	    		stack.setDisposed(true);
	    		// runAsyncBody(), not asyncFunction(): dispose() below may call
	    		// RuntimeUtil.await_() internally (per resource, for an async
	    		// dispose method) - needs the same spec-correct synchronous-
	    		// prefix/tick-ordering semantics a real `await` inside a JS
	    		// async function gets, not asyncFunction()'s fire-and-forget
	    		// submit (see JSExecutor.runAsyncBody()'s own doc, and
	    		// KnownGaps.md's "Top-level-await tick ordering" entry - this is
	    		// the disposal-callback ordering gap it names as the same
	    		// underlying issue, reached via a different call site).
	    		return ctx.getGlobalContext().getExecutor().runAsyncBody(() -> {
	    			Throwable toThrow = DisposeResourcesUtil.dispose(JSRuntimeContext.get(),takeResources(stack.getResources()),null);
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
	    		});
	    	}

	    	BuiltinAsyncDisposableStack stack = asStack(obj);
	    	switch(methodId) {
	    		case use -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot use a disposed AsyncDisposableStack");
	    			}
	    			Object value = param(args,0,RuntimeUtil.UNDEFINED);
	    			// CreateDisposableResource's own null/undefined branch (spec
	    			// 27.4.4 AddDisposableResource step 1a's early-return-unused
	    			// only applies to the SYNC hint - here, an async hint, a
	    			// null/undefined value still gets a resource record, just
	    			// with DisposeMethod undefined) - a no-op DISPOSAL, but still
	    			// present on the stack, so DisposeResources' own needsAwait
	    			// bookkeeping sees it (see DisposeResourcesUtil.dispose()'s
	    			// own doc/explicit-await-for-{null,undefined}.js: even an
	    			// all-null-valued AsyncDisposableStack must still take the
	    			// SAME extra microtask tick disposing a real resource would).
	    			if(!RuntimeUtil.isNullOrUndefined(value)) {
	    				if(!RuntimeUtil.isObject(env,value)) {
	    					throw RuntimeUtil.typeError("Cannot dispose non-object value, {0}", RuntimeUtil.objectTypeName(env,value));
	    				}
	    				Callable m = DisposeResourcesUtil.resolveDisposeMethod(env,value,true);
	    				stack.getResources().add(new DisposableResource(value,m,true));
	    			} else {
	    				stack.getResources().add(new DisposableResource(value,null,true));
	    			}
	    			return value;
	    		}
	    		case adopt -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot adopt a value into a disposed AsyncDisposableStack");
	    			}
	    			Object value = param(args,0,RuntimeUtil.UNDEFINED);
	    			Object onDispose = param(args,1,RuntimeUtil.UNDEFINED);
	    			if(!(onDispose instanceof Callable cb)) {
	    				throw RuntimeUtil.typeError("onDispose is not a function");
	    			}
	    			Callable closure = (_this,_args) -> cb.call(RuntimeUtil.UNDEFINED,value);
	    			stack.getResources().add(new DisposableResource(RuntimeUtil.UNDEFINED,closure,true));
	    			return value;
	    		}
	    		case defer -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot defer onto a disposed AsyncDisposableStack");
	    			}
	    			Object onDispose = param(args,0,RuntimeUtil.UNDEFINED);
	    			if(!(onDispose instanceof Callable cb)) {
	    				throw RuntimeUtil.typeError("onDispose is not a function");
	    			}
	    			stack.getResources().add(new DisposableResource(RuntimeUtil.UNDEFINED,cb,true));
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case move -> {
	    			if(stack.isDisposed()) {
	    				throw RuntimeUtil.referenceError("Cannot move a disposed AsyncDisposableStack");
	    			}
	    			BuiltinAsyncDisposableStack newStack = new BuiltinAsyncDisposableStack(env);
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
