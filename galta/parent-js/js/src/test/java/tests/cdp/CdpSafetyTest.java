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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.CdpServer;
import org.monflabs.galtajs.cdp.inprocess.InProcessCdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import junit.framework.TestCase;

/**
 * Protocol behaviours that keep a session safe: no evaluation racing the
 * running script, a disabled debugger leaving nothing paused, scope variables
 * that can really be set, and a WebSocket server that follows RFC 6455.
 */
public class CdpSafetyTest extends TestCase {

	private DebuggerImpl debugger;
	private InProcessCdpServer.Handle server;
	private InProcessCdpClient client;
	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	private DebuggerImpl newDebugger(String name, String script) {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(script, name);
		return new DebuggerImpl(unit, () -> {
			InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
			ctx.setOutStream(new PrintStream(output, true));
			return ctx;
		});
	}

	private void startSession(String name, String script) throws Exception {
		debugger = newDebugger(name, script);
		server = InProcessCdpServer.open(debugger, DebugOptions.parse("", false));
		client = new InProcessCdpClient(server.clientChannel());
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

	private void joinExecution() throws InterruptedException {
		debugger.getExecutionThread().join(5000);
		assertFalse(debugger.getExecutionThread().isAlive());
	}

	// start() only starts the execution thread: it returns before the script has run
	// its first statement. A Debugger.pause sent right away pauses there, not where the
	// test means to pause, so wait for what the script prints on its way.
	private void awaitOutput(String text) throws InterruptedException {
		for (int i = 0; i < 500 && !output.toString().contains(text); i++) {
			Thread.sleep(10);
		}
		assertTrue(output.toString(), output.toString().contains(text));
	}

	// While the script runs, its objects belong to its thread: an evaluation
	// is refused rather than run concurrently from the connection's thread
	public void testEvaluateWhileRunningIsRefused() throws Exception {
		startSession("Busy.js", "var stop = false;\nconsole.log('running');\nwhile (!stop) {\n  stop = stop;\n}\nconsole.log('done');\n");
		debugger.start();
		awaitOutput("running");
		try {
			client.call("Runtime.evaluate", "expression", "stop = true");
			fail("expected an error while the script runs");
		} catch (final CdpClient.CdpFailure e) {
			assertEquals(-32000, e.code);
		}
		client.call("Debugger.pause");
		client.event("Debugger.paused");
		client.call("Runtime.evaluate", "expression", "stop = true");
		client.call("Debugger.resume");
		joinExecution();
		assertEquals("running\ndone", output.toString().trim());
	}

	// An evaluation run for a client never pauses, even on `debugger;`
	public void testEvaluationNeverPauses() throws Exception {
		startSession("Eval.js", "var a = 1;\ndebugger;\nconsole.log(a);\n");
		debugger.start();
		final Map<String, Object> paused = client.event("Debugger.paused");
		final String frameId = (String) map(list(paused.get("callFrames")).get(0)).get("callFrameId");
		final Map<String, Object> eval = client.call("Debugger.evaluateOnCallFrame", "callFrameId", frameId, "expression", "debugger; a + 41");
		assertEquals(42L, map(eval.get("result")).get("value"));
		client.call("Debugger.resume");
		joinExecution();
	}

	// Debugger.disable resumes, and a disabled session never leaves a pause behind
	public void testDisableResumes() throws Exception {
		startSession("Disable.js", "var a = 1;\ndebugger;\ndebugger;\nconsole.log(a);\n");
		debugger.start();
		client.event("Debugger.paused");
		client.call("Debugger.disable");
		joinExecution();
		assertEquals("1", output.toString().trim());
	}

	// A local variable can be set through its scope
	public void testSetVariableValueOnLocalScope() throws Exception {
		startSession("SetVar.js", "function f() {\n  var b = 1;\n  debugger;\n  return b;\n}\nconsole.log(f());\n");
		debugger.start();
		final Map<String, Object> paused = client.event("Debugger.paused");
		final String frameId = (String) map(list(paused.get("callFrames")).get(0)).get("callFrameId");
		client.call("Debugger.setVariableValue", "callFrameId", frameId, "scopeNumber", 0, "variableName", "b", "newValue", Map.of("value", 99L));
		try {
			client.call("Debugger.setVariableValue", "callFrameId", frameId, "scopeNumber", 0, "variableName", "nope", "newValue", Map.of("value", 1L));
			fail("expected an error for an unknown variable");
		} catch (final CdpClient.CdpFailure e) {
			assertEquals(-32000, e.code);
		}
		client.call("Debugger.resume");
		joinExecution();
		assertEquals("99", output.toString().trim());
	}

	// {"value": null} is null; an argument without a value is undefined
	public void testNullAndMissingArguments() throws Exception {
		startSession("Args.js", "var o = {};\ndebugger;\n");
		debugger.start();
		client.event("Debugger.paused");
		final Object objectId = map(client.call("Runtime.evaluate", "expression", "o").get("result")).get("objectId");
		final java.util.Map<String, Object> nullArg = new java.util.HashMap<>();
		nullArg.put("value", null);
		final Map<String, Object> called = client.call("Runtime.callFunctionOn",
				"functionDeclaration", "function (a, b) { return (a === null) + ',' + (b === undefined); }",
				"objectId", objectId, "arguments", List.of(nullArg, Map.of()));
		assertEquals("true,true", map(called.get("result")).get("value"));
		client.call("Debugger.resume");
		joinExecution();
	}

	// RFC 6455 section 5.1: an unmasked client frame closes the connection
	public void testUnmaskedFrameIsRejected() throws Exception {
		debugger = newDebugger("Ws.js", "1;\n");
		final CdpServer.Handle ws = CdpServer.open(debugger, DebugOptions.parse("127.0.0.1:0", false));
		try {
			final java.net.URI uri = java.net.URI.create(ws.webSocketUrl());
			try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
				socket.setSoTimeout(5000);
				final OutputStream out = socket.getOutputStream();
				final InputStream in = socket.getInputStream();
				out.write(("GET " + uri.getPath() + " HTTP/1.1\r\nHost: " + uri.getHost() + ":" + uri.getPort()
						+ "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n")
						.getBytes(StandardCharsets.ISO_8859_1));
				out.flush();
				final String status = readLine(in);
				assertTrue(status, status.startsWith("HTTP/1.1 101"));
				while (!readLine(in).isEmpty()) {
					// headers
				}
				final byte[] text = "{}".getBytes(StandardCharsets.UTF_8);
				out.write(new byte[] { (byte) 0x81, (byte) text.length });
				out.write(text);
				out.flush();
				final int b0 = in.read();
				assertEquals("a close frame", 0x88, b0);
				final int length = in.read() & 0x7f;
				assertTrue(length >= 2);
				assertEquals(1002, (in.read() << 8) | in.read());
			}
		} finally {
			ws.close();
		}
	}

