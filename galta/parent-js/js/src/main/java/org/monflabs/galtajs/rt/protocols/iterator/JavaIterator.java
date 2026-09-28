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
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;

/**
 * Java Iterator on top of a JavaScript iterator
 */
public class JavaIterator implements Iterator<Object> {

	private JSEnvironment env;
	// Deliberately a generic Object, not JSObject - a Proxy (BuiltinProxy)
	// implementing the iterator protocol is a perfectly valid receiver here
	// too (confirmed via a plain `for-of` over `new Proxy({[Symbol.iterator](){
	// return this; }, next(){...}}, {})`), and RuntimeUtil.getProperty(...)/
	// Callable.call already dispatch correctly for ANY JS-level value (its
	// own env.getAccessor(...) fallback), not just JSObject instances
	// specifically - only a plain JSObject (the overwhelmingly common case)
	// gets the faster direct dispatch.
	private Object o;
	// Deliberately Object, not Callable - GetIteratorDirect (spec) reads
	// "next" eagerly (a plain Get, same timing as before) but does NOT
	// validate it's callable at that point; the Iterator Record just stores
	// whatever "next" is, even undefined/non-callable, and only actually
	// fails once a real IteratorStep/IteratorNext tries to CALL it (see
	// readNext() below). Confirmed via built-ins/Iterator/zip/iterables-
	// iteration.js, which constructs (but never consumes) a wrapped iterator
	// over an object with no "next" at all and expects no error.
	private Object next;

	// Javascript supports the collection to be content to be updated while being traversed
	// We try to do that here.
	private boolean shouldReadNext;

	private boolean done;
	private Object value;

	// Exposes the wrapped JS-level iterator object so callers (e.g. for-of's
	// IteratorClose) can call its own "return" method directly, since this
	// class itself is a plain Java Iterator with no JS-visible "return" of
	// its own.
	public Object getIteratorObject() {
		return o;
	}

	public JavaIterator(JSEnvironment env, Object o) {
		this.env = env;
		this.o = o;
		// Get(o,"next"): a normal property read, walking the prototype
		// chain - "next" is very commonly a class/prototype method (e.g.
		// `class Foo extends Iterator { next() {...} }`), not necessarily
		// an own property of the instance itself.
		this.next = RuntimeUtil.getProperty(env,o,"next",RuntimeUtil.UNDEFINED);
		shouldReadNext = true;
	}

	private void readNext() {
		if(!(next instanceof Callable c)) {
			throw RuntimeUtil.typeError("next is not a function");
		}
		Object res = c.call(o,RuntimeUtil.EMPTY_PARAMS);
		if(RuntimeUtil.isPrimitiveType(res)) {
			throw RuntimeUtil.typeError("next() should return an object");
		}
		// RuntimeUtil.getProperty(...) already has the plain-JSObject fast
		// path (skips the env.getAccessor(...) dispatch entirely) - an
		// iterator result is overwhelmingly a plain object/generator
		// result, so going through env.getAccessor(res) unconditionally
		// here, like any other non-JSObject value would need to, wasted it.
		this.done = RuntimeUtil.toBoolean(env,RuntimeUtil.getProperty(env,res,"done",Boolean.FALSE));
		// IteratorValue must not be read at all once IteratorStep is done - a
		// lazy "value" getter must not be invoked (see spec 7.4.x IteratorStep).
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
