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
package org.monflabs.galtajs.rt;

/**
 * Internal-only marker: what an `await` expression inside an ASYNC
 * GENERATOR's own body actually yields, through the SAME Exchanger-based
 * yield/resume protocol (GeneratorImpl.yield()/Yielder) a real `yield`
 * uses - never exposed to script. A plain async function's `await`
 * (RuntimeUtil.await_()) blocks the CALLING thread via the executor's own
 * LockSupport.park()-based wait, which is safe there because that thread
 * is a fresh worker spawned specifically for that call - but an async
 * generator's body already runs on GeneratorImpl's own dedicated worker
 * thread, paired with the caller via Exchanger.exchange(); blocking THAT
 * thread the same way deadlocks, since resolving the awaited promise
 * requires draining the microtask queue on some other thread, and the
 * only thread that would normally do that (whoever called .next()) is
 * itself parked waiting on the very same Exchanger.
 *
 * Instead, RuntimeUtil.awaitInGenerator_() hands this marker to the
 * generator's own Yielder (yielder.yield(new AwaitYieldSignal(value))),
 * which safely suspends the worker thread via the EXISTING, already-
 * correct Exchanger-based park (not LockSupport.park()) and returns
 * control to whoever called .next(). BuiltinAsyncGeneratorPrototype's own
 * driving loop recognizes this marker (rather than a real yielded value),
 * asynchronously settles it via BuiltinPromiseConstructor.resolve(...)
 * .then_(...), and resumes the generator - gen.next(resolvedValue) on
 * fulfillment, or gen.throwInto(reason) on rejection (which
 * GeneratorImpl.yield()'s own existing _ThrowSignal_ handling turns back
 * into a real thrown exception AT the await expression, for free) - all
 * driven by ordinary queued microtasks, never blocking any thread
 * synchronously.
 */
public record AwaitYieldSignal(Object value) {
}
