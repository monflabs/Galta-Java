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

import java.lang.reflect.Constructor;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseCallableObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;

/**
 * Specialized map to store JSON object.
 * 
 * Allows the use of property descriptors to manage the access. The iterator
 * also allows concurrent updated to match the JS specification. This is
 * different from the Java specification.
 */
public abstract class CustomLinkedMap<K> extends AbstractMap<K, Object> {

	private static final int MINIMAL_SIZE = 16;
	private static final float LOAD_FACTOR = 0.75f;

	private float loadFactor = LOAD_FACTOR;

	private int elementCount;
	private EntryImpl<K>[] elementData;
	private int threshold;

	private EntryImpl<K> listFirst;
	private EntryImpl<K> listLast;

	// Tail of the sorted-ascending run of integer-key entries kept at the
	// front of the list (see insertInLinkedList()'s own doc comment for why
	// this exists) - null whenever no integer-key entry is currently
	// present. Kept in sync on removal by unlinkFromList()'s own check.
	private EntryImpl<K> lastIntegerKeyEntry;

	public static final class EntryImpl<K> implements Map.Entry<K, Object> {
		private boolean removed;
		private int hashCode;
		private K key;
		private long longKey;
		private Object value;
		private PropertyDescriptor descriptor;
		private EntryImpl<K> next;
		private EntryImpl<K> listPrev;
		private EntryImpl<K> listNext;

		private EntryImpl(int hashCode, K key) {
			this.hashCode = hashCode;
			this.key = key;
			this.value = RuntimeUtil.UNDEFINED;
			this.longKey = -1;
		}

		@Override
		public String toString() {
			return StringFormat.format("[{0},{1}]", key, value);
		}

		@Override
		public boolean equals(Object o) {
			return this == o;
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public K getKey() {
			return key;
		}
		public long getLongKey() {
			return longKey;
		}

		@Override
		public Object getValue() {
			return value;
		}

		public PropertyDescriptor getPropertyDescriptor() {
			return descriptor;
		}

		// Phase 3: cheap liveness check for the ASTMember PropIC. `removed` is
		// set (never cleared) when a still-cached-outside entry has been either
		// hard-removed from the map or soft-removed by an iterator; a repopulate
		// of the same key allocates a fresh EntryImpl. Reader treats a stale
		// cache-hit-on-removed-entry as a slow-path fallback.
		public boolean isRemoved() {
			return removed;
		}

		@Override
		public Object setValue(Object value) {
			Object old = this.value;
			this.value = value;
			return old;
		}

		public Object resolveValue(Object _this) {
			if (descriptor.getGetter() != null) {
				return descriptor.getGetter().get(_this, key);
			}
			// A getter-less ACCESSOR property (e.g. `set x(v){}` with no
			// matching `get x()`) always resolves to undefined per [[Get]] -
			// never the possibly-STALE `value` field left over from before
			// this property became an accessor. putEntry() intentionally
			// skips writing `value` when (re)defining an accessor (see
			// litSetter/litGetter's RuntimeUtil.NOT_AVAILABLE sentinel), so
			// redefining an EXISTING data property (e.g. a class static
			// element `static set length(_){}` overwriting the auto-
			// installed "length" data property) into a getter-less accessor
			// would otherwise leak that old data value back out here. A pure
			// DATA descriptor's getter is always null too, so the check
			// above alone can't distinguish the two - isAccessor() can.
			if (descriptor.isAccessor()) {
				return RuntimeUtil.UNDEFINED;
			}
			return value;
		}
	}


	public CustomLinkedMap() {
	}

	// getClass().getConstructor() re-resolves (and re-validates access to)
	// the no-arg constructor on every single clone() call - cached per
	// concrete subclass since it's always the same Constructor for a given
	// class.
	private static final Map<Class<?>,Constructor<?>> CLONE_CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

	@Override
	public CustomLinkedMap<K> clone() {
		try {
			Class<?> cls = getClass();
			Constructor<?> ctor = CLONE_CONSTRUCTOR_CACHE.get(cls);
			if(ctor==null) {
				ctor = cls.getConstructor();
				CLONE_CONSTRUCTOR_CACHE.put(cls, ctor);
			}
			@SuppressWarnings("unchecked")
			CustomLinkedMap<K> map = (CustomLinkedMap<K>)ctor.newInstance();
			map.putAll(this);
			return map;
		} catch (Exception ex) {
			throw new InternalError(ex); // Should not happen
		}
	}

