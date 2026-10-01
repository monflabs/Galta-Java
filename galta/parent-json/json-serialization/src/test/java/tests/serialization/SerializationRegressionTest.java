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

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.SimpleRegistry;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;

import tests.ProjectTestCase;

/**
 * Regression tests: collections without generic parameters, map keys, key order,
 * lossy numeric conversions, field order, cycles, enums, registry binding.
 */
public class SerializationRegressionTest extends ProjectTestCase {

	public enum Color { RED, GREEN, BLUE }
	public enum Op {
		PLUS { @Override int apply(int a, int b) { return a+b; } },
		MINUS { @Override int apply(int a, int b) { return a-b; } };
		abstract int apply(int a, int b);
	}
	
	public static class Keys {
		Map<Integer,String> byInt;
		Map<Long,String> byLong;
		Map<Color,Integer> byColor;
		Map<Boolean,String> byBool;
		Map<Double,String> byDouble;
		Map<BigInteger,String> byBig;
		TreeMap<String,Integer> sorted;
		public Keys() {
		}
	}
	
	public static class Numbers {
		int i;
		long l;
		short s;
		byte b;
		float f;
		double d;
		boolean z;
		Integer boxed;
		Long boxedLong;
		BigInteger bi;
		BigDecimal bd;
		int[] ints;
		byte[] bytes;
		short[] shorts;
		long[] longs;
		float[] floats;
		double[] doubles;
		boolean[] bools;
		Object[] objects;
		public Numbers() {
		}
	}
	
	public static class Parent {
		String p2;
		String p1;
		public Parent() {
		}
	}
	public static class Child extends Parent {
		String z;
		String a;
		String m;
		public Child() {
		}
	}
	
	public static class Node {
		String name;
		List<Node> children = new ArrayList<>();
		Node next;
		public Node() {
		}
	}
	
	public static class Holder {
		Color color;
		Op op;
		Set<String> tags;
		LinkedList<Integer> linked;
		Map<String,List<Color>> nested;
		public Holder() {
		}
	}
	
	private static SimpleRegistry registry(Class<?>... classes) {
		SimpleRegistry.Builder b = SimpleRegistry.newBuilder();
		for(Class<?> c: classes) {
			b.add(c);
		}
		return b.build();
	}

	// ------------------------------------------------------------------
	// Collections without generic parameters
	// ------------------------------------------------------------------
	
	public void testTopLevelCollectionsWithoutParams() throws Exception {
		SimpleRegistry reg = registry();
		JsonArray arr = JsonArray.of("a", 1, true);
		// Used to NPE on the missing generic parameters
		List<?> l = reg.deserialize(List.class, arr);
		assertEquals(List.of("a", 1, true), l);
		Set<?> s = reg.deserialize(Set.class, JsonArray.of("b", "a", "b"));
		assertEquals(List.of("b", "a"), new ArrayList<>(s));
		Map<?,?> m = reg.deserialize(Map.class, JsonObject.of("z", 1, "a", 2));
		assertEquals(List.of("z", "a"), new ArrayList<>(m.keySet()));
		
		// Top-level serialization finds the interface adapters from the runtime class
		assertEquals(JsonArray.of("a", "b"), reg.serialize(new ArrayList<>(List.of("a", "b"))));
		assertEquals(JsonArray.of("a"), reg.serialize(List.of("a")));
		assertEquals(JsonArray.of("x"), reg.serialize(new LinkedHashSet<>(List.of("x"))));
		Map<String,Object> hm = new LinkedHashMap<>();
		hm.put("k", 1);
		assertEquals(JsonObject.of("k", 1), reg.serialize(hm));
		assertEquals(JsonArray.of("a"), reg.findAdapter(List.class).serialize(List.of("a"), null));
		
		// JSON containers stay JSON containers, deep copied
		JsonObject o = JsonObject.of("a", 1);
		assertEquals(o, reg.serialize(o));
		assertNotSame(o, reg.serialize(o));
		assertEquals(arr, reg.serialize(arr));
		assertNotSame(arr, reg.serialize(arr));
	}
	
