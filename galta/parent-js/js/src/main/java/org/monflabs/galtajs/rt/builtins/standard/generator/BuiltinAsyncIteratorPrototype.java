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
package org.monflabs.galtajs.rt.builtins.standard.generator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionNative;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromiseConstructor;

// %AsyncIteratorPrototype% - deliberately minimal: its ONLY own property is
// [Symbol.asyncIterator] () { return this; } (per spec). Inserted as the
// [[Prototype]] of %AsyncGeneratorPrototype% (see
// BuiltinAsyncGeneratorPrototype.getDefaultPrototype()) - previously that
// chain went straight to Object.prototype (a deliberate choice to avoid
// leaking the SYNC %IteratorPrototype%/%IteratorHelperPrototype% hierarchy's
// map/filter/take/drop/... methods onto an async generator), but a real,
// separate %AsyncIteratorPrototype% carrying nothing but @@asyncIterator
// doesn't have that problem - it adds exactly the one property spec
// requires, nothing else.
public class BuiltinAsyncIteratorPrototype extends BasePrototype {

	public static BuiltinAsyncIteratorPrototype get(JSEnvironment env) {
		BuiltinAsyncIteratorPrototype proto = (BuiltinAsyncIteratorPrototype)env.getRegisteredPrototype(BuiltinAsyncIteratorPrototype.class);
		if(proto==null) {
			proto = new BuiltinAsyncIteratorPrototype(env);
			env.registerPrototype(BuiltinAsyncIteratorPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinAsyncIteratorPrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new AsyncIteratorMethod(env));
		setOwnMethod(new AsyncDisposeMethod(env));
	}

	private static final class AsyncIteratorMethod extends BaseMethod {
		private AsyncIteratorMethod(JSEnvironment env) {
			super(env, Symbol.ASYNC_ITERATOR, 0);
		}
		@Override
		protected Object invoke(Object _this, Object[] args) {
			return _this;
		}
	}

	// %AsyncIteratorPrototype% [ @@asyncDispose ] ( ): GetMethod(O,"return");
	// if absent, resolve undefined; else call it, PromiseResolve() the
	// result, and map its fulfillment to undefined (rejection propagates
	// unchanged) - never throws synchronously, always returns a Promise
	// (any synchronous failure reading/calling "return" rejects instead).
	private static final class AsyncDisposeMethod extends BaseMethod {
		private AsyncDisposeMethod(JSEnvironment env) {
			super(env, Symbol.ASYNC_DISPOSE, 0);
		}
		@Override
		protected Object invoke(Object _this, Object[] args) {
			JSEnvironment env = getEnvironment();
			Object returnMethod;
			try {
				returnMethod = RuntimeUtil.getProperty(env, _this, "return");
				if(returnMethod!=null && returnMethod!=RuntimeUtil.UNDEFINED && !(returnMethod instanceof Callable)) {
					throw RuntimeUtil.typeError("return is not a function");
				}
			} catch(RuntimeException ex) {
				return BuiltinPromiseConstructor.reject(env, JSRuntimeException.exceptionObject(ex));
			}
			if(returnMethod==null || returnMethod==RuntimeUtil.UNDEFINED) {
				return BuiltinPromiseConstructor.resolve(env, RuntimeUtil.UNDEFINED);
			}
			Object result;
			try {
				result = RuntimeUtil.call(env, returnMethod, _this, RuntimeUtil.EMPTY_PARAMS);
			} catch(RuntimeException ex) {
				return BuiltinPromiseConstructor.reject(env, JSRuntimeException.exceptionObject(ex));
			}
			BuiltinPromise resultWrapper;
			try {
				resultWrapper = BuiltinPromiseConstructor.resolve(env, result);
			} catch(RuntimeException ex) {
				return BuiltinPromiseConstructor.reject(env, JSRuntimeException.exceptionObject(ex));
			}
			Callable unwrap = new BuiltinFunctionNative(env, "", 1) {
				@Override
				public Object call(Object thisArg, Object[] args, Constructor newTarget) {
					return RuntimeUtil.UNDEFINED;
				}
			};
			return resultWrapper.then_(unwrap, null);
		}
	}
}
