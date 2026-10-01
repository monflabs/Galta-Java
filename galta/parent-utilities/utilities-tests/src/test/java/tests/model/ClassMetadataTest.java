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
package tests.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.monflabs.util.model.ClassMetadata;
import org.monflabs.util.model.ClassMetadata.MethodCache;
import org.monflabs.util.model.ModelAccessor;
import org.monflabs.util.model.ModelException;
import org.monflabs.util.model.PojoAccessor;

import tests.ProjectTestCase;

/**
 * ClassMetadata overload resolution and member access, through PojoAccessor.
 */
public class ClassMetadataTest extends ProjectTestCase {

	public static class Overloads {
		public String f(int i) {
			return "int";
		}
		public String f(String s) {
			return "String";
		}
		public String g(Object a, Number b) {
			return "Object,Number";
		}
		public String g(Number a, Object b) {
			return "Number,Object";
		}
		public String g(Number a, Number b) {
			return "Number,Number";
		}
		public String h(Object o) {
			return "Object";
		}
		public String h(CharSequence o) {
			return "CharSequence";
		}
		public String h(String o) {
			return "String";
		}
		public String amb(String s) {
			return "String";
		}
		public String amb(Integer i) {
			return "Integer";
		}
		public String prim(int i) {
			return "int "+i;
		}
		public int sum(int a, int b) {
			return a+b;
		}
	}

	public static class Base {
		public Object getV() {
			return "base";
		}
	}
	public static class Sub extends Base {
		private String v = "sub";
		// Covariant return type: the compiler also generates a bridge Object getV()
		@Override
		public String getV() {
			return v;
		}
		public void setV(String v) {
			this.v = v;
		}
	}

	// A non-public class with public members (like the JDK's List.of() implementations)
	static class Hidden implements Comparable<Hidden> {
		public int field = 7;
		public int size() {
			return 3;
		}
		public boolean isEmpty() {
			return false;
		}
		@Override
		public int compareTo(Hidden o) {
			return 0;
		}
		public static String stat() {
			return "static";
		}
	}

	private PojoAccessor accessor() {
		PojoAccessor a = new PojoAccessor();
		a.setUseExceptions(true);
		return a;
	}

	public void testNullNeverMatchesPrimitive() throws Exception {
		// f(int) used to be an exact match for a null argument (and was then picked, or not,
		// depending on the declaration order)
		assertEquals("String", accessor().call(new Overloads(), "f", new Object[] {null}));
		assertEquals("int", accessor().call(new Overloads(), "f", new Object[] {1}));
		ModelException e = assertThrows(ModelException.class, () -> accessor().call(new Overloads(), "prim", new Object[] {null}));
		assertTrue(e.getMessage(), e.getMessage().contains("prim("));
	}

	public void testNullPicksMostSpecific() throws Exception {
		// null was an exact match for the first overload found
		assertEquals("String", accessor().call(new Overloads(), "h", new Object[] {null}));
		assertEquals("CharSequence", accessor().call(new Overloads(), "h", new Object[] {new StringBuilder()}));
		assertEquals("Object", accessor().call(new Overloads(), "h", new Object[] {1}));
		assertEquals("String", accessor().call(new Overloads(), "h", new Object[] {"s"}));
	}

	public void testMostSpecificAfterAmbiguity() throws Exception {
		// (Object,Number) and (Number,Object) are ambiguous, but (Number,Number) is more specific
		// than both: it used to be ignored once the ambiguity was recorded
		assertEquals("Number,Number", accessor().call(new Overloads(), "g", new Object[] {1L, 1L}));
		assertEquals("Object,Number", accessor().call(new Overloads(), "g", new Object[] {"s", 1L}));
		assertEquals("Number,Object", accessor().call(new Overloads(), "g", new Object[] {1L, "s"}));
	}

	public void testRealAmbiguity() throws Exception {
		ModelException e = assertThrows(ModelException.class, () -> accessor().call(new Overloads(), "amb", new Object[] {null}));
		assertTrue(e.getMessage(), e.getMessage().contains("Ambiguity"));
	}

	public void testCallerArgumentsNotModified() throws Exception {
		Object[] args = {1.9, 2.2};
		assertEquals(3, accessor().call(new Overloads(), "sum", args));
		// The converted arguments used to be written back into the caller's array
		assertArrayEquals(new Object[] {1.9, 2.2}, args);
	}

	public void testErrorMessageNamesTheMethod() throws Exception {
		ModelException e = assertThrows(ModelException.class, () -> accessor().call(new Overloads(), "nope", new Object[] {1}));
		// It used to read "Overloads(java.lang.Integer)"
		assertTrue(e.getMessage(), e.getMessage().contains("nope(java.lang.Integer)"));
		assertTrue(e.getMessage(), e.getMessage().contains(Overloads.class.getName()));
	}

	public void testCovariantGetterProperty() throws Exception {
		Sub s = new Sub();
		PojoAccessor a = accessor();
		assertEquals("sub", a.getMember(s, "v"));
		// The bridge Object getV() used to be picked as the getter, so setV(String) was not
		// matched and the property was reported read-only
		assertTrue(a.putMember(s, "v", "changed"));
		assertEquals("changed", s.getV());
	}

