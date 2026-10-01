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
package org.monflabs.galtajs.rt.executors;

import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromiseConstructor;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorImpl;
import org.monflabs.util.generators.GeneratorScheduler;
import org.monflabs.util.generators.Yielder;

/**
 * Executor emulating the JavaScript event loop.
 * <p>
 * The thread that calls {@link #execute(Supplier, boolean)} runs the code,
 * then the event loop: every queued microtask (promise reactions, await
 * resumptions), then one macrotask (a due timer being one), and so on,
 * until nothing is pending. It blocks on a semaphore, released by every
 * enqueue and by the completion of async work running on other threads, so
 * it never polls. A pending timer caps the wait at its deadline.
 * <p>
 * Async functions and module top-level await run as coroutines (see
 * {@link #runAsyncBody}): an await suspends the coroutine and resumes it
 * from a promise reaction. {@link #asyncFunction} runs Java code (blocking
 * I/O for example) on a worker thread and settles a promise once it is
 * done. The queues and the worker pool are created lazily, on first use,
 * and released once the outermost {@code execute()} returns.
 */
public class JSAsyncExecutor implements JSExecutor {
	
	// The creation cost is unnecessary for small expressions when async is not used
	// We lazily create it only when needed.
	private static final class AsyncData {
		
		final ExecutorService executor = GeneratorScheduler.createExecutor();
		final ReentrantLock lock = new ReentrantLock();
		final Semaphore wakeups = new Semaphore(0);

		final ArrayDeque<AsyncTask> microQ = new ArrayDeque<>(32);
		final ArrayDeque<AsyncTask> macroQ = new ArrayDeque<>(32);

		// Timers: macrotasks moved to macroQ once due, ordered by earliest
		// readyAt (System.nanoTime() based). Guarded by `lock`. A
		// monotonically-increasing sequence disambiguates ties so ordering is
		// stable (FIFO among tasks scheduled for the same instant).
		final PriorityQueue<TimedTask> timedQ = new PriorityQueue<>();
		final AtomicLong timedSeq = new AtomicLong();

		final AtomicInteger pendingAsync = new AtomicInteger(0);

		private boolean hasPending() {
			//Console.log("hasPending: {0}, microQ: {1}", pendingAsync.get(), microQ.size());
			if(pendingAsync.get()>0) {
				return true;
			}
			// The queues are plain collections: read them under their lock
			lock.lock();
			try {
				return !microQ.isEmpty() || !macroQ.isEmpty() || !timedQ.isEmpty();
			} finally {
				lock.unlock();
			}
		}

		private void shutdown() {
	        lock.lock();
	        try {
				microQ.clear();
				macroQ.clear();
				timedQ.clear();
				pendingAsync.set(0);
	        } finally {
	        	lock.unlock();
	        }
			try {
				executor.shutdownNow();
			} catch(Throwable t) {}
	    }
	}

	private static final class TimedTask implements Comparable<TimedTask> {
		final long readyAt;
		final long seq;
		final MacroTask task;

		TimedTask(long readyAt, long seq, MacroTask task) {
			this.readyAt = readyAt;
			this.seq = seq;
			this.task = task;
		}

		@Override
		public int compareTo(TimedTask o) {
			// nanoTime values: compare the difference, not the values
			int c = Long.signum(readyAt - o.readyAt);
			return c != 0 ? c : Long.compare(seq, o.seq);
		}
	}

	
	private final JSEnvironment env;
	
	private volatile AsyncData _asyncData;
	private volatile AsyncTask currentTask;
	private volatile boolean STOP = false;
	// An uncatchable exception (a stop request, an interrupt) raised where it
	// cannot simply propagate - on an asyncFunction() worker thread. The drain
	// loop rethrows it on the thread that runs the script, instead of letting
	// it turn into a (catchable) promise rejection.
	private volatile JSRuntimeUncatchableException fatal;
	// True for the whole extent of an in-progress drainPendingTasks() call
	// (set/cleared around its own loop) - lets execute() detect a NESTED
	// call (e.g. `await import(...)`: the import() microtask, itself being
	// run BY drainPendingTasks(), synchronously loads the target module via
	// its own executeWithContext()->execute() call, on the SAME thread) and
	// skip draining/shutting down a second time. Without this, the inner
	// execute() call's own drainPendingTasks() sees the OUTER call's still-
	// elevated pendingAsync (the awaiting async function hasn't finished
	// yet - it's parked waiting for THIS import to resolve) and blocks
	// forever on the wakeups semaphore for a signal that can only arrive
	// once control returns back up through this very call stack - a
	// genuine deadlock, not just a slow path (confirmed via a 15s-timeout
	// repro: `(async()=>{ await import('a.js'); })()` never completes).
	private volatile boolean draining = false;

