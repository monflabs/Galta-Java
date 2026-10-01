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
package org.monflabs.galtajs.jsonfactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.JSArrayInternal;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseCallableObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayPrototype;
import org.monflabs.galtajs.util.SparseList;
import org.monflabs.util.iterators.LongIterator;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.JsonArray.EntryConsumerWhile;
import org.monflabs.json.java.JsonArrayAsArrayList;


/**
 * Json Array implemented as an ArrayLisy.
 */
@SuppressWarnings("serial")
public abstract class JSArrayImpl extends JsonArrayAsArrayList implements JSArrayInternal  {

	// JavaScript data
	private JSEnvironment env;
	private JSObjectImpl members;
	private SparseList<Object> sparseArray;
	// Independent of a full freeze/seal - set via
	// Object.defineProperty(arr,"length",{writable:false}). See
	// JSArray.isLengthWritable()/setLengthWritable().
	private boolean lengthWritable = true;
	// Lazily-created per-index accessor (getter/setter) descriptors installed
	// via Object.defineProperty - see JSArray.arrayGetAccessor/arraySetAccessor.
	private Map<Long,PropertyDescriptor> indexAccessors;

	// Three-tier storage (mirrors V8's element kinds):
	//
	//   DENSE  — no holes: ArrayList[i] = JS value at (firstItem + i).
	//   HOLEY  — interior holes: same layout, but HOLE sentinel marks absent slots.
	//   SPARSE — SparseList, used only when gaps exceed MAX_HOLEY_GAP.
	//
	// arrayLength() = max(lastItem, firstItem + super.size())
	// firstItem > 0 means there are leading holes [0..firstItem-1] (no allocation).
	// lastItem > firstItem + super.size() means trailing holes (no allocation).
	private long firstItem; // JS index of ArrayList[0]; 0 by default
	private long lastItem;  // explicit JS length; 0 = unset (use firstItem + super.size())

	// Interior-hole sentinel — unforgeable; never escapes to callers.
	private static final Object HOLE = new Object();
	// Max gap to fill with HOLE before falling back to SparseList.
	private static final int MAX_HOLEY_GAP = 1024;

	// Only to be used by JsonFactory!
	protected JSArrayImpl(JSEnvironment env) {
		this.env = env;
	}
	protected JSArrayImpl(JSEnvironment env, int initialCapacity) {
		super(initialCapacity);
		this.env = env;
	}
	protected JSArrayImpl(JSEnvironment env, SparseList<Object> sparseArray) {
		super(0);
		this.env = env;
		this.sparseArray = sparseArray;
	}
	
	@Override
	public JSEnvironment getEnvironment() {
		return env;
	}


	@Override
	public GaltaJsJsonFactory factory() {
		return getEnvironment().getJsonFactory();
	}

	//
	// Object Prototype
	//
	@Override
	public Object getPrototype() {
		if(members!=null) {
			return members.getPrototype();
		}
		return BuiltinArrayPrototype.get(getEnvironment());
	}
	@Override
	public boolean setPrototype(Object prototype) {
		return getMembers(true).setPrototype(prototype);
	}

	@Override
	public JSObjectInternal getMembers(boolean autoCreate) {
		if(members==null && autoCreate) {
			members = JSObject.createWithPrototype(env,BuiltinArrayPrototype.get(getEnvironment()));
		}
		return members;
	}

	@Override
	public boolean isSealed() {
		if(members!=null) {
			return members.isSealed();
		}
		return false;
	}
	@Override
	public boolean isFrozen() {
		if(members!=null) {
			return members.isFrozen();
		}
		return false;
	}
	@Override
	public boolean isExtensible() {
		if(members!=null) {
			return members.isExtensible();
		}
		return true;
	}
	@Override
	public void seal() {
		getMembers(true).seal();
	}
	@Override
	public void freeze() {
		getMembers(true).freeze();
	}
	@Override
	public boolean preventExtensions() {
		return getMembers(true).preventExtensions();
	}

	protected boolean canAddEntry() {
		if(members!=null) {
			return members.canAddEntry();
		}
		return true;
	}
	protected boolean canUpdateEntry() {
		if(members!=null) {
			return members.canUpdateEntry();
		}
		return true;
	}
	protected boolean canRemoveEntry() {
		if(members!=null) {
			return members.canRemoveEntry();
		}
		return true;
	}



	//
	// Sparse Array support
	//

	@Override
	public SparseList<Object> makeSparse() {
		if(sparseArray==null) {
			long jsLength = arrayLength();
			SparseList<Object> a = new SparseList<>();
			int sz = super.size();
			for(int i=0; i<sz; i++) {
				Object v = _get(i);
				if(v != HOLE) a.put(firstItem + i, v); // skip interior holes
			}
			a.setSize(jsLength);
			sparseArray = a;
			firstItem = 0;
			lastItem = 0;
			super.clear();
		}
		return sparseArray;
	}
	@Override
	public SparseList<Object> getSparseList() {
		return sparseArray;
	}

