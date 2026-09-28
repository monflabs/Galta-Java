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

import java.util.HashSet;
import java.util.Set;

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

public class ParameterizedSetTest extends ProjectTestCase {
	
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

	public static class MainSet {
		Set<A> aa;
		Set<B> bb;
		
		public MainSet() {
		}
		public MainSet(boolean init) {
			if(init) {
				aa = new HashSet<A>();
				aa.add(new A("AA"));
				aa.add(new A("AAA"));
				bb = new HashSet<B>();
				bb.add(new B("B"));
				bb.add(new B("BB"));
			}
		}
	}
	
	public void testSet() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(A.class)
				.add(B.class)
				.add(MainSet.class)
				.build();
		
		MainSet m = new MainSet(true);
		checkObject(m);
		
		JsonObject json = reg.serialize(m);
		
		MainSet m2 = reg.deserialize( MainSet.class, json);
		checkObject(m2);
	}
	private void checkObject(MainSet o) {
		assertEquals(true,o.aa.contains(new A("AA")));
		assertEquals(true,o.aa.contains(new A("AAA")));
		assertEquals(true,o.bb.contains(new B("B")));
		assertEquals(true,o.bb.contains(new B("BB")));
	}
}
