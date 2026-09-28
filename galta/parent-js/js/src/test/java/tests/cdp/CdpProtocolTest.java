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
package tests.cdp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.CdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import junit.framework.TestCase;

/**
 * The protocol, as a real client speaks it over a real WebSocket: enable,
 * breakpoints, pauses, frames, scopes, evaluation, stepping, exceptions -
 * end to end against {@link DebuggerImpl} through {@link CdpServer}, with no
 * shortcut past the actual wire format. Scenarios ported from a sibling
 * project's own Chrome DevTools Protocol debugger test suite (same author),
 * adapted to GaltaJS's v1 scope: one {@link DebuggerImpl} per script (not a
 * persistent, multi-script engine), so each test opens its own session
 * rather than sharing one across scripts. Not ported: console.log forwarding
 * (GaltaJS's console builtin isn't wired to Runtime.consoleAPICalled yet -
 * a documented gap) and Runtime.terminateExecution (a documented no-op - see
 * RuntimeDomain's own doc).
 */
public class CdpProtocolTest extends TestCase {

	private DebuggerImpl debugger;
	private CdpServer.Handle server;
	private CdpClient client;
	private ByteArrayOutputStream output;

	private void startSession(String className, String script) throws Exception {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(script, className + ".js");
		output = new ByteArrayOutputStream();
		debugger = new DebuggerImpl(unit, () -> {
			InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
			ctx.setOutStream(new PrintStream(output, true));
			return ctx;
		});
		server = CdpServer.open(debugger, DebugOptions.parse("127.0.0.1:0", false));
		client = new CdpClient(server.webSocketUrl());
		client.call("Runtime.enable");
		client.call("Debugger.enable");
	}

