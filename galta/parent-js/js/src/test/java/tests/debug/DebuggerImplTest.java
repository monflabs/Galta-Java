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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.debug.api.BreakpointRequest;
import org.monflabs.galtajs.debug.api.DebugFrame;
import org.monflabs.galtajs.debug.api.DebugListener;
import org.monflabs.galtajs.debug.api.DebugProperty;
import org.monflabs.galtajs.debug.api.DebugScope;
import org.monflabs.galtajs.debug.api.PauseReason;
import org.monflabs.galtajs.debug.api.PausedEvent;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import junit.framework.TestCase;
import util.GlobalTestEnvironment;

/**
 * End-to-end test of the debug.api facade's interpreted-mode implementation
 * (DebuggerImpl and friends) - attach, set a breakpoint, pause, inspect a
 * scope variable, evaluate an expression ON the paused thread via
 * PausedEvent.call(), resume, and confirm the script still finishes with the
 * expected result. No CDP/transport layer involved yet - this proves the
 * facade itself is correct.
 */
public class DebuggerImplTest extends TestCase {

	private static final String SCRIPT = """
			var x = 10;
			var out = [];
			for(let i=0; i<3; i++) {
			  var y = x + i;
			  out.push(y);
			}
			console.log(out.join(","));
			""";

	public void testBreakpointPauseInspectEvaluateResume() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(SCRIPT, "DebuggerImplTest.js");

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		DebuggerImpl debugger = new DebuggerImpl(unit, () -> {
			InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
			ctx.setOutStream(new PrintStream(captured, true));
			return ctx;
		});

		BlockingQueue<PausedEvent> pauses = new ArrayBlockingQueue<>(16);
		debugger.addListener(new DebugListener() {
			@Override
			public void paused(PausedEvent event) {
				pauses.add(event);
			}
		});

		// "out.push(y);" - line 5
		debugger.setBreakpoint(BreakpointRequest.at("DebuggerImplTest.js", 5));

		debugger.start();
		try {
			for (int i = 0; i < 3; i++) {
				PausedEvent event = pauses.poll(5, TimeUnit.SECONDS);
				assertNotNull("expected a pause for iteration " + i, event);
				assertEquals(PauseReason.BREAKPOINT, event.reason());
				assertEquals(List.of("bp1"), event.hitBreakpoints());

				List<DebugFrame> frames = event.frames();
				assertFalse(frames.isEmpty());
				DebugFrame top = frames.get(0);
				assertEquals(5, top.location().line());

				// Find the scope carrying "y" and check its value.
				Object yValue = null;
				boolean foundY = false;
				for (DebugScope scope : top.scopes()) {
					for (DebugProperty p : debugger.values().ownProperties(scope.object(), true, true)) {
						if ("y".equals(p.name())) {
							foundY = true;
							yValue = p.value();
						}
					}
				}
				assertTrue("expected to find 'y' in some scope", foundY);
				assertEquals(Integer.valueOf(10 + i), yValue);

				// Evaluate an expression on the paused thread itself.
				Object result = event.call(() -> top.evaluate("x + 100"));
				assertEquals(110, ((Number) result).intValue());

				event.resume();
			}

			Thread execThread = debugger.getExecutionThread();
			execThread.join(5000);
			assertFalse(execThread.isAlive());
			assertEquals("10,11,12", captured.toString().trim());
		} finally {
			debugger.close();
		}
	}

	// A `for(...)` header packs three sub-expression positions (init, test,
	// update) onto ONE line, each with its own ASTDebugHook wrap
	// (statementLevel=false); the for-statement itself gets a separate,
	// SEPARATE statement-level wrap around the whole loop, covering that
	// same header line. Before onStatement()/onExit() gated stepping on
	// DebugLocation.statementLevel(), a single stepOver() sitting on the
	// header could re-pause on one of those inner test/update wraps next,
	// still reporting THIS line - exactly the "clicking Next stays on the
	// same line" bug. Separately, NodeFactory.createDebugHookStatement()
	// used to skip wrapping ASTFor/ASTWhile/ASTIf/ASTBlock/ASTDoWhile
	// entirely (an accidental carry-over from createDebugHook()'s own
	// DebuggableNode opt-out, meant only for sub-expression sites - see its
	// own doc), which meant the loop header had NO statement-level pause
	// point of its own at all: stepping over it landed straight in the
	// loop body, silently
	// skipping the header's own line. This test pins down both: (1) the
	// header line IS its own genuine, single step target, and (2) stepping
	// off it does not get stuck on its own internal test/update clauses.
	private static final String STEP_SCRIPT = """
			var out = [];
			for (let i = 0; i < 3; i++) {
			  out.push(i);
			}
			console.log(out.join(","));
			""";

	public void testStepOverTreatsForLoopHeaderAsOneStatementLevelStop() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(STEP_SCRIPT, "StepGranularityTest.js");

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		DebuggerImpl debugger = new DebuggerImpl(unit, () -> {
			InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
			ctx.setOutStream(new PrintStream(captured, true));
			return ctx;
		});

		BlockingQueue<PausedEvent> pauses = new ArrayBlockingQueue<>(16);
		debugger.addListener(new DebugListener() {
			@Override
			public void paused(PausedEvent event) {
				pauses.add(event);
			}
		});

		// "var out = [];" - line 1 (1-based, matching the debug.api facade).
		debugger.setBreakpoint(BreakpointRequest.at("StepGranularityTest.js", 1));

		debugger.start();
		try {
			PausedEvent atBreakpoint = pauses.poll(5, TimeUnit.SECONDS);
			assertNotNull(atBreakpoint);
			assertEquals(PauseReason.BREAKPOINT, atBreakpoint.reason());
			assertEquals(1, atBreakpoint.frames().get(0).location().line());

			// One stepOver() from "var out = [];" must land on the for
			// loop's own header line - "for (let i = 0; i < 3; i++) {" -
			// line 2 - as a single, distinct pause, not skip past it.
			atBreakpoint.stepOver();
			PausedEvent atForHeader = pauses.poll(5, TimeUnit.SECONDS);
			assertNotNull("expected a pause on the for loop's own header line", atForHeader);
			assertEquals(PauseReason.STEP, atForHeader.reason());
			assertEquals(2, atForHeader.frames().get(0).location().line());
			assertTrue(pauses.isEmpty());

			// A second stepOver() from the header must land directly on the
			// loop body's own statement - "out.push(i);" - line 3 - not
			// re-pause on the header's own test/update sub-expressions
			// first (both on line 2 too).
			atForHeader.stepOver();
			PausedEvent atBody = pauses.poll(5, TimeUnit.SECONDS);
			assertNotNull("expected exactly one pause from a single stepOver()", atBody);
			assertEquals(PauseReason.STEP, atBody.reason());
			assertEquals(3, atBody.frames().get(0).location().line());
			assertTrue(pauses.isEmpty());

			atBody.resume();
			Thread execThread = debugger.getExecutionThread();
			execThread.join(5000);
			assertFalse(execThread.isAlive());
			assertEquals("0,1,2", captured.toString().trim());
		} finally {
			debugger.close();
		}
	}
}