	public void testConcreteCollectionFields() throws Exception {
		SimpleRegistry reg = registry(Holder.class);
		Holder h = new Holder();
		h.tags = new LinkedHashSet<>(List.of("c", "a", "b"));
		h.linked = new LinkedList<>(List.of(3, 1));
		h.nested = new LinkedHashMap<>();
		h.nested.put("z", List.of(Color.RED, Color.BLUE));
		h.nested.put("a", List.of());
		JsonObject json = reg.serialize(h);
		assertEquals(JsonArray.of("c", "a", "b"), json.get("tags"));
		assertEquals(JsonArray.of(3, 1), json.get("linked"));
		assertEquals(JsonArray.of("RED", "BLUE"), json.getObject("nested").get("z"));
		
		Holder back = reg.deserialize(Holder.class, json);
		assertEquals(List.of("c", "a", "b"), new ArrayList<>(back.tags));   // order kept
		assertEquals(LinkedList.class, back.linked.getClass());
		assertEquals(List.of(3, 1), back.linked);
		assertEquals(List.of("z", "a"), new ArrayList<>(back.nested.keySet()));
		assertEquals(List.of(Color.RED, Color.BLUE), back.nested.get("z"));
	}
	
	// ------------------------------------------------------------------
	// Map keys
	// ------------------------------------------------------------------
	
	public void testNonStringMapKeys() throws Exception {
		SimpleRegistry reg = registry(Keys.class);
		Keys k = new Keys();
		k.byInt = new LinkedHashMap<>(Map.of(1, "one"));
		k.byLong = new LinkedHashMap<>(Map.of(5000000000L, "big"));
		k.byColor = new LinkedHashMap<>();
		k.byColor.put(Color.GREEN, 2);
		k.byColor.put(Color.RED, 1);
		k.byBool = new LinkedHashMap<>(Map.of(true, "yes"));
		k.byDouble = new LinkedHashMap<>(Map.of(1.5, "x"));
		k.byBig = new LinkedHashMap<>(Map.of(new BigInteger("123456789012345678901234567890"), "b"));
		k.sorted = new TreeMap<>(Map.of("b", 2, "a", 1));
		
		JsonObject json = reg.serialize(k);
		assertEquals(JsonObject.of("1", "one"), json.get("byInt"));
		assertEquals(JsonObject.of("5000000000", "big"), json.get("byLong"));
		assertEquals(List.of("GREEN", "RED"), new ArrayList<>(json.getObject("byColor").keySet()));
		assertEquals(JsonObject.of("true", "yes"), json.get("byBool"));
		
		Keys back = reg.deserialize(Keys.class, json);
		assertEquals("one", back.byInt.get(1));
		assertEquals("big", back.byLong.get(5000000000L));
		assertEquals(Integer.valueOf(2), back.byColor.get(Color.GREEN));
		assertEquals(List.of(Color.GREEN, Color.RED), new ArrayList<>(back.byColor.keySet()));
		assertEquals("yes", back.byBool.get(true));
		assertEquals("x", back.byDouble.get(1.5));
		assertEquals("b", back.byBig.get(new BigInteger("123456789012345678901234567890")));
		assertEquals(TreeMap.class, back.sorted.getClass());
		assertEquals(Integer.valueOf(1), back.sorted.get("a"));
		
		// Invalid keys are reported
		assertThrows(JsonException.class, () -> reg.deserialize(Keys.class, JsonObject.of("byInt", JsonObject.of("x", "v"))));
		assertThrows(JsonException.class, () -> reg.deserialize(Keys.class, JsonObject.of("byColor", JsonObject.of("PINK", 1))));
		assertThrows(JsonException.class, () -> reg.deserialize(Keys.class, JsonObject.of("byBool", JsonObject.of("maybe", "v"))));
	}
	
	// ------------------------------------------------------------------
	// Numbers
	// ------------------------------------------------------------------
	
