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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;

/**
 * Implements the ES2025 "joint iteration"/"iterator sequencing" proposals:
 * Iterator.zip, Iterator.zipKeyed, Iterator.concat.
 */
class IteratorZip {

	private IteratorZip() {}

	// GetIteratorFlattenable(element, reject-primitives) + wrap the result as
	// a plain Java Iterator<Object> - mirrors ConcatIterator.wrapIteratorResult
	// below exactly. Unlike RuntimeUtil.valueIterator (plain GetIterator,
	// which REQUIRES a Symbol.iterator method to exist), GetIteratorFlattenable
	// falls back to treating an object with no Symbol.iterator method as
	// ALREADY being the iterator itself (confirmed via zip/iterables-
	// iteration.js: a bare {next(){...}} object with no Symbol.iterator must
	// be accepted, not rejected as "not iterable").
	private static Iterator<Object> flattenToIterator(JSEnvironment env, Object element) {
		Object flattened = BuiltinIteratorConstructor.getIteratorFlattenable(env, element);
		return ConcatIterator.wrapIteratorResult(env, flattened);
	}

	// GetOptionsObject: undefined -> {}, an Object -> itself, else TypeError.
	private static Object getOptionsObject(JSEnvironment env, Object options) {
		if(options==RuntimeUtil.UNDEFINED) {
			return JSObject.create(env);
		}
		if(RuntimeUtil.isObject(env,options)) {
			return options;
		}
		throw RuntimeUtil.typeError("options must be an object");
	}

	private static final String MODE_SHORTEST = "shortest";
	private static final String MODE_LONGEST = "longest";
	private static final String MODE_STRICT = "strict";

	// Deliberately checks `m instanceof String` (a genuine JS primitive
	// string, this engine's convention for representing one - no wrapper
	// type) rather than coercing via RuntimeUtil.toString(): per spec, an
	// invalid "mode" must be rejected outright, INCLUDING a String wrapper
	// object (`new String("shortest")`), without ever calling toString()/
	// valueOf()/Symbol.toPrimitive on it - confirmed via zip/options-mode.js.
	private static String readMode(JSEnvironment env, Object options) {
		Object m = RuntimeUtil.getProperty(env, options, "mode", RuntimeUtil.UNDEFINED);
		if(m==RuntimeUtil.UNDEFINED) {
			return MODE_SHORTEST;
		}
		// A plain Java String represents BOTH a JS primitive string AND a
		// boxed String object (`new String(...)`) in this engine - the two
		// are only distinguished by identity via RuntimeUtil.isPrimitiveValue/
		// isObject (a PrimitivePropertyMap membership check), NOT by Java
		// type. A boxed String wrapper must be rejected just like any other
		// object (confirmed via zip/options-mode.js's "String wrappers are
		// not accepted" case).
		if(m instanceof String mode && RuntimeUtil.isPrimitiveValue(env,m) && (MODE_SHORTEST.equals(mode) || MODE_LONGEST.equals(mode) || MODE_STRICT.equals(mode))) {
			return mode;
		}
		throw RuntimeUtil.typeError("Invalid Iterator.zip mode");
	}

	// Reads the "padding" option as a RAW value only (a single property Get) -
	// per spec, this must happen BEFORE "iterables" is ever touched (before
	// GetIterator/[[OwnPropertyKeys]]), but its actual per-slot VALUES can
	// only be materialized once n (the real iterable count) is known, which
	// happens strictly later - see materializePadding[Keyed] below and
	// zip/zipKeyed/iterables-iteration-after-reading-options.js. Must be
	// undefined or an Object - validated HERE, immediately, regardless of
	// whether n turns out to be 0 later (confirmed via zip/options-padding.js:
	// `Iterator.zip([], {mode:"longest", padding:null})` must still throw
	// even though the empty "iterables" means no padding value is ever
	// actually consumed).
	private static Object readPaddingOption(JSEnvironment env, Object options) {
		Object padding = RuntimeUtil.getProperty(env, options, "padding", RuntimeUtil.UNDEFINED);
		if(padding!=RuntimeUtil.UNDEFINED && !RuntimeUtil.isObject(env,padding)) {
			throw RuntimeUtil.typeError("Iterator.zip padding option must be an object");
		}
		return padding;
	}

