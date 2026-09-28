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
package tests.javascript.util;


import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.util.SparseList;
import org.monflabs.json.JsonArray.EntryConsumer;
import org.monflabs.json.JsonArray.EntryConsumerWhile;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.iterators.LongIterator;

import tests.javascript.JavaScriptStrictTestCase;

public class SparseListTest extends JavaScriptStrictTestCase {
	
	private static final class PublicSparseArrayList<T> extends SparseList<T> {
    	static Field f_slotSize;
    	static Field f_keySlots;
    	static {
    		try {
	    		f_slotSize = SparseList.class.getDeclaredField("slotSize");
	    		f_slotSize.setAccessible(true);
	    		f_keySlots = SparseList.class.getDeclaredField("keySlots");
	    		f_keySlots.setAccessible(true);
    		} catch(Exception e) {
    			e.printStackTrace();
    		}
    	}
    	private int get_slotSize() {
    		try {
    			return f_slotSize.getInt(this);
    		} catch(Exception e) {
    			throw new IllegalStateException(e);
    		}
    	}
    	private int[] get_keySlots() {
    		try {
    			return (int[])f_keySlots.get(this);
    		} catch(Exception e) {
    			throw new IllegalStateException(e);
    		}
    	}
    	
		PublicSparseArrayList() {
		}
		public int slotSize() {
	        return get_slotSize();
	    }
		public int keyAt(int index) {
	        if(index<0 || index>=get_slotSize()) {
	            throw new IndexOutOfBoundsException(index);
	        }
	        return get_keySlots()[index];
	    }
	}

    public void testCreate() {
    	PublicSparseArrayList<Integer> a = new PublicSparseArrayList<Integer>();
    	assertEquals(0, a.slotSize());
    	assertEquals(0, a.size());
    	assertEquals("[]", a.toString());
    }

    public void testToString() {
    	PublicSparseArrayList<Integer> a = new PublicSparseArrayList<Integer>();
    	assertEquals("[]", a.toString());
    	a.add(11);
    	assertEquals("[ 11 ]", a.toString());
    	a.add(22);
    	assertEquals("[ 11, 22 ]", a.toString());
    	
    	PublicSparseArrayList<Integer> a1 = new PublicSparseArrayList<Integer>();
    	a1.setSize(1);
    	assertEquals("[ <1 empty item> ]", a1.toString());
    	a1.setSize(2);
    	assertEquals("[ <2 empty items> ]", a1.toString());
    	a1.add(11);
    	assertEquals("[ <2 empty items>, 11 ]", a1.toString());
    	a1.add(22);
    	assertEquals("[ <2 empty items>, 11, 22 ]", a1.toString());
    	a1.setSize(7);
    	assertEquals("[ <2 empty items>, 11, 22, <3 empty items> ]", a1.toString());

    	assertEquals("[ <1 empty item>, 11 ]", a1.toString(1,3));
    	assertEquals("[ <2 empty items>, 11 ]", a1.toString(0,3));
    	assertEquals("[ <1 empty item>, 11, 22, <1 empty item> ]", a1.toString(1,5));
    	assertEquals("[ <1 empty item>, 11, 22, <2 empty items> ]", a1.toString(1,6));
    }

    public void testAdd() {
    	PublicSparseArrayList<Integer> a = new PublicSparseArrayList<Integer>();
    	
    	a.add(11);
    	assertEquals(1, a.slotSize());
    	assertEquals(1, a.size());
    	//assertEquals("[ 11 ]", a.toString());
    	    	
    	a.add(22);
    	assertEquals(2, a.slotSize());
    	assertEquals(2, a.size());
    	assertEquals("[ 11, 22 ]", a.toString());
    	
    	
    	a.put(6,66);
    	
    	a.add(99);
    	assertEquals(4, a.slotSize());
    	assertEquals(8, a.size());
    	assertEquals("[ 11, 22, <4 empty items>, 66, 99 ]", a.toString());
    }
    public void testAddAll() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	
    	a.add(11);
    	a.put(3,33);
    	assertEquals("[ 11, <2 empty items>, 33 ]", a.toString());
    	
    	a.addAll(List.of(66,77));
    	assertEquals("[ 11, <2 empty items>, 33, 66, 77 ]", a.toString());

