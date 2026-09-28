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
import org.monflabs.galtajs.library.platform.headers.Headers;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.executors.MicroTask;
import org.monflabs.json.parser.JsonParser;

/**
 * WHATWG Response-like object. Body is stored as raw bytes plus a decoded-once
 * String; body-consumption methods enforce the "body already used" invariant.
 */
public class Response extends NativeObject {

	public static final String CLASSNAME = "Response";

	private final int status;
	private final String statusText;
	private final String url;
	private final Headers headers;
	private final byte[] body;

	private boolean bodyUsed;

	public Response(JSEnvironment env, int status, String statusText, String url,
			Headers headers, byte[] body) {
		super(env);
		this.status = status;
		this.statusText = statusText != null ? statusText : "";
		this.url = url != null ? url : "";
		this.headers = headers != null ? headers : new Headers(env);
		this.body = body != null ? body : new byte[0];

		setOwnProperty("status", (long) status);
		setOwnProperty("statusText", this.statusText);
		setOwnProperty("ok", status >= 200 && status < 300);
		setOwnProperty("url", this.url);
		setOwnProperty("headers", this.headers);
		setOwnProperty("redirected", false);
		setOwnProperty("type", "basic");
		setOwnProperty("bodyUsed", false);

		setOwnMethod(new BodyMethod(env, "text"));
		setOwnMethod(new BodyMethod(env, "json"));
		setOwnMethod(new BodyMethod(env, "arrayBuffer"));
		setOwnMethod(new BodyMethod(env, "bytes"));
		setOwnMethod(new BodyMethod(env, "clone"));
	}

	@Override
	public String getClassName() {
		return CLASSNAME;
	}

	private BuiltinPromise resolved(Object value) {
		BuiltinPromise p = new BuiltinPromise(getEnvironment());
		// Resolve on a microtask so callers get real async semantics.
		JSRuntimeContext ctx = JSRuntimeContext.get();
		ctx.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Response - body", ctx, null) {
			@Override
			public void run() {
				p.resolvePromise(value);
			}
		});
		return p;
	}

	private BuiltinPromise rejected(Object reason) {
		BuiltinPromise p = new BuiltinPromise(getEnvironment());
		JSRuntimeContext ctx = JSRuntimeContext.get();
		ctx.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Response - body", ctx, null) {
			@Override
			public void run() {
				p.reject(reason);
			}
		});
		return p;
	}

	private final class BodyMethod extends BaseMethod {
		private final String methodName;

		private BodyMethod(JSEnvironment env, String name) {
			super(env, name, 0);
			this.methodName = name;
		}

		@Override
		public Object call(Object obj, Object[] args) {
			JSEnvironment env = getEnvironment();
			if ("clone".equals(methodName)) {
				return new Response(env, status, statusText, url, headers.copy(), body.clone());
			}
			if (bodyUsed) {
				return rejected(RuntimeUtil.typeError("Body already consumed"));
			}
			bodyUsed = true;
			Response.this.setOwnProperty("bodyUsed", true);

			switch (methodName) {
				case "text":
					return resolved(new String(body, StandardCharsets.UTF_8));
				case "json": {
					String text = new String(body, StandardCharsets.UTF_8);
					try {
						JsonParser.StringParser p = new JsonParser.StringParser(env.getJsonFactory());
						p.setStrict(true);
						Object parsed = p.parse(text);
						return resolved(parsed);
					} catch (Exception ex) {
						return rejected(RuntimeUtil.syntaxError("Response.json: invalid JSON"));
					}
				}
				case "arrayBuffer":
				case "bytes":
					// Return the raw bytes as a Java byte[]; JS interop exposes
					// .length and indexed access. A full ArrayBuffer/Uint8Array
					// wrap is out of scope here.
					return resolved(body.clone());
				default:
					throw new IllegalStateException("Unknown body method: " + methodName);
			}
		}
	}
}
