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
package org.monflabs.galtajs.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.monflabs.galtajs.jsonfactory.JSArray.ArrayItem;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.json.JsonArray.EntryConsumer;
import org.monflabs.json.JsonArray.EntryConsumerWhile;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.NotImplementedException;
import org.monflabs.util.iterators.Iterators;
import org.monflabs.util.iterators.LongIterator;

/**
 * SpareArray implemented as a list.
 * 
 * Note that it is not a list as the indexes are long values, despite the fact that
 * it can only contains 2^32-1 elements. This is to be compatible with JavaScript.
 * 
 * It is vaguely inspired from Google Android, but written from Scratch as it is a real list
 * with JavaScript Array capabilities.
 * 
 * @param <T>
 */
public class SparseList<T> implements Cloneable, Iterable<T> {
	
	public static long MAX_LENGTH = 0xFFFFFFFFL;
	public static long MAX_INDEX  = MAX_LENGTH-1;
	
    private int[] keySlots;
    private Object[] valueSlots;
    private int slotSize;
    private long listSize; // JavaScript equivalent to length

    private static final int[] EMPTY_INTS = new int[0];
    private static final Object[] EMPTY_OBJECTS = new Object[0];
    
    private static final int DEFAULT_INITIAL_CAPACITY = 64;

    public SparseList() {
    	this.keySlots = new int[DEFAULT_INITIAL_CAPACITY];
        this.valueSlots = new Object[DEFAULT_INITIAL_CAPACITY];
    }
    public SparseList(int listSize) {
    	this.keySlots = new int[DEFAULT_INITIAL_CAPACITY];
        this.valueSlots = new Object[DEFAULT_INITIAL_CAPACITY];
        this.slotSize = 0;
        this.listSize = listSize;
    }
    
    public boolean isActuallySparse() {
    	return listSize>slotSize;
    }
    
    @SuppressWarnings("unchecked")
	protected T emptyValue() {
    	return (T)RuntimeUtil.UNDEFINED;
    }
    
    
    //
    // Access slots values
    // the array cannot be accessed directly because we need a proper int->long
    // conversion.
    //
    private long getIndexAt(int slot) {
    	return ((long)keySlots[slot]) & 0xFFFFFFFFL;
    }
    private void setIndexAt(int slot, long index) {
    	keySlots[slot] = (int)(index& 0xFFFFFFFFL);
    }
    private void incIndexAt(int slot) {
    	setIndexAt(slot, getIndexAt(slot)+1L);
    }
    private void decIndexAt(int slot) {
    	setIndexAt(slot, getIndexAt(slot)-1L);
    }
    private void decIndexAt(int slot, long value) {
    	setIndexAt(slot, getIndexAt(slot)-value);
    }
    
    
    //
    // Handle Slots
    //

    private void insertSlot(int pos) {
    	insertSlots(pos,1);
    }
    private void insertSlots(int pos, int count) {
    	ensureCapacity(count);
		int length = slotSize - pos;
        System.arraycopy(keySlots, pos, keySlots, pos+count, length);
        System.arraycopy(valueSlots, pos, valueSlots, pos+count, length);
        Arrays.fill(valueSlots, pos, pos+count, null); // Is that necessary or is that going to be assigned anyway?
        slotSize += count;
    }
    
    private void addSlot() {
    	if(keySlots.length<=slotSize) {
    		ensureCapacity(slotSize+1);
    	}
    	slotSize++;
    }

    
    private void deleteSlot(int pos) {
    	deleteSlots(pos,1);
    }
    private void deleteSlots(int pos, int count) {
		int length = slotSize - pos - count;
		System.arraycopy(keySlots, pos+count, keySlots, pos, length);
		System.arraycopy(valueSlots, pos+count, valueSlots, pos, length);
        Arrays.fill(valueSlots, slotSize-count, slotSize, null);
        slotSize -= count;
    }