	// The `its` param (the already-opened, real per-iterable iterators) is
	// needed ONLY for closing them on any of padding's own three abrupt-
	// completion scenarios (padding's GetIterator failing, a step on
	// padding's iterator throwing, or padding's own close/return() throwing)
	// - see the three padding-iteration-*-abrupt-completion.js files below.
	private static List<Object> materializePadding(JSEnvironment env, Object paddingArg, int n, List<Iterator<Object>> its) {
		List<Object> padding = new ArrayList<>(n);
		if(paddingArg==RuntimeUtil.UNDEFINED) {
			for(int i=0; i<n; i++) {
				padding.add(RuntimeUtil.UNDEFINED);
			}
			return padding;
		}
		Iterator<Object> it;
		try {
			it = RuntimeUtil.valueIterator(env, paddingArg);
		} catch(RuntimeException e) {
			// GetIterator(paddingOption) itself failed - paddingOption IS
			// the exception source (never itself closed), only the already-
			// opened its[] are closed (reverse order) - confirmed via zip/
			// padding-iteration-get-iterator-abrupt-completion.js.
			closeReverseThenInput(env, its, null);
			throw e;
		}
		// Per spec: close the padding iterator ONLY if it's still "in use"
		// (not yet naturally exhausted) once n values have been drained - if
		// it ran out mid-drain (n > the padding iterable's own length), it
		// was already fully consumed and must NOT be closed again. Confirmed
		// via zip/padding-iteration.js's exact expected call log across many
		// n/k combinations (a "call return" trailing entry appears only when
		// n <= k, never when n > k).
		boolean usingIterator = true;
		for(int i=0; i<n; i++) {
			if(usingIterator) {
				boolean stepDone;
				Object stepValue = RuntimeUtil.UNDEFINED;
				try {
					stepDone = !it.hasNext();
					if(!stepDone) {
						stepValue = it.next();
					}
				} catch(RuntimeException e) {
					// IteratorStepValue(paddingIter) itself threw (next(),
					// or the result's "done"/"value" getter) - paddingIter
					// IS the exception source, only its[] closed (reverse) -
					// confirmed via zip/padding-iteration-iterator-step-
					// value-abrupt-completion.js.
					closeReverseThenInput(env, its, null);
					throw e;
				}
				if(stepDone) {
					usingIterator = false;
					padding.add(RuntimeUtil.UNDEFINED);
				} else {
					padding.add(stepValue);
				}
			} else {
				padding.add(RuntimeUtil.UNDEFINED);
			}
		}
		if(usingIterator) {
			try {
				RuntimeUtil.iteratorClose(env, it);
			} catch(RuntimeException e) {
				// Padding's OWN close (return()) threw - that becomes the
				// new completion, propagating from here; its[] must still
				// be closed afterward (reverse, each swallowing its own new
				// failure, since the completion is now already abrupt) -
				// confirmed via zip/padding-iteration-iterator-close-
				// abrupt-completion.js.
				closeReverseThenInput(env, its, null);
				throw e;
			}
		}
		return padding;
	}

	// zipKeyed's padding option is keyed BY PROPERTY NAME (matching the same
	// keys as the iterables record), not positional/iterable like zip's.
	private static List<Object> materializePaddingKeyed(JSEnvironment env, Object paddingArg, List<Object> keys, List<Iterator<Object>> its) {
		List<Object> padding = new ArrayList<>(keys.size());
		if(paddingArg==RuntimeUtil.UNDEFINED) {
			for(int i=0; i<keys.size(); i++) {
				padding.add(RuntimeUtil.UNDEFINED);
			}
			return padding;
		}
		if(!RuntimeUtil.isObject(env, paddingArg)) {
			throw RuntimeUtil.typeError("padding must be an object");
		}
		for(Object key: keys) {
			try {
				padding.add(RuntimeUtil.getProperty(env, paddingArg, key, RuntimeUtil.UNDEFINED));
			} catch(RuntimeException e) {
				// Get(paddingOption, key) itself failed - paddingOption is
				// the exception source, only its[] closed (reverse) -
				// confirmed via zipKeyed/padding-iteration-get-abrupt-
				// completion.js.
				closeReverseThenInput(env, its, null);
				throw e;
			}
		}
		return padding;
	}