	protected boolean canAddEntry() {
		return true;
	}

	protected boolean canUpdateEntry() {
		return true;
	}

	protected boolean canRemoveEntry() {
		return true;
	}

	// Spec SetIntegrityLevel (7.3.16): freeze/seal must eagerly redefine
	// EVERY own property's [[Configurable]] (and, for freeze, a data
	// property's [[Writable]]) to false at the time of the call - not just
	// gate FUTURE writes via a lazily-checked object-level flag (confirmed
	// via Object/freeze/15.2.3.9-2-4.js and its many siblings:
	// Object.getOwnPropertyDescriptor must report the update immediately,
	// which a flag-only implementation never reflects since it never
	// touches each entry's own stored descriptor). `transform` returns
	// null to leave an entry's descriptor untouched (already at the
	// target shape).
	protected void updateAllPropertyDescriptors(java.util.function.UnaryOperator<PropertyDescriptor> transform) {
		for(EntryImpl<K> e = listFirst; e!=null; e=e.listNext) {
			if(!e.removed) {
				PropertyDescriptor newDesc = transform.apply(e.descriptor);
				if(newDesc!=null) {
					e.descriptor = newDesc;
				}
			}
		}
	}

	/**
	 * @deprecated iterators no longer need soft deletion (see
	 *             {@link #liveSuccessor}); kept so subclasses overriding it
	 *             still compile. Never called.
	 */
	@Deprecated
	protected boolean isShouldSoftDelete() {
		return false;
	}

	/**
	 * @deprecated see {@link #isShouldSoftDelete()}. Never called.
	 */
	@Deprecated
	protected void setShouldSoftDelete(boolean shouldSoftDelete) {
	}

	// Entry that follows `pos` in the current list, `pos` being the last entry
	// an iterator returned (null: none yet). Entries are unlinked as soon as
	// they are removed, but a removed entry keeps its own listPrev/listNext
	// pointers, so an iterator positioned on one can still find its way back:
	// walk listPrev back to the nearest entry still in the list (necessarily
	// already visited) and continue from its successor - or from the head when
	// everything before was removed too. Going back rather than forward
	// matters: a removed tail's listNext stays null even after new entries are
	// appended to the list, which the spec requires Map/Set iteration to visit.
	// No tombstones are left behind, so abandoned or concurrent iterators cost
	// nothing.
	private EntryImpl<K> liveSuccessor(EntryImpl<K> pos) {
		while (pos != null && pos.removed) {
			pos = pos.listPrev;
		}
		return pos == null ? listFirst : pos.listNext;
	}

	// Shared by every removal path (remove(Object),
	// remove(K,DESC_CHECK), removeEntry() below) - factored out so the
	// lastIntegerKeyEntry bookkeeping insertInLinkedList()'s fast path
	// relies on only has to be kept correct in ONE place instead of once
	// per (previously duplicated) unlink site.
	private void unlinkFromList(EntryImpl<K> entry) {
		if (entry.listPrev != null) {
			entry.listPrev.listNext = entry.listNext;
		} else {
			listFirst = entry.listNext;
		}
		if (entry.listNext != null) {
			entry.listNext.listPrev = entry.listPrev;
		} else {
			listLast = entry.listPrev;
		}
		if (lastIntegerKeyEntry == entry) {
			// The removed entry was the tail of the sorted-integer-key run -
			// the new tail (if any) is whatever was immediately before it,
			// but only if THAT is itself still an integer-key entry (it
			// could be a string-keyed entry, or null, if the removed entry
			// was the run's only member).
			EntryImpl<K> prev = entry.listPrev;
			lastIntegerKeyEntry = (prev != null && prev.longKey >= 0) ? prev : null;
		}
	}

	@Override
	public int size() {
		return elementCount;
	}

