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
package org.monflabs.galtajs.rt.util;


import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

import org.monflabs.util.Console;

/**
 * Weak Map that is not thread safe.
 */
public class WeakIdentityMap<K,V> {
	
    private static final int   MINIMAL_SIZE 	= 16;
    private static final int   MINIMAL_RESIZE 	= 1024;
    private static final float LOAD_FACTOR 		= 0.75f;

    private float loadFactor = LOAD_FACTOR;
    private ReferenceQueue<K> referenceQueue;
    
    private int elementCount;
    private WeakEntry<K,V>[] elementData;
    private int threshold;

    private int modCount;

    private final static class WeakEntry<K,V> extends WeakReference<K> implements Map.Entry<K, V> {
        private int hashCode;
        private V value;
        private Supplier<V> lazyInitializer;
        private WeakEntry<K,V> next;

        WeakEntry(int hashCode, K key, V value, Supplier<V> lazyInitializer, ReferenceQueue<K> queue) {
            super(key, queue);
            this.hashCode = hashCode;
            this.value = value;
            this.lazyInitializer = lazyInitializer;
        }
        
        @Override
		public K getKey() {
        	return get();
        }
        @Override
		public V getValue() {
        	if(lazyInitializer!=null) {
        		this.value = lazyInitializer.get();
        		this.lazyInitializer = null;
        	}
        	return value;
        }
        @Override
		public V setValue(V value) {
            V old = this.value;
            this.value = value;
    		this.lazyInitializer = null;
            return old;        
        }
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) other;
            Object key = super.get();
            return (key == entry.getKey())
                    && (value == null ? value == entry.getValue() : value
                    .equals(entry.getValue()));        
        }
        @Override
        public int hashCode() {
        	return hashCode + (value == null ? 0 : value.hashCode());
        }
    }

    public WeakIdentityMap() {
    	this.elementCount = 0;
        this.referenceQueue = new ReferenceQueue<K>();
    }
    
    public int size() {
    	return elementCount;
    }

	public boolean containsKey(K key) {
    	// containsKey() is used very frequently so we try to minimize its cost
    	// and thus we don't cleanup the map at that time.
    	//Object toRemove = referenceQueue.poll();
    	//if(toRemove!=null) {
    	//	cleanupMap((WeakEntry<K,V>)toRemove);
    	//}
    	if(elementCount>0) {
	        WeakEntry<K,V> entry = getEntry(key);
	        return entry!=null;
    	}
    	return false;
    }
    
    public V get(K key) {
    	return getOrDefault(key,null);
    }    
    @SuppressWarnings("unchecked")
	public V getOrDefault(K key, V defaultValue) {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
    	if(elementCount>0) {
	        WeakEntry<K,V> entry = getEntry(key);
	        if(entry!=null) {
	        	return entry.getValue();
	        }
    	}
    	return defaultValue;
    }
    @SuppressWarnings("unchecked")
    public V getOrCreate(K key, Supplier<V> supplier) {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
    	if(elementCount>0) {
	        WeakEntry<K,V> entry = getEntry(key);
	        if(entry!=null) {
	        	return entry.getValue();
	        }
    	}
    	WeakEntry<K,V> entry = _create(key,supplier.get(),null);
    	return entry.getValue();
    }
    private WeakEntry<K,V> getEntry(K key) {
    	if(elementData==null) { // Nothing put yet, or cleared
    		return null;
    	}
        int index = hash(key) & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
        WeakEntry<K,V> entry = elementData[index];
        while (entry != null) {
            if (key == entry.get()) {
                return entry;
            }
            entry = entry.next;
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
    	if(elementCount>0) {
	        WeakEntry<K,V> entry = getEntry(key);
	        if(entry!=null) {
	        	V oldValue = entry.value; // don't activate lazy initializer
	        	entry.value = value;
	        	entry.lazyInitializer = null;
	        	return oldValue;
	        }
    	}
    	_create(key,value,null);
    	return null;
    }
    @SuppressWarnings("unchecked")
    protected void create(K key, Supplier<V> lazyInitializer) {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
        _create(key,null,lazyInitializer);
    }
    private WeakEntry<K,V> _create(K key, V value, Supplier<V> lazyInitializer) {
        modCount++;
    	// Assumes it is not in the map yet!
        if (++elementCount > threshold) {
            rehash(elementCount,false);
        }
        int keyHashCode;
        int index = (keyHashCode=hash(key)) & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
        WeakEntry<K,V> entry = new WeakEntry<>(keyHashCode, key, value, lazyInitializer, referenceQueue);
        entry.next = elementData[index];
        elementData[index] = entry;
        return entry;
    }
    
    public void clear() {
    	this.elementCount = 0;
    	this.elementData = null;
        this.referenceQueue = new ReferenceQueue<K>();
        modCount++;
    }
    @SuppressWarnings("unchecked")
    public V remove(K key) {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
    	WeakEntry<K,V> e = getEntry(key);
    	if(e!=null) {
            modCount++;
    		removeEntry(e);
    		return e.value; // don't activate lazy initializer
    	}
    	return null;
    }

// Perf tests
//    int cc = 0;
    @SuppressWarnings("unchecked")
	private void cleanupMap(WeakEntry<K,V> toRemove) {
//    	if(++cc % 10000 == 0) {
//    		dumpStats();
//    	}
        removeEntry(toRemove);
        while ((toRemove = (WeakEntry<K,V>) referenceQueue.poll()) != null) {
            removeEntry(toRemove);
        }
        // Resize the map if it becomes to big for the actual entries
    	if((elementCount/loadFactor)<(elementData.length/2)) {
    		rehash(elementCount,true);
        }
    }
    private void removeEntry(WeakEntry<K,V> toRemove) {
        int index = toRemove.hashCode & elementData.length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
        WeakEntry<K,V> last = null;
        WeakEntry<K,V> entry = elementData[index];
        // Ignore queued entries which cannot be found, the user could
        // have removed them before they were queued, i.e. using clear()
        while (entry != null) {
            if (toRemove == entry) {
                modCount++;
                if (last == null) {
                    elementData[index] = entry.next;
                } else {
                    last.next = entry.next;
                }
                elementCount--;
                break;
            }
            last = entry;
            entry = entry.next;
        }
    }
    
    
    
    //
    // Iterators
    //
    
   @SuppressWarnings("unchecked")
   public Set<Map.Entry<K, V>> entrySet() {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
        return new AbstractSet<Map.Entry<K, V>>() {
            @Override
            public int size() {
                return WeakIdentityMap.this.size();
            }

            @Override
            public void clear() {
                WeakIdentityMap.this.clear();
            }
			@Override
            public boolean remove(Object object) {
                if (contains(object)) {
                    WeakIdentityMap.this.remove(((WeakEntry<K,V>) object).getKey());
                    return true;
                }
                return false;
            }
            @Override
            public boolean contains(Object object) {
                if (object instanceof Map.Entry<?,?> e) {
					WeakEntry<?, ?> entry = getEntry((K)e.getKey());
                    if (entry != null && entry.get() != null) {
                        return java.util.Objects.equals(entry.value, e.getValue());
                    }
                }
                return false;
            }
            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                return new BaseIterator<Map.Entry<K, V>>() {
                    @Override
					protected Entry<K,V> iteratorValue(WeakEntry<K,V> e) {
                    	return e;
                    }
                };
            }
        };
    }
   
    @SuppressWarnings("unchecked")    
    public Set<K> keySet() {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
        return new AbstractSet<K>() {
            @Override
            public int size() {
                return WeakIdentityMap.this.size();
            }

            @Override
            public void clear() {
                WeakIdentityMap.this.clear();
            }
			@Override
            public boolean remove(Object object) {
                if (contains(object)) {
                    WeakIdentityMap.this.remove((K) object);
                    return true;
                }
                return false;
            }
            @Override
            public boolean contains(Object object) {
            	// A key set holds keys, not entries
				WeakEntry<?, ?> entry = getEntry((K)object);
                return entry != null && entry.get() != null;
            }
            @Override
            public Iterator<K> iterator() {
                return new BaseIterator<K>() {
                    @Override
					protected K iteratorValue(WeakEntry<K,V> e) {
                    	return e.getKey();
                    }
                };
            }
        };
    }
    
    @SuppressWarnings("unchecked")
    public Collection<V> values() {
    	Object toRemove = referenceQueue.poll();
    	if(toRemove!=null) {
    		cleanupMap((WeakEntry<K,V>)toRemove);
    	}
    	return new AbstractCollection<V>() {
	        @Override
			public Iterator<V> iterator() {
                return new BaseIterator<V>() {
                    @Override
					protected V iteratorValue(WeakEntry<K,V> e) {
                    	return e.getValue();
                    }
                };
	        }
			@Override
	        public int size() {
	            return elementCount;
	        }
			@Override
	        public boolean isEmpty() {
	            return elementCount==0;
	        }
			@Override
	        public void clear() {
				WeakIdentityMap.this.clear();
	        }
			@Override
	        public boolean contains(Object v) {
				for(V item: this) {
					if(item==null) {
						return v==null;
					}
					if(item.equals(v)) {
						return true;
					}
				}
				return false;
	        }
    	};
    };
    
    
    private abstract class BaseIterator<R> implements Iterator<R> {
    	
        private int entrySlot;
        private WeakEntry<K, V> currentEntry;
        private WeakEntry<K, V> nextEntry;
        
        private int expectedModCount;

        BaseIterator() {
            expectedModCount = modCount;
            nextEntry = elementData[0];
        }
        
        protected abstract R iteratorValue(WeakEntry<K,V> e);

        @Override
        public boolean hasNext() {
            while (true) {
                if (nextEntry!=null) {
                    return true;
                } else {
                    if(entrySlot < elementData.length) {
                        nextEntry = elementData[entrySlot++];
                    } else {
                        return false;
                    }
                }
            }
        }

        @Override
        public R next() {
            if (expectedModCount!=modCount) {
                throw new ConcurrentModificationException();
            }
            if (nextEntry==null) {
                throw new NoSuchElementException();
            }
            currentEntry = nextEntry;
            nextEntry = currentEntry.next;
            return iteratorValue(currentEntry);
        }

        @Override
        public void remove() {
            if (expectedModCount!=modCount) {
                throw new ConcurrentModificationException();
            }
            if (currentEntry==null) {
                throw new IllegalStateException();
            }
        	// Do not cleamupMap() to not rehash.
            removeEntry(currentEntry);
            currentEntry = null;
            expectedModCount=modCount;
        }
    }
    

    //
    // Managing the EntryImpl list
    //
    private static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = System.identityHashCode(key)) ^ (h >>> 16);
    }
    @SuppressWarnings("unchecked")
	private void rehash(int newSize, boolean resize) {
    	int length = Math.max( resize ? MINIMAL_RESIZE : MINIMAL_SIZE, powerOfTwo((int)(newSize/loadFactor)) );
    	if(elementData==null || length!=elementData.length) {
	        WeakEntry<K,V>[] newData = (WeakEntry<K,V>[])new WeakEntry[length];
	        if(elementData!=null) {
		        for (WeakEntry<K,V> entry : elementData) {
		            while (entry != null) {
		                int index = entry.hashCode & length-1; // & works when power 2 (https://stackoverflow.com/questions/70089037/what-to-use-modulus-or-bitwise-and-operator-when-creating-an-implementation-of)
		                WeakEntry<K,V> next = entry.next;
		                entry.next = newData[index];
		                newData[index] = entry;
		                entry = next;
		            }
		        }
	        }
	        elementData = newData;
	    	this.threshold = (int)(length * loadFactor);
    		//dumpStats();
    	}
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
    	if(count>0) {
        	min = Integer.MAX_VALUE;
        	max = Integer.MIN_VALUE;
	    	for(int i=0; i<count; i++) {
	    		int c=0;
	    		for(WeakEntry<K,V> e=elementData[i]; e!=null; e=e.next) {
	    			c++;
	    		}
	    		min = Math.min(min,c);
	    		max = Math.max(max,c);
	    		total += c;
	    	}
	    	avg = ((double)total) / count;
    	}
    	Console.log("Map count={0}", elementCount);
    	Console.log("  Total entries={0}", total);
    	Console.log("  Map slots={0}", elementData.length);
    	Console.log("  Avg slot={0}", avg);
    	Console.log("  Min slot={0}", min);
    	Console.log("  Max slot={0}", max);
    }
}