	// IteratorCloseAll(the list-concatenation of « inputIter » and iters):
	// closes in REVERSE list order - i.e. the most-recently-opened flattened
	// iterator first, working backwards, with inputIter (the "iterables"
	// argument's own iterator, if any - null for zipKeyed, which never opens
	// one) closed LAST. Each close is independently best-effort (a NEW
	// exception from a return() call must not override the original abrupt
	// completion already in flight) - see iterables-iteration-get-iterator-
	// flattenable-abrupt-completion.js.
	private static void closeReverseThenInput(JSEnvironment env, List<Iterator<Object>> its, Iterator<Object> inputIter) {
		for(int i=its.size()-1; i>=0; i--) {
			RuntimeUtil.iteratorCloseQuietly(env, its.get(i));
		}
		if(inputIter!=null) {
			RuntimeUtil.iteratorCloseQuietly(env, inputIter);
		}
	}

	// Per spec, each element of "iterables" is fetched ONE AT A TIME and
	// IMMEDIATELY flattened (GetIteratorFlattenable) before the next element
	// is fetched - NOT "read every element first, then flatten every element"
	// (confirmed via the exact interleaved property-access order required by
	// zip/iterables-iteration.js's Proxy-trap log).
	static Object zip(JSEnvironment env, Object iterablesArg, Object optionsArg) {
		// Step 1: "iterables" must be an Object - checked BEFORE anything
		// else, including reading "options"/"mode"/"padding" (confirmed via
		// zip/iterables-primitive.js: a getter-trapped "mode"/"padding" on
		// the options argument must NOT be invoked when "iterables" is
		// itself invalid).
		if(!RuntimeUtil.isObject(env, iterablesArg)) {
			throw RuntimeUtil.typeError("Iterator.zip argument must be an object");
		}
		Object options = getOptionsObject(env, optionsArg);
		String mode = readMode(env, options);
		Object paddingArg = MODE_LONGEST.equals(mode) ? readPaddingOption(env, options) : RuntimeUtil.UNDEFINED;
		Iterator<Object> inputIter = RuntimeUtil.valueIterator(env, iterablesArg);
		List<Iterator<Object>> its = new ArrayList<>();
		while(true) {
			Object element;
			try {
				if(!inputIter.hasNext()) {
					break;
				}
				element = inputIter.next();
			} catch(RuntimeException e) {
				// IfAbruptCloseIterators(next, iters): the FETCH itself
				// (inputIter.next()) failed - inputIter IS the source of
				// this exception, not something to additionally close (only
				// the already-flattened `its` are closed) - confirmed via
				// iterables-iteration-iterator-step-value-abrupt-completion.js,
				// which expects "iterables"'s own return() to NEVER be
				// called in this scenario.
				closeReverseThenInput(env, its, null);
				throw e;
			}
			try {
				its.add(flattenToIterator(env, element));
			} catch(RuntimeException e) {
				// IfAbruptCloseIterators(iter, list-concat(inputIter, iters)):
				// flattening a successfully-fetched element failed - inputIter
				// itself is still valid/open here, so it's included in the
				// close set too (this is the scenario iterables-iteration-
				// get-iterator-flattenable-abrupt-completion.js already
				// covers).
				closeReverseThenInput(env, its, inputIter);
				throw e;
			}
		}
		int n = its.size();
		List<Object> padding = MODE_LONGEST.equals(mode) ? materializePadding(env, paddingArg, n, its) : null;
		return new BuiltinIteratorHelper(env, new ZipRowIterator(env, its, mode, padding, false));
	}

