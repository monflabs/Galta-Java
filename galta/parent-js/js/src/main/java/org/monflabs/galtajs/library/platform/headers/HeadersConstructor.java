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
package org.monflabs.galtajs.library.platform.headers;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.platform.FetchLibrary;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

public class HeadersConstructor extends BaseStandardConstructor {
	
	public HeadersConstructor(JSEnvironment env) {
		super(env, Headers.CLASSNAME, prototype(env), 0);
	}

	private static HeadersPrototype prototype(JSEnvironment env) {
		HeadersPrototype p = (HeadersPrototype) env.getRegisteredPrototype(HeadersPrototype.class);
		if (p == null) {
			p = new HeadersPrototype(env);
			env.registerPrototype(HeadersPrototype.class, p);
		}
		return p;
	}

	@Override
	public Class<?> getNativeClass() {
		return Headers.class;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		return constructObject(parameters, this);
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		Headers h = applyNewTargetPrototype(new Headers(getEnvironment()), topConstructor);
		if (parameters.length > 0 && parameters[0] != null && parameters[0] != RuntimeUtil.UNDEFINED) {
			FetchLibrary.populateHeaders(getEnvironment(), h, parameters[0]);
		}
		return h;
	}
}