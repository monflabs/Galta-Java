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
package org.monflabs.galtajs.library.platform.response;

import java.nio.charset.StandardCharsets;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.library.platform.FetchLibrary;
import org.monflabs.galtajs.library.platform.headers.Headers;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

public class ResponseConstructor extends BaseStandardConstructor {
	
	public ResponseConstructor(JSEnvironment env) {
		super(env, Response.CLASSNAME, prototype(env), 0);
	}

	private static ResponsePrototype prototype(JSEnvironment env) {
		ResponsePrototype p = (ResponsePrototype) env.getRegisteredPrototype(ResponsePrototype.class);
		if (p == null) {
			p = new ResponsePrototype(env);
			env.registerPrototype(ResponsePrototype.class, p);
		}
		return p;
	}

	@Override
	public Class<?> getNativeClass() {
		return Response.class;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		return constructObject(parameters, this);
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		JSEnvironment env = getEnvironment();
		byte[] body = new byte[0];
		if (parameters.length > 0 && parameters[0] != null && parameters[0] != RuntimeUtil.UNDEFINED) {
			body = RuntimeUtil.toString(env, parameters[0]).getBytes(StandardCharsets.UTF_8);
		}
		int status = 200;
		String statusText = "";
		Headers headers = new Headers(env);
		if (parameters.length > 1 && parameters[1] instanceof JSObject init) {
			Object s = init.getOwnProperty("status", null, init);
			if (s != null && s != RuntimeUtil.UNDEFINED) {
				status = (int) RuntimeUtil.toLong(env, s);
			}
			Object st = init.getOwnProperty("statusText", null, init);
			if (st != null && st != RuntimeUtil.UNDEFINED) {
				statusText = RuntimeUtil.toString(env, st);
			}
			Object hs = init.getOwnProperty("headers", null, init);
			if (hs != null && hs != RuntimeUtil.UNDEFINED) {
				FetchLibrary.populateHeaders(env, headers, hs);
			}
		}
		return applyNewTargetPrototype(new Response(env, status, statusText, "", headers, body),
				topConstructor);
	}
}