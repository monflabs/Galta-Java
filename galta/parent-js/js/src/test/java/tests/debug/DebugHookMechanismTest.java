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
package tests.debug;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.debug.api.impl.DebugHook;
import org.monflabs.galtajs.debug.api.impl.DebugLocation;
import org.monflabs.galtajs.debug.api.impl.DebugRuntime;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSGlobalContext.RunningState;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import junit.framework.TestCase;
import util.GlobalTestEnvironment;

/**
 * Exercises the CDP-oriented instrumentation mechanism
 * (JSEnvironment.Builder.debug(true), ASTDebugHook, DebugHook) at the
 * lowest level - directly, without any facade or CDP transport on top - to
 * prove out the pause/resume/breakpoint/step primitives the {@code debug.api}
 * facade and the CDP module will be built on.
 */
public class DebugHookMechanismTest extends TestCase {

	private static final String SCRIPT = """
			var out = [];
			for(let i=0; i<3; i++) {
			  out.push(i);
			}
			console.log(out.join(","));
			""";

	// A minimal stand-in for what DebuggerImpl will eventually be: pauses on
	// every statement (breakOnEachStatement-style), records the line hit,
	// and only resumes when told to.
	private static class RecordingHook implements DebugHook {
		final BlockingQueue<Integer> pausedLines = new ArrayBlockingQueue<>(64);
		final List<Integer> allLinesSeen = new ArrayList<>();
		volatile boolean breakOnEachStatement = true;

		@Override
		public boolean onStatement(JSRuntimeContext context, DebugLocation location) {
			int line = location.line();
			allLinesSeen.add(line);
			// Only genuine statement-boundary wraps count as pause points -
			// a sub-expression wrap (a call argument, an if/for test/update
			// clause) on the same line must not double-fire a line-based
			// breakpoint/step, matching CDP's own statement-granularity model.
			if (breakOnEachStatement && location.statementLevel()) {
				pausedLines.add(line);
				return true;
			}
			return false;
		}

		@Override
		public boolean onExit(JSRuntimeContext context, DebugLocation location) {
			return false;
		}

		@Override
		public boolean onExceptionThrown(JSRuntimeContext context, JSRuntimeException t, DebugLocation location) {
			return false;
		}

		@Override
		public boolean onWoken() {
			return false;
		}

		@Override
		public void onStateChanged(JSGlobalContext context, RunningState state) {
		}
	}

	public void testPauseAndResumeAtEveryStatement() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(SCRIPT, "DebugHookMechanismTest.js");

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		InterpretedGlobalRuntimeContext gctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		gctx.setOutStream(new PrintStream(captured, true));

		RecordingHook hook = new RecordingHook();
		gctx.setDebugHook(hook);

		DebugRuntime.sessionStarted();
		Thread executionThread = new Thread(() -> unit.executeWithContext(gctx), "DebugHookMechanismTest-exec");
		try {
			executionThread.start();

			// Drain every pause and resume it, until the script finishes.
			int resumedCount = 0;
			while (executionThread.isAlive() || !hook.pausedLines.isEmpty()) {
				Integer line = hook.pausedLines.poll(2, java.util.concurrent.TimeUnit.SECONDS);
				if (line == null) {
					break;
				}
				resumedCount++;
				synchronized (hook) {
					hook.notify();
				}
			}
			executionThread.join(5000);
			assertFalse("script execution thread should have finished", executionThread.isAlive());

			// var out=[] (1) + the for-loop's own header, "for(...) {" (1,
			// entered once - see NodeFactory.createDebugHookStatement()'s
			// own doc for why this now gets its own statement-level wrap,
			// unlike a bare block's opening brace) + out.push(i) once per
			// iteration (3) + console.log(...) (1) = 6 statement-level
			// pauses. The for-loop's OWN test/update sub-expression wraps
			// are not statement-level, so they don't contribute extra pauses.
			assertEquals(6, resumedCount);
			assertEquals("0,1,2", captured.toString().trim());
		} finally {
			DebugRuntime.sessionEnded();
		}
	}

	public void testNoDebuggerAttachedRunsNormally() throws Exception {
		// Same debug(true)-compiled script, but with ACTIVE_SESSIONS==0 (no
		// session anywhere) - must run to completion untouched, proving the
		// fast path costs nothing beyond the single int compare.
		JSEnvironment env = GlobalTestEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(SCRIPT, "DebugHookMechanismTest2.js");

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		InterpretedGlobalRuntimeContext gctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		gctx.setOutStream(new PrintStream(captured, true));

		unit.executeWithContext(gctx);

		assertEquals("0,1,2", captured.toString().trim());
	}

	public void testBreakpointLineMatchOnly() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(SCRIPT, "DebugHookMechanismTest3.js");

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		InterpretedGlobalRuntimeContext gctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		gctx.setOutStream(new PrintStream(captured, true));

		int pushLine = 3; // "out.push(i);"
		RecordingHook hook = new RecordingHook() {
			@Override
			public boolean onStatement(JSRuntimeContext context, DebugLocation location) {
				int line = location.line();
				allLinesSeen.add(line);
				if (line == pushLine && location.statementLevel()) {
					pausedLines.add(line);
					return true;
				}
				return false;
			}
		};
		hook.breakOnEachStatement = false;
		gctx.setDebugHook(hook);

		DebugRuntime.sessionStarted();
		Thread executionThread = new Thread(() -> unit.executeWithContext(gctx), "DebugHookMechanismTest3-exec");
		try {
			executionThread.start();
			int hits = 0;
			while (executionThread.isAlive() || !hook.pausedLines.isEmpty()) {
				Integer line = hook.pausedLines.poll(2, java.util.concurrent.TimeUnit.SECONDS);
				if (line == null) {
					break;
				}
				hits++;
				synchronized (hook) {
					hook.notify();
				}
			}
			executionThread.join(5000);
			assertFalse(executionThread.isAlive());

			// The loop body's push() runs 3 times (i=0,1,2) - the breakpoint
			// line should have fired exactly 3 times, no more, no less.
			assertEquals(3, hits);
			assertEquals("0,1,2", captured.toString().trim());
		} finally {
			DebugRuntime.sessionEnded();
		}
	}
}