	static Object zipKeyed(JSEnvironment env, Object iterablesArg, Object optionsArg) {
		if(!RuntimeUtil.isObject(env, iterablesArg)) {
			throw RuntimeUtil.typeError("Iterator.zipKeyed argument must be an object");
		}
		Object options = getOptionsObject(env, optionsArg);
		String mode = readMode(env, options);
		Object paddingArg = MODE_LONGEST.equals(mode) ? readPaddingOption(env, options) : RuntimeUtil.UNDEFINED;
		List<Object> keys = new ArrayList<>();
		List<Iterator<Object>> its = new ArrayList<>();
		try {
			// Per spec, allKeys = iterables.[[OwnPropertyKeys]]() is captured
			// ONCE, up front, strictly before any property is actually read -
			// a mutation triggered by one of THIS SAME loop's own later [[Get]]
			// calls (a getter deleting or adding a property) must not affect
			// which keys are considered: a key deleted after the snapshot was
			// taken is simply skipped (its [[GetOwnProperty]] comes back empty
			// once its turn comes), but a key ADDED after the snapshot is never
			// visited at all, since it was never in allKeys to begin with.
			// Collecting the raw entries first - WITHOUT calling .getValue(),
			// which is what actually invokes a getter - captures this snapshot
			// without running any user code yet. Both string AND symbol keys
			// are used - confirmed via zipKeyed/iterables-iteration-symbol-
			// key.js; the snapshot-vs-live distinction itself is confirmed via
			// zipKeyed/iterables-iteration-deleted.js.
			List<Object> allKeys = new ArrayList<>();
			for(var it=env.getAccessor(iterablesArg).ownPropertyEntries(iterablesArg,true,true,false); it.hasNext();) {
				allKeys.add(it.next().getKey());
			}
			JSAccessor acc = env.getAccessor(iterablesArg);
			for(Object key: allKeys) {
				PropertyDescriptor desc = acc.getOwnPropertyDescriptor(iterablesArg, key);
				if(desc==null || !desc.isEnumerable()) {
					continue;
				}
				Object value = RuntimeUtil.getProperty(env, iterablesArg, key);
				// Per spec: a property whose value is exactly undefined is
				// skipped entirely (no key, no iterator) - see
				// zipKeyed/iterables-iteration-undefined.js.
				if(value==RuntimeUtil.UNDEFINED) {
					continue;
				}
				keys.add(key);
				its.add(flattenToIterator(env, value));
			}
		} catch(RuntimeException e) {
			closeReverseThenInput(env, its, null);
			throw e;
		}
		List<Object> padding = MODE_LONGEST.equals(mode) ? materializePaddingKeyed(env, paddingArg, keys, its) : null;
		return new BuiltinIteratorHelper(env, new ZipRowIterator(env, its, mode, padding, true, keys));
	}

	static Object concat(JSEnvironment env, List<Object> items) {
		List<Object[]> iterables = new ArrayList<>(); // {item, openMethod}
		for(Object item: items) {
			if(!RuntimeUtil.isObject(env, item)) {
				throw RuntimeUtil.typeError("Iterator.concat arguments must be objects");
			}
			Object method = RuntimeUtil.getProperty(env, item, org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol.ITERATOR, RuntimeUtil.UNDEFINED);
			if(!(method instanceof Callable c && c.isCallable())) {
				throw RuntimeUtil.typeError("Object is not iterable");
			}
			iterables.add(new Object[]{item,c});
		}
		return new BuiltinIteratorHelper(env, new ConcatIterator(env, iterables));
	}

	// Steps every inner iterator once per row, yielding a JSArray (zip) or a
	// null-prototype JSObject (zipKeyed) each step - implementing shortest/
	// longest/strict per-row semantics. Rows are computed eagerly (cached)
	// so hasNext()/next() (called separately by BuiltinIteratorHelperPrototype)
	// don't double-step the underlying iterators.
	private static final class ZipRowIterator implements Iterator<Object>, ExternallyCloseable {
		private final JSEnvironment env;
		private final List<Iterator<Object>> its;
		private final String mode;
		private final List<Object> padding;
		private final boolean keyed;
		private final List<Object> keys;
		private final boolean[] alive;

		private boolean shouldCompute = true;
		private boolean done;
		private Object row;

		ZipRowIterator(JSEnvironment env, List<Iterator<Object>> its, String mode, List<Object> padding, boolean keyed) {
			this(env, its, mode, padding, keyed, null);
		}
		ZipRowIterator(JSEnvironment env, List<Iterator<Object>> its, String mode, List<Object> padding, boolean keyed, List<Object> keys) {
			this.env = env;
			this.its = its;
			this.mode = mode;
			this.padding = padding;
			this.keyed = keyed;
			this.keys = keys;
			this.alive = new boolean[its.size()];
			java.util.Arrays.fill(alive, true);
			if(its.isEmpty()) {
				this.done = true;
				this.shouldCompute = false;
			}
		}

