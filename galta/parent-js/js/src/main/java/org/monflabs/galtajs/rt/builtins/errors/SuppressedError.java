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
package org.monflabs.galtajs.rt.builtins.errors;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * SuppressedError(error, suppressed, message) - thrown by DisposeResources
 * when disposing a resource itself throws while an earlier (abrupt or
 * disposal) completion is already in flight; see ASTBlock's disposal hook.
 */
public class SuppressedError extends BaseError {

	public static final class PrototypeImpl extends BasePrototype {

		public static PrototypeImpl get(JSEnvironment env) {
			PrototypeImpl proto = (PrototypeImpl)env.getRegisteredPrototype(PrototypeImpl.class);
			if(proto==null) {
				proto = new PrototypeImpl(env);
				env.registerPrototype(PrototypeImpl.class,proto);
			}
			return proto;
		}

		private PrototypeImpl(JSEnvironment env) {
			super(env);
			setOwnProperty("name",ConstructorImpl.CLASSNAME,PropertyDescriptor.DESC_METHOD);
			setOwnProperty("message","",PropertyDescriptor.DESC_METHOD);
		}

		@Override
		protected Object getDefaultPrototype() {
			return Error.PrototypeImpl.get(getEnvironment());
		}
	}

	public static final class ConstructorImpl extends BaseStandardConstructor {

		public static final String CLASSNAME = "SuppressedError";

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,PrototypeImpl.get(env),3);
		}

		@Override
		public Class<?> getNativeClass() {
			return SuppressedError.class;
		}

		// A NativeError constructor's own [[Prototype]] is %Error% (constructor-
		// level inheritance), not Function.prototype - see AggregateError's
		// identical override.
		@Override
		protected Object getDefaultPrototype() {
			return getEnvironment().getStandardObjects().getConstructor(Error.ConstructorImpl.CLASSNAME);
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			Object errorArg = parameters.length>=1 ? parameters[0] : RuntimeUtil.UNDEFINED;
			Object suppressedArg = parameters.length>=2 ? parameters[1] : RuntimeUtil.UNDEFINED;
			Object messageArg = parameters.length>=3 ? parameters[2] : RuntimeUtil.UNDEFINED;
			String message = null;
			if(messageArg!=RuntimeUtil.UNDEFINED) {
				message = RuntimeUtil.toString(getEnvironment(),messageArg);
			}
			SuppressedError error = new SuppressedError(getEnvironment(),errorArg,suppressedArg,message);
			return applyNewTargetPrototype(error, topConstructor);
		}
	}

	public SuppressedError(JSEnvironment env, Object error, Object suppressed) {
		this(env,error,suppressed,null);
	}

	// Property definition order matters (Object.getOwnPropertyNames() /
	// for-in enumeration order) - spec defines "message" (if present) BEFORE
	// "error" and "suppressed" - see order-of-args-evaluation.js.
	public SuppressedError(JSEnvironment env, Object error, Object suppressed, String message) {
		super(env);
		if(message!=null) {
			setOwnProperty("message",message,PropertyDescriptor.DESC_METHOD);
		}
		setOwnProperty("error",error,PropertyDescriptor.DESC_METHOD);
		setOwnProperty("suppressed",suppressed,PropertyDescriptor.DESC_METHOD);
	}

	@Override
	protected Object getDefaultPrototype() {
		return PrototypeImpl.get(getEnvironment());
	}
}