    	a.setSize(8);
    	assertEquals("[ 11, <2 empty items>, 33, 66, 77, <2 empty items> ]", a.toString());

    	a.addAll(List.of(88,99));
    	assertEquals("[ 11, <2 empty items>, 33, 66, 77, <2 empty items>, 88, 99 ]", a.toString());
    	
    	SparseList<Integer> ac = new SparseList<Integer>();
    	ac.add(11);
    	ac.add(22);
    	assertFalse( ac.addAll( Collections.emptyList() ));
    	assertEquals("[ 11, 22 ]", ac.toString());
    	assertTrue( ac.addAll( List.of(33,44) ));
    	assertEquals("[ 11, 22, 33, 44 ]", ac.toString());
    }

    public void testAddAt() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	
    	a.add(0,55);
    	assertEquals("[ 55, 11, 22, 33 ]", a.toString());
    	assertEquals(4, a.size());
    	
    	a.add(2,66);
    	assertEquals("[ 55, 11, 66, 22, 33 ]", a.toString());
    	assertEquals(5, a.size());
    	
    	a.setSize(8);
    	assertEquals("[ 55, 11, 66, 22, 33, <3 empty items> ]", a.toString());
    	a.add(2,44);
    	assertEquals("[ 55, 11, 44, 66, 22, 33, <3 empty items> ]", a.toString());
    }
    public void testAddAllAt() {
    	SparseList<Integer> ac = new SparseList<Integer>();
    	ac.add(11);
    	ac.add(22);
    	assertFalse( ac.addAll( 0, Collections.emptyList() ));
    	assertEquals("[ 11, 22 ]", ac.toString());
    	assertTrue( ac.addAll( 1, List.of(33,44) ));
    	assertEquals("[ 11, 33, 44, 22 ]", ac.toString());
    	assertTrue( ac.addAll( 8, List.of(66) ));
    	assertEquals("[ 11, 33, 44, 22, <4 empty items>, 66 ]", ac.toString());
    }

    public void testPut() {
    	PublicSparseArrayList<Integer> a = new PublicSparseArrayList<Integer>();
    	
    	a.put(0,11);
    	assertEquals(1, a.slotSize());
    	assertEquals(1, a.size());
    	assertEquals("[ 11 ]", a.toString());
    	
    	a.put(3,33);
    	assertEquals(2, a.slotSize());
    	assertEquals(4, a.size());
    	assertEquals("[ 11, <2 empty items>, 33 ]", a.toString());
    	
    	a.put(5,55);
    	assertEquals(3, a.slotSize());
    	assertEquals(6, a.size());
    	assertEquals("[ 11, <2 empty items>, 33, <1 empty item>, 55 ]", a.toString());
    	
    	a.put(350, 67);
    	assertEquals(4, a.slotSize());
    	assertEquals(351, a.size());
    	assertEquals("[ 11, <2 empty items>, 33, <1 empty item>, 55, <344 empty items>, 67 ]", a.toString());

    	assertEquals(11, a.get(0).intValue());
    	assertEquals(RuntimeUtil.UNDEFINED, a.get(1));
    	assertEquals(RuntimeUtil.UNDEFINED, a.get(2));
    	assertEquals(33, a.get(3).intValue());
    	
    	a.remove(3);
    	assertEquals(3, a.slotSize());
    	assertEquals(350, a.size());
    	assertEquals("[ 11, <3 empty items>, 55, <344 empty items>, 67 ]", a.toString());
    }
    

    public void testSet() {
    	PublicSparseArrayList<Integer> a = new PublicSparseArrayList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	
    	a.set(0, 44);
    	assertEquals("[ 44, 22, 33 ]", a.toString());
    	
    	assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1,null));
    	assertThrows(IndexOutOfBoundsException.class, () -> a.set(3,null));
    }
    
    public void testIndexOf() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(8,22);
    	assertEquals( 0, a.indexOf(11) );
    	assertEquals( 1, a.indexOf(22) );
    	assertTrue  ( a.indexOf(55)<0 );

    	assertEquals( 0, a.lastIndexOf(11) );
    	assertEquals( 8, a.lastIndexOf(22) );
    	assertTrue  ( a.lastIndexOf(55)<0 );
    }
    
    public void testContains() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(8,44);
    	assertTrue( a.contains(22) );
    	assertFalse( a.contains(55) );
    }
    
    public void testContainsAll() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(8,44);
    	assertTrue( a.containsAll(Collections.emptyList()) );
    	assertTrue( a.containsAll(List.of(22)) );
    	assertTrue( a.containsAll(List.of(22,33)) );
    	assertTrue( a.containsAll(List.of(22,33,44)) );
    	assertFalse( a.containsAll(List.of(22,55)) );
    	assertFalse( a.containsAll(List.of(55)) );
    }

    public void testClear() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	assertEquals("[ 11, 22, 33 ]", a.toString());
    	a.clear();
    	assertEquals("[]", a.toString());
    }

    public void testRemove() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	assertEquals("[ 11, 22, 33, 44 ]", a.toString());
    	assertEquals( (Integer)11, a.remove(0) );
    	assertEquals("[ 22, 33, 44 ]", a.toString());
    	a.remove(2);
    	assertEquals("[ 22, 33 ]", a.toString());
    	a.remove(8);
    	assertEquals("[ 22, 33 ]", a.toString());
    	a.setSize(8);
    	assertEquals("[ 22, 33, <6 empty items> ]", a.toString());
    	a.remove(6);
    	assertEquals("[ 22, 33, <5 empty items> ]", a.toString());
    	a.remove(1);
    	assertEquals("[ 22, <5 empty items> ]", a.toString());

    	SparseList<Integer> a2 = new SparseList<Integer>();
    	a2.add(11);
    	a2.add(22);
    	a2.add(33);
    	a2.remove((Integer)22);
    	assertEquals("[ 11, 33 ]", a2.toString());
    	a2.remove((Integer)55);
    	assertEquals("[ 11, 33 ]", a2.toString());
    }

    public void testRemoveRange() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	a.remove(0,2 );
    	assertEquals("[ 33, 44 ]", a.toString());
    	assertEquals(2, a.size());
    	a.add(55);
    	a.add(66);
    	a.add(77);
    	a.remove(0,1);
    	assertEquals("[ 44, 55, 66, 77 ]", a.toString());
    	a.put(5,88);
    	a.add(99);
    	assertEquals("[ 44, 55, 66, 77, <1 empty item>, 88, 99 ]", a.toString());
    	a.remove(3,5);
    	assertEquals("[ 44, 55, 66, 88, 99 ]", a.toString());
    	a.put(15,12);
    	assertEquals("[ 44, 55, 66, 88, 99, <10 empty items>, 12 ]", a.toString());
    	a.remove(8,8);
    	assertEquals("[ 44, 55, 66, 88, 99, <10 empty items>, 12 ]", a.toString());
    	a.remove(8,11);
    	assertEquals("[ 44, 55, 66, 88, 99, <7 empty items>, 12 ]", a.toString());
    	a.remove(12,13);
    	assertEquals("[ 44, 55, 66, 88, 99, <7 empty items> ]", a.toString());
    }

    public void testRemoveAll() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	assertFalse( a.removeAll(Collections.emptyList()));
    	assertEquals("[ 11, 22, 33, 44 ]", a.toString());
    	assertTrue( a.removeAll(List.of(22) ));
    	assertEquals("[ 11, 33, 44 ]", a.toString());
    	assertTrue( a.removeAll(List.of(11,44) ));
    	assertEquals("[ 33 ]", a.toString());
    }

    public void testRetainAll() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	assertFalse( a.retainAll(List.of(11,22,33,44) ));
    	assertEquals("[ 11, 22, 33, 44 ]", a.toString());
    	assertFalse( a.retainAll(List.of(11,22,44) ));
    	assertEquals("[ 11, 22, 44 ]", a.toString());
    	assertFalse( a.retainAll(List.of(22) ));
    	assertEquals("[ 22 ]", a.toString());
    }
    
    public void testDelete() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	assertEquals("[ 11, 22, 33, 44 ]", a.toString());
    	assertEquals( (Integer)11, a.delete(0) );
    	assertEquals("[ <1 empty item>, 22, 33, 44 ]", a.toString());
    	a.delete(2);
    	assertEquals("[ <1 empty item>, 22, <1 empty item>, 44 ]", a.toString());
    	a.delete(8);
    	assertEquals("[ <1 empty item>, 22, <1 empty item>, 44 ]", a.toString());
    	a.delete(1);
    	assertEquals("[ <3 empty items>, 44 ]", a.toString());
    	a.setSize(8);
    	assertEquals("[ <3 empty items>, 44, <4 empty items> ]", a.toString());
    	a.delete(6);
    	assertEquals("[ <3 empty items>, 44, <4 empty items> ]", a.toString());
    	a.delete(3);
    	assertEquals("[ <8 empty items> ]", a.toString());

    	SparseList<Integer> a2 = new SparseList<Integer>();
    	a2.add(11);
    	a2.add(22);
    	a2.add(33);
    	a2.delete((Integer)22);
    	assertEquals("[ 11, <1 empty item>, 33 ]", a2.toString());
    	a2.delete((Integer)55);
    	assertEquals("[ 11, <1 empty item>, 33 ]", a2.toString());
    }

    public void testDeleteRange() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	a.delete(0,2 );
    	assertEquals("[ <2 empty items>, 33, 44 ]", a.toString());
    	assertEquals(4, a.size());
    	a.delete(3,4 );
    	assertEquals("[ <2 empty items>, 33, <1 empty item> ]", a.toString());
    	a.put(10,88);
    	a.add(99);
    	assertEquals("[ <2 empty items>, 33, <7 empty items>, 88, 99 ]", a.toString());
    	a.delete(3,6 );
    	assertEquals("[ <2 empty items>, 33, <7 empty items>, 88, 99 ]", a.toString());
    	a.delete(11,12);
    	assertEquals("[ <2 empty items>, 33, <7 empty items>, 88, <1 empty item> ]", a.toString());
    }

    public void testDeleteAll() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	assertFalse( a.deleteAll(Collections.emptyList()));
    	assertEquals("[ 11, 22, 33, 44 ]", a.toString());
    	assertTrue( a.deleteAll(List.of(22) ));
    	assertEquals("[ 11, <1 empty item>, 33, 44 ]", a.toString());
    	assertTrue( a.deleteAll(List.of(11,44) ));
    	assertEquals("[ <2 empty items>, 33, <1 empty item> ]", a.toString());
    }
    
    public void testSize() {
    	PublicSparseArrayList<Integer> a = new PublicSparseArrayList<Integer>();
    	assertEquals("", dumpKeys(a));
    	a.put(0,11);
    	assertEquals("0", dumpKeys(a));
    	a.put(1,22);
    	assertEquals("0,1", dumpKeys(a));

    	a.setSize(5);
    	assertEquals("0,1", dumpKeys(a));
    	
    	a.setSize(0);
    	assertEquals("", dumpKeys(a));

    	a.setSize(5);
    	assertEquals("", dumpKeys(a));

    	a.put(6,33);
    	assertEquals("6", dumpKeys(a));
    	assertEquals(7, a.size());
    	
    	PublicSparseArrayList<Integer> a2 = new PublicSparseArrayList<Integer>();
    	a2.put(1,11);
    	a2.put(2,22);
    	assertEquals("1,2", dumpKeys(a2));
    	a2.put(8,88);
    	assertEquals("1,2,8", dumpKeys(a2));
    	a2.setSize(5);
    	assertEquals("1,2", dumpKeys(a2));
    	assertEquals(5, a2.size());
    	a2.setSize(5);
    }
    

    public void testSort() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(22);
    	a.add(33);
    	a.add(11);
    	a.add(44);
    	a.sort( (i1,i2) -> i1-i2 );
    	assertEquals("[ 11, 22, 33, 44 ]", a.toString());

    	SparseList<Integer> a1 = new SparseList<Integer>();
    	a1.put(2,33);
    	a1.put(3,44);
    	a1.put(4,22);
    	a1.put(6,11);
    	a1.setSize(9);
    	a1.sort( (i1,i2) -> i1-i2 );
    	assertEquals("[ 11, 22, 33, 44, <5 empty items> ]", a1.toString());
    }

    
    public void testIterateItems() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.put(2,22);
    	a.put(3,33);
    	a.put(6,66);
    	a.put(7,77);
    	a.put(11,111);
    	a.setSize(14);
    	
    	assertEquals("~2,22,33,~2,66,77,~3,111,~2", iterateItems(a,0,a.size()));
    	assertEquals("~1,22,33,~2,66,77,~3,111,~1", iterateItems(a,1,a.size()-1));
    	assertEquals("22,33,~2,66,77,~3,111,~1", iterateItems(a,2,a.size()-1));
    	assertEquals("22,33,~2,66,77,~3,111", iterateItems(a,2,a.size()-2));
    	assertEquals("33,~2,66,77,~3", iterateItems(a,3,a.size()-3));
    	assertEquals("33,~2,66,77,~2", iterateItems(a,3,a.size()-4));
    }
    private static String iterateItems(SparseList<?> l, long startIndex, long endIndex) {
    	StringBuilder b = new StringBuilder();
    	l.iterateItems( (index,empty,value) -> {
    		if(b.length()>0) {
    			b.append(",");
    		}
    		if(empty>0) {
    			b.append("~"+empty);
    		} else {
    			b.append(value);
    		}
        	return true;
    	}, startIndex, endIndex);
    	return b.toString();
    }
    	
    public void testSizeLimits() {
    	// Max size
    	PublicSparseArrayList<Integer> a1 = new PublicSparseArrayList<Integer>();
    	a1.setSize(0xFFFFFFFFL);
    	assertEquals(0xFFFFFFFFL, a1.size());
    	assertThrows(IllegalArgumentException.class, () -> a1.setSize(0x100000000L));
    	
    	// Indexed elements
    	PublicSparseArrayList<Integer> a2 = new PublicSparseArrayList<Integer>();
    	a2.put(0xFFFFFFFEL, 33);
    	assertEquals(Integer.valueOf(33), a2.get(0xFFFFFFFEL));
    	assertThrows(IndexOutOfBoundsException.class, () -> a2.set(0xFFFFFFFFL,44));
    	assertThrows(IndexOutOfBoundsException.class, () -> a2.get(0xFFFFFFFFL));
    	
    	// Add Elements
    	PublicSparseArrayList<Integer> a3 = new PublicSparseArrayList<Integer>();
    	a3.put(0xFFFFFFFDL, 11);
    	a3.add(22);
    	assertThrows(IndexOutOfBoundsException.class, () -> a3.add(33));
    	
    	// Add Elements
    	PublicSparseArrayList<Integer> a4 = new PublicSparseArrayList<Integer>();
    	a4.add(0xFFFFFFFEL,11);
    	assertThrows(IndexOutOfBoundsException.class, () -> a4.add(0xFFFFFFFEL,22));
    }
    private static String dumpKeys(PublicSparseArrayList<?> l) {
    	StringBuilder b = new StringBuilder();
    	int sz = l.slotSize();
    	for(int i=0; i<sz; i++) {
    		if(!b.isEmpty()) {
    			b.append(",");
    		}
    		b.append(Integer.toString(l.keyAt(i)));
    	}
    	return b.toString();
    }


    public void testIterator() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.put(2,11);
    	assertEquals("undefined,undefined,11", iteratorToString(a.iterator()));
    	assertEquals("11", iteratorToString(a.iterator(false)));
    	a.put(3,22);
    	assertEquals("undefined,undefined,11,22", iteratorToString(a.iterator()));
    	assertEquals("11,22", iteratorToString(a.iterator(false)));
    	a.put(5,44);
    	assertEquals("undefined,undefined,11,22,undefined,44", iteratorToString(a.iterator()));
    	assertEquals("11,22,44", iteratorToString(a.iterator(false)));
    	a.setSize(7);
    	assertEquals("undefined,undefined,11,22,undefined,44,undefined", iteratorToString(a.iterator()));
    	assertEquals("11,22,44", iteratorToString(a.iterator(false)));
    }
    public void testKeys() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.put(2,11);
    	assertEquals("0,1,2", iteratorToString(a.keys()));
    	assertEquals("2", iteratorToString(a.keys(false)));
    	a.put(3,22);
    	assertEquals("0,1,2,3", iteratorToString(a.keys()));
    	assertEquals("2,3", iteratorToString(a.keys(false)));
    	a.put(5,44);
    	assertEquals("0,1,2,3,4,5", iteratorToString(a.keys()));
    	assertEquals("2,3,5", iteratorToString(a.keys(false)));
    	a.setSize(7);
    	assertEquals("0,1,2,3,4,5,6", iteratorToString(a.keys()));
    	assertEquals("2,3,5", iteratorToString(a.keys(false)));
    }

    public void testListIterator() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.put(0,11);
    	a.put(1,22);
    	a.put(4,55);
    	a.put(6,77);
    	a.put(7,88);
    	assertEquals("11,22,undefined,undefined,55,undefined,77,88", iteratorToString(a.iterator()));
    	
    	ListIterator<Integer> it = a.listIterator();
    	assertTrue(it.hasNext());
    	assertFalse(it.hasPrevious());
    	assertEquals( 11, it.next().intValue());
    	assertTrue(it.hasNext());
    	assertTrue(it.hasPrevious());
    	assertEquals( 11, it.previous().intValue());
    	assertTrue(it.hasNext());
    	assertFalse(it.hasPrevious());
    	
    	assertEquals( 11, it.next().intValue());
    	assertEquals( 22, it.next().intValue());
    	assertEquals( RuntimeUtil.UNDEFINED, it.next());
    	assertTrue(it.hasNext());
    	assertTrue(it.hasPrevious());
    	// ListIterator contract: previous() after next() returns the same element
    	assertEquals( RuntimeUtil.UNDEFINED, it.previous());
    	assertEquals( 22, it.previous().intValue());
    	assertEquals( 22, it.next().intValue());
    	
    	while(it.hasNext()) { it.next(); }
    	assertFalse(it.hasNext());
    	assertTrue(it.hasPrevious());
    	assertEquals( 88, it.previous().intValue());
    	while(it.hasPrevious()) { it.previous(); }
    	assertTrue(it.hasNext());
    	assertFalse(it.hasPrevious());
    	assertEquals( 11, it.next().intValue());
    }

    private static String iteratorToString(Iterator<?> it) {
    	StringBuilder b = new StringBuilder();
    	while(it.hasNext()) {
    		if(!b.isEmpty()) {
    			b.append(",");
    		}
    		Object o = it.next();
    		b.append(JsonUtil.encodeValue(o));
    	}
    	return b.toString();
    }
    private static String iteratorToString(LongIterator it) {
    	StringBuilder b = new StringBuilder();
    	while(it.hasNext()) {
    		if(!b.isEmpty()) {
    			b.append(",");
    		}
    		long l = it.next();
    		b.append(Long.toString(l));
    	}
    	return b.toString();
    }

    public void testForEach() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	assertEquals( "11, 22, 33", forEach(a::forEach,0,true));
    	assertEquals( "11, 22, 33", forEach(a::forEach,0,true));
    	assertEquals( "33, 22, 11", forEach(a::forEachReverse,a.size(),false));
    	assertEquals( "33, 22, 11", forEach(a::forEachReverse,a.size(),false));

    	a.put(6,66);
    	assertEquals( "11, 22, 33, undefined, undefined, undefined, 66", forEach(a::forEach,0,true));
    	assertEquals( "11, 22, 33, 66", forEach(a::forEach,0,false));

    	a.put(8,88);
    	assertEquals( "11, 22, 33, undefined, undefined, undefined, 66, undefined, 88", forEach(a::forEach,0,true));
    	assertEquals( "88, undefined, 66, undefined, undefined, undefined, 33, 22, 11", forEach(a::forEachReverse,a.size(),true));
    	assertEquals( "11, 22, 33, 66, 88", forEach(a::forEach,0,false));

    	assertEquals( "33, 66, 88", forEach(a::forEach,2,false));

    	a.add(0,0);
    	a.add(0,0);
    	a.delete(0);
    	a.delete(1);
    	assertEquals( "undefined, undefined, 11, 22, 33, undefined, undefined, undefined, 66, undefined, 88", forEach(a::forEach,0,true));
    	assertEquals( "88, undefined, 66, undefined, undefined, undefined, 33, 22, 11, undefined, undefined", forEach(a::forEachReverse,a.size(),true));
    	assertEquals( "11, 22, 33, 66, 88", forEach(a::forEach,0,false));
    	assertEquals( "88, 66, 33, 22, 11", forEach(a::forEachReverse,a.size(),false));
    }

    public void testForEachWhile() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	assertEquals( "11, 22", forEachWhile(a::forEachWhile,22,true,0));
    	assertEquals( "44, 33, 22", forEachWhile(a::forEachWhileReverse,22,true,a.size()));
    	
    	a.put(6,66);
    	assertEquals( "11, 22", forEachWhile(a::forEachWhile,22,true,0));
    	assertEquals( "66, undefined, undefined, 44, 33, 22", forEachWhile(a::forEachWhileReverse,22,true,a.size()));
    	
    	a.put(7,77);
    	a.put(8,66);
    	assertEquals( "11, 22, 33, 44, undefined, undefined, 66", forEachWhile(a::forEachWhile,66,true,0));
    	assertEquals( "33, 44, undefined, undefined, 66", forEachWhile(a::forEachWhile,66,true,2));
    	assertEquals( "undefined, undefined, 66", forEachWhile(a::forEachWhile,66,true,4));
    	assertEquals( "undefined, 66", forEachWhile(a::forEachWhile,66,true,5));
    	
    	assertEquals( "66", forEachWhile(a::forEachWhileReverse,66,true,a.size()));
    	assertEquals( "66, 77, 66, undefined, undefined, 44", forEachWhile(a::forEachWhileReverse,44,true,a.size()));
    	assertEquals( "77, 66, undefined, undefined, 44", forEachWhile(a::forEachWhileReverse,44,true,a.size()-2));
    	assertEquals( "undefined, undefined, 44", forEachWhile(a::forEachWhileReverse,44,true,a.size()-4));
    }
    
    private interface ForEach {
    	public void forEach(EntryConsumer c, long start, boolean emptyItems, Object emptyValue);
    }
    private static String forEach(ForEach iterator, long start, boolean emptyItems) {
    	StringBuilder b = new StringBuilder();
    	iterator.forEach( (index,value) -> {
			if(!b.isEmpty()) {
				b.append(", ");
			}
			b.append(JsonUtil.encodeValue(value));
    	},start,emptyItems,RuntimeUtil.UNDEFINED);
    	return b.toString();
    }
 
    
    private interface ForEachWhile {
    	public boolean forEachWhile(EntryConsumerWhile c, long start, boolean emptyItems, Object emptyValue);
    }
    private static String forEachWhile(ForEachWhile iterator, Object testValue,  boolean emptyItems, long start) {
    	StringBuilder b = new StringBuilder();
    	iterator.forEachWhile( (index,value) -> {
			if(!b.isEmpty()) {
				b.append(", ");
			}
			b.append(JsonUtil.encodeValue(value));
			return !testValue.equals(value);
    	},start,emptyItems,RuntimeUtil.UNDEFINED);
    	return b.toString();
    }
    

    public void testExtract() {
    	SparseList<Integer> a = new SparseList<Integer>();
    	a.add(11);
    	a.add(22);
    	a.add(33);
    	a.add(44);
    	assertEquals( "[ 11, 22, 33, 44 ]", a.extract(0,4).toString());
    	assertEquals( "[ 11, 22 ]", a.extract(0,2).toString());
    	assertEquals( "[ 33, 44 ]", a.extract(2,4).toString());
    	a.put(7,77);
    	a.put(12,112);
    	assertEquals( "[ 11, 22, 33, 44, <3 empty items>, 77, <4 empty items>, 112 ]", a.toString());
    	assertEquals( "[ 11, 22, 33, 44, <3 empty items>, 77, <4 empty items>, 112 ]", a.extract(0,13).toString());
    	assertEquals( "[ 11, 22, 33, 44, <2 empty items> ]", a.extract(0,6).toString());
    	assertEquals( "[ <3 empty items> ]", a.extract(4,7).toString());
    	assertEquals( "[ <3 empty items>, 77, <2 empty items> ]", a.extract(4,10).toString());
    }

    public void testNotSparse() {
    	SparseList<Integer> ns = new SparseList<Integer>();
    	assertFalse(ns.isActuallySparse());

    	SparseList<Integer> np = new SparseList<Integer>(3);
    	assertTrue(np.isActuallySparse());
    	np.put(0, 1);
    	assertTrue(np.isActuallySparse());
    	np.put(1, 2);
    	assertTrue(np.isActuallySparse());
    	np.put(2, 3);
    	assertFalse(np.isActuallySparse());
    }

}
