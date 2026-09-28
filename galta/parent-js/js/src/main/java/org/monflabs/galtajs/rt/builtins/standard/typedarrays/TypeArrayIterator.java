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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayIteratorPrototype;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIterator;

/**
 * Spec 23.2.5.1-.3 (%TypedArray%.prototype.entries/keys/values): all three
 * are defined as "Return CreateArrayIterator(O, ...)" - the SAME abstract
 * operation Array.prototype.entries/keys/values use - so a typed array's
 * iterator shares the real %ArrayIteratorPrototype%, with class/toStringTag
 * "Array Iterator" too, not a separate "TypedArray Iterator" of its own.
 */
public class TypeArrayIterator extends BuiltinIterator{

	private final TypedArray source;
	// Spec's ArrayIteratorPrototype.next(): once the iterator has reached
	// its natural end (index >= len, with NEITHER the buffer detached NOR
	// the view out of bounds - that case always forces a throw instead,
	// see below), it must stay permanently exhausted - a LATER resize that
	// makes the source valid/non-empty again must not un-exhaust it
	// (confirmed via values/make-in-bounds-after-exhausted.js). Likewise,
	// once naturally exhausted, a LATER resize that makes the source
	// out-of-bounds must not start throwing either - exhaustion takes
	// permanent precedence over the out-of-bounds check below (confirmed
	// via values/make-out-of-bounds-after-exhausted.js).
	private boolean done;

	public TypeArrayIterator(JSEnvironment env, TypedArray source, Iterator<?> iterator) {
		super(env, iterator, "Array Iterator");
		this.source = source;
	}

	// %ArrayIteratorPrototype%.next()'s own algorithm has a TypedArray-specific
	// step (spec 23.1.5.1 step 8): if the iterated object is backed by a
	// TypedArray whose buffer has since been detached, throw - regardless of
	// whether this particular iteration kind (keys/values/entries) would
	// otherwise need to touch the buffer at all (confirmed via
	// detach-typedarray-in-progress.js: a plain keys() iterator, which never
	// reads element values, must still throw on a mid-iteration detach).
	public TypedArray getSource() {
		return source;
	}

	// Exposed so BuiltinIteratorHelperPrototype's own separate, duplicate
	// out-of-bounds check (its JS-level "next" dispatch, ahead of ever
	// calling into this class's next() below) can also respect the same
	// permanent-exhaustion latch, instead of throwing on a source that
	// only went out-of-bounds AFTER this iterator was already naturally
	// exhausted (confirmed via values/make-out-of-bounds-after-
	// exhausted.js).
	public boolean isDone() {
		return done;
	}

	// Both hasNext() and next() are overridden here (not just checked in
	// BuiltinIteratorHelperPrototype's JS-level "next" dispatch) because
	// for-of/spread drive iteration via a Java-level fast path
	// (RuntimeUtil.valueIteratorUnchecked) that calls this Iterator's own
	// hasNext()/next() DIRECTLY, bypassing the JS "next" property entirely
	// when it hasn't been monkey-patched - so the out-of-bounds check must
	// live here too, or that fast path silently misses it.
	//
	// hasNext() must ALSO report true (forcing a next() call) once the
	// source is out of bounds, even though the wrapped Java sub-iterator
	// (e.g. an index range computed from the typed array's length AT
	// ITERATOR-CREATION time) may already correctly report hasNext()=false
	// in that situation, since the length used to build that range was
	// itself already 0 (out-of-bounds) - a caller that trusts hasNext()
	// before ever calling next() (both the JS dispatch above and the Java
	// fast path) would otherwise silently stop instead of ever reaching
	// next()'s throw (confirmed via entries|keys|values/resizable-
	// buffer*.js, whose already-out-of-bounds fixed-length view must still
	// throw on the FIRST next() call of a freshly-created iterator, per
	// spec's per-call - not per-iterator-creation - bounds check).
	@Override
	public boolean hasNext() {
		if(done) {
			return false;
		}
		if(source.isOutOfBounds()) {
			return true;
		}
		boolean has = super.hasNext();
		if(!has) {
			done = true;
		}
		return has;
	}

	@Override
	public Object next() {
		if(done) {
			throw new java.util.NoSuchElementException();
		}
		// isOutOfBounds() already covers detached (see its own javadoc) -
		// also covers a resizable-buffer view that's gone out of bounds
		// due to a shrink since this iterator was created (confirmed via
		// entries|keys|values/resizable-buffer*.js: a plain keys()
		// iterator over a now-out-of-bounds fixed-length view must throw
		// on next(), not just on an actually-detached buffer).
		if(source.isOutOfBounds()) {
			throw RuntimeUtil.typeError("TypedArray buffer is detached");
		}
		return super.next();
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinArrayIteratorPrototype.get(getEnvironment());
	}
}