	// Set only around a runAsyncBody() coroutine's OWN body execution (on
	// that coroutine's dedicated thread, for its entire lifetime - see
	// GeneratorImpl's Exchanger-based rendezvous, which suspends/resumes the
	// SAME thread rather than hopping threads), so await() can tell "am I
	// running inside a runAsyncBody()-driven coroutine right now" without
	// threading a parameter through every interpreter/transpiler call frame.
	// A nested asyncFunction() call (e.g. a Java library wrapping blocking
	// I/O, submitted to ITS OWN separate worker thread) never sees this set,
	// since it never calls await() at all - see JSExecutor.runAsyncBody()'s
	// own doc for the asyncFunction()/runAsyncBody() split.
	private final ThreadLocal<Yielder<Object>> currentDriverYielder = new ThreadLocal<>();

	public JSAsyncExecutor(JSEnvironment env) {
		this.env = env;
	}
	
    public synchronized void shutdown() {
    	if(_asyncData!=null) {
    		_asyncData.shutdown();
    		_asyncData = null;
    	}
    }

	
	/* ============================================================ */
	/* 1. Scheduler: event loop + queues                            */
	/* ============================================================ */

	private boolean isAsyncData() {
		return _asyncData!=null;
	}
	private synchronized AsyncData getAsyncData() {
		if(_asyncData==null) {
			_asyncData = new AsyncData();
		}
		return _asyncData;
	}

	/* ---------------------- Public API ---------------------- */
	
	@Override
	public void queueMicrotask(MicroTask r) {
		AsyncData asyncData = getAsyncData();
		asyncData.lock.lock();
		try {
			//Console.log("Enqueue micro task: {0}", microQ.toString());
			asyncData.microQ.addLast(r);
		} finally {
			asyncData.lock.unlock();
		}
		//Console.log("queueMicrotask release: {0}",wakeups.availablePermits());
		asyncData.wakeups.release();
	}

	@Override
	public void queueMacrotask(MacroTask r) {
		AsyncData asyncData = getAsyncData();
		asyncData.lock.lock();
		try {
			//Console.log("Enqueue macro task: {0}", microQ.toString());
			asyncData.macroQ.addLast(r);
		} finally {
			asyncData.lock.unlock();
		}
		//Console.log("queueMacrotask release: {0}",wakeups.availablePermits());
		asyncData.wakeups.release();
	}

	@Override
	public void queueMacrotask(MacroTask r, long delayMs) {
		if (delayMs <= 0) {
			queueMacrotask(r);
			return;
		}
		AsyncData asyncData = getAsyncData();
		long readyAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
		asyncData.lock.lock();
		try {
			asyncData.timedQ.add(new TimedTask(readyAt, asyncData.timedSeq.incrementAndGet(), r));
		} finally {
			asyncData.lock.unlock();
		}
		// Wake the loop so it can shorten its wait to the new earliest deadline.
		asyncData.wakeups.release();
	}

	void _signalWork() {
		// Never (re)creates the async data: a worker finishing after shutdown()
		// must not allocate a new thread pool nobody will ever shut down
		AsyncData asyncData = _asyncData;
		if(asyncData!=null) {
			asyncData.wakeups.release();
		}
	}

	private void _signalWork(AsyncData asyncData) {
		asyncData.wakeups.release();
	}

	private void reportFatal(JSRuntimeUncatchableException e) {
		if(fatal==null) {
			fatal = e;
		}
		_signalWork();
	}

	// Rethrow (once) an uncatchable exception reported from another thread
	private void checkFatal() {
		JSRuntimeUncatchableException f = fatal;
		if(f!=null) {
			fatal = null;
			throw f;
		}
	}

