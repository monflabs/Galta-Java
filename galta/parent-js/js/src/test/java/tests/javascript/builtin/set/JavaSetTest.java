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
package tests.javascript.builtin.set;

import static org.junit.Assert.assertArrayEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.monflabs.galtajs.rt.builtins.standard.set.BuiltinSet;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test JsonObjectMap and ensure it works like LinkedHashMap
 */
public class JavaSetTest extends JavaScriptStrictTestCase {
	
	public void testSet() {
		Set<Object> s = new BuiltinSet(getEnvironment());
		
		s.add(1);
		assertEquals( true, s.contains(1.0) );
	}
	public void testForEach() {
		Set<Object> s = new BuiltinSet(getEnvironment());
		s.add("a");
		s.add(1);
		s.add(true);
		List<Object> l = new ArrayList<Object>();
		s.forEach( v -> l.add(v) );
		l.sort( (a,b) -> a.toString().compareTo(b.toString()));
		assertArrayEquals(new Object[] {1,"a",true},l.toArray());
	}
	public void testRemoveIf() {
		Set<Object> s = new BuiltinSet(getEnvironment());
		s.add("a");
		s.add(1);
		s.add(2);
		s.add(true);
		s.removeIf( v -> v instanceof Number );
		assertEquals(2,s.size());
		assertTrue  ( s.contains("a") );
		assertTrue  ( s.contains(true) );
	}
	public void testRetainAll() {
		Set<Object> s = new BuiltinSet(getEnvironment());
		s.retainAll(Collections.emptyList());
		assertEquals(0,s.size());
		s.retainAll(List.of(1,2));
		assertEquals(0,s.size());

		List<Object> l1 = List.of(1.0,true,"a");

		s.add("a");
		s.add(1);
		s.add(true);
		s.retainAll(Collections.emptyList());
		assertEquals(0,s.size());

		s.add("a");
		s.add(1);
		s.add(false);
		assertEquals(3,s.size());

		s.add(2);
		s.retainAll(l1);
		assertEquals(2,s.size());
		assertEquals( true, s.contains("a") );
		assertEquals( false, s.contains(BigInteger.ONE) ); //does nothing
	}
	public void testEquals() {
		Set<Object> s1 = new BuiltinSet(getEnvironment());
		s1.add("a");
		s1.add(1);
		s1.add(2);
		s1.add(true);
		
		Set<Object> s2 = new BuiltinSet(getEnvironment());
		s2.add("a");
		s2.add(1.0);
		s2.add(true);
		s2.add(2.0);

		assertTrue(s1.equals(s1));
		assertTrue(s1.equals(s2));
		assertTrue(s2.equals(s1));

		s1.remove(BigDecimal.ONE); // Does nothing
		assertTrue(s1.equals(s2));
		assertTrue(s2.equals(s1));
	}
}
