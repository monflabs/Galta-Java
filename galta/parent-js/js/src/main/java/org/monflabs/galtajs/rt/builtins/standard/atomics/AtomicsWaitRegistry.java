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
package org.monflabs.galtajs.rt.builtins.standard.atomics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Real cross-thread Atomics.wait()/notify() suspend/wake, keyed by the
 * SharedArrayBuffer object identity + absolute byte offset -
 * NOT gated behind test262 specifically; this is genuine engine
 * infrastructure for whenever GaltaJS runs multiple real Java threads
 * against the same SharedArrayBuffer (currently only test262's
 * `$262.agent.start()` does this - see the AgentCanSuspend()-style gating
 * in Atomics.java - but nothing here is test-only).
 */
public final class AtomicsWaitRegistry {

	private static final AtomicsWaitRegistry INSTANCE = new AtomicsWaitRegistry();
	public static AtomicsWaitRegistry get() {
		return INSTANCE;
	}
	private AtomicsWaitRegistry() {}

	// buffer identity -> byte offset -> FIFO list of currently-blocked waiters.
	private final Map<Object, Map<Long, List<Waiter>>> waiters = new IdentityHashMap<>();

	// Package-visible (not private) so Atomics.waitAsync can pre-register a
	// waiter on the CALLING thread (see registerForAsyncWait) and then hand
	// it off to a background thread to actually block on (see
	// blockUntilWokenOrTimeout) - waitAsync must never block the calling
	// thread itself.
	static final class Waiter {
		final Object lock = new Object();
		boolean woken;
	}

	public enum WaitResult { OK, NOT_EQUAL, TIMED_OUT }

	/**
	 * Spec's DoWait: registers the calling (real Java) thread as a waiter at
	 * (buffer,byteOffset), THEN evaluates {@code stillExpectedValue}, and
	 * ONLY blocks if it's still true - both steps happen under this
	 * registry's own lock, the SAME lock notify() needs to find/wake
	 * waiters, so a notify() (or the underlying value changing) can never
	 * race into the gap between "check the value" and "start blocking" -
	 * whichever happens first is what's actually observed:
	 * - value already changed before registration completes: the
	 *   registration itself doesn't matter, `stillExpectedValue` sees the
	 *   new value and returns false immediately, no blocking, NOT_EQUAL.
	 * - value changes / notify() arrives after registration: this thread is
	 *   already in the waiter list by then, so it's found and woken - can't
	 *   be missed.
	 * timeoutMs may be {@link Double#POSITIVE_INFINITY} for "no timeout".
	 */
	public WaitResult await(Object buffer, long byteOffset, BooleanSupplier stillExpectedValue, double timeoutMs) {
		Waiter w = new Waiter();
		synchronized(this) {
			if(!stillExpectedValue.getAsBoolean()) {
				return WaitResult.NOT_EQUAL;
			}
			waiters.computeIfAbsent(buffer, k -> new HashMap<>())
					.computeIfAbsent(byteOffset, k -> new ArrayList<>())
					.add(w);
		}
		return blockUntilWokenOrTimeout(w, buffer, byteOffset, timeoutMs);
	}

	/**
	 * waitAsync's own version of {@link #await}'s registration step (spec's
	 * DoWait up through AddWaiter, mode=async): checks
	 * {@code stillExpectedValue} and, unlike {@link #await}, ALSO
	 * short-circuits a zero-length timeout - both under this registry's own
	 * lock, so nothing can race into the gap - and either returns a
	 * registered {@link Waiter} for the caller to hand off to a background
	 * thread (see {@link #blockUntilWokenOrTimeout}), or {@code null} with
	 * {@code shortCircuitOut[0]} set to the synchronous result the CALLING
	 * thread should return immediately (no Promise involved at all - per
	 * spec/test262, "not-equal" and a zero-timeout "timed-out" are both
	 * observed synchronously, never via the returned Promise).
	 */
	public Waiter registerForAsyncWait(Object buffer, long byteOffset, BooleanSupplier stillExpectedValue, double timeoutMs, WaitResult[] shortCircuitOut) {
		synchronized(this) {
			if(!stillExpectedValue.getAsBoolean()) {
				shortCircuitOut[0] = WaitResult.NOT_EQUAL;
				return null;
			}
			if(timeoutMs==0) {
				shortCircuitOut[0] = WaitResult.TIMED_OUT;
				return null;
			}
			Waiter w = new Waiter();
			waiters.computeIfAbsent(buffer, k -> new HashMap<>())
					.computeIfAbsent(byteOffset, k -> new ArrayList<>())
					.add(w);
			return w;
		}
	}

	// Shared blocking+cleanup tail for an ALREADY-registered waiter - used
	// directly by await() (blocks the calling thread itself) and, via
	// Atomics.waitAsync's asyncFunction body, on a background thread instead
	// (see registerForAsyncWait's doc comment).
	public WaitResult blockUntilWokenOrTimeout(Waiter w, Object buffer, long byteOffset, double timeoutMs) {
		try {
			long start = System.nanoTime();
			double timeoutNanos = timeoutMs*1_000_000.0;
			// A huge (but finite) timeout would overflow nanoTime()+timeout into the past
			long deadline = Double.isInfinite(timeoutMs) || timeoutNanos >= (double)(Long.MAX_VALUE - start) ? Long.MAX_VALUE : start + (long)timeoutNanos;
			synchronized(w.lock) {
				while(!w.woken) {
					long remainingMs;
					if(deadline==Long.MAX_VALUE) {
						remainingMs = 0; // wait(0) == wait indefinitely
					} else {
						long remainingNanos = deadline - System.nanoTime();
						if(remainingNanos<=0) {
							break; // timed out
						}
						remainingMs = Math.max(1, remainingNanos/1_000_000L);
					}
					try {
						w.lock.wait(remainingMs);
					} catch(InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				return w.woken ? WaitResult.OK : WaitResult.TIMED_OUT;
			}
		} finally {
			synchronized(this) {
				Map<Long, List<Waiter>> byOffset = waiters.get(buffer);
				if(byOffset!=null) {
					List<Waiter> list = byOffset.get(byteOffset);
					if(list!=null) {
						list.remove(w);
						if(list.isEmpty()) {
							byOffset.remove(byteOffset);
						}
						if(byOffset.isEmpty()) {
							waiters.remove(buffer);
						}
					}
				}
			}
		}
	}

	// Wakes up to `count` waiters blocked at (buffer,byteOffset), FIFO order
	// (registration order) - returns how many were actually woken.
	public long notify(Object buffer, long byteOffset, long count) {
		List<Waiter> woken;
		synchronized(this) {
			Map<Long, List<Waiter>> byOffset = waiters.get(buffer);
			List<Waiter> list = byOffset!=null ? byOffset.get(byteOffset) : null;
			if(list==null || list.isEmpty() || count<=0) {
				return 0;
			}
			int n = (int)Math.min(count, list.size());
			woken = new ArrayList<>(list.subList(0, n));
			// Remove them from the registry HERE (not left to await()'s own
			// finally block) - otherwise a second notify() racing before a
			// just-woken waiter's thread gets scheduled could select the
			// SAME waiter again and over-report how many were woken.
			list.subList(0, n).clear();
			if(list.isEmpty()) {
				byOffset.remove(byteOffset);
				if(byOffset.isEmpty()) {
					waiters.remove(buffer);
				}
			}
		}
		for(Waiter w: woken) {
			synchronized(w.lock) {
				w.woken = true;
				w.lock.notifyAll();
			}
		}
		return woken.size();
	}
}