	@Override
	protected void tearDown() throws Exception {
		if (client != null) {
			client.close();
		}
		if (server != null) {
			server.close();
		}
		if (debugger != null) {
			debugger.close();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(final Object o) {
		return (Map<String, Object>) o;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(final Object o) {
		return (List<Object>) o;
	}

	private static Map<String, Object> topFrame(final Map<String, Object> paused) {
		return map(list(paused.get("callFrames")).get(0));
	}

	private static long line(final Map<String, Object> frame) {
		return ((Number) map(frame.get("location")).get("lineNumber")).longValue();
	}

	private void joinExecution() throws InterruptedException {
		debugger.getExecutionThread().join(5000);
		assertFalse(debugger.getExecutionThread().isAlive());
	}

	public void testEnableReportsScriptParsedAndSource() throws Exception {
		startSession("EnableTest", "1;\ndebugger;\n");
		debugger.start();
		final Map<String, Object> script = client.event("Debugger.scriptParsed");
		assertEquals("EnableTest.js", script.get("url"));
		// the protocol's end position is 0-based, like its start: 2 lines, then an empty third one
		assertEquals(2L, script.get("endLine"));
		assertEquals(0L, script.get("endColumn"));
		// The session ends when the script does (like Node's inspector):
		// everything below happens while the script is still paused
		client.event("Debugger.paused");
		assertEquals(client.call("Debugger.getScriptSource", "scriptId", script.get("scriptId")).get("scriptSource"), "1;\ndebugger;\n");
		client.call("Debugger.resume");
		joinExecution();
	}

	// The literal `debugger;` statement: unconditional, PauseReason.DEBUGGER_STATEMENT
	// (a real CDP reason value, not "other" - Chrome shows a distinct
	// "Paused on debugger statement" banner for it).
	public void testDebuggerStatementPauses() throws Exception {
		startSession("DebuggerStmtTest", """
				var a = 1;
				debugger;
				var b = 2;
				""");
		debugger.start();

		final Map<String, Object> paused = client.event("Debugger.paused");
		assertEquals("debuggerStatement", paused.get("reason"));
		final List<Object> frames = list(paused.get("callFrames"));
		assertFalse(frames.isEmpty());
		assertEquals(1L, line(topFrame(paused))); // 0-based line 1 - "debugger;" is 1-based line 2

		client.call("Debugger.resume");
		client.event("Debugger.resumed");
		joinExecution();
	}

	public void testBreakpointPauseFramesScopesEvaluateResume() throws Exception {
		startSession("BpTest", """
				function f(a) {
				  var b = a + 1;
				  return b * 2;
				}
				console.log(f(20));
				""");
		final Map<String, Object> set = client.call("Debugger.setBreakpointByUrl", "lineNumber", 2, "url", "BpTest.js");
		final String breakpointId = (String) set.get("breakpointId");

		try {
			client.call("Debugger.resume");
			fail("expected an error while not paused");
		} catch (final CdpClient.CdpFailure e) {
			assertEquals(-32000, e.code);
		}

		debugger.start();

		final Map<String, Object> paused = client.event("Debugger.paused");
		// CDP's own Debugger.paused.reason enum has no "breakpoint" value -
		// a breakpoint hit is reported as "other" with a non-empty
		// hitBreakpoints array (matching CdpSession.reason()'s own mapping,
		// itself matching a real client's expectations).
		assertEquals("other", paused.get("reason"));
		assertEquals(List.of(breakpointId), list(paused.get("hitBreakpoints")));
		final List<Object> frames = list(paused.get("callFrames"));
		assertTrue(frames.size() >= 1);
		final Map<String, Object> top = map(frames.get(0));
		assertEquals("f", top.get("functionName"));
		assertEquals(2L, line(top));
		final List<Object> scopes = list(top.get("scopeChain"));
		assertEquals("local", map(scopes.get(0)).get("type"));
		assertEquals("global", map(scopes.get(scopes.size() - 1)).get("type"));
		assertEquals("object", map(top.get("this")).get("type"));

		// the local scope's properties
		final String localId = (String) map(map(scopes.get(0)).get("object")).get("objectId");
		final Map<String, Object> props = client.call("Runtime.getProperties", "objectId", localId, "ownProperties", true);
		boolean sawB = false;
		for (final Object p : list(props.get("result"))) {
			if ("b".equals(map(p).get("name"))) {
				sawB = true;
				assertEquals(21L, map(map(p).get("value")).get("value"));
			}
		}
		assertTrue(props.toString(), sawB);

		// evaluate on the frame
		final Map<String, Object> eval = client.call("Debugger.evaluateOnCallFrame", "callFrameId", top.get("callFrameId"), "expression", "b * 10");
		assertEquals(210L, map(eval.get("result")).get("value"));

		client.call("Debugger.resume");
		client.event("Debugger.resumed");
		joinExecution();
		assertEquals("42", output.toString().trim());
	}

	public void testStepping() throws Exception {
		startSession("StepTest", """
				function inner(v) {
				  var r = v + 1;
				  return r;
				}
				var x = inner(1);
				x = inner(x);
				console.log(x);
				""");
		client.call("Debugger.setBreakpointByUrl", "lineNumber", 4, "url", "StepTest.js");
		debugger.start();

		Map<String, Object> paused = client.event("Debugger.paused");
		assertEquals(4L, line(topFrame(paused)));
		client.call("Debugger.stepInto");
		paused = client.event("Debugger.paused");
		assertEquals("step", paused.get("reason"));
		assertEquals("inner", topFrame(paused).get("functionName"));
		assertEquals(1L, line(topFrame(paused)));
		client.call("Debugger.stepOut");
		paused = client.event("Debugger.paused");
		// Lands back on the SAME statement that made the call ("var x =
		// inner(1);", line 4) - matching V8's own step-out convention of
		// pausing right where the call returns, not skipping ahead to the
		// next statement.
		assertEquals(4L, line(topFrame(paused)));
		client.call("Debugger.stepOver");
		paused = client.event("Debugger.paused");
		assertEquals(5L, line(topFrame(paused)));
		client.call("Debugger.resume");
		joinExecution();
		assertEquals("3", output.toString().trim());
	}

	public void testPauseOnExceptionsAndExceptionThrown() throws Exception {
		startSession("ExcTest", """
				function thrower() { throw new TypeError('boom'); }
				try { thrower(); } catch (e) { console.log(e.message); }
				""");
		client.call("Debugger.setPauseOnExceptions", "state", "all");
		debugger.start();

		final Map<String, Object> paused = client.event("Debugger.paused");
		assertEquals("exception", paused.get("reason"));
		assertEquals("error", map(paused.get("data")).get("subtype"));
		assertTrue(String.valueOf(map(paused.get("data")).get("description")).startsWith("TypeError: boom"));
		client.call("Debugger.resume");
		joinExecution();
		assertEquals("boom", output.toString().trim());
	}

	public void testRuntimeEvaluateAndProperties() throws Exception {
		startSession("RtTest", "var o = { a: 1, b: [1, 2, 3] };\ndebugger;\n");
		debugger.start();
		// The session ends when the script does (like Node's inspector):
		// everything below happens while the script is still paused
		client.event("Debugger.paused");

		final Map<String, Object> evaluated = client.call("Runtime.evaluate", "expression", "o.b", "generatePreview", true);
		final Map<String, Object> value = map(evaluated.get("result"));
		assertEquals("array", value.get("subtype"));
		final Map<String, Object> props = client.call("Runtime.getProperties", "objectId", value.get("objectId"), "ownProperties", true);
		assertEquals(4, list(props.get("result")).size()); // three elements and length
		assertEquals("[[Prototype]]", map(list(props.get("internalProperties")).get(0)).get("name"));

		final Map<String, Object> called = client.call("Runtime.callFunctionOn", "functionDeclaration", "function (n) { return this.a + n; }",
				"objectId", map(client.call("Runtime.evaluate", "expression", "o").get("result")).get("objectId"),
				"arguments", List.of(Map.of("value", 41L)));
		assertEquals(42L, map(called.get("result")).get("value"));

		final Map<String, Object> nan = map(client.call("Runtime.evaluate", "expression", "NaN").get("result"));
		assertEquals("NaN", nan.get("unserializableValue"));

		assertTrue(map(client.call("Runtime.getHeapUsage")).containsKey("usedSize"));
		assertNotNull(client.call("Runtime.getIsolateId").get("id"));
		client.call("Debugger.resume");
		joinExecution();
	}

	public void testUnknownMethodsDoNotEndTheSession() throws Exception {
		startSession("UnknownTest", "1;\ndebugger;\n");
		debugger.start();
		// The session ends when the script does (like Node's inspector):
		// everything below happens while the script is still paused
		client.event("Debugger.paused");

		try {
			client.call("Profiler.enable");
			fail("expected -32601");
		} catch (final CdpClient.CdpFailure e) {
			assertEquals(-32601, e.code);
		}
		assertEquals(Map.of(), client.call("Debugger.setAsyncCallStackDepth", "maxDepth", 32));
		client.sendRaw("this is not json");
		client.sendRaw("{\"id\": 77}");
		final Map<String, Object> response = client.rawResponse();
		assertEquals(77L, response.get("id"));
		assertEquals(-32600L, map(response.get("error")).get("code"));
		assertEquals(42L, map(client.call("Runtime.evaluate", "expression", "6 * 7").get("result")).get("value"));
		client.call("Debugger.resume");
		joinExecution();
	}
}
