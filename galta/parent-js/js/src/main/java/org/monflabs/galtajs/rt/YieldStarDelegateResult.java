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
 * Marks a value yielded through a generator's Yielder as coming from a `yield*`
 * delegation step (as opposed to a plain `yield value`). Per spec, a non-final
 * `yield*` step forwards the delegate's raw iterator result object straight through
 * to the outer generator's next()/throw()/return() caller, unwrapped and with
 * whatever shape it has (which may lack a "done" property, extra properties, etc.) -
 * unlike a plain yield, whose value is always freshly wrapped as {value, done:false}.
 */
public record YieldStarDelegateResult(Object rawResult) {
}
