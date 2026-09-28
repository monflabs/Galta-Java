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
package org.monflabs.galtajs.cdp.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.cdp.json.Json;
import org.monflabs.galtajs.debug.api.DebugException;
import org.monflabs.galtajs.debug.api.DebugProperty;
import org.monflabs.galtajs.debug.api.DebugValues;
import org.monflabs.galtajs.debug.api.Debugger;
import org.monflabs.galtajs.debug.api.ExecutionContext;
import org.monflabs.galtajs.debug.api.PausedEvent;

/**
 * The {@code Runtime} domain.
 *
 * <p>Ported from a sibling project's own Chrome DevTools Protocol debugger
 * (same author) - the method list and dispatch shape are unchanged; every
 * handler body now calls GaltaJS's own {@code debug.api} facade.
 * {@code terminateExecution} is a documented v1 no-op: GaltaJS's facade has
 * no "forcibly kill the running/paused thread" primitive yet.
 */
final class RuntimeDomain {
	private final CdpSession session;

	RuntimeDomain(final CdpSession session) {
		this.session = session;
	}

	private Debugger debugger() {
		return session.debugger();
	}

	Map<String, Object> handle(final String method, final Params params) throws Exception {
		switch (method) {
		case "enable":
			session.runtimeEnabled = true;
			for (final ExecutionContext ctx : debugger().executionContexts()) {
				session.sendEvent("Runtime.executionContextCreated", Json.object("context", CdpSession.contextDescription(ctx, session.uniqueId())));
			}
			return null;
		case "disable":
			session.runtimeEnabled = false;
			return null;
		case "evaluate": {
			final String expression = params.string("expression");
			final String group = params.string("objectGroup", null);
			final boolean byValue = params.bool("returnByValue", false);
			final boolean preview = params.bool("generatePreview", false);
			final ExecutionContext ctx = params.has("contextId") ? session.context(params.integer("contextId")) : session.defaultContext();
			final PausedEvent pause = session.pause();
			return session.inContext(ctx, () -> {
				try {
					final Object value;
					if (pause != null && !pause.isResumed() && !pause.frames().isEmpty()) {
						value = pause.frames().get(0).evaluate(expression);
					} else if (ctx != null) {
						value = debugger().values().evaluateWith(ctx, expression, null);
					} else {
						throw new CdpError(CdpError.SERVER_ERROR, "no execution context yet");
					}
					return Json.object("result", session.objects.remoteObject(value, group, byValue, preview));
				} catch (final DebugException e) {
					return Json.object("result", session.objects.remoteObject(e.thrown(), group, false, false),
							"exceptionDetails", session.objects.exceptionDetails(e, group, null, ctx == null ? 0 : ctx.id()));
				}
			});
		}
		case "callFunctionOn": {
			final String declaration = params.string("functionDeclaration");
			final String group = params.string("objectGroup", null);
			final boolean byValue = params.bool("returnByValue", false);
			final boolean preview = params.bool("generatePreview", false);
			final Object target = params.has("objectId") ? session.objects.get(params.string("objectId")) : null;
			final ExecutionContext ctx = params.has("executionContextId") ? session.context(params.integer("executionContextId")) : session.defaultContext();
			final List<?> rawArguments = params.list("arguments");
			return session.inContext(ctx, () -> {
				try {
					final DebugValues values = debugger().values();
					final Object function = values.evaluateWith(ctx, "(" + declaration + ")", null);
					final Object[] args = new Object[rawArguments.size()];
					for (int i = 0; i < args.length; i++) {
						args[i] = argument(session, new Params(rawArguments.get(i)));
					}
					final Object result = values.callFunction(function, target, args);
					return Json.object("result", session.objects.remoteObject(result, group, byValue, preview));
				} catch (final DebugException e) {
					return Json.object("result", session.objects.remoteObject(e.thrown(), group, false, false),
							"exceptionDetails", session.objects.exceptionDetails(e, group, null, ctx == null ? 0 : ctx.id()));
				}
			});
		}
		case "getProperties": {
			final Object object = session.objects.get(params.string("objectId"));
			final boolean own = params.bool("ownProperties", false);
			final boolean accessorsOnly = params.bool("accessorPropertiesOnly", false);
			final boolean preview = params.bool("generatePreview", false);
			final boolean nonIndexed = params.bool("nonIndexedPropertiesOnly", false);
			final String group = params.string("objectGroup", null);
			return session.inContext(null, () -> {
				final DebugValues values = debugger().values();
				final List<Object> result = new ArrayList<>();
				for (final DebugProperty p : values.ownProperties(object, true, !nonIndexed)) {
					if (accessorsOnly && p.getter() == null && p.setter() == null) {
						continue;
					}
					result.add(session.objects.propertyDescriptor(p, group, preview));
				}
				final Map<String, Object> response = Json.object("result", result);
				if (own && !accessorsOnly) {
					final List<Object> internal = new ArrayList<>();
					for (final DebugProperty p : values.internalProperties(object)) {
						if (p.value() != null) {
							internal.add(session.objects.internalPropertyDescriptor(p, group));
						}
					}
					response.put("internalProperties", internal);
				}
				return response;
			});
		}
		case "releaseObject":
			session.objects.release(params.string("objectId"));
			return null;
		case "releaseObjectGroup":
			session.objects.releaseGroup(params.string("objectGroup"));
			return null;
		case "runIfWaitingForDebugger":
			session.runIfWaitingForDebugger();
			return null;
		case "getIsolateId":
			return Json.object("id", session.uniqueId());
		case "getHeapUsage": {
			final Runtime rt = Runtime.getRuntime();
			return Json.object("usedSize", (double) (rt.totalMemory() - rt.freeMemory()), "totalSize", (double) rt.totalMemory());
		}
		case "compileScript":
			// parse-only compilation is not offered; the client evaluates instead
			return Json.object();
		case "globalLexicalScopeNames":
			return Json.object("names", List.of());
		case "terminateExecution":
			// v1: no "forcibly kill the running/paused thread" primitive in
			// GaltaJS's facade yet - a no-op rather than a misleading action.
			return null;
		case "discardConsoleEntries", "setCustomObjectFormatterEnabled", "setMaxCallStackSizeToCapture",
			 "setAsyncCallStackDepth", "addBinding", "removeBinding":
			return null;
		default:
			throw new CdpError(CdpError.METHOD_NOT_FOUND, "'Runtime." + method + "' wasn't found");
		}
	}

	/** A {@code Runtime.CallArgument} as an engine value. */
	static Object argument(final CdpSession session, final Params argument) throws CdpError {
		if (argument.has("objectId")) {
			return session.objects.get(argument.string("objectId"));
		}
		if (argument.has("unserializableValue")) {
			return switch (argument.string("unserializableValue")) {
				case "NaN" -> Double.NaN;
				case "Infinity" -> Double.POSITIVE_INFINITY;
				case "-Infinity" -> Double.NEGATIVE_INFINITY;
				case "-0" -> -0.0d;
				default -> throw CdpError.invalidParams("unserializableValue");
			};
		}
		// An explicit {"value": null} is null; no value at all is undefined
		if (!argument.containsKey("value")) {
			return DebugValues.UNDEFINED;
		}
		final Object value = argument.raw("value");
		if (value == null) {
			return null;
		}
		if (value instanceof Number n) {
			return n.doubleValue();
		}
		return value; // a String, a Boolean; a Map or List is passed as such
	}
}
