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

import static org.junit.Assert.assertThrows;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.SimpleRegistry;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;

import tests.ProjectTestCase;

public class JsonTypesTest extends ProjectTestCase {
	
	public static class A {
		JsonObject a;
		public A() {
		}
		public A(JsonObject a) {
			this.a = a;
		}
	}
	public static class B {
		JsonArray b;
		public B() {
		}
		public B(JsonArray b) {
			this.b = b;
		}
	}
	public static class C {
		Object a;
		Object b;
		public C() {
		}
		public C(JsonObject a, JsonArray b) {
			this.a = a;
			this.b = b;
		}
	}
	public static class Main {
		A aa;
		B bb;
		C cc;
		
		public Main() {
		}
		public Main(boolean init) {
			if(init) {
				aa = new A();
				JsonObject o = JsonObject.create();
				o.put("AA",JsonObject.of("a1",1,"a2",2));
				o.put("AAA",JsonObject.of("a3",3));
				aa.a = o;
				
				bb = new B();
				JsonArray a = JsonArray.create();
				a.add("B");
				a.add("BB");
				bb.b = a;
				
				cc = new C();
				cc.a = o;
				cc.b = a;
			}
		}
	}
	
	public void testObjects() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(A.class)
				.add(B.class)
				.add(C.class)
				.add(Main.class)
				.build();
		
		Main m = new Main(true);
		checkObject(m);
		
		JsonObject json = reg.serialize(m);
		//Console.log("{0}",json.toString());
		
		Main m2 = reg.deserialize( Main.class, json);
		checkObject(m2);
	}
	
	public void testAutoObjects() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Main.class)
				.classFactory( (clazz) -> SimpleClassAdapter.newBuilder(clazz).reflection().build() )
				.build();
		
		Main m = new Main(true);
		checkObject(m);
		
		JsonObject json = reg.serialize(m);
		//Console.log("{0}",json.toString());
		
		Main m2 = reg.deserialize( Main.class, json);
		checkObject(m2);
	}
	
	public void testAutoNotSetObjects() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Main.class)
				.build();
		
		Main m = new Main(true);
		checkObject(m);
		
		assertThrows( JsonException.class, () -> reg.serialize(m) );
	}

	private void checkObject(Main o) {
		assertEquals(1,o.aa.a.getObject("AA").getInt("a1"));
		assertEquals(2,o.aa.a.getObject("AA").getInt("a2"));
		assertEquals(3,o.aa.a.getObject("AAA").getInt("a3"));
		assertEquals("B",o.bb.b.getString(0));
		assertEquals("BB",o.bb.b.getString(1));

		assertEquals(1,((JsonObject)o.cc.a).getObject("AA").getInt("a1"));
		assertEquals(2,((JsonObject)o.cc.a).getObject("AA").getInt("a2"));
		assertEquals(3,((JsonObject)o.cc.a).getObject("AAA").getInt("a3"));
		assertEquals("B",((JsonArray)o.cc.b).getString(0));
		assertEquals("BB",((JsonArray)o.cc.b).getString(1));

	}
}