	// Whether any prototype in this array's chain has ever had an array-
	// index-shaped property defined on it. When false, a hole in this
	// array's OWN storage is guaranteed to resolve to "absent" without
	// walking the prototype chain per index - the precondition that makes
	// arrayForEachWhile/Reverse's sparse fast path below safe. Reuses
	// StringPropertyMap's existing mayHaveNumberProp() bookkeeping
	// (already maintained for the unrelated numeric-setter-walk
	// optimization in JSAccessor/JSObject) rather than adding new state.
	private boolean prototypeChainMayHaveNumberProp() {
		Object p = getPrototype();
		while(p instanceof JSObjectImpl bp) {
			if(bp.mayHaveNumberProp()) {
				return true;
			}
			p = bp.getPrototype();
		}
		// A prototype of some other, unrecognized shape (not a plain
		// JSObjectImpl) - can't rule it out, so be conservative.
		return p!=null;
	}

	// Spec's hole-skipping iteration (indexOf/lastIndexOf/includes/forEach/
	// every/some/reduce/etc, `emptyItems=false`) genuinely requires a
	// linear index-by-index walk in general, since ANY index in [start,len)
	// could resolve to a value via the prototype chain even if this
	// array's own storage has a hole there - that per-index HasProperty
	// check is exactly what made a huge (near 2**32/2**53) declared length
	// combined with a genuinely sparse array catastrophically slow
	// (EMPIRICALLY reproduced as a multi-minute+ hang earlier this
	// session, e.g. indexOf/15.4.4.14-5-12.js's `arr[2**32-2]=true`). But
	// when this array IS sparse-backed AND its prototype chain provably
	// has no indexed properties at all, a hole can ONLY mean "absent" -
	// safe to walk just the SparseList's own real entries (already an
	// O(actual element count) structure, via binary-search-positioned
	// forEachWhile) instead of every integer in the range.
	// Same sparse fast-path precondition as arrayForEachWhile just below -
	// see its comment for the full reasoning (a huge declared length
	// combined with sparse-backed storage makes the default hole-skipping
	// walk in JSArray.nonHoleIndices() O(length) instead of O(actual
	// element count), the same catastrophic-hang class of bug). Backs
	// Object.keys/values/entries, for-in and spread over arrays via
	// ownPropertyEntries().
	@Override
	public LongIterator nonHoleIndices() {
		long length = arrayLength();
		// (Not with index accessors: an accessor-only index is not stored in sparseArray)
		if(sparseArray!=null && indexAccessors==null && length==sparseArray.size() && !prototypeChainMayHaveNumberProp()) {
			return sparseArray.keys(false);
		}
		// Duplicated from JSArray's own default (not reachable via
		// JSArray.super.* here - see arrayForEachWhile's matching comment
		// below for why). Keep in sync with JSArray.nonHoleIndices.
		final long len = length;
		return new LongIterator() {
			private long next = -1;
			private boolean primed;
			private void prime() {
				if(!primed) {
					next++;
					while(next<len && !arrayHas(next)) {
						next++;
					}
					primed = true;
				}
			}
			@Override
			public boolean hasNext() {
				prime();
				return next<len;
			}
			@Override
			public long next() {
				prime();
				primed = false;
				return next;
			}
		};
	}
	@Override
	public boolean arrayForEachWhile(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue, long len) {
		if(!emptyItems && sparseArray!=null && indexAccessors==null && len==sparseArray.size() && !prototypeChainMayHaveNumberProp()) {
			return sparseArray.forEachWhile(c, start, false, emptyValue);
		}
		// Duplicated from JSArray's own default (not reachable via
		// `JSArray.super.*` here - JSArrayInternal already extends JSArray,
		// making it a "redundant" superinterface javac refuses a default
		// super call through). Keep in sync with JSArray.arrayForEachWhile.
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
	@Override
	public boolean arrayForEachWhileReverse(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue, long len) {
		// Also requires start==len (a full reverse walk, e.g. reduceRight):
		// SparseList.forEachWhileReverse's own `start` means "the exact
		// first index to visit" (inclusive), whereas JSArray's convention
		// is "one past the first index to visit" - the two conventions
		// only coincide at start==len (SparseList special-cases exactly
		// that value to mean "start from the last real index"). A partial
		// reverse walk (e.g. lastIndexOf's fromIndex+1) falls through to
		// the safe default instead of risking an off-by-one.
		if(!emptyItems && start==len && sparseArray!=null && len==sparseArray.size() && !prototypeChainMayHaveNumberProp()) {
			return sparseArray.forEachWhileReverse(c, start, false, emptyValue);
		}
		// Duplicated from JSArray's own default - see arrayForEachWhile's
		// matching comment above for why `JSArray.super.*` isn't usable
		// here. Keep in sync with JSArray.arrayForEachWhileReverse.
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


	//
	// JS API support
	//

	@Override
	public PropertyDescriptor arrayGetAccessor(long index) {
		return indexAccessors==null ? null : indexAccessors.get(index);
	}
	@Override
	public void arraySetAccessor(long index, PropertyDescriptor desc) {
		if(desc==null) {
			if(indexAccessors!=null) {
				indexAccessors.remove(index);
			}
			return;
		}
		if(indexAccessors==null) {
			indexAccessors = new HashMap<>();
		}
		indexAccessors.put(index,desc);
	}

	@Override
	public boolean arrayHas(long index) {
		if(indexAccessors!=null && indexAccessors.containsKey(index)) {
			return true;
		}
		if(sparseArray!=null) {
			return sparseArray.has(index);
		}
		long listIndex = index - firstItem;
		if(listIndex>=0 && listIndex<super.size()) {
			return _get((int)listIndex) != HOLE;
		}
		return false; // leading or trailing hole
	}
	@Override
	public Object arrayGet(long index, Object defaultValue) {
		if(index>=0 && index<arrayLength()) {
			if(sparseArray!=null) {
				return sparseArray.getOrDefault(index,defaultValue);
			}
			long listIndex = index - firstItem;
			if(listIndex>=0 && listIndex<super.size()) {
				Object v = _get((int)listIndex);
				return v==HOLE ? defaultValue : v;
			}
			return defaultValue; // leading or trailing hole
		}
		return defaultValue;
	}
	// ArrayList.toArray() (inherited via JsonArrayAsArrayList) would otherwise
	// shadow JSArray's default toArray() - since a concrete class method always
	// wins over an inherited interface default with the same signature - and
	// leak the raw internal HOLE sentinel for sparse-array holes instead of
	// translating them to `undefined`. This surfaced as Reflect.apply/construct,
	// Function.prototype.apply, super(...spread), and (...spread) call argument
	// conversion all passing a raw Java Object (the HOLE marker) through to
	// callees instead of `undefined` for an elided array element.
	@Override
	public Object[] toArray() {
		return JSArrayInternal.super.toArray();
	}
	@Override
	public boolean arraySet(long index, Object value, DESC_CHECK check) {
		// index<size() unconditionally calls size() (the `int`-returning,
		// java.util.List-contract override below), which deliberately
		// throws once arrayLength() exceeds Integer.MAX_VALUE - correct
		// for that method's own real contract, but wrong here: this is
		// just choosing between canUpdateEntry()/canAddEntry(), for which
		// the `long`-returning arrayLength() works equally well and never
		// throws (confirmed via S15.4.5.2_A1_T1.js: assigning to a valid
		// index near 2^32-2 on an array whose length is ALREADY past
		// 2^31-1 must not throw just to decide it's an "add").
		if(index<arrayLength() ? canUpdateEntry() : canAddEntry()) {
			if(sparseArray==null) {
				if(index>=Integer.MAX_VALUE) {
					makeSparse();
				} else if(super.size()==0) {
					// No data yet — anchor the dense block at this index.
					// Preserve the JS length implied by the old firstItem / lastItem.
					long prevLength = arrayLength();
					firstItem = index;
					if(lastItem < prevLength) lastItem = prevLength;
				} else if(index==firstItem-1) {
					// Adjacent prepend: insert a slot at position 0 and decrement firstItem.
					// arrayLength() = max(lastItem, firstItem+size) is unchanged because
					// (firstItem-1) + (size+1) == firstItem + size.
					_add(0, value);
					firstItem--;
					return true;
				} else if(index < firstItem) {
					// Leading gap (index < firstItem-1; adjacent case handled above).
					long gap = firstItem - index - 1;
					if(gap > MAX_HOLEY_GAP) {
						makeSparse();
					} else {
						// Prepend [value, HOLE x gap] then the existing dense block.
						long prevLength = arrayLength();
						int gapSize = (int)gap;
						Object[] prefix = new Object[gapSize + 1];
						prefix[0] = value;
						Arrays.fill(prefix, 1, gapSize + 1, HOLE);
						super.addAll(0, Arrays.asList(prefix));
						firstItem = index;
						if(lastItem < prevLength) lastItem = prevLength;
						return true;
					}
				} else if(index > firstItem + super.size()) {
					// Trailing gap — stay holey if small, else go sparse.
					long gap = index - (firstItem + super.size());
					if(gap > MAX_HOLEY_GAP) {
						makeSparse();
					}
					// else: fall through; the while loop below fills the gap with HOLE.
				}
			}
			if(sparseArray!=null) {
				sparseArray.put(index,value);
				return true;
			}
			int listIndex = (int)(index - firstItem);
			while(listIndex>=super.size()) {
				super.add(HOLE); // fill any gap with HOLE sentinel (holey mode)
			}
			_set(listIndex, value);
			return true;
		}
		// OK, can't update - emit a error on strict mode
		if(check!=DESC_CHECK.NONE) {
			if(RuntimeUtil.isStrictCheck(check)) {
	    		throw RuntimeUtil.typeError("Property {0} is not writable",index);
			}
		}
		return false;
	}
	// See JSArray.arrayDefineDataProperty's doc comment - a genuine
	// [[DefineOwnProperty]] (CreateDataPropertyOrThrow), unlike arraySet's
	// plain [[Set]] above (which preserves an EXISTING property's own
	// writable/enumerable/configurable attributes, only updating the
	// value - wrong for CreateDataPropertyOrThrow, which must overwrite
	// them to {writable:true,enumerable:true,configurable:true} whenever
	// the existing property is configurable, and reject the redefinition
	// entirely otherwise). JSArray's own default setOwnProperty(long,...)
	// (which this class does NOT override) already implements exactly
	// that genuine per-index [[DefineOwnProperty]] algorithm - confirmed
	// via splice/target-array-with-non-writable-property.js/
	// -non-configurable-property.js, both of which need a species-created
	// real Array's existing index property to be genuinely redefined, not
	// merely value-assigned.
	@Override
	public boolean arrayDefineDataProperty(long index, Object value, DESC_CHECK check) {
		return setOwnProperty(index, value, PropertyDescriptor.DESC_PROP_ARRAYINDEX, check, this);
	}

	//
	// Array specific
	//

	@Override
	public long arrayLength() {
		if(sparseArray!=null) {
			return sparseArray.size();
		}
		return Math.max(lastItem, firstItem + super.size());
	}
	@Override
	public boolean isLengthWritable() {
		return lengthWritable;
	}
	@Override
	public void setLengthWritable(boolean writable) {
		lengthWritable = writable;
	}
	@Override
	public boolean arraySetLength(long size, DESC_CHECK check) {
		// Spec ArraySetLength step: "If newLen > 2^32-1, throw a RangeError
		// exception" - this low-level path (push/pop/shift/unshift's own
		// trailing Set(O,"length",len,true), bypassing the String-keyed
		// setOwnProperty("length",...) overload's own equivalent check)
		// otherwise silently fell through to whatever canAddEntry()/the
		// underlying SparseList happened to reject at Integer.MAX_VALUE
		// (2^31-1, a Java collection-size artifact, not the spec's actual
		// 2^32-1 array-length bound) - confirmed via
		// push/S15.4.4.7_A3.js: `[].length=4294967295; [].push("x")` must
		// throw RangeError specifically (not some other TypeError) once
		// the resulting length would be 4294967296.
		if(size>4294967295L) {
			if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.rangeError("Invalid array length {0}",size);
			}
			return false;
		}
		if(!lengthWritable) {
			// A genuine [[Set]] (array.length = N, or push/pop/shift/unshift's
			// trailing Set(O,"length",len,true)) always rejects once "length"
			// is non-writable - even a same-value write (confirmed via
			// pop/set-length-zero-array-length-is-non-writable.js).
			if(check!=DESC_CHECK.NONE) {
				if(RuntimeUtil.isStrictCheck(check)) {
					throw RuntimeUtil.typeError("Cannot assign to read only property 'length'");
				}
			}
			return false;
		}
		if(canAddEntry()) {
			boolean shrinkStoppedEarly = false;
			if(indexAccessors!=null && size<arrayLength()) {
				// Spec's ArraySetLength deletion loop scans indices
				// DESCENDING from oldLen-1 down to the requested new
				// length, and STOPS at the first (i.e. highest) one that
				// can't be deleted (non-configurable) - the array's actual
				// final length becomes that index+1, not the originally
				// requested size, and any index at-or-below the stopping
				// point (even a configurable one that would otherwise be
				// deletable) is left untouched since the loop never
				// reaches it (confirmed via
				// reduce/15.4.4.21-9-b-16.js/-29.js/-9-c-ii-4-s.js and
				// their reduceRight siblings: a non-configurable getter
				// installed via Object.defineProperty must still be
				// observed by reduce/reduceRight after a length shrink
				// triggered mid-iteration). This engine only tracks
				// non-configurable ARRAY-INDEX properties via
				// indexAccessors (an accessor descriptor) - an ordinary
				// dense-list value has no way to be non-configurable here,
				// so scanning just this map is sufficient.
				long effectiveSize = size;
				for(Map.Entry<Long,PropertyDescriptor> e : indexAccessors.entrySet()) {
					long k = e.getKey();
					if(k>=size && !e.getValue().isConfigurable() && k+1>effectiveSize) {
						effectiveSize = k+1;
					}
				}
				final long finalEffectiveSize = effectiveSize;
				indexAccessors.keySet().removeIf(k -> k>=finalEffectiveSize);
				shrinkStoppedEarly = effectiveSize!=size;
				size = effectiveSize;
			}
			if(sparseArray!=null) {
				sparseArray.setSize(size);
			} else {
				long denseLen = firstItem + super.size();
				if(size > denseLen) {
					// Extend: record the explicit length, no SparseList needed
					lastItem = size;
				} else if(size <= firstItem) {
					// All data elements are beyond the new length; record the new JS length.
					super.clear();
					firstItem = 0;
					lastItem = size; // max(size, 0+0) == size, which is the correct JS length
				} else {
					int newListSize = (int)(size - firstItem);
					while(super.size() > newListSize) {
						_remove(super.size()-1);
					}
					lastItem = 0; // length is now exactly firstItem + super.size()
				}
			}
			if(shrinkStoppedEarly) {
				// Partial shrink: the [[Set]] as a whole is spec'd to fail
				// (return false) once ArraySetLength's deletion loop hits a
				// non-configurable property - only a Throw=true caller
				// (STRICT/CHECK) surfaces that as an actual TypeError; a
				// non-strict `arr.length = N` silently leaves length at the
				// higher effectiveSize computed above (already applied), but
				// the caller must still learn the [[Set]] didn't fully
				// succeed (e.g. Object.defineProperty's DefinePropertyOrThrow
				// throws unconditionally on a false return, independent of
				// this DESC_CHECK's own strictness).
				if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
					throw RuntimeUtil.typeError("Cannot delete property '{0}'",size-1);
				}
				return false;
			}
			return true;
		}
		if(check!=DESC_CHECK.NONE) {
    		if(RuntimeUtil.isStrictCheck(check)) {
        		throw RuntimeUtil.typeError("Property {0} cannot be added",size);
    		}
		}
		return false;
	}

