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
package doc_examples;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.debug.api.BreakpointRequest;
import org.monflabs.galtajs.debug.api.DebugListener;
import org.monflabs.galtajs.debug.api.PauseReason;
import org.monflabs.galtajs.debug.api.PausedEvent;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.tests.__BaseTestCase;

/**
 * Samples for docs/GaltaJS/UserGuide/Debugging.md
 */
public class DebuggerExamples extends __BaseTestCase {

	private static final String SCRIPT = """
		let total = 0;
		for (let i = 1; i <= 3; i++) {
		  total += i;
		}
		debugger;
		total;
		""";

	public void testBreakpointsAndTheDebuggerStatement() throws Exception {
		// debug(true) instruments the parsed scripts with debug hooks
		JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript(SCRIPT, "sum.js");

		DebuggerImpl debugger = new DebuggerImpl(unit,
				() -> new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));

		// Pauses are delivered on the script thread; hand them to the controlling thread
		LinkedBlockingQueue<PausedEvent> pauses = new LinkedBlockingQueue<>();
		CountDownLatch finished = new CountDownLatch(1);
		debugger.addListener(new DebugListener() {
			@Override public void paused(PausedEvent event) { pauses.add(event); }
			@Override public void executionFinished() { finished.countDown(); }
		});

		debugger.setBreakpoint(BreakpointRequest.at("sum.js", 3));   // inside the loop
		debugger.start();

		List<String> seen = new ArrayList<>();
		for(int i = 0; i < 4; i++) {
			PausedEvent pause = pauses.poll(10, TimeUnit.SECONDS);
			assertNotNull(pause);
			Object total = debugger.evaluate(pause.context(), "total");
			seen.add(pause.reason() + "@" + pause.frames().get(0).location().line() + "=" + total);
			pause.resume();
		}
		assertTrue(finished.await(10, TimeUnit.SECONDS));
		debugger.close();

		assertEquals(List.of("BREAKPOINT@3=0", "BREAKPOINT@3=1", "BREAKPOINT@3=3", "DEBUGGER_STATEMENT@5=6"), seen);
		assertEquals(PauseReason.BREAKPOINT.name(), seen.get(0).split("@")[0]);
	}
}