	public void testJdkNonPublicImplementationClasses() throws Exception {
		// List.of() and Collections.unmodifiableList() return instances of non-public classes:
		// invoking their public methods reflectively used to fail with IllegalAccessException
		PojoAccessor a = accessor();
		assertEquals(3, a.call(List.of(1,2,3), "size", new Object[0]));
		assertEquals(2, a.call(List.of(1,2,3), "get", new Object[] {1}));
		assertEquals(1, a.call(Collections.unmodifiableList(List.of(1)), "size", new Object[0]));
		assertEquals(Boolean.TRUE, a.call(Map.of("k","v"), "containsKey", new Object[] {"k"}));
		assertEquals(Boolean.FALSE, a.getMember(List.of(1), "empty"));
	}

	public void testNonPublicClassMembers() throws Exception {
		PojoAccessor a = accessor();
		Hidden h = new Hidden();
		assertEquals(3, a.call(h, "size", new Object[0]));
		assertEquals(Boolean.FALSE, a.getMember(h, "empty"));
		assertEquals(7, a.getMember(h, "field"));
		assertTrue(a.putMember(h, "field", 8));
		assertEquals(8, h.field);
	}

	public void testStaticMethodOfNonPublicClass() throws Exception {
		// The last resort used to call getClass() on the null target
		ClassMetadata cm = new ClassMetadata(null);
		MethodCache mc = cm.getClassInfoCache(Hidden.class).getMethod("stat");
		assertEquals("static", mc.call((v,c) -> v, true, null, new Object[0]));
	}

	public void testAccessibleMethod() throws Exception {
		java.lang.reflect.Method size = List.of(1,2).getClass().getMethod("size");
		java.lang.reflect.Method m = ClassMetadata.getAccessibleMethod(size);
		assertTrue(java.lang.reflect.Modifier.isPublic(m.getDeclaringClass().getModifiers()));
		assertEquals(2, m.invoke(List.of(1,2)));
		java.lang.reflect.Method pub = String.class.getMethod("length");
		assertSame(pub, ClassMetadata.getAccessibleMethod(pub));
	}

	public void testClassInfoCacheIsPerClass() throws Exception {
		ClassMetadata cm = new ClassMetadata(null);
		assertSame(cm.getClassInfoCache(Sub.class), cm.getClassInfoCache(Sub.class));
		assertNotSame(cm.getClassInfoCache(Sub.class), cm.getClassInfoCache(Base.class));
		assertNotSame(cm.getClassInfoCache(Sub.class), new ClassMetadata(null).getClassInfoCache(Sub.class));
		assertEquals(Sub.class, cm.getClassInfoCache(Sub.class).getNativeClass());
	}

	public void testUseExceptionsOff() throws Exception {
		PojoAccessor a = new PojoAccessor();
		assertSame(ModelAccessor.UNHANDLED, a.call(new Overloads(), "nope", new Object[0]));
		assertSame(ModelAccessor.UNHANDLED, a.getMember(new Overloads(), "nope"));
		assertFalse(a.putMember(new Overloads(), "nope", 1));
		assertSame(ModelAccessor.UNHANDLED, a.getMember((Object)null, "x"));
	}

	public static class LoaderOverloads {
		public static String f(Object o) {
			return "object";
		}
		public static String f(String s) {
			return "string";
		}
	}

	public void testOverloadCacheDoesNotPinForeignLoaders() throws Exception {
		ClassMetadata cm = new ClassMetadata(null);
		MethodCache mc = cm.getClassInfoCache(LoaderOverloads.class).getMethod("f");
		assertEquals("string", mc.call((v,c) -> v, true, null, new Object[] {"x"}));
		java.lang.ref.WeakReference<ClassLoader> loader = callWithForeignArgument(mc);
		// Several shapes, so the foreign one is not only the "last call" either
		assertEquals("string", mc.call((v,c) -> v, true, null, new Object[] {"y"}));
		assertEquals("object", mc.call((v,c) -> v, true, null, new Object[] {1}));
		for (int i = 0; i < 100 && loader.get() != null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		// The resolution cache used to keep the argument class, and its loader, forever
		assertNull("the overload cache pinned a foreign class loader", loader.get());
		assertEquals("string", mc.call((v,c) -> v, true, null, new Object[] {"z"}));
	}

	private static java.lang.ref.WeakReference<ClassLoader> callWithForeignArgument(MethodCache mc) throws Exception {
		java.net.URL classes = sample.Class1.class.getProtectionDomain().getCodeSource().getLocation();
		try (java.net.URLClassLoader l = new java.net.URLClassLoader(new java.net.URL[] {classes}, ClassLoader.getPlatformClassLoader())) {
			Object foreign = l.loadClass("sample.Class1").getConstructor().newInstance();
			assertNotSame(sample.Class1.class, foreign.getClass());
			assertEquals("object", mc.call((v,c) -> v, true, null, new Object[] {foreign}));
			assertEquals("object", mc.call((v,c) -> v, true, null, new Object[] {foreign}));
			return new java.lang.ref.WeakReference<>(l);
		}
	}
}