		// Per-mode row-stepping precision (confirmed via the exact expected
		// call logs in iterator-zip-iteration.js and its shortest/strict
		// "-close-abrupt-completion"/"-step-abrupt-completion" siblings):
		// - shortest: steps iterators 0..n-1 in order, but STOPS calling
		//   next() the moment ANY reports done (later iterators that row are
		//   never touched at all) - then closes every STILL-alive iterator
		//   (which naturally excludes only the one just found done) in
		//   REVERSE index order.
		// - strict: steps EVERY iterator every row regardless (needed to
		//   detect a length mismatch at all), but compares each one's
		//   done-ness against index 0's - the moment any index's done-ness
		//   DIFFERS from index 0's (in EITHER direction: index 0 done while a
		//   later one isn't, or vice versa), stops immediately and closes
		//   every still-alive iterator in reverse order, then throws.
		// - longest: steps every iterator every row unconditionally, no
		//   early exit, no mid-row closing - only ends (no closing) once ALL
		//   are simultaneously done.
		private void compute() {
			shouldCompute = false;
			int n = its.size();
			Object[] values = new Object[n];
			if(MODE_SHORTEST.equals(mode)) {
				boolean rowDone = false;
				for(int i=0; i<n; i++) {
					if(!step(i, values)) {
						rowDone = true;
						break;
					}
				}
				if(rowDone) {
					// Per spec, shortest mode's natural stop closes with a
					// NORMAL completion (ReturnCompletion(undefined)), not an
					// already-abrupt one - so unlike strict mode's mismatch
					// closing below (which starts from an ALREADY-thrown
					// TypeError that must always win), the FIRST exception
					// from closing here must actually propagate out of
					// next() itself, while subsequent closes still run
					// (ignoring their own new exceptions) - exactly
					// closeAll()'s own semantics, reused directly (it also
					// sets done=true). Confirmed via iterator-zip-iteration-
					// shortest-iterator-close-abrupt-completion.js, which
					// expects `it.next()` itself to throw.
					closeAll(env);
					return;
				}
				done = false;
				row = keyed ? buildKeyedRow(values) : buildArrayRow(values);
				return;
			}
			if(MODE_STRICT.equals(mode)) {
				Boolean firstDone = null;
				boolean mismatch = false;
				for(int i=0; i<n; i++) {
					boolean stepDone = !step(i, values);
					if(stepDone) {
						values[i] = RuntimeUtil.UNDEFINED;
					}
					if(i==0) {
						firstDone = stepDone;
					} else if(stepDone!=firstDone) {
						mismatch = true;
						break;
					}
				}
				if(mismatch) {
					closeAliveReverse(n);
					throw RuntimeUtil.typeError("Iterator.zip: iterables have different lengths in strict mode");
				}
				if(Boolean.TRUE.equals(firstDone)) {
					done = true;
					return;
				}
				done = false;
				row = keyed ? buildKeyedRow(values) : buildArrayRow(values);
				return;
			}
			// longest
			boolean allDone = true;
			for(int i=0; i<n; i++) {
				if(!alive[i]) {
					values[i] = padding.get(i);
					continue;
				}
				if(step(i, values)) {
					allDone = false;
				} else {
					values[i] = padding.get(i);
				}
			}
			if(allDone) {
				done = true;
				return;
			}
			done = false;
			row = keyed ? buildKeyedRow(values) : buildArrayRow(values);
		}

		// IteratorStepValue(iter): steps a single inner iterator, recording
		// its value into values[i] and returning true, or marking alive[i]
		// false and returning false once it's done. If the step ITSELF
		// throws (the underlying next() call throwing, not just reporting
		// done), that exception IS the abrupt completion already in flight -
		// per IteratorCloseAll's semantics, every OTHER still-alive iterator
		// must still be closed (in reverse order, each swallowing its own
		// new exception, since the ORIGINAL exception here always wins),
		// then the original exception is re-thrown. Confirmed via
		// iterator-zip-iteration-iterator-step-value-abrupt-completion.js
		// (applies identically across all three modes, not mode-specific).
		private boolean step(int i, Object[] values) {
			Iterator<Object> it = its.get(i);
			try {
				if(it.hasNext()) {
					values[i] = it.next();
					return true;
				}
				alive[i] = false;
				return false;
			} catch(RuntimeException e) {
				alive[i] = false;
				closeAliveReverse(its.size());
				throw e;
			}
		}

		private void closeAliveReverse(int n) {
			for(int i=n-1; i>=0; i--) {
				if(alive[i]) {
					alive[i] = false;
					RuntimeUtil.iteratorCloseQuietly(env, its.get(i));
				}
			}
		}

