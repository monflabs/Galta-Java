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

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseCallableObject;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArray;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.util.SparseList;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonArray.EntryConsumer;
import org.monflabs.json.JsonArray.EntryConsumerWhile;
import org.monflabs.util.iterators.Iterators;
import org.monflabs.util.iterators.LongIterator;

public interface JSArray extends JSObjectDelegate {
	
	public static final long MAX_ARRAY_SIZE = 2147483647L;

	
	public static BuiltinArray createSparse(JSEnvironment env, SparseList<Object> sparseList) {
		return new BuiltinArray(env,sparseList);
	}
	public static BuiltinArray createSparse(JSEnvironment env, long size) {
		BuiltinArray a = new BuiltinArray(env);
		a.arraySetLength(size);
		return a;
	}
	public static BuiltinArray create(JSEnvironment env) {
		return new BuiltinArray(env);
	}
	public static BuiltinArray create(JSEnvironment env, int initialCapacity) {
		return new BuiltinArray(env,initialCapacity);
	}
	public static BuiltinArray of(JSEnvironment env, Object...values) {
		BuiltinArray a = (BuiltinArray)create(env);
		for(int i=0; i<values.length; i++) {
			Object value = values[i];
			a.addValue(value);
		}
		return a;
	}

	
	@FunctionalInterface 
	public interface ArrayItem {
		public boolean process(long index, long emptyItems, Object value);
	}

	public default SparseList<Object> makeSparse() {
		throw new IllegalStateException("Array of class {0} cannot be sparse");
	}
	public default SparseList<Object> getSparseList() {
		return null;
	}
	public default boolean isSparse() {
		return getSparseList()!=null;
	}
	
	@Override
	public JSObjectInternal getMembers(boolean autoCreate);

	@Override
	public default String getClassName() {
		return BuiltinArrayConstructor.CLASSNAME;
	}
	
	// Object.freeze()/seal() only flip a blanket flag (see ObjectPropertiesMap); the
	// per-index/length descriptors reported to JS must reflect that flag dynamically
	// rather than always claiming writable/configurable:true.
	private PropertyDescriptor arrayIndexDescriptor() {
		if(isFrozen()) {
			return PropertyDescriptor.DESC_READONLY_PROP; // writable:false, configurable:false, enumerable:true
		}
		if(isSealed()) {
			return PropertyDescriptor.DESC_FIXED_PROP; // writable:true, configurable:false, enumerable:true
		}
		return PropertyDescriptor.DESC_PROP_ARRAYINDEX;
	}
	private PropertyDescriptor arrayLengthDescriptor() {
		return (isFrozen() || !isLengthWritable()) ? PropertyDescriptor.DESC_READONLY_HIDDEN_PROP : PropertyDescriptor.DESC_HIDDEN_PROP;
	}

	// "length" can be made non-writable independently of a full freeze/seal
	// (e.g. Object.defineProperty(arr,"length",{writable:false})) - default
	// is "no support" (always writable); JSArrayImpl overrides both with a
	// real per-instance flag. Other JSArray implementations (array-like
	// wrappers around a plain JSObject/String/etc.) don't need their own
	// tracking here since their "length" write already routes through the
	// wrapped object's own property system.
	public default boolean isLengthWritable() {
		return true;
	}
	public default void setLengthWritable(boolean writable) {
	}

	// Per-index accessor (getter/setter) descriptors installed via
	// Object.defineProperty - the numeric fast path (arrayGet/arraySet) has
	// no notion of these, so they're stored out-of-band. Default is "no
	// support" (returns null / no-op); JSArrayImpl overrides both with a
	// lazily-created Map<Long,PropertyDescriptor>.
	public default PropertyDescriptor arrayGetAccessor(long index) {
		return null;
	}
	public default void arraySetAccessor(long index, PropertyDescriptor desc) {
	}

