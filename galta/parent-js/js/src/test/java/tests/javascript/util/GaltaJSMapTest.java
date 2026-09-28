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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.jsonfactory.StringPropertyMap;
import org.monflabs.galtajs.rt.builtins.BaseGetter;
import org.monflabs.galtajs.rt.builtins.BaseSetter;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.util.iterators.Iterators;

import tests.javascript.JavaScriptStrictTestCase;
import util.Hashtable;

/**
 * Test JsonObjectMap and ensure it works like LinkedHashMap
 */
public class GaltaJSMapTest extends JavaScriptStrictTestCase {

	public static final boolean CHECK_LINKEDHASHMAP = true; // Test that the results are the same with LinkedHashMap
	
	public void testObjectMap() {
		checkObjectMap( () -> new StringPropertyMap() );
		if(CHECK_LINKEDHASHMAP) {
			checkObjectMap( () -> new LinkedHashMap<String,Object>() );
		}
	}
	private void checkObjectMap(Supplier<Map<String,Object>> f) {
		Map<String,Object> mn = f.get();
		assertEquals(0,mn.size());
		mn.put(null,44);
		assertEquals(1,mn.size());
		assertEquals(44,mn.get(null));

		Map<String,Object> m = f.get();
		assertEquals(0,m.size());
		assertTrue  (m.isEmpty());
		assertEquals(null,m.get("a"));
		assertEquals(66,m.getOrDefault("a",66));
		
		m.put("a",11);
		assertEquals(1,m.size());
		assertFalse (m.isEmpty());
		assertEquals(11,m.get("a"));
		assertEquals(66,m.getOrDefault("b",66));
		
		m.put("b",22);
		assertEquals(2,m.size());
		assertEquals(22,m.get("b"));
		
		m.put("b",23);
		assertEquals(2,m.size());
		assertEquals(23,m.get("b"));
		
		m.put("c",33);
		assertEquals(3,m.size());
		assertEquals(33,m.get("c"));

		m.put("d",40);
		m.put("e",41);
		m.put("f",42);
		m.put("g",43);
		m.put("h",44);
		m.put("i",45);
		m.put("j",46);
		m.put("k",47);
		m.put("l",48);
		m.put("m",49);
		assertEquals(13,m.size());
		
		assertEquals(true, m.containsKey("a"));
		assertEquals(true, m.containsKey("j"));
		assertEquals(false, m.containsKey("z"));

		assertEquals(true, m.containsValue(33));
		assertEquals(false, m.containsValue(99));

		Object old = m.remove("g");
		assertEquals(43,old);
		assertEquals(12,m.size());
		assertEquals(null,m.get("g"));
	}
	
	public void testIterators() {
		checkIterators( () -> new StringPropertyMap() );
		if(CHECK_LINKEDHASHMAP) {
			checkIterators( () -> new LinkedHashMap<String,Object>() );
		}
	}	
	private void checkIterators(Supplier<Map<String,Object>> f) {
		Map<String,Object> m = f.get();
		m.put("a",11);
		m.put("b",22);
		m.put("c",33);

		assertArrayEquals(new Object[]{"a","b","c"}, iterate(m.keySet()));
		assertArrayEquals(new Object[]{11,22,33}, iterate(m.values()));
		
		Object[] k = new Object[m.size()];
		Object[] v = new Object[m.size()];
		int i =0;
		for(Map.Entry<String,Object> e: m.entrySet()) {
			k[i] = e.getKey();
			v[i] = e.getValue();
			i++;
		}
		assertArrayEquals(new Object[]{"a","b","c"}, k);
		assertArrayEquals(new Object[]{11,22,33}, v);
		
		Iterator<?> it1=m.keySet().iterator(); it1.next(); it1.remove();
		assertArrayEquals(new Object[]{"b","c"}, iterate(m.keySet()));
		Iterator<?> it2=m.values().iterator(); it2.next(); it2.remove();
		assertArrayEquals(new Object[]{"c"}, iterate(m.keySet()));
		Iterator<?> it3=m.entrySet().iterator(); it3.next(); it3.remove();
		assertArrayEquals(new Object[]{}, iterate(m.keySet()));
		
		m.put("a",11);
		m.put("b",22);
		m.put("c",33);
		assertThrows(IllegalStateException.class, () -> m.keySet().iterator().remove());
		Iterator<?> it4=m.keySet().iterator();
		it4.next(); it4.remove();
		assertArrayEquals(new Object[]{"b","c"}, iterate(m.keySet()));
		assertThrows(IllegalStateException.class, ()-> it4.remove());
	}
	private Object[] iterate(Collection<?> c) {
		Object[] a = new Object[c.size()];
		int i = 0;
		for(Object v: c) {
			a[i++] = v;
		}
		return a;
	}
	
