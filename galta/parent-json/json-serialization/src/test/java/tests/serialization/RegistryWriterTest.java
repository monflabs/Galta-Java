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

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

/**
 * serialize(Writer, obj, genericParams, compact) must honour genericParams (A6).
 */
public class RegistryWriterTest extends ProjectTestCase {

	public static class Foo {
		String a;
		public Foo() {
		}
		public Foo(String a) {
			this.a = a;
		}
	}

	public static class Container<T> {
		T t;
		List<T> ts;
		public Container() {
		}
	}

	public void testWriterHonoursGenericParams() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Foo.class)
				.add(Container.class)
				.build();

		Container<Foo> c = new Container<>();
		c.t = new Foo("x");
		c.ts = Arrays.asList(new Foo("y"), new Foo("z"));
		ClassAdapter[] params = new ClassAdapter[] { reg.findAdapter(Foo.class) };

		StringWriter w = new StringWriter();
		reg.serialize(w, c, params, true);

		JsonObject o = (JsonObject)JsonFactory.get().parse(w.toString());
		assertEquals("x", o.getObject("t").getString("a"));
		assertEquals("y", o.getArray("ts").getObject(0).getString("a"));
		assertEquals("z", o.getArray("ts").getObject(1).getString("a"));

		Container<Foo> c2 = reg.deserialize(Container.class, o, params);
		assertEquals("x", c2.t.a);
		assertEquals("z", c2.ts.get(1).a);
	}
}
