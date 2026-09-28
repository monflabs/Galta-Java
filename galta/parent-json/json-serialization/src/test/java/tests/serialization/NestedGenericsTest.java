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
package tests.serialization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

/**
 * Nested generics, wildcards, raw types and type variables as type arguments (A2).
 */
public class NestedGenericsTest extends ProjectTestCase {

	public static class Foo {
		String a;
		public Foo() {
		}
		public Foo(String a) {
			this.a = a;
		}
	}

	public static class Nested {
		List<Map<String,Foo>> list;
		Map<String,List<Foo>> map;
		public Nested() {
		}
	}

	public static class Wildcards {
		List<? extends Foo> extendsList;
		List<?> anyList;
		public Wildcards() {
		}
	}

	public static class RawTypes {
		@SuppressWarnings("rawtypes")
		List rawList;
		@SuppressWarnings("rawtypes")
		Map rawMap;
		public RawTypes() {
		}
	}

	public static class Holder<T> {
		List<T> items;
		Map<String,T> byName;
		public Holder() {
		}
	}
	public static class Main {
		Holder<Foo> holder;
		public Main() {
		}
	}

	private SimpleRegistry newRegistry() {
		return SimpleRegistry.newBuilder()
				.add(Foo.class)
				.add(Nested.class)
				.add(Wildcards.class)
				.add(RawTypes.class)
				.add(Holder.class)
				.add(Main.class)
				.build();
	}

	public void testNestedParameterizedTypes() throws Exception {
		SimpleRegistry reg = newRegistry();

		Nested n = new Nested();
		n.list = new ArrayList<>();
		Map<String,Foo> m = new HashMap<>();
		m.put("x", new Foo("X"));
		n.list.add(m);
		n.map = new HashMap<>();
		n.map.put("l", Arrays.asList(new Foo("L1"), new Foo("L2")));

		JsonObject json = reg.serialize(n);
		assertEquals("X", json.getArray("list").getObject(0).getObject("x").getString("a"));
		assertEquals("L2", json.getObject("map").getArray("l").getObject(1).getString("a"));

		Nested n2 = reg.deserialize(Nested.class, json);
		assertEquals("X", n2.list.get(0).get("x").a);
		assertEquals("L1", n2.map.get("l").get(0).a);
		assertEquals("L2", n2.map.get("l").get(1).a);
	}

	public void testWildcards() throws Exception {
		SimpleRegistry reg = newRegistry();

		Wildcards w = new Wildcards();
		w.extendsList = Arrays.asList(new Foo("E"));
		w.anyList = Arrays.asList("s", 1);

		JsonObject json = reg.serialize(w);
		assertEquals("E", json.getArray("extendsList").getObject(0).getString("a"));
		assertEquals("s", json.getArray("anyList").getString(0));

		Wildcards w2 = reg.deserialize(Wildcards.class, json);
		assertEquals("E", w2.extendsList.get(0).a);
		assertEquals(2, w2.anyList.size());
		assertEquals("s", w2.anyList.get(0));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testRawTypes() throws Exception {
		SimpleRegistry reg = newRegistry();

		RawTypes r = new RawTypes();
		r.rawList = new ArrayList();
		r.rawList.add("s");
		r.rawList.add(JsonObject.of("k", 1));
		r.rawMap = new HashMap();
		r.rawMap.put("a", JsonArray.of(1, 2));

		JsonObject json = reg.serialize(r);
		assertEquals("s", json.getArray("rawList").getString(0));
		assertEquals(1, json.getArray("rawList").getObject(1).getInt("k"));
		assertEquals(2, json.getObject("rawMap").getArray("a").getInt(1));

		RawTypes r2 = reg.deserialize(RawTypes.class, json);
		assertEquals("s", r2.rawList.get(0));
		assertEquals(1, ((JsonObject)r2.rawList.get(1)).getInt("k"));
		assertEquals(2, ((JsonArray)r2.rawMap.get("a")).getInt(1));
	}

	public void testTypeVariableAsTypeArgument() throws Exception {
		SimpleRegistry reg = newRegistry();

		Main m = new Main();
		m.holder = new Holder<>();
		m.holder.items = Arrays.asList(new Foo("I1"), new Foo("I2"));
		m.holder.byName = new HashMap<>();
		m.holder.byName.put("n", new Foo("N"));

		JsonObject json = reg.serialize(m);
		assertEquals("I2", json.getObject("holder").getArray("items").getObject(1).getString("a"));
		assertEquals("N", json.getObject("holder").getObject("byName").getObject("n").getString("a"));

		Main m2 = reg.deserialize(Main.class, json);
		assertEquals("I1", m2.holder.items.get(0).a);
		assertEquals("I2", m2.holder.items.get(1).a);
		assertEquals("N", m2.holder.byName.get("n").a);
	}
}
