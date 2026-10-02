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
package org.monflabs.util.generators;

import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A generator yields {@code <T>} and can return a final value {@code <V>}
 * For example, it can yield objects and return the number of object yielded.
 * <p>
 * The body runs on a thread of the executor, handing each value to the consumer through
 * a rendezvous. The object returned by {@link #create} is only the consumer-facing
 * handle: the body runs against a separate internal state object and never references
 * the handle. When the handle becomes unreachable while the body is still parked in
 * {@code yield()}, a {@link Cleaner} abandons the body, which then unwinds (as with
 * {@link #returnWith}) and lets its thread terminate. The body also unwinds when its
 * executor is shut down with {@code shutdownNow()}. Things to know:
 * <ul>
 * <li>The executor must be able to start every generator's body without waiting for
 * another one to complete: a body parked in {@code yield()} keeps its thread. An
 * unbounded executor is required - the default one (see {@link GeneratorScheduler})
 * creates a virtual thread per generator. With a bounded pool, the generators beyond
 * the pool size never start and their consumers wait forever (a warning is logged when
 * a body has not started after {@value #START_WARNING_SECONDS} seconds).</li>
 * <li>On JDK 21 to 23, a virtual thread that blocks inside a {@code synchronized} block
 * or method pins its carrier thread (JEP 491, fixed in JDK 24). A body that yields
 * while holding a monitor therefore pins a carrier until it is resumed; once every
 * carrier is pinned, no other virtual thread can run and the application deadlocks.
 * Don't yield inside {@code synchronized} code (use a {@code ReentrantLock}), or run
 * such bodies on platform threads ({@link GeneratorScheduler#createPlatformExecutor()}).</li>
 * <li>The body runs on another thread: thread locals set by the consumer are not
 * visible to it. An {@code InheritableThreadLocal} is inherited when the body's thread
 * is created, that is on the first resume ({@code hasNext()}, {@code next()}...), not
 * when the generator is created.</li>
 * <li>Interrupting the consumer while it waits for the body (or calling it with its
 * interrupt flag set) abandons the generator: the call throws a
 * {@link CancellationException}, the interrupt flag stays set, and the body is unwound
 * in the background.</li>
 * <li>An abandoned body (closed, interrupted or collected generator) gets a
 * {@link GeneratorReturnSignal} from its pending {@code yield()}. A body that swallows
 * it and yields again gets a {@link GeneratorAbandonedError}; if it still yields after
 * that, it stays parked in that {@code yield()} until its executor is shut down, rather
 * than spinning.</li>
 * </ul>
 *
 * @param <T>
 * @param <V>
 */
public class GeneratorImpl<T,V> implements Generator<T,V>, Yielder<T> {

	private static final class CleanerHolder {
		static final Cleaner CLEANER = Cleaner.create();
	}

	public static <T,V> GeneratorImpl<T,V> create(Function<Yielder<T>,V> body) {
		return create(GeneratorScheduler.getExecutorService(),body);
	}
	public static <T,V> GeneratorImpl<T,V> create(ExecutorService executor, Function<Yielder<T>,V> body) {
		return new GeneratorImpl<>(new Core<>(executor, body));
	}

	private final Core<T,V> core;

	private GeneratorImpl(Core<T,V> core) {
		this.core = core;
		// The action references the core only, never this handle
		core.cleanable = CleanerHolder.CLEANER.register(this, core::abandon);
	}

	// Every consumer method keeps the handle reachable until it returns: otherwise the
	// JIT may consider it dead as soon as `core` has been read, and the cleaner would
	// abandon the body while the consumer is still waiting for it.

	@Override
	public V getReturnValue() {
		return core.returnValue;
	}

	@Override
	public boolean hasNext() {
		try {
			return core.hasNext();
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	@Override
	public T next() {
		try {
			return core.next();
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	// Delivers resumeValue into the paused yield() (its return value) and runs until
	// the next yield or completion. If a value was already buffered by a prior
	// hasNext() call (which delivers no resume value), that value is returned as-is.
	@Override
	public T next(Object resumeValue) {
		try {
			return core.consume(resumeValue);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	@Override
	public T throwInto(Throwable t) {
		try {
			return core.consume(new _ThrowSignal_(t));
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	@Override
	public T returnWith(V value) {
		try {
			return core.consume(new _ReturnSignal_(value));
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	/**
	 * Completes the generator. A body parked in yield() is resumed with a return
	 * signal so it unwinds (running its finally blocks) and its thread terminates.
	 */
	@Override
	public void close() {
		try {
			core.close();
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	@SuppressWarnings("unchecked")
	public <TH extends Throwable> void throwException(Throwable t) throws TH {
	    throw (TH) t; // compiler thinks it's throwing T, erased at runtime
	}

	// Body-side methods, kept for compatibility. The body itself receives the internal
	// state object as its Yielder, so that it never references this handle.

	@Override
	public T yield(T v) {
		return core.yield(v);
	}

	@Override
	public void exception(Throwable t) {
		core.exception(t);
	}

	@Override
	public void finish() {
		core.finish();
	}


	private record _Exception_ (Throwable value) {};
	// Consumer-to-generator signals, delivered through the toGenerator exchanger in
	// place of a plain resume value - see throwInto()/returnWith() and yield().
	private record _ThrowSignal_ (Throwable value) {};
	private record _ReturnSignal_ (Object value) {};

	// Returned by a body-side handoff that was abandoned instead of completed
	private static final Object ABANDONED = new Object();

	// How long a consumer waits for a handoff before checking whether the body is gone
	private static final long CONSUMER_POLL_MS = 100;
	// A body that has not started running after this delay is reported (bounded executor)
	static final int START_WARNING_SECONDS = 10;

	@SuppressWarnings("unchecked")
	private static <TH extends Throwable> void sneakyThrow(Throwable t) throws TH {
	    throw (TH) t;
	}

	/**
	 * The generator state shared by the consumer and the body. The body (and so its
	 * thread) only ever references this object, never the consumer-facing handle.
	 */
	private static final class Core<T,V> implements Yielder<T> {

		// Two separate handoff channels decouple "the generator hands out a yielded
		// value" from "the consumer delivers a resume value": a JS generator's first
		// next() call must start the body and read its first yielded value WITHOUT
		// unblocking it yet - it only unblocks (with whatever the *next* next(value)
		// call supplies) when that next call arrives. A single shared Exchanger can't
		// express that gap, since each exchange() is an atomic, paired send-and-receive.
		private final Exchanger<Object> toConsumer = new Exchanger<>();
		private final Exchanger<Object> toGenerator = new Exchanger<>();

		private Boolean hasValue;
		private T value;
		private V returnValue;
		// Whether a yield() call is currently blocked waiting on toGenerator - i.e.
		// whether the next fetch must first deliver a resume value to unblock it.
		private boolean pendingResume;

		// Guards against reentrant resume calls (e.g. a filter()/map() predicate,
		// running as part of this generator's own body execution, calling back
		// into THIS generator's next()) - see GeneratorExecutingException's own
		// doc comment for why this would otherwise deadlock instead of throwing.
		private final AtomicBoolean executing = new AtomicBoolean(false);

		// The body doesn't run on a background thread until the generator is first
		// resumed (next()/hasNext()/throwInto()/returnWith()) - a mere function call
		// must return a suspended generator without executing any of its body.
		private final ExecutorService executor;
		private final Function<Yielder<T>,V> body;
		private boolean started;

		// Set when nobody will ever take the body's handoffs again (the handle was
		// collected, the consumer was interrupted, the executor was shut down): every
		// body-side handoff then unwinds instead of waiting for a consumer.
		private volatile boolean abandoned;
		// The thread running the body, while it runs. Guarded by `this` so that
		// abandon() never interrupts a pooled thread that has already moved on.
		private Thread bodyThread;
		// The body task has ended: no handoff will ever come again
		private volatile boolean terminated;
		// Set by the body's thread once it actually runs (see the bounded executor warning)
		private volatile boolean bodyRunning;
		private long startNanos;
		private boolean startWarned;
		// The yields made by the body after it was abandoned (body thread only)
		private int abandonedYields;
		private volatile Cleaner.Cleanable cleanable;

		Core(ExecutorService executor, Function<Yielder<T>,V> body) {
			this.executor = executor;
			this.body = body;
		}

		// Abandons the body: marks it so and wakes it up if it waits in a handoff.
		// Called by the cleaner thread, so it must never block.
		void abandon() {
			abandoned = true;
			synchronized(this) {
				if(bodyThread!=null) {
					bodyThread.interrupt();
				}
			}
		}

		private void ensureStarted() {
			if(!started) {
				started = true;
				startNanos = System.nanoTime();
				executor.submit(this::runBody);
			}
		}

		@SuppressWarnings("unchecked")
		private void runBody() {
			synchronized(this) {
				bodyThread = Thread.currentThread();
			}
			bodyRunning = true;
			try {
				if(abandoned) {
					return;
				}
				// EXACTLY ONE terminal handoff on toConsumer, ever: the consumer's
				// fetchNext() performs exactly one matching exchange per resume, so a
				// second one (e.g. finish() from a finally after exception()) would
				// park this thread forever.
				try {
					returnValue = body.apply(this);
					finish();
				} catch(GeneratorReturnSignal grs) {
					// returnWith() unwound the body without it catching the signal:
					// that is a normal completion with the forced return value, not
					// an error to hand back to the consumer
					returnValue = (V) grs.getValue();
					finish();
				} catch(Throwable t) {
					exception(t);
				}
			} finally {
				synchronized(this) {
					bodyThread = null;
				}
				terminated = true;
				if(abandoned) {
					// Our own wake-up call: don't leak it to the executor's next task
					Thread.interrupted();
				}
				Cleaner.Cleanable c = cleanable;
				if(c!=null) {
					// Nothing left to clean: unregister now rather than at GC time
					c.clean();
				}
			}
		}

		// A consumer-side handoff. It polls, so that a body that went away without a
		// final handoff (abandoned on executor shutdown) can't leave it waiting forever.
		private Object consumerExchange(Exchanger<Object> exchanger, Object v) throws InterruptedException {
			while(true) {
				try {
					return exchanger.exchange(v, CONSUMER_POLL_MS, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					// The body ended without a final handoff, or never ran at all (its
					// task was dropped by shutdownNow() before it could start)
					if(terminated || executor.isTerminated()) {
						throw new CancellationException("The generator body was abandoned");
					}
					checkStarted();
				}
			}
		}

		// With a bounded executor whose threads are all held by other (parked) generator
		// bodies, a body never starts. That can't be detected for sure, but a body still
		// not running after a while is reported once, instead of the consumer hanging
		// silently.
		private void checkStarted() {
			if(!bodyRunning && !startWarned && System.nanoTime()-startNanos > TimeUnit.SECONDS.toNanos(START_WARNING_SECONDS)) {
				startWarned = true;
				System.getLogger(GeneratorImpl.class.getName()).log(System.Logger.Level.WARNING,
						"A generator body has not started after {0} seconds: its executor may be bounded, and all its threads held by other generators (generator executors must be unbounded)",
						START_WARNING_SECONDS);
			}
		}

		// A body-side handoff. Interrupts are deferred while a consumer may still take
		// the handoff: returning early (e.g. with a bogus null resume value) would
		// desynchronize the body from the consumer, which then parks forever. Once the
		// generator is abandoned, or its executor shut down, the handoff is given up.
		private Object bodyExchange(Exchanger<Object> exchanger, Object v) {
			boolean interrupted = false;
			try {
				while(true) {
					if(abandoned) {
						return ABANDONED;
					}
					try {
						return exchanger.exchange(v);
					} catch (InterruptedException e) {
						if(executor.isShutdown()) {
							abandoned = true;
						} else {
							interrupted = true;
						}
					}
				}
			} finally {
				if(interrupted) {
					Thread.currentThread().interrupt();
				}
			}
		}

		@SuppressWarnings("unchecked")
		private void fetchNext(Object resumeValue) {
			if(!executing.compareAndSet(false, true)) {
				throw new GeneratorExecutingException();
			}
			try {
				// A return()/throw() signal arriving while the body isn't actually
				// suspended at a live yield() - either not started yet, or already
				// completed - must be handled without ever running/resuming the
				// body: exchanging through toGenerator here would either
				// incorrectly start the body from scratch (silently ignoring the
				// signal) or hang forever (no thread left listening once
				// completed). `started && pendingResume` is exactly "body is
				// parked in a live yield() call, safe to hand the signal to it".
				if(!(started && pendingResume)) {
					if(resumeValue instanceof _ReturnSignal_ rs) {
						started = true;
						hasValue = Boolean.FALSE;
						returnValue = (V) rs.value();
						return;
					}
					if(resumeValue instanceof _ThrowSignal_ ts) {
						started = true;
						hasValue = Boolean.FALSE;
						sneakyThrow(ts.value());
					}
				}
				ensureStarted();
				if (pendingResume) {
					consumerExchange(toGenerator, resumeValue);
					pendingResume = false;
				}
				Object v = consumerExchange(toConsumer, null);
				if (v == this) {
					hasValue = Boolean.FALSE;
					pendingResume = false;
					return;
				}
				if (v instanceof _Exception_ ex) {
					hasValue = Boolean.FALSE;
					sneakyThrow(ex.value);
				}
				hasValue = Boolean.TRUE;
				this.value = (T) v;
				pendingResume = true;
			} catch (InterruptedException e) {
				// The consumer gave up: the generator is done from its point of view.
				// The body is either parked in yield() or still running towards its
				// next handoff - nobody would ever take that handoff, so it is
				// abandoned and unwinds instead of leaking a parked thread.
				// It is reported as such: silently answering "no more values" would make an
				// interrupted consumer believe it saw every value.
				done();
				abandon();
				Thread.currentThread().interrupt();
				throw new CancellationException("The generator consumer was interrupted: the generator is abandoned");
			} catch (CancellationException e) {
				done();
				throw e;
			} finally {
				executing.set(false);
			}
		}

		private void done() {
			hasValue = Boolean.FALSE;
			pendingResume = false;
			value = null;
		}

		boolean hasNext() {
			if(hasValue==null) {
				fetchNext(null);
			}
			return hasValue;
		}

		T next() {
			if (hasNext()) {
				hasValue = null;
				T ret = value;
				value = null;
				return ret;
			}
			throw new NoSuchElementException();
		}

		T consume(Object resumeSignal) {
			// Unlike a plain next(value) (which correctly no-ops once hasValue is
			// already FALSE - an already-completed generator just keeps
			// reporting done), a return()/throw() signal must always reach
			// fetchNext(), even post-completion: GeneratorResumeAbrupt is defined
			// (and observable, e.g. GeneratorPrototype/return/from-state-
			// completed.js) for the completed state too, not just suspendedStart.
			if(hasValue==null || resumeSignal instanceof _ReturnSignal_ || resumeSignal instanceof _ThrowSignal_) {
				fetchNext(resumeSignal);
			}
			if (hasValue) {
				hasValue = null;
				T ret = value;
				value = null;
				return ret;
			}
			throw new NoSuchElementException();
		}

		void close() {
			if(started && pendingResume) {
				try {
					consume(new _ReturnSignal_(null));
				} catch(NoSuchElementException ignored) {
					// Completed without yielding another value: the expected outcome
				} catch(RuntimeException ignored) {
					// The body's finally blocks may throw; the generator is done either way
				}
				if(pendingResume) {
					// A finally block yielded again: the body is parked in that yield(), so
					// it is abandoned (and unwinds) rather than left parked forever
					done();
					abandon();
				}
			}
			if(hasValue==null || hasValue) {
				hasValue = Boolean.FALSE;
				value = null;
			}
			started = true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public T yield(T v) {
			if(bodyExchange(toConsumer, v)==ABANDONED) {
				return abandonedYield();
			}
			Object resumeSignal = bodyExchange(toGenerator, null);
			if(resumeSignal==ABANDONED) {
				return abandonedYield();
			}
			if (resumeSignal instanceof _ThrowSignal_ ts) {
				sneakyThrow(ts.value());
			}
			if (resumeSignal instanceof _ReturnSignal_ rs) {
				throw new GeneratorReturnSignal(rs.value());
			}
			return (T) resumeSignal;
		}

		// A yield() of an abandoned body: asked to return, then, if it swallowed that and
		// yields again, aborted with an Error. If it still yields after that (e.g. a
		// JavaScript "finally { continue }"), it is parked until its executor is shut down:
		// answering every further yield would make it spin forever.
		private T abandonedYield() {
			int n = ++abandonedYields;
			if(n == 1) {
				throw new GeneratorReturnSignal(null);
			}
			if(n == 2) {
				throw new GeneratorAbandonedError();
			}
			while(!executor.isShutdown()) {
				java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
			}
			throw new GeneratorAbandonedError();
		}

		void exception(Throwable t) {
			bodyExchange(toConsumer, new _Exception_(t));
		}

		void finish() {
			bodyExchange(toConsumer, this);
		}
	}
}
