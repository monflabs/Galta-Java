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
import org.monflabs.galtajs.debug.api.Breakpoint;
import org.monflabs.galtajs.debug.api.BreakpointRequest;
import org.monflabs.galtajs.debug.api.DebugException;
import org.monflabs.galtajs.debug.api.DebugFrame;
import org.monflabs.galtajs.debug.api.DebugScope;
import org.monflabs.galtajs.debug.api.DebugScript;
import org.monflabs.galtajs.debug.api.Debugger;
import org.monflabs.galtajs.debug.api.Location;
import org.monflabs.galtajs.debug.api.PauseOnExceptions;
import org.monflabs.galtajs.debug.api.PausedEvent;

/**
 * The {@code Debugger} domain.
 *
 * <p>Ported from a sibling project's own Chrome DevTools Protocol debugger
 * (same author) - the method list and dispatch shape are unchanged; every
 * handler body now calls GaltaJS's own {@code debug.api} facade.
 */
final class DebuggerDomain {
	private final CdpSession session;
	private final List<String> breakpointIds = new ArrayList<>();
	private volatile String temporaryBreakpoint;

	DebuggerDomain(final CdpSession session) {
		this.session = session;
	}

	private Debugger debugger() {
		return session.debugger();
	}

	Map<String, Object> handle(final String method, final Params params) throws Exception {
		switch (method) {
		case "enable": {
			session.debuggerEnabled = true;
			for (final DebugScript script : debugger().scripts()) {
				session.sendEvent("Debugger.scriptParsed", CdpSession.scriptParsedParams(script));
			}
			// A client that attaches after the script already paused (e.g.
			// "start the server, then open Chrome" - the pause happened the
			// moment the client-less script hit pauseOnStart()) missed the
			// one-shot Debugger.paused event entirely; replay it now that
			// this client exists, through the exact same path a live pause
			// takes, so this session's own pause-tracking/state stay correct.
			// Built on the paused thread, which owns the objects it describes
			// (CdpSession.paused() ignores a pause it already reported, so a
			// live pause racing this replay is sent once)
			final PausedEvent current = debugger().currentPause();
			if (current != null && !current.isResumed()) {
				current.call(() -> {
					session.paused(current);
					return null;
				});
			}
			return Json.object("debuggerId", "galtajs-" + session.uniqueId());
		}
		case "disable": {
			session.debuggerEnabled = false;
			detach();
			// A disabled debugger leaves nothing paused behind (as in V8)
			final PausedEvent current = session.pause();
			if (current != null && !current.isResumed()) {
				current.resume();
			}
			return null;
		}
		case "setBreakpointByUrl": {
			final int line = RemoteObjects.engineLine(params.integer("lineNumber"));
			final int column = RemoteObjects.engineColumn(params.integer("columnNumber", -1));
			final String url = params.string("url", null);
			final String urlRegex = params.string("urlRegex", null);
			String scriptId = null;
			if (url == null && urlRegex == null) {
				final String hash = params.string("scriptHash", null);
				if (hash == null) {
					throw CdpError.invalidParams("one of url, urlRegex or scriptHash is required");
				}
				for (final DebugScript script : debugger().scripts()) {
					if (hash.equals(script.hash())) {
						scriptId = script.id();
					}
				}
				if (scriptId == null) {
					throw CdpError.invalidParams("no script with hash " + hash);
				}
			}
			final Breakpoint bp = debugger().setBreakpoint(new BreakpointRequest(url, urlRegex, scriptId, line, column, params.string("condition", null)));
			breakpointIds.add(bp.id());
			return Json.object("breakpointId", bp.id(), "locations", locations(bp.locations()));
		}
		case "setBreakpoint": {
			final Params location = params.object("location");
			final Breakpoint bp = debugger().setBreakpoint(new BreakpointRequest(null, null, location.string("scriptId"),
					RemoteObjects.engineLine(location.integer("lineNumber")), RemoteObjects.engineColumn(location.integer("columnNumber", -1)), params.string("condition", null)));
			breakpointIds.add(bp.id());
			if (bp.locations().isEmpty()) {
				debugger().removeBreakpoint(bp.id());
				throw new CdpError(CdpError.SERVER_ERROR, "Could not resolve breakpoint");
			}
			return Json.object("breakpointId", bp.id(), "actualLocation", RemoteObjects.location(bp.locations().get(0)));
		}
		case "removeBreakpoint": {
			final String id = params.string("breakpointId");
			breakpointIds.remove(id);
			debugger().removeBreakpoint(id);
			return null;
		}
		case "setBreakpointsActive":
			debugger().setBreakpointsActive(params.bool("active", true));
			return null;
		case "setSkipAllPauses":
			debugger().setSkipAllPauses(params.bool("skip", false));
			return null;
		case "getPossibleBreakpoints": {
			final Params start = params.object("start");
			final DebugScript script = script(start.string("scriptId"));
			final Params end = params.objectOrNull("end");
			final List<Object> found = new ArrayList<>();
			for (final Location l : script.possibleBreakpoints(RemoteObjects.engineLine(start.integer("lineNumber")), RemoteObjects.engineColumn(start.integer("columnNumber", 0)),
					end == null ? -1 : RemoteObjects.engineLine(end.integer("lineNumber")), end == null ? -1 : RemoteObjects.engineColumn(end.integer("columnNumber", -1)))) {
				found.add(RemoteObjects.location(l));
			}
			return Json.object("locations", found);
		}
		case "getScriptSource":
			return Json.object("scriptSource", script(params.string("scriptId")).source());
		case "pause":
			debugger().pause();
			return null;
		case "resume":
			session.requirePause().resume();
			return null;
		case "stepInto":
			session.requirePause().stepInto();
			return null;
		case "stepOver":
			session.requirePause().stepOver();
			return null;
		case "stepOut":
			session.requirePause().stepOut();
			return null;
		case "continueToLocation": {
			final Params location = params.object("location");
			final PausedEvent pause = session.requirePause();
			final Breakpoint bp = debugger().setBreakpoint(new BreakpointRequest(null, null, location.string("scriptId"),
					RemoteObjects.engineLine(location.integer("lineNumber")), RemoteObjects.engineColumn(location.integer("columnNumber", -1)), null));
			temporaryBreakpoint = bp.id();
			pause.resume();
			return null;
		}
		case "evaluateOnCallFrame": {
			final DebugFrame frame = session.frame(params.string("callFrameId"));
			final String expression = params.string("expression");
			final String group = params.string("objectGroup", null);
			final boolean byValue = params.bool("returnByValue", false);
			final boolean preview = params.bool("generatePreview", false);
			if (params.bool("throwOnSideEffect", false)) {
				throw new CdpError(CdpError.SERVER_ERROR, "throwOnSideEffect is not supported");
			}
			final PausedEvent pause = session.requirePause();
			return pause.call(() -> {
				try {
					return Json.object("result", session.objects.remoteObject(frame.evaluate(expression), group, byValue, preview));
				} catch (final DebugException e) {
					return Json.object("result", session.objects.remoteObject(e.thrown(), group, false, false),
							"exceptionDetails", session.objects.exceptionDetails(e, group, frame.location(), pause.context() == null ? 0 : pause.context().id()));
				}
			});
		}
		case "setPauseOnExceptions": {
			final String state = params.string("state");
			debugger().setPauseOnExceptions(switch (state) {
				case "all" -> PauseOnExceptions.ALL;
				case "uncaught" -> PauseOnExceptions.UNCAUGHT;
				case "caught" -> PauseOnExceptions.CAUGHT;
				default -> PauseOnExceptions.NONE;
			});
			return null;
		}
		case "setVariableValue": {
			final DebugFrame frame = session.frame(params.string("callFrameId"));
			final int scopeNumber = params.integer("scopeNumber");
			final String name = params.string("variableName");
			final Params newValue = params.object("newValue");
			final PausedEvent pause = session.requirePause();
			pause.call(() -> {
				final List<DebugScope> scopes = frame.scopes();
				if (scopeNumber < 0 || scopeNumber >= scopes.size()) {
					throw CdpError.invalidParams("scopeNumber " + scopeNumber);
				}
				debugger().values().setProperty(scopes.get(scopeNumber).object(), name, RuntimeDomain.argument(session, newValue));
				return null;
			});
			return null;
		}
		case "setAsyncCallStackDepth", "setBlackboxPatterns", "setBlackboxedRanges", "setBlackboxExecutionContexts",
			 "restartFrame", "searchInContent", "setScriptSource", "setReturnValue", "getStackTrace",
			 "setInstrumentationBreakpoint", "removeInstrumentationBreakpoint", "setBreakpointOnFunctionCall",
			 "pauseOnAsyncCall", "getWasmBytecode", "disassembleWasmModule":
			return null;
		default:
			throw new CdpError(CdpError.METHOD_NOT_FOUND, "'Debugger." + method + "' wasn't found");
		}
	}

