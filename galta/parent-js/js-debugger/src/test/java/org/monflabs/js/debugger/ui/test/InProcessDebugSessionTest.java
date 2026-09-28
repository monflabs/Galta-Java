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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.inprocess.InProcessCdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.js.debugger.ui.model.DebugSession;
import org.monflabs.js.debugger.ui.model.PauseState;
import org.monflabs.js.debugger.ui.model.PropertyEntry;
import org.monflabs.js.debugger.ui.model.RemoteValue;
import org.monflabs.js.debugger.ui.model.ScriptInfo;

import junit.framework.TestCase;

/**
 * {@link DebugSessionTest}'s own scenarios (attach, breakpoints, pauses,
 * frames, scopes, evaluation, stepping, the staleness guard, watches)
 * against {@link InProcessCdpServer} instead of a real socket - same {@link
 * DebugSession}, only {@code attach(...)} differs. The one scenario that
 * needs adapting rather than a verbatim copy is "detach and reattach": an
 * in-process channel is one-shot (nothing to redial), so instead of
 * reattaching to the same server, this opens a NEW {@code
 * InProcessCdpServer} for the fresh script run and attaches to ITS channel -
 * still proving the client-owned breakpoint survives and re-arms across a
 * detach/attach cycle, just not literally the same server instance.
 */
public class InProcessDebugSessionTest extends TestCase {
	private static final long TIMEOUT = 20;

	private DebuggerImpl debugger;
	private InProcessCdpServer.Handle server;
	private PumpExecutor ui;
	private DebugSession session;
	private Recorder recorder;

	/** An executor that queues tasks for the test thread to pump - the "EDT" here. */
	private static final class PumpExecutor implements Executor {
		final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
		@Override
		public void execute(final Runnable task) {
			tasks.add(task);
		}
		void pump() {
			Runnable task;
			while ((task = tasks.poll()) != null) {
				task.run();
			}
		}
	}

	/** Records what the session tells its listener. */
	private static final class Recorder implements DebugSession.SessionListener {
		final List<ScriptInfo> scripts = new ArrayList<>();
		volatile PauseState lastPause;

		@Override public void scriptAdded(final ScriptInfo s) { scripts.add(s); }
		@Override public void paused(final PauseState p) { lastPause = p; }
		@Override public void resumed() { lastPause = null; }
	}

	@Override
	protected void setUp() {
		ui = new PumpExecutor();
		session = new DebugSession(ui);
		recorder = new Recorder();
		session.addListener(recorder);
	}

	@Override
	protected void tearDown() {
		session.close();
		ui.pump();
		if (server != null) {
			server.close();
		}
		if (debugger != null) {
			debugger.close();
		}
	}

	// ---- driving helpers ----