		// Called when the ZIP RESULT ITSELF is closed from the outside (a
		// for-of `break`, or an explicit `.return()` call) - see
		// RuntimeUtil.iteratorClose(Iterator)'s ExternallyCloseable check.
		// Unlike closeAliveReverse() (used internally when a row-stepping
		// mode naturally finishes/mismatches, where any close failure is
		// simply swallowed), IteratorCloseAll's real semantics must let the
		// FIRST close failure actually propagate, while every SUBSEQUENT
		// close still runs (ignoring ITS own new exception) - confirmed via
		// iterator-zip-iteration-iterator-close-abrupt-completion.js, where
		// three inner iterators all throw a DIFFERENT error on close() and
		// only the middle (first-encountered-in-reverse-order) one's error
		// is the one that actually propagates.
		@Override
		public void closeAll(JSEnvironment env) {
			RuntimeException pending = null;
			for(int i=its.size()-1; i>=0; i--) {
				if(alive[i]) {
					alive[i] = false;
					try {
						RuntimeUtil.iteratorClose(env, its.get(i));
					} catch(RuntimeException e) {
						if(pending==null) {
							pending = e;
						}
					}
				}
			}
			done = true;
			if(pending!=null) {
				throw pending;
			}
		}

		private Object buildArrayRow(Object[] values) {
			JSArray a = JSArray.create(env);
			for(Object v: values) {
				a.arrayAdd(v);
			}
			return a;
		}
		private Object buildKeyedRow(Object[] values) {
			JSObject o = JSObject.createWithPrototype(env, null);
			for(int i=0; i<values.length; i++) {
				Object key = keys.get(i);
				if(key instanceof org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol sym) {
					o.setOwnProperty(sym, values[i]);
				} else {
					o.setOwnProperty((String)key, values[i]);
				}
			}
			return o;
		}

		@Override
		public boolean hasNext() {
			if(shouldCompute) {
				compute();
			}
			return !done;
		}
		@Override
		public Object next() {
			if(shouldCompute) {
				compute();
			}
			if(done) {
				throw new NoSuchElementException();
			}
			shouldCompute = true;
			return row;
		}
	}

	// Sequentially exhausts each (item, Symbol.iterator-method) pair in
	// order, only invoking the NEXT pair's open method once the CURRENT one
	// is exhausted (per test262: inner iterators are created lazily, in
	// order, not all up front).
	private static final class ConcatIterator implements Iterator<Object>, ExternallyCloseable {
		private final JSEnvironment env;
		private final List<Object[]> iterables; // {item, Callable openMethod}
		private int index = 0;
		private Iterator<Object> current;

		private boolean shouldCompute = true;
		private boolean done;
		private Object value;

		ConcatIterator(JSEnvironment env, List<Object[]> iterables) {
			this.env = env;
			this.iterables = iterables;
		}

		// Called when the CONCAT RESULT ITSELF is closed from the outside (a
		// for-of `break`, or an explicit `.return()` call) - see
		// RuntimeUtil.iteratorClose(Iterator)'s ExternallyCloseable check.
		// Without this, that check falls through all its instanceof branches
		// as a silent no-op (confirmed via test262 Iterator/concat/return-
		// is-forwarded.js and return-method-called-with-zero-arguments.js).
		// Only the CURRENTLY active inner iterator needs closing - earlier
		// ones already ran to exhaustion, and later ones in the sequence
		// were never even opened yet.
		@Override
		public void closeAll(JSEnvironment env) {
			Iterator<Object> c = current;
			current = null;
			done = true;
			shouldCompute = false;
			if(c!=null) {
				RuntimeUtil.iteratorClose(env, c);
			}
		}

		private void compute() {
			shouldCompute = false;
			while(true) {
				if(current==null) {
					if(index>=iterables.size()) {
						done = true;
						return;
					}
					Object[] pair = iterables.get(index++);
					Object item = pair[0];
					Callable openMethod = (Callable)pair[1];
					Object it = openMethod.call(item, RuntimeUtil.EMPTY_PARAMS);
					if(!RuntimeUtil.isObject(env,it)) {
						throw RuntimeUtil.typeError("Result of the Symbol.iterator method is not an object");
					}
					current = wrapIteratorResult(env, it);
				}
				if(current.hasNext()) {
					done = false;
					value = current.next();
					return;
				}
				current = null;
			}
		}

		private static Iterator<Object> wrapIteratorResult(JSEnvironment env, Object jsIterator) {
			if(jsIterator instanceof Iterator<?> it) {
				@SuppressWarnings("unchecked")
				Iterator<Object> r = (Iterator<Object>)it;
				return r;
			}
			return new org.monflabs.galtajs.rt.protocols.iterator.JavaIterator(env, jsIterator);
		}

		@Override
		public boolean hasNext() {
			if(shouldCompute) {
				compute();
			}
			return !done;
		}
		@Override
		public Object next() {
			if(shouldCompute) {
				compute();
			}
			if(done) {
				throw new NoSuchElementException();
			}
			shouldCompute = true;
			return value;
		}
	}
}
