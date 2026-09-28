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
import java.util.concurrent.TimeUnit;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.debug.api.BreakpointRequest;
import org.monflabs.galtajs.debug.api.DebugListener;
import org.monflabs.galtajs.debug.api.PauseReason;
import org.monflabs.galtajs.debug.api.PausedEvent;
import org.monflabs.galtajs.debug.api.impl.DebugRuntime;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import junit.framework.TestCase;
import util.GlobalTestEnvironment;

/**
 * The debugger must never leave a script blocked with nobody able to resume
 * it, and breakpoints hit exactly where (and when) they were asked for.
 */
public class DebuggerSafetyTest extends TestCase {

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();
	private DebuggerImpl debugger;

	private DebuggerImpl debugger(String name, String script) {
		JSEnvironment env = GlobalTestEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(script, name);
		debugger = new DebuggerImpl(unit, () -> {
			InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
			ctx.setOutStream(new PrintStream(output, true));
			return ctx;
		});
		return debugger;
	}

	private static BlockingQueue<PausedEvent> listen(DebuggerImpl debugger) {
		BlockingQueue<PausedEvent> pauses = new ArrayBlockingQueue<>(64);
		debugger.addListener(new DebugListener() {
			@Override
			public void paused(PausedEvent event) {
				pauses.add(event);
			}
		});
		return pauses;
	}

	private void assertFinishes() throws InterruptedException {
		debugger.getExecutionThread().join(5000);
		assertFalse("the script is still blocked", debugger.getExecutionThread().isAlive());
	}

	@Override
	protected void tearDown() throws Exception {
		if (debugger != null) {
			debugger.close();
		}
	}

	// `debugger;` with no client attached is ignored, as in Node without an inspector
	public void testDebuggerStatementWithoutClient() throws Exception {
		debugger("NoClient.js", "var a = 1;\ndebugger;\nconsole.log(a + 1);\n").start();
		assertFinishes();
		assertEquals("2", output.toString().trim());
	}

	// The last client going away releases a paused script and forgets its steps
	public void testClientGoneResumes() throws Exception {
		debugger("Gone.js", "var a = 1;\ndebugger;\nvar b = 2;\nvar c = 3;\nconsole.log(a + b + c);\n");
		BlockingQueue<PausedEvent> pauses = new ArrayBlockingQueue<>(8);
		DebugListener listener = new DebugListener() {
			@Override
			public void paused(PausedEvent event) {
				pauses.add(event);
			}
		};
		debugger.addListener(listener);
		debugger.start();
		PausedEvent pause = pauses.poll(5, TimeUnit.SECONDS);
		assertNotNull(pause);
		pause.stepInto();
		pause = pauses.poll(5, TimeUnit.SECONDS);
		assertNotNull(pause);
		assertEquals(PauseReason.STEP, pause.reason());
		// a step request is pending when the client leaves: it must not pause again
		pause.stepInto();
		debugger.removeListener(listener);
		assertFinishes();
		assertEquals("6", output.toString().trim());
	}

	// close() releases a paused script and detaches from it
	public void testCloseWhilePaused() throws Exception {
		int before = DebugRuntime.ACTIVE_SESSIONS;
		debugger("Close.js", "var a = 1;\ndebugger;\ndebugger;\nconsole.log(a);\n");
		BlockingQueue<PausedEvent> pauses = listen(debugger);
		debugger.start();
		assertNotNull(pauses.poll(5, TimeUnit.SECONDS));
		debugger.close();
		assertFinishes();
		assertEquals("1", output.toString().trim());
		assertTrue("no second pause after close", pauses.isEmpty());
		assertEquals(before, DebugRuntime.ACTIVE_SESSIONS);
	}

	// A breakpoint condition is evaluated in the paused frame; an error is false
	public void testConditionalBreakpoint() throws Exception {
		debugger("Cond.js", "var s = 0;\nfor (var i = 0; i < 5; i++) {\n  s += i;\n}\nconsole.log(s);\n");
		BlockingQueue<PausedEvent> pauses = listen(debugger);
		debugger.setBreakpoint(new BreakpointRequest("Cond.js", null, null, 3, -1, "i == 3"));
		debugger.setBreakpoint(new BreakpointRequest("Cond.js", null, null, 3, -1, "notDefined.x"));
		debugger.start();
		PausedEvent pause = pauses.poll(5, TimeUnit.SECONDS);
		assertNotNull(pause);
		assertEquals(List.of("bp1"), pause.hitBreakpoints());
		assertEquals(3, ((Number) pause.frames().get(0).evaluate("i")).intValue());
		pause.resume();
		assertFinishes();
		assertTrue("paused only once", pauses.isEmpty());
		assertEquals("10", output.toString().trim());
	}

	// DevTools logpoints are conditions that log and return false: they must not pause
	public void testLogpointDoesNotPause() throws Exception {
		debugger("Log.js", "var a = 1;\nvar b = a + 1;\nconsole.log(b);\n");
		BlockingQueue<PausedEvent> pauses = listen(debugger);
		debugger.setBreakpoint(new BreakpointRequest("Log.js", null, null, 2, -1, "console.log('log:' + a), false"));
		debugger.start();
		assertFinishes();
		assertTrue(pauses.isEmpty());
		assertEquals("log:1\n2", output.toString().trim().replace("\r\n", "\n"));
	}

	// With a column, only the statements starting at or after it are hits
	public void testBreakpointColumn() throws Exception {
		String line2 = "var a = 1; var b = 2;";
		debugger("Col.js", "var z = 0;\n" + line2 + "\nconsole.log(a + b);\n");
		BlockingQueue<PausedEvent> pauses = listen(debugger);
		int column = line2.indexOf("var b") + 1; // 1-based
		debugger.setBreakpoint(new BreakpointRequest("Col.js", null, null, 2, column, null));
		debugger.start();
		PausedEvent pause = pauses.poll(5, TimeUnit.SECONDS);
		assertNotNull(pause);
		assertEquals(column, pause.frames().get(0).location().column());
		pause.resume();
		assertFinishes();
		assertTrue("the first statement of the line is not a hit", pauses.isEmpty());
	}

	// A statement of another unit on the breakpoint's line is not a hit
	public void testBreakpointIgnoresOtherUnits() throws Exception {
		debugger("Units.js", "var r = [];\nr.push(1);\n(0, eval)('r.push(2);\\nr.push(3);');\nconsole.log(r.join());\n");
		BlockingQueue<PausedEvent> pauses = listen(debugger);
		debugger.setBreakpoint(BreakpointRequest.at("Units.js", 2));
		debugger.start();
		List<Integer> lines = new ArrayList<>();
		PausedEvent pause;
		while ((pause = pauses.poll(2, TimeUnit.SECONDS)) != null) {
			lines.add(pause.frames().get(0).location().line());
			pause.resume();
		}
		assertFinishes();
		assertEquals(List.of(2), lines);
		assertEquals("1,2,3", output.toString().trim());
	}
}