	private void startServer(final String className, final String script) throws Exception {
		final JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		final JSInterpretedUnit unit = env.createScript(script, className + ".js");
		debugger = new DebuggerImpl(unit, () -> new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));
		server = InProcessCdpServer.open(debugger, DebugOptions.parse("", false));
	}

	/** Pumps the ui queue until a condition holds or the timeout elapses. */
	private void pumpUntil(final BooleanSupplier done) {
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
		while (System.nanoTime() < deadline) {
			ui.pump();
			if (done.getAsBoolean()) {
				return;
			}
			try {
				Thread.sleep(5);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		ui.pump();
		if (!done.getAsBoolean()) {
			throw new AssertionError("condition not met within " + TIMEOUT + "s");
		}
	}

	private void attach() {
		session.attach(server.clientChannel());
		pumpUntil(() -> session.state() == DebugSession.State.RUNNING);
	}

	/**
	 * Waits for a breakpoint's own arm round trip to be confirmed by the
	 * server. Starting the debugged script right after
	 * {@code toggleBreakpoint()} without this races the in-flight
	 * {@code Debugger.setBreakpointByUrl} request - harmless most of the
	 * time (the script's first statement gives the request time to land),
	 * but confirmed flaky under load.
	 */
	private void waitArmed(final String url) {
		pumpUntil(() -> session.breakpointsFor(url).stream().anyMatch(b -> b.serverId() != null));
	}

	private void joinExecution() throws InterruptedException {
		debugger.getExecutionThread().join(TIMEOUT * 1000);
		assertFalse(debugger.getExecutionThread().isAlive());
	}

	private <T> T await(final CompletableFuture<T> future) {
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
		while (System.nanoTime() < deadline) {
			ui.pump();
			if (future.isDone()) {
				return future.getNow(null);
			}
			try {
				Thread.sleep(5);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		throw new AssertionError("future not done within " + TIMEOUT + "s");
	}

	// ---- tests ----

	/**
	 * The socket-based {@link DebugSessionTest#testAttachAfterStartReplaysTheScript}
	 * counterpart does not apply here: an in-process {@link InProcessCdpServer}
	 * handle is a one-shot pipe to the ONE client its {@code Handle} was ever
	 * handed (see its own doc), created and listening from the moment
	 * {@code open()} is called - unlike a socket server, which stays bound and
	 * listening on its port regardless of whether the debugged script has
	 * finished, so a later socket connection can still reach it and see a
	 * replay. Once the script finishes with nobody having attached yet,
	 * CdpSession.executionFinished() closes this one-shot pipe (the same one
	 * and only client would ever have used), so attach() now correctly fails
	 * fast - matching a real process that already exited - instead of hanging
	 * forever waiting for a reply nothing will ever send.
	 */
	public void testAttachAfterFinishFailsFastRatherThanHanging() throws Exception {
		startServer("SessReplay", "1;\n");
		debugger.start();
		joinExecution();
		session.attach(server.clientChannel());
		pumpUntil(() -> session.state() == DebugSession.State.DETACHED);
	}

	public void testABreakpointSetBeforeTheScriptHitsAfterRun() throws Exception {
		startServer("SessBp", "var a = 1;\na = a + 1;\na;\n");
		attach();
		// the script has not started yet; set the breakpoint by url
		session.toggleBreakpoint("SessBp.js", 1);
		waitArmed("SessBp.js");
		debugger.start();
		pumpUntil(() -> recorder.lastPause != null);
		assertEquals(DebugSession.State.PAUSED, session.state());
		assertEquals(1, recorder.lastPause.frames().get(0).line());
		session.resume();
		joinExecution();
	}

	public void testFramesScopesEvaluateAndProperties() throws Exception {
		startServer("SessFrames", """
				function f(a) {
				  var b = a + 1;
				  return b * 2;
				}
				f(20);
				""");
		attach();
		session.toggleBreakpoint("SessFrames.js", 2);
		waitArmed("SessFrames.js");
		debugger.start();
		pumpUntil(() -> recorder.lastPause != null);

		// evaluate on the frame sees the local
		final RemoteValue result = await(session.evaluate("b * 10"));
		assertEquals(210, ((Number) result.value()).intValue());

		// the scope chain has a local scope with an object we can read
		final RemoteValue localScope = recorder.lastPause.frames().get(0).scopeChain().get(0).object();
		assertNotNull(localScope.objectId());
		final List<PropertyEntry> props = await(session.properties(localScope.objectId()));
		assertTrue(props.toString(), props.stream().anyMatch(p -> "b".equals(p.name())));

		session.resume();
		joinExecution();
	}

	public void testStalePropertyResultsAreDroppedAfterResume() throws Exception {
		startServer("SessStale", """
				function f() {
				  var o = { a: 1 };
				  return o;
				}
				f();
				""");
		attach();
		session.toggleBreakpoint("SessStale.js", 2);
		waitArmed("SessStale.js");
		debugger.start();
		pumpUntil(() -> recorder.lastPause != null);
		final RemoteValue scope = recorder.lastPause.frames().get(0).scopeChain().get(0).object();
		// resume first, then ask for properties captured against the old pause
		session.resume();
		joinExecution();
		// not RUNNING specifically: f() was the script's last statement, so
		// resuming past it lets the script finish - the connection then
		// closes itself (see CdpSession.executionFinished()) and the state
		// goes straight to DETACHED, same as this assertion's own intent
		// ("we're no longer paused, so a stale fetch predates the resume")
		pumpUntil(() -> session.state() != DebugSession.State.PAUSED);
		final List<PropertyEntry> props = await(session.properties(scope.objectId()));
		assertTrue("expected the stale fetch to be dropped, got " + props, props.isEmpty());
	}

	public void testSteppingAdvancesLineByLine() throws Exception {
		startServer("SessStep", """
				function inner(v) {
				  var r = v + 1;
				  return r;
				}
				var x = inner(1);
				x = inner(x);
				x;
				""");
		attach();
		session.toggleBreakpoint("SessStep.js", 4);
		waitArmed("SessStep.js");
		debugger.start();
		pumpUntil(() -> recorder.lastPause != null);
		assertEquals(4, recorder.lastPause.frames().get(0).line());
		session.stepInto();
		pumpUntil(() -> recorder.lastPause != null && recorder.lastPause.frames().get(0).line() == 1);
		assertEquals("inner", recorder.lastPause.frames().get(0).functionName());
		session.stepOut();
		pumpUntil(() -> recorder.lastPause != null && recorder.lastPause.frames().get(0).line() == 4);
		session.stepOver();
		pumpUntil(() -> recorder.lastPause != null && recorder.lastPause.frames().get(0).line() == 5);
		session.resume();
		joinExecution();
	}

	public void testDetachAndReattachToAFreshSessionReArmsBreakpoints() throws Exception {
		final String script = "var a = 1;\na = a + 1;\na;\n";
		final String url = "SessRearm.js";

		startServer("SessRearm", script);
		attach();
		session.toggleBreakpoint(url, 1);
		waitArmed(url);
		debugger.start();
		pumpUntil(() -> recorder.lastPause != null);
		session.resume();
		joinExecution();
		// not RUNNING specifically: resuming past the last statement lets the
		// script finish, which closes the connection on its own (see
		// CdpSession.executionFinished()) - detach() below is then a no-op,
		// which is fine, the session ends up DETACHED either way
		pumpUntil(() -> session.state() != DebugSession.State.PAUSED);

		session.detach();
		pumpUntil(() -> session.state() == DebugSession.State.DETACHED);
		// the breakpoint definition survives on the client
		assertEquals(1, session.breakpoints().size());
		server.close();
		debugger.close();

		// a fresh run of a script at the SAME url (as a real client sees it -
		// e.g. the host re-ran the same file), over a BRAND NEW in-process
		// server (a fresh InProcessCdpServer.open() call - unlike the
		// socket-based test this mirrors, an in-process channel is one-shot,
		// so there is no "reconnect to the same server" to do): attaching to
		// the new channel re-arms the client-owned breakpoint with no
		// further action from the client
		recorder.lastPause = null;
		startServer("SessRearm", script);
		attach();
		// wait for the re-arm's own round trip (enableAndArm()) to be
		// confirmed before running the script
		waitArmed(url);
		debugger.start();
		pumpUntil(() -> recorder.lastPause != null);
		assertEquals(1, recorder.lastPause.frames().get(0).line());
		session.resume();
		joinExecution();
	}

	public void testDetachWhilePausedResumesTheScript() throws Exception {
		startServer("SessHang", "var a = 1;\na;\n");
		attach();
		session.toggleBreakpoint("SessHang.js", 0);
		waitArmed("SessHang.js");
		debugger.start();
		pumpUntil(() -> recorder.lastPause != null);
		assertEquals(DebugSession.State.PAUSED, session.state());
		// detach must resume first, or the execution thread would hang forever
		session.detach();
		ui.pump();
		joinExecution();
	}

	// Re-attaching before the old connection's close was delivered: that stale
	// close must not detach the session from its new, live connection
	public void testStaleCloseAfterReattachIsIgnored() throws Exception {
		startServer("SessStaleA", "var a = 1;\n");
		attach();
		final InProcessCdpServer.Handle oldServer = server;
		final DebuggerImpl oldDebugger = debugger;
		startServer("SessStaleB", "var b = 1;\n");
		// no pumping in between: the old connection's onClosed is still queued
		session.attach(server.clientChannel());
		pumpUntil(() -> session.state() == DebugSession.State.RUNNING);
		ui.pump();
		assertEquals(DebugSession.State.RUNNING, session.state());
		oldServer.close();
		oldDebugger.close();
		ui.pump();
		assertEquals(DebugSession.State.RUNNING, session.state());
	}

	public void testWatchesAreRememberedInOrder() {
		session.addWatch("a + b");
		session.addWatch("c");
		session.addWatch("a + b");   // duplicate ignored
		assertEquals(List.of("a + b", "c"), session.watches());
		session.removeWatch("c");
		assertEquals(List.of("a + b"), session.watches());
	}
}
