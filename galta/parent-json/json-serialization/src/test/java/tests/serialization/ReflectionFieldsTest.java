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

import java.io.Serializable;

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.SimpleRegistry;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;

import tests.ProjectTestCase;

/**
 * Reflection must ignore static, transient and synthetic fields (A1).
 */
public class ReflectionFieldsTest extends ProjectTestCase {

	public static class Outer {
		public class Inner {
			String value;
			public Inner() {
			}
		}
	}

	public static class Bean implements Serializable {
		private static final long serialVersionUID = 1L;
		public static String COUNTER = "static";

		String name;
		transient String secret;
		Outer.Inner inner;

		public Bean() {
		}
	}

	public void testStaticAndTransientFieldsAreSkipped() throws Exception {
		// A non-static inner class has no empty constructor: it needs a factory,
		// but reflection must not pull in its synthetic outer reference (this$0)
		Outer outer = new Outer();
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Bean.class)
				.add(SimpleClassAdapter.newBuilder(Outer.Inner.class)
						.reflection()
						.factory(() -> outer.new Inner())
						.build())
				.build();

		Bean b = new Bean();
		b.name = "n";
		b.secret = "s";
		b.inner = outer.new Inner();
		b.inner.value = "v";

		JsonObject json = reg.serialize(b);
		assertEquals("n", json.getString("name"));
		assertFalse(json.containsKey("serialVersionUID"));
		assertFalse(json.containsKey("COUNTER"));
		assertFalse(json.containsKey("secret"));
		assertFalse(json.getObject("inner").containsKey("this$0"));
		assertEquals("v", json.getObject("inner").getString("value"));

		Bean b2 = reg.deserialize(Bean.class, json);
		assertEquals("n", b2.name);
		assertNull(b2.secret);
		assertEquals("v", b2.inner.value);
	}
}
