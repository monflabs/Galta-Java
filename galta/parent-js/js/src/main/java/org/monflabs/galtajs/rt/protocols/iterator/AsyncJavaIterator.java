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
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;

/**
 * Java Iterator on top of a genuine JavaScript ASYNC iterator (one actually
 * obtained via [Symbol.asyncIterator]()), for `for await (... of ...)`.
 * Mirrors JavaIterator's own hasNext()/next() caching shape exactly, except
 * each "next" call's result is itself Await-ed (13.7.5.13
 * ForIn/OfBodyEvaluation's per-iteration "Let nextResult be ? Await(...)")
 * before its done/value are read - using the SAME blocking-await machinery a
 * hand-written `await` expression already uses (RuntimeUtil.await_ /
 * awaitInGenerator_, chosen exactly like ASTAwait chooses between them, see
 * its own doc), since this runs inside the interpreter/transpiled loop's own
 * execution context - not the Promise-continuation style Array.fromAsync
 * uses for its detached native callback code.
 */
public class AsyncJavaIterator implements Iterator<Object> {

	private final JSRuntimeContext context;
	private final JSEnvironment env;
	private final boolean awaitInGenerator;
	private final Object o;
	private final Object next;

	private boolean shouldReadNext;
	private boolean done;
	private Object value;

	// Exposes the wrapped JS-level async iterator object so callers (e.g.
	// for-await-of's AsyncIteratorClose) can call its own "return" method
	// directly, same as JavaIterator.getIteratorObject().
	public Object getIteratorObject() {
		return o;
	}

	public AsyncJavaIterator(JSRuntimeContext context, Object o, boolean awaitInGenerator) {
		this.context = context;
		this.env = context.getEnvironment();
		this.awaitInGenerator = awaitInGenerator;
		this.o = o;
		this.next = RuntimeUtil.getProperty(env,o,"next",RuntimeUtil.UNDEFINED);
		this.shouldReadNext = true;
	}

	private Object await(Object v) {
		return awaitInGenerator ? RuntimeUtil.awaitInGenerator_(context,v) : RuntimeUtil.await_(context,v);
	}

	private void readNext() {
		if(!(next instanceof Callable c)) {
			throw RuntimeUtil.typeError("next is not a function");
		}
		Object rawResult = c.call(o,RuntimeUtil.EMPTY_PARAMS);
		Object res = await(rawResult);
		if(RuntimeUtil.isPrimitiveType(res)) {
			throw RuntimeUtil.typeError("Iterator result {0} is not an object", res);
		}
		// See JavaIterator.readNext()'s matching comment - RuntimeUtil.getProperty(...)
		// already has the plain-JSObject fast path.
		this.done = RuntimeUtil.toBoolean(env, RuntimeUtil.getProperty(env,res,"done",Boolean.FALSE));
		// IteratorValue must not be read at all once IteratorStep is done - a
		// lazy "value" getter must not be invoked (mirrors JavaIterator).
		this.value = this.done ? RuntimeUtil.UNDEFINED : RuntimeUtil.getProperty(env,res,"value",RuntimeUtil.UNDEFINED);
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
		if(!done) {
			this.shouldReadNext = true;
			return value;
		}
		throw new NoSuchElementException();
	}
}
