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

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.Yielder;

public interface JSExecutor {
	
	public void queueMicrotask(MicroTask task);

	/**
	 * Enqueue a microtask that will not run before {@code atTimeMs} (an absolute
	 * time in milliseconds, comparable to {@link System#currentTimeMillis()}).
	 * If the time has already passed, the task is queued immediately.
	 */
	public void queueMicrotask(MicroTask task, long atTimeMs);

	public void queueMacrotask(MacroTask task);
	
	public void performMicrotaskCheckpoint();
	
    public default Object execute(@NonNull Supplier<Object> code) {
    	return execute(code,false);
    }
    public Object execute(@NonNull Supplier<Object> code, boolean async);

	/**
	 * Like {@link #execute(Supplier, boolean)}, but also calls
	 * {@code onGenuineCompletion} exactly once with the code's OWN true
	 * final outcome (value, null) on success or (null, throwable) on
	 * failure - which for a NESTED async call (already inside another
	 * {@code execute()}/{@code runAsyncBody()} call's own drain, so this
	 * one correctly does NOT re-enter {@code drainPendingTasks()} itself -
	 * see {@code JSAsyncExecutor}'s own doc) may fire LATER than this
	 * method's own return, from inside a later microtask, if {@code code}
	 * has its own top-level await still in flight. This method's own
	 * return value in that case is a placeholder, NOT the genuine result -
	 * only {@code onGenuineCompletion} is reliable for a caller (module
	 * evaluation-completion tracking, for one) that needs to know when
	 * {@code code} has ACTUALLY finished, not just reached its first
	 * suspension. The default implementation (correct for any executor
	 * where {@link #execute(Supplier, boolean)} always already reaches
	 * genuine completion before returning) just calls that overload and
	 * invokes the callback synchronously with its outcome.
	 */
	public default Object execute(@NonNull Supplier<Object> code, boolean async, @NonNull BiConsumer<Object,Throwable> onGenuineCompletion) {
		try {
			Object v = execute(code, async);
			onGenuineCompletion.accept(v, null);
			return v;
		} catch(Throwable t) {
			onGenuineCompletion.accept(null, t);
			throw t;
		}
	}

    public AsyncTask getCurrentAsyncTask();
    
	public Generator<Object,Object> generator(Function<Yielder<Object>,Object> body);
	
	public BuiltinPromise asyncFunction(Callable<Object> body);

	/**
	 * Runs a genuine JS {@code async function} body (or module top-level
	 * await code): {@code body} runs SYNCHRONOUSLY, on the calling thread,
	 * up to its first {@link #await(Object)} suspension (or completion) -
	 * matching spec's single-threaded, cooperative AsyncFunctionStart
	 * semantics - then returns a pending {@link BuiltinPromise}, resumed via
	 * ordinary microtasks (never blocking any thread) as each awaited value
	 * settles. Unlike {@link #asyncFunction(Callable)}, which submits the
	 * WHOLE body to a worker thread and returns immediately without waiting
	 * for anything - correct for wrapping an arbitrary blocking Java
	 * operation as a fire-and-forget background task, but NOT spec-correct
	 * for real JS async-function-body execution. Only {@code body}'s own,
	 * direct {@link #await(Object)} calls get the synchronous-prefix
	 * treatment - a nested call that goes through {@link #asyncFunction(Callable)}
	 * instead (e.g. a Java library wrapping blocking I/O) is unaffected.
	 */
	public BuiltinPromise runAsyncBody(Callable<Object> body);

	public Object await(Object value);

	/**
	 * Runs {@code body} with drain-suppression claimed for its duration -
	 * the SAME "claim draining early, restore on exit" mechanism
	 * {@code startCoroutine()} uses internally for a coroutine's own
	 * synchronous first step (see {@code JSAsyncExecutor}'s own doc on
	 * that method and on the {@code draining} field), exposed here for a
	 * caller that synchronously loads/starts OTHER async work of its own
	 * OUTSIDE any coroutine's synchronous prefix - namely
	 * {@code JSInterpretedUnit.linkModule()}'s pre-body dependency walk: a
	 * module's static {@code import} may synchronously trigger a
	 * DEPENDENCY module's own async execution, whose
	 * {@code execute(...,true)} call must see draining already claimed
	 * (so it returns after its own synchronous prefix instead of draining
	 * the whole microtask queue to completion right there) even though
	 * {@code linkModule()} itself runs BEFORE the importing module's own
	 * {@code runBody()}/{@code startCoroutine()} call even begins. The
	 * default implementation (correct for any executor where
	 * {@link #execute(Supplier, boolean)} always already reaches genuine
	 * completion before returning, i.e. has no such suppression concept)
	 * just runs {@code body} directly.
	 */
	public default <T> T runWithDrainSuppressed(Supplier<T> body) {
		return body.get();
	}

	/**
	 * Drains every pending microtask/macrotask/timer to completion, exactly
	 * like the tail of {@link #execute(Supplier, boolean)}'s own
	 * non-nested case (drain, then release the executor's own resources) -
	 * exposed here for a caller that reaches a "nothing left to run right
	 * now" point WITHOUT itself going through {@code execute()}, namely
	 * {@code JSInterpretedUnit.executeWithContext()} when a module's own
	 * {@code linkModule()} defers its body (still-pending async
	 * dependency): nothing else would otherwise ever trigger a drain for
	 * that module's own dependency graph, since {@code runBody()} - the
	 * ONLY other call site that reaches {@code execute()} - is deliberately
	 * not called until later. Like {@code execute()}'s own drain, this
	 * correctly no-ops for a NESTED caller (one already running inside an
	 * outer drain, or inside a {@link #runWithDrainSuppressed(Supplier)}
	 * scope) - only the true outermost caller actually drains. The default
	 * implementation (correct for any executor with no such deferred/
	 * suppressed-drain concept) is a no-op.
	 */
	public default void drainAndShutdownIfOutermost() {
	}

	/**
	 * Drives the pending microtask/macrotask/timer queue - the SAME
	 * underlying loop {@link #drainAndShutdownIfOutermost()}/{@code execute()}
	 * use - but stops the INSTANT {@code condition} becomes true, rather
	 * than only once the whole queue is empty. Exposed here for
	 * {@code JSInterpretedUnit.linkModule()}'s SCC-closing step: a
	 * non-root member of a just-closed module cycle must run to FULL
	 * completion before the cycle's ROOT starts its own body (confirmed
	 * against real engine behavior, not derivable from spec text alone -
	 * see {@code JSInterpretedUnit}'s own call site doc and
	 * {@code KnownGaps.md}), which needs waiting for THAT ONE module to
	 * settle specifically, not draining everything. Also stops early if
	 * the queue itself empties out before {@code condition} is met (a
	 * safety valve - never a genuine infinite loop). The default
	 * implementation (correct for any executor with no such queue at
	 * all) is a no-op.
	 */
	public default void drainUntil(java.util.function.BooleanSupplier condition) {
	}
}
