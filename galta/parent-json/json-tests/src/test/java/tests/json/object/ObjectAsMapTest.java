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
package tests.json.object;

import static org.junit.Assert.assertThrows;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.monflabs.json.JsonFactory;

import tests.ProjectTestCase;

public class ObjectAsMapTest extends ProjectTestCase {

	public void testPut() {
		Map<String,Object> m = JsonFactory.get().createObject();
		
		m.put("a",1);
		m.put("b",2);
		assertFalse(m.isEmpty());
		assertEquals(2,m.size());
		assertEquals(1,m.get("a"));
		assertEquals(2,m.get("b"));
		assertEquals(null,m.get("XY"));
		
		Map<String,Object> m2 = JsonFactory.get().createObject();
		m2.put("c",3);
		m2.put("d",4);
		m.putAll(m2);
		assertEquals(4,m.size());
		
		m.remove("b");
		assertEquals(3,m.size());
		assertEquals(1,m.get("a"));
		assertEquals(null,m.get("b"));
		assertEquals(3,m.get("c"));
		assertEquals(4,m.get("d"));
		
		m.clear();
		assertTrue(m.isEmpty());
		assertEquals(0,m.size());
	}
	
	public void testContains() {
		Map<String,Object> m = JsonFactory.get().createObject();
		
		m.put("a",1);
		m.put("b",2);
		m.put("c",3);
		m.put("d",4);

		assertTrue(m.containsKey("a"));
		assertFalse(m.containsKey("zz"));
		assertTrue(m.containsValue(3));
		assertFalse(m.containsValue(99));
	}
	
	public void testIterator() {
		Map<String,Object> m = JsonFactory.get().createObject();
		
		m.put("a",1);

		Iterator<String> it = m.keySet().iterator();
		assertEquals( "a", it.next() );
		assertThrows( NoSuchElementException.class, () -> it.next() );

		Iterator<Object> it2 = m.values().iterator();
		assertEquals( 1, it2.next() );
		assertThrows( NoSuchElementException.class, () -> it2.next() );

		Iterator<Map.Entry<String,Object>> it3 = m.entrySet().iterator();
		Map.Entry<String,Object> e = it3.next();
		assertEquals( "a", e.getKey() );
		assertEquals( 1, e.getValue() );
		assertThrows( NoSuchElementException.class, () -> it3.next() );
	}
}
;