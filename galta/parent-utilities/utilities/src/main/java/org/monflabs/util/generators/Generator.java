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

import java.util.Iterator;

public interface Generator<T,V> extends Iterator<T>, AutoCloseable  {

	public void exception(Throwable t);
	public void finish();

	public V getReturnValue();

	// Resumes a paused "yield" with resumeValue (what it evaluates to) and runs until
	// the next yield or completion, returning that next yielded value. Unlike next(),
	// this delivers a value into the generator body - needed for JS's
	// Generator.prototype.next(value) semantics. The very first call's resumeValue is
	// discarded (there is no paused yield yet to receive it), matching the spec.
	public T next(Object resumeValue);

	// Like next(), but raises `t` at the paused yield() point instead of delivering a
	// resume value - the generator body sees it as though `t` had been thrown right
	// there (catchable by an enclosing try/catch in the body). Needed for JS's
	// Generator.prototype.throw(exception). If the body doesn't catch it, it
	// propagates back out of this call.
	public T throwInto(Throwable t);

	// Like next(), but raises a GeneratorReturnSignal(value) at the paused yield()
	// point - the generator body cannot catch it (it isn't a normal exception to JS
	// try/catch), but enclosing finally blocks still run, and the generator completes
	// with `value` as its return value. Needed for JS's Generator.prototype.return(value).
	public T returnWith(V value);

	// Completes the generator early, releasing a body suspended at a yield() point
	// (its finally blocks run). Safe to call on a generator that never started or
	// that has already completed.
	@Override
	public default void close() {
	}
}