	@Override
	public AsyncTask getCurrentAsyncTask() {
		return currentTask;
	}

	@Override
    public Object execute(@NonNull Supplier<Object> code, boolean async) {
		return execute(code, async, (v,t) -> {});
	}

	// onGenuineCompletion fires exactly once with code's own TRUE final
	// outcome - see JSExecutor's own doc for why this differs from this
	// method's own return value in the nested-with-still-pending-coroutine
	// case (a narrower, pre-existing race this callback exists specifically
	// to give callers - module evaluation-completion tracking, so far - a
	// reliable way to work around, without needing drainPendingTasks() to
	// be re-entered here at all).
	@Override
    public Object execute(@NonNull Supplier<Object> code, boolean async, @NonNull BiConsumer<Object,Throwable> onGenuineCompletion) {
		if(!draining) {
			// A new outermost execution: a stop() requested for a previous one
			// must not disable this one's drain loop
			STOP = false;
			fatal = null;
		}
		if(!async) {
			Object returnValue;
			try {
				returnValue = code.get();
			} catch(Throwable t) {
				onGenuineCompletion.accept(null, t);
				throw t;
			}
			// code's own completion is reached HERE, the instant code.get()
			// returns - a non-async caller's body is done running, full
			// stop, by definition. Firing onGenuineCompletion BEFORE
			// drainPendingTasks() (not after) matters: the drain loop below
			// may itself process OTHER queued work - e.g. a dynamic
			// import() of THIS SAME module, queued as a microtask by
			// code.get() itself - that depends on THIS callback having
			// already fired (a self-import's addEvaluationCompletionCallback()
			// registration, reached from inside that microtask, needs
			// moduleStatus already updated to resolve/reject its own
			// promise DURING this drain, not after it - firing this after
			// drainPendingTasks() left that promise permanently pending,
			// since nothing would ever call this callback while still
			// inside the very call that's supposed to settle it).
			onGenuineCompletion.accept(returnValue, null);
			if(!draining && isAsyncData()) {
				try {
					drainPendingTasks();
					checkFatal();
				} finally {
					shutdown();
				}
			}
			return returnValue;
		}

		// 0: not completed yet, 1: returned, 2: threw
		AtomicInteger type = new AtomicInteger(0);
		AtomicReference<Object> result = new AtomicReference<>();
		// Runs code's own synchronous prefix (up to its first top-level
		// await, if any) synchronously right here, via the same spec-correct
		// coroutine driver runAsyncBody() uses - see startCoroutine()'s own
		// doc for why it must claim `draining` early. Any further awaits
		// resume later, from inside ordinary promise-reaction microtasks
		// (never blocking any thread), which the drainPendingTasks() call
		// below (unconditionally still needed to drive those, plus any
		// OTHER unrelated queued work, to full completion) picks up exactly
		// like any other queued microtask. onGenuineCompletion fires from
		// INSIDE these same onComplete/onError callbacks - synchronously,
		// right here, if the coroutine finishes within this first step;
		// later, from inside a promise reaction, otherwise.
		startCoroutine(makeCoroutine(code::get),
			v -> { result.set(v); type.set(1); onGenuineCompletion.accept(v, null); },
			t -> { result.set(JSRuntimeException.exceptionObject(t)); type.set(2); onGenuineCompletion.accept(null, t); });

		// A NESTED call (draining already true - see its own field doc, and
		// startCoroutine()'s: it leaves `draining` exactly as it found it
		// for a nested call) must not drain/shut down a second time - the
		// OUTER, already-running drainPendingTasks() on this same thread
		// will pick up anything this call just queued once it returns.
		// startCoroutine()'s own synchronous first step already means
		// result/type ARE correctly set here for the common nested case
		// (the nested code has no top-level await of its own, so its
		// coroutine completes within that one synchronous step) - only a
		// nested module/code block that ALSO has its own top-level await
		// still reads result/type prematurely here (this method's OWN
		// return value only, not onGenuineCompletion above, which a caller
		// that cares should use instead).
		if(!draining && isAsyncData()) {
			try {
				// For the non-nested call, result/type are guaranteed
				// correct by the time this returns: drainPendingTasks()
				// only exits once every queued microtask/macrotask/timer -
				// including every resume this coroutine's own promise
				// reactions still need - has run, which is exactly when
				// onComplete/onError above will have fired.
				drainPendingTasks();
				checkFatal();
			} finally {
				shutdown();
			}
			// Stopped before the code completed: not a success. (Code that
			// awaits a promise nothing will ever settle also ends here
			// without completing - it returns undefined.)
			if (type.get()==0 && STOP) {
				throw new JSRuntimeInterruptException();
			}
		}
		if (type.get()==2) {
			throw RuntimeUtil.wrap(result.get());
		}
		return result.get();
	}

