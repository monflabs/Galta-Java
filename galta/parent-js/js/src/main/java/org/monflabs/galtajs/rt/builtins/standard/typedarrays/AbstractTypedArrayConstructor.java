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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseConstructor;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * The shared, abstract {@code %TypedArray%} intrinsic - the common
 * [[Prototype]] of every concrete typed array constructor (Int8Array,
 * Float64Array, ...), analogous to how {@link TypedArrayPrototype} is
 * their common prototype-to-prototype link. Per spec it is not directly
 * constructable or callable; {@code from}/{@code of}/{@code isView} and the
 * {@code Symbol.species} getter live here once and are inherited by every
 * concrete typed array constructor, rather than being duplicated on each.
 */
public class AbstractTypedArrayConstructor extends BaseConstructor {

	public static final String CLASSNAME = "TypedArray";

	public static AbstractTypedArrayConstructor get(JSEnvironment env) {
		AbstractTypedArrayConstructor ctor = (AbstractTypedArrayConstructor)env.getRegisteredPrototype(AbstractTypedArrayConstructor.class);
		if(ctor==null) {
			ctor = new AbstractTypedArrayConstructor(env);
			env.registerPrototype(AbstractTypedArrayConstructor.class,ctor);
		}
		return ctor;
	}

	private AbstractTypedArrayConstructor(JSEnvironment env) {
		super(env,CLASSNAME,TypedArrayPrototype.get(env),0);
		setOwnMethod(new Method(env,MethodId.from,1));
		setOwnMethod(new Method(env,MethodId.isView,1));
		setOwnMethod(new Method(env,MethodId.of,0));

		// get [Symbol.species] () { return this; } - a getter-only accessor
		// (not a static value) so subclasses correctly return themselves.
		setOwnProperty(Symbol.SPECIES, true, false, (base,key) -> base, null);
	}

	@Override
	public Class<?> getNativeClass() {
		return TypedArray.class;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Abstract class TypedArray not directly constructable");
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		throw RuntimeUtil.typeError("Abstract class TypedArray not directly constructable");
	}

	// TypedArrayCreate(C, «len»): constructs via C, then requires the
	// result's actual length to be at least `len` - a custom constructor
	// returning a SMALLER instance must throw TypeError here (rather than
	// being allowed through only to fail later with a RangeError from an
	// out-of-bounds element write); a LARGER instance is explicitly fine
	// (confirmed via test262: map/filter/slice's speciesctor tests expect
	// a custom species constructor returning a bigger-than-requested
	// TypedArray to succeed, not throw).
	private static TypedArray typedArrayCreate(Constructor c, Constructor newTarget, long len) {
		Object newObj = c.constructObject(new Object[]{Long.valueOf(len)}, newTarget);
		if(!(newObj instanceof TypedArray result)) {
			throw RuntimeUtil.typeError("TypedArrayCreate: constructor did not return a TypedArray instance");
		}
		if(result.getLength()<len) {
			throw RuntimeUtil.typeError("TypedArrayCreate: constructor returned a TypedArray of the wrong length");
		}
		return result;
	}
	private static TypedArray typedArrayCreate(Constructor c, long len) {
		return typedArrayCreate(c, c, len);
	}

	// Per spec, an Integer-Indexed Exotic Object's [[ContentType]]
	// (BigInt vs Number) must match between the species-constructed result
	// and the exemplar it was derived from - checked by every
	// TypedArraySpeciesCreate call site (slice/filter/map/subarray/...).
	private static void checkContentTypeMatches(TypedArray exemplar, TypedArray result) {
		if(exemplar.isBigIntTypedArray()!=result.isBigIntTypedArray()) {
			throw RuntimeUtil.typeError("TypedArraySpeciesCreate: content type mismatch between species constructor result and exemplar");
		}
	}

	// TypedArraySpeciesCreate(exemplar, «len»): unlike TypedArrayCreate,
	// consults exemplar.constructor[Symbol.species] (falling back to
	// exemplar's own real constructor) rather than always reusing the
	// exemplar's own type - used by slice/filter/map, whose result must be
	// built via a subclass's own overridden species constructor if present.
	public static TypedArray typedArraySpeciesCreate(JSEnvironment env, TypedArray exemplar, long len) {
		Constructor c = RuntimeUtil.speciesConstructor(env, exemplar, exemplar.getConstructor());
		TypedArray result = typedArrayCreate(c, len);
		checkContentTypeMatches(exemplar, result);
		// TypedArraySpeciesCreate(..., ~write~): every caller of THIS
		// overload (map/filter/slice) is about to WRITE the mapped/copied
		// elements into `result`, so a custom species constructor handing
		// back a TypedArray backed by an immutable ArrayBuffer must be
		// rejected here, before any element is written (test262
		// TypedArray/prototype/{map,filter,slice}/speciesctor-destination-
		// backed-by-immutable-buffer.js).
		if(result.getArrayBuffer().isImmutable()) {
			throw RuntimeUtil.typeError("TypedArraySpeciesCreate: constructor returned a TypedArray backed by an immutable ArrayBuffer");
		}
		return result;
	}