	@Override
	public JSArray arrayAdd(Object value, DESC_CHECK check) {
		if(canAddEntry()) {
			if(sparseArray!=null) {
				sparseArray.add(value);
				return this;
			}
			if(lastItem > firstItem + super.size()) {
				// Trailing holes exist: fill with HOLE if gap is small, else go sparse.
				long gap = lastItem - (firstItem + super.size());
				if(gap > MAX_HOLEY_GAP) {
					makeSparse();
					sparseArray.add(value);
					return this;
				}
				while(firstItem + super.size() < lastItem) super.add(HOLE);
				lastItem = 0; // length now tracked by firstItem + super.size()
			}
			if(firstItem + (long)super.size() >= Integer.MAX_VALUE) {
				makeSparse();
				sparseArray.add(value);
				return this;
			}
			super.add(value);
			return this;
		}
		if(check!=DESC_CHECK.NONE) {
    		if(RuntimeUtil.isStrictCheck(check)) {
        		throw RuntimeUtil.typeError("Property {0} cannot be added",size());
    		}
		}
		return this;
	}
	@Override
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check) {
		if(canAddEntry()) {
			if(sparseArray==null) {
				if(firstItem+(long)super.size()>=Integer.MAX_VALUE) {
					makeSparse();
				} else if(super.size()==0) {
					// No data yet — anchor the dense block at this index.
					long prevLength = arrayLength();
					firstItem = index;
					if(lastItem < prevLength) lastItem = prevLength;
				} else if(index<firstItem || index>firstItem+super.size()) {
					makeSparse();
				}
			}
			if(sparseArray!=null) {
				sparseArray.add(index,value);
				return this;
			}
			_add((int)(index - firstItem), value);
			if(lastItem > 0) lastItem++;
			return this;
		}
		if(check!=DESC_CHECK.NONE) {
    		if(RuntimeUtil.isStrictCheck(check)) {
        		throw RuntimeUtil.typeError("Property {0} cannot be added",size());
    		}
		}
		return this;
	}
	@Override
	public boolean arrayDelete(long index, DESC_CHECK check) {
		if(canRemoveEntry()) {
			if(indexAccessors!=null) {
				indexAccessors.remove(index);
			}
			if(sparseArray!=null) {
				if(index>=0 && index<arrayLength()) {
					sparseArray.delete((int)index);
				}
				return true;
			}
			long listIndex = index - firstItem;
			if(listIndex>=0 && listIndex<super.size()) {
				if(_get((int)listIndex)==HOLE) {
					return true; // already a hole — no-op
				}
				if(listIndex==0) {
					// Deleting the first data element: shrink from the front.
					_remove(0);
					firstItem++;
					// Compact any additional leading HOLEs exposed by this removal.
					while(super.size()>0 && _get(0)==HOLE) {
						_remove(0);
						firstItem++;
					}
				} else if(listIndex==super.size()-1) {
					// Deleting the last data element: preserve JS length explicitly.
					long prevLength = arrayLength();
					_remove((int)listIndex);
					if(lastItem < prevLength) lastItem = prevLength;
					// Compact trailing HOLEs (their absence is now covered by lastItem).
					while(super.size()>0 && _get(super.size()-1)==HOLE) {
						_remove(super.size()-1);
					}
				} else {
					// Interior deletion: mark as HOLE and stay in holey mode.
					_set((int)listIndex, HOLE);
				}
			}
			// else: index is in a leading/trailing hole or out of range — nothing to do
			return true;
		}
		if(check!=DESC_CHECK.NONE) {
			if(RuntimeUtil.isStrictCheck(check)) {
	    		throw RuntimeUtil.typeError("Property {0} is not deletable",index);
			}
		}
		return false;
	}
	@Override
	public boolean arrayRemove(long index, DESC_CHECK check) {
		// Removing the array's last-index slot implicitly shrinks "length"
		// by one, same as arraySetLength - a non-writable "length" must
		// reject this the same way (confirmed via
		// pop/set-length-array-length-is-non-writable.js: the prototype-chain
		// Get() that pop performs before removing can itself make "length"
		// non-writable as a side effect, and the subsequent removal must
		// then fail).
		if(!lengthWritable) {
			if(check!=DESC_CHECK.NONE) {
				if(RuntimeUtil.isStrictCheck(check)) {
					throw RuntimeUtil.typeError("Cannot assign to read only property 'length'");
				}
			}
			return false;
		}
		if(canRemoveEntry()) {
			if(index>=0 && index<arrayLength()) {
				if(isSparse()) {
					sparseArray.remove((int)index);
				} else {
					long listIndex = index - firstItem;
					if(listIndex<0) {
						// Removing one of the leading holes: the stored
						// elements all move down by one index
						firstItem--;
					} else if(listIndex<super.size()) {
						_remove((int)listIndex);
					}
					// The JS length must shrink by one regardless of whether
					// a real stored element existed at this index - a
					// "virtual hole" beyond the dense list's actual
					// contents (e.g. `x=[]; x.length=1;`, never assigning
					// index 0) still occupies one unit of index space that
					// splice()/shift() are removing (confirmed via
					// splice/S15.4.4.12_A4_T3.js: removing the sole,
					// never-assigned element of a length-1 array must still
					// bring length back to 0).
					if(lastItem > 0) lastItem--;
				}
			}
			return true;
		}
		if(check!=DESC_CHECK.NONE) {
			if(RuntimeUtil.isStrictCheck(check)) {
	    		throw RuntimeUtil.typeError("Property {0} is not removeable",index);
			}
		}
		return false;
	}

	// Spec's precise sort algorithm (23.1.3.30 as clarified by tc39/ecma262#1585):
	// snapshot len and read every PRESENT element (skipping holes) into a
	// plain list BEFORE any comparisons or writes happen, sort just that
	// list, write the sorted values back to [0, itemCount), then delete
	// (not just leave stale) every index in [itemCount, len) so holes end
	// up at the end. A raw in-place list sort(c) - the previous
	// implementation - neither skips holes (sorting the internal HOLE
	// sentinel as if it were a real value, confirmed via bug_596_2.js) nor
	// relocates them to the end (confirmed via S15.4.4.11_A1.2_T1.js), and
	// re-reads/re-writes live during the sort rather than from a fixed
	// upfront snapshot (confirmed via the precise-getter-*/precise-setter-*
	// side-effect-during-sort tests, which need indices added by a getter
	// mid-sort to be excluded from the sort entirely).
	@Override
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check) {
		if(!canUpdateEntry()) {
			if(check!=DESC_CHECK.NONE) {
				if(RuntimeUtil.isStrictCheck(check)) {
		    		throw RuntimeUtil.typeError("Object is not writable");
				}
			}
			return;
		}
		long len = arrayLength();
		List<Object> items = new ArrayList<>();
		for(long i=0; i<len; i++) {
			Object v = getProperty(i,RuntimeUtil.NOT_AVAILABLE);
			if(v!=RuntimeUtil.NOT_AVAILABLE) {
				items.add(v);
			}
		}
		items.sort(c);
		int itemCount = items.size();
		for(int i=0; i<itemCount; i++) {
			setOwnProperty(i,items.get(i));
		}
		for(long i=itemCount; i<len; i++) {
			arrayDelete(i,check);
		}
	}

	// See JSArray.arraySort(c,check,receiver)'s doc comment - genuine
	// HasProperty+Get (collection) and Set (write-back), dispatched on the
	// RECEIVER, not this array's own storage directly - confirmed via
	// sort/precise-prototype-accessors.js: a hole this array leaves absent
	// but whose index has an accessor property on Object.prototype must be
	// read via that getter during collection, and written back via that
	// SAME accessor's setter (never becoming a real own property on this
	// array) rather than being written directly into storage. The trailing
	// delete step is unaffected ([[Delete]] has no receiver-forwarding
	// concept - it always targets the object's own property directly).
	@Override
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check, Object receiver) {
		if(!canUpdateEntry()) {
			if(check!=DESC_CHECK.NONE) {
				if(RuntimeUtil.isStrictCheck(check)) {
		    		throw RuntimeUtil.typeError("Object is not writable");
				}
			}
			return;
		}
		JSEnvironment env = getEnvironment();
		long len = arrayLength();
		List<Object> items = new ArrayList<>();
		for(long i=0; i<len; i++) {
			if(RuntimeUtil.hasProperty(env, receiver, i)) {
				items.add(RuntimeUtil.getProperty(env, receiver, i, RuntimeUtil.UNDEFINED));
			}
		}
		items.sort(c);
		int itemCount = items.size();
		for(int i=0; i<itemCount; i++) {
			RuntimeUtil.setProperty(env, receiver, (long)i, items.get(i), DESC_CHECK.STRICT);
		}
		for(long i=itemCount; i<len; i++) {
			arrayDelete(i,check);
		}
	}


	//
	// All list methods overridden
	//

	// The java.util.List view of the JavaScript array: index i is the JS
	// index, a hole reads as undefined. The raw ArrayList storage is offset
	// by firstItem and holds HOLE sentinels, so it must never be exposed.
	private List<Object> listView() {
		return new java.util.AbstractList<Object>() {
			@Override
			public Object get(int index) {
				return JSArrayImpl.this.get(index);
			}
			@Override
			public int size() {
				return JSArrayImpl.this.size();
			}
			@Override
			public Object set(int index, Object element) {
				return JSArrayImpl.this.set(index, element);
			}
			@Override
			public void add(int index, Object element) {
				JSArrayImpl.this.add(index, element);
			}
			@Override
			public Object remove(int index) {
				return JSArrayImpl.this.remove(index);
			}
		};
	}

	@Override
	public Object get(int index) {
		// Consult a tracked getter/setter accessor (installed via
		// Object.defineProperty on a numeric index - see arrayGetAccessor/
		// arraySetAccessor) before falling back to the raw backing storage.
		// Without this, callers that go through the plain java.util.List
		// contract (e.g. JsonStringifier.outArrayLiteral(), which has no
		// notion of GaltaJS's own JSAccessor machinery) silently bypass an
		// index's getter entirely - confirmed via
		// built-ins/JSON/stringify/value-array-abrupt.js's 3rd assertion (a
		// throwing getter on a real array's own numeric index must abort
		// JSON.stringify with that getter's exception, not read a stale/
		// default value). Mirrors JSArray.getOwnProperty(long,...)'s
		// identical accessor-first check.
		PropertyDescriptor accessorDesc = arrayGetAccessor(index);
		if(accessorDesc!=null && accessorDesc.isAccessor()) {
			BaseCallableObject getter = accessorDesc.getGetter();
			return getter!=null ? getter.get(this,index) : RuntimeUtil.UNDEFINED;
		}
		if(sparseArray!=null) {
			return sparseArray.getOrDefault(index,RuntimeUtil.UNDEFINED);
		}
		long listIndex = (long)index - firstItem;
		if(listIndex>=0 && listIndex<super.size()) {
			Object v = _get((int)listIndex);
			return v==HOLE ? RuntimeUtil.UNDEFINED : v;
		}
		return RuntimeUtil.UNDEFINED;
	}
    @Override
    public Object set(int index, Object value) {
		if(sparseArray!=null) {
			return sparseArray.set(index,value);
		}
		long listIndex = (long)index - firstItem;
		if(listIndex>=0 && listIndex<super.size()) {
			Object old = _set((int)listIndex, value);
			return old==HOLE ? RuntimeUtil.UNDEFINED : old;
		}
		if(index>=0 && index<arrayLength()) {
			// A leading or trailing hole
			arraySet(index, value, DESC_CHECK.NONE);
			return RuntimeUtil.UNDEFINED;
		}
		throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
    }
    @Override
    public boolean add(Object value) {
		if(sparseArray!=null) {
			return sparseArray.add(value);
		}
   		return super.add(value);
    }
    @Override
    public void add(int index, Object value) {
		if(sparseArray!=null) {
			sparseArray.add(index,value);
			return;
		}
		long listIndex = (long)index - firstItem;
		if(listIndex>=0 && listIndex<=super.size()) {
			_add((int)listIndex, value);
			if(lastItem > 0) lastItem++;
		} else {
			makeSparse();
			sparseArray.add(index, value);
		}
    }
    @Override
    public Object remove(int index) {
		if(sparseArray!=null) {
			return sparseArray.remove(index);
		}
		Object old = get(index);
		arrayRemove(index, DESC_CHECK.NONE);
		return old;
    }


	@Override
	public void forEach(Consumer<? super Object> action) {
		if(sparseArray!=null) {
			sparseArray.forEach(action);
			return;
		}
		listView().forEach(action);
	}

	@Override
	public int hashCode() {
		if(sparseArray!=null) {
			return sparseArray.hashCode();
		}
		return listView().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(sparseArray!=null) {
			return sparseArray.equals(obj);
		}
		return listView().equals(obj);
	}

	@Override
	public JSArrayImpl clone() {
		JSArrayImpl c = (JSArrayImpl)super.clone();
		if(sparseArray!=null) {
			c.sparseArray = sparseArray.clone();
		}
		// The clone must not share its non-index members or index accessors
		// with the original
		if(members!=null) {
			c.members = JSObject.createWithPrototype(env, members.getPrototype());
			c.members.copyOwnPropertiesFrom(members);
		}
		if(indexAccessors!=null) {
			c.indexAccessors = new HashMap<>(indexAccessors);
		}
		return c;
	}

	@Override
	public String toString() {
		if(sparseArray!=null) {
			return sparseArray.toString();
		}
		long jsLen = arrayLength();
		if(jsLen==0) return "[]";
		StringBuilder b = new StringBuilder(64);
		b.append('[');
		long emptyRun = firstItem; // leading holes [0..firstItem-1]
		int sz = super.size();
		for(int i=0; i<sz; i++) {
			Object v = _get(i);
			if(v==HOLE) {
				emptyRun++;
			} else {
				if(emptyRun>0) {
					b.append(b.length()==1 ? " <" : ", <");
					b.append(emptyRun);
					b.append(emptyRun==1 ? " empty item>" : " empty items>");
					emptyRun = 0;
				}
				b.append(b.length()==1 ? " " : ", ");
				b.append(JsonUtil.encodeValue(v));
			}
		}
		// trailing holes: lastItem extends beyond firstItem + sz
		emptyRun += Math.max(0L, lastItem - (firstItem + sz));
		if(emptyRun>0) {
			b.append(b.length()==1 ? " <" : ", <");
			b.append(emptyRun);
			b.append(emptyRun==1 ? " empty item>" : " empty items>");
		}
		b.append(b.length()==1 ? "]" : " ]");
		return b.toString();
	}

	@Override
	public int size() {
		if(sparseArray!=null) {
			long sz = sparseArray.size();
			if(sz>Integer.MAX_VALUE) {
				throw new IllegalStateException("Size is greater than max integer");
			}
			return (int)sz;
		}
		long sz = arrayLength();
		if(sz>Integer.MAX_VALUE) {
			throw new IllegalStateException("Size is greater than max integer");
		}
		return (int)sz;
	}

	@Override
	public boolean isEmpty() {
		if(sparseArray!=null) {
			return sparseArray.isEmpty();
		}
		return arrayLength()==0;
	}

	@Override
	public void clear() {
		if(sparseArray!=null) {
			sparseArray.clear();
			return;
		}
		super.clear();
		firstItem = 0;
		lastItem = 0;
	}

	@Override
	public boolean contains(Object o) {
		if(sparseArray!=null) {
			return sparseArray.contains(o);
		}
		return listView().contains(o);
	}

	@Override
	public Iterator<Object> iterator() {
		if(sparseArray!=null) {
			return sparseArray.iterator();
		}
		return listView().iterator();
	}

	@Override
	public boolean addAll(Collection<? extends Object> c) {
		if(sparseArray!=null) {
			return sparseArray.addAll(c);
		}
		return super.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection<? extends Object> c) {
		if(sparseArray!=null) {
			return sparseArray.addAll(index,c);
		}
		return super.addAll(index,c);
	}

	@Override
	public boolean remove(Object value) {
		if(sparseArray!=null) {
			return sparseArray.remove(value);
		}
		return super.remove(value);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		if(sparseArray!=null) {
			return sparseArray.removeAll(c);
		}
		return super.removeAll(c);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		if(sparseArray!=null) {
			return sparseArray.containsAll(c);
		}
		return listView().containsAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		if(sparseArray!=null) {
			return sparseArray.retainAll(c);
		}
		return super.retainAll(c);
	}

	@Override
	public int indexOf(Object value) {
		if(sparseArray!=null) {
			long idx = sparseArray.indexOf(value);
			if(idx>Integer.MAX_VALUE) {
				throw new IllegalStateException("Index is greater than max integer");
			}
			return (int)idx;
		}
		return listView().indexOf(value);
	}

	@Override
	public int lastIndexOf(Object value) {
		if(sparseArray!=null) {
			long idx = sparseArray.lastIndexOf(value);
			if(idx>Integer.MAX_VALUE) {
				throw new IllegalStateException("Index is greater than max integer");
			}
			return (int)idx;
		}
		return listView().lastIndexOf(value);
	}

	@Override
	public ListIterator<Object> listIterator() {
		if(sparseArray!=null) {
			return sparseArray.listIterator();
		}
		return listView().listIterator();
	}

	@Override
	public ListIterator<Object> listIterator(int index) {
		if(sparseArray!=null) {
			return sparseArray.listIterator(index);
		}
		return listView().listIterator(index);
	}

	@Override
	public List<Object> subList(int fromIndex, int toIndex) {
		if(sparseArray!=null) {
			return sparseArray.subList(fromIndex,toIndex);
		}
		return listView().subList(fromIndex,toIndex);
	}

	@Override
	public void replaceAll(UnaryOperator<Object> operator) {
		if(sparseArray!=null) {
			sparseArray.replaceAll(operator);
			return;
		}
		super.replaceAll(operator);
	}

	@Override
	public void sort(Comparator<? super Object> c) {
		if(sparseArray!=null) {
			sparseArray.sort(c);
			return;
		}
		super.sort(c);
	}

	@Override
	public boolean removeIf(Predicate<? super Object> filter) {
		if(sparseArray!=null) {
			return sparseArray.removeIf(filter);
		}
		return super.removeIf(filter);
	}

	@Override
	public Spliterator<Object> spliterator() {
		if(sparseArray!=null) {
			return sparseArray.spliterator();
		}
		return listView().spliterator();
	}

	@Override
	public Stream<Object> stream() {
		if(sparseArray!=null) {
			return sparseArray.stream();
		}
		return listView().stream();
	}

	@Override
	public Stream<Object> parallelStream() {
		if(sparseArray!=null) {
			return sparseArray.parallelStream();
		}
		return listView().parallelStream();
	}


	//
	// To support Array literal
	//

	// For the  transpiler and to use a different class or method
	public JSArrayImpl litValues(Consumer<JSArrayImpl> builder) {
		builder.accept(this);
		return this;
	}
}