    private void ensureCapacity(int capacity) {
    	int available = keySlots.length - slotSize;
    	if(available>=capacity) {
    		return;
    	}
    	int newArraySize = powerOfTwo(capacity+slotSize);
		int[] newKeys = new int[newArraySize];
		Object[] newValues = new Object[newArraySize];
		if(keySlots.length>0) {
    		System.arraycopy(keySlots,0,newKeys,0,keySlots.length);
    		System.arraycopy(valueSlots,0,newValues,0,valueSlots.length);
		}
		keySlots = newKeys;
		valueSlots = newValues;
    }
    private int powerOfTwo(int number) {
    	return 1 << (32 - Integer.numberOfLeadingZeros(number - 1));
    }

    /*
		When the binarySearch function does not find an exact match for the value in the array, 
		it returns a negative value. This negative value is calculated using the bitwise complement 
		operator ~ on the variable lo.

		The bitwise complement operator ~ inverts all the bits of its operand. For a non-matching 
		scenario in the binary search, lo will be the position where the search gives up. This position 
		is the index where the value would be inserted to keep the array sorted if it were to be added.

		Thus, the returned value is ~lo. The bitwise complement of lo (~lo) results in a negative value, 
		and it is computed as -(lo + 1). This provides a unique way to indicate not only that the 
		value wasn't found, but also where it could be inserted while maintaining the sorted or
		would be x - 1.     
	*/
    private int searchKey(long key) {
    	if(slotSize==0) {
    		return ~0; // No slots, no keys
    	}

//		// In case the array has contiguous values, we can optimize the search
//		long firstKey = getIndexAt(0);
//    	if(listSize==slotSize+firstKey) { // Values are contiguous
//    		if(key>=firstKey && key<slotSize+firstKey) {
//    			return (int)(key-firstKey);
//    		}
//    	}

		long firstKey = getIndexAt(0);
		long lastKey = getIndexAt(slotSize-1);
		if(key<firstKey) {
			return ~0; // Key is before the first key
		} else if(key>lastKey) {
			return ~slotSize; // Key is after the last key
		}
		// If the values are contiguous, we can optimize the search
		// The first and last keys are known, so we can check if the values are contiguous
		// If they are, we can return the index directly
		// This is a common case for sparse arrays with sequential keys.
    	if(slotSize==lastKey-firstKey+1) { // Values are contiguous
   			return (int)(key-firstKey);
    	}
    	
    	// else we do a binary search
    	int lo = 0;
        int hi = slotSize - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            
            // Inline th call here!
            //long midVal = getIndexAt(mid);
            long midVal = ((long)keySlots[mid]) & 0xFFFFFFFFL;
            if (midVal < key) {
                lo = mid + 1;
            } else if (midVal > key) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return ~lo;
    }


