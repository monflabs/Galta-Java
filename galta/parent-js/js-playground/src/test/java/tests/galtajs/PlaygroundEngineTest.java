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
package tests.galtajs;

import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.Snippet;

import com.monflabs.playground.galtajs.GaltaJSExecutionEngine;
import com.monflabs.playground.galtajs.SnippetEnvironment;

import tests.ProjectTestCase;
import tests.util.TestExecutionContext;

/**
 * GaltaJS playground engine: blank scripts, stop requests, environment options.
 */
public class PlaygroundEngineTest extends ProjectTestCase {

	private Snippet snippet(String mainJs) throws Exception {
		Path dir = Files.createTempDirectory("playground-snippet");
		Files.writeString(dir.resolve(GaltaJSExecutionEngine.DEFAULT_JS), mainJs);
		return new Snippet(dir);
	}

	public void testBlankMainIsNotAnError() throws Exception {
		// Auto-execution runs on every keystroke: a blank main.js must not throw
		GaltaJSExecutionEngine eng = new GaltaJSExecutionEngine(new TestExecutionContext(snippet("   \n")));
		assertNull(eng.execute());
	}

	public void testSoftInterruptStopsAnInfiniteLoop() throws Exception {
		GaltaJSExecutionEngine eng = new GaltaJSExecutionEngine(new TestExecutionContext(snippet("var i=0; while(true) { i++; }")));
		assertTrue(eng.isSoftInterruptable());
		ExecutionResult[] result = new ExecutionResult[1];
		Thread t = new Thread(() -> {
			try {
				result[0] = eng.execute();
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		});
		t.start();
		Thread.sleep(300);
		eng.softInterrupt();
		t.join(10_000);
		assertFalse("the script did not stop", t.isAlive());
		assertNotNull(result[0]);
		assertNotNull(result[0].getException());
		assertTrue(result[0].getException().getClass().getName(), result[0].getException().getClass().getName().endsWith("InterruptException"));
	}

	public void testInterruptBeforeTheScriptStarts() throws Exception {
		GaltaJSExecutionEngine eng = new GaltaJSExecutionEngine(new TestExecutionContext(snippet("while(true) {}")));
		eng.softInterrupt(); // nothing running yet: the request is kept
		ExecutionResult r = eng.execute();
		assertNotNull(r.getException());
	}

	public void testDeprecatedApisInBothModes() throws Exception {
		// Annex B APIs are available whatever the mode (they used to exist in strict mode only)
		for(boolean strictMode: new boolean[] {true, false}) {
			JSEnvironment env = SnippetEnvironment.create(true, strictMode);
			assertEquals("function", env.evaluateScript("typeof escape"));
			assertEquals("bc", env.evaluateScript("'abc'.substr(1)"));
		}
	}
}
