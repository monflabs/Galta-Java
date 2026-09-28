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
package org.monflabs.galtajs.rt.builtins.primitives.array.arraylike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.util.StringFormat;
import org.monflabs.json.JsonArray.EntryConsumerWhile;


public class JSArrayJSObject extends JSBaseArray {

	public static JSArrayJSObject of(@NonNull JSObject object) {
		return new JSArrayJSObject(object);
	}

	private JSObject object;

	private JSArrayJSObject(@NonNull JSObject object) {
		super(object.getEnvironment());
		this.object = object;
	}

	// JSBaseArray's default getPrototype() hardcodes Array.prototype -
	// correct for the OTHER array-like adapters (String/Arguments/Java
	// array/List have no real JS prototype of their own to speak of), but
	// wrong here: `object` is a genuine JSObject that may have its OWN
	// real prototype (e.g. the result of a custom @@species constructor
	// via RuntimeUtil.arraySpeciesCreate, which wraps whatever plain
	// object the constructor returned in a JSArrayJSObject purely for the
	// CALLING Array.prototype method's own convenience during iteration,
	// then returns that wrapper as the method's actual result - confirmed
	// via map/create-proxy.js and its filter/slice/splice siblings:
	// `Object.getPrototypeOf(result)` must reflect the custom
	// constructor's `.prototype`, not always the built-in Array.prototype).
	@Override
	public Object getPrototype() {
		return object.getPrototype();
	}
	@Override
	public boolean setPrototype(Object prototype) {
		return object.setPrototype(prototype);
	}