	private DebugScript script(final String id) throws CdpError {
		for (final DebugScript script : debugger().scripts()) {
			if (script.id().equals(id)) {
				return script;
			}
		}
		throw CdpError.invalidParams("No script for id: " + id);
	}

	private static List<Object> locations(final List<Location> locations) {
		final List<Object> list = new ArrayList<>();
		for (final Location l : locations) {
			list.add(RemoteObjects.location(l));
		}
		return list;
	}

	/** Removes continueToLocation's breakpoint once any pause happens; tells whether it was the cause. */
	boolean consumeTemporaryBreakpoint(final PausedEvent event) {
		final String id = temporaryBreakpoint;
		if (id == null) {
			return false;
		}
		temporaryBreakpoint = null;
		debugger().removeBreakpoint(id);
		return event.hitBreakpoints().contains(id);
	}

	/** Forgets the client's breakpoints and modes when it goes. */
	void detach() {
		for (final String id : breakpointIds) {
			debugger().removeBreakpoint(id);
		}
		breakpointIds.clear();
		final String temp = temporaryBreakpoint;
		if (temp != null) {
			temporaryBreakpoint = null;
			debugger().removeBreakpoint(temp);
		}
		debugger().setPauseOnExceptions(PauseOnExceptions.NONE);
		debugger().setSkipAllPauses(false);
		debugger().setBreakpointsActive(true);
	}
}
