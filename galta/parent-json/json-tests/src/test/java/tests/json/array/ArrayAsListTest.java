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
package tests.json.array;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.monflabs.json.JsonFactory;

import tests.ProjectTestCase;

public class ArrayAsListTest extends ProjectTestCase {

	public void testAdd() {
		List<Object> l = JsonFactory.get().createArray();
		
		l.add(2);
		l.add(3);		
		assertFalse(l.isEmpty());
		assertEquals(2,l.size());
		assertEquals(2,l.get(0));
		assertEquals(3,l.get(1));
		
		l.add(0, 1);
		assertEquals(3,l.size());
		
		List<Object> l2 = JsonFactory.get().createArray();
		l2.add(4);
		l2.add(5);
		l.addAll(l2);
		assertEquals(5,l.size());
		
		l.remove(0);
		assertEquals(4,l.size());
		assertEquals(2,l.get(0));

		l.addAll(0,l2);
		assertEquals(6,l.size());
		assertEquals(4,l.get(0));
		assertEquals(5,l.get(1));
		
		l.clear();
		assertTrue(l.isEmpty());
		assertEquals(0,l.size());
	}
	
	public void testContains() {
		List<Object> l = JsonFactory.get().createArray();
		l.add(2);
		l.add(3);		
		l.add(4);
		l.add(3);

		assertEquals(-1,l.indexOf(99));
		assertEquals(1,l.indexOf(3));
		assertEquals(3,l.lastIndexOf(3));
		assertTrue(l.contains(3));

		List<Object> l2 = JsonFactory.get().createArray();
		l2.add(2);
		l2.add(3);		
		assertTrue(l.containsAll(l2));
		l2.set(1,99);		
		assertFalse(l.containsAll(l2));
	}
	
	public void testJavaArray() {
		List<Object> l = JsonFactory.get().createArray();
		l.add(2);
		l.add(3);		
		l.add(4);

		assertArrayEquals(new Object[] {2,3,4},l.toArray());
		assertArrayEquals(new Integer[] {2,3,4},l.toArray(new Integer[3]));
	}
	
	public void testIterator() {
		List<Object> l = JsonFactory.get().createArray();
		l.add(2);
		l.add(3);		

		Iterator<Object> it = l.iterator();
		assertEquals( 2, it.next() );
		assertEquals( 3, it.next() );
		assertThrows( NoSuchElementException.class, () -> it.next() );
	}
}