	// A request with an unreasonable number of header lines is refused
	public void testTooManyHeaders() throws Exception {
		debugger = newDebugger("Hdr.js", "1;\n");
		final CdpServer.Handle ws = CdpServer.open(debugger, DebugOptions.parse("127.0.0.1:0", false));
		try {
			final java.net.URI uri = java.net.URI.create(ws.webSocketUrl());
			try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
				socket.setSoTimeout(5000);
				final StringBuilder request = new StringBuilder("GET /json HTTP/1.1\r\nHost: 127.0.0.1\r\n");
				for (int i = 0; i < 500; i++) {
					request.append("X-Filler-").append(i).append(": x\r\n");
				}
				request.append("\r\n");
				socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
				socket.getOutputStream().flush();
				// no response: the connection is dropped (a reset, when unread
				// header lines remain on the server side)
				try {
					assertEquals(-1, socket.getInputStream().read());
				} catch (final java.net.SocketException reset) {
					// dropped
				}
			}
		} finally {
			ws.close();
		}
	}

	private static String readLine(final InputStream in) throws Exception {
		final StringBuilder b = new StringBuilder();
		int c;
		while ((c = in.read()) >= 0 && c != '\n') {
			if (c != '\r') {
				b.append((char) c);
			}
		}
		return b.toString();
	}
}
