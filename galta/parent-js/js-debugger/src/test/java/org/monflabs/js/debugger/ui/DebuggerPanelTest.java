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

package org.monflabs.js.debugger.ui;

import java.awt.Font;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.CdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.js.debugger.ui.model.DebugSession;

import junit.framework.TestCase;

/**
 * The panel headless (no JFrame, as {@code java.awt.headless=true} requires):
 * it composes, attaches to a real GaltaJS CDP server, reflects a paused run,
 * and cleans up. Rewritten from a sibling Nashorn-fork project's own
 * {@code debugger-ui} test suite (same author) against GaltaJS's
 * {@code org.monflabs.galtajs.cdp.CdpServer} /
 * {@link org.monflabs.galtajs.debug.api.impl.DebuggerImpl}: each test opens
 * its own server for its own script, since GaltaJS's v1 debugger is one
 * script per session rather than a persistent, long-lived engine.
 */
public class DebuggerPanelTest extends TestCase {
	private static final long TIMEOUT = 20;

	private DebuggerImpl debugger;
	private CdpServer.Handle server;
	private DebuggerPanel panel;

	private void startServer(final String className, final String script) throws Exception {
		final JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		final JSInterpretedUnit unit = env.createScript(script, className + ".js");
		debugger = new DebuggerImpl(unit, () -> new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));
		server = CdpServer.open(debugger, DebugOptions.parse("127.0.0.1:0", false));
	}

	@Override
	protected void setUp() throws Exception {
		onEdt(() -> panel = new DebuggerPanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), false));
	}

	@Override
	protected void tearDown() throws Exception {
		onEdt(() -> panel.close());
		if (server != null) {
			server.close();
		}
		if (debugger != null) {
			debugger.close();
		}
	}

	private static void onEdt(final Runnable r) throws Exception {
		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
		} else {
			SwingUtilities.invokeAndWait(r);
		}
	}

	/** A value read from the EDT. */
	private static <T> T onEdtGet(final Callable<T> c) throws Exception {
		final AtomicReference<T> ref = new AtomicReference<>();
		final AtomicReference<Exception> err = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			try {
				ref.set(c.call());
			} catch (final Exception e) {
				err.set(e);
			}
		});
		if (err.get() != null) {
			throw err.get();
		}
		return ref.get();
	}

	private void waitUntil(final EdtCondition condition) throws Exception {
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
		while (System.nanoTime() < deadline) {
			if (onEdtGet(condition::holds)) {
				return;
			}
			Thread.sleep(10);
		}
		throw new AssertionError("condition not met within " + TIMEOUT + "s");
	}

	private interface EdtCondition {
		boolean holds();
	}

	/**
	 * Waits for a breakpoint's own arm round trip to be confirmed by the
	 * server, so starting the debugged script right after
	 * {@code toggleBreakpoint()} cannot race the in-flight
	 * {@code Debugger.setBreakpointByUrl} request.
	 */
	private void waitArmed(final String url) throws Exception {
		waitUntil(() -> panel.session().breakpointsFor(url).stream().anyMatch(b -> b.serverId() != null));
	}

	public void testThePanelComposes() throws Exception {
		assertNotNull(panel);
		assertTrue(onEdtGet(() -> panel.getComponentCount() > 0));
	}

	public void testAttachRunAndPauseReflectsInTheStack() throws Exception {
		startServer("PanelBp", "var a = 1;\na = a + 1;\na;\n");
		final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
		onEdt(() -> panel.onConnectionChange((state, detail) -> connState.set(state)));
		onEdt(() -> panel.attach(server.webSocketUrl()));
		waitUntil(() -> connState.get() == DebuggerPanel.ConnectionState.CONNECTED);

		// set a breakpoint by url, then run the debugged script
		onEdt(() -> panel.session().toggleBreakpoint("PanelBp.js", 1));
		waitArmed("PanelBp.js");
		debugger.start();

		waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
		assertEquals(Integer.valueOf(1), onEdtGet(() -> panel.session().pauseState().frames().get(0).line()));

		onEdt(() -> panel.session().resume());
		debugger.getExecutionThread().join(TIMEOUT * 1000);
		assertFalse(debugger.getExecutionThread().isAlive());
	}

	public void testDetachWhilePausedReleasesTheScript() throws Exception {
		startServer("PanelHang", "var a = 1;\na;\n");
		final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
		onEdt(() -> panel.onConnectionChange((state, detail) -> connState.set(state)));
		onEdt(() -> panel.attach(server.webSocketUrl()));
		waitUntil(() -> connState.get() == DebuggerPanel.ConnectionState.CONNECTED);

		onEdt(() -> panel.session().toggleBreakpoint("PanelHang.js", 0));
		waitArmed("PanelHang.js");
		debugger.start();
		waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
		onEdt(() -> panel.detach());
		debugger.getExecutionThread().join(TIMEOUT * 1000);   // released only if detach resumed
		assertFalse(debugger.getExecutionThread().isAlive());
	}

	/**
	 * Mirrors an embedder's "launch a debugger for this script" flow (the
	 * playground's own {@code debugNow()}): request a pause at the very
	 * first statement, attach, and only start the script once the panel is
	 * actually connected. {@code Debugger.enable} does not replay an
	 * already-in-progress pause, so a pause requested before any client
	 * exists must still reach the client that attaches afterward.
	 */
	public void testPauseOnStartBeforeAttachIsDeliveredOnceConnected() throws Exception {
		startServer("PanelPauseOnStart", "var a = 1;\na;\n");
		debugger.pauseOnStart();

		final AtomicBoolean started = new AtomicBoolean();
		onEdt(() -> panel.onConnectionChange((state, detail) -> {
			if (state == DebuggerPanel.ConnectionState.CONNECTED && started.compareAndSet(false, true)) {
				debugger.start();
			}
		}));
		onEdt(() -> panel.attach(server.webSocketUrl()));

		waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
		assertEquals(Integer.valueOf(0), onEdtGet(() -> panel.session().pauseState().frames().get(0).line()));

		onEdt(() -> panel.session().resume());
		debugger.getExecutionThread().join(TIMEOUT * 1000);
		assertFalse(debugger.getExecutionThread().isAlive());
	}
}
