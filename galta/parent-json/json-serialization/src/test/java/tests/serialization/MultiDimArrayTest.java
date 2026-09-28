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

import static org.junit.Assert.assertArrayEquals;

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

/**
 * Object arrays must keep their real component type, so String[][] is not built as Object[][] (A3).
 */
public class MultiDimArrayTest extends ProjectTestCase {

	public static class Foo {
		String a;
		public Foo() {
		}
		public Foo(String a) {
			this.a = a;
		}
	}

	public static class Arrays2D {
		String[][] strings;
		Foo[][] foos;
		public Arrays2D() {
		}
	}

	public void testStringMatrix() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Foo.class)
				.add(Arrays2D.class)
				.build();

		Arrays2D a = new Arrays2D();
		a.strings = new String[][] { {"a","b"}, {"c"} };
		a.foos = new Foo[][] { {new Foo("f")} };

		JsonObject json = reg.serialize(a);
		assertEquals("c", json.getArray("strings").getArray(1).getString(0));
		assertEquals("f", json.getArray("foos").getArray(0).getObject(0).getString("a"));

		Arrays2D a2 = reg.deserialize(Arrays2D.class, json);
		assertArrayEquals(new String[] {"a","b"}, a2.strings[0]);
		assertArrayEquals(new String[] {"c"}, a2.strings[1]);
		assertEquals("f", a2.foos[0][0].a);
	}

	public void testArrayAdapterReportsRealClass() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder().build();
		ClassAdapter a = reg.findAdapter(String[][].class);
		assertEquals(String[][].class, a.getAdaptedClazz());
		Object v = reg.deserialize(String[][].class, org.monflabs.json.JsonArray.of(org.monflabs.json.JsonArray.of("x")));
		assertEquals(String[][].class, v.getClass());
		assertEquals("x", ((String[][])v)[0][0]);
	}
}
