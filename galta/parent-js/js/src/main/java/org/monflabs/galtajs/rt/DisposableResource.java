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

import org.monflabs.galtajs.rt.builtins.Callable;

/**
 * A single entry on a scope's DisposableResourceStack (explicit resource
 * management proposal - `using`/`await using`), as registered by
 * AddDisposableResource. `disposeMethod` is the RESOLVED [Symbol.dispose]/
 * [Symbol.asyncDispose] function captured once at declaration time (not
 * re-looked-up at disposal time - see test262's gets-initializer-Symbol.
 * dispose-property-once.js), or null for a no-op entry (the value was
 * null/undefined, per spec AddDisposableResource step 1a).
 */
public record DisposableResource(Object value, Callable disposeMethod, boolean isAsync) {
}
