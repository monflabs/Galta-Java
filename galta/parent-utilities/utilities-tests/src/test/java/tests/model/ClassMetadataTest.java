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

	public static class Resolution {
		public String max(int a, int b) {
			return "int";
		}
		public String max(long a, long b) {
			return "long";
		}
		public String max(double a, double b) {
			return "double";
		}
		public String clamp(long v, int min, int max) {
			return "long,int,int";
		}
		public String clamp(double v, double min, double max) {
			return "double";
		}
		public String q(java.io.Serializable a, String b) {
			return "Serializable,String";
		}
		public String q(Comparable<?> a, Object b) {
			return "Comparable,Object";
		}
		public String cyc(List<?> a, java.util.Collection<?> b, java.util.RandomAccess c) {
			return "A";
		}
		public String cyc(java.util.Collection<?> a, java.util.RandomAccess b, List<?> c) {
			return "B";
		}
		public String cyc(java.util.RandomAccess a, List<?> b, java.util.Collection<?> c) {
			return "C";
		}
		public String atomic(java.util.concurrent.atomic.AtomicInteger a) {
			return "AtomicInteger";
		}
		public String atomic2(int a) {
			return "int";
		}
		public String atomic2(java.util.concurrent.atomic.AtomicLong a) {
			return "AtomicLong";
		}
		public String c1(char c) {
			return "char";
		}
		public String c1(Object o) {
			return "Object";
		}
		public String c2(char c) {
			return "char";
		}
		public String c2(CharSequence s) {
			return "CharSequence";
		}
		public String c3(char a, char b) {
			return "char,char";
		}
		public String c3(CharSequence a, CharSequence b) {
			return "CharSequence,CharSequence";
		}
		public String w1(int i) {
			return "int";
		}
		public String w1(Integer i) {
			return "Integer";
		}
		public String w2(Long i) {
			return "Long";
		}
		public String w2(long i) {
			return "long";
		}
	}
	public interface Api {
		String run(String s);
		String run(Object o);
	}
	// A non-public implementation with an extra public overload no public type declares
	static class HiddenApi implements Api {
		@Override
		public String run(String s) {
			return "String";
		}
		@Override
		public String run(Object o) {
			return "Object";
		}
		public String run(Integer i) {
			return "Integer";
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

	// Overload resolution cases that used to fail

	public static class Widening {
		public String g(int i) {
			return "int";
		}
		public String g(long l) {
			return "long";
		}
		public String d(int i) {
			return "int";
		}
		public String d(double d) {
			return "double";
		}
		public String h(char c) {
			return "char " + c;
		}
		public String s(String s) {
			return "String " + s;
		}
		public String join(String sep, String... parts) {
			return String.join(sep, parts);
		}
		public int total(int... values) {
			int t = 0;
			for (int v : values) {
				t += v;
			}
			return t;
		}
		public String fixed(Object o) {
			return "fixed";
		}
		public String fixed(Object... o) {
			return "varargs " + o.length;
		}
	}
	public static class Cmp implements Comparable<Cmp> {
		@Override
		public int compareTo(Cmp o) {
			return 0;
		}
	}

	public void testPrimitiveWidening() throws Exception {
		// g(int)/g(long) with a Short used to be an ambiguity: Java picks g(int)
		assertEquals("int", accessor().call(new Widening(), "g", new Object[] {(short)1}));
		assertEquals("int", accessor().call(new Widening(), "g", new Object[] {(byte)1}));
		assertEquals("long", accessor().call(new Widening(), "g", new Object[] {1L}));
		assertEquals("int", accessor().call(new Widening(), "g", new Object[] {1}));
		// A Long reaches double by widening, not int (as in Java)
		assertEquals("double", accessor().call(new Widening(), "d", new Object[] {3L}));
		assertEquals("double", accessor().call(new Widening(), "d", new Object[] {1.5f}));
		// Nothing widens: the wider parameter, which loses less, is chosen
		assertEquals("long", accessor().call(new Widening(), "g", new Object[] {2.0}));
	}

	public void testStringToChar() throws Exception {
		assertEquals("char a", accessor().call(new Widening(), "h", new Object[] {"a"}));
		// A longer String used to be accepted, and the reflective call failed with an
		// IllegalArgumentException
		ModelException e = assertThrows(ModelException.class, () -> accessor().call(new Widening(), "h", new Object[] {"abc"}));
		assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot find public method h("));
		// The shape cache must not reuse the one-character resolution
		assertEquals("char b", accessor().call(new Widening(), "h", new Object[] {"b"}));
		// And a Character is converted for a String parameter
		assertEquals("String c", accessor().call(new Widening(), "s", new Object[] {'c'}));
	}

	public void testBridgeMethodsIgnored() throws Exception {
		assertEquals(0, accessor().call(new Cmp(), "compareTo", new Object[] {new Cmp()}));
		// The bridge compareTo(Object) used to be selected, failing with a ClassCastException
		ModelException e = assertThrows(ModelException.class, () -> accessor().call(new Cmp(), "compareTo", new Object[] {"s"}));
		assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot find public method compareTo("));
	}

	public void testVarArgs() throws Exception {
		assertEquals("a-b-c", accessor().call(new Widening(), "join", new Object[] {"-", "a", "b", "c"}));
		assertEquals("", accessor().call(new Widening(), "join", new Object[] {"-"}));
		assertEquals("x", accessor().call(new Widening(), "join", new Object[] {"-", new String[] {"x"}}));
		assertEquals(6, accessor().call(new Widening(), "total", new Object[] {1, 2.9, 3L}));
		assertEquals(0, accessor().call(new Widening(), "total", new Object[0]));
		// Fixed arity is preferred, as in Java
		assertEquals("fixed", accessor().call(new Widening(), "fixed", new Object[] {"x"}));
		assertEquals("varargs 2", accessor().call(new Widening(), "fixed", new Object[] {"x", "y"}));
		// A null can't be an int
		assertThrows(ModelException.class, () -> accessor().call(new Widening(), "total", new Object[] {1, null}));
	}

	public void testMixedNumericArguments() throws Exception {
		// max(1, 2.5): int is more specific for the first argument, double for the second -
		// it used to be an ambiguity. The signature both arguments widen to wins
		assertEquals("double", accessor().call(new Resolution(), "max", new Object[] {1, 2.5}));
		assertEquals("double", accessor().call(new Resolution(), "max", new Object[] {2.5, 1}));
		assertEquals("long", accessor().call(new Resolution(), "max", new Object[] {1, 2L}));
		assertEquals("int", accessor().call(new Resolution(), "max", new Object[] {1, 2}));
		assertEquals("double", accessor().call(new Resolution(), "clamp", new Object[] {5.5, 1, 10}));
		assertEquals("long,int,int", accessor().call(new Resolution(), "clamp", new Object[] {5L, 1, 10}));
	}

	public void testIncomparableSignatures() throws Exception {
		// Serializable and Comparable are unrelated: neither signature is more specific, as in
		// Java. The String of the second position used to decide alone
		ModelException e = assertThrows(ModelException.class, () -> accessor().call(new Resolution(), "q", new Object[] {"x", "y"}));
		assertTrue(e.getMessage(), e.getMessage().startsWith("Ambiguity between q("));
		// Every candidate beaten by another one used to throw an IndexOutOfBoundsException
		e = assertThrows(ModelException.class, () -> accessor().call(new Resolution(), "cyc", new Object[] {new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>()}));
		assertTrue(e.getMessage(), e.getMessage().startsWith("Ambiguity between cyc("));
	}

	public void testNumberSubclassesAreNotConversionTargets() throws Exception {
		// An Integer used to be accepted for an AtomicInteger, and the invocation failed
		ModelException e = assertThrows(ModelException.class, () -> accessor().call(new Resolution(), "atomic", new Object[] {1}));
		assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot find public method atomic("));
		assertEquals("AtomicInteger", accessor().call(new Resolution(), "atomic", new Object[] {new java.util.concurrent.atomic.AtomicInteger()}));
		// And made int/AtomicLong an ambiguity for a Double
		assertEquals("int", accessor().call(new Resolution(), "atomic2", new Object[] {1.5}));
	}

	public void testStringPrefersStringParametersOverChar() throws Exception {
		// A one-character String goes to the parameter taking it as is, like a longer one
		assertEquals("Object", accessor().call(new Resolution(), "c1", new Object[] {"a"}));
		assertEquals("Object", accessor().call(new Resolution(), "c1", new Object[] {"ab"}));
		assertEquals("char", accessor().call(new Resolution(), "c1", new Object[] {'a'}));
		assertEquals("CharSequence", accessor().call(new Resolution(), "c2", new Object[] {"a"}));
		assertEquals("CharSequence,CharSequence", accessor().call(new Resolution(), "c3", new Object[] {"a", "b"}));
		// A char parameter alone still takes a one-character String
		assertEquals("char a", accessor().call(new Widening(), "h", new Object[] {"a"}));
	}

	public void testPrimitivePreferredOverWrapper() throws Exception {
		// f(int) and f(Integer) both match an Integer exactly: the reflection order decided
		assertEquals("int", accessor().call(new Resolution(), "w1", new Object[] {1}));
		assertEquals("long", accessor().call(new Resolution(), "w2", new Object[] {1L}));
		assertEquals("int", accessor().call(new Resolution(), "w1", new Object[] {1.5}));
		assertEquals("Integer", accessor().call(new Resolution(), "w1", new Object[] {null}));
	}

	public void testPublicOverloadsOfNonPublicClass() throws Exception {
		// The extra run(Integer) of the implementation is not part of its public API
		Api api = new HiddenApi();
		assertEquals("Object", accessor().call(api, "run", new Object[] {1}));
		assertEquals("String", accessor().call(api, "run", new Object[] {"s"}));
		assertEquals("String", accessor().call(api, "run", new Object[] {null}));
	}

	public void testMissingNamesNotCached() throws Exception {
		ClassMetadata cm = new ClassMetadata(null);
		ClassMetadata.ClassInfoCache ci = cm.getClassInfoCache(Sub.class);
		java.lang.reflect.Field f = ClassMetadata.ClassInfoCache.class.getDeclaredField("members");
		f.setAccessible(true);
		Map<?,?> members = (Map<?,?>) f.get(ci);
		for (int i = 0; i < 10000; i++) {
			assertNull(ci.getMethod("missing" + i));
		}
		// Every looked up name used to be cached, misses included
		assertTrue(String.valueOf(members.size()), members.size() < 10);
		assertNotNull(ci.getProperty("v"));
		assertNotNull(ci.getMethod("getV"));
		assertNull(ci.getField("v"));
	}

	private static boolean collected(java.lang.ref.WeakReference<?> ref) throws InterruptedException {
		for (int i = 0; i < 50 && ref.get() != null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		return ref.get() == null;
	}

	private static java.lang.ref.WeakReference<ClassMetadata> useMetadata() {
		ClassMetadata cm = new ClassMetadata(null);
		PojoAccessor a = new PojoAccessor(cm);
		assertEquals(3, a.call("abc", "length", new Object[0]));
		assertNotNull(cm.getClassInfoCache(int.class));
		assertNotNull(cm.getClassInfoCache(String[].class).getMethod("hashCode"));
		assertEquals("sub", a.getMember(new Sub(), "v"));
		return new java.lang.ref.WeakReference<>(cm);
	}

	public void testClassMetadataIsCollectable() throws Exception {
		// The ClassValue entries of never-unloaded classes (String...) referenced their
		// ClassMetadata, which then stayed reachable forever
		java.lang.ref.WeakReference<ClassMetadata> ref = useMetadata();
		assertTrue("The ClassMetadata was not garbage collected", collected(ref));
	}

	// The class, defined again by a throw-away class loader
	private static Class<?> defineInOwnLoader(Class<?> c) throws Exception {
		byte[] bytes;
		try (java.io.InputStream is = c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
			bytes = is.readAllBytes();
		}
		ClassLoader loader = new ClassLoader(ClassMetadataTest.class.getClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (name.equals(c.getName())) {
					synchronized (getClassLoadingLock(name)) {
						Class<?> l = findLoadedClass(name);
						return l != null ? l : defineClass(name, bytes, 0, bytes.length);
					}
				}
				return super.loadClass(name, resolve);
			}
		};
		return loader.loadClass(c.getName());
	}

	private static java.lang.ref.WeakReference<?>[] useOtherLoader() throws Exception {
		Class<?> other = defineInOwnLoader(sample.Class1.class);
		assertNotSame(sample.Class1.class, other);
		ClassMetadata cm = new ClassMetadata(null);
		assertSame(cm.getClassInfoCache(other), cm.getClassInfoCache(other));
		assertEquals(other, cm.getClassInfoCache(other).getNativeClass());
		Object o = other.getConstructor().newInstance();
		assertEquals("Class #1", new PojoAccessor(cm).call(o, "toString", new Object[0]));
		return new java.lang.ref.WeakReference<?>[] {new java.lang.ref.WeakReference<>(other), new java.lang.ref.WeakReference<>(cm)};
	}

	public void testClassOfOtherLoader() throws Exception {
		// The class, its loader and the metadata can all go away together
		java.lang.ref.WeakReference<?>[] refs = useOtherLoader();
		assertTrue(collected(refs[0]));
		assertTrue(collected(refs[1]));
	}
}
