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
package org.monflabs.galtajs.library.platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.library.platform.headers.Headers;
import org.monflabs.galtajs.library.platform.headers.HeadersConstructor;
import org.monflabs.galtajs.library.platform.request.Request;
import org.monflabs.galtajs.library.platform.request.RequestConstructor;
import org.monflabs.galtajs.library.platform.response.Response;
import org.monflabs.galtajs.library.platform.response.ResponseConstructor;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.executors.JSExecutor;

/**
 * WHATWG-style fetch API: fetch(), Request, Response, Headers.
 *
 * Transport: JDK 17 java.net.http.HttpClient (async). Responses are dispatched
 * back through a microtask so the Promise resolves on the event-loop thread.
 * The Promise rejects on network/URL errors; HTTP status codes (including 4xx
 * and 5xx) resolve the Promise with a Response whose .ok reflects 2xx status.
 */
public class FetchLibrary extends GlobalLibrary {

	// One shared client per JVM; HttpClient is thread-safe and internally
	// pooled. Creating one per fetch() call would leak virtual threads / native
	// resources.
	private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(30))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	// Upper bound for a whole request (response included), so a server that
	// never answers can't keep a script waiting forever. System property
	// "galtajs.fetch.timeoutSeconds" overrides the default.
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(Long.getLong("galtajs.fetch.timeoutSeconds", 300L));

	public FetchLibrary() {
	}

	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		standardObjects.setOwnProperty(Headers.CLASSNAME, new HeadersConstructor(env), PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Request.CLASSNAME, new RequestConstructor(env), PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Response.CLASSNAME, new ResponseConstructor(env), PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnMethod(new FetchMethod(env));
	}

	/* =====================================================================
	 * fetch(input, init) -> Promise<Response>
	 * ===================================================================== */
	private static final class FetchMethod extends BaseMethod {
		private FetchMethod(JSEnvironment env) {
			super(env, "fetch", 1);
		}

		@Override
		public Object call(Object obj, Object[] args) {
			JSEnvironment env = getEnvironment();
			JSExecutor executor = JSRuntimeContext.get().getGlobalContext().getExecutor();

			// Parse the request here, on the script's thread: it converts JS
			// values (init, body) and may run user code (toString). Only the
			// HTTP call goes to asyncFunction(), so the executor keeps its
			// pending-async counter balanced and the returned Promise
			// integrates naturally with then_/await. An invalid request still
			// gives a rejected promise, as fetch() specifies.
			final Request req;
			try {
				req = buildRequest(env, args);
			} catch (JSRuntimeUncatchableException e) {
				throw e;
			} catch (JSRuntimeException e) {
				return executor.asyncFunction(() -> {
					throw e;
				});
			}
			return executor.asyncFunction(() -> {
				HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(req.getUrl())).timeout(REQUEST_TIMEOUT);

				String method = req.getMethod();
				HttpRequest.BodyPublisher publisher;
				if (req.getBody() != null && !"GET".equals(method) && !"HEAD".equals(method)) {
					publisher = BodyPublishers.ofString(req.getBody(), StandardCharsets.UTF_8);
				} else {
					publisher = BodyPublishers.noBody();
				}
				rb.method(method, publisher);

				for (Iterator<Map.Entry<String, List<String>>> it = req.getHeaders().headerEntries(); it.hasNext(); ) {
					Map.Entry<String, List<String>> e = it.next();
					for (String v : e.getValue()) {
						try {
							rb.header(e.getKey(), v);
						} catch (IllegalArgumentException ignored) {
							// restricted header; skip
						}
					}
				}

				HttpResponse<byte[]> resp;
				try {
					resp = SHARED_CLIENT.send(rb.build(), BodyHandlers.ofByteArray());
				} catch (InterruptedException ie) {
					// the script is being stopped: don't turn that into a JS rejection
					Thread.currentThread().interrupt();
					throw new JSRuntimeInterruptException();
				} catch (Exception t) {
					throw RuntimeUtil.typeError("fetch: " + rootMessage(t));
				}

				Headers respHeaders = new Headers(env);
				for (Map.Entry<String, List<String>> e : resp.headers().map().entrySet()) {
					for (String v : e.getValue()) {
						respHeaders.append(e.getKey(), v);
					}
				}
				return new Response(env,
						resp.statusCode(),
						reasonPhrase(resp.statusCode()),
						resp.uri().toString(),
						respHeaders,
						resp.body());
			});
		}
	}

	

	/* =====================================================================
	 * Shared helpers
	 * ===================================================================== */

	public static Request buildRequest(JSEnvironment env, Object[] args) {
		if (args.length == 0) {
			throw RuntimeUtil.typeError("Request/fetch requires at least 1 argument");
		}
		Object input = args[0];
		String url;
		String method = "GET";
		Headers headers = null;
		String body = null;

		if (input instanceof Request existing) {
			url = existing.getUrl();
			method = existing.getMethod();
			headers = existing.getHeaders().copy();
			body = existing.getBody();
		} else {
			url = RuntimeUtil.toString(env, input);
		}

		if (args.length > 1 && args[1] instanceof JSObject init) {
			Object m = init.getOwnProperty("method", null, init);
			if (m != null && m != RuntimeUtil.UNDEFINED) {
				method = coerceMethod(env, m);
			}
			Object hs = init.getOwnProperty("headers", null, init);
			if (hs != null && hs != RuntimeUtil.UNDEFINED) {
				if (headers == null) {
					headers = new Headers(env);
				}
				populateHeaders(env, headers, hs);
			}
			Object b = init.getOwnProperty("body", null, init);
			if (b != null && b != RuntimeUtil.UNDEFINED) {
				body = RuntimeUtil.toString(env, b);
			}
		}
		if (headers == null) {
			headers = new Headers(env);
		}
		return new Request(env, url, method, headers, body);
	}

	static String coerceMethod(JSEnvironment env, Object v) {
		if (v == null || v == RuntimeUtil.UNDEFINED) {
			return "GET";
		}
		return RuntimeUtil.toString(env, v).toUpperCase();
	}

	// Accepts: a FetchHeaders instance, an array of [name,value] arrays, or a
	// plain object whose enumerable own properties become header pairs.
	public static void populateHeaders(JSEnvironment env, Headers target, Object source) {
		if (source instanceof Headers src) {
			for (Iterator<Map.Entry<String, List<String>>> it = src.headerEntries(); it.hasNext(); ) {
				Map.Entry<String, List<String>> e = it.next();
				for (String v : e.getValue()) {
					target.append(e.getKey(), v);
				}
			}
			return;
		}
		if (source instanceof JSArray arr) {
			long n = arr.arrayLength();
			for (long i = 0; i < n; i++) {
				Object row = arr.getOwnProperty(i, null, arr);
				if (row instanceof JSArray pair && pair.arrayLength() >= 2) {
					String name = RuntimeUtil.toString(env, pair.getOwnProperty(0L, null, pair));
					String value = RuntimeUtil.toString(env, pair.getOwnProperty(1L, null, pair));
					target.append(name, value);
				}
			}
			return;
		}
		if (source instanceof JSObject obj) {
			for (Iterator<Map.Entry<String, Object>> it = obj.ownPropertyEntries(true); it.hasNext(); ) {
				Map.Entry<String, Object> e = it.next();
				target.append(e.getKey(), RuntimeUtil.toString(env, e.getValue()));
			}
			return;
		}
	}

	private static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null && c.getCause() != c) {
			c = c.getCause();
		}
		String m = c.getMessage();
		return m != null ? m : c.getClass().getSimpleName();
	}

	private static String reasonPhrase(int status) {
		return switch (status) {
			case 200 -> "OK";
			case 201 -> "Created";
			case 204 -> "No Content";
			case 301 -> "Moved Permanently";
			case 302 -> "Found";
			case 304 -> "Not Modified";
			case 400 -> "Bad Request";
			case 401 -> "Unauthorized";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 500 -> "Internal Server Error";
			case 502 -> "Bad Gateway";
			case 503 -> "Service Unavailable";
			default -> "";
		};
	}
}
