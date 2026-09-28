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
package org.monflabs.galtajs.rt.transpiler;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;

// Transpiler-generated-code counterpart of the interpreter's
// ASTArrayLiteral.IteratorState: a mutable iterator record used while emitting
// array-pattern destructuring, so an elision consumes exactly one step, a
// trailing rest drains what's left, and IteratorClose (the iterator's
// "return") can be invoked once - either because the pattern doesn't exhaust
// the iterator (no rest element) or because evaluating an element threw
// partway through. Kept as a standalone copy rather than a shared base with
// IteratorState to avoid touching already-correct interpreted-mode code.
public final class IteratorRecord {

	private final JSEnvironment env;
	private final Object iterator;
	private boolean done;

	public static IteratorRecord begin(JSEnvironment env, Object value) {
		return new IteratorRecord(env, value);
	}

	private IteratorRecord(JSEnvironment env, Object value) {
		this.env = env;
		this.iterator = RuntimeUtil.getIterator(env, value);
	}

	// Returns RuntimeUtil.NOT_AVAILABLE once the iterator is exhausted.
	public Object step() {
		if(done) {
			return RuntimeUtil.NOT_AVAILABLE;
		}
		Object v;
		try {
			v = RuntimeUtil.iteratorStep(env, iterator);
		} catch(RuntimeException e) {
			// Per spec (IteratorStepValue): if the iterator's OWN next()
			// throws, the record is marked done BEFORE the abrupt completion
			// propagates - so close() (called from generated catch-block
			// code) must NOT then ALSO call return() on this same iterator,
			// since its next() just threw, not merely reported "done". Same
			// fix as the interpreter's ASTArrayLiteral.IteratorState.step().
			done = true;
			throw e;
		}
		if(v==RuntimeUtil.NOT_AVAILABLE) {
			done = true;
		}
		return v;
	}

	public void close() {
		if(!done) {
			done = true;
			RuntimeUtil.iteratorClose(env, iterator);
		}
	}
}