	public void testNumericRoundTrip() throws Exception {
		SimpleRegistry reg = registry(Numbers.class);
		Numbers n = new Numbers();
		n.i = Integer.MIN_VALUE; n.l = Long.MAX_VALUE; n.s = Short.MIN_VALUE; n.b = Byte.MAX_VALUE;
		n.f = 1.25f; n.d = 0.1; n.z = true; n.boxed = 7; n.boxedLong = -1L;
		n.bi = new BigInteger("123456789012345678901234567890"); n.bd = new BigDecimal("0.10");
		n.ints = new int[] {1, 2}; n.bytes = new byte[] {-1}; n.shorts = new short[] {3};
		n.longs = new long[] {Long.MIN_VALUE}; n.floats = new float[] {0.5f}; n.doubles = new double[] {2.5};
		n.bools = new boolean[] {true, false}; n.objects = new Object[] {"a", 1};
		JsonObject json = reg.serialize(n);
		Numbers b = reg.deserialize(Numbers.class, JsonObject.parse(json.stringify(false)));
		assertEquals(n.i, b.i); assertEquals(n.l, b.l); assertEquals(n.s, b.s); assertEquals(n.b, b.b);
		assertEquals(n.f, b.f); assertEquals(n.d, b.d); assertEquals(n.z, b.z);
		assertEquals(n.boxed, b.boxed); assertEquals(n.boxedLong, b.boxedLong);
		assertEquals(n.bi, b.bi); assertEquals(0, n.bd.compareTo(b.bd));
		assertEquals(2, b.ints[1]); assertEquals(-1, b.bytes[0]); assertEquals(3, b.shorts[0]);
		assertEquals(Long.MIN_VALUE, b.longs[0]); assertEquals(0.5f, b.floats[0]); assertEquals(2.5, b.doubles[0]);
		assertTrue(b.bools[0]); assertFalse(b.bools[1]);
		assertEquals("a", b.objects[0]);
		
		// Whole doubles convert exactly
		Numbers w = reg.deserialize(Numbers.class, JsonObject.of("i", 2.0, "l", 3.0, "boxed", 4.0, "bi", 5.0));
		assertEquals(2, w.i);
		assertEquals(3L, w.l);
		assertEquals(Integer.valueOf(4), w.boxed);
		assertEquals(BigInteger.valueOf(5), w.bi);
	}
	
	public void testLossyNumericConversionsFail() throws Exception {
		SimpleRegistry reg = registry(Numbers.class);
		Object[][] bad = {
				{"i", 1.5}, {"i", 3.9e10}, {"i", 5000000000L}, {"l", 1e19}, {"l", 1.5},
				{"s", 40000}, {"b", 300}, {"b", 1.5}, {"f", 1e40}, {"boxed", 1.5},
				{"boxedLong", 2.5}, {"bi", 1.5}, {"i", "12"}, {"bd", Double.NaN},
				{"ints", JsonArray.of(1, 1.5)}, {"bytes", JsonArray.of(128)}, {"shorts", JsonArray.of(1e6)},
				{"longs", JsonArray.of(0.5)}, {"floats", JsonArray.of(1e300)},
		};
		for(Object[] kv: bad) {
			try {
				reg.deserialize(Numbers.class, JsonObject.of((String)kv[0], kv[1]));
				fail(kv[0]+"="+kv[1]+" should not be converted");
			} catch(JsonException ex) {
				// expected
			}
		}
		// Integer class adapter at the top level too
		assertThrows(JsonException.class, () -> reg.deserialize(Integer.class, 1.5));
		assertEquals(Integer.valueOf(3), reg.deserialize(Integer.class, 3.0));
	}
	
	// ------------------------------------------------------------------
	// Field order
	// ------------------------------------------------------------------
	
	public void testFieldsInDeclarationOrder() throws Exception {
		SimpleRegistry reg = registry(Child.class);
		Child c = new Child();
		c.p1 = "1"; c.p2 = "2"; c.z = "z"; c.a = "a"; c.m = "m";
		JsonObject json = reg.serialize(c);
		assertEquals(List.of("p2", "p1", "z", "a", "m"), new ArrayList<>(json.keySet()));
		assertEquals("{\"p2\":\"2\",\"p1\":\"1\",\"z\":\"z\",\"a\":\"a\",\"m\":\"m\"}", json.stringify(true));
	}
	
	// ------------------------------------------------------------------
	// Cycles
	// ------------------------------------------------------------------
	
	public void testCyclesAreReported() throws Exception {
		SimpleRegistry reg = registry(Node.class);
		
		Node self = new Node();
		self.next = self;
		assertThrows(JsonException.class, () -> reg.serialize(self));
		
		// Through a collection
		Node a = new Node();
		Node b = new Node();
		a.children.add(b);
		b.children.add(a);
		JsonException ex = assertThrows(JsonException.class, () -> reg.serialize(a));
		assertTrue(ex.getMessage(), ex.getMessage().contains("cyclic"));
		
		// Through an array
		Node c = new Node();
		Node[] arr = new Node[] {c};
		c.children.add(new Node());
		c.children.get(0).next = c;
		assertThrows(JsonException.class, () -> reg.serialize(arr));
		
		// Shared, but not cyclic: fine, and the guard was reset after the failures
		Node shared = new Node();
		shared.name = "s";
		Node root = new Node();
		root.name = "r";
		root.next = shared;
		root.children.add(shared);
		root.children.add(shared);
		JsonObject json = reg.serialize(root);
		assertEquals("s", json.getObject("next").getString("name"));
		assertEquals(2, json.getArray("children").size());
	}
	
