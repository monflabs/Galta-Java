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
package tests.execution;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionController;
import org.monflabs.playground.ExecutionController.Run;
import org.monflabs.playground.ExecutionEngine;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.Snippet;

import junit.framework.TestCase;

public class ExecutionControllerTest extends TestCase {

	static class Context extends ExecutionContext {
		Context() throws Exception {
			super(new Snippet(Files.createTempDirectory("controller-test")));
		}
		@Override
		public String getConsoleText() {
			return "";
		}
	}

	static class Result extends ExecutionResult {
		final String name;
		Result(String name) {
			this.name = name;
		}
	}

	/**
	 * Records what the listener hears.
	 */
	static class Recorder implements ExecutionController.Listener {
		final List<String> events = new CopyOnWriteArrayList<>();
		final CountDownLatch finished;
		Recorder(int expected) {
			finished = new CountDownLatch(expected);
		}
		@Override
		public void executionStarted(Run run) {
			events.add("start:"+run.getId());
		}
		@Override
		public void executionFinished(Run run, ExecutionResult result, Throwable failure) {
			String what = result instanceof Result r ? r.name : failure!=null ? failure.getClass().getSimpleName() : "null";
			events.add("end:"+run.getId()+":"+what);
			finished.countDown();
		}
		void await() throws InterruptedException {
			assertTrue("the run(s) did not finish: "+events, finished.await(10, TimeUnit.SECONDS));
		}
	}

	/**
	 * An engine running a task.
	 */
	static ExecutionEngine engine(ExecutionContext ctx, String name, boolean soft, Blocker task) {
		return new ExecutionEngine(ctx) {
			volatile boolean stop;
			@Override
			protected ExecutionResult _execute() throws Exception {
				task.run(() -> stop);
				return new Result(name);
			}
			@Override
			public boolean isSoftInterruptable() {
				return soft;
			}
			@Override
			public void softInterrupt() {
				stop = true;
			}
		};
	}

	interface Blocker {
		void run(java.util.function.BooleanSupplier stopped) throws Exception;
	}

	private ExecutionController controller;

	@Override
	protected void tearDown() throws Exception {
		if(controller!=null) {
			controller.close();
		}
		super.tearDown();
	}

