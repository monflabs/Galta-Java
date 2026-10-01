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

package org.monflabs.js.debugger.ui.test;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.inprocess.InProcessCdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.js.debugger.ui.cdp.CdpConnection;
import org.monflabs.js.debugger.ui.cdp.CdpException;
import org.monflabs.js.debugger.ui.cdp.Json;

import junit.framework.TestCase;

/**
 * {@link CdpConnectionTest}'s own scenarios (request/response, protocol
 * errors, events, close) against {@link InProcessCdpServer} instead of a
 * real socket - same {@link CdpConnection}, only the channel underneath it
 * differs. Deliberately does NOT include a "second client is refused as
 * busy" counterpart: {@code CdpConnectionTest.testASecondClientIsRefusedAsBusy}
 * relies on a discoverable {@code ws://} url a second party can dial while
 * the first is still attached - an in-process channel has no such url, only
 * the one {@code InProcessCdpServer.Handle} its own {@code open()} call
 * returned, so that failure mode simply does not exist for this transport
 * (not an intentional gap to fill in later).
 */
public class InProcessCdpConnectionTest extends TestCase {
	private static final long TIMEOUT = 20;

	private DebuggerImpl debugger;
	private InProcessCdpServer.Handle server;

	private void startServer(final String className, final String script) throws Exception {
		final JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		final JSInterpretedUnit unit = env.createScript(script, className + ".js");
		debugger = new DebuggerImpl(unit, () -> new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));
		server = InProcessCdpServer.open(debugger, DebugOptions.parse("", false));
	}

	@Override
	protected void tearDown() throws Exception {
		if (server != null) {
			server.close();
		}
		if (debugger != null) {
			debugger.close();
		}
	}

	/** A test listener that queues events and remembers the close reason. */
	private static final class Events implements CdpConnection.Listener {
		final LinkedBlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
		final AtomicReference<String> closed = new AtomicReference<>();

		@Override
		public void onEvent(final String method, final Map<String, Object> params) {
			queue.add(Json.object("method", method, "params", params));
		}

		@Override
		public void onClosed(final String reason) {
			closed.set(reason);
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> next(final String method) throws InterruptedException {
			while (true) {
				final Map<String, Object> event = queue.poll(TIMEOUT, TimeUnit.SECONDS);
				if (event == null) {
					throw new AssertionError("no " + method + " event within " + TIMEOUT + "s");
				}
				if (method.equals(event.get("method"))) {
					return (Map<String, Object>) event.get("params");
				}
			}
		}
	}

	private CdpConnection connect(final Events events) {
		return CdpConnection.open(server.clientChannel(), events);
	}

	/** Runs a call expected to fail and returns the CdpException it carried. */
	private static CdpException failureOf(final Callable call) {
		try {
			call.run();
		} catch (final java.util.concurrent.ExecutionException e) {
			return (CdpException) e.getCause();
		} catch (final Exception e) {
			throw new AssertionError("unexpected exception", e);
		}
		throw new AssertionError("expected a failure");
	}

	private interface Callable {
		void run() throws Exception;
	}

	public void testCallsRoundTripAndErrorsSurface() throws Exception {
		// NOT a bare literal statement ("1;\n"): the constant-folding
		// optimizer evaluates a whole sole-literal program directly during
		// createScript() itself, before any debugger is attached - the real
		// execution thread then has nothing left to walk, so it runs to
		// completion instantly regardless of pauseOnStart(), closing the
		// connection (see CdpSession.executionFinished()) before the call
		// below gets a chance to run. A call expression is never
		// constant-folded, so this script genuinely reaches, and pauses at,
		// its own first (and only) statement at real execution time.
		startServer("ConnRoundTrip", "f();\nfunction f() {}\n");
		final Events events = new Events();
		try (CdpConnection connection = connect(events)) {
			connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
			connection.call("Debugger.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
			// paused, not left to run to completion - see the script's own
			// comment above for why an unpaused script can't be relied on to
			// still be connected by the time the call below runs
			debugger.pauseOnStart();
			debugger.start();
			// starting fires both events to the now-enabled client
			assertNotNull(events.next("Runtime.executionContextCreated").get("context"));

			// an unknown method is a protocol error, code -32601
			final CdpException cdp = failureOf(() -> connection.call("Nope.doesNotExist", null).get(TIMEOUT, TimeUnit.SECONDS));
			assertEquals(-32601, cdp.code());
			assertFalse(cdp.isTransport());
		}
		debugger.getExecutionThread().join(TIMEOUT * 1000);
	}

	public void testEventsArriveOnTheListener() throws Exception {
		startServer("ConnEvents", "1;\n");
		final Events events = new Events();
		try (CdpConnection connection = connect(events)) {
			connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
			connection.call("Debugger.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
			debugger.start();
			final Map<String, Object> script = events.next("Debugger.scriptParsed");
			assertEquals("ConnEvents.js", script.get("url"));
		}
		debugger.getExecutionThread().join(TIMEOUT * 1000);
	}

	public void testCloseFailsPendingAndTellsTheListener() throws Exception {
		startServer("ConnClose", "1;\n");
		final Events events = new Events();
		final CdpConnection connection = connect(events);
		connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
		connection.close();
		assertNotNull(events.closed.get());
		final CdpException cdp = failureOf(() -> connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS));
		assertEquals(CdpException.TRANSPORT_CLOSED, cdp.code());
	}

	// A failed send fails only its own call: the next calls still go out
	public void testAFailedSendDoesNotPoisonLaterCalls() throws Exception {
		final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
		final AtomicReference<org.monflabs.galtajs.cdp.CdpClientChannel.ChannelListener> peer = new AtomicReference<>();
		final org.monflabs.galtajs.cdp.CdpClientChannel channel = new org.monflabs.galtajs.cdp.CdpClientChannel() {
			@Override
			public void listen(final ChannelListener listener) {
				peer.set(listener);
			}
			@Override
			public java.util.concurrent.CompletionStage<?> sendText(final String text) {
				if (sends.getAndIncrement() == 0) {
					return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("first send fails"));
				}
				// Echo an empty result for the request's id
				final Object id = ((Map<?, ?>)Json.parse(text)).get("id");
				peer.get().onText(Json.write(Json.object("id", id, "result", Json.object())), true);
				return java.util.concurrent.CompletableFuture.completedFuture(null);
			}
			@Override
			public void requestClose() {
			}
		};
		final CdpConnection connection = CdpConnection.open(channel, new Events());
		final CdpException cdp = failureOf(() -> connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS));
		assertEquals(CdpException.TRANSPORT_CLOSED, cdp.code());
		assertNotNull(connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS));
		assertNotNull(connection.call("Debugger.enable", null).get(TIMEOUT, TimeUnit.SECONDS));
	}
}
