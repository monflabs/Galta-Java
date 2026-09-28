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
package org.monflabs.galtajs.library.platform.request;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.platform.FetchLibrary;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

public class RequestConstructor extends BaseStandardConstructor {
	
	public RequestConstructor(JSEnvironment env) {
		super(env, Request.CLASSNAME, prototype(env), 1);
	}

	private static RequestPrototype prototype(JSEnvironment env) {
		RequestPrototype p = (RequestPrototype) env.getRegisteredPrototype(RequestPrototype.class);
		if (p == null) {
			p = new RequestPrototype(env);
			env.registerPrototype(RequestPrototype.class, p);
		}
		return p;
	}

	@Override
	public Class<?> getNativeClass() {
		return Request.class;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Request constructor cannot be called as a function");
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		return applyNewTargetPrototype(FetchLibrary.buildRequest(getEnvironment(), parameters), topConstructor);
	}
}