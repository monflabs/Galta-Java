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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.java.JavaJsonFactoryChecked;
import org.monflabs.json.model.JsonModelAccessor;

import tests.ProjectTestCase;

/**
 * The java.util.Map/List contracts of the JSON containers, and the checked factory.
 */
public class CollectionContractTest extends ProjectTestCase {

	public void testObjectEqualsIsSymmetricWithJdkMaps() {
		JsonObject o = JsonObject.parse("{\"a\":1,\"b\":\"x\",\"c\":null}");
		Map<String,Object> m = new LinkedHashMap<>();
		m.put("a", 1);
		m.put("b", "x");
		m.put("c", null);
		assertTrue(m.equals(o));
		assertTrue(o.equals(m));
		assertEquals(m.hashCode(), o.hashCode());
		Map<String,Object> h = new HashMap<>(m);
		assertTrue(o.equals(h) && h.equals(o));
		// A different value: not equal, either way
		h.put("a", 2);
		assertFalse(o.equals(h));
		assertFalse(h.equals(o));
		// With other JSON objects, the JSON equality (1 is 1.0)
		assertTrue(o.equals(JsonObject.parse("{\"a\":1.0,\"b\":\"x\",\"c\":null}")));
		assertFalse(o.equals("x"));
		assertFalse(o.equals(List.of()));
	}

	public void testArrayEqualsIsSymmetricWithJdkLists() {
		JsonArray a = JsonArray.of(1, "x", null, true);
		List<Object> l = new ArrayList<>();
		l.add(1);
		l.add("x");
		l.add(null);
		l.add(true);
		assertTrue(l.equals(a));
		assertTrue(a.equals(l));
		assertEquals(l.hashCode(), a.hashCode());
		l.set(0, 2);
		assertFalse(a.equals(l));
		assertFalse(l.equals(a));
		assertTrue(a.equals(JsonArray.of(1.0, "x", null, true)));
		assertEquals(JsonArray.of(1).hashCode(), List.of(1).hashCode());
		assertEquals(32, JsonArray.of(1).hashCode());
	}