	// TypedArraySpeciesCreate(exemplar, «buffer, byteOffset, length»): the
	// ArrayBuffer-view-taking overload used by `subarray`.
	public static TypedArray typedArraySpeciesCreate(JSEnvironment env, TypedArray exemplar, BaseArrayBuffer buffer, long byteOffset, long length) {
		Constructor c = RuntimeUtil.speciesConstructor(env, exemplar, exemplar.getConstructor());
		// A `length` of TypedArray.LENGTH_TRACKING (subarray's "end
		// omitted on a length-tracking source" case) must construct via
		// the 2-ARGUMENT form (buffer, byteOffset) - passing it through as
		// a literal negative JS-level length argument would be rejected
		// by ToIndex - so the resulting view is ALSO auto-length-tracking
		// (confirmed via subarray/resizable-buffer.js's final assertions,
		// which require a subarray-of-a-length-tracking-array to itself
		// keep tracking the buffer's length after subsequent growth).
		Object[] args = length==TypedArray.LENGTH_TRACKING
			? new Object[]{buffer, byteOffset}
			: new Object[]{buffer, byteOffset, length};
		Object newObj = c.constructObject(args, c);
		if(!(newObj instanceof TypedArray result)) {
			throw RuntimeUtil.typeError("TypedArraySpeciesCreate: constructor did not return a TypedArray instance");
		}
		checkContentTypeMatches(exemplar, result);
		return result;
	}

	// TypedArrayCreate(C, «len»)-style: constructs via the RECEIVER `c`
	// (the actual constructor `from`/`of` was called on), not any
	// Java-abstract "creation" method - so a subclass calling
	// `Int8Array.from.call(SomeOtherCtor, ...)` correctly builds a
	// SomeOtherCtor instance rather than an Int8Array.
	// Package-visible: also used by TypedArrayConstructor.constructObject()'s
	// iterable/array-like argument path (`new Int8Array(iterable)`) - via the
	// newTarget-aware overload below, since that path (unlike the static
	// %TypedArray%.from() method) has a real, possibly-different newTarget
	// (e.g. Reflect.construct(Int8Array, [iterable], SomeOtherNewTarget)) that
	// must be consulted for GetPrototypeFromConstructor.
	static TypedArray from(JSEnvironment env, Constructor c, Object arrayLike, Object function, Object thisArg) {
		return from(env, c, c, arrayLike, function, thisArg);
	}
	// Per spec, IsCallable(mapfn) is checked FIRST, before even looking at
	// source[@@iterator] (confirmed via mapfn-is-not-callable.js: getting
	// @@iterator must never happen if mapfn is not callable). The target
	// typed array is then created via TypedArrayCreate ONCE, with the
	// already-known final length, BEFORE the mapping loop runs - writes
	// during that loop go through the tolerant setIfValid (spec's ordinary
	// Set, which silently ignores an out-of-bounds/detached index) rather
	// than raw set(), since the mapper function may itself detach the
	// target's buffer mid-loop (confirmed via
	// from-array-mapper-detaches-result.js and its typedarray-source
	// siblings).
	static TypedArray from(JSEnvironment env, Constructor c, Constructor newTarget, Object arrayLike, Object function, Object thisArg) {
		Callable mapfn = null;
		if(function!=RuntimeUtil.UNDEFINED) {
			if(!(function instanceof Callable callable && callable.isCallable())) {
				throw RuntimeUtil.typeError("Function is not a Callable");
			}
			mapfn = callable;
		}
		List<Object> list = new ArrayList<>();
		Iterator<Object> it = RuntimeUtil.valueIteratorUnchecked(env,arrayLike);
		TypedArray result;
		if(it!=null) {
			while(it.hasNext()) {
				list.add(it.next());
			}
			result = typedArrayCreate(c, newTarget, list.size());
			checkResultNotImmutable(result);
		} else {
			JSArray a = RuntimeUtil.getArrayLikeUnchecked(env, arrayLike, false);
			if(a!=null) {
				// Spec step "Perform ? AllocateTypedArrayBuffer(O, len)"
				// happens BEFORE any element is copied - a length that
				// can't back a real TypedArray buffer must throw
				// RangeError immediately, not after first materializing
				// an in-memory List with that many entries (confirmed via
				// TypedArrayConstructors/ctors/object-arg/
				// length-excessive-throws.js: `{length: 2**53}` - a
				// REPRODUCED hang/OOM attempting to build a 2^53-entry
				// List before ever reaching typedArrayCreate's own,
				// already-correct, length check below).
				long len = a.arrayLength();
				if(len<0 || len>=Integer.MAX_VALUE) {
					throw RuntimeUtil.rangeError("Invalid TypedArray length '{0}'",len);
				}
				// Construct (and check for an immutable-backed result)
				// BEFORE visiting any array-like source element - unlike
				// the iterator-source branch above, where IterableToList
				// must unconditionally exhaust the iterator first per
				// spec, an array-like source's own length is already
				// known here without reading any INDEXED property, so
				// TypedArrayCreate can (and per spec must) run first
				// (test262 TypedArrayConstructors/from/custom-ctor-
				// returns-immutable-arraybuffer.js: a custom constructor
				// returning an immutable-backed TypedArray must reject
				// here, before `source[0]` etc. are ever read).
				result = typedArrayCreate(c, newTarget, len);
				checkResultNotImmutable(result);
				Iterator<Object> ai = a.arrayIterator();
				while(ai.hasNext()) {
					list.add(ai.next());
				}
			} else {
				result = typedArrayCreate(c, newTarget, list.size());
				checkResultNotImmutable(result);
			}
		}
		for(int i=0; i<list.size(); i++) {
			Object v = list.get(i);
			if(mapfn!=null) {
				v = mapfn.call(thisArg, new Object[] {v,(long)i});
			}
			result.setIfValid(i, RuntimeUtil.toTypedArrayElement(env,result,v));
		}
		return result;
	}