    @Override
    @SuppressWarnings("unchecked")
    public SparseList<T> clone() {
        SparseList<T> clone = null;
        try {
            clone = (SparseList<T>) super.clone();
            clone.keySlots = keySlots.clone();
            clone.valueSlots = valueSlots.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    @Override
    public String toString() {
    	return toString(null,0,listSize);
    }
    public String toString(Function<Object,String> serialize) {
    	return toString(serialize,0,listSize);
    }
    public String toString(long startIndex, long endIndex) {
    	return toString(null,startIndex,endIndex);
    }
    public String toString(Function<Object,String> serialize, long startIndex, long endIndex) {
        StringBuilder builder = new StringBuilder(64);
        builder.append("[");
        if(listSize>0) { 
        	iterateItems( (index,empty,value) -> {
            	if(empty>0) {
            		if(builder.length()>1) {
            	        builder.append(", ");
            		} else {
            	        builder.append(" ");
            		}
            		builder.append("<");
                    builder.append(empty);
                    if(empty<=1) {
                    	builder.append(" empty item>");
                    } else {
                    	builder.append(" empty items>");
                    }
            	} else {
	        		if(builder.length()>1) {
	        	        builder.append(", ");
	        		} else {
	        	        builder.append(" ");
	        		}
	                //builder.append(JsonUtil.encodeValue(value));
	                builder.append(serialize!=null ? serialize.apply(value) : JsonUtil.encodeValue(value));
            	}
            	return true;
        	}, startIndex, endIndex );
        }
		if(builder.length()>1) {
	        builder.append(" ]");
		} else {
	        builder.append("]");
		}
        return builder.toString();
    }

    public String toStringList(Function<Object,String> serialize) {
    	return toStringList(serialize,0,listSize);
    }
    private String toStringList(Function<Object,String> serialize, long startIndex, long endIndex) {
        StringBuilder builder = new StringBuilder(64);
        if(listSize>0) { 
        	iterateItems( (index,empty,value) -> {
            	if(empty>0) {
            		if(builder.length()>1) {
            	        builder.append(", ");
            		} else {
            	        builder.append(" ");
            		}
            		builder.append("<");
                    builder.append(empty);
                    if(empty<=1) {
                    	builder.append(" empty item>");
                    } else {
                    	builder.append(" empty items>");
                    }
            	} else {
	        		if(builder.length()>1) {
	        	        builder.append(", ");
	        		} else {
	        	        builder.append(" ");
	        		}
	                //builder.append(JsonUtil.encodeValue(value));
	                builder.append(serialize!=null ? serialize.apply(value) : JsonUtil.encodeValue(value));
            	}
            	return true;
        	}, startIndex, endIndex );
        }
	    return builder.toString();
    }

    public void reverse() {
        if(listSize>0) {
        	int sz = slotSize;
            int m = sz/2;
            for(int i=0; i<m; i++) {
                int vi = sz-i-1;
                int idx0 = keySlots[i];
                int idx1 = keySlots[vi];
                Object val0 = valueSlots[i];
                Object val1 = valueSlots[vi];
                setIndexAt(i,listSize-idx1-1);
                setIndexAt(vi,listSize-idx0-1);
                valueSlots[i] = val1;
                valueSlots[vi] = val0;
            }
        }
    }
    
    @SuppressWarnings("unchecked")
	public SparseList<T> extract(long startIndex, long endIndex) {
    	SparseList<T> r = new SparseList<>();        	
    	iterateItems( (index,empty,value) -> {
        	if(empty>0) {
        		r.setSize(r.size()+empty);
        	} else {
        		r.add((T)value);
        	}
        	return true;
    	}, startIndex, endIndex );

    	return r;
    }

    
    //
    // Access items
    //

	public long size() {
        return listSize;
    }
    public void setSize(long newSize) {
    	if(newSize<0 || newSize>MAX_LENGTH) {
    		throw new IllegalArgumentException("Invalid Size");
    	}
    	if(newSize!=listSize) {
	    	if(newSize<listSize) {
	    		int i = searchKey(newSize);
	    		if(i<0) {
	    			i = Math.max(0, ~i);
	    		}
	    		deleteSlots(i,slotSize-i);
	    	}
    		listSize = newSize;
    	}
    }
    
	public boolean isEmpty() {
		return listSize==0;
	}

	public boolean has(long index) {
    	if(index>=0 || index<listSize) {
    		return searchKey(index)>=0;
    	}
    	return false;
	}
	public T get(long index) {
        return getOrDefault(index, emptyValue());
    }

    @SuppressWarnings("unchecked")
    public T getOrDefault(long index, T defaultValue) {
    	if(index<0 || index>MAX_INDEX) {
    		throw new IndexOutOfBoundsException("Invalid index");
    	}
        int i = searchKey(index);
        return i<0 ? defaultValue : (T)valueSlots[i];
    }

	public void clear() {
        keySlots = EMPTY_INTS;
        valueSlots = EMPTY_OBJECTS;
        slotSize = 0;
        listSize = 0;
    }

	public boolean contains(Object o) {
		return indexOf(o)>=0;
	}

	@Override
	public Iterator<T> iterator() {
		return iterator(true);
	}
	public Iterator<T> iterator(boolean emptyValues) {
		return new SparseIterator(emptyValues);
	}

	public LongIterator keys() {
		return keys(true);
	}
	public LongIterator keys(boolean emptyValues) {
		if(emptyValues) {
			return Iterators.longSequence(0, size());
		}
		return new LongIterator() {
			int index;
			@Override
			public boolean hasNext() {
				return index<slotSize;
			}
			@Override
			public long next() {
				if(index<slotSize) {
					return getIndexAt(index++);
				}
				throw new NoSuchElementException();
			}
		};
	}

	public Object[] toArray() {
		Object[] a = new Object[(int)listSize];
		return toArray(a);
	}

	@SuppressWarnings({ "unchecked", "hiding" })
	public <T> T[] toArray(T[] a) {
		// Only the used slots: past slotSize, keySlots holds stale keys
		for(int i=0; i<slotSize; i++) {
			a[(int)getIndexAt(i)] = (T)valueSlots[i];
		}
		return a;
	}

	public boolean add(T value) {
    	if(listSize+1>MAX_LENGTH) {
    		throw new IndexOutOfBoundsException("Maximum array capacity reached");
    	}
		addSlot();
		setIndexAt(slotSize-1, listSize);
		valueSlots[slotSize-1] = value;
		listSize++;
		return true;
	}
	public boolean add(long index, T value) {
    	if(index<0 || Math.max(index+1, listSize+1)>MAX_LENGTH) {
    		throw new IndexOutOfBoundsException("Maximum array capacity reached");
    	}
		if(index>=listSize) {
			put(index,value);
			return true;
		}
		if(slotSize==0 || index>getIndexAt(slotSize-1)) {
			addSlot();
			setIndexAt(slotSize-1, index);
			valueSlots[slotSize-1] = value;
	        listSize = Math.max(index+1, listSize+1);
			return true;
		}
        int i = searchKey(index);
        if(i<0) {
        	i = ~i;
        }
        insertSlot(i);
		setIndexAt(i, index);
		valueSlots[i] = value;
		listSize++;
		for(int k=i+1; k<slotSize; k++) {
			incIndexAt(k);
		}
		return true;
	}
	public boolean addAll(Collection<? extends T> c) {
		// This is not too bad as there is not array copy or key increments like addAll(index)
		// But it could be slightly enhanced
		boolean changed = false;
		for(T o: c) {
			if(add(o)) {
				changed = true;
			}
		}
		return changed;
	}
	public boolean addAll(long index, Collection<? extends T> c) {
		// Could do better, this is a basic implementation
		// An optimized one could reserve the slots and fill them sequentially
		boolean changed = false;
		for(T o: c) {
			add(index++,o);
			changed = true;
		}
		return changed;
	}

	public T set(long index, T value) {
		if(index<0 || index>=listSize) {
			throw new IndexOutOfBoundsException();
		}
		return put(index,value);
	}

	@SuppressWarnings({ "unchecked" })
	public T put(long index, T value) {
    	if(index<0 || index>MAX_INDEX) {
    		throw new IndexOutOfBoundsException("Maximum array capacity reached");
    	}

    	if(slotSize==0 || index>getIndexAt(slotSize-1)) {
			addSlot();
			setIndexAt(slotSize-1, index);
			valueSlots[slotSize-1] = value;
	        listSize = Math.max(index+1, listSize);
			return emptyValue();
		}
		
        int i = searchKey(index);
        if (i >= 0) {
        	T old = (T)valueSlots[i]; 
            valueSlots[i] = value;
            return old;
        }
        
        i = ~i;
        insertSlot(i);
		setIndexAt(i, index);
        valueSlots[i] = value;
        return emptyValue();
    }

	public T remove(long key) {
		if(key>=0 && key<listSize) {
	        int i = searchKey(key);
	        return removeSlot(i);
		}
		return null;
    }
	public void remove(long startIndex, long lastIndex) {
    	long deleted = lastIndex-startIndex;
    	if(deleted>0) {
	        int startSlot = searchKey(startIndex);
	        if(startSlot<0) {
	        	startSlot = ~startSlot;
	        }
	        int endSlot = searchKey(lastIndex);
	        if(endSlot<0) {
	        	endSlot = ~endSlot;
	        }
	        if(endSlot>startSlot) {
	        	deleteSlots(startSlot,endSlot-startSlot);
	        }
            for(int k=startSlot; k<slotSize; k++) {
    			decIndexAt(k,deleted);
            }
        	listSize -= deleted;
    	}
    }
	public boolean remove(Object value) {
        for (int i = 0; i<slotSize; i++) {
            if (valueSlots[i] == value) {
            	removeSlot(i);
    			return true;
            }
        }
        return false;
	}
	public boolean removeAll(Collection<?> c) {
		boolean changed = false;
		for(Object o: c) {
			if(remove(o)) {
				changed = true;
			}
		}
		return changed;
	}
	@SuppressWarnings("unchecked")
	private T removeSlot(int slot) {
    	Object oldValue = null;
        if (slot>=0) {
        	oldValue = valueSlots[slot];
        	deleteSlot(slot);
        } else {
        	slot = ~slot;
        }
        for(int k=slot; k<slotSize; k++) {
			decIndexAt(k);
        }
    	listSize--;
    	return (T)oldValue;
    }
	
	

	public T delete(long key) {
        int i = searchKey(key);
        return _deleteSlot(i);
    }
	public void delete(long startIndex, long lastIndex) {
        int startSlot = searchKey(startIndex);
        if(startSlot<0) {
        	startSlot = ~startSlot;
        }
        int endSlot = searchKey(lastIndex);
        if(endSlot<0) {
        	endSlot = ~endSlot;
        }
        if(endSlot>startSlot) {
        	deleteSlots(startSlot,endSlot-startSlot);
        }
    }
	public boolean delete(Object value) {
        for (int i = 0; i<slotSize; i++) {
            if (valueSlots[i] == value) {
    			_deleteSlot(i);
    			return true;
            }
        }
        return false;
	}
	public boolean deleteAll(Collection<?> c) {
		boolean changed = false;
		for(Object o: c) {
			if(delete(o)) {
				changed = true;
			}
		}
		return changed;
	}
	@SuppressWarnings("unchecked")
	private T _deleteSlot(int i) {
        if (i>=0) {
        	Object oldValue = valueSlots[i];
        	deleteSlot(i);
        	return (T)oldValue;
        }
        return emptyValue();
    }
	

	public boolean containsAll(Collection<?> c) {
		for(Object o: c) {
			if(!contains(o)) {
				return false;
			}
		}
		return true;
	}


	public boolean retainAll(Collection<?> c) {
		boolean changed = false;
		for(int i=0; i<slotSize; ) {
			Object v = valueSlots[i];
			if(!c.contains(v)) {
				removeSlot(i);
			} else {
				i++;
			}
		}
		return changed;
	}

	public long indexOf(Object value) {
        for (int i = 0; i<slotSize; i++) {
            if (valueSlots[i] == value) {
                return getIndexAt(i);
            }
        }
        return -1;
	}

	public long lastIndexOf(Object value) {
        for (int i = slotSize-1; i >=0 ; i--) {
            if (valueSlots[i] == value) {
                return getIndexAt(i);
            }
        }
        return -1;
	}

	public ListIterator<T> listIterator() {
		return new SparseIterator(true);
	}

	public ListIterator<T> listIterator(int index) {
		return new SparseIterator(true,index);
	}

	public List<T> subList(long fromIndex, long toIndex) {
		throw new NotImplementedException();
	}

	public void iterateItems(ArrayItem consumer, long startIndex, long endIndex) {
    	int slot = searchKey(startIndex);
    	if(slot<0) {
    		slot = ~slot; // The first slot after
    	}
    	long index = startIndex;
        for (int i=slot; i<slotSize; i++) {
        	long l = getIndexAt(i);
        	if(l>=endIndex) {
        		break;
        	}
        	long emptyCount = Math.min(l,listSize)-index;
        	if(emptyCount>0) {
        		consumer.process(index,emptyCount,null);
        		index += emptyCount;
        	}
        	consumer.process(index++,0,valueSlots[i]);
        }
    	long remainingEmpty = endIndex-index;
    	if(remainingEmpty>0) {
    		consumer.process(index,remainingEmpty,null);
    	}
	}
	
	
	public void forEach(EntryConsumer c, boolean emptyItems, Object emptyValue) {
		forEachWhile( (i,v) -> {c.process(i,v); return true;}, 0, emptyItems, emptyValue);
	}
	public void forEach(EntryConsumer c, long start, boolean emptyItems, Object emptyValue) {
		forEachWhile( (i,v) -> {c.process(i,v); return true;}, start, emptyItems, emptyValue);
	}
	public boolean forEachWhile(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue) {
        if(start>=0 && start<listSize) {
        	if(emptyItems) {
	        	int slot = searchKey(start);
	        	if(slot<0) {
	        		slot = ~slot; // The first slot after
	        	}
	        	long index = start;
	            for (int i=slot; i<slotSize; i++) {
	        		while(index<getIndexAt(i)) {
	        			if(!c.process(index++, emptyValue)) {
	        				return false;
	        			}
	        		}
        			if(!c.process(index++, valueSlots[i])) {
        				return false;
        			}
	            }
        		while(index<listSize) {
        			if(!c.process(index++, emptyValue)) {
        				return false;
        			}
        		}
        	} else {
        		int slot = 0;
        		if(start>0) {
    	        	slot = searchKey(start);
    	        	if(slot<0) {
    	        		slot = ~slot;
    	        	}
        		}
        		for(int i=slot; i<slotSize; i++) {
        			// keySlots[i] is a raw signed int - must go through
        			// getIndexAt()'s unsigned reinterpretation (a plain
        			// widening cast sign-extends, corrupting any index
        			// >= 2**31 into a negative long, e.g. 2**32-2 becomes
        			// -2 - confirmed via indexOf/15.4.4.14-5-12.js).
        			if(!c.process(getIndexAt(i), valueSlots[i])) {
        				return false;
        			}
        		}
        	}
        }
        return true;
	}
	
	public void forEachReverse(EntryConsumer c, boolean emptyItems, Object emptyValue) {
		forEachWhileReverse( (i,v) -> {c.process(i,v); return true;}, size(), emptyItems, emptyValue);
	}
	public void forEachReverse(EntryConsumer c, long start, boolean emptyItems, Object emptyValue) {
		forEachWhileReverse( (i,v) -> {c.process(i,v); return true;}, start, emptyItems, emptyValue );
	}
	public boolean forEachWhileReverse(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue) {
		if(start==listSize) {
			start = listSize-1;
		}
        if(start>=0 && start<listSize) {
        	if(emptyItems) {
        		int slot = slotSize-1;
        		if(start>0) {
    	        	slot = searchKey(start);
    	        	if(slot<0) {
    	        		slot = ~slot - 1; // The slot before where it should be inserted
    	        	}
        		}
        		slot = Math.min(slot,slotSize-1);
	        	long index = start;
	            for (int i=slot; i>=0; i--) {
	        		while(index>getIndexAt(i)) {
	        			if(!c.process(index--, emptyValue)) {
	        				return false;
	        			}
	        		}
        			if(!c.process(index--, valueSlots[i])) {
        				return false;
        			}
	            }
        		while(index>=0) {
        			if(!c.process(index--, emptyValue)) {
        				return false;
        			}
        		}
        	} else {
        		int slot = slotSize-1;
        		if(start>0) {
    	        	slot = searchKey(start);
    	        	if(slot<0) {
    	        		slot = ~slot;
    	        	}
        		}
        		slot = Math.min(slot,slotSize-1);
	            for (int i=slot; i>=0; i--) {
	            	// Must report the real sparse-array INDEX (keySlots[i]),
	            	// not the slot position i itself - the forward
	            	// forEachWhile's matching branch already does this
	            	// correctly; this reverse twin had a copy-paste bug
	            	// (confirmed while adding JSArrayImpl's new sparse-aware
	            	// arrayForEachWhileReverse fast path, which relies on
	            	// this method reporting genuine indices).
        			// keySlots[i] is a raw signed int - must go through
        			// getIndexAt()'s unsigned reinterpretation (a plain
        			// widening cast sign-extends, corrupting any index
        			// >= 2**31 into a negative long, e.g. 2**32-2 becomes
        			// -2 - confirmed via indexOf/15.4.4.14-5-12.js).
        			if(!c.process(getIndexAt(i), valueSlots[i])) {
        				return false;
        			}
        		}
        	}
        }
        return true;
	}
	
    public void replaceAll(UnaryOperator<T> operator) {
        Objects.requireNonNull(operator);
        final ListIterator<T> li = this.listIterator();
        while (li.hasNext()) {
            li.set(operator.apply(li.next()));
        }
    }
    @SuppressWarnings({ "unchecked" })
	public void sort(Comparator<? super T> c) {
    	// The empty slots go to the LOOP_COUNT...
    	// so we just reset the index
    	Arrays.sort((T[])valueSlots,0,slotSize,c);
		for(int i=0; i<slotSize; i++) {
			keySlots[i] = i;
		}
    }
    public boolean removeIf(Predicate<? super T> filter) {
        Objects.requireNonNull(filter);
        boolean removed = false;
        final Iterator<T> each = iterator();
        while (each.hasNext()) {
            if (filter.test(each.next())) {
                each.remove();
                removed = true;
            }
        }
        return removed;
    }
    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
    public Stream<T> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }
    @Override
	public Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), size(), 0);
    }
	
	
	private final class SparseIterator implements ListIterator<T> {

		private long current;
		private int keyIndex;
		private boolean emptyValues;
		// Index of the element last returned by next()/previous(), -1 if none
		// (or after remove()/add()): the target of set() and remove()
		private long lastReturned = -1;
		
		private SparseIterator(boolean emptyValues) {
			this.emptyValues = emptyValues;
		}
		private SparseIterator(boolean emptyValues, long index) {
			this.emptyValues = emptyValues;
			gotoIndex(index);
		}

		@SuppressWarnings("unchecked")
		private T gotoIndex(long index) {
			if(index>=0 && index<listSize) {
				current = index;
				int i = searchKey(index);
				if(i<0) {
					i = ~i;
				}
				if(i>=0) {
					keyIndex = i;
					//current = keySlots[i];
					return (T)valueSlots[i];							
				} else {
					i = ~i;
					keyIndex = i;
					//current = index;
					return emptyValue();
				}
			} else {
				current = listSize;
				return emptyValue();
			}
		}
		
		
		//
		// Basic iterator
		//
		
		@Override
		public boolean hasNext() {
			if(emptyValues) {
				return current<listSize;
			} else {
				return keyIndex<slotSize;
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public T next() {
			if(emptyValues) {
				if (current<listSize) {
					// Remaining items after the last known index
					lastReturned = current;
					if(keyIndex>=slotSize) {
						current++;
						return emptyValue();
						
					}
					long k = getIndexAt(keyIndex);
					// Current index
					if(current==k) { 
						current++;
						keyIndex++;
						return (T)valueSlots[keyIndex-1];
					}
					current++;
					return emptyValue();
				}
			} else {
				if (keyIndex<slotSize) {
					lastReturned = getIndexAt(keyIndex);
					current = lastReturned+1;
					return (T)valueSlots[keyIndex++];
				}
			}
			throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			if(lastReturned<0) {
				throw new IllegalStateException();
			}
			// The elements after the removed one shift down: the cursor ends up
			// on the removed position, whichever direction the iteration goes
			long pos = lastReturned;
			lastReturned = -1;
        	SparseList.this.remove(pos);
        	current = pos;
        	resync();
		}

		// Re-position keyIndex on the first slot at or after current
		private void resync() {
			if(current>=listSize) {
				current = listSize;
				keyIndex = slotSize;
				return;
			}
			int i = searchKey(current);
			keyIndex = i<0 ? ~i : i;
		}
		
		
		//
		// List Iterator
		//
        @Override
		public boolean hasPrevious() {
            return current>0;
        }

        @Override
		public T previous() {
        	if(current>0) {
        		// Not optimal but should work
        		current--;
        		lastReturned = current;
        		T v = SparseList.this.get(current);
        		resync();
        		return v;
        		
//        		current--;
//        		if(keyIndex>0 && keySlots[keyIndex]>current) {
//        			keyIndex--;
//        		}
//    			int k = keySlots[keyIndex];
//    			if(current<k) {
//    				current++;
//    				return null;
//    			}
//        		
        	}
			throw new NoSuchElementException();
        }

        @Override
		public int nextIndex() {
            return (int)Math.min(listSize,current+1);
        }

        @Override
		public int previousIndex() {
            return (int)Math.max(-1,current-1);
        }

        @Override
		public void set(T e) {
			if(lastReturned<0) {
				throw new IllegalStateException();
			}
			// May create a slot (a hole being set): re-position afterwards
        	SparseList.this.set(lastReturned,e);
        	resync();
        }

        @Override
		public void add(T e) {
			// Inserted before the cursor: next() is unaffected, previous() returns it
        	SparseList.this.add(current,e);
        	current++;
        	lastReturned = -1;
        	resync();
        }
	}
}