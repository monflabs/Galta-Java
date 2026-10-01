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

/**
 * InternalError (see NativeError).
 */
public class InternalError extends NativeError {

	public static final class PrototypeImpl extends NativeErrorPrototype {

		public static PrototypeImpl get(JSEnvironment env) {
			PrototypeImpl proto = (PrototypeImpl)env.getRegisteredPrototype(PrototypeImpl.class);
			if(proto==null) {
				proto = new PrototypeImpl(env);
				env.registerPrototype(PrototypeImpl.class,proto);
			}
			return proto;
		}

		private PrototypeImpl(JSEnvironment env) {
			super(env,ConstructorImpl.CLASSNAME);
		}
	}

	public static final class ConstructorImpl extends NativeErrorConstructor {

		public static final String CLASSNAME = "InternalError";

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,PrototypeImpl.get(env));
		}

		@Override
		public Class<?> getNativeClass() {
			return InternalError.class;
		}

		@Override
		protected NativeError createError(JSEnvironment env) {
			return new InternalError(env);
		}
	}

	public InternalError(JSEnvironment env) {
		super(env);
	}

	public InternalError(JSEnvironment env, String message) {
		super(env,message);
	}

	@Override
	protected Object getDefaultPrototype() {
		return PrototypeImpl.get(getEnvironment());
	}
}
