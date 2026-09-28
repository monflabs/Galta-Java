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

/**
 * Thrown when a generator's next()/throwInto()/returnWith() (or hasNext(), which also
 * drives the underlying coroutine forward) is invoked while that SAME generator is
 * already mid-execution - e.g. the generator body (or a callback it invokes, like
 * Iterator Helper's predicate/mapper) reentrantly calls back into the generator it's
 * currently running inside of. GeneratorImpl's coroutine is implemented via a blocking
 * java.util.concurrent.Exchanger handoff between the calling thread and the generator's
 * own background thread - a reentrant call from the generator's own thread while the
 * calling thread is already blocked waiting on that same handoff would otherwise
 * deadlock forever (Exchanger.exchange() needs a THIRD party to pair with, and there
 * isn't one), rather than surfacing as a catchable error. Corresponds to spec's
 * GeneratorValidate step "If state is executing, throw a TypeError exception" -
 * JS-facing callers should catch this and translate it to a real TypeError.
 */
@SuppressWarnings("serial")
public class GeneratorExecutingException extends RuntimeException {

	public GeneratorExecutingException() {
		super(null, null, false, false);
	}
}
