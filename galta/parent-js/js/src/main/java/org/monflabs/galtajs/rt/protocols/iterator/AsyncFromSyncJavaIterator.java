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
package org.monflabs.galtajs.rt.protocols.iterator;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromiseConstructor;

/**
 * %AsyncFromSyncIteratorPrototype% (25.1.4.4 AsyncFromSyncIteratorContinuation),
 * inlined here as a Java Iterator adapter rather than a separate JS-visible
 * builtin object - used for `for await (... of syncIterable)` when the
 * source has no [Symbol.asyncIterator] of its own. Wraps a plain SYNC
 * {@code Iterator<Object>} already obtained via the ordinary valueIterator()
 * GetIterator path (reusing that for the "is this even an iterable"
 * validation, exactly like plain for-of).
 *
 * Mirrors AsyncJavaIterator's own hasNext()/next() caching shape: real spec
 * runs AsyncFromSyncIteratorContinuation on EVERY call, including the one
 * that discovers `done: true` (IteratorValue defaults to undefined, but the
 * wrap-and-Await cost is still paid) - so the async work has to happen in
 * readNext()/hasNext(), not only in next() as an earlier version of this
 * class did (that undercounted ticks for the terminal check, test262
 * built-ins/AsyncFromSyncIteratorPrototype/next/tick-ordering*.js family).
 *
 * Note: this is a behavioral approximation of the real
 * %AsyncFromSyncIteratorPrototype% object - it is never itself exposed as a
 * JS-visible value (e.g. as the result of manually calling
 * `obj[Symbol.asyncIterator]`, which %AsyncFromSyncIteratorPrototype% would
 * need to be for that direct case), only used internally to drive a
 * `for await` loop's own iteration. Good enough for for-await-of's own
 * observable behavior; not a substitute for a genuine
 * built-ins/AsyncFromSyncIteratorPrototype/* implementation.
 */
public class AsyncFromSyncJavaIterator implements Iterator<Object> {

	private final JSRuntimeContext context;
	private final JSEnvironment env;
	private final boolean awaitInGenerator;
	private final Iterator<Object> inner;

	private boolean shouldReadNext;
	private boolean done;
	private Object value;

	// Exposes the wrapped SYNC iterator so callers (e.g. for-await-of's
	// AsyncIteratorClose) can close it via the existing sync-iterator close
	// dispatch (RuntimeUtil.iteratorClose(Iterator)), per spec 25.1.4.4's own
	// note that closing an async-from-sync wrapper closes the underlying
	// sync iterator.
	public Iterator<Object> getInner() {
		return inner;
	}

	public AsyncFromSyncJavaIterator(JSRuntimeContext context, Iterator<Object> inner, boolean awaitInGenerator) {
		this.context = context;
		this.env = context.getEnvironment();
		this.awaitInGenerator = awaitInGenerator;
		this.inner = inner;
		this.shouldReadNext = true;
	}

	private Object await(Object v) {
		return awaitInGenerator ? RuntimeUtil.awaitInGenerator_(context,v) : RuntimeUtil.await_(context,v);
	}

	private void readNext() {
		if(inner.hasNext()) {
			this.value = inner.next();
			this.done = false;
		} else {
			this.value = RuntimeUtil.UNDEFINED;
			this.done = true;
		}
		// AsyncFromSyncIteratorContinuation step 5: `valueWrapper` is
		// PromiseResolve(%Promise%, value) - if THIS ITSELF throws
		// synchronously (e.g. a poisoned "constructor" getter - PromiseResolve
		// step 2a reads it), step 6's IfAbruptRejectPromise means there never
		// was a separate valueWrapper at all: promiseCapability is rejected
		// DIRECTLY, so `for await`'s own later Await(nextResult) has only
		// ONE promise to wait on - a single real tick. Only when
		// PromiseResolve itself SUCCEEDS does the real two-promise structure
		// exist (valueWrapper, settled by a reaction that resolves a
		// SEPARATE promiseCapability which Await(nextResult) then waits on
		// again) - two genuinely separate reaction hops/ticks, regardless of
		// whether valueWrapper itself ultimately fulfills or rejects.
		Object wrapper;
		boolean abrupt;
		try {
			wrapper = BuiltinPromiseConstructor.resolve(env, value);
			abrupt = false;
		} catch(Throwable t) {
			RuntimeUtil.rethrowIfUncatchable(t);
			wrapper = BuiltinPromiseConstructor.reject(env, JSRuntimeException.exceptionObject(t));
			abrupt = true;
		}
		try {
			if(abrupt) {
				this.value = await(wrapper);
			} else {
				// Two sequential await() calls (tick 1, then tick 2) to get
				// the real 2-reaction-hop behavior, instead of hand-rolling
				// a second BuiltinPromise chained via performPromiseThen_()
				// called directly from this coroutine's own body thread. A
				// prior attempt did the latter and got tick counts/ordering
				// exactly right too, but caused
				// async-from-sync-iterator-continuation-abrupt-completion-get-constructor.js
				// to silently stop rejecting its own async function's
				// promise - performPromiseThen_()/newPromiseCapability() had,
				// until then, only ever been called from JSAsyncExecutor's
				// driveCoroutine() (the external driver thread), never from
				// inside a running coroutine itself; that untested call site
				// is the suspected cause. Calling the already-proven
				// await()/await_() helper twice goes through the same
				// driveCoroutine()-only reaction-registration path both times.
				//
				// The second await's argument matters: await() itself (per
				// its own PromiseResolve-then-Await shape, mirroring the
				// real 6.2.3.1 Await AO) ALWAYS calls
				// BuiltinPromiseConstructor.resolve() on whatever it's given -
				// and that unconditionally reads `.constructor` on ANY
				// BuiltinPromise instance (not just ones with an own-property
				// override - test262's own tick-ordering file poisons
				// Promise.prototype's shared getter, so even a fresh,
				// unrelated BuiltinPromise trips it). So the fulfilled case
				// passes the RAW resolved value (a non-Promise primitive
				// here, already unwrapped by the first await) rather than a
				// hand-wrapped promise - resolve()'s `instanceof
				// BuiltinPromise` check is then false, no extra read, while
				// still producing a genuine freshly-fulfilled internal
				// promise that costs its own real queued-microtask tick
				// (same mechanism an ordinary `await primitiveValue` already
				// relies on elsewhere in the engine).
				Object stage1;
				Throwable rejected = null;
				try {
					stage1 = await(wrapper);
				} catch(Throwable t) {
					RuntimeUtil.rethrowIfUncatchable(t);
					stage1 = null;
					rejected = t;
				}
				this.value = rejected == null
					? await(stage1)
					: await(BuiltinPromiseConstructor.reject(env, JSRuntimeException.exceptionObject(rejected)));
			}
		} catch(Throwable t) {
			RuntimeUtil.rethrowIfUncatchable(t);
			// 7.4.8 IteratorClose: only meaningful if the underlying sync
			// iterator hasn't already naturally completed. Quietly: a
			// `return()` failure here must never replace the ORIGINAL
			// rejection `t` (test262 built-ins/AsyncFromSyncIteratorPrototype/
			// next/iterator-result-poisoned-wrapper.js and its sibling
			// *-rejected-promise-close.js files).
			if(!done) {
				RuntimeUtil.iteratorCloseQuietly(env, inner);
			}
			throw t;
		}
		this.shouldReadNext = false;
	}

	@Override
	public boolean hasNext() {
		if(shouldReadNext) {
			readNext();
		}
		return !done;
	}

	@Override
	public Object next() {
		if(shouldReadNext) {
			readNext();
		}
		this.shouldReadNext = true;
		return value;
	}
}