	public void testDebounce() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		Recorder rec = new Recorder(1);
		controller = new ExecutionController(ctx -> {
			runs.incrementAndGet();
			return engine(ctx, "r", false, s -> {});
		}, rec);
		Context ctx = new Context();
		controller.request(ctx, Map.of(), 200);
		controller.request(ctx, Map.of(), 200);
		long last = controller.request(ctx, Map.of(), 200);
		rec.await();
		Thread.sleep(300);
		assertEquals("only the last request runs", 1, runs.get());
		assertEquals(List.of("start:"+last, "end:"+last+":r"), rec.events);
	}

	public void testNewRequestSoftInterruptsTheRunningOne() throws Exception {
		CountDownLatch firstRunning = new CountDownLatch(1);
		CountDownLatch firstStopped = new CountDownLatch(1);
		Recorder rec = new Recorder(1);
		AtomicInteger count = new AtomicInteger();
		controller = new ExecutionController(ctx -> {
			if(count.incrementAndGet()==1) {
				return engine(ctx, "first", true, stopped -> {
					firstRunning.countDown();
					while(!stopped.getAsBoolean()) {
						Thread.sleep(5);
					}
					firstStopped.countDown();
				});
			}
			return engine(ctx, "second", true, s -> {});
		}, rec);
		Context ctx = new Context();
		long first = controller.request(ctx, Map.of(), 0);
		assertTrue(firstRunning.await(10, TimeUnit.SECONDS));
		long second = controller.request(ctx, Map.of(), 0);
		rec.await();
		assertTrue("the first run was interrupted", firstStopped.await(10, TimeUnit.SECONDS));
		Thread.sleep(100);
		// the superseded run is never reported, not even its end
		assertEquals(List.of("start:"+first, "start:"+second, "end:"+second+":second"), rec.events);
	}

	public void testNonSoftEngineIsThreadInterrupted() throws Exception {
		CountDownLatch running = new CountDownLatch(1);
		Recorder rec = new Recorder(1);
		controller = new ExecutionController(ctx -> engine(ctx, "sleeper", false, s -> {
			running.countDown();
			Thread.sleep(60_000);
		}), rec);
		long id = controller.request(new Context(), Map.of(), 0);
		assertTrue(running.await(10, TimeUnit.SECONDS));
		assertTrue(controller.isRunning());
		controller.stop();
		rec.await();
		// stopped, still current: its end is reported
		assertEquals("end:"+id+":InterruptedException", rec.events.get(1));
		assertTrue(controller.getCurrentRun().isStopped());
		assertFalse(controller.isRunning());
	}

	public void testStuckEngineIsAbandoned() throws Exception {
		CountDownLatch running = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Recorder rec = new Recorder(1);
		controller = new ExecutionController(ctx -> engine(ctx, "stuck", false, s -> {
			running.countDown();
			// ignores interrupts
			while(release.getCount()>0) {
				try {
					release.await();
				} catch(InterruptedException e) {
					// keep going
				}
			}
		}), rec);
		controller.setStopTimeout(Duration.ofMillis(200));
		long id = controller.request(new Context(), Map.of(), 0);
		assertTrue(running.await(10, TimeUnit.SECONDS));
		Thread runThread = findThread("-run-");
		assertNotNull(runThread);
		assertTrue("execution threads are daemons", runThread.isDaemon());
		controller.stop();
		rec.await();
		assertEquals("end:"+id+":PlaygroundException", rec.events.get(1));
		assertTrue(controller.getCurrentRun().isAbandoned());
		// a new run is not blocked by the abandoned one
		Recorder rec2 = new Recorder(1);
		ExecutionController c2 = new ExecutionController(ctx -> engine(ctx, "next", false, s -> {}), rec2);
		try {
			c2.request(new Context(), Map.of(), 0);
			rec2.await();
		} finally {
			c2.close();
		}
		release.countDown();
	}

	public void testCancelDropsTheResultAndTheConsole() throws Exception {
		CountDownLatch running = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(1);
		ByteArrayOutputStream console = new ByteArrayOutputStream();
		PrintStream target = new PrintStream(console, true, StandardCharsets.UTF_8);
		Recorder rec = new Recorder(1);
		controller = new ExecutionController(ctx -> engine(ctx, "late", false, s -> {
			PrintStream out = ExecutionController.currentThreadRun().gate(target);
			out.print("before;");
			running.countDown();
			try {
				release.await();
			} catch(InterruptedException e) {
				// cancelled: finish anyway, late
			}
			out.print("after;");
			done.countDown();
		}), rec);
		controller.request(new Context(), Map.of(), 0);
		assertTrue(running.await(10, TimeUnit.SECONDS));
		controller.cancel();
		assertTrue(done.await(10, TimeUnit.SECONDS));
		Thread.sleep(100);
		assertEquals("the output after the cancel is dropped", "before;", console.toString(StandardCharsets.UTF_8));
		assertEquals("a cancelled run is not reported", List.of(rec.events.get(0)), rec.events);
		assertNull(controller.getCurrentRun());
	}

	public void testCancelDropsThePendingRequests() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		controller = new ExecutionController(ctx -> {
			runs.incrementAndGet();
			return engine(ctx, "r", false, s -> {});
		}, null);
		controller.request(new Context(), Map.of(), 200);
		controller.cancel();
		Thread.sleep(400);
		assertEquals(0, runs.get());
	}

	public void testOptionsAndBeforeRun() throws Exception {
		Recorder rec = new Recorder(1);
		List<String> seen = new CopyOnWriteArrayList<>();
		controller = new ExecutionController(ctx -> engine(ctx, "r", false, s -> {
			Run run = ExecutionController.currentThreadRun();
			seen.add(String.valueOf(run.getOption("k", null)));
		}), rec);
		controller.setBeforeRun(() -> seen.add("before"));
		controller.request(new Context(), Map.of("k", "v"), 0);
		rec.await();
		assertEquals(List.of("before", "v"), seen);
	}

	public void testFailureIsReported() throws Exception {
		Recorder rec = new Recorder(1);
		controller = new ExecutionController(ctx -> engine(ctx, "r", false, s -> {
			throw new IllegalStateException("boom");
		}), rec);
		long id = controller.request(new Context(), Map.of(), 0);
		rec.await();
		assertEquals("end:"+id+":IllegalStateException", rec.events.get(1));
	}

	public void testCloseStopsEverything() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		Function<ExecutionContext,ExecutionEngine> f = ctx -> {
			runs.incrementAndGet();
			return engine(ctx, "r", false, s -> {});
		};
		controller = new ExecutionController(f, null);
		Thread scheduler = null;
		controller.request(new Context(), Map.of(), 100);
		scheduler = findThread("-scheduler");
		assertNotNull(scheduler);
		assertTrue("the scheduler is a daemon", scheduler.isDaemon());
		controller.close();
		assertTrue(controller.isClosed());
		Thread.sleep(300);
		assertEquals(0, runs.get());
		scheduler.join(5_000);
		assertFalse(scheduler.isAlive());
		// requests after close are ignored
		controller.request(new Context(), Map.of(), 0);
		Thread.sleep(100);
		assertEquals(0, runs.get());
	}

	private Thread findThread(String part) {
		for(Thread t: Thread.getAllStackTraces().keySet()) {
			if(t.getName().startsWith("playground-execution-") && t.getName().contains(part) && t.isAlive()) {
				return t;
			}
		}
		return null;
	}
}
