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
package tests.iterators;

import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.monflabs.util.iterators.CharIterator;
import org.monflabs.util.iterators.IntIterable;
import org.monflabs.util.iterators.IntIterator;
import org.monflabs.util.iterators.Iterables;
import org.monflabs.util.iterators.Iterators;
import org.monflabs.util.iterators.LongIterable;
import org.monflabs.util.iterators.LongIterator;

import tests.ProjectTestCase;

public class IteratorsTest extends ProjectTestCase {

	public void testSize() throws Exception {
		assertEquals(0, Iterators.size(Iterators.empty()));
		assertEquals(0, Iterators.size(Iterators.staticValues()));
		assertEquals(1, Iterators.size(Iterators.staticValues(11)));
		assertEquals(2, Iterators.size(Iterators.staticValues(11,22)));
	}

	public void testFirst() throws Exception {
		assertEquals(null, Iterators.first(Iterators.staticValues()));
		assertEquals(11, Iterators.first(Iterators.staticValues(11)).intValue());
		assertEquals(11, Iterators.first(Iterators.staticValues(11,22)).intValue());
	}

	public void testLast() throws Exception {
		assertEquals(null, Iterators.last(Iterators.staticValues()));
		assertEquals(11, Iterators.last(Iterators.staticValues(11)).intValue());
		assertEquals(22, Iterators.last(Iterators.staticValues(11,22)).intValue());
	}

	public void testGet() throws Exception {
		assertEquals(null, Iterators.get(Iterators.staticValues(),0));
		assertEquals(null, Iterators.get(Iterators.staticValues(),10));
		assertEquals(11, Iterators.get(Iterators.staticValues(11),0).intValue());
		assertEquals(11, Iterators.get(Iterators.staticValues(11,22),0).intValue());
		assertEquals(22, Iterators.get(Iterators.staticValues(11,22),1).intValue());
	}

