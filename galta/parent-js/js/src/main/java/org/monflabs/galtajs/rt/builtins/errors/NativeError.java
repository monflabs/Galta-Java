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
 * The NativeError objects (EvalError, RangeError, ReferenceError, SyntaxError,
 * TypeError, URIError, plus the non-standard InternalError): they only differ by
 * their name, which the thin subclasses provide.
 */
public abstract class NativeError extends BaseError {

	/**
	 * NativeError.prototype: own "name" and "message" ("") properties, inherits
	 * from Error.prototype.
	 */
	public abstract static class NativeErrorPrototype extends BasePrototype {

		protected NativeErrorPrototype(JSEnvironment env, String name) {
			super(env);
			setOwnProperty("name",name,PropertyDescriptor.DESC_METHOD);
			// Every NativeError prototype has its own "message" - "" own property.
			setOwnProperty("message","",PropertyDescriptor.DESC_METHOD);
		}

		@Override
		protected Object getDefaultPrototype() {
			return Error.PrototypeImpl.get(getEnvironment());
		}
	}

	/**
	 * NativeError constructor.
	 */
	public abstract static class NativeErrorConstructor extends BaseStandardConstructor {

		protected NativeErrorConstructor(JSEnvironment env, String className, BasePrototype prototype) {
			super(env,className,prototype,1);
		}

		/**
		 * Creates the error instance, before its message/cause are installed.
		 */
		protected abstract NativeError createError(JSEnvironment env);

		// A NativeError constructor's own [[Prototype]] is %Error% (constructor-
		// level inheritance), not Function.prototype - mirrors how its own
		// "prototype" property's [[Prototype]] is Error.prototype.
		@Override
		protected Object getDefaultPrototype() {
			return getEnvironment().getStandardObjects().getConstructor(Error.ConstructorImpl.CLASSNAME);
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			NativeError error = createError(getEnvironment());
			if(parameters.length>=1 && parameters[0]!=RuntimeUtil.UNDEFINED) {
				error.setOwnProperty("message",RuntimeUtil.toString(getEnvironment(),parameters[0]),PropertyDescriptor.DESC_METHOD);
			}
			if(parameters.length>=2) {
				Object options = parameters[1];
				if(options != null && options != RuntimeUtil.UNDEFINED && RuntimeUtil.isObject(getEnvironment(),options)) {
					// InstallErrorCause: HasProperty must be checked (and its
					// abrupt completion propagated, e.g. via a Proxy "has"
					// trap) before Get - not folded into a single lookup.
					if(RuntimeUtil.hasProperty(getEnvironment(),options,"cause")) {
						Object cause = RuntimeUtil.getProperty(getEnvironment(),options,"cause");
						error.setOwnProperty("cause", cause, PropertyDescriptor.DESC_METHOD);
					}
				}
			}
			// GetPrototypeFromConstructor - see Error.ConstructorImpl's own
			// constructObject for the full rationale.
			return applyNewTargetPrototype(error, topConstructor);
		}
	}

	protected NativeError(JSEnvironment env) {
		super(env);
	}

	protected NativeError(JSEnvironment env, String message) {
		super(env);
		setOwnProperty("message",message,PropertyDescriptor.DESC_METHOD);
	}
}
