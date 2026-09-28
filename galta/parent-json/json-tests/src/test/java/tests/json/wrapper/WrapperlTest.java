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
package tests.json.wrapper;

import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonObject;
import org.monflabs.json.wrappers.JsonObjectWrapperImpl;
import org.monflabs.json.wrappers.WrappedList;
import org.monflabs.json.wrappers.WrappedMap;

import tests.ProjectTestCase;

public class WrapperlTest extends ProjectTestCase {
	
	public static String JSON =
"""
{
  str: "a1",
  b: {
	str: "b0"
  },
  mb: {
    b1: {
	  str: "mb1"
    },
    b2: {
	  str: "mb2"
    }
  },
  lb: [
    {
      str: "lb1"
    },
    {
      str: "lb2"
    }
  ]
}
""";
	public static class A extends JsonObjectWrapperImpl {
		public static A of(JsonObject o) {
			return o!=null ? new A(o) : null;
		}
		public A(JsonObject o) {
			super(o);
		}
		public String p_str() {
			return wrapped().getString("str");
		}
		public B p_b() {
			return B.of(wrapped().getObject("b"));
		}
		public Map<String,B> p_mb() {
			return new WrappedMap<B>(wrapped().getObject("mb"), B::of);
		}
		public List<B> p_lb() {
			return new WrappedList<B>(wrapped().getArray("lb"), B::of);
		}
	}
	public static class B extends JsonObjectWrapperImpl {
		public static B of(Object o) {
			return o!=null ? new B((JsonObject)o) : null;
		}
		private B(JsonObject o) {
			super(o);
		}
		public String p_str() {
			return wrapped().getString("str");
		}
	}
	
	
	public void testWrappers() throws Exception {
		JsonObject o = JsonObject.parse(JSON);
		
		A a = new A(o);
		assertEquals(a.p_str(), "a1");
		
		B b = a.p_b();
		assertEquals(b.p_str(), "b0");
		
		{
			List<B> lb = a.p_lb();
			assertEquals( 2, lb.size() ); 
			assertEquals( "lb1", lb.get(0).p_str() ); 
			assertEquals( "lb2", lb.get(1).p_str() );
			
			lb.add(B.of(JsonObject.of("str","lb3")));
			assertEquals( 3, lb.size() ); 
			assertEquals( "lb1", lb.get(0).p_str() ); 
			assertEquals( "lb2", lb.get(1).p_str() );
			assertEquals( "lb3", lb.get(2).p_str() );
			
			List<B> lb2 = a.p_lb();
			assertEquals( 3, lb2.size() ); 
			assertEquals( "lb1", lb2.get(0).p_str() ); 
			assertEquals( "lb2", lb2.get(1).p_str() );
			assertEquals( "lb3", lb2.get(2).p_str() );
		}

		{
			Map<String,B> mb = a.p_mb();
			assertEquals( 2, mb.size() ); 
			assertEquals( "mb1", mb.get("b1").p_str() ); 
			assertEquals( "mb2", mb.get("b2").p_str() );
			
			mb.put("b3", B.of(JsonObject.of("str","mb3")));
			assertEquals( 3, mb.size() ); 
			assertEquals( "mb1", mb.get("b1").p_str() ); 
			assertEquals( "mb2", mb.get("b2").p_str() );
			assertEquals( "mb3", mb.get("b3").p_str() );

			Map<String,B> mb2 = a.p_mb();
			assertEquals( 3, mb2.size() ); 
			assertEquals( "mb1", mb2.get("b1").p_str() ); 
			assertEquals( "mb2", mb2.get("b2").p_str() );
			assertEquals( "mb3", mb2.get("b3").p_str() );
		}
	}
	
	public void testWrapperContracts() throws Exception {
		JsonObject o = JsonObject.parse(JSON);
		A a = new A(o);
		// Two wrappers of the same value are equal
		assertEquals(a.p_b(), a.p_b());
		assertEquals(a.p_b().hashCode(), a.p_b().hashCode());
		assertFalse(a.p_b().equals(B.of(JsonObject.of("str","other"))));
		assertFalse(a.equals(B.of(o)));   // not the same kind of wrapper
		
		// Map contract
		Map<String,B> mb = a.p_mb();
		Map.Entry<String,B> e = mb.entrySet().iterator().next();
		assertTrue(mb.entrySet().contains(e));
		assertTrue(mb.entrySet().contains(new java.util.AbstractMap.SimpleEntry<>("b1", B.of(o.getObject("mb").getObject("b1")))));
		assertFalse(mb.entrySet().contains(new java.util.AbstractMap.SimpleEntry<>("b1", B.of(JsonObject.of("str","x")))));
		assertFalse(mb.entrySet().contains("b1"));
		assertEquals(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()), e);
		assertEquals(e, new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
		Map<String,B> copy = new java.util.HashMap<>(mb);
		assertEquals(copy, mb);
		assertEquals(mb, copy);
		assertEquals(copy.hashCode(), mb.hashCode());
		assertEquals(a.p_mb(), mb);
		
		// A null value is not wrapped, in get() as in the entries
		o.getObject("mb").putNull("n");
		Map<String,B> withNull = a.p_mb();
		assertNull(withNull.get("n"));
		for(Map.Entry<String,B> en: withNull.entrySet()) {
			if(en.getKey().equals("n")) {
				assertNull(en.getValue());
			}
		}
		
		// List contract
		List<B> lb = a.p_lb();
		List<B> lcopy = new java.util.ArrayList<>(lb);
		assertEquals(lcopy, lb);
		assertEquals(lb, lcopy);
		assertEquals(lcopy.hashCode(), lb.hashCode());
		assertEquals(a.p_lb(), lb);
		assertTrue(lb.contains(B.of(o.getArray("lb").getObject(0))));
		assertEquals(1, lb.indexOf(B.of(o.getArray("lb").getObject(1))));
	}
}
