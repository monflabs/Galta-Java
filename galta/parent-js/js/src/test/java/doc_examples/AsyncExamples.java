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

import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.library.platform.HostLibrary;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/UserGuide/Async.md
 */
public class AsyncExamples extends __BaseTestCase {

	public void testPromisesNeedTheProgramExecutor() {
		JSEnvironment env = JavaScriptEnvironment.create();
		// evaluateScript() runs an event loop until every pending task is done
		assertEquals(2, (Object)env.evaluateScript("await Promise.resolve(1).then(x => x + 1)"));
		// evaluateExpression() uses the synchronous executor
		try {
			env.evaluateExpression("Promise.resolve(1).then(x => x + 1)");
			fail();
		} catch(JSRuntimeException e) {
			assertTrue(e.getMessage().contains("This executor does not support micro-tasks"));
		}
	}

	public void testReadingAPromiseFromJava() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object result = env.evaluateScript("Promise.resolve(20).then(x => x * 2)");
		// The loop has been drained, so the promise is already settled
		BuiltinPromise promise = (BuiltinPromise)result;
		assertEquals("FULFILLED", String.valueOf(promise.getState()));
		assertEquals(40, promise.getResult());
	}

	public void testAsyncFunctionsAndTopLevelAwait() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
		Object r = env.evaluateScript("""
			const sleep = (ms, v) => new Promise(resolve => setTimeout(() => resolve(v), ms));
			async function fetchAll() {
				const [a, b] = await Promise.all([sleep(5, 'a'), sleep(1, 'b')]);
				return a + b;
			}
			await fetchAll()
			""");
		assertEquals("ab", r);
	}

	public void testTimersRequireHostLibrary() {
		try {
			JavaScriptEnvironment.create().evaluateScript("setTimeout(() => 1, 1)");
			fail();
		} catch(JSRuntimeException e) {
			assertTrue(e.getMessage().contains("Unknown identifier setTimeout"));
		}
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
		assertEquals(3, (Object)env.evaluateScript("""
			let n = 0;
			const id = setInterval(() => { if (++n === 3) clearInterval(id) }, 1);
			await new Promise(r => setTimeout(r, 30));
			n
			"""));
	}

	public void testEventLoopOrdering() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
		Captured run = captureOutput(env, """
			setTimeout(() => console.log('timer'), 5);
			Promise.resolve().then(() => console.log('microtask'));
			queueMicrotask(() => console.log('queueMicrotask'));
			console.log('sync');
			""");
		assertEquals(lines("sync", "microtask", "queueMicrotask", "timer"), run.output());
	}

	public void testBlockingJavaWorkAsAPromise() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.registerLibrary(new GlobalLibrary() {
					@Override
					public void configureStandardObjects(JSEnvironment env, StandardObjects globals) {
						globals.setOwnProperty("slowLookup", new BaseMethod(env, "slowLookup", 1) {
							@Override
							public Object call(Object thisValue, Object[] args) {
								JSExecutor executor = JSRuntimeContext.get().getGlobalContext().getExecutor();
								// Runs on a worker thread; the returned promise settles on the JS event loop
								return executor.asyncFunction(() -> {
									Thread.sleep(20);
									return "value for " + args[0];
								});
							}
						});
					}
				})
				.build();
		Object r = env.evaluateScript("""
			const results = await Promise.all([slowLookup('a'), slowLookup('b')]);
			results
			""");
		assertEquals(List.of("value for a", "value for b"), list(r));
	}
}
