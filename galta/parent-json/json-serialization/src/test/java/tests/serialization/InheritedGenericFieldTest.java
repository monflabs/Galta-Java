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

import java.util.Arrays;
import java.util.List;

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

/**
 * Generic fields inherited from a parameterized superclass resolve against the declaring class (A5).
 */
public class InheritedGenericFieldTest extends ProjectTestCase {

	public static class Foo {
		String a;
		public Foo() {
		}
		public Foo(String a) {
			this.a = a;
		}
	}

	public static class Base<T> {
		T t;
		List<T> ts;
		public Base() {
		}
	}
	public static class Sub extends Base<String> {
		int n;
		public Sub() {
		}
	}
	public static class Mid<U> extends Base<U> {
		public Mid() {
		}
	}
	public static class SubFoo extends Mid<Foo> {
		public SubFoo() {
		}
	}
	public static class Open<V> extends Base<V> {
		public Open() {
		}
	}
	public static class Main {
		Open<Foo> open;
		public Main() {
		}
	}

	private SimpleRegistry newRegistry() {
		return SimpleRegistry.newBuilder()
				.add(Foo.class)
				.add(Sub.class)
				.add(SubFoo.class)
				.add(Open.class)
				.add(Main.class)
				.build();
	}

	public void testSubclassBindsTypeVariable() throws Exception {
		SimpleRegistry reg = newRegistry();

		Sub s = new Sub();
		s.t = "hello";
		s.ts = Arrays.asList("x","y");
		s.n = 3;

		JsonObject json = reg.serialize(s);
		assertEquals("hello", json.getString("t"));
		assertEquals("y", json.getArray("ts").getString(1));
		assertEquals(3, json.getInt("n"));

		Sub s2 = reg.deserialize(Sub.class, json);
		assertEquals("hello", s2.t);
		assertEquals(Arrays.asList("x","y"), s2.ts);
		assertEquals(3, s2.n);
	}

	public void testIntermediateGenericClass() throws Exception {
		SimpleRegistry reg = newRegistry();

		SubFoo s = new SubFoo();
		s.t = new Foo("f");
		s.ts = Arrays.asList(new Foo("g"));

		JsonObject json = reg.serialize(s);
		assertEquals("f", json.getObject("t").getString("a"));
		assertEquals("g", json.getArray("ts").getObject(0).getString("a"));

		SubFoo s2 = reg.deserialize(SubFoo.class, json);
		assertEquals("f", s2.t.a);
		assertEquals("g", s2.ts.get(0).a);
	}

	public void testOpenSubclassResolvedFromOuterParams() throws Exception {
		SimpleRegistry reg = newRegistry();

		Main m = new Main();
		m.open = new Open<>();
		m.open.t = new Foo("o");
		m.open.ts = Arrays.asList(new Foo("p"));

		JsonObject json = reg.serialize(m);
		assertEquals("o", json.getObject("open").getObject("t").getString("a"));
		assertEquals("p", json.getObject("open").getArray("ts").getObject(0).getString("a"));

		Main m2 = reg.deserialize(Main.class, json);
		assertEquals("o", m2.open.t.a);
		assertEquals("p", m2.open.ts.get(0).a);
	}
}