	public void testNegativeIndexesOnListMethods() {
		JsonArray a = JsonArray.of("a","b","c");
		// java.util.List methods: the List contract
		assertThrows(IndexOutOfBoundsException.class, () -> a.get(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, (Object)"x"));
		assertThrows(IndexOutOfBoundsException.class, () -> a.add(-1, (Object)"x"));
		assertThrows(IndexOutOfBoundsException.class, () -> a.remove(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> a.listIterator(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> a.subList(-1, 1));
		// JSON accessors: from the end
		assertEquals("c", a.getString(-1));
		assertTrue(a.isString(-3));
		a.setValue(-1, "C");
		a.addValue(-1, "x");
		a.set(-1, "D");
		assertEquals("[\"a\",\"b\",\"x\",\"D\"]", a.stringify());
		assertEquals(3, a.actualIndex(-1));
	}

	public void testNullKeysAreRejected() {
		JsonObject o = JsonObject.create();
		assertThrows(NullPointerException.class, () -> o.put(null, 1));
		assertThrows(NullPointerException.class, () -> o.put((String)null, "s"));
		assertThrows(NullPointerException.class, () -> o.putIfAbsent(null, 1));
		assertThrows(NullPointerException.class, () -> o.merge(null, 1, (a,b) -> a));
		Map<String,Object> withNull = new HashMap<>();
		withNull.put(null, 1);
		assertThrows(NullPointerException.class, () -> o.putAll(withNull));
		assertTrue(o.isEmpty());
		// Looking for a null key is fine (and finds nothing)
		assertFalse(o.containsKey(null));
	}

	public void testCheckedObjectGuardsEveryMutator() {
		JsonObject o = JavaJsonFactoryChecked.instance.createObject();
		Object bad = new Date(0);
		o.put("a", 1);
		assertThrows(JsonException.class, () -> o.put("x", bad));
		assertThrows(JsonException.class, () -> o.putAll(Map.of("x", bad)));
		assertThrows(JsonException.class, () -> o.putIfAbsent("x", bad));
		assertThrows(JsonException.class, () -> o.replace("a", bad));
		assertThrows(JsonException.class, () -> o.replace("a", 1, bad));
		assertThrows(JsonException.class, () -> o.replaceAll((k,v) -> bad));
		assertThrows(JsonException.class, () -> o.computeIfAbsent("x", k -> bad));
		assertThrows(JsonException.class, () -> o.computeIfPresent("a", (k,v) -> bad));
		assertThrows(JsonException.class, () -> o.compute("a", (k,v) -> bad));
		assertThrows(JsonException.class, () -> o.merge("a", bad, (v1,v2) -> v2));
		assertThrows(JsonException.class, () -> o.merge("a", 2, (v1,v2) -> bad));
		Map.Entry<String,Object> e = o.entrySet().iterator().next();
		assertThrows(JsonException.class, () -> e.setValue(bad));
		// Nothing was stored
		assertEquals(JsonObject.of("a", 1), o);
		// The valid values go through
		o.putAll(Map.of("b", "x"));
		o.merge("a", 2, (v1,v2) -> ((Integer)v1)+((Integer)v2));
		o.entrySet().iterator().next().setValue(10);
		assertEquals(10, o.get("a"));
		assertEquals("x", o.get("b"));
		// The entry set is still a view
		Iterator<Map.Entry<String,Object>> it = o.entrySet().iterator();
		it.next();
		it.remove();
		assertFalse(o.containsKey("a"));
		assertEquals(1, o.entrySet().size());
	}

	public void testCheckedArrayGuardsEveryMutator() {
		JsonArray a = JavaJsonFactoryChecked.instance.createArray();
		Object bad = new Date(0);
		a.add(1);
		a.add(2);
		assertThrows(JsonException.class, () -> a.add((Object)bad));
		assertThrows(JsonException.class, () -> a.add(0, (Object)bad));
		assertThrows(JsonException.class, () -> a.set(0, (Object)bad));
		assertThrows(JsonException.class, () -> a.addAll(List.of(3, bad)));
		assertThrows(JsonException.class, () -> a.addAll(0, List.of(bad)));
		assertThrows(JsonException.class, () -> a.replaceAll(v -> bad));
		assertThrows(JsonException.class, () -> a.subList(0, 1).set(0, bad));
		assertThrows(JsonException.class, () -> a.subList(0, 1).add(bad));
		assertThrows(JsonException.class, () -> a.subList(0, 2).replaceAll(v -> bad));
		assertThrows(JsonException.class, () -> a.subList(0, 2).subList(0, 1).set(0, bad));
		ListIterator<Object> li = a.listIterator();
		li.next();
		assertThrows(JsonException.class, () -> li.set(bad));
		assertThrows(JsonException.class, () -> li.add(bad));
		assertEquals(JsonArray.of(1, 2), a);
		// The sub list is still a view
		List<Object> sub = a.subList(0, 1);
		sub.set(0, 10);
		sub.add(11);
		assertEquals(JsonArray.of(10, 11, 2), a);
		a.subList(0, 2).clear();
		assertEquals(JsonArray.of(2), a);
	}

	public void testSliceLargeSteps() {
		JsonArray a = JsonArray.of(0, 1, 2, 3);
		assertEquals(JsonArray.of(1), a.slice(1, 4, Integer.MAX_VALUE));
		assertEquals(JsonArray.of(3), a.slice(3, -10, Integer.MIN_VALUE+1));
		assertEquals(JsonArray.of(3, 1), a.slice(null, null, -2));
	}

	public void testModelAccessorIndexes() throws Exception {
		JsonModelAccessor acc = new JsonModelAccessor();
		JsonArray a = JsonArray.of("x", "y");
		assertEquals("y", acc.getMember(a, 1));
		// Like a missing member: null, no exception, no index from the end
		assertNull(acc.getMember(a, 2));
		assertNull(acc.getMember(a, -1));
		assertFalse(acc.putMember(a, -1, "z"));
		assertTrue(acc.putMember(a, 3, "w"));
		assertEquals(JsonArray.of("x", "y", null, "w"), a);
		assertNull(acc.getMember(JsonObject.create(), "a"));
	}
}
