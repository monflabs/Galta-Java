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

import java.util.Iterator;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * @author Philippe Riand
 */
public class AggregateError extends BaseError {

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

		public static final String CLASSNAME = "AggregateError";

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,PrototypeImpl.get(env),2);
		}

		@Override
		public Class<?> getNativeClass() {
			return AggregateError.class;
		}

		// A NativeError constructor's own [[Prototype]] is %Error% (constructor-
		// level inheritance), not Function.prototype - mirrors how its own
		// "prototype" property's [[Prototype]] is Error.prototype.
		@Override
		protected Object getDefaultPrototype() {
			return getEnvironment().getStandardObjects().getConstructor(Error.ConstructorImpl.CLASSNAME);
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			// AggregateError(errors, message[, options]) - message is converted
			// (and the cause option extracted) BEFORE errors is iterated; see
			// order-of-args-evaluation.js.
			Object messageArg = parameters.length>=2 ? parameters[1] : RuntimeUtil.UNDEFINED;
			String message = null;
			if(messageArg!=RuntimeUtil.UNDEFINED) {
				message = RuntimeUtil.toString(getEnvironment(),messageArg);
			}
			Object cause = RuntimeUtil.NOT_AVAILABLE;
			if(parameters.length>=3) {
				Object options = parameters[2];
				// InstallErrorCause: HasProperty must be checked (and its abrupt
				// completion propagated, e.g. via a Proxy "has" trap) before Get.
				if(options != null && options != RuntimeUtil.UNDEFINED && RuntimeUtil.isObject(getEnvironment(),options)
						&& RuntimeUtil.hasProperty(getEnvironment(),options,"cause")) {
					cause = RuntimeUtil.getProperty(getEnvironment(),options,"cause");
				}
			}
			Object errorsArg = parameters.length>=1 ? parameters[0] : RuntimeUtil.UNDEFINED;
			Iterator<Object> errorsIterator = RuntimeUtil.valueIterator(getEnvironment(),errorsArg);

			AggregateError error = new AggregateError(getEnvironment(),errorsIterator);
			if(message!=null) {
				error.setOwnProperty("message",message,PropertyDescriptor.DESC_METHOD);
			}
			if(cause != RuntimeUtil.NOT_AVAILABLE) {
				error.setOwnProperty("cause", cause, PropertyDescriptor.DESC_METHOD);
			}
			return applyNewTargetPrototype(error, topConstructor);
		}
	}

    public AggregateError(JSEnvironment env, List<Object> errors) {
		super(env);
    	JSArray ar = JSArray.create(getEnvironment());
    	if(errors!=null) {
    		ar.arrayAddAll(errors.iterator());
    	}
    	setOwnProperty("errors",ar,PropertyDescriptor.DESC_METHOD);
    }

    public AggregateError(JSEnvironment env, Iterator<Object> errors) {
		super(env);
    	JSArray ar = JSArray.create(getEnvironment());
    	if(errors!=null) {
    		ar.arrayAddAll(errors);
    	}
    	setOwnProperty("errors",ar,PropertyDescriptor.DESC_METHOD);
    }

    @Override
	protected Object getDefaultPrototype() {
		return PrototypeImpl.get(getEnvironment());
	}
}
