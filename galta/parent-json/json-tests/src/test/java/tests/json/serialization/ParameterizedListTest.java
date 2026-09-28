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

import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

public class ParameterizedListTest extends ProjectTestCase {
	
	public static class A {
		String a;
		public A() {
		}
		public A(String a) {
			this.a = a;
		}
		@Override
		public int hashCode() {
			return a.hashCode();
		}
		@Override
		public boolean equals(Object o) {
			if(o instanceof A i) {
				return a.equals(i.a);
			}
			return false;
		}
	}
	public static class B {
		String b;
		public B() {
		}
		public B(String b) {
			this.b = b;
		}
		@Override
		public int hashCode() {
			return b.hashCode();
		}
		@Override
		public boolean equals(Object o) {
			if(o instanceof B i) {
				return b.equals(i.b);
			}
			return false;
		}
	}
	public static class MainList {
		List<A> aa;
		List<B> bb;
		
		public MainList() {
		}
		public MainList(boolean init) {
			if(init) {
				aa = new ArrayList<A>();
				aa.add(new A("AA"));
				aa.add(new A("AAA"));
				bb = new ArrayList<B>();
				bb.add(new B("B"));
				bb.add(new B("BB"));
			}
		}
	}
	
	public void testList() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(A.class)
				.add(B.class)
				.add(MainList.class)
				.build();
		
		MainList m = new MainList(true);
		checkObject(m);
		
		JsonObject json = reg.serialize(m);
		
		MainList m2 = reg.deserialize( MainList.class, json);
		checkObject(m2);
	}
	private void checkObject(MainList o) {
		assertEquals("AA",o.aa.get(0).a);
		assertEquals("AAA",o.aa.get(1).a);
		assertEquals("B",o.bb.get(0).b);
		assertEquals("BB",o.bb.get(1).b);
	}
}
