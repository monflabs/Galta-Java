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
package tests.json.serialization;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.java.JavaJsonFactoryChecked;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

public class ParameterizedTypesTest extends ProjectTestCase {
	
	public static class A {
		String a = "AA";
	}
	public static class B {
		String b = "BB";
	}
	public static class Container<T> {
		T t;
		Container() {
		}
		Container(T t) {
			this.t = t;
		}
	}
	public static class Main {
		Container<A> aa = new Container<A>(new A());
		Container<B> bb = new Container<B>(new B());
	}
	
	public void testCustomReflection() throws Exception {
		// The factory is global: restore it, the next tests expect the suite's one
		JsonFactory saved = JsonFactory.get();
		JsonFactory.set(JavaJsonFactoryChecked.instance);
		try {
			customReflection();
		} finally {
			JsonFactory.set(saved);
		}
	}
	
	private void customReflection() throws Exception {
		JsonObject.create();
		
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(A.class)
				.add(B.class)
				.add(Container.class)
				.add(Main.class)
				.build();
		
		Main m = new Main();
		checkObject(m);
		
		JsonObject json = reg.serialize(m);
		//Console.log(json.toString());
		
		Main m2 = reg.deserialize( Main.class, json);
		checkObject(m2);
	}

	private void checkObject(Main o) {
		assertEquals(true,o.aa.t.a.equals("AA"));
		assertEquals(true,o.bb.t.b.equals("BB"));
	}
}