	@Override
	public boolean isEmpty() {
		return elementCount == 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean containsKey(Object key) {
		EntryImpl<K> entry = getEntry((K) key);
		return entry != null;
	}

	@Override
	public boolean containsValue(Object value) {
		for (EntryImpl<K> e = listFirst; e != null; e = e.listNext) {
			if (!e.removed) {
				if (equalsValue(e.value, value)) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object get(Object key) {
		EntryImpl<K> entry = getEntry((K) key);
		if (entry != null) {
			PropertyDescriptor d = entry.descriptor;
			if (d != null) {
				BaseCallableObject getter = d.getGetter();
				if (getter != null) {
					return getter.get(this, key);
				}
			}
			return entry.value;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object getOrDefault(Object key, Object defaultValue) {
		EntryImpl<K> entry = getEntry((K) key);
		if (entry != null) {
			PropertyDescriptor d = entry.descriptor;
			if (d != null) {
				BaseCallableObject getter = d.getGetter();
				if (getter != null) {
					return getter.get(this, key);
				}
			}
			return entry.value;
		}
		return defaultValue;
	}

	public EntryImpl<K> getEntry(K key) {
		if (elementCount > 0) {
			int keyHashCode;
			int index = (keyHashCode = hash(key)) & elementData.length - 1; // & works when power 2
																			// (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
			EntryImpl<K> entry = elementData[index];
			while (entry != null) {
				if (keyHashCode == entry.hashCode && equalsKey(key, entry.key) && !entry.removed) {
					return entry;
				}
				entry = entry.next;
			}
		}
		return null;
	}

	public PropertyDescriptor getPropertyDescriptor(K name) {
		EntryImpl<K> e = getEntry(name);
		if (e != null) {
			return e.getPropertyDescriptor();
		}
		return null;
	}

	public JSObjectImpl getPropertyDescriptors(JSObjectImpl descriptors) {
		for (EntryImpl<K> e = listFirst; e != null; e = e.listNext) {
			if (!e.removed) {
				// For now...
				descriptors.put((String) e.getKey(), e.getPropertyDescriptor());
			}
		}
		return descriptors;
	}

	@Override
	public Object put(K key, Object value) {
		if (elementData == null || elementCount >= threshold) {
			rehash(elementCount + 1);
		}
		Object v = putEntry((K) key, value, null, DESC_CHECK.NONE, this);
		return v != RuntimeUtil.NOT_AVAILABLE ? v : null;
	}

	public boolean put(K key, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		if (elementCount == 0 || elementCount >= threshold) {
			rehash(elementCount + 1);
		}
		return putEntry(key, value, descriptor, check, receiver) != RuntimeUtil.NOT_AVAILABLE;
	}

	private Object putEntry(K key, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		int keyHashCode;
		int index = (keyHashCode = hash(key)) & elementData.length - 1; // & works when power 2
																		// (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
		EntryImpl<K> entry = elementData[index];
		while (entry != null) {
			if (keyHashCode == entry.hashCode && (key == entry.key || equalsKey(key, entry.key)) && !entry.removed) {
				PropertyDescriptor d = entry.descriptor;

				// Check the descriptor before changing anything
				if (descriptor!=null) {
					if (check != DESC_CHECK.NONE) {
						if (!d.isConfigurable() && isRejectedNonConfigurableChange(d, descriptor, value, entry.value)) {
							if (RuntimeUtil.isStrictCheck(check)) {
								throw RuntimeUtil.typeError("Property {0} cannot be configured", entry.key);
							}
							return RuntimeUtil.NOT_AVAILABLE;
						}
					}
				}

				Object oldValue = entry.value;
				if (value != RuntimeUtil.NOT_AVAILABLE) {
					if((descriptor!=null && descriptor.isData()) || d.isData()) {
						// Writability only gates a plain [[Set]] (descriptor==null). A [[DefineOwnProperty]]
						// call (descriptor!=null) may freely change the value of a configurable property
						// even while it is currently non-writable; the non-configurable case was already
						// validated above by isRejectedNonConfigurableChange.
						if (check != DESC_CHECK.NONE) {
							if (!canUpdateEntry() || (descriptor==null && d.isData() && !d.isWritable()) ) {
								if (RuntimeUtil.isStrictCheck(check)) {
									throw RuntimeUtil.typeError("Property {0} is not writable", entry.key);
								}
								return RuntimeUtil.NOT_AVAILABLE;
							}
						}
						entry.value = value;
					} else {
						entry.value = RuntimeUtil.UNDEFINED;
						if (d.getSetter() != null) {
							d.getSetter().set(receiver, (K) key, value);
						} else if (check != DESC_CHECK.NONE) {
							// Accessor property with no setter: [[Set]] fails.
							if (RuntimeUtil.isStrictCheck(check)) {
								throw RuntimeUtil.typeError("Cannot set property {0} which has only a getter", entry.key);
							}
							return RuntimeUtil.NOT_AVAILABLE;
						}
					}
				}

				// Store the new descriptor
				if(descriptor!=null) {
					entry.descriptor = descriptor;
				}

				// Access optimization
				entryAdded(entry);
				return oldValue;
			}
			entry = entry.next;
		}

		if (check != DESC_CHECK.NONE) {
			if (!canAddEntry()) {
				if (RuntimeUtil.isStrictCheck(check)) {
					throw RuntimeUtil.typeError("Property {0} cannot be added", key);
				}
				return RuntimeUtil.NOT_AVAILABLE;
			}
		}

		entry = addEntry(keyHashCode, key);
		if (descriptor != null) {
			entry.descriptor = descriptor;
			// When is that required??
			if (value != RuntimeUtil.NOT_AVAILABLE) {
				if (descriptor.getSetter() != null) {
					descriptor.getSetter().set(receiver, (K) key, value);
				} else {
					entry.value = value;
				}
			}
		} else {
			entry.descriptor = PropertyDescriptor.DESC_DEFAULT;
			entry.value = value;
		}
		entryAdded(entry);
		return null;
	}

	/**
	 * Spec 10.1.6.3 ValidateAndApplyPropertyDescriptor: whether redefining a
	 * non-configurable property with {@code descriptor}/{@code newValue} must be rejected.
	 * A non-configurable, still-writable data property may freely change its value and/or
	 * narrow {@code writable} to false; only once {@code writable} is false does the value
	 * become locked too.
	 */
	// Package-private (not private) and static: JSArray.java reuses this
	// exact logic for array-index defineProperty redefinition checks,
	// since it can't go through CustomLinkedMap's own entry storage.
	static boolean isRejectedNonConfigurableChange(PropertyDescriptor current, PropertyDescriptor next, Object newValue, Object currentValue) {
		if (next.isConfigurable() != current.isConfigurable() || next.isEnumerable() != current.isEnumerable()) {
			return true;
		}
		if (next.isData() != current.isData()) {
			return true;
		}
		if (!next.isData()) {
			return next.getGetter() != current.getGetter() || next.getSetter() != current.getSetter();
		}
		if (current.isWritable()) {
			return false;
		}
		if (next.isWritable()) {
			return true;
		}
		return newValue != RuntimeUtil.NOT_AVAILABLE && !isSameValue(newValue, currentValue);
	}

	// A SameValue comparison for the narrow purpose above, without needing a
	// JSEnvironment (unavailable at this low a level) - java.util.Objects.equals
	// alone is CLOSE but not quite right for GaltaJS's "no wrapper objects,
	// raw Java primitives" numeric representation: Double.equals() already
	// happens to match spec SameValue for same-typed doubles (NaN equals
	// NaN, +0 does NOT equal -0), but a JS Number stored as one Java numeric
	// type (e.g. Integer 5) vs another (e.g. Double 5.0) - both the SAME JS
	// value - would wrongly compare unequal via plain Objects.equals(), since
	// Integer/Double instances are never equal() to each other regardless of
	// value. Falls back to Objects.equals for anything else (String,
	// Boolean, BigInteger/BigDecimal, object references), which is already
	// correct there (objects: reference equality via the o1==o2 fast path
	// above; strings/booleans: value equality matches SameValue directly).
	static boolean isSameValue(Object o1, Object o2) {
		if (java.util.Objects.equals(o1, o2)) {
			return true;
		}
		if (isPlainNumber(o1) && isPlainNumber(o2)) {
			double d1 = ((Number)o1).doubleValue();
			double d2 = ((Number)o2).doubleValue();
			if (Double.isNaN(d1)) {
				return Double.isNaN(d2);
			}
			if (d1==0 && d2==0) {
				return (1/d1)==(1/d2); // distinguishes +0 from -0
			}
			return d1==d2;
		}
		return false;
	}
	private static boolean isPlainNumber(Object o) {
		return o instanceof Integer || o instanceof Long || o instanceof Double || o instanceof Float || o instanceof Short || o instanceof Byte;
	}

	private EntryImpl<K> addEntry(int hashCode, K key) {
		EntryImpl<K> newEntry = new EntryImpl<>(hashCode, key);
		
		insertInLinkedList(newEntry);

		// Insert in hashmap
		int index = hashCode & elementData.length - 1; // & works when power 2
														// (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
		newEntry.next = elementData[index];
		elementCount++;
		elementData[index] = newEntry;

		// Dummy entry at the end
		return newEntry;
	}
	
	
	private void insertInLinkedList(EntryImpl<K> newEntry) {
		// If this is a number index, add it to the head, sorted (JS spec)
		if(separateIntegerKeys()) {
			if(newEntry.getKey() instanceof String s && !s.isEmpty()) {
				char c = s.charAt(0);
				if(c>='0' && c<='9') {
					long longValue = RuntimeUtil.memberIndex(s);
					if(longValue!=Long.MIN_VALUE) {
						newEntry.longKey = longValue;
						// Fast path: strictly-ascending insertion (the common
						// case - `for(i=0;i<N;i++) o[i]=x`, Object.fromEntries
						// with numeric keys, JSON round-trips of index-keyed
						// data) - append right after the current tail of the
						// sorted-integer-key run in O(1), instead of re-
						// scanning every already-inserted integer key below to
						// rediscover the same "insert at the end of the run"
						// position every time (that scan alone made building
						// an N-entry object this way O(N^2)).
						if(lastIntegerKeyEntry!=null && longValue>=lastIntegerKeyEntry.longKey) {
							EntryImpl<K> after = lastIntegerKeyEntry.listNext;
							newEntry.listPrev = lastIntegerKeyEntry;
							newEntry.listNext = after;
							lastIntegerKeyEntry.listNext = newEntry;
							if(after!=null) {
								after.listPrev = newEntry;
							} else {
								listLast = newEntry;
							}
							lastIntegerKeyEntry = newEntry;
							return;
						}
						// Slow path: either the very first integer key ever
						// inserted (lastIntegerKeyEntry still null - findable
						// only by scanning, since it's not yet known where
						// the run should start relative to any string keys
						// already present), or a genuine out-of-order insert
						// (rare) that belongs somewhere before the run's tail.
						boolean wasFirstIntegerKey = lastIntegerKeyEntry==null;
						if(listFirst!=null) {
							EntryImpl<K> prev = null;
							for(EntryImpl<K> e=listFirst; e!=null; e=e.listNext) {
								if(e.longKey<0 || e.longKey>longValue) {
									// listPrev must be kept consistent here too - not
									// just listNext - since removeEntry() unlinks via
									// listPrev (falling back to overwriting listFirst
									// itself when listPrev is null/stale), and a plain
									// object mixes this sorted-integer-key insertion
									// path with the listLast-append path below for its
									// string keys. A stale/unset listPrev on the entry
									// immediately after an integer-key insertion here
					        		// previously caused a later delete of THAT entry to
					        		// silently orphan the integer-keyed one - it was
					        		// still reachable via ITS OWN listNext, but nothing
					        		// upstream pointed to it anymore once listFirst got
					        		// overwritten by the deletion's own (wrong) fallback.
									newEntry.listNext = e;
									newEntry.listPrev = prev;
									e.listPrev = newEntry;
									if(prev==null) {
										listFirst = newEntry;
									} else {
										prev.listNext = newEntry;
									}
									if(wasFirstIntegerKey) {
										lastIntegerKeyEntry = newEntry;
									}
									return;
								}
								prev = e;
							}
							// Ok, insert at the end...
						}
						if(wasFirstIntegerKey) {
							lastIntegerKeyEntry = newEntry;
						}
					}
				}
			}
		}

		if (listLast != null) {
			listLast.listNext = newEntry;
			newEntry.listPrev = listLast;
			listLast = newEntry;
		} else {
			listFirst = listLast = newEntry;
		}
	}
	protected boolean separateIntegerKeys() {
		return false;
	}
	protected void entryAdded(EntryImpl<K> entry) {
	}

	@SuppressWarnings("unchecked")
	@Override
	public void putAll(Map<? extends K, ? extends Object> m) {
		if (m.isEmpty()) {
			return;
		}

		int newSize = size() + m.size(); // Assume no duplicate...
		if (newSize > threshold) {
			rehash(newSize);
		}
		if (m instanceof CustomLinkedMap bm) {
			// Other map
			if (elementCount == 0) {
				// No need to check the entries
				// Loop is up to last, which is a place holder for tha next entry
				for (EntryImpl<K> e = bm.listFirst; e != null; e = e.listNext) {
					if (!e.removed) {
						EntryImpl<K> entry = addEntry(e.hashCode, e.key);
						entry.value = e.value;
						entry.descriptor = e.descriptor;
					}
				}
			} else {
				// Loop is up to last, which is a place holder for tha next entry
				for (EntryImpl<K> e = bm.listFirst; e != null; e = e.listNext) {
					if (!e.removed) {
						putEntry(e.getKey(), e.getValue(), e.descriptor, DESC_CHECK.NONE, this);
					}
				}
			}
		} else {
			// Other map
			if (elementCount == 0) {
				for (Map.Entry<? extends K, ? extends Object> e : m.entrySet()) {
					K key = e.getKey();
					EntryImpl<K> entry = addEntry(hash(key), key);
					entry.value = e.getValue();
					entry.descriptor = PropertyDescriptor.DESC_DEFAULT;
				}
			} else {
				for (Map.Entry<? extends K, ? extends Object> e : m.entrySet()) {
					putEntry(e.getKey(), e.getValue(), PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.NONE, this);
				}
			}
		}
	}

	@Override
	public Object remove(Object key) {
		if (elementCount > 0) {
			int keyHashCode = hash(key);
			int index = (keyHashCode = hash(key)) & elementData.length - 1; // & works when power 2
																			// (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
			EntryImpl<K> lastEntry = null;
			EntryImpl<K> entry = elementData[index];
			while (entry != null) {
				if (keyHashCode == entry.hashCode && equalsKey(key, entry.key)) {
					if (lastEntry == null) {
						elementData[index] = entry.next;
					} else {
						lastEntry.next = entry.next;
					}
					elementCount--;

					entry.removed = true;
					unlinkFromList(entry);
					return entry.value;
				}
				lastEntry = entry;
				entry = entry.next;
			}
		}
		return null;
	}

	// Remove in a JS sense: returns true unless the property cannot be removed
	// because of the descriptor
	public boolean remove(K key, DESC_CHECK check) {
		if (elementCount > 0) {
			int keyHashCode;
			int index = (keyHashCode = hash(key)) & elementData.length - 1; // & works when power 2
																			// (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
			EntryImpl<K> lastEntry = null;
			EntryImpl<K> entry = elementData[index];
			while (entry != null) {
				if (keyHashCode == entry.hashCode && equalsKey(key, entry.key)) {
					if (check != DESC_CHECK.NONE) {
						PropertyDescriptor d = entry.descriptor;
						if (!canRemoveEntry() || !d.isConfigurable()) {
							if (RuntimeUtil.isStrictCheck(check)) {
								throw RuntimeUtil.typeError("Property {0} cannot be removed", entry.key);
							}
							return false;
						}
					}
					if (lastEntry == null) {
						elementData[index] = entry.next;
					} else {
						lastEntry.next = entry.next;
					}
					elementCount--;

					entry.removed = true;
					unlinkFromList(entry);
					return true;
				}
				lastEntry = entry;
				entry = entry.next;
			}
		}
		return true;
	}

	private boolean removeEntry(EntryImpl<K> e, DESC_CHECK check) {
		if (check != DESC_CHECK.NONE) {
			if (!canRemoveEntry()) {
				if (RuntimeUtil.isStrictCheck(check)) {
					throw RuntimeUtil.typeError("Property {0} cannot be removed", e.key);
				}
				return false;
			}
		}
		if (elementCount > 0) {
			int index = (e.hashCode) & elementData.length - 1; // & works when power 2
																// (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
			EntryImpl<K> lastEntry = null;
			EntryImpl<K> entry = elementData[index];
			while (entry != null) {
				if (entry == e) {
					if (check != DESC_CHECK.NONE) {
						PropertyDescriptor d = entry.descriptor;
						if (!canRemoveEntry() || !d.isConfigurable()) {
							if (RuntimeUtil.isStrictCheck(check)) {
								throw RuntimeUtil.typeError("Property {0} cannot be removed", e.key);
							}
							return false;
						}
					}
					if (lastEntry == null) {
						elementData[index] = entry.next;
					} else {
						lastEntry.next = entry.next;
					}
					elementCount--;

					entry.removed = true;
					unlinkFromList(entry);
					return true;
				}
				lastEntry = entry;
				entry = entry.next;
			}
		}
		return true;
	}

	@Override
	public void clear() {
		if(elementCount>0) {
			// Every entry is flagged removed (live iterators then restart from
			// the - new - head, see liveSuccessor(), and cached entries such as
			// ASTMember's PropIC see them as stale); the entries' own list
			// pointers are left intact.
			for (EntryImpl<K> e = listFirst; e != null; e = e.listNext) {
				e.removed = true;
			}
			this.listFirst = this.listLast = null;
			this.lastIntegerKeyEntry = null;
			elementCount = 0;
			elementData = null;
			threshold = 0;
		}
	}

	// Iterator that supports concurrent updates, as JavaScript requires for
	// Map, Set and property enumeration: entries added before the iteration
	// reaches them are visited, removed ones are not.
	private abstract class CollectionIterator<IT> implements Iterator<IT> {

		private final boolean enumerableOnly;
		// Last entry returned (null: none yet)
		private EntryImpl<K> current;
		// Look-ahead computed by hasNext(), valid while pendingRead is true
		private EntryImpl<K> pending;
		private boolean pendingRead;
		private EntryImpl<K> toDelete;

		protected CollectionIterator(boolean enumerableOnly) {
			this.enumerableOnly = enumerableOnly;
		}

		private void readNext() {
			// Recomputed when the look-ahead got removed since. Reaching the
			// end is final, as for a JS Map/Set iterator ([[Done]])
			if (pendingRead && (pending == null || !pending.removed)) {
				return;
			}
			EntryImpl<K> e = liveSuccessor(current);
			while (e != null && enumerableOnly && !e.descriptor.isEnumerable()) {
				e = e.listNext;
			}
			pending = e;
			pendingRead = true;
		}

		@Override
		public boolean hasNext() {
			readNext();
			return pending != null;
		}

		public EntryImpl<K> nextEntry() {
			readNext();
			if (pending == null) {
				throw new NoSuchElementException();
			}
			current = toDelete = pending;
			pendingRead = false;
			return current;
		}

		@Override
		public void remove() {
			// Remove the current one
			if (toDelete != null) {
				removeEntry(toDelete, ITERATOR_CHECK);
				toDelete = null;
				return;
			}
			throw new IllegalStateException();
		}
	};

	private static DESC_CHECK ITERATOR_CHECK = DESC_CHECK.CHECK;

	private KeySet keySet;
	private KeySet keySetEmum;

	@Override
	public Set<K> keySet() {
		return keySet(false);
	}

	public Set<K> keySet(boolean enumerableOnly) {
		if (enumerableOnly) {
			if (keySetEmum == null) {
				keySetEmum = new KeySet(enumerableOnly);
			}
			return keySetEmum;

		} else {
			if (keySet == null) {
				keySet = new KeySet(enumerableOnly);
			}
			return keySet;
		}
	}

	private final class KeySet extends AbstractSet<K> {
		private boolean enumerableOnly;

		private KeySet(boolean enumerableOnly) {
			this.enumerableOnly = enumerableOnly;
		}

		@Override
		public int size() {
			return elementCount;
		}

		@Override
		public void clear() {
			CustomLinkedMap.this.clear();
		}

		@Override
		public Iterator<K> iterator() {
			return new CollectionIterator<K>(enumerableOnly) {
				@Override
				public K next() {
					return nextEntry().getKey();
				}
			};
		}

		@Override
		public boolean contains(Object o) {
			return containsKey(o);
		}

		@Override
		public boolean remove(Object key) {
			return CustomLinkedMap.this.remove(key) != null;
		}
	}

	private Collection<Object> values;
	private Collection<Object> valuesEnum;

	@Override
	public Collection<Object> values() {
		return values(true);
	}

	public Collection<Object> values(boolean enumerableOnly) {
		if (enumerableOnly) {
			if (valuesEnum == null) {
				valuesEnum = new ValueCollection(enumerableOnly);
			}
			return valuesEnum;

		} else {
			if (values == null) {
				values = new ValueCollection(enumerableOnly);
			}
			return values;
		}
	}

	private final class ValueCollection extends AbstractCollection<Object> {
		private boolean enumerableOnly;

		private ValueCollection(boolean enumerableOnly) {
			this.enumerableOnly = enumerableOnly;
		}

		@Override
		public Iterator<Object> iterator() {
			return new CollectionIterator<Object>(enumerableOnly) {
				@Override
				public Object next() {
					return nextEntry().resolveValue(CustomLinkedMap.this);
				}
			};
		}

		@Override
		public int size() {
			return elementCount;
		}

		@Override
		public boolean isEmpty() {
			return elementCount == 0;
		}

		@Override
		public void clear() {
			CustomLinkedMap.this.clear();
		}

		@Override
		public boolean contains(Object v) {
			return CustomLinkedMap.this.containsValue(v);
		}
	};

	private EntrySet entrySet;
	private EntrySet entrySetEnum;

	@Override
	public Set<Map.Entry<K, Object>> entrySet() {
		return entrySet(true);
	}

	public Set<Map.Entry<K, Object>> entrySet(boolean enumerableOnly) {
		if (enumerableOnly) {
			if (entrySetEnum == null) {
				entrySetEnum = new EntrySet(enumerableOnly);
			}
			return entrySetEnum;

		} else {
			if (entrySet == null) {
				entrySet = new EntrySet(enumerableOnly);
			}
			return entrySet;
		}
	}

	private final class EntrySet extends AbstractSet<Map.Entry<K, Object>> {
		private boolean enumerableOnly;

		private EntrySet(boolean enumerableOnly) {
			this.enumerableOnly = enumerableOnly;
		}

		@Override
		public int size() {
			return elementCount;
		}

		@Override
		public void clear() {
			CustomLinkedMap.this.clear();
		}

		@Override
		public Iterator<Map.Entry<K, Object>> iterator() {
			return new CollectionIterator<Map.Entry<K, Object>>(enumerableOnly) {
				@Override
				public Map.Entry<K, Object> next() {
					EntryImpl<K> e = nextEntry();
					if(e.descriptor.getGetter()!=null) {
						return new Map.Entry<K, Object>() {
							@Override
							public K getKey() {
								return e.getKey();
							}
							@Override
							public Object getValue() {
								return e.resolveValue(CustomLinkedMap.this);
							}
							@Override
							public Object setValue(Object value) {
								throw new IllegalStateException("Entry is read-only");
							}
						};
					}
					return e;
				}
			};
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean contains(Object o) {
			return findEntry(o) != null;
		}

		// The live entry matching the Map.Entry `o` (same key, equal value)
		@SuppressWarnings("unchecked")
		private EntryImpl<K> findEntry(Object o) {
			if (!(o instanceof Map.Entry<?, ?> e)) {
				return null;
			}
			EntryImpl<K> candidate;
			try {
				candidate = getEntry((K) e.getKey());
			} catch (ClassCastException ex) {
				return null;
			}
			if (candidate == null || (enumerableOnly && !candidate.descriptor.isEnumerable())) {
				return null;
			}
			if (candidate != e && !java.util.Objects.equals(candidate.resolveValue(CustomLinkedMap.this), e.getValue())) {
				return null;
			}
			return candidate;
		}

		@Override
		public boolean remove(Object o) {
			EntryImpl<K> e = findEntry(o);
			return e != null && CustomLinkedMap.this.remove(e.key, DESC_CHECK.NONE);
		}
	}

	//
	// Managing the EntryImpl list
	//
	private final int hash(Object key) {
		int h;
		// return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
		return (key == null) ? 0 : (h = _hash(key)) ^ (h >>> 16);
	}

	// Object comparison
	// To adapt to different scenarios, like JS objects
	protected abstract int _hash(Object key);
	protected abstract boolean equalsKey(Object o1, Object o2);
	protected abstract boolean equalsValue(Object o1, Object o2);

	private void rehash(int newSize) {
		int length = Math.max(MINIMAL_SIZE, powerOfTwo((int) (newSize / loadFactor)));
		@SuppressWarnings("unchecked")
		EntryImpl<K>[] newData = (EntryImpl<K>[]) new EntryImpl[length];
		// Up to last, which is just a placerholder for the next entry
		for (EntryImpl<K> e = listFirst; e != null; e = e.listNext) {
			// Soft-deleted entries are still linked (for live iterators) but are
			// no longer in the map: they must not come back into the buckets
			if (e.removed) {
				continue;
			}
			int index = e.hashCode & length - 1; // & works when power 2
													// (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
			e.next = newData[index];
			newData[index] = e;
		}
		elementData = newData;
		this.threshold = (int) (length * loadFactor);
	}

	private int powerOfTwo(int number) {
		return 1 << (32 - Integer.numberOfLeadingZeros(number - 1));
	}

	// Dump Stats
	public void dumpStats() {
		int total = 0;
		int min = 0;
		int max = 0;
		double avg = 0;
		int count = elementData.length;
		if (count > 0) {
			min = Integer.MAX_VALUE;
			max = Integer.MIN_VALUE;
			for (int i = 0; i < count; i++) {
				int c = 0;
				for (EntryImpl<K> e = elementData[i]; e != null; e = e.next) {
					c++;
				}
				min = Math.min(min, c);
				max = Math.max(max, c);
				total += c;
			}
			avg = ((double) total) / count;
		}
		Console.log("Map count={0}", elementCount);
		Console.log("  Total entries={0}", total);
		Console.log("  Map slots={0}", elementData.length);
		Console.log("  Avg slot={0}", avg);
		Console.log("  Min slot={0}", min);
		Console.log("  Max slot={0}", max);
	}
}
