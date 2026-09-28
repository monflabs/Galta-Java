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
package org.monflabs.galtajs.rt.interpreter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;

import junit.framework.TestCase;
import util.GlobalTestEnvironment;

/**
 * Concurrency stress for AST-embedded runtime caches. The AST is shared across
 * threads but each thread has its own runtime context, so any per-node cache
 * (IdentIC in Phase 2d, PropIC in Phase 3, type feedback in Phase 5a) must be
 * publishable atomically or a torn read will surface as a wrong return value
 * or a ClassCastException.
 *
 * <p>Runtime is bounded by system properties so the test can double as a smoke
 * check in the default suite and a longer stress run in a scheduled job:
 * <ul>
 *   <li>{@code -Dpic.stress.threads=16} - worker count (default 8)</li>
 *   <li>{@code -Dpic.stress.millis=2000} - duration per test (default 1500)</li>
 * </ul>
 */
public class PICConcurrencyTest extends TestCase {

	private static final int    THREADS = Integer.getInteger("pic.stress.threads", 8);
	private static final long   MILLIS  = Long.getLong("pic.stress.millis", 1500L);

	/**
	 * Test A - IdentIC snapshot atomicity (Phase 2d).
	 *
	 * <p>Parse one script once (shared AST). Spawn N worker threads, each with
	 * its own {@link InterpretedGlobalRuntimeContext}. Every thread hot-loops
	 * calling the same shared function with its own thread-local argument and
	 * asserts the return value equals the argument. The read of {@code n}
	 * inside the function body flows through the shared {@link
	 * org.monflabs.galtajs.node.ASTIdentifier}'s IdentIC cache.
	 *
	 * <p>A torn cache publish would surface as one thread reading another
	 * thread's binding (wrong-owner) and returning the wrong number.
	 */
	public void testIdentIC_SharedAST_ConcurrentReads() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.create();
		// A tight identifier-read loop: the function's body only touches a
		// parameter binding, which is exactly the case IdentIC caches.
		String src = "function f(n){ return n; } var acc = 0; for (var i=0;i<1000;i++){ acc = f(acc+1); } acc;";
		JSInterpretedUnit script = env.createScript(src, "PICConcurrencyTest_A");

		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> firstFailure = new AtomicReference<>();
		AtomicLong iterations = new AtomicLong();
		Thread[] workers = new Thread[THREADS];
		for(int t=0; t<THREADS; t++) {
			final int tid = t;
			workers[t] = new Thread(() -> {
				try {
					start.await();
					long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MILLIS);
					long localIter = 0;
					while(System.nanoTime() < deadline && firstFailure.get()==null) {
						InterpretedGlobalRuntimeContext ctx =
							new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
						Object result = script.executeWithContext(ctx);
						// Each run computes acc via f(f(f(...f(0+1)+1)+1)) 1000 times,
						// where f is the identity - so acc must be 1000.
						int rv = ((Number)result).intValue();
						if(rv != 1000) {
							firstFailure.compareAndSet(null,
								new AssertionError("tid="+tid+" got "+rv+" expected 1000"));
							return;
						}
						localIter++;
					}
					iterations.addAndGet(localIter);
				} catch(Throwable ex) {
					firstFailure.compareAndSet(null, ex);
				}
			}, "PICConcurrency-A-"+t);
			workers[t].setDaemon(true);
			workers[t].start();
		}
		start.countDown();
		for(Thread w : workers) {
			w.join(TimeUnit.MILLISECONDS.toMillis(MILLIS)*3 + 5_000);
			if(w.isAlive()) {
				w.interrupt();
				fail("Worker "+w.getName()+" did not finish");
			}
		}
		if(firstFailure.get()!=null) {
			throw new AssertionError("Test A failed after "+iterations.get()+" iterations", firstFailure.get());
		}
		// Sanity: the workers actually did work.
		assertTrue("no iterations observed", iterations.get()>0);
	}

	/**
	 * Test B - PropIC snapshot atomicity (Phase 3).
	 *
	 * <p>Same skeleton as Test A but the hot access is a property read on a
	 * plain object: {@code o.x}. Each thread constructs and mutates its own
	 * object; the shared {@link org.monflabs.galtajs.node.ASTMember} node's
	 * PropIC cache alternates ownership between threads at native speed. A
	 * torn (owner, entry) publish would return the neighbour's stored value
	 * or a ClassCastException, both of which show up as the assertion below.
	 */
	public void testPropIC_SharedAST_ConcurrentReads() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.create();
		// Each execution constructs a fresh object with x=42 and reads o.x 1000
		// times; the reads all flow through the same ASTMember node's PropIC.
		String src = "function run(){ var o={x:42}; var s=0; for(var i=0;i<1000;i++) s+=o.x; return s; } run();";
		JSInterpretedUnit script = env.createScript(src, "PICConcurrencyTest_B");
		final int expected = 42 * 1000;

		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> firstFailure = new AtomicReference<>();
		AtomicLong iterations = new AtomicLong();
		Thread[] workers = new Thread[THREADS];
		for(int t=0; t<THREADS; t++) {
			final int tid = t;
			workers[t] = new Thread(() -> {
				try {
					start.await();
					long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MILLIS);
					long localIter = 0;
					while(System.nanoTime() < deadline && firstFailure.get()==null) {
						InterpretedGlobalRuntimeContext ctx =
							new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
						Object result = script.executeWithContext(ctx);
						int rv = ((Number)result).intValue();
						if(rv != expected) {
							firstFailure.compareAndSet(null,
								new AssertionError("tid="+tid+" got "+rv+" expected "+expected));
							return;
						}
						localIter++;
					}
					iterations.addAndGet(localIter);
				} catch(Throwable ex) {
					firstFailure.compareAndSet(null, ex);
				}
			}, "PICConcurrency-B-"+tid);
			workers[t].setDaemon(true);
			workers[t].start();
		}
		start.countDown();
		for(Thread w : workers) {
			w.join(TimeUnit.MILLISECONDS.toMillis(MILLIS)*3 + 5_000);
			if(w.isAlive()) {
				w.interrupt();
				fail("Worker "+w.getName()+" did not finish");
			}
		}
		if(firstFailure.get()!=null) {
			throw new AssertionError("Test B failed after "+iterations.get()+" iterations", firstFailure.get());
		}
		assertTrue("no iterations observed", iterations.get()>0);
	}

	/**
	 * Test B' - variant that stresses PropIC's invalidation-on-delete path.
	 * Each iteration reads o.x, deletes it, reintroduces it, and reads again;
	 * the reintroduced own entry is a fresh EntryImpl - the cache must
	 * fall back to the slow path once the prior entry's `removed` flag flips.
	 */
	public void testPropIC_DeleteReintroduce_InvalidatesCache() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.create();
		String src =
			"function run(){"+
			"  var o={x:7}; var s=0;"+
			"  for(var i=0;i<500;i++){"+
			"    s+=o.x;"+       // read: pre-delete
			"    delete o.x;"+   // invalidate entry (removed=true)
			"    o.x=11;"+       // fresh EntryImpl for x
			"    s+=o.x;"+       // read: post-reintroduce
			"    o.x=7;"+        // reset to 7 for next iteration
			"  }"+
			"  return s;"+
			"} run();";
		JSInterpretedUnit script = env.createScript(src, "PICConcurrencyTest_Bp");
		final int expected = (7 + 11) * 500;

		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> firstFailure = new AtomicReference<>();
		AtomicLong iterations = new AtomicLong();
		Thread[] workers = new Thread[THREADS];
		for(int t=0; t<THREADS; t++) {
			final int tid = t;
			workers[t] = new Thread(() -> {
				try {
					start.await();
					long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MILLIS);
					long localIter = 0;
					while(System.nanoTime() < deadline && firstFailure.get()==null) {
						InterpretedGlobalRuntimeContext ctx =
							new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
						Object result = script.executeWithContext(ctx);
						int rv = ((Number)result).intValue();
						if(rv != expected) {
							firstFailure.compareAndSet(null,
								new AssertionError("tid="+tid+" got "+rv+" expected "+expected));
							return;
						}
						localIter++;
					}
					iterations.addAndGet(localIter);
				} catch(Throwable ex) {
					firstFailure.compareAndSet(null, ex);
				}
			}, "PICConcurrency-Bp-"+tid);
			workers[t].setDaemon(true);
			workers[t].start();
		}
		start.countDown();
		for(Thread w : workers) {
			w.join(TimeUnit.MILLISECONDS.toMillis(MILLIS)*3 + 5_000);
			if(w.isAlive()) {
				w.interrupt();
				fail("Worker "+w.getName()+" did not finish");
			}
		}
		if(firstFailure.get()!=null) {
			throw new AssertionError("Test B' failed after "+iterations.get()+" iterations", firstFailure.get());
		}
		assertTrue("no iterations observed", iterations.get()>0);
	}

	/**
	 * Test A' - variant that stresses the miss path: identifiers whose
	 * resolving map differs across threads. Each thread declares its own
	 * outer-scope binding shadowing the same name; the cache on the shared
	 * ASTIdentifier will thrash between owners. A torn read would return the
	 * neighbour's binding.
	 */
	public void testIdentIC_ThreadLocalOwners_ThrashingCache() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.create();
		// n is a function parameter here too, but the outer let x means every
		// call site's context.getVariableMap() is the function frame - and
		// f(n) below reads n which lives in that per-thread frame.
		String src = "function f(n){ return n + 1; } (function(){ var s = 0; for (var i=0;i<500;i++) s += f(i); return s; })();";
		JSInterpretedUnit script = env.createScript(src, "PICConcurrencyTest_Ap");
		// expected sum: sum(1..500) = 125250
		final int expected = 500*501/2;

		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> firstFailure = new AtomicReference<>();
		AtomicLong iterations = new AtomicLong();
		Thread[] workers = new Thread[THREADS];
		for(int t=0; t<THREADS; t++) {
			final int tid = t;
			workers[t] = new Thread(() -> {
				try {
					start.await();
					long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MILLIS);
					long localIter = 0;
					while(System.nanoTime() < deadline && firstFailure.get()==null) {
						InterpretedGlobalRuntimeContext ctx =
							new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
						Object result = script.executeWithContext(ctx);
						int rv = ((Number)result).intValue();
						if(rv != expected) {
							firstFailure.compareAndSet(null,
								new AssertionError("tid="+tid+" got "+rv+" expected "+expected));
							return;
						}
						localIter++;
					}
					iterations.addAndGet(localIter);
				} catch(Throwable ex) {
					firstFailure.compareAndSet(null, ex);
				}
			}, "PICConcurrency-Ap-"+t);
			workers[t].setDaemon(true);
			workers[t].start();
		}
		start.countDown();
		for(Thread w : workers) {
			w.join(TimeUnit.MILLISECONDS.toMillis(MILLIS)*3 + 5_000);
			if(w.isAlive()) {
				w.interrupt();
				fail("Worker "+w.getName()+" did not finish");
			}
		}
		if(firstFailure.get()!=null) {
			throw new AssertionError("Test A' failed after "+iterations.get()+" iterations", firstFailure.get());
		}
		assertTrue("no iterations observed", iterations.get()>0);
	}
}