	// ------------------------------------------------------------------
	// Enums
	// ------------------------------------------------------------------
	
	public void testEnums() throws Exception {
		SimpleRegistry reg = registry(Holder.class);
		Holder h = new Holder();
		h.color = Color.BLUE;
		h.op = Op.MINUS;                              // a constant with a body
		JsonObject json = reg.serialize(h);
		assertEquals("BLUE", json.get("color"));
		assertEquals("MINUS", json.get("op"));
		Holder back = reg.deserialize(Holder.class, json);
		assertEquals(Color.BLUE, back.color);
		assertSame(Op.MINUS, back.op);
		assertEquals("PLUS", reg.serialize(Op.PLUS));
		assertEquals(Color.RED, reg.deserialize(Color.class, "RED"));
		assertThrows(JsonException.class, () -> reg.deserialize(Color.class, "PINK"));
		assertThrows(JsonException.class, () -> reg.deserialize(Color.class, 1));
	}
	
	// ------------------------------------------------------------------
	// Registry
	// ------------------------------------------------------------------
	
	static class CountingAdapter extends BaseClassAdapter {
		int inits;
		CountingAdapter() {
			super(Color.class);
		}
		@Override
		public void init(JsonRegistry registry) {
			inits++;
		}
		@Override
		public Object serialize(Object value, ClassAdapter[] genericParams) {
			return "c:"+value;
		}
		@Override
		public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
			return Color.valueOf(((String)jsonValue).substring(2));
		}
	}
	
	public void testAdapterInitializedOncePerRegistry() throws Exception {
		CountingAdapter c = new CountingAdapter();
		SimpleRegistry reg = SimpleRegistry.newBuilder().add(c).build();
		// Registered adapters win over the enum default
		assertEquals("c:RED", reg.serialize(Color.RED));
		reg.findAdapter(Color.class);
		reg.findAdapter(Color.class);
		assertEquals(1, c.inits);
	}
	
	public void testSharedClassAdapterRejectedBySecondRegistry() throws Exception {
		SimpleClassAdapter<Parent> ad = SimpleClassAdapter.newBuilder(Parent.class).reflection().build();
		SimpleRegistry r1 = SimpleRegistry.newBuilder().add(ad).build();
		SimpleRegistry r2 = SimpleRegistry.newBuilder().add(ad).build();
		Parent p = new Parent();
		p.p1 = "x";
		assertEquals("x", ((JsonObject)r1.serialize(p)).get("p1"));
		// Used to be silently bound to the first registry
		assertThrows(JsonException.class, () -> r2.serialize(p));
		// Separate adapters work in both
		SimpleRegistry r3 = registry(Parent.class);
		SimpleRegistry r4 = registry(Parent.class);
		assertEquals("x", ((JsonObject)r3.serialize(p)).get("p1"));
		assertEquals("x", ((JsonObject)r4.serialize(p)).get("p1"));
	}
	
	public void testRegistriesAreIndependent() throws Exception {
		// Same class, different adapters for a field type in two registries
		SimpleRegistry custom = SimpleRegistry.newBuilder()
				.add(new CountingAdapter())
				.add(Holder.class)
				.build();
		SimpleRegistry plain = registry(Holder.class);
		Holder h = new Holder();
		h.color = Color.GREEN;
		assertEquals("c:GREEN", ((JsonObject)custom.serialize(h)).get("color"));
		assertEquals("GREEN", ((JsonObject)plain.serialize(h)).get("color"));
	}
	
	public void testDefaultRegistryCanBeCleared() throws Exception {
		SimpleRegistry.clearDefault();
		try {
			SimpleRegistry r = SimpleRegistry.newBuilder().defaultRegistry(true).build();
			assertSame(r, SimpleRegistry.get());
			assertThrows(IllegalStateException.class, () -> SimpleRegistry.newBuilder().defaultRegistry(true).build());
			SimpleRegistry.clearDefault();
			assertNull(SimpleRegistry.get());
			SimpleRegistry r2 = SimpleRegistry.newBuilder().defaultRegistry(true).build();
			assertSame(r2, SimpleRegistry.get());
		} finally {
			SimpleRegistry.clearDefault();
		}
	}
	
	// ------------------------------------------------------------------
	// Lambda fields and factories
	// ------------------------------------------------------------------
	
	public static class Point {
		private final int x;
		private final int y;
		Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
	public static class MutablePoint {
		int x;
		int y;
	}
	
	public void testLambdaFieldsAndFactory() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(MutablePoint.class, b -> b
						.factory(MutablePoint::new)
						.add("y", (MutablePoint p) -> p.y, (p, v) -> p.y = ((Number)v).intValue())
						.add("x", (MutablePoint p) -> p.x, (p, v) -> p.x = ((Number)v).intValue()))
				.build();
		MutablePoint p = new MutablePoint();
		p.x = 1;
		p.y = 2;
		JsonObject json = reg.serialize(p);
		assertEquals(List.of("y", "x"), new ArrayList<>(json.keySet()));     // the order of add()
		MutablePoint back = reg.deserialize(MutablePoint.class, json);
		assertEquals(1, back.x);
		assertEquals(2, back.y);
	}
	
	public void testMissingNoArgConstructor() throws Exception {
		SimpleRegistry reg = registry(Point.class);
		assertNotNull(reg.serialize(new Point(1, 2)));
		assertThrows(JsonException.class, () -> reg.deserialize(Point.class, JsonObject.of("x", 1)));
	}
	
	public void testMapNullKeyAndNonObject() throws Exception {
		SimpleRegistry reg = registry();
		Map<String,Object> m = new HashMap<>();
		m.put(null, 1);
		assertThrows(JsonException.class, () -> reg.serialize(m));
		assertThrows(JsonException.class, () -> reg.deserialize(Map.class, JsonArray.of(1)));
		assertThrows(JsonException.class, () -> reg.deserialize(List.class, JsonObject.of("a", 1)));
	}

	// ------------------------------------------------------------------
	// Second review
	// ------------------------------------------------------------------

	public record Address(String city, int zip) {}
	public record Person(String name, int age, boolean active, Address address, List<Address> previous, Map<String,Integer> scores, char initial) {}
	public record Box<T>(T value, List<T> values) {}
	public static class BoxHolder {
		Box<Address> box;
		public BoxHolder() {
		}
	}

	public void testRecords() throws Exception {
		SimpleRegistry reg = registry(Address.class, Person.class);
		Person p = new Person("Ada", 36, true, new Address("London", 1), List.of(new Address("Paris", 2)), Map.of("x", 3), 'A');
		JsonObject json = reg.serialize(p);
		assertEquals(List.of("name","age","active","address","previous","scores","initial"), new ArrayList<>(json.keySet()));
		assertEquals("London", json.getObject("address").getString("city"));
		assertEquals("A", json.get("initial"));
		Person back = reg.deserialize(Person.class, json);
		assertEquals(p, back);

		// Missing components: null, or the primitive default
		Person partial = reg.deserialize(Person.class, JsonObject.of("name", "Bob"));
		assertEquals(new Person("Bob", 0, false, null, null, null, '\0'), partial);

		// Generic record, bound through the field type
		SimpleRegistry reg2 = registry(Address.class, Box.class, BoxHolder.class);
		BoxHolder h = new BoxHolder();
		h.box = new Box<>(new Address("Rome", 3), List.of(new Address("Oslo", 4)));
		JsonObject hj = reg2.serialize(h);
		BoxHolder hb = reg2.deserialize(BoxHolder.class, hj);
		assertEquals(h.box, hb.box);
		assertEquals("Oslo", hb.box.values().get(0).city());
	}

	public static class Chars {
		char c;
		Character boxed;
		char[] text;
		Character[] boxes;
		public Chars() {
		}
	}

	public void testCharacters() throws Exception {
		SimpleRegistry reg = registry(Chars.class);
		Chars c = new Chars();
		c.c = 'x';
		c.boxed = 'y';
		c.text = "hello".toCharArray();
		c.boxes = new Character[] {'a','b'};
		JsonObject json = reg.serialize(c);
		assertEquals("{\"c\":\"x\",\"boxed\":\"y\",\"text\":\"hello\",\"boxes\":[\"a\",\"b\"]}", json.stringify());
		Chars back = reg.deserialize(Chars.class, json);
		assertEquals('x', back.c);
		assertEquals(Character.valueOf('y'), back.boxed);
		assertEquals("hello", new String(back.text));
		assertEquals(List.of('a','b'), List.of(back.boxes));
		assertThrows(JsonException.class, () -> reg.deserialize(Chars.class, JsonObject.of("c", "too long")));
		// A char[] can also be read from an array of characters
		assertEquals("ab", new String(reg.deserialize(char[].class, JsonArray.of("a","b"))));
	}

	public static class Collections2 {
		java.util.Collection<String> col;
		Iterable<Integer> it;
		public Collections2() {
		}
	}

	public void testCollectionAndIterableFields() throws Exception {
		SimpleRegistry reg = registry(Collections2.class);
		Collections2 c = new Collections2();
		c.col = new LinkedHashSet<>(List.of("a","b"));
		c.it = List.of(1,2);
		JsonObject json = reg.serialize(c);
		assertEquals("{\"col\":[\"a\",\"b\"],\"it\":[1,2]}", json.stringify());
		Collections2 back = reg.deserialize(Collections2.class, json);
		assertEquals(List.of("a","b"), new ArrayList<>(back.col));
		assertEquals(List.of(1,2), back.it);
	}

	public static class Pojo {
		String name;
		public Pojo() {
		}
		Pojo(String name) {
			this.name = name;
		}
		@Override
		public String toString() {
			return "POJO!";
		}
	}
	public static class Loose {
		Object any;
		@SuppressWarnings("rawtypes")
		List raw;
		Map<String,Object> map;
		public Loose() {
		}
	}

	@SuppressWarnings("unchecked")
	public void testPojoInsideObjectValues() throws Exception {
		SimpleRegistry reg = registry(Pojo.class, Loose.class);
		Loose l = new Loose();
		l.any = new Pojo("a");
		l.raw = new ArrayList<>(List.of(new Pojo("b"), 1));
		l.map = new LinkedHashMap<>();
		l.map.put("p", new Pojo("c"));
		l.map.put("list", List.of(new Pojo("d")));
		JsonObject json = reg.serialize(l);
		assertEquals("{\"any\":{\"name\":\"a\"},\"raw\":[{\"name\":\"b\"},1],\"map\":{\"p\":{\"name\":\"c\"},\"list\":[{\"name\":\"d\"}]}}", json.stringify());
		// A class without an adapter is still kept as is
		assertEquals("x", ((JsonObject)reg.serialize(new LinkedHashMap<>(Map.of("k", new StringBuilder("x"))))).get("k").toString());
	}

	public static class Shape {
		String kind = "shape";
		public Shape() {
		}
	}
	public static class Circle extends Shape {
		double radius = 2;
		public Circle() {
		}
	}
	public static class Drawing {
		Shape main;
		List<Shape> shapes;
		public Drawing() {
		}
	}

	public void testPolymorphicFieldsKeepSubclassData() throws Exception {
		SimpleRegistry reg = registry(Shape.class, Circle.class, Drawing.class);
		Drawing d = new Drawing();
		d.main = new Circle();
		d.shapes = List.of(new Shape(), new Circle());
		JsonObject json = reg.serialize(d);
		assertEquals(2.0, json.getObject("main").getDouble("radius"));
		// The list items too
		assertFalse(json.getArray("shapes").getObject(0).containsKey("radius"));
		assertEquals(2.0, json.getArray("shapes").getObject(1).getDouble("radius"));
		// Read back as the declared type: the JSON is not typed
		Drawing back = reg.deserialize(Drawing.class, JsonObject.of("main", JsonObject.of("kind", "k")));
		assertEquals(Shape.class, back.main.getClass());
	}

	public void testBuilderFieldsAreCopied() throws Exception {
		SimpleClassAdapter.Builder<Pojo> b = SimpleClassAdapter.newBuilder(Pojo.class).reflection();
		SimpleClassAdapter<Pojo> a1 = b.build();
		b.add("extra", (o) -> "x", null);
		SimpleRegistry reg = SimpleRegistry.newBuilder().add(a1).build();
		assertEquals("{\"name\":\"n\"}", ((JsonObject)reg.serialize(new Pojo("n"))).stringify());
	}
}
