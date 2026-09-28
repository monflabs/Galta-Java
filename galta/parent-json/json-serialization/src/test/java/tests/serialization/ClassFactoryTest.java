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

import java.util.HashMap;
import java.util.Map;

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.SimpleRegistry;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;

import tests.ProjectTestCase;

/**
 * Class factory results are cached (self-referencing types) and arrays are still handled (A4).
 */
public class ClassFactoryTest extends ProjectTestCase {

	public static class Node {
		String name;
		Node next;
		public Node() {
		}
		public Node(String name, Node next) {
			this.name = name;
			this.next = next;
		}
	}

	public static class WithArray {
		String[] names;
		Node[] nodes;
		public WithArray() {
		}
	}

	public void testSelfReferencingTypeIsCached() throws Exception {
		Map<Class<?>,Integer> calls = new HashMap<>();
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.classFactory( (clazz) -> {
					calls.merge(clazz, 1, Integer::sum);
					return SimpleClassAdapter.newBuilder(clazz).reflection().build();
				})
				.build();

		Node n = new Node("a", new Node("b", null));

		JsonObject json = reg.serialize(n);
		assertEquals("a", json.getString("name"));
		assertEquals("b", json.getObject("next").getString("name"));

		Node n2 = reg.deserialize(Node.class, json);
		assertEquals("a", n2.name);
		assertEquals("b", n2.next.name);
		assertNull(n2.next.next);

		assertEquals(Integer.valueOf(1), calls.get(Node.class));
		ClassAdapter a1 = reg.findAdapter(Node.class);
		ClassAdapter a2 = reg.findAdapter(Node.class);
		assertSame(a1, a2);
		assertEquals(Integer.valueOf(1), calls.get(Node.class));
	}

	public void testArraysWithClassFactory() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.classFactory( (clazz) -> SimpleClassAdapter.newBuilder(clazz).reflection().build() )
				.build();

		WithArray w = new WithArray();
		w.names = new String[] {"x","y"};
		w.nodes = new Node[] { new Node("n", null) };

		JsonObject json = reg.serialize(w);
		assertEquals("y", json.getArray("names").getString(1));
		assertEquals("n", json.getArray("nodes").getObject(0).getString("name"));

		WithArray w2 = reg.deserialize(WithArray.class, json);
		assertEquals("y", w2.names[1]);
		assertEquals("n", w2.nodes[0].name);
	}
}
