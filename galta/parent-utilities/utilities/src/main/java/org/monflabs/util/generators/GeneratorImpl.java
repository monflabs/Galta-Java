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

import java.util.NoSuchElementException;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A generator yields {@code <T>} and can return a final value {@code <V>}
 * For example, it can yield objects and return the number of object yielded.
 *
 * @param <T>
 * @param <V>
 */

public class GeneratorImpl<T,V> implements Generator<T,V>, Yielder<T> {

	public static <T,V> GeneratorImpl<T,V> create(Function<Yielder<T>,V> body) {
		return create(GeneratorScheduler.getExecutorService(),body);
	}
	public static <T,V> GeneratorImpl<T,V> create(ExecutorService executor, Function<Yielder<T>,V> body) {
		GeneratorImpl<T,V> g = new GeneratorImpl<>();
		g.executor = executor;
		g.body = body;
		return g;
	}

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
	private ExecutorService executor;
	private Function<Yielder<T>,V> body;
	private boolean started;

	private record _Exception_ (Throwable value) {};
	// Consumer-to-generator signals, delivered through the toGenerator exchanger in
	// place of a plain resume value - see throwInto()/returnWith() and yield().
	private record _ThrowSignal_ (Throwable value) {};
	private record _ReturnSignal_ (Object value) {};

	protected GeneratorImpl() {
	}

	@Override
	public V getReturnValue() {
		return returnValue;
	}

	private void ensureStarted() {
		if(!started) {
			started = true;
			executor.submit(() -> {
				// EXACTLY ONE terminal handoff on toConsumer, ever.
				//
				// finish() and exception() each perform a toConsumer.exchange(),
				// and the consumer's fetchNext() performs exactly one matching
				// exchange per resume. Calling exception() from catch and then
				// finish() from a finally therefore hands off TWICE on the
				// abnormal path: the consumer takes the _Exception_ and
				// rethrows it out of fetchNext(), so nobody is ever left to take
				// the finish() handoff and this thread parks on that exchange
				// forever. Because GeneratorScheduler's executor is a static
				// one that is never shut down, every generator whose body threw
				// leaked a permanently parked thread for the life of the JVM.
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
			});
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
					throwException(ts.value());
				}
			}
			ensureStarted();
			if (pendingResume) {
				toGenerator.exchange(resumeValue);
				pendingResume = false;
			}
			Object v = toConsumer.exchange(null);
			if (v == this) {
				hasValue = Boolean.FALSE;
				pendingResume = false;
				return;
			}
			if (v instanceof _Exception_ ex) {
				hasValue = Boolean.FALSE;
				throwException(ex.value);
			}
			hasValue = Boolean.TRUE;
			this.value = (T) v;
			pendingResume = true;
		} catch (InterruptedException e) {
			// The consumer gave up: the generator is done from its point of view.
			// The body thread is either parked in yield() (pendingResume) or still
			// running towards its next handoff - either way nobody would ever take
			// that handoff, so a background task plays the consumer until the body
			// has unwound (returning from every further yield), instead of leaking it.
			hasValue = Boolean.FALSE;
			boolean parkedInYield = pendingResume;
			pendingResume = false;
			value = null;
			if(started) {
				executor.submit(() -> abandon(parkedInYield));
			}
			Thread.currentThread().interrupt();
		} finally {
			executing.set(false);
		}
	}

	@Override
	public boolean hasNext() {
		if(hasValue==null) {
			fetchNext(null);
		}
		return hasValue;
	}

	@SuppressWarnings("unchecked")
	public <TH extends Throwable> void throwException(Throwable t) throws TH {
	    throw (TH) t; // compiler thinks it's throwing T, erased at runtime
	}

	@Override
	public T next() {
		if (hasNext()) {
			hasValue = null;
			T ret = value;
			value = null;
			return ret;
		}
		throw new NoSuchElementException();
	}

	// Delivers resumeValue into the paused yield() (its return value) and runs until
	// the next yield or completion. If a value was already buffered by a prior
	// hasNext() call (which delivers no resume value), that value is returned as-is.
	@Override
	public T next(Object resumeValue) {
		return consume(resumeValue);
	}

	@Override
	public T throwInto(Throwable t) {
		return consume(new _ThrowSignal_(t));
	}

	@Override
	public T returnWith(V value) {
		return consume(new _ReturnSignal_(value));
	}

	/**
	 * Completes the generator. A body parked in yield() is resumed with a return
	 * signal so it unwinds (running its finally blocks) and its thread terminates;
	 * without this an abandoned generator leaked a thread parked in yield() forever.
	 */
	@Override
	public void close() {
		if(started && pendingResume) {
			try {
				returnWith(null);
			} catch(NoSuchElementException ignored) {
				// Completed without yielding another value: the expected outcome
			} catch(RuntimeException ignored) {
				// The body's finally blocks may throw; the generator is done either way
			}
		}
		if(hasValue==null || hasValue) {
			hasValue = Boolean.FALSE;
			value = null;
		}
		started = true;
	}

	private T consume(Object resumeSignal) {
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

	// Drives the body to completion on behalf of a consumer that gave up (was interrupted)
	private void abandon(boolean parkedInYield) {
		try {
			if(parkedInYield) {
				toGenerator.exchange(new _ReturnSignal_(null));
			}
			while(true) {
				Object v = toConsumer.exchange(null);
				if(v == this || v instanceof _Exception_) {
					return;
				}
				// The body caught the return signal and yielded again
				toGenerator.exchange(new _ReturnSignal_(null));
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	// The body side of a handoff cannot be abandoned half way: returning early (e.g.
	// with a bogus null resume value) would desynchronize it from the consumer, which
	// then parks forever. Interrupts are therefore deferred until the handoff is done.
	private Object exchangeUninterruptibly(Exchanger<Object> exchanger, Object v) {
		boolean interrupted = false;
		try {
			while(true) {
				try {
					return exchanger.exchange(v);
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		} finally {
			if(interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public T yield(T v) {
		exchangeUninterruptibly(toConsumer, v);
		Object resumeSignal = exchangeUninterruptibly(toGenerator, null);
		if (resumeSignal instanceof _ThrowSignal_ ts) {
			throwException(ts.value());
		}
		if (resumeSignal instanceof _ReturnSignal_ rs) {
			throw new GeneratorReturnSignal(rs.value());
		}
		return (T) resumeSignal;
	}

	@Override
	public void exception(Throwable t) {
		exchangeUninterruptibly(toConsumer, new _Exception_(t));
	}

	@Override
	public void finish() {
		exchangeUninterruptibly(toConsumer, this);
	}
}