	// %TypedArray%.from writes each (possibly mapped) source element into
	// `result` - a custom constructor (`TA.from.call(ctor, ...)`) handing
	// back a TypedArray backed by an immutable ArrayBuffer must be
	// rejected as soon as `result` exists, before any source element is
	// visited/mapped.
	private static void checkResultNotImmutable(TypedArray result) {
		if(result.getArrayBuffer().isImmutable()) {
			throw RuntimeUtil.typeError("TypedArrayCreate: constructor returned a TypedArray backed by an immutable ArrayBuffer");
		}
	}

	private static enum MethodId {
		from,
		of,
		isView,
	}

	private static final class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}

		@Override
		protected Object invoke(final Object obj, final Object[] args) {
			switch(methodId) {

				// %TypedArray%.of/from use `this` (the receiver, per spec's
				// TypedArrayCreate(C,...)) as the constructor - they only
				// require IsConstructor(this), not that the receiver be a
				// genuine TypedArrayConstructor instance.
				case of -> {
					if(!(obj instanceof Constructor c)) {
						throw RuntimeUtil.typeError("%TypedArray%.of called on a non-constructor receiver");
					}
					TypedArray result = typedArrayCreate(c, args.length);
					// Same rejection as %TypedArray%.from - see its own
					// doc comment (test262 TypedArrayConstructors/of/
					// custom-ctor-returns-immutable-arraybuffer.js).
					if(result.getArrayBuffer().isImmutable()) {
						throw RuntimeUtil.typeError("TypedArrayCreate: constructor returned a TypedArray backed by an immutable ArrayBuffer");
					}
					for(int i=0; i<args.length; i++) {
						// Set(newObj, Pk, kValue, true) - a plain [[Set]], not a
						// direct buffer write, so an index that's gone out of
						// bounds (a resizable buffer shrunk via a poisoned
						// ToNumber/ToBigInt valueOf() on an EARLIER argument) must
						// be silently ignored, same as .from()'s own use of
						// setIfValid() above - not throw (confirmed via test262
						// built-ins/TypedArray/of/resized-with-out-of-bounds-and-
						// in-bounds-indices.js).
						result.setIfValid(i, RuntimeUtil.toTypedArrayElement(getEnvironment(),result,args[i]));
					}
					return result;
				}

				case from -> {
					if(!(obj instanceof Constructor c)) {
						throw RuntimeUtil.typeError("%TypedArray%.from called on a non-constructor receiver");
					}
					Object arrayLike = param(args, 0);
					Object function = param(args, 1, RuntimeUtil.UNDEFINED);
					Object thisArg = param(args, 2, RuntimeUtil.UNDEFINED);
					return AbstractTypedArrayConstructor.from(getEnvironment(), c, arrayLike, function, thisArg);
				}

				case isView -> {
					Object o = param(args,0);
					return o instanceof ArrayBufferView;
				}

				default -> {
					throw new IllegalStateException(); // Should never be here
				}
			}
		}
	}
}