	private boolean rejectArrayDefine(DESC_CHECK check, long index) {
		if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
			throw RuntimeUtil.typeError("Property {0} cannot be configured",index);
		}
		return false;
	}

	@Override
	public default PropertyDescriptor getOwnPropertyDescriptor(String member) {
		if("length".equals(member)) {
			return arrayLengthDescriptor();
		}
		long index = RuntimeUtil.memberIndex(member);
		if(index>=0) {
			// Not delegated to getOwnPropertyDescriptor(long): its own miss-fallback
			// (JSObjectDelegate.super) stringifies the index and calls back into this
			// very overload - delegating here would recurse indefinitely on a miss.
			PropertyDescriptor accessorDesc = arrayGetAccessor(index);
			if(accessorDesc!=null) {
				return accessorDesc;
			}
			return arrayHas(index) ? arrayIndexDescriptor() : null;
		}
		return JSObjectDelegate.super.getOwnPropertyDescriptor(member);
	}
	@Override
	public default PropertyDescriptor getOwnPropertyDescriptor(long index) {
		if(RuntimeUtil.isMemberIndex(index)) {
			PropertyDescriptor accessorDesc = arrayGetAccessor(index);
			if(accessorDesc!=null) {
				return accessorDesc;
			}
			return arrayHas(index) ? arrayIndexDescriptor() : null;
		}
		return JSObjectDelegate.super.getOwnPropertyDescriptor(index);
	}
	@Override
	public default JSObject getOwnPropertyDescriptors(JSObject descriptors) {
		// Must not go through getProperty()/arrayForEach - that would invoke
		// index accessor getters merely to report their descriptor shape.
		for(LongIterator it=nonHoleIndices(); it.hasNext(); ) {
			long i = it.next();
			descriptors.setOwnProperty(Long.toString(i),getOwnPropertyDescriptor(i));
		}
		descriptors.setOwnProperty("length",arrayLengthDescriptor());
		return JSObjectDelegate.super.getOwnPropertyDescriptors(descriptors);
	}

	@Override
	public default Object getOwnProperty(String member, Object defaultValue, Object receiver) {
		if(member.equals("length")) {
			return arrayLength();
		}
		long index = RuntimeUtil.memberIndex(member);
		if(index!=Long.MIN_VALUE) {
			// Not delegated to getOwnProperty(long,...): its own miss-fallback
			// (JSObjectDelegate.super) stringifies the index and calls back into this
			// very overload - delegating here would recurse indefinitely on a miss.
			// arrayGetAccessor now also tracks plain DATA descriptors with
			// non-default attributes (not just genuine getter/setter
			// accessors) - isAccessor() must gate the getter-invocation
			// branch, or a tracked plain descriptor's real value (stored
			// separately via arraySet/arrayGet) is masked by an
			// unconditional "undefined" (confirmed via
			// defineProperty/15.2.3.6-4-182.js: `Object.defineProperty(
			// arrObj,"0",{value:12})` must read back as 12).
			PropertyDescriptor accessorDesc = arrayGetAccessor(index);
			if(accessorDesc!=null && accessorDesc.isAccessor()) {
				BaseCallableObject getter = accessorDesc.getGetter();
				return getter!=null ? getter.get(receiver,index) : RuntimeUtil.UNDEFINED;
			}
			return arrayGet(index,defaultValue);
		}
		return JSObjectDelegate.super.getOwnProperty(member,defaultValue,receiver);
	}
	@Override
	public default Object getOwnProperty(long index, Object defaultValue, Object receiver) {
		if(RuntimeUtil.isMemberIndex(index)) {
			// See the String-keyed overload's matching comment above.
			PropertyDescriptor accessorDesc = arrayGetAccessor(index);
			if(accessorDesc!=null && accessorDesc.isAccessor()) {
				BaseCallableObject getter = accessorDesc.getGetter();
				return getter!=null ? getter.get(receiver,index) : RuntimeUtil.UNDEFINED;
			}
			Object v = arrayGet(index,RuntimeUtil.NOT_AVAILABLE);
			if(v!=RuntimeUtil.NOT_AVAILABLE) {
				return v;
			}
		}
		return JSObjectDelegate.super.getOwnProperty(index,defaultValue,receiver);
	}

	@Override
	public default boolean setOwnProperty(String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		if(member.equals("length")) {
			// "length"'s [[Configurable]] and [[Enumerable]] are ALWAYS
			// false and it's ALWAYS a data property - by the time a
			// defineProperty call reaches here, `desc` is already the
			// spec-merged "next" descriptor (absent fields filled in
			// from the CURRENT one - see RuntimeUtil.defineProperty),
			// so any deviation here means the caller's raw Desc
			// EXPLICITLY requested it, which
			// ValidateAndApplyPropertyDescriptor must reject outright -
			// but per ArraySetLength's own step order, this validation is
			// step 10a/14 (via OrdinaryDefineOwnProperty), which only runs
			// AFTER a present [[Value]]'s coercion + overflow RangeError
			// check (steps 3-5) - confirmed via
			// define-own-prop-length-overflow-order.js/-error.js:
			// {value:-1,configurable:true} must throw RangeError (the
			// overflow), NOT reject for configurable:true first. For a
			// Desc with NO value at all (step 1: "return
			// OrdinaryDefineOwnProperty" immediately, no coercion to run
			// first), this check is the very first thing that runs -
			// confirmed via define-own-prop-length-no-value-order.js:
			// {configurable:true}/{enumerable:true}/{get:...}/{set:...}
			// must all be rejected, the getter must never even be
			// invoked. A plain `array.length = N` [[Set]] (desc==null)
			// never hits either branch - only [[DefineOwnProperty]]
			// supplies a non-null desc.
			if(desc!=null && value==RuntimeUtil.NOT_AVAILABLE) {
				if(desc.isConfigurable() || desc.isEnumerable() || desc.isAccessor()) {
					if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
						throw RuntimeUtil.typeError("Cannot redefine property: length");
					}
					return false;
				}
				// Object.defineProperty(arr,"length",{writable:...}) with no
				// "value" field - per ArraySetLength (10.4.2.4), a Desc with
				// no Value only adjusts [[Writable]] (length's own
				// enumerable/configurable are always fixed), it must NOT be
				// treated as an invalid-length error just because there's no
				// numeric value to parse (confirmed via
				// push/set-length-zero-array-length-is-non-writable.js and
				// its pop/shift/unshift siblings, whose
				// Object.defineProperty(array,"length",{writable:false})
				// previously threw RangeError here instead of succeeding).
				// Going from non-writable back to writable is a disallowed
				// change to a non-configurable property.
				if(!isLengthWritable() && desc.isWritable()) {
					if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
						throw RuntimeUtil.typeError("Cannot redefine property: length");
					}
					return false;
				}
				setLengthWritable(desc.isWritable());
				return true;
			}
			{
				// Spec ArraySetLength: "newLen = ToUint32(value)" (step 3)
				// and "numberLen = ToNumber(value)" (step 4) are TWO
				// INDEPENDENT coercions of the SAME value, each capable of
				// its own valueOf/toString side effect - NOT one derived
				// mathematically from the other's already-computed result.
				// A stateful valueOf (e.g. one that makes "length" non-
				// writable on its second call) must be observed exactly
				// twice, in this order, for the throw/return-false to
				// happen at the correct point (confirmed via
				// define-own-prop-length-coercion-order.js/-set.js/
				// -no-value-order.js: valueOfCalls must equal 2). Also
				// coerces any value type, not just an already-Number one
				// (confirmed via S15.4.5.1_A1.3_T1.js/-T2.js: `x.length =
				// true`/`null`/`new Boolean(false)`/`"1"`/`new String("1")`
				// must all coerce successfully, not throw).
				long size = RuntimeUtil.toUInt32(getEnvironment(), value);
				double numberLen = RuntimeUtil.toNumber(getEnvironment(), value).doubleValue();
		    	if(size!=numberLen) {
		    		throw RuntimeUtil.rangeError("Invalid array length");
		    	}
		    	// Only AFTER the overflow check above (see this method's
		    	// opening comment for why) - "length" must stay non-
		    	// configurable/non-enumerable/data-shaped regardless of a
		    	// present value (confirmed via
		    	// define-own-prop-length-overflow-order.js's third case:
		    	// {value: Number.MAX_SAFE_INTEGER, writable: true} on an
		    	// already-non-writable "length" must throw RangeError from
		    	// the overflow check, not reach this far at all - but a
		    	// smaller, in-range value with configurable:true must still
		    	// be rejected here).
		    	if(desc!=null && (desc.isConfigurable() || desc.isEnumerable() || desc.isAccessor())) {
					if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
						throw RuntimeUtil.typeError("Cannot redefine property: length");
					}
					return false;
		    	}
		    	// A plain [[Set]] (array.length = N, desc==null) rejects ANY
		    	// write once non-writable, unconditionally, even a same-
		    	// value one (OrdinarySetWithOwnDescriptor has no same-value
		    	// exception) - confirmed via
		    	// pop/set-length-zero-array-length-is-non-writable.js, which
		    	// still expects a TypeError from `array.push()`'s trailing
		    	// Set(O,"length",0,true) even though 0 is already the
		    	// current length. A defineProperty call (desc!=null) is
		    	// more permissive per ValidateAndApplyPropertyDescriptor
		    	// 7a: a same-value write is tolerated on a non-writable
		    	// non-configurable data property, UNLESS the caller's Desc
		    	// itself explicitly asks for writable:true - that alone
		    	// must reject even with a matching value (confirmed via
		    	// define-own-prop-length-coercion-order.js: a same-value
		    	// {value:2,writable:true} defineProperty on an array whose
		    	// "length" became non-writable mid-coercion must still
		    	// throw TypeError).
		    	if(!isLengthWritable() && (desc==null || size!=arrayLength() || desc.isWritable())) {
					if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
						throw RuntimeUtil.typeError("Cannot assign to read only property 'length'");
					}
					return false;
		    	}
		    	// Reaching here with a non-writable length means the branch
		    	// above already established this is the tolerated same-value
		    	// defineProperty case (size==arrayLength(), desc doesn't ask
		    	// for writable:true) - nothing actually needs to change, and
		    	// arraySetLength's own !lengthWritable gate exists for the
		    	// unconditional (no same-value exception) plain [[Set]] path,
		    	// so it must not be invoked here (confirmed via
		    	// 15.2.3.6-4-163.js/15.2.3.7-6-a-159.js: a same-value
		    	// {value:0} defineProperty on a non-writable zero-length array
		    	// must succeed, not throw).
		    	boolean applied = isLengthWritable() ? arraySetLength(size, check) : true;
		    	if(desc!=null) {
		    		setLengthWritable(desc.isWritable());
		    	}
				return applied;
			}
		}
		long index = RuntimeUtil.memberIndex(member);
		if(index!=Long.MIN_VALUE) {
			return setOwnProperty(index,value,desc,check,receiver);
		}
		return JSObjectDelegate.super.setOwnProperty(member,value,desc,check,receiver);
	}
	@Override
	public default boolean setOwnProperty(long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		if(RuntimeUtil.isMemberIndex(index)) {
			// Array exotic [[DefineOwnProperty]] step 3.d: an array-index
			// write that would EXTEND the array (index >= current length)
			// must be rejected outright once "length" is non-writable -
			// checked before any mutation, for both the accessor and plain-
			// data branches below (confirmed via
			// defineProperty/15.2.3.6-4-188.js: defining index 3 on a
			// length-3 non-writable-length array must throw TypeError).
			if(desc!=null && index>=arrayLength() && !isLengthWritable()) {
				return rejectArrayDefine(check,index);
			}
			PropertyDescriptor current = arrayGetAccessor(index);
			if(desc!=null) {
				// Object.defineProperty path.
				if(desc.isAccessor()) {
					PropertyDescriptor currentReported = current!=null ? current : (arrayHas(index) ? arrayIndexDescriptor() : null);
					if(currentReported==null) {
						if(!isExtensible()) {
							return rejectArrayDefine(check,index);
						}
					} else if(!currentReported.isConfigurable()) {
						// Not just kind/getter/setter - configurable and
						// enumerable changes on a non-configurable property
						// must also be rejected (confirmed via
						// defineProperty/15.2.3.6-4-194.js/-196.js/-197.js:
						// a non-configurable accessor's own configurable:
						// false->true request must throw even when get/set
						// are otherwise unchanged).
						if(CustomLinkedMap.isRejectedNonConfigurableChange(currentReported, desc, RuntimeUtil.NOT_AVAILABLE, null)) {
							return rejectArrayDefine(check,index);
						}
					}
					arraySetAccessor(index,desc);
					if(index>=arrayLength()) {
						arraySetLength(index+1);
					}
					return true;
				}
				// A plain (non-accessor) Object.defineProperty call. A
				// PRIOR attempt to persist a non-default descriptor here
				// (mirroring the accessor branch above) was reverted after
				// an unisolated regression - root-caused this time: that
				// attempt stored the descriptor WITHOUT first validating a
				// redefinition against an EXISTING non-configurable index
				// (the accessor branch above already does this - the
				// plain-data branch never did), so a rejection that spec
				// requires (or, more subtly, storing `desc` including its
				// [[Value]] instead of a value-less attributes-only
				// descriptor, letting a stale/duplicated value shadow the
				// real one written via arraySet below) plausibly slipped
				// through. Mirrors CustomLinkedMap's own
				// isRejectedNonConfigurableChange (this storage can't
				// reuse its entries directly, hence the direct reuse of
				// that same static logic) - confirmed via
				// defineProperty/15.2.3.6-4-168.js and its many siblings.
				PropertyDescriptor currentReported = current!=null ? current : (arrayHas(index) ? arrayIndexDescriptor() : null);
				if(currentReported!=null && !currentReported.isConfigurable()) {
					if(CustomLinkedMap.isRejectedNonConfigurableChange(currentReported, desc, value, arrayGet(index, RuntimeUtil.UNDEFINED))) {
						return rejectArrayDefine(check,index);
					}
				} else if(currentReported==null && !isExtensible()) {
					return rejectArrayDefine(check,index);
				}
				// Only track an explicit attributes-only descriptor (never
				// [[Value]], which arraySet below already stores in the
				// normal dense/sparse backing) when it genuinely deviates
				// from the array's own implicit default shape - avoids
				// needless bookkeeping (and matches the fast [[Set]] path)
				// for the overwhelmingly common case of a defineProperty
				// call that doesn't touch attributes at all.
				PropertyDescriptor plain = PropertyDescriptor.of(desc.isWritable(), desc.isConfigurable(), desc.isEnumerable());
				arraySetAccessor(index, PropertyDescriptor.of(true,true,true).equals(plain) ? null : plain);
				return value!=RuntimeUtil.NOT_AVAILABLE ? arraySet(index,value,check) : true;
			}
			// Plain [[Set]] path. `current` may now also represent a plain
			// (non-accessor) descriptor with custom flags, not just an
			// accessor - a WRITABLE tracked data descriptor must still let
			// the write through via arraySet below, only a getter-only
			// accessor or a non-writable data property blocks it.
			if(current!=null) {
				BaseCallableObject setter = current.getSetter();
				if(setter!=null) {
					setter.set(receiver,index,value);
					return true;
				}
				if(current.isAccessor()) {
					if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
						throw RuntimeUtil.typeError("Cannot set property {0} which has only a getter",index);
					}
					return false;
				}
				if(!current.isWritable()) {
					if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
						throw RuntimeUtil.typeError("Cannot assign to read only property {0}",index);
					}
					return false;
				}
			}
			return arraySet(index,value,check);
		}
		return JSObjectDelegate.super.setOwnProperty(index,value,desc,check, receiver);
	}
	@Override
	public default boolean setOwnProperty(Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return JSObjectDelegate.super.setOwnProperty(symbol,value,desc,check,receiver);
	}
	
	@Override
	public default boolean deleteProperty(String member, DESC_CHECK check) {
		if("length".equals(member)) {
			return false;
		}
		long index = RuntimeUtil.memberIndex(member);
		if(index!=Long.MIN_VALUE) {
			return arrayDeleteChecked(index,check);
		}
		return JSObjectDelegate.super.deleteProperty(member, check);
	}
	@Override
	public default boolean deleteProperty(long index, DESC_CHECK check) {
		if(RuntimeUtil.isMemberIndex(index)) {
			return arrayDeleteChecked(index,check);
		}
		return JSObjectDelegate.super.deleteProperty(RuntimeUtil.memberIndex(index),check);
	}
	// arrayDelete() (a JSArrayImpl-level override) only consults the
	// OBJECT-level sealed/frozen flag (canRemoveEntry) - it can't also
	// check a single tracked index's own [[Configurable]] (arrayGetAccessor/
	// arrayIndexDescriptor are private interface methods, invisible to an
	// implementing class's own overrides). A non-configurable index
	// tracked via Object.defineProperty must still reject a delete even
	// when the array itself isn't sealed/frozen (confirmed via
	// defineProperty/15.2.3.6-4-168.js's sibling deletion-rejection
	// cases).
	private boolean arrayDeleteChecked(long index, DESC_CHECK check) {
		PropertyDescriptor current = arrayGetAccessor(index);
		PropertyDescriptor currentReported = current!=null ? current : (arrayHas(index) ? arrayIndexDescriptor() : null);
		if(currentReported!=null && !currentReported.isConfigurable()) {
			if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property {0}",index);
			}
			return false;
		}
		return arrayDelete(index,check);
	}

	// Skips holes: an elided/never-set array index is not an own property at
	// all (unlike a plain value read, which falls back to undefined).
	// Overridden by JSArrayImpl with a sparse-aware fast path (same
	// catastrophic-hang risk as arrayForEachWhile's own hole-skipping walk
	// for a huge declared length combined with sparse-backed storage - see
	// arrayForEachWhile's comment in JSArrayImpl for the full reasoning);
	// `default` (not `private`) so that override is possible.
	default LongIterator nonHoleIndices() {
		long length = arrayLength();
		return new LongIterator() {
			private long next = -1;
			private boolean primed;
			private void prime() {
				if(!primed) {
					next++;
					while(next<length && !arrayHas(next)) {
						next++;
					}
					primed = true;
				}
			}
			@Override
			public boolean hasNext() {
				prime();
				return next<length;
			}
			@Override
			public long next() {
				prime();
				primed = false;
				return next;
			}
		};
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public default Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		JSObjectInternal m = getMembers(false);
		return Iterators.concat(
				// A non-hole index is not always enumerable - defineProperty
				// can track a non-default (e.g. enumerable:false) descriptor
				// for it via arrayGetAccessor/indexAccessors, same as any
				// other own property (confirmed via
				// defineProperties/15.2.3.7-6-a-198.js/-203.js: an index
				// explicitly defined non-enumerable must be excluded from
				// for-in/Object.keys/Object.entries/Object.values, not
				// treated as unconditionally enumerable just for being a
				// present array index).
				strings ? (enumerableOnly
						? Iterators.filter(
							Iterators.map(nonHoleIndices(), (v) -> {
								PropertyDescriptor d = getOwnPropertyDescriptor(v);
								if(d!=null && !d.isEnumerable()) {
									return null;
								}
								return (Map.Entry)JSAccessor.newEntry(Long.toString(v),getProperty(v));
							}),
							(e) -> e!=null)
						: Iterators.map(nonHoleIndices(), (v) -> (Map.Entry)JSAccessor.newEntry(Long.toString(v),getProperty(v)))
					) : null,
				strings && !enumerableOnly ? (Iterator)Iterators.single(JSAccessor.newEntry("length",arrayLength())) : null,
				m!=null ? (Iterator)m.ownPropertyEntries(strings,symbols,enumerableOnly) : null
			);
	}
	
	
	
	
	
	//
	// Array specific methods
	// These are shortcut to access the array with convenient methods
	// They are direct method that do not handle the prototype
	//

	public long arrayLength();
	public default void arraySetLength(long size) {
		arraySetLength(size,DESC_CHECK.NONE);
	}
	public boolean arraySetLength(long size, DESC_CHECK check);

	public default Iterator<Object> arrayIterator() {
        return new Iterator<Object>() {
            private long i;
            // Once index>=length is observed, %ArrayIteratorPrototype%.next
            // permanently sets its own [[IteratedArrayLike]] to undefined
            // (spec 23.1.5.2.1 step 10.a) - every later call short-circuits
            // at step 5 without ever re-reading length again, even if the
            // array grows afterward. So length is re-checked LIVE (not
            // captured once at creation) right up until exhaustion is first
            // observed, then permanently latched.
            private boolean exhausted;
            @Override
            public boolean hasNext() {
                if(exhausted) {
                	return false;
                }
                if(i>=arrayLength()) {
                	exhausted = true;
                	return false;
                }
                return true;
            }
            @Override
            public Object next() {
            	if(hasNext()) {
            		return getProperty(i++,RuntimeUtil.UNDEFINED);
            	}
				throw new NoSuchElementException();
            }
        };
	}
	
	public default JSArray arrayAddLength(long value) {
		return arrayAddLength(value,DESC_CHECK.NONE);
	}
	
	public default boolean arrayHas(long index) {
		return index>=0 && index<arrayLength();
	}
	public Object arrayGet(long index, Object defaultValue);

	public default JSArray arrayAddLength(long value, DESC_CHECK check) {
		// This may create a sparse array
		arraySetLength(arrayLength()+value);
		return this;
	}
	
	public default boolean arraySet(long index, Object value) {
		return arraySet(index, value,DESC_CHECK.NONE);
	}
	public boolean arraySet(long index, Object value, DESC_CHECK check);

	// Spec's CreateDataPropertyOrThrow (a genuine [[DefineOwnProperty]]) -
	// distinct from arraySet's plain [[Set]] semantics, used by callers
	// implementing an algorithm that specifically calls
	// CreateDataPropertyOrThrow on a fresh/species-created array (splice's
	// deleted-elements result, Array.from/Array.of, toReversed/toSorted/
	// toSpliced, ...) where the two internal methods are observably
	// different for a Proxy target (confirmed via
	// splice/property-traps-order-with-species.js: [[Set]]'s fallback,
	// when no "set" trap exists, does an extra "getOwnPropertyDescriptor"
	// trap call CreateDataPropertyOrThrow must not make). Default falls
	// back to arraySet, which is correct/unobservably-identical for every
	// implementor without its own trap layer (a real Array, Arguments,
	// String, Java array/List, ...) - only JSArrayAccessor (wrapping an
	// arbitrary accessor, e.g. a Proxy) needs its own override.
	public default boolean arrayDefineDataProperty(long index, Object value, DESC_CHECK check) {
		return arraySet(index, value, check);
	}

	
	public default JSArray arrayAdd(Object value) {
		return arrayAdd(value,DESC_CHECK.NONE);
	}
	public JSArray arrayAdd(Object value, DESC_CHECK check);

	public default JSArray arrayAdd(long index, Object value) {
		return arrayAdd(index,value,DESC_CHECK.NONE);
	}
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check);
	
	public default boolean arrayDelete(long index) {
		return arrayDelete(index,DESC_CHECK.NONE);
	}
	public boolean arrayDelete(long index, DESC_CHECK check);
	
	public default boolean arrayRemove(long index) {
		return arrayRemove(index,DESC_CHECK.NONE);
	}
	public boolean arrayRemove(long index, DESC_CHECK check);
	
	
	public default JSArray arrayAddAll(JSArray from) {
		return arrayAddAll(from,DESC_CHECK.NONE);
	}
	public default JSArray arrayAddAll(JSArray from, DESC_CHECK check) {
		if(from!=null) {
			for(Iterator<Object> it=from.arrayIterator(); it.hasNext();  ) {
				Object v=it.next();
				arrayAdd(v,check);
			}
		}
		return this;
	}
	
	public default JSArray arrayAddAll(Iterator<Object> it) {
		return arrayAddAll(it,DESC_CHECK.NONE);
	}
	public default JSArray arrayAddAll(Iterator<Object> it, DESC_CHECK check) {
		if(it!=null) {
			while( it.hasNext() ) {
				arrayAdd(it.next(),check);
			}
		}
		return this;
	}
	
	public default JSArray arrayAddAll(JSResult result) {
		return arrayAddAll(result,DESC_CHECK.NONE);
	}
	public default JSArray arrayAddAll(JSResult result, DESC_CHECK check) {
		return result.derefArray(this);
	}
	
	
	// Could be locally optimized..
	public default void arrayForEach(EntryConsumer c, boolean emptyItems, Object emptyValue) {
		arrayForEachWhile( (i,v) -> {c.process(i,v); return true;}, 0, emptyItems,emptyValue);
	}
	public default void arrayForEach(EntryConsumer c, boolean emptyItems, Object emptyValue, long len) {
		arrayForEachWhile( (i,v) -> {c.process(i,v); return true;}, 0, emptyItems, emptyValue, len);
	}
	public default boolean arrayForEachWhile(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue) {
		return arrayForEachWhile(c, start, emptyItems, emptyValue, arrayLength());
	}
	// Callers that already read "length" themselves (e.g. to observe a
	// length-getter's side effect in the correct spec order relative to
	// some other check) must pass that SAME value through here rather than
	// letting this method call arrayLength() again - a getter that's only
	// supposed to be invoked once per spec would otherwise fire twice
	// (confirmed via flatMap/array-like-objects.js).
	public default boolean arrayForEachWhile(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue, long len) {
		// EMPIRICALLY CONFIRMED unsafe to widen this to `long` without ALSO
		// making the walk sparse-aware: doing so (briefly, this session)
		// caused an actual multi-minute+ hang on
		// indexOf/15.4.4.14-5-12.js (`arr[2**32-2]=true` then indexOf with
		// a huge fromIndex) - a one-index-at-a-time linear walk over a
		// declared length near 2**32 is catastrophically slow regardless
		// of int vs long, since real work (getProperty/HasProperty per
		// index) happens on every iteration. The `(int)` truncation here
		// is a deliberate (if ugly) safety valve, not an oversight -
		// fixing it properly needs OwnPropertyKeys-based sparse iteration,
		// not just a wider integer type. See KnownGaps.md's Array section.
		int size = (int)len;
		if(emptyItems) {
			for(int i=Math.max(0,(int)start); i<size; i++) {
				if(!c.process(i,getProperty(i,emptyValue))) {
					return false;
				}
			}
		} else {
			// NOTE: per spec, hole-skipping iteration should do HasProperty
			// THEN (only if present) Get, as two separate MOP calls - tried
			// switching to genuine hasProperty(i)+getProperty(i) here
			// (confirmed it fixes
			// indexOf/calls-only-has-on-prototype-after-length-zeroed.js,
			// whose Proxy asserts [[Get]] is never called for an absent
			// index) but REVERTED after it caused ~59 regressions across
			// every/some/forEach/filter/map/reduce/reduceRight's own
			// call-count/getter-side-effect-order test262 files (e.g.
			// every/15.4.4.16-7-b-3.js) - those evidently depend on this
			// engine's existing single-combined-Get behavior in ways not
			// isolated in the time available this session. Left as a known,
			// still-open gap - see KnownGaps.md's Array section.
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

	public default boolean arrayForEachWhileReverse(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue) {
		return arrayForEachWhileReverse(c, start, emptyItems, emptyValue, arrayLength());
	}
	public default boolean arrayForEachWhileReverse(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue, long len) {
		// See arrayForEachWhile's note above - the (int) truncation here is
		// the same deliberate safety valve against a catastrophic linear
		// walk over a near-2**32 declared length, not an oversight.
		int size = (int)len;
		if(emptyItems) {
			for(int i=Math.min(size-1,(int)start); i>=0; i--) {
				if( !c.process(i,getProperty(i,emptyValue)) ) {
					return false;
				}
			}
		} else {
			// Must skip holes (HasProperty false) the same way the forward
			// arrayForEachWhile does - was previously calling getProperty(i)
			// unconditionally for every index regardless of emptyItems,
			// silently treating every hole as a present `undefined` element
			// (confirmed via reduceRight's sparse-array tests, e.g.
			// `new Array(10).reduceRight(cb, initial)` must never invoke cb
			// at all). See arrayForEachWhile's matching note above re: a
			// genuine two-step hasProperty()+getProperty() split being
			// reverted due to real regressions elsewhere.
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
	
	public default void arraySort(Comparator<? super Object> c) {
		arraySort(c, DESC_CHECK.NONE);
	}
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check);

	// Spec 23.1.3.34 Array.prototype.sort's real algorithm collects via
	// genuine HasProperty+Get and writes back via genuine Set(obj,...,true)
	// - BOTH dispatched on the RECEIVER (which may have a polluted
	// Object.prototype accessor at an index this array itself leaves as a
	// hole, confirmed via sort/precise-prototype-accessors.js), not the
	// array's own internal storage directly. Default falls back to the
	// existing own-storage-only 2-arg overload (correct/unobservably
	// identical for `toSorted`'s always-fresh, never-prototype-polluted
	// clone, and for every implementor without its own override); only
	// JSArrayImpl (genuine in-place `sort()`, which CAN be called on a
	// receiver with a polluted prototype) needs its own receiver-aware
	// version.
	public default void arraySort(Comparator<? super Object> c, DESC_CHECK check, Object receiver) {
		arraySort(c, check);
	}
	
	public default JsonArray toJsonArray() {
		if(this instanceof JsonArray a) {
			return a;
		}
		JsonArray a = JsonArray.create();
		for(Iterator<Object> it=arrayIterator(); it.hasNext(); ) {
			Object v=it.next();
			a.add(v);
		}
		return a;
	}
	
	public default Object[] toArray() {
		int sz = (int)arrayLength();
		if(sz>0) {
			Object[] a = new Object[sz];
			for(int i=0; i<sz; i++) {
				a[i] = getProperty(i);
			}
			return a;
		} else {
			return RuntimeUtil.EMPTY_PARAMS;
		}
	}
}