	@Override
	public void performMicrotaskCheckpoint() {
		if(!draining && isAsyncData()) {
			drainPendingTasks();
		}
	}

	// See JSExecutor.drainAndShutdownIfOutermost()'s own doc - same
	// drain-then-release pattern execute()'s own non-nested tail uses.
	@Override
	public void drainAndShutdownIfOutermost() {
		if(!draining && isAsyncData()) {
			try {
				drainPendingTasks();
			} finally {
				shutdown();
			}
		}
	}
	
	private void drainPendingTasks() {
		drain(() -> false);
	}

	// See JSExecutor.drainUntil()'s own doc.
	@Override
	public void drainUntil(BooleanSupplier condition) {
		drain(condition);
	}

	// The event loop: runs every microtask, then one macrotask (a due timer
	// being one), and so on, until nothing is pending any more, stop() is
	// called or `until` becomes true. Blocks on the wakeups semaphore when
	// only async work (or a timer not yet due) is pending.
	private void drain(BooleanSupplier until) {
		AsyncData asyncData = _asyncData;
		if(asyncData==null) {
			return;
		}
		boolean wasDraining = draining;
		draining = true;
		try {
			while (!STOP && !until.getAsBoolean() && asyncData.hasPending()) {
				checkFatal();
				// Every enqueue/completion releases a permit, but the queues
				// are re-checked below anyway: consume the permits already
				// there, so the wait further down only returns on a NEW signal
				// instead of spinning over stale ones.
				asyncData.wakeups.drainPermits();
				AsyncTask task = null;
				long waitNanos = -1L; // <0 means "wait indefinitely"
				asyncData.lock.lock();
				try {
					// Due timers become macrotasks
					long now = System.nanoTime();
					while (!asyncData.timedQ.isEmpty()
							&& asyncData.timedQ.peek().readyAt - now <= 0) {
						asyncData.macroQ.addLast(asyncData.timedQ.poll().task);
					}
					if (!asyncData.microQ.isEmpty()) {
						task = asyncData.microQ.pollFirst();
					} else if (!asyncData.macroQ.isEmpty()) {
						task = asyncData.macroQ.pollFirst();
					} else if (!asyncData.timedQ.isEmpty()) {
						waitNanos = asyncData.timedQ.peek().readyAt - now;
					}
				} finally {
					asyncData.lock.unlock();
				}

				if (task != null) {
					currentTask = task;
					try {
						_executeTask(task);
					} catch (Throwable e) {
						throw RuntimeUtil.wrap(e);
					} finally {
						currentTask = null;
					}
					continue;
				}

				// No work in queues: check for idle termination
				if (STOP || until.getAsBoolean() || !asyncData.hasPending()) {
					break;
				}

				// Wait until the next signal (cannot miss one: a permit released
				// after drainPermits() above stays available). If a timer is
				// pending, cap the wait at its deadline.
				if (waitNanos < 0) {
					asyncData.wakeups.acquire();
				} else if (waitNanos > 0) {
					asyncData.wakeups.tryAcquire(waitNanos, TimeUnit.NANOSECONDS);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new JSRuntimeInterruptException();
		} finally {
			draining = wasDraining;
		}
	}

	void _executeTask(AsyncTask task) {
		task.run();
	}
	
	
	/* ------------------------------------------------------- */
    /* Stop                                                    */
    /* ------------------------------------------------------- */

	public void stop() {
        STOP = true;
    }

    
    /* ============================================================ */
	/* 2. Await */
	/* ============================================================ */

	@Override
	public Object await(Object value) {
		// Running inside a runAsyncBody()-driven coroutine (a real JS async
		// function or module top-level await, on this coroutine's own
		// dedicated thread) - suspend it via yield() instead of blocking the
		// thread outright, so the synchronous prefix/resume semantics
		// documented on runAsyncBody() hold. See that method's own doc and
		// driveAsyncBody() (the consumer side of this same yield). Normalize
		// even an already-a-BuiltinPromise value through PromiseResolve here
		// (not just plain values) - harmless (PromiseResolve is a no-op for
		// a genuine promise of the right realm) and keeps driveAsyncBody()
		// simple (every yielded value is always already a real BuiltinPromise).
		Yielder<Object> yielder = currentDriverYielder.get();
		if(yielder!=null) {
			return yielder.yield(BuiltinPromiseConstructor.resolve(env,value));
		}
		if(value instanceof BuiltinPromise promise) {
			Thread current = Thread.currentThread();
			AtomicInteger type = new AtomicInteger(0);
			AtomicReference<Object> result = new AtomicReference<>();
	
			promise.performPromiseThen_((t, a) -> {
				result.set(a[0]);
				type.set(1);
				LockSupport.unpark(current);
				return RuntimeUtil.UNDEFINED;
			}, (t, a) -> {
				result.set(a[0]);
				type.set(2);
				LockSupport.unpark(current);
				return RuntimeUtil.UNDEFINED;
			});

			while (type.get()==0) {
				LockSupport.park();
				// shutdown() interrupts the workers: don't spin on a promise
				// whose reactions will never run any more
				if (type.get()==0 && Thread.interrupted()) {
					throw new JSRuntimeInterruptException();
				}
			}
			if (type.get()==2) {
				throw RuntimeUtil.wrap(result.get());
			}
			return result.get();
		}
		// Thenable: if it's an object with a callable "then" property, wrap it
		// in a new BuiltinPromise and await that.
		if (RuntimeUtil.isObject(env, value)) {
			Object thenValue;
			try {
				JSAccessor a = env.getAccessor(value);
				thenValue = a.getProperty(value, "then", null);
			} catch (Throwable ex) {
				throw RuntimeUtil.wrap(ex);
			}

			if (thenValue instanceof org.monflabs.galtajs.rt.builtins.Callable then) {
				// Wrap the thenable in a BuiltinPromise and await it
				BuiltinPromise wrapper = new BuiltinPromise(env);
				final Object thenable = value;
				AtomicBoolean called = new AtomicBoolean(false);
				try {
					then.call(thenable,
						(org.monflabs.galtajs.rt.builtins.Callable) (thisArg, args) -> {
							if (called.getAndSet(true)) return null;
							Object y = args.length > 0 ? args[0] : null;
							wrapper.resolvePromise(y);
							return null;
						},
						(org.monflabs.galtajs.rt.builtins.Callable) (thisArg, args) -> {
							if (called.getAndSet(true)) return null;
							Object r = args.length > 0 ? args[0] : null;
							wrapper.reject(r);
							return null;
						});
				} catch (JSRuntimeUncatchableException t) {
					throw t;
				} catch (Throwable t) {
					if (!called.get()) {
						wrapper.reject(JSRuntimeException.exceptionObject(t));
					}
				}
				return await(wrapper);
			}
		}
		return value;
	}

	/* ============================================================ */
	/* 3. Generators */
	/* ============================================================ */
	
	@Override
	public Generator<Object,Object> generator(Function<Yielder<Object>,Object> body) {
		AsyncData asyncData = getAsyncData();
		return GeneratorImpl.create(asyncData.executor,body);
	}

	
	/* ============================================================ */
	/* 4. Async functions */
	/* ============================================================ */
	
	@Override
	public BuiltinPromise asyncFunction(Callable<Object> body) {
		AsyncData asyncData = getAsyncData();
		BuiltinPromise p = new BuiltinPromise(env);
		asyncData.pendingAsync.incrementAndGet();
		asyncData.executor.submit(() -> {
			try {
				Object result = body.call();
				p.resolvePromise(result);
			} catch (JSRuntimeUncatchableException e) {
				reportFatal(e);
			} catch (Throwable e) {
				p.reject(JSRuntimeException.exceptionObject(e));
			} finally {
				asyncData.pendingAsync.decrementAndGet();
				// The captured data, not getAsyncData(): see _signalWork()
				_signalWork(asyncData);
			}
		});
		return p;
	}

	// Spec-correct driver for a genuine JS async-function/module-top-level
	// body: runs body SYNCHRONOUSLY on the calling thread up to its first
	// await() suspension (or full completion), matching AsyncFunctionStart's
	// single-threaded, cooperative semantics - see JSExecutor.runAsyncBody()'s
	// own doc for the full rationale and the asyncFunction() split. execute()'s
	// own async branch (module top-level await) below reuses this same
	// machinery via startCoroutine()/driveCoroutine() directly (it doesn't
	// settle a BuiltinPromise - its caller wants the final value/exception
	// synchronously, once EVERYTHING, not just this coroutine, has drained).
	//
	// Implemented by reusing GeneratorImpl - the exact same Exchanger-based
	// coroutine primitive `function*` generators already use, proven correct
	// there. A coroutine's `next(v)`/`throwInto(t)` call genuinely BLOCKS the
	// calling thread until the coroutine's body (running on its own
	// dedicated thread for its whole lifetime) reaches its next yield() or
	// completion - exactly the synchronous-call semantics spec's "run up to
	// the next suspension point" requires, for free, without inventing a new
	// latch protocol (see git history / KnownGaps.md for four earlier,
	// reverted attempts to bolt this behavior onto the OLD
	// submit-and-return-immediately design via ad hoc latches instead).
	//
	// await()'s own new branch (below) yields the awaited value's normalized
	// BuiltinPromise back through this SAME coroutine when called on its
	// thread; driveCoroutine() below is the consumer side, mirroring
	// BuiltinAsyncGeneratorPrototype.drive()'s already-proven pattern
	// (register .then_(), resume via next()/throwInto() inside the
	// reaction - i.e. as an ordinary later microtask, never blocking).
	@Override
	public BuiltinPromise runAsyncBody(Callable<Object> body) {
		BuiltinPromise p = new BuiltinPromise(env);
		startCoroutine(makeCoroutine(body), p::resolvePromise, t -> p.reject(JSRuntimeException.exceptionObject(t)));
		return p;
	}

	// Wraps a THROWN body completion as a normal RETURN (never lets the
	// exception propagate out of the Function<Yielder,Object> body itself) -
	// see driveCoroutine()'s own matching unwrap. GeneratorImpl's own
	// uncaught-exception path (ensureStarted()'s catch(Throwable){exception(t);}
	// finally{finish();}) does TWO toConsumer exchanges, but a consumer whose
	// next()/throwInto() call re-throws on the FIRST (exception()'s) only ever
	// performs ONE - permanently parking the coroutine's own thread on the
	// second (finish()'s), since nothing ever calls next() on it again (it's
	// done, from the driver's point of view, once it has rejected). Harmless
	// in isolation (a cheap parked virtual thread - matches how a `function*`
	// that throws uncaught already behaves, today, unremarked), but this
	// driver runs vastly more often than that already-rare shape (every
	// plain async function call and every module's top-level await, and
	// test262 alone has thousands of intentionally-rejecting/throwing async
	// tests) - confirmed via two full test262 sweeps to reproducibly leave
	// enough parked threads to blow the surefire fork's own shutdown timeout
	// afterward (identical 33-error result both times - a build/shutdown
	// artifact, not a test outcome difference). Fixed by never letting
	// GeneratorImpl see the throw at all: an error completes this coroutine
	// exactly like a normal return (one exchange, clean thread exit), just
	// carrying a marker driveCoroutine() unwraps back into an error.
	private record AsyncBodyError(Throwable cause) {}

	private GeneratorImpl<Object,Object> makeCoroutine(Callable<Object> body) {
		AsyncData asyncData = getAsyncData();
		return GeneratorImpl.create(asyncData.executor, yielder -> {
			currentDriverYielder.set(yielder);
			try {
				return body.call();
			} catch(Throwable t) {
				return new AsyncBodyError(t);
			} finally {
				currentDriverYielder.remove();
			}
		});
	}

	// Runs a freshly-created coroutine's own FIRST synchronous step (see
	// driveCoroutine()). Claims `draining` EARLY, for the extent of just this
	// first blocking step, if not already claimed by an outer drain loop -
	// this is the fix the third of four prior (reverted) attempts at this
	// same redesign found necessary (see KnownGaps.md): without it, a NESTED
	// runAsyncBody()/execute(...,true) call reached DURING this synchronous
	// prefix (e.g. a module's own top-level `import` synchronously loading a
	// dependency, itself calling execute() again on this SAME thread, before
	// the true outermost caller has entered its own drainPendingTasks() loop
	// at all) would ALSO see `!draining` and enter drainPendingTasks() itself -
	// exactly the multi-threaded-draining hazard the single `draining` flag
	// exists to prevent (see that field's own doc comment). Every RESUME
	// (driveCoroutine() calls after the first) is already correctly covered:
	// they're only ever reached from inside a promise reaction, which only
	// ever runs from inside drainPendingTasks()'s own loop, so `draining` is
	// already true there.
	private void startCoroutine(GeneratorImpl<Object,Object> coro, Consumer<Object> onComplete, Consumer<Throwable> onError) {
		boolean wasDraining = draining;
		if(!wasDraining) {
			draining = true;
		}
		try {
			driveCoroutine(coro, null, false, onComplete, onError);
		} finally {
			if(!wasDraining) {
				draining = false;
			}
		}
	}

	// See JSExecutor.runWithDrainSuppressed()'s own doc - identical claim/
	// restore pattern to startCoroutine() above, just wrapping arbitrary
	// caller code instead of one coroutine's own synchronous first step.
	@Override
	public <T> T runWithDrainSuppressed(java.util.function.Supplier<T> body) {
		boolean wasDraining = draining;
		if(!wasDraining) {
			draining = true;
		}
		try {
			return body.get();
		} finally {
			if(!wasDraining) {
				draining = false;
			}
		}
	}

	// resumeArg/isThrow are ignored for the very first call (coro hasn't
	// started - GeneratorImpl.next(null) both starts it and runs its
	// synchronous prefix). Every later call is reached only from inside a
	// promise reaction (a microtask), resuming the coroutine's own next
	// synchronous span - genuinely blocking that microtask's thread until
	// this span completes, exactly as if it were one long native call, which
	// is spec-correct for a single-threaded event loop.
	private void driveCoroutine(GeneratorImpl<Object,Object> coro, Object resumeArg, boolean isThrow, Consumer<Object> onComplete, Consumer<Throwable> onError) {
		Object yielded;
		try {
			yielded = isThrow ? coro.throwInto(RuntimeUtil.wrap(resumeArg)) : coro.next(resumeArg);
		} catch(NoSuchElementException done) {
			// The coroutine is done - either a genuine return (getReturnValue()
			// is the body's own return value) or an uncaught throw, wrapped as
			// an AsyncBodyError by makeCoroutine() specifically so it reaches
			// here as a normal completion rather than propagating as a real
			// exception - see that field's own doc for why.
			if(coro.getReturnValue() instanceof AsyncBodyError err) {
				// A stop request/interrupt is not a rejection: propagate it to
				// whoever drives this step (execute() or the drain loop)
				if(err.cause() instanceof JSRuntimeUncatchableException u) {
					throw u;
				}
				onError.accept(err.cause());
			} else {
				onComplete.accept(coro.getReturnValue());
			}
			return;
		} catch(JSRuntimeUncatchableException t) {
			throw t;
		} catch(Throwable t) {
			onError.accept(t);
			return;
		}
		// await()'s own new branch (below) always yields a genuine, already-
		// normalized BuiltinPromise (PromiseResolve-wrapped even for a plain
		// value) - guaranteeing the "at least one tick" behavior spec's
		// Await requires unconditionally.
		BuiltinPromise awaited = (BuiltinPromise)yielded;
		awaited.performPromiseThen_(
			(thisArg,args) -> {
				driveCoroutine(coro, args.length>0 ? args[0] : RuntimeUtil.UNDEFINED, false, onComplete, onError);
				return RuntimeUtil.UNDEFINED;
			},
			(thisArg,args) -> {
				driveCoroutine(coro, args.length>0 ? args[0] : RuntimeUtil.UNDEFINED, true, onComplete, onError);
				return RuntimeUtil.UNDEFINED;
			}
		);
	}
}
