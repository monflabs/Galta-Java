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
package tests.generators;

import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.util.generators.GeneratorAbandonedError;
import org.monflabs.util.generators.GeneratorImpl;
import org.monflabs.util.generators.GeneratorReturnSignal;
import org.monflabs.util.generators.GeneratorScheduler;

import tests.ProjectTestCase;

/**
 * Thread handling of the generators: interrupts, abandoned generators, scheduler threads.
 */
public class GeneratorThreadsTest extends ProjectTestCase {

	public void testSchedulerUsesVirtualThreads() throws Exception {
		// Virtual threads never keep the JVM alive: an abandoned generator parked in yield()
		// used to hold a non-daemon platform thread forever
		ExecutorService ex = GeneratorScheduler.getExecutorService();
		AtomicReference<Thread> t = new AtomicReference<>();
		ex.submit(() -> t.set(Thread.currentThread())).get(5, TimeUnit.SECONDS);
		assertTrue(t.get().isVirtual());
		assertTrue(t.get().isDaemon());
	}

	public void testConsumerInterruptedWhileBodyRuns() throws Exception {
		CountDownLatch bodyRunning = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch bodyDone = new CountDownLatch(1);
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			try {
				g.yield(1);
				bodyRunning.countDown();
				// Still running when the consumer is interrupted
				release.await();
				g.yield(2);
				g.yield(3);
				return null;
			} catch(InterruptedException e) {
				return null;
			} finally {
				bodyDone.countDown();
			}
		});
		assertEquals(Integer.valueOf(1), gen.next());
		Thread consumer = Thread.currentThread();
		Thread interrupter = new Thread(() -> {
			try {
				bodyRunning.await();
				Thread.sleep(50);
				consumer.interrupt();
				Thread.sleep(50);
				release.countDown();
			} catch(InterruptedException e) {
			}
		});
		interrupter.start();
		// Interrupted while waiting for the body's next value: reported as a cancellation,
		// not as the end of the values
		assertThrows(CancellationException.class, () -> gen.hasNext());
		assertTrue(Thread.interrupted());   // the interrupt status is kept (and cleared here)
		interrupter.join();
		// The body is not left parked in yield(2) forever: it is driven to completion
		assertTrue("the generator body leaked a parked thread", bodyDone.await(5, TimeUnit.SECONDS));
		assertThrows(NoSuchElementException.class, () -> gen.next());
	}

	public void testConsumerInterruptedWhileBodyParked() throws Exception {
		CountDownLatch bodyDone = new CountDownLatch(1);
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			try {
				g.yield(1);
				g.yield(2);
				return null;
			} finally {
				bodyDone.countDown();
			}
		});
		assertEquals(Integer.valueOf(1), gen.next());
		// The next resume is interrupted before it starts: a stale interrupt flag used to
		// make the generator silently report that it had no more values
		Thread.currentThread().interrupt();
		assertThrows(CancellationException.class, () -> gen.hasNext());
		assertTrue(Thread.interrupted());
		assertTrue(bodyDone.await(5, TimeUnit.SECONDS));
	}

	public void testBodyThreadInterruptedInYield() throws Exception {
		// Interrupting the body thread while it waits for the consumer must not make yield()
		// return a bogus null resume value
		AtomicReference<Thread> bodyThread = new AtomicReference<>();
		AtomicReference<Object> resumed = new AtomicReference<>();
		AtomicReference<Boolean> interruptKept = new AtomicReference<>();
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			bodyThread.set(Thread.currentThread());
			resumed.set(g.yield(1));
			interruptKept.set(Thread.currentThread().isInterrupted());
			return null;
		});
		assertEquals(Integer.valueOf(1), gen.next());
		bodyThread.get().interrupt();
		Thread.sleep(50);
		assertThrows(NoSuchElementException.class, () -> gen.next("resume"));
		assertEquals("resume", resumed.get());
		assertEquals(Boolean.TRUE, interruptKept.get());
	}

	public void testCloseAbandonedGenerator() throws Exception {
		CountDownLatch bodyDone = new CountDownLatch(1);
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			try {
				for(int i=0; ; i++) {
					g.yield(i);
				}
			} finally {
				bodyDone.countDown();
			}
		});
		assertEquals(Integer.valueOf(0), gen.next());
		gen.close();
		assertTrue(bodyDone.await(5, TimeUnit.SECONDS));
		assertFalse(gen.hasNext());
	}

	public void testUnreachableGeneratorReleasesItsBody() throws Exception {
		// A generator dropped without close() must not leave its body parked in yield()
		// forever: once the handle is collected, the body unwinds (its finally runs)
		CountDownLatch bodyDone = new CountDownLatch(1);
		startAndDrop(bodyDone);
		for(int i=0; i<100 && bodyDone.getCount()>0; i++) {
			System.gc();
			bodyDone.await(50, TimeUnit.MILLISECONDS);
		}
		assertEquals("the unreachable generator leaked its parked body", 0, bodyDone.getCount());
	}

	private static void startAndDrop(CountDownLatch bodyDone) {
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			try {
				for(int i=0; ; i++) {
					g.yield(i);
				}
			} finally {
				bodyDone.countDown();
			}
		});
		assertEquals(Integer.valueOf(0), gen.next());
	}

	public void testExecutorShutdownNowReleasesParkedBody() throws Exception {
		ExecutorService executor = GeneratorScheduler.createExecutor();
		CountDownLatch bodyDone = new CountDownLatch(1);
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create(executor, (g) -> {
			try {
				g.yield(1);
				g.yield(2);
				return null;
			} finally {
				bodyDone.countDown();
			}
		});
		assertEquals(Integer.valueOf(1), gen.next());
		executor.shutdownNow();
		assertTrue("the body ignored the executor shutdown", bodyDone.await(5, TimeUnit.SECONDS));
		assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		// The consumer is not left waiting for a handoff that will never come
		assertThrows(java.util.concurrent.CancellationException.class, () -> gen.next());
		assertFalse(gen.hasNext());
		gen.close();
	}

	private static void assertThrows(Class<? extends Throwable> c, org.junit.function.ThrowingRunnable r) {
		org.junit.Assert.assertThrows(c, r);
	}

	public void testCloseWhenFinallyYields() throws Exception {
		// close() used to take the value yielded by the finally block and return, leaving
		// the body parked in that yield() forever
		CountDownLatch bodyDone = new CountDownLatch(1);
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			try {
				try {
					g.yield(1);
				} finally {
					g.yield(99);
				}
				g.yield(2);
				return null;
			} finally {
				bodyDone.countDown();
			}
		});
		assertEquals(Integer.valueOf(1), gen.next());
		gen.close();
		assertTrue("the generator body was left parked", bodyDone.await(5, TimeUnit.SECONDS));
		assertFalse(gen.hasNext());
	}

	public void testAbandonedBodyCatchingRuntimeException() throws Exception {
		// A body catching RuntimeException (so the GeneratorReturnSignal) around its yield
		// used to be answered with a new return signal on every yield: both threads spun
		// forever. It now gets a GeneratorAbandonedError
		AtomicInteger yields = new AtomicInteger();
		AtomicReference<Throwable> ended = new AtomicReference<>();
		CountDownLatch bodyDone = new CountDownLatch(1);
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			try {
				while(true) {
					try {
						g.yield(yields.incrementAndGet());
					} catch(RuntimeException e) {
						// swallowed
					}
				}
			} catch(Throwable t) {
				ended.set(t);
				throw t;
			} finally {
				bodyDone.countDown();
			}
		});
		assertEquals(Integer.valueOf(1), gen.next());
		gen.close();
		assertTrue(bodyDone.await(5, TimeUnit.SECONDS));
		assertTrue(String.valueOf(ended.get()), ended.get() instanceof GeneratorAbandonedError);
		assertTrue(String.valueOf(yields.get()), yields.get() <= 4);
	}

	public void testAbandonedBodyYieldingForever() throws Exception {
		// Even an Error is swallowed by a body yielding from a finally (like a JavaScript
		// "finally { continue }"): it is then left parked instead of spinning
		AtomicInteger yields = new AtomicInteger();
		GeneratorImpl<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			while(true) {
				try {
					g.yield(yields.incrementAndGet());
				} catch(Throwable t) {
					// swallowed
				}
			}
		});
		assertEquals(Integer.valueOf(1), gen.next());
		gen.close();
		Thread.sleep(200);
		int count = yields.get();
		assertTrue(String.valueOf(count), count <= 4);
		Thread.sleep(200);
		assertEquals(count, yields.get());
	}

	public void testGeneratorReturnSignalStillWorks() throws Exception {
		GeneratorImpl<Integer,String> gen = GeneratorImpl.create( (g) -> {
			try {
				g.yield(1);
			} catch(GeneratorReturnSignal s) {
				throw s;
			}
			return "end";
		});
		assertEquals(Integer.valueOf(1), gen.next());
		assertThrows(NoSuchElementException.class, () -> gen.returnWith("r"));
		assertEquals("r", gen.getReturnValue());
	}

	public void testPlatformExecutor() throws Exception {
		ExecutorService ex = GeneratorScheduler.createPlatformExecutor();
		try {
			AtomicReference<Thread> t = new AtomicReference<>();
			GeneratorImpl<Integer,Void> gen = GeneratorImpl.create(ex, (g) -> {
				t.set(Thread.currentThread());
				synchronized(t) {
					g.yield(1);	// pinning is harmless on a platform thread
				}
				return null;
			});
			assertEquals(Integer.valueOf(1), gen.next());
			assertFalse(t.get().isVirtual());
			assertTrue(t.get().isDaemon());
			gen.close();
		} finally {
			ex.shutdown();
		}
	}

	public void testBoundedExecutorStillWorksWhenFree() throws Exception {
		// A bounded executor works as long as a thread is free for each live generator
		ExecutorService ex = Executors.newFixedThreadPool(1);
		try {
			for(int i=0; i<3; i++) {
				try(GeneratorImpl<Integer,Void> gen = GeneratorImpl.create(ex, (g) -> {
					g.yield(1);
					return null;
				})) {
					assertEquals(Integer.valueOf(1), gen.next());
				}
			}
		} finally {
			ex.shutdown();
		}
	}
}
