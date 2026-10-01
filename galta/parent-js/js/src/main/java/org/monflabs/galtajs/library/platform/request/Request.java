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
import org.monflabs.galtajs.library.platform.headers.Headers;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * WHATWG Request-like object.
 * Body is captured as a String at construction time.
 */
public class Request extends NativeObject {

	public static final String CLASSNAME = "Request";

	private final String url;
	private final String method;
	private final Headers headers;
	private final String body;

	public Request(JSEnvironment env, String url, String method, Headers headers, String body) {
		super(env);
		this.url = url;
		this.method = method == null ? "GET" : method.toUpperCase();
		this.headers = headers != null ? headers : new Headers(env);
		this.body = body;

		setOwnProperty("url", this.url);
		setOwnProperty("method", this.method);
		setOwnProperty("headers", this.headers);
		setOwnProperty("bodyUsed", false);

		setOwnMethod(new CloneMethod(env));
	}

	@Override
	public String getClassName() {
		return CLASSNAME;
	}

	public String getUrl() {
		return url;
	}

	public String getMethod() {
		return method;
	}

	public Headers getHeaders() {
		return headers;
	}

	public String getBody() {
		return body;
	}

	private final class CloneMethod extends BaseMethod {
		private CloneMethod(JSEnvironment env) {
			super(env, "clone", 0);
		}

		@Override
		protected Object invoke(Object obj, Object[] args) {
			return new Request(getEnvironment(), url, method, headers.copy(), body);
		}
	}
}
