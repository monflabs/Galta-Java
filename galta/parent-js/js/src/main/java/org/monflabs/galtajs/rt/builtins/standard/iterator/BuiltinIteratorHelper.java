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
package org.monflabs.galtajs.rt.builtins.standard.iterator;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;

/**
 * Internal iterator helper class - the result of map()/filter()/take()/
 * drop()/flatMap(). Optionally tracks the immediate SOURCE iterator so its
 * own return() (and any abrupt completion mid-computation) can close it,
 * per the Iterator Helpers proposal's closing semantics.
 */
public class BuiltinIteratorHelper extends BuiltinIterator {

	private Iterator<?> closeSource;
	private boolean closed;
	// Reentrancy guard: whether THIS helper's own next()/return() is currently
	// mid-computation (e.g. running its mapper/predicate callback) - set/checked
	// by BuiltinIteratorHelperPrototype's dispatch, not GaltaJS's Generator/
	// GeneratorImpl layer, since a plain-Java-Iterator-backed helper (e.g.
	// filter()/map(), built from org.monflabs.util.iterators.Iterators, not a
	// real coroutine) has no Generator of its own for that layer to guard -
	// the callback reentrantly calling THIS SAME helper's next() would
	// otherwise just keep recursing (each call reaching a fresh, non-conflicting
	// state in whatever underlying source it eventually pulls from) until
	// Java's own call stack is exhausted, rather than throwing the spec-
	// mandated TypeError immediately on the first reentrant call.
	private boolean executing;
	// Whether next() has ever actually been resumed at least once (the
	// generator's state has moved past "suspendedStart"). Needed so return()
	// can tell apart its two spec-mandated paths: from suspendedStart (or
	// completed), GeneratorResumeAbrupt jumps straight to "completed" and runs
	// the close WITHOUT ever entering "executing" (a reentrant next()/return()
	// call during that close sees isClosed()==true and returns normally); from
	// suspendedYield, it goes through "executing" instead (a reentrant call
	// during that close must throw TypeError). See test262
	// Iterator/zip/suspended-start-iterator-close-calls-*.js (return() before
	// any next() call - must not throw) vs Iterator/concat/throws-typeerror-
	// when-generator-is-running-return.js (return() after next() - must throw).
	private boolean started;

	public BuiltinIteratorHelper(JSEnvironment env, Iterator<?> iterator) {
		this(env,iterator,iterator);
	}

	public BuiltinIteratorHelper(JSEnvironment env, Iterator<?> iterator, Iterator<?> closeSource) {
		super(env,iterator,"Iterator Helper");
		this.closeSource = closeSource;
	}

	// The iterator to forward return()/close to - the DIRECT source this
	// helper was built from (e.g. what map()/filter() was called on), not
	// necessarily the same as the derived iterator used for next().
	public Iterator<?> getCloseSource() {
		return closeSource;
	}

	public boolean isClosed() {
		return closed;
	}

	public void markClosed() {
		this.closed = true;
	}

	public boolean isExecuting() {
		return executing;
	}

	public void setExecuting(boolean executing) {
		this.executing = executing;
	}

	public boolean isStarted() {
		return started;
	}

	public void markStarted() {
		this.started = true;
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinIteratorHelperPrototype.get(getEnvironment());
	}
}