	// JSArray's own default ownPropertyEntries(...) is written for a
	// genuine Array's dense/sparse index storage - it enumerates
	// nonHoleIndices() bounded by arrayLength() (0 here, since a custom
	// species constructor never sets "length" at all) plus THIS wrapper's
	// own, unrelated `members` bag (see JSBaseArray.getMembers) - never
	// the real wrapped `object`'s actual own properties. This silently
	// made for-in/Object.keys/JSON.stringify/spread etc. see ZERO
	// properties on a species-constructed concat/map/filter/... result
	// whose constructor predefines properties without ever touching
	// "length" (confirmed via
	// concat/create-species-with-non-writable-property.js: propertyHelper.js's
	// isEnumerable() cross-checks getOwnPropertyDescriptor's answer against
	// a for-in scan, which found nothing). Delegating straight to the
	// wrapped object's real ownPropertyEntries mirrors the same fix
	// already applied to setOwnProperty/getOwnPropertyDescriptor/
	// deleteProperty above.
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		return object.ownPropertyEntries(strings,symbols,enumerableOnly);
	}

	// Per spec, an array-LIKE object's length (as opposed to a genuine
	// Array's own [[DefineOwnProperty]]-validated "length") is read via
	// LengthOfArrayLike/ToLength, which clamps a negative/NaN value to 0
	// and a huge value to 2**53-1 - it never throws. A prior attempt to
	// lift this cap appeared to cause a hang on
	// reverse/length-exceeding-integer-limit-with-object.js, but that was
	// confounded with an unrelated, separately-reverted sparse-candidate-
	// enumeration change made at the same time; re-tested with ONLY this
	// clamp fix in isolation and reverse completes instantly (its
	// hand-rolled swap loop pairs index 0 with index len-1 on its very
	// FIRST iteration, and every huge-length test in this bucket relies on
	// an early exception/early-return/zero-arguments short-circuit within
	// the first few iterations - none require actually walking anywhere
	// near len iterations).
	@Override
	public long arrayLength() {
		Object l = object.getProperty("length",0);
		return RuntimeUtil.toLength(getEnvironment(), l);
	}
	@Override
	public Object arrayGet(long index, Object defaultValue) {
		return object.getProperty(index, defaultValue);
	}

	// JSArray's own default getOwnProperty(long/String,...) is written for
	// a genuine Array's SparseList-backed storage: it gates the array-index
	// fast path on RuntimeUtil.isMemberIndex/memberIndex, both capped at
	// SparseList.MAX_INDEX (~2^32-2, the real max Array index) - anything
	// past that cap falls through to JSObjectDelegate's OWN, entirely
	// separate lazily-created `members` bag (see JSBaseArray.getMembers),
	// which has NOTHING to do with the wrapped `object` here. For a plain
	// array-LIKE object (ToLength-clamped length up to 2**53-1, per spec),
	// an index-shaped property key can legitimately exceed 2^32-2 - the cap
	// silently made such a key invisible, turning e.g. a getter defined at
	// key "9007199254740990" into a permanent miss and forcing callers like
	// reverse's hand-rolled swap loop to walk every index from 0 instead of
	// hitting the getter's side effect on its very first access (confirmed
	// via reverse/length-exceeding-integer-limit-with-object.js, reproduced
	// as a genuine multi-minute hang, not a hypothetical). Overridden here
	// to parse ANY non-negative integer string (uncapped) and delegate
	// straight to arrayGet/`object`, with a real fallback to the wrapped
	// object's own property for anything else - not the unrelated `members`
	// bag.
	private static long parseUncappedIndex(String member) {
		int len = member.length();
		if(len==0) {
			return -1;
		}
		char c = member.charAt(0);
		if(c<'0' || c>'9') {
			return -1;
		}
		if(len==1) {
			return c-'0';
		}
		if(c=='0') {
			return -1;
		}
		long result = c-'0';
		for(int i=1; i<len; i++) {
			c = member.charAt(i);
			if(c<'0' || c>'9') {
				return -1;
			}
			result = result*10 + (c-'0');
			if(result<0) {
				return -1;
			}
		}
		return result;
	}
	@Override
	public Object getOwnProperty(long index, Object defaultValue, Object receiver) {
		if(index>=0) {
			Object v = arrayGet(index, RuntimeUtil.NOT_AVAILABLE);
			if(v!=RuntimeUtil.NOT_AVAILABLE) {
				return v;
			}
		}
		return defaultValue;
	}
	@Override
	public Object getOwnProperty(String member, Object defaultValue, Object receiver) {
		if("length".equals(member)) {
			return arrayLength();
		}
		long index = parseUncappedIndex(member);
		if(index>=0) {
			return getOwnProperty(index, defaultValue, receiver);
		}
		return object.getOwnProperty(member, defaultValue, receiver);
	}

	// JSBaseArray's own getOwnPropertyDescriptor(long) ("index>=0 &&
	// index<arrayLength() -> a generic always-writable DESC_PROP_ARRAYINDEX
	// template") and JSArray's default getOwnPropertyDescriptor(String)
	// ("arrayHas(index) -> same generic template") are BOTH wrong for a
	// wrapped array-LIKE object: (a) the arrayLength() bound rejects a
	// perfectly valid index that's simply beyond the CURRENT declared
	// length (exactly what Object.defineProperty(obj, hugeIndex, {...})
	// sets up, and what push/splice/unshift are actively extending past
	// mid-call, before the final length write); (b) the generic template
	// doesn't reflect the WRAPPED property's real writable/configurable
	// state at all, so JSObject.setProperty's [[Set]]-style writability
	// check (which trusts this descriptor) never sees a real non-writable
	// property and silently falls through to setOwnProperty's
	// CreateDataProperty-style "overwrite if configurable" semantics
	// instead of rejecting the write (confirmed via
	// push/length-near-integer-limit-set-failure.js: pushing into an index
	// pre-defined as {writable:false,configurable:true} must throw
	// TypeError, not silently overwrite). Delegate straight to the wrapped
	// object's own real descriptor instead.
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(long index) {
		if(index>=0) {
			return object.getOwnPropertyDescriptor(Long.toString(index));
		}
		return null;
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(String member) {
		if("length".equals(member)) {
			// Only synthesize a descriptor when the wrapped object
			// genuinely HAS an own "length" (e.g. a plain array-like
			// receiver such as `{0:'a',1:'b',length:2}` passed to
			// Array.prototype.slice.call(...), whose real "length" this
			// reflects with a genuine-Array-like writable/non-enumerable/
			// non-configurable shape) - a species-constructed result whose
			// custom constructor never touches "length" at all must NOT
			// report one as present (confirmed via
			// flatMap/this-value-ctor-object-species-custom-ctor.js:
			// `Object.prototype.hasOwnProperty.call(actual,'length')` must
			// be false when the custom ctor is a no-op).
			return object.getOwnPropertyDescriptor("length")!=null ? PropertyDescriptor.DESC_HIDDEN_PROP : null;
		}
		return object.getOwnPropertyDescriptor(member);
	}

	// Whether `object`'s OWN prototype chain (Object.prototype, typically -
	// NOT this wrapper's own getPrototype(), which is Array.prototype and
	// irrelevant here) has ever had an indexed property defined on it. When
	// false, an index absent from `object`'s OWN properties is guaranteed
	// to resolve to "absent" without a per-index prototype-chain check -
	// the precondition that makes the sparse arrayForEachWhile/Reverse
	// fast path below safe. Mirrors JSArrayImpl's identical-purpose helper.
	private boolean prototypeChainMayHaveNumberProp() {
		Object p = object.getPrototype();
		while(p instanceof JSObjectImpl bp) {
			if(bp.mayHaveNumberProp()) {
				return true;
			}
			p = bp.getPrototype();
		}
		return p!=null;
	}
	private List<Long> ownIndexKeysInRange(long startIncl, long endExcl) {
		List<Long> keys = new ArrayList<>();
		Iterator<Map.Entry<Object,Object>> it = object.ownPropertyEntries(true,false,false);
		while(it.hasNext()) {
			Object k = it.next().getKey();
			if(k instanceof String s) {
				long idx = parseUncappedIndex(s);
				if(idx>=startIncl && idx<endExcl) {
					keys.add(idx);
				}
			}
		}
		Collections.sort(keys);
		return keys;
	}
	// The shared JSArray.arrayForEachWhile/Reverse defaults truncate `len`
	// to `int` as a deliberate safety valve against a catastrophic
	// one-index-at-a-time linear walk over a near-2^32/2^53 declared
	// length (see JSArray.java's own comment) - correct for avoiding a
	// hang, but WRONG for correctness: it silently makes the walk visit
	// ZERO indices whenever `len` doesn't fit in an int (e.g. `(int)
	// (2**53-1)` is -1), so every/some/indexOf/lastIndexOf/includes/
	// findLast/findLastIndex/reduceRight etc. on a huge-length array-like
	// object return the vacuous "not found"/"true" result instead of
	// actually checking any element (confirmed via
	// every/15.4.4.16-3-14.js: `{0:9,length:"Infinity"}` must invoke the
	// callback at index 0 and return false, but returned true - the
	// callback was never called at all). Overridden here to walk ONLY
	// `object`'s real own index-shaped properties (there are only ever a
	// handful in practice, regardless of the declared `len`) instead of
	// the full range - safe per the prototypeChainMayHaveNumberProp()
	// precondition above, and correct for every huge-length test262 file
	// in this area, which all place their meaningful content at a small,
	// fixed number of real own properties rather than actually requiring
	// O(len) work.
	//
	// ONLY taken when `len` exceeds int range (where the plain walk below
	// is already broken by truncation, so there is nothing correct to
	// preserve) - NOT for ordinary small lengths, even when otherwise
	// eligible. A snapshot-then-walk of `object`'s own keys cannot observe
	// a property added AS A SIDE EFFECT of an earlier index's own
	// getter/callback - a real, spec-mandated behavior for ordinary-sized
	// arrays (confirmed via forEach/15.4.4.18-7-b-4.js: accessing index 0
	// defines index 1 via Object.defineProperty, and forEach must still
	// visit the newly-added index 1 within the original length bound; the
	// snapshot approach silently skips it, a genuine regression caught
	// re-running the full built-ins/Array suite after adding this path).
	@Override
	public boolean arrayForEachWhile(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue, long len) {
		if(len>Integer.MAX_VALUE) {
			// emptyItems=true (findLast/findLastIndex) can't skip holes -
			// every conceptual index must be visited - but a genuine `long`
			// walk is safe here because every huge-length test262 file in
			// this area (confirmed empirically, not assumed) places its
			// early-termination condition within a handful of iterations of
			// `start`, same reasoning as the sparse path below just without
			// the ability to skip holes (findLast/maximum-index.js: a
			// single-property object whose predicate returns true
			// unconditionally, terminating on the very first index checked).
			if(emptyItems) {
				for(long i=Math.max(0,start); i<len; i++) {
					if(!c.process(i,getProperty(i,emptyValue))) {
						return false;
					}
				}
				return true;
			}
			if(!prototypeChainMayHaveNumberProp()) {
				for(long k : ownIndexKeysInRange(Math.max(0,start), len)) {
					Object v = arrayGet(k, RuntimeUtil.NOT_AVAILABLE);
					if(v!=RuntimeUtil.NOT_AVAILABLE) {
						if(!c.process(k,v)) {
							return false;
						}
					}
				}
				return true;
			}
			// Prototype chain has an indexed property - can't safely skip
			// holes via the sparse path, fall back to a genuine long walk.
			for(long i=Math.max(0,start); i<len; i++) {
				Object v = getProperty(i,RuntimeUtil.NOT_AVAILABLE);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					if(!c.process(i,v)) {
						return false;
					}
				}
			}
			return true;
		}
		// Duplicated from JSArray's own default (not reachable via
		// `JSArray.super.*` here - JSArrayInternal already extends JSArray,
		// making it a "redundant" superinterface javac refuses a default
		// super call through). Keep in sync with JSArray.arrayForEachWhile.
		// Only reached for ordinary (int-range) lengths, where this is
		// already correct (including observing properties added mid-
		// iteration - see the snapshot caveat above).
		int size = (int)len;
		if(emptyItems) {
			for(int i=Math.max(0,(int)start); i<size; i++) {
				if(!c.process(i,getProperty(i,emptyValue))) {
					return false;
				}
			}
		} else {
			for(int i=Math.max(0,(int)start); i<size; i++) {
				Object v = getProperty(i,RuntimeUtil.NOT_AVAILABLE);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					if(!c.process(i,v)) {
						return false;
					}
				}
			}
		}
		return true;
	}
	// See arrayForEachWhile's matching comment above (both the snapshot
	// caveat and the len>Integer.MAX_VALUE gate that avoids it for
	// ordinary-sized arrays). `start` here means "the exact first index to
	// visit, inclusive, already clamped to len-1 by the caller" (e.g.
	// lastIndexOf's fromIndex) - NOT "one past" (that framing only applies
	// to the shared default's own `int`-range walk when start>=len).
	// Visits real own keys in [0, min(start,len-1)] descending.
	@Override
	public boolean arrayForEachWhileReverse(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue, long len) {
		if(len>Integer.MAX_VALUE) {
			long lastIncl = Math.min(start,len-1);
			if(emptyItems) {
				// See arrayForEachWhile's matching comment - genuine `long`
				// walk, safe per the same empirically-confirmed pattern
				// (findLastIndex/maximum-index.js's sibling).
				for(long i=lastIncl; i>=0; i--) {
					if(!c.process(i,getProperty(i,emptyValue))) {
						return false;
					}
				}
				return true;
			}
			if(!prototypeChainMayHaveNumberProp()) {
				if(lastIncl>=0) {
					List<Long> keys = ownIndexKeysInRange(0, lastIncl+1);
					for(int i=keys.size()-1; i>=0; i--) {
						long k = keys.get(i);
						Object v = arrayGet(k, RuntimeUtil.NOT_AVAILABLE);
						if(v!=RuntimeUtil.NOT_AVAILABLE) {
							if(!c.process(k,v)) {
								return false;
							}
						}
					}
				}
				return true;
			}
			for(long i=lastIncl; i>=0; i--) {
				Object v = getProperty(i,RuntimeUtil.NOT_AVAILABLE);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					if(!c.process(i,v)) {
						return false;
					}
				}
			}
			return true;
		}
		// Duplicated from JSArray's own default - see arrayForEachWhile's
		// matching comment above for why `JSArray.super.*` isn't usable
		// here. Keep in sync with JSArray.arrayForEachWhileReverse. Only
		// reached for ordinary (int-range) lengths.
		int size = (int)len;
		if(emptyItems) {
			for(int i=Math.min(size-1,(int)start); i>=0; i--) {
				if(!c.process(i,getProperty(i,emptyValue))) {
					return false;
				}
			}
		} else {
			for(int i=Math.min(size-1,(int)start); i>=0; i--) {
				Object v = getProperty(i,RuntimeUtil.NOT_AVAILABLE);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					if(!c.process(i,v)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	@Override
	public boolean arraySetLength(long size, DESC_CHECK check) {
		return object.setProperty("length",size, null, check);
	}
	@Override
	public JSArray arrayAdd(Object value, DESC_CHECK check) {
		long size = arrayLength();
		// 2^53-1 (not JSArray.MAX_ARRAY_SIZE/2^31-1), matching ToLength's
		// real clamp ceiling - see arrayLength().
		if(size==9007199254740991L) {
			throw RuntimeUtil.typeError("Invalid array length {0}",size+1);
		}
		object.setProperty(Long.toString(size), value, null, check);
		object.setProperty("length", size+1, null, check);
		return this;
	}
	@Override
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check) {
		long size = arrayLength();
		if(size==9007199254740991L) {
			throw RuntimeUtil.typeError("Invalid array length {0}",size+1);
		}
		if(index>size) {
			throw new IllegalStateException(StringFormat.format("Ibvalid index in JSArray {0}", getClass()));
		}
		// We don't check the max array size on purpose
		// Must shift the element AT `index` itself too (i>=index, not
		// i>index) - the old loop bound left the pre-existing value at
		// `index` un-shifted, so it was simply discarded by the
		// object.setProperty(index,...) below instead of moving to
		// index+1 (confirmed via splice/S15.4.4.12_A2_T2.js: inserting 2
		// elements at position 0 of a 2-element array-like must preserve
		// BOTH original elements at positions 2 and 3, not lose the first
		// one).
		// Same hole-propagation rule as arrayRemove: a source index that's
		// a genuine hole must leave the destination absent too, not become
		// an own property (confirmed via
		// splice/length-near-integer-limit-grow-array.js).
		for(long i=size-1; i>=index; i--) {
			Object val = object.getProperty(Long.toString(i),RuntimeUtil.NOT_AVAILABLE);
			if(val!=RuntimeUtil.NOT_AVAILABLE) {
				object.setProperty(Long.toString(i+1),val, null, check);
			} else {
				object.deleteProperty(Long.toString(i+1), check);
			}
		}
		object.setProperty(Long.toString(index), value, null, check);
		object.setProperty("length", size+1, null, check);
		return this;
	}
	@Override
	public boolean arraySet(long index, Object value, DESC_CHECK check) {
		object.setProperty(Long.toString(index), value, null,check);
		return true;
	}
	// See JSArray.arrayDefineDataProperty's doc comment - a genuine
	// [[DefineOwnProperty]] (CreateDataPropertyOrThrow), unlike arraySet's
	// plain [[Set]] above - same rationale as the setOwnProperty(long,...)
	// override below (delegates to the wrapped object's own real
	// setOwnProperty, which already correctly rejects redefining an
	// existing non-configurable property instead of silently succeeding
	// the way [[Set]] would for one that's merely writable - confirmed via
	// splice/target-array-with-non-configurable-property.js/
	// -non-writable-property.js).
	@Override
	public boolean arrayDefineDataProperty(long index, Object value, DESC_CHECK check) {
		return object.setOwnProperty(Long.toString(index), value, PropertyDescriptor.DESC_PROP_ARRAYINDEX, check, object);
	}
	// JSArray's own default setOwnProperty(long,...) assumes a genuine
	// Array's in-memory dense/sparse index representation (indexAccessors
	// map etc.) - entirely inappropriate here, since an index on a WRAPPED
	// plain object is just a normal string-keyed property with its OWN
	// already-correct descriptor tracking. Delegating straight to the
	// wrapped object's real setOwnProperty gives genuine
	// CreateDataProperty/[[DefineOwnProperty]] semantics (overwrites a
	// non-writable-but-configurable existing property; rejects a
	// non-configurable one) instead of the array-index fast path's
	// [[Set]]-flavored arraySet, which doesn't understand per-property
	// configurability at all for a non-JSArrayImpl backing (confirmed via
	// filter/target-array-with-non-configurable-property.js and its
	// map/slice/splice/flat/flatMap siblings).
	@Override
	public boolean setOwnProperty(long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return object.setOwnProperty(Long.toString(index), value, desc, check, receiver);
	}
	// JSObject's own default 4-arg setOwnProperty(long,value,desc,check)
	// (no explicit receiver - used throughout BuiltinArrayPrototype, e.g.
	// unshift's `_this.setOwnProperty(to,v,desc,check)`) converts the
	// index to a String and calls the STRING-keyed 5-arg overload, NOT the
	// long-keyed one overridden just above - and since THAT String-keyed
	// overload was never overridden here either, it fell through to
	// JSObjectDelegate's default, which writes into THIS wrapper's own
	// separate, unrelated `members` bag (see JSBaseArray.getMembers) -
	// never touching the wrapped `object` at all. Confirmed via
	// unshift/length-near-integer-limit.js: a mid-shift write silently
	// vanished (read back as undefined from the real `arrayLike` object)
	// even though the write call itself didn't throw. Overridden here so
	// EVERY entry point (long-keyed, String-keyed, with or without an
	// explicit receiver) consistently reaches the real wrapped object.
	@Override
	public boolean setOwnProperty(String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return object.setOwnProperty(member, value, desc, check, receiver);
	}
	// A genuine delete (the property disappears entirely, letting the
	// prototype chain show through again for subsequent reads) - NOT a
	// set-to-undefined (which leaves a real own property behind, still
	// shadowing the prototype) and NOT a length-shrink (that's
	// arrayRemove's job; a caller doing an explicit shift-down, e.g.
	// shift()/unshift(), calls arraySetLength itself afterward, so this
	// silently double-decrementing length on top of that was a second,
	// independent bug). Also respects the passed `check` (was hardcoded
	// to CHECK, silently swallowing STRICT's throw-on-read-only-property
	// requirement) - confirmed via shift/S15.4.4.9_A4_T2.js (own property
	// must actually vanish so the inherited value shows through) and
	// unshift/read-only-property.js (must throw for a non-writable
	// existing property).
	@Override
	public boolean arrayDelete(long index, DESC_CHECK check) {
		if(index>=0 && index<arrayLength()) {
			object.deleteProperty(Long.toString(index), check);
			return true;
		}
		return false;
	}
	// Same rationale as the setOwnProperty(String,...) override above -
	// JSObjectDelegate's default deleteProperty(String,...) targets this
	// wrapper's own unrelated `members` bag, not the real wrapped object.
	@Override
	public boolean deleteProperty(String key, DESC_CHECK check) {
		return object.deleteProperty(key, check);
	}
	@Override
	public boolean arrayRemove(long index, DESC_CHECK check) {
		if(index>=0 && index<arrayLength()) {
			long size = arrayLength();
			// A source index that's a genuine hole (absent, not just
			// undefined) must propagate as an ABSENCE at the destination
			// too - not become an own property holding whatever
			// getProperty's Java-null default happens to be (confirmed via
			// splice/length-exceeding-integer-limit-shrink-array.js: a
			// shifted-away hole must leave `"key" in arrayLike` false, not
			// true). Same fix shape as copyWithin's matching hole-handling.
			for(long i=index; i<size-1; i++) {
				Object v = object.getProperty(Long.toString(i+1), RuntimeUtil.NOT_AVAILABLE);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					object.setProperty(Long.toString(i), v, null, DESC_CHECK.CHECK);
				} else {
					object.deleteProperty(Long.toString(i), DESC_CHECK.CHECK);
				}
			}
			object.deleteProperty(Long.toString(size-1),DESC_CHECK.CHECK);
			object.setProperty("length", size-1, null, DESC_CHECK.CHECK);
			return true;
		}
		return false;
	}
}