	public void testClone() {
		StringPropertyMap m = new StringPropertyMap();

		Map<String,Object> c1 = m.clone();
		assertEquals(0,c1.size());

		m.put("a",11);
		m.put("b",22);
		m.put("c",33);

		Map<String,Object> c2 = m.clone();
		assertEquals(3,c2.size());
		assertArrayEquals(new Object[]{"a","b","c"}, iterate(c2.keySet()));
	}

	public void testIterationList() {
		StringPropertyMap m = new StringPropertyMap();
		m.put("a",11);
		m.put("b",22);
		m.put("c",33);
		Iterator<Entry<String,Object>> i1=m.entrySet().iterator();
		assertArrayEquals(new Object[]{"a","b","c"}, Iterators.collectArray(Iterators.map(i1, (e) -> e.getKey()) ));
		
		Iterator<Entry<String,Object>> i2=m.entrySet().iterator();
		m.remove("b");
		assertArrayEquals(new Object[]{"a","c"}, Iterators.collectArray(Iterators.map(i2, (e) -> e.getKey()) ));
		m.clear();
		assertArrayEquals(new Object[]{}, Iterators.collectArray(Iterators.map(i2, (e) -> e.getKey()) ));
	}	


	public void testOrder() {
		StringPropertyMap m = new StringPropertyMap();
		m.put("c", 3);
		m.put("a", 1);
		m.put("b", 2);
		assertArrayEquals( new Object[]{"c","a","b"} , m.keySet().toArray() );
		m.put("a", 4);
		assertArrayEquals( new Object[]{"c","a","b"} , m.keySet().toArray() );

		assertArrayEquals( new Object[]{3,4,2} , m.values().toArray() );

		List<String> keys = new ArrayList<>();
		List<Object> values = new ArrayList<>();
		for(Entry<String,Object> e: m.entrySet()) {
			keys.add(e.getKey());
			values.add(e.getValue());
		}
		assertArrayEquals( new Object[]{"c","a","b"} , keys.toArray() );
		assertArrayEquals( new Object[]{3,4,2} , values.toArray() );
		
		// large list
		StringPropertyMap ml = new StringPropertyMap();
		for(int i=0;i<100;i++) {
			ml.put(Integer.toString(i), i);
		}
		for(int i=0; i<256; i++) { // Shuffle
			int idx = (int)(Math. random() * 100);
			ml.put(Integer.toString(idx), idx);
		}
		
		int i=0;
		for(String ki: ml.keySet()) {
			String t = Integer.toString(i++);
			assertEquals(t,ki);
		}
	}

	public void testGetterSetter() {
		StringPropertyMap m = new StringPropertyMap();

		AtomicInteger val = new AtomicInteger(66);
		assertEquals(66, val.get());

		m.put("vv",88, PropertyDescriptor.of(true,true,true,
				new BaseGetter(getEnvironment(),"vv",(t,k) -> val.get()),
				new BaseSetter(getEnvironment(),"vv",(t,k,v) -> { val.set(((Number)v).intValue()); return true;})),
				DESC_CHECK.CHECK,
				m);
		assertEquals(88, val.get());
		m.put("ww",99);
		
		assertEquals(88, val.get());
		assertEquals(88, m.get("vv"));
		assertEquals(99, m.get("ww"));
		
		m.put("vv",77); 
		assertEquals(77, val.get());
		assertEquals(77, m.get("vv"));
	}
	
	
	public void testMozilla() {
		Hashtable m = new Hashtable();
		m.put("a","11");
		m.put("b","22");
		assertArrayEquals(new Object[]{"a","b"}, iterateKeys(m.iterator()));
		
		var it1=m.iterator(); 
		it1.next(); 
		m.deleteEntry("a");
		m.deleteEntry("b");
		m.put("b","33");
		assertEquals("b",it1.next().key());
	}
	private Object[] iterateKeys(Iterator<Hashtable.Entry> entries) {
		List<Object> keys = new ArrayList<>();
		while(entries.hasNext()) {
			keys.add(entries.next().key());
		}
		return keys.toArray();
	}
}