	public void testCollect() throws Exception {
		assertEquals(List.of(), Iterators.collect(Iterators.staticValues()));
		assertEquals(List.of("a"), Iterators.collect(Iterators.staticValues("a")));
		assertEquals(List.of("b","c"), Iterators.collect(Iterators.staticValues("b","c")));
	}

	
	public void testEmptyIterator() throws Exception {
		Iterator<Object> it = Iterators.empty();
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testEmptyIterable() throws Exception {
		Iterator<Object> it = Iterables.empty().iterator();
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}

	public void testReadonlyIterator() throws Exception {
		List<Integer> l = List.of(3,4,5,6);

		int sum = 0;
		Iterator<Integer> it = Iterators.readOnly(l.iterator());
		for(int i=0; i<4; i++) {
			int v = it.next();
			assertEquals( i+3, v);
			assertThrows(Exception.class, () -> it.remove() );
			sum += v;
		}
		assertEquals(18,sum);
		try {
			it.next();
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testReadonlyListIterator() throws Exception {
		List<Integer> l = List.of(3,4,5,6);

		int sum = 0;
		Iterator<Integer> it = Iterators.readOnlyList(l);
		for(int i=0; i<4; i++) {
			int v = it.next();
			assertEquals( i+3, v);
			assertThrows(Exception.class, () -> it.remove() );
			sum += v;
		}
		assertEquals(18,sum);
		try {
			it.next();
			fail();
		} catch(NoSuchElementException ex) {}
	}

	public void testSingleIterator() throws Exception {
		Iterator<String> it = Iterators.single("ABC");
		String s = toString(it);
		assertEquals("ABC", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testSingleIterable() throws Exception {
		Iterator<String> it = Iterables.single("ABC").iterator();
		String s = toString(it);
		assertEquals("ABC", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	
	public void testStaticIterator() throws Exception {
		Iterator<Integer> it = Iterators.staticValues(1,8,4,7);
		String s = toString(it);
		assertEquals("1,8,4,7", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}

	public void testFilteredIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		
		Iterator<Integer> it = Iterators.filter(it1, (v) -> v!=2 && v!=4 );
		String s = toString(it);
		assertEquals("1,3,5", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testFilteredIteratorIdx() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		
		Iterator<Integer> it = Iterators.filter(it1, (v,i) -> v!=2 && v!=4 && v.intValue()==i+1);
		String s = toString(it);
		assertEquals("1,3,5", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}

	public void testWrapIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(2,1,3).iterator();
		
		Iterator<String> it = Iterators.map(it1, (v) -> v.equals(1)?"one":v.equals(2)?"two":"three" );
		String s = toString(it);
		assertEquals("two,one,three", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testWrapWithIndexIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(2,1,3).iterator();
		
		Iterator<String> it = Iterators.map(it1, (v) -> v.equals(1)?"one":v.equals(2)?"two":"three" );
		String s = toString(it);
		assertEquals("two,one,three", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testWrapperIterable() throws Exception {
		Iterable<Integer> it1 = Arrays.asList(2,1,3);
		
		Iterable<String> it = Iterables.map(it1, (v) -> v.equals(1)?"one":v.equals(2)?"two":"three" );
		String s = toString(it.iterator());
		assertEquals("two,one,three", s);
	}
	public void testWrapperWithIndexIterable() throws Exception {
		Iterable<Integer> it1 = Arrays.asList(2,1,3);
		
		Iterable<String> it = Iterables.map(it1, (v,i) -> v.equals(1)?"one":v.equals(2)?"two":"three" );
		String s = toString(it.iterator());
		assertEquals("two,one,three", s);
	}

	public void testConcatIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3).iterator();
		Iterator<Integer> it2 = Arrays.asList(4,5).iterator();
		Iterator<Integer> it3 = Arrays.asList(6,7,8).iterator();
		
		Iterator<Integer> it = Iterators.concat(null,it1,null,it2,it3,null);
		String s = toString(it);
		assertEquals("1,2,3,4,5,6,7,8", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}

		Iterator<Integer> r2 = Iterators.concat();
		assertTrue(r2==(Iterator<?>)Iterators.empty());
		String s2 = toString(r2);
		assertEquals("", s2);

		Iterator<Integer> r4 = Iterators.concat((Iterator<Integer>)null);
		assertTrue(r4==(Iterator<?>)Iterators.empty());
		String s4 = toString(r4);
		assertEquals("", s4);

		Iterator<Integer> it11 = Arrays.asList(1,2,3).iterator();
		Iterator<Integer> r3 = Iterators.concat(it11);
		assertTrue(r3==it11);
		String s3 = toString(r3);
		assertEquals("1,2,3", s3);
	}
	public void testConcatIterable() throws Exception {
		Iterable<Integer> it1 = Arrays.asList(1,2,3);
		Iterable<Integer> it2 = Arrays.asList(4,5);
		Iterable<Integer> it3 = Arrays.asList(6,7,8);
		
		Iterable<Integer> it = Iterables.concat(null,it1,null,it2,it3,null);
		String s = toString(it.iterator());
		assertEquals("1,2,3,4,5,6,7,8", s);
	}

	public void testSkipIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		String s1 = toString(Iterators.skip(it1,0));
		assertEquals("1,2,3,4,5", s1);
		
		Iterator<Integer> it2 = Arrays.asList(1,2,3,4,5).iterator();
		String s2 = toString(Iterators.skip(it2,3));
		assertEquals("4,5", s2);
		
		Iterator<Integer> it3 = Arrays.asList(1,2,3,4,5).iterator();
		String s3 = toString(Iterators.skip(it3,5));
		assertEquals("", s3);
	}
	public void testLimitIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		String s1 = toString(Iterators.limit(it1,0));
		assertEquals("", s1);
		
		Iterator<Integer> it2 = Arrays.asList(1,2,3,4,5).iterator();
		String s2 = toString(Iterators.limit(it2,3));
		assertEquals("1,2,3", s2);
		
		Iterator<Integer> it3 = Arrays.asList(1,2,3,4,5).iterator();
		String s3 = toString(Iterators.limit(it3,1000));
		assertEquals("1,2,3,4,5", s3);
	}
	public void testSkipLimitIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		String s1 = toString(Iterators.limit(Iterators.skip(it1,1),2));
		assertEquals("2,3", s1);
	}
	
	public void testForEachIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		StringBuilder b1 = new StringBuilder();
		Iterators.forEach(it1,(v,i) -> b1.append('[').append(v).append(']'));
		assertEquals("[1][2][3][4][5]", b1.toString());
		
		Iterator<Integer> it2 = Arrays.asList(1,2,3,4,5).iterator();
		StringBuilder b2 = new StringBuilder();
		Iterators.forEach(it2,(v,i) -> b2.append('[').append(i*10).append(']'));
		assertEquals("[0][10][20][30][40]", b2.toString());
	}
	
	public void testFindIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		Integer v1 = Iterators.find(it1,(v,i) -> v.intValue()==2, -1 );
		assertEquals(2,v1.intValue());

		Iterator<Integer> it2 = Arrays.asList(1,2,3,4,5).iterator();
		Integer v2 = Iterators.find(it2,(v,i) -> v.intValue()==99, -1 );
		assertEquals(-1,v2.intValue());
	}
	
	public void testReduceIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		Integer v1 = Iterators.reduce(it1, (a,v,i) -> a.intValue() + v.intValue(), 0);
		assertEquals(1+2+3+4+5,v1.intValue());
	}
	
	public void testEntryIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		boolean r1 = Iterators.every(it1,(v,i) -> v.intValue()<6 );
		assertTrue(r1);

		Iterator<Integer> it2 = Arrays.asList(1,2,3,4,5).iterator();
		boolean r2 = Iterators.every(it2,(v,i) -> v.intValue()<2 );
		assertFalse(r2);

		Iterator<Integer> it3 = Arrays.asList(1,2,3,4,5).iterator();
		boolean r3 = Iterators.every(it3,(v,i) -> v.intValue()<0 );
		assertFalse(r3);
	}
	
	public void testSomeIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4,5).iterator();
		boolean r1 = Iterators.some(it1,(v,i) -> v.intValue()>3 );
		assertTrue(r1);

		Iterator<Integer> it2 = Arrays.asList(1,2,3,4,5).iterator();
		boolean r2 = Iterators.some(it2,(v,i) -> v.intValue()>6 );
		assertFalse(r2);
	}

	
	public void testArrayIterator() throws Exception {
		Iterator<String> it1 = Iterators.array("A","B","C");

		String s = toString(it1);
		assertEquals("A,B,C", s);

		assertFalse(it1.hasNext());
		try {
			assertNull(it1.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testArrayReflectionIterator() throws Exception {
		Iterator<String> it1 = Iterators.array("A","B","C");

		String s = toString(it1);
		assertEquals("A,B,C", s);

		assertFalse(it1.hasNext());
		try {
			assertNull(it1.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}

	public void testEnumerationIterator() throws Exception {
		Vector<Object> v = new Vector<>();
		v.addAll(Arrays.asList("A","B","C"));

		Iterator<Object> it1 = Iterators.enumeration(v.elements());

		String s = toString(it1);
		assertEquals("A,B,C", s);

		assertFalse(it1.hasNext());
		try {
			assertNull(it1.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}

	public void testCharSequenceIterator() throws Exception {
		CharIterator it1 = Iterators.charSequence("ABC");

		String s = toString(it1.objectIterator());
		assertEquals("A,B,C", s);

		assertFalse(it1.hasNext());
		try {
			assertNull(it1.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}

	public void testIntegerSequenceIterator() throws Exception {
		IntIterator it1 = Iterators.intSequence(3,6);

		String s = toString(it1.objectIterator());
		assertEquals("3,4,5", s);

		assertFalse(it1.hasNext());
		try {
			assertNull(it1.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testIntegerSequenceIterable() throws Exception {
		IntIterable it1 = Iterables.intSequence(3,6);

		String s = toString(Iterables.<String>map(it1,(v)->Integer.toString(v)));
		assertEquals("3,4,5", s);
	}
	
	public void testLongSequenceIterator() throws Exception {
		LongIterator it1 = Iterators.longSequence(3,6);

		String s = toString(it1.objectIterator());
		assertEquals("3,4,5", s);

		assertFalse(it1.hasNext());
		try {
			assertNull(it1.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testLongSequenceIterable() throws Exception {
		LongIterable it1 = Iterables.longSequence(3,6);

		String s = toString(Iterables.<String>map(it1,(v)->Long.toString(v)));
		assertEquals("3,4,5", s);
	}
	
	public void testNestedIterator1() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3).iterator();
		
		Iterator<String> it = Iterators.nested(it1, (o) -> {
			return Arrays.asList("A"+o.toString(),"B"+o.toString()).iterator();
		});
		String s = toString(it);
		assertEquals("A1,B1,A2,B2,A3,B3", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	public void testNestedIterator2() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3).iterator();
		
		Iterator<String> it = Iterators.nested(it1, (o) -> {
			return Arrays.asList("A"+o.toString(),"B"+o.toString()).iterator();
		}, (o) -> {
			return Arrays.asList("C"+o.toString(),"D"+o.toString(),"E"+o.toString()).iterator();
		});
		String s = toString(it);
		assertEquals("CA1,DA1,EA1,CB1,DB1,EB1,CA2,DA2,EA2,CB2,DB2,EB2,CA3,DA3,EA3,CB3,DB3,EB3", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}
	
	public void testFlattenedIterator() throws Exception {
		Iterator<Integer> it1 = Arrays.asList(1,2,3,4).iterator();
		
		Iterator<Integer> it = Iterators.flatten(it1, (o) -> {
			int v = (Integer)o;
			return v<10 && v%2==0 ? Arrays.asList(v+3,v+2).iterator() : v;
		});
		String s = toString(it);
		assertEquals("1,5,7,9,11,10,3,7,9,11,10", s);
		
		assertFalse(it.hasNext());
		try {
			assertNull(it.next());
			fail();
		} catch(NoSuchElementException ex) {}
	}

	
	private String toString(Iterable<?> it) {
		return toString(it.iterator());
	}
	private String toString(Iterator<?> it) {
		StringBuilder b = new StringBuilder();
		while (it.hasNext()) {
			if(!b.isEmpty()) {
				b.append(',');
			}
		    b.append(it.next().toString());
		}
		return b.toString();
	}

	public void testConcatIterableTwice() throws Exception {
		Iterable<Integer> it = Iterables.concat(Arrays.asList(1,2), Arrays.asList(3));
		// Each iterator() call must start over (the outer iterator used to be shared and exhausted)
		assertEquals(3, Iterators.size(it.iterator()));
		assertEquals(3, Iterators.size(it.iterator()));
	}

	public void testRemoveThroughSkipAndLimit() throws Exception {
		List<Integer> l = new java.util.ArrayList<>(Arrays.asList(1,2,3,4,5));
		Iterator<Integer> skip = Iterators.skip(l.iterator(), 1);
		skip.next(); // 2
		skip.next(); // 3
		skip.next(); // 4
		skip.next(); // 5, the last one: remove() used to be skipped because hasNext() was false
		skip.remove();
		assertEquals(Arrays.asList(1,2,3,4), l);

		l = new java.util.ArrayList<>(Arrays.asList(1,2,3));
		Iterator<Integer> limit = Iterators.limit(l.iterator(), 2);
		limit.next();
		limit.next();
		limit.remove();
		assertEquals(Arrays.asList(1,3), l);
	}

	public void testRemoveThroughFilter() throws Exception {
		List<Integer> l = new java.util.ArrayList<>(Arrays.asList(1,2,3,4));
		Iterator<Integer> f = Iterators.filter(l.iterator(), (Integer v) -> v%2==0);
		assertEquals(2, (int)f.next());
		f.remove();
		assertEquals(Arrays.asList(1,3,4), l);
		assertEquals(4, (int)f.next());
		// After a look-ahead the underlying iterator has moved on: refuse rather than remove the wrong element
		Iterator<Integer> f2 = Iterators.filter(l.iterator(), (Integer v) -> true);
		f2.next();
		f2.hasNext();
		assertThrows(IllegalStateException.class, () -> f2.remove());
	}

	public void testRemoveThroughConcat() throws Exception {
		List<Integer> l1 = new java.util.ArrayList<>(Arrays.asList(1,2));
		List<Integer> l2 = new java.util.ArrayList<>(Arrays.asList(3,4));
		Iterator<Integer> c = Iterators.concat(l1.iterator(), l2.iterator());
		assertThrows(IllegalStateException.class, () -> c.remove());
		c.next(); c.next();
		assertTrue(c.hasNext()); // advances to the second iterator
		c.remove();              // must still remove from the first one
		assertEquals(Arrays.asList(1), l1);
		assertEquals(Arrays.asList(3,4), l2);
		assertEquals(3, (int)c.next());
		c.remove();
		assertEquals(Arrays.asList(4), l2);
	}
}
