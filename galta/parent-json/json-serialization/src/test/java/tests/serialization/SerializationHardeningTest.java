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

import java.io.StringWriter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.NumberConverter;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.SimpleRegistry;
import org.monflabs.json.serialization.TypeRef;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;
import org.monflabs.json.serialization.classes.collections.ListClassAdapter;

import tests.ProjectTestCase;

/**
 * Regression tests of the review fixes: number conversion limits, adapter initialization
 * failures, polymorphic items, type variable bounds, nulls, NaN, declared collection types,
 * registry concurrency, records, error paths, JSON values, JDK classes, map keys, depth,
 * registry builds, lambda properties, built-in value types, sealed types, typed API.
 */
public class SerializationHardeningTest extends ProjectTestCase {

	public enum Color { RED, GREEN, BLUE }

	private static SimpleRegistry registry(Class<?>... classes) {
		SimpleRegistry.Builder b = SimpleRegistry.newBuilder();
		for(Class<?> c: classes) {
			b.add(c);
		}
		return b.build();
	}

	private static String message(Throwable t) {
		return t.getMessage()!=null ? t.getMessage() : "";
	}

	// ------------------------------------------------------------------
	// 1. Huge numbers are rejected before being materialized
	// ------------------------------------------------------------------

	public void testHugeExponentRejectedQuickly() throws Exception {
		long start = System.nanoTime();
		BigDecimal huge = new BigDecimal("1e10000000");
		BigDecimal tiny = new BigDecimal("1e-10000000");
		assertThrows(JsonException.class, () -> NumberConverter.toInt(huge));
		assertThrows(JsonException.class, () -> NumberConverter.toLong(huge));
		assertThrows(JsonException.class, () -> NumberConverter.toByte(huge));
		assertThrows(JsonException.class, () -> NumberConverter.toShort(huge));
		assertThrows(JsonException.class, () -> NumberConverter.toBigInteger(huge));
		assertThrows(JsonException.class, () -> NumberConverter.toInt(tiny));
		assertThrows(JsonException.class, () -> NumberConverter.toBigInteger(tiny));
		assertThrows(JsonException.class, () -> NumberConverter.toBigInteger(new BigDecimal("-1e10000000")));
		// Parsed from a JSON text
		Object parsed = JsonFactory.get().parse("[1e10000000]");
		Object n = ((JsonArray)parsed).get(0);
		if(n instanceof BigDecimal) {
			assertThrows(JsonException.class, () -> NumberConverter.toLong(n));
		}
		assertTrue("Took too long", System.nanoTime()-start < 2_000_000_000L);

		// The valid conversions still work
		assertEquals(100, NumberConverter.toInt(new BigDecimal("100.000")));
		assertEquals(100, NumberConverter.toInt(new BigDecimal("1E+2")));
		assertEquals(0, NumberConverter.toInt(new BigDecimal("0E-10000000")));
		assertEquals(new BigInteger("1"+"0".repeat(400)), NumberConverter.toBigInteger(new BigDecimal("1e400")));
		assertEquals(Long.MAX_VALUE, NumberConverter.toLong(new BigDecimal(Long.MAX_VALUE)));
		assertThrows(JsonException.class, () -> NumberConverter.toLong(new BigDecimal(Long.MAX_VALUE).add(BigDecimal.ONE)));
		assertThrows(JsonException.class, () -> NumberConverter.toInt(new BigDecimal("1.5")));
		assertThrows(JsonException.class, () -> NumberConverter.toInt(new BigDecimal("0.5")));
	}

	// ------------------------------------------------------------------
	// 2. A failed initialization can be retried
	// ------------------------------------------------------------------

	public static class Inner {
		String name;
		public Inner() {
		}
		Inner(String name) {
			this.name = name;
		}
	}
	public static class Outer {
		Inner inner;
		public Outer() {
		}
	}

	public void testFailedInitCanBeRetried() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Outer.class)
				.classFactory(c -> {
					if(c==Inner.class && calls.incrementAndGet()==1) {
						throw new JsonException(null, "Not yet");
					}
					return c==Inner.class ? SimpleClassAdapter.newBuilder(c).reflection().build() : null;
				})
				.build();
		Outer o = new Outer();
		o.inner = new Inner("x");
		JsonException e = assertThrows(JsonException.class, () -> reg.serialize(o));
		assertTrue(message(e), message(e).contains("Not yet"));
		// Used to NPE: the Outer adapter was bound to the registry, its field not initialized
		JsonObject json = reg.serialize(o);
		assertEquals("x", json.getObject("inner").getString("name"));
		assertEquals("x", reg.deserialize(Outer.class, json).inner.name);
	}

	public void testMissingAdapterReportedAgain() throws Exception {
		SimpleRegistry reg = registry(Outer.class);
		JsonException e1 = assertThrows(JsonException.class, () -> reg.findAdapter(Outer.class));
		JsonException e2 = assertThrows(JsonException.class, () -> reg.findAdapter(Outer.class));
		assertTrue(message(e1), message(e1).contains("Missing adapter"));
		assertTrue(message(e2), message(e2).contains("Missing adapter"));
	}

	// ------------------------------------------------------------------
	// 3. Polymorphic items
	// ------------------------------------------------------------------

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
		List<Shape> list;
		Set<Shape> set;
		Map<String,Shape> map;
		Shape[] array;
		Optional<Shape> optional;
		public Drawing() {
		}
	}

	public void testPolymorphicItemsKeepSubclassData() throws Exception {
		SimpleRegistry reg = registry(Shape.class, Circle.class, Drawing.class);
		Drawing d = new Drawing();
		d.list = List.of(new Shape(), new Circle());
		d.set = new LinkedHashSet<>(List.of(new Circle()));
		d.map = Map.of("c", new Circle());
		d.array = new Shape[] { new Circle() };
		d.optional = Optional.of(new Circle());
		JsonObject json = reg.serialize(d);
		assertFalse(json.getArray("list").getObject(0).containsKey("radius"));
		assertEquals(2.0, json.getArray("list").getObject(1).getDouble("radius"));
		assertEquals(2.0, json.getArray("set").getObject(0).getDouble("radius"));
		assertEquals(2.0, json.getObject("map").getObject("c").getDouble("radius"));
		assertEquals(2.0, json.getArray("array").getObject(0).getDouble("radius"));
		assertEquals(2.0, json.getObject("optional").getDouble("radius"));
	}

	// ------------------------------------------------------------------
	// 4. Type variables: bounds, generic arrays, enclosing classes
	// ------------------------------------------------------------------

	public static class Item {
		String sku;
		public Item() {
		}
		Item(String sku) {
			this.sku = sku;
		}
	}
	public static class Bounded<T extends Item> {
		T item;
		List<T> items;
		public Bounded() {
		}
	}
	public static class Arrays<T> {
		T[] items;
		List<String>[] lists;
		public Arrays() {
		}
	}
	public static class ArraysHolder {
		Arrays<Item> arrays;
		public ArraysHolder() {
		}
	}
	public static class Enclosing<T> {
		public class Nested {
			T value;
		}
		Nested nested;
	}
	public static class EnclosingHolder {
		Enclosing<Item>.Nested nested;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testTypeVariableBounds() throws Exception {
		SimpleRegistry reg = registry(Item.class, Bounded.class);
		JsonObject json = JsonObject.of("item", JsonObject.of("sku", "A"), "items", JsonArray.of(JsonObject.of("sku", "B")));
		// Without generic parameters, T is read as its bound, not as a raw JSON object
		Bounded b = reg.deserialize(Bounded.class, json);
		assertEquals(Item.class, b.item.getClass());
		assertEquals("B", ((Item)b.items.get(0)).sku);
	}

	public void testGenericArrays() throws Exception {
		SimpleRegistry reg = registry(Item.class, Arrays.class, ArraysHolder.class);
		ArraysHolder h = new ArraysHolder();
		h.arrays = new Arrays<>();
		h.arrays.items = new Item[] { new Item("A") };
		@SuppressWarnings("unchecked")
		List<String>[] lists = new List[] { List.of("x") };
		h.arrays.lists = lists;
		JsonObject json = reg.serialize(h);
		assertEquals("A", json.getObject("arrays").getArray("items").getObject(0).getString("sku"));
		ArraysHolder back = reg.deserialize(ArraysHolder.class, json);
		assertEquals(Item.class, back.arrays.items[0].getClass());
		assertEquals("A", ((Item)back.arrays.items[0]).sku);
		assertEquals(List.of("x"), back.arrays.lists[0]);
	}

	public void testEnclosingClassTypeVariables() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Item.class)
				.add(EnclosingHolder.class)
				.add(Enclosing.Nested.class)
				.build();
		Enclosing<Item> e = new Enclosing<>();
		EnclosingHolder h = new EnclosingHolder();
		h.nested = e.new Nested();
		h.nested.value = new Item("Z");
		JsonObject json = reg.serialize(h);
		assertEquals("Z", json.getObject("nested").getObject("value").getString("sku"));
	}

	// ------------------------------------------------------------------
	// 5. Nulls
	// ------------------------------------------------------------------

	public static class Defaults {
		String s = "default";
		List<String> list = new ArrayList<>(List.of("a"));
		int i = 7;
		Integer boxed = 3;
		Optional<String> opt = Optional.of("x");
		public Defaults() {
		}
	}
	public record Rec(int count, String name) {
	}

	public void testNullAssignsNull() throws Exception {
		SimpleRegistry reg = registry(Defaults.class, Rec.class);
		Defaults d = reg.deserialize(Defaults.class, JsonObject.of("s", null, "list", null, "boxed", null, "opt", null));
		assertNull(d.s);
		assertNull(d.list);
		assertNull(d.boxed);
		assertEquals(Optional.empty(), d.opt);
		assertEquals(7, d.i);
		// A primitive rejects null instead of silently keeping its value
		JsonException e = assertThrows(JsonException.class, () -> reg.deserialize(Defaults.class, JsonObject.of("i", null)));
		assertTrue(message(e), message(e).contains("i"));
		assertTrue(e instanceof SerializationException);
		assertEquals("$.i", ((SerializationException)e).getPath());
		// Records: a missing primitive is 0, an explicit null is an error
		assertEquals(0, reg.deserialize(Rec.class, JsonObject.of("name", "n")).count());
		assertThrows(JsonException.class, () -> reg.deserialize(Rec.class, JsonObject.of("count", null)));
		assertNull(reg.deserialize(Rec.class, JsonObject.of("count", 1, "name", null)).name());
	}

	// ------------------------------------------------------------------
	// 6. NaN and the infinities
	// ------------------------------------------------------------------

	public static class Measures {
		double d;
		float f;
		Double boxed;
		double[] array;
		List<Double> list;
		public Measures() {
		}
	}

	public void testNonFiniteNumbers() throws Exception {
		Measures m = new Measures();
		m.d = Double.NaN;
		m.f = Float.POSITIVE_INFINITY;
		m.boxed = Double.NEGATIVE_INFINITY;
		m.array = new double[] { 1, Double.NaN };
		m.list = List.of(Double.POSITIVE_INFINITY);

		// Default: written as numbers, null once stringified, rejected by a primitive
		SimpleRegistry plain = registry(Measures.class);
		StringWriter w = new StringWriter();
		plain.serialize(w, m, null, true);
		assertTrue(w.toString(), w.toString().contains("\"d\":null"));
		JsonException e = assertThrows(JsonException.class, () -> plain.deserialize(Measures.class, JsonObject.parse(w.toString())));
		assertTrue(message(e), message(e).contains("nonFiniteNumbersAsStrings"));

		// Option: written as strings, and read back
		SimpleRegistry reg = SimpleRegistry.newBuilder().nonFiniteNumbersAsStrings(true).add(Measures.class).build();
		JsonObject json = reg.serialize(m);
		assertEquals("NaN", json.get("d"));
		assertEquals("Infinity", json.get("f"));
		assertEquals("-Infinity", json.get("boxed"));
		assertEquals(JsonArray.of(1.0, "NaN"), json.get("array"));
		assertEquals(JsonArray.of("Infinity"), json.get("list"));
		Measures back = reg.deserialize(Measures.class, JsonObject.parse(json.stringify()));
		assertTrue(Double.isNaN(back.d));
		assertEquals(Float.POSITIVE_INFINITY, back.f);
		assertEquals(Double.NEGATIVE_INFINITY, back.boxed);
		assertTrue(Double.isNaN(back.array[1]));
		assertEquals(Double.POSITIVE_INFINITY, back.list.get(0));
		// The strings are always accepted
		assertTrue(Double.isNaN(plain.deserialize(Measures.class, JsonObject.of("d", "NaN")).d));
		assertThrows(JsonException.class, () -> plain.deserialize(Measures.class, JsonObject.of("d", "nan")));
	}

	// ------------------------------------------------------------------
	// 7. Declared collection types
	// ------------------------------------------------------------------

	static class PkgList extends ArrayList<String> {
		private static final long serialVersionUID = 1L;
	}
	public interface CustomList extends List<String> {
	}
	public static class Collections {
		SortedMap<String,Integer> sortedMap;
		NavigableMap<String,Integer> navigableMap;
		SortedSet<String> sortedSet;
		NavigableSet<String> navigableSet;
		Queue<String> queue;
		Deque<String> deque;
		EnumMap<Color,Integer> enumMap;
		EnumSet<Color> enumSet;
		PkgList pkgList;
		ConcurrentMap<String,Integer> concurrent;
		LinkedList<String> linked;
		public Collections() {
		}
	}
	public static class Custom {
		CustomList custom;
		public Custom() {
		}
	}

	public void testDeclaredCollectionTypes() throws Exception {
		SimpleRegistry reg = registry(Collections.class, Custom.class);
		JsonObject json = JsonObject.of(
				"sortedMap", JsonObject.of("b", 1, "a", 2),
				"navigableMap", JsonObject.of("b", 1, "a", 2),
				"sortedSet", JsonArray.of("b", "a"),
				"navigableSet", JsonArray.of("b", "a"),
				"queue", JsonArray.of("q"),
				"deque", JsonArray.of("d1", "d2"),
				"enumMap", JsonObject.of("GREEN", 1),
				"enumSet", JsonArray.of("BLUE", "RED"),
				"pkgList", JsonArray.of("p"),
				"concurrent", JsonObject.of("c", 1),
				"linked", JsonArray.of("l"));
		Collections c = reg.deserialize(Collections.class, json);
		assertEquals(TreeMap.class, c.sortedMap.getClass());
		assertEquals("a", c.sortedMap.firstKey());
		assertEquals(TreeMap.class, c.navigableMap.getClass());
		assertEquals(TreeSet.class, c.sortedSet.getClass());
		assertEquals("a", c.sortedSet.first());
		assertEquals(TreeSet.class, c.navigableSet.getClass());
		assertEquals(ArrayDeque.class, c.queue.getClass());
		assertEquals("d2", c.deque.getLast());
		assertEquals(Integer.valueOf(1), c.enumMap.get(Color.GREEN));
		assertEquals(EnumSet.of(Color.RED, Color.BLUE), c.enumSet);
		assertEquals(PkgList.class, c.pkgList.getClass());
		assertEquals(ConcurrentHashMap.class, c.concurrent.getClass());
		assertEquals(LinkedList.class, c.linked.getClass());
		// Round trip (the sorted collections are sorted)
		JsonObject json2 = reg.serialize(c);
		assertEquals(JsonArray.of("a", "b"), json2.get("sortedSet"));
		assertEquals(JsonArray.of("RED", "BLUE"), json2.get("enumSet"));
		assertEquals(json.get("deque"), json2.get("deque"));
		assertEquals(json.get("enumMap"), json2.get("enumMap"));
		assertEquals(json.get("pkgList"), json2.get("pkgList"));

		// No implementation for a custom interface: a clear error naming the field
		JsonException e = assertThrows(JsonException.class, () -> reg.deserialize(Custom.class, JsonObject.of("custom", JsonArray.of("x"))));
		assertTrue(message(e), message(e).contains("CustomList"));
		assertTrue(message(e), message(e).contains("$.custom"));
		// A null element in an ArrayDeque
		assertThrows(JsonException.class, () -> reg.deserialize(Collections.class, JsonObject.of("queue", JsonArray.of((Object)null))));
	}

	// ------------------------------------------------------------------
	// 8. Registry concurrency, negative cache
	// ------------------------------------------------------------------

	public static class Node {
		String name;
		Node next;
		List<Node> children;
		public Node() {
		}
	}

	public void testConcurrentLookups() throws Exception {
		AtomicInteger factoryCalls = new AtomicInteger();
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.classFactory(c -> {
					factoryCalls.incrementAndGet();
					return c.getName().startsWith("tests.") ? SimpleClassAdapter.newBuilder(c).reflection().build() : null;
				})
				.build();
		Node n = new Node();
		n.name = "a";
		n.next = new Node();
		n.next.name = "b";
		n.children = List.of(new Node());
		ExecutorService ex = Executors.newFixedThreadPool(8);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Object>> results = new ArrayList<>();
			for(int i=0; i<32; i++) {
				results.add(ex.submit(() -> {
					start.await();
					JsonObject json = reg.serialize(n);
					return reg.deserialize(Node.class, json).next.name;
				}));
			}
			start.countDown();
			for(Future<Object> f: results) {
				assertEquals("b", f.get());
			}
		} finally {
			ex.shutdown();
		}
		// The factory is called once per class, refusals included
		int calls = factoryCalls.get();
		assertNull(reg.findAdapterOrNull(StringBuilder.class));
		assertNull(reg.findAdapterOrNull(StringBuilder.class));
		assertEquals(calls+1, factoryCalls.get());
		assertThrows(JsonException.class, () -> reg.findAdapter(StringBuilder.class));
		assertEquals(calls+1, factoryCalls.get());
	}

	// ------------------------------------------------------------------
	// 9. Records and factories
	// ------------------------------------------------------------------

	public void testRecordFactoryRejected() throws Exception {
		JsonException e = assertThrows(JsonException.class, () -> SimpleClassAdapter.newBuilder(Rec.class).factory(() -> new Rec(0, null)).reflection().build());
		assertTrue(message(e), message(e).contains("record"));
	}

	// ------------------------------------------------------------------
	// 10. Error messages
	// ------------------------------------------------------------------

	public static class Line {
		int count;
		public Line() {
		}
	}
	public static class Order {
		List<Line> lines;
		Map<String,Line> byName;
		public Order() {
		}
	}

	public void testErrorMessagesHaveContext() throws Exception {
		SimpleRegistry reg = registry(Line.class, Order.class);
		SerializationException e = (SerializationException)assertThrows(JsonException.class, () -> reg.deserialize(Order.class,
				JsonObject.of("lines", JsonArray.of(JsonObject.of("count", 1), JsonObject.of("count", "two")))));
		assertEquals("$.lines[1].count", e.getPath());
		assertTrue(message(e), message(e).contains("two"));
		assertTrue(message(e), message(e).contains("$.lines[1].count"));

		e = (SerializationException)assertThrows(JsonException.class, () -> reg.deserialize(Order.class,
				JsonObject.of("byName", JsonObject.of("a b", JsonArray.of()))));
		assertEquals("$.byName['a b']", e.getPath());
		assertTrue(message(e), message(e).contains("a JSON object is expected"));
		assertTrue(message(e), message(e).contains(Line.class.getName()));

		// Not an object at the root: a message, not a bare IllegalStateException
		JsonException root = assertThrows(JsonException.class, () -> reg.deserialize(Line.class, "text"));
		assertTrue(message(root), message(root).contains("a JSON string"));
		assertTrue(message(root), message(root).contains(Line.class.getName()));
	}

	// ------------------------------------------------------------------
	// 11. JSON values: checked and copied
	// ------------------------------------------------------------------

	public static class JsonHolder {
		JsonObject object;
		JsonArray array;
		Object any;
		public JsonHolder() {
		}
	}

	public void testJsonValuesCheckedAndCopied() throws Exception {
		SimpleRegistry reg = registry(JsonHolder.class);
		JsonException e = assertThrows(JsonException.class, () -> reg.deserialize(JsonHolder.class, JsonObject.of("object", JsonArray.of(1))));
		assertTrue(message(e), message(e).contains("$.object"));
		assertThrows(JsonException.class, () -> reg.deserialize(JsonHolder.class, JsonObject.of("array", JsonObject.of())));

		JsonObject source = JsonObject.of("object", JsonObject.of("k", JsonArray.of(1)), "array", JsonArray.of(JsonObject.of()), "any", JsonObject.of("a", 1));
		JsonHolder h = reg.deserialize(JsonHolder.class, source);
		source.getObject("object").getArray("k").add(2);
		((JsonObject)source.get("any")).put("b", 2);
		assertEquals(1, h.object.getArray("k").size());
		assertFalse(((JsonObject)h.any).containsKey("b"));

		JsonObject json = reg.serialize(h);
		json.getObject("object").put("x", 1);
		assertFalse(h.object.containsKey("x"));
	}

	// ------------------------------------------------------------------
	// 12. Classes with JDK superclasses, enums
	// ------------------------------------------------------------------

	public static class AppError extends RuntimeException {
		private static final long serialVersionUID = 1L;
		String code;
		public AppError() {
		}
	}
	public static class Anything {
		Object value;
		public Anything() {
		}
	}

	public void testJdkSuperclassesAndEnums() throws Exception {
		SimpleRegistry reg = registry(AppError.class, Color.class);
		AppError err = new AppError();
		err.code = "E1";
		assertEquals(JsonObject.of("code", "E1"), reg.serialize(err));
		assertEquals("E1", reg.deserialize(AppError.class, JsonObject.of("code", "E1")).code);
		assertEquals("GREEN", reg.serialize(Color.GREEN));
		assertEquals(Color.BLUE, reg.deserialize(Color.class, "BLUE"));

		// An accept-all factory meeting a JDK class: a clear error
		SimpleRegistry all = SimpleRegistry.newBuilder()
				.classFactory(c -> SimpleClassAdapter.newBuilder(c).reflection().build())
				.build();
		Anything a = new Anything();
		a.value = new StringBuilder("x");
		JsonException e = assertThrows(JsonException.class, () -> all.serialize(a));
		assertTrue(message(e), message(e).contains("register an adapter"));
	}

	// ------------------------------------------------------------------
	// 13. Map keys with the same string form
	// ------------------------------------------------------------------

	public void testDuplicateMapKeys() throws Exception {
		SimpleRegistry reg = registry();
		Map<Object,String> m = new LinkedHashMap<>();
		m.put("1", "string");
		m.put(1, "int");
		JsonException e = assertThrows(JsonException.class, () -> reg.serialize(m));
		assertTrue(message(e), message(e).contains("same JSON form"));
	}

	// ------------------------------------------------------------------
	// 14. Depth
	// ------------------------------------------------------------------

	public void testDepthLimit() throws Exception {
		SimpleRegistry reg = registry(Node.class);
		Node head = new Node();
		Node n = head;
		for(int i=0; i<20_000; i++) {
			n.next = new Node();
			n = n.next;
		}
		JsonException e = assertThrows(JsonException.class, () -> reg.serialize(head));
		assertTrue(message(e), message(e).contains("deeper than 1000"));

		JsonObject deep = JsonObject.create();
		JsonObject o = deep;
		for(int i=0; i<20_000; i++) {
			JsonObject next = JsonObject.create();
			o.put("next", next);
			o = next;
		}
		e = assertThrows(JsonException.class, () -> reg.deserialize(Node.class, deep));
		assertTrue(message(e), message(e).contains("deeper than 1000"));

		// Configurable
		SimpleRegistry small = SimpleRegistry.newBuilder().maxDepth(3).add(Node.class).build();
		Node n3 = new Node();
		n3.next = new Node();
		n3.next.next = new Node();
		assertNotNull(small.serialize(n3));
		n3.next.next.next = new Node();
		assertThrows(JsonException.class, () -> small.serialize(n3));
		// The state is clean after the errors
		assertNotNull(reg.serialize(n3));
	}

	// ------------------------------------------------------------------
	// 15. Registry builds
	// ------------------------------------------------------------------

	public static class Upper extends org.monflabs.json.serialization.classes.BaseClassAdapter {
		public Upper() {
			super(String.class);
		}
		@Override
		public Object serialize(Object value, ClassAdapter[] genericParams) {
			return value!=null ? value.toString().toUpperCase() : null;
		}
		@Override
		public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
			return jsonValue;
		}
	}

	public void testBuildsDoNotShareBuiltinAdapters() throws Exception {
		SimpleRegistry.Builder b = SimpleRegistry.newBuilder();
		SimpleRegistry r1 = b.build();
		SimpleRegistry r2 = b.build();
		assertNotSame(r1.findAdapter(List.class), r2.findAdapter(List.class));
		assertNotSame(r1.findAdapter(Object.class), r2.findAdapter(Object.class));
		// A list in r1 holding a POJO known by r2 only
		SimpleRegistry r3 = SimpleRegistry.newBuilder().add(Item.class).build();
		r3.serialize(new ArrayList<>(List.of(new Item("a"))));
		Object json = r1.serialize(new ArrayList<>(List.of(new Item("a"))));
		assertTrue(((JsonArray)json).get(0) instanceof Item);   // kept as is: r1 has no adapter for Item

		// addDefaultAdapters() does not undo an override
		SimpleRegistry r4 = SimpleRegistry.newBuilder().add(new Upper()).addDefaultAdapters().build();
		assertEquals("ABC", r4.serialize("abc"));
	}

	// ------------------------------------------------------------------
	// 16. Lambda properties
	// ------------------------------------------------------------------

	public static class Card {
		Item item;
		LocalDate date;
		public Card() {
		}
	}

	public void testLambdaProperties() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(Item.class)
				.add(Card.class, b -> b
						// Untyped: V is Object, the writer receives the raw JSON value
						.add("raw", (Card c) -> (Object)c.item, (c, v) -> { })
						.add("item", Item.class, (Card c) -> c.item, (c, v) -> c.item = v)
						.add("date", LocalDate.class, (Card c) -> c.date, (c, v) -> c.date = v))
				.build();
		Card c = new Card();
		c.item = new Item("S");
		c.date = LocalDate.of(2026, 9, 26);
		JsonObject json = reg.serialize(c);
		// An untyped reader returning a POJO: serialized with its adapter, not put as is
		assertEquals(JsonObject.of("sku", "S"), json.get("raw"));
		assertEquals(JsonObject.of("sku", "S"), json.get("item"));
		assertEquals("2026-09-26", json.get("date"));
		Card back = reg.deserialize(Card.class, json);
		assertEquals("S", back.item.sku);
		assertEquals(c.date, back.date);
	}

	// ------------------------------------------------------------------
	// 17. Built-in value types
	// ------------------------------------------------------------------

	public static class Values {
		LocalDate localDate;
		LocalDateTime localDateTime;
		LocalTime localTime;
		Instant instant;
		OffsetDateTime offsetDateTime;
		ZonedDateTime zonedDateTime;
		Duration duration;
		Period period;
		ZoneId zoneId;
		UUID uuid;
		URI uri;
		Date date;
		Optional<String> present;
		Optional<Item> item;
		Optional<String> absent;
		Number number;
		Map<UUID,LocalDate> byId;
		public Values() {
		}
	}

	public void testBuiltinValueTypes() throws Exception {
		SimpleRegistry reg = registry(Values.class, Item.class);
		Values v = new Values();
		v.localDate = LocalDate.of(2026, 9, 26);
		v.localDateTime = LocalDateTime.of(2026, 9, 26, 10, 15, 30);
		v.localTime = LocalTime.of(10, 15);
		v.instant = Instant.parse("2026-09-26T10:15:30Z");
		v.offsetDateTime = OffsetDateTime.of(v.localDateTime, ZoneOffset.ofHours(2));
		v.zonedDateTime = ZonedDateTime.of(v.localDateTime, ZoneId.of("Europe/Paris"));
		v.duration = Duration.ofMinutes(90);
		v.period = Period.ofDays(3);
		v.zoneId = ZoneId.of("America/New_York");
		v.uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
		v.uri = URI.create("https://example.com/a?b=c");
		v.date = Date.from(v.instant);
		v.present = Optional.of("p");
		v.item = Optional.of(new Item("I"));
		v.absent = Optional.empty();
		v.number = new BigDecimal("1.5");
		v.byId = Map.of(v.uuid, v.localDate);
		JsonObject json = reg.serialize(v);
		assertEquals("2026-09-26", json.get("localDate"));
		assertEquals("2026-09-26T10:15:30", json.get("localDateTime"));
		assertEquals("10:15:00", json.get("localTime"));
		assertEquals("2026-09-26T10:15:30Z", json.get("instant"));
		assertEquals("2026-09-26T10:15:30+02:00", json.get("offsetDateTime"));
		assertEquals("PT1H30M", json.get("duration"));
		assertEquals("P3D", json.get("period"));
		assertEquals("America/New_York", json.get("zoneId"));
		assertEquals("123e4567-e89b-12d3-a456-426614174000", json.get("uuid"));
		assertEquals("2026-09-26T10:15:30Z", json.get("date"));
		assertEquals("p", json.get("present"));
		assertEquals(JsonObject.of("sku", "I"), json.get("item"));
		assertNull(json.get("absent"));
		assertTrue(json.containsKey("absent"));
		assertEquals(JsonObject.of(v.uuid.toString(), "2026-09-26"), json.get("byId"));

		Values back = reg.deserialize(Values.class, JsonObject.parse(json.stringify()));
		assertEquals(v.localDate, back.localDate);
		assertEquals(v.localDateTime, back.localDateTime);
		assertEquals(v.localTime, back.localTime);
		assertEquals(v.instant, back.instant);
		assertEquals(v.offsetDateTime, back.offsetDateTime);
		assertEquals(v.zonedDateTime, back.zonedDateTime);
		assertEquals(v.duration, back.duration);
		assertEquals(v.period, back.period);
		assertEquals(v.zoneId, back.zoneId);
		assertEquals(v.uuid, back.uuid);
		assertEquals(v.uri, back.uri);
		assertEquals(v.date, back.date);
		assertEquals(v.present, back.present);
		assertEquals("I", back.item.get().sku);
		assertEquals(Optional.empty(), back.absent);
		assertEquals(0, new BigDecimal("1.5").compareTo(new BigDecimal(back.number.toString())));
		assertEquals(v.byId, back.byId);

		// Top level, and subclasses of the value types
		assertEquals("+02:00", reg.serialize(ZoneOffset.ofHours(2)));
		assertEquals(LocalDate.of(2026, 1, 2), reg.deserialize(LocalDate.class, "2026-01-02"));
		JsonException e = assertThrows(JsonException.class, () -> reg.deserialize(Values.class, JsonObject.of("localDate", "26/09/2026")));
		assertTrue(message(e), message(e).contains("LocalDate"));
	}

	// ------------------------------------------------------------------
	// 18. Sealed types
	// ------------------------------------------------------------------

	public sealed interface Figure permits Round, Square, Polygon {
	}
	public record Round(double radius) implements Figure {
	}
	public record Square(double side) implements Figure {
	}
	public static sealed abstract class Polygon implements Figure permits Triangle {
		int sides;
	}
	public static final class Triangle extends Polygon {
		String name;
		public Triangle() {
			sides = 3;
		}
	}
	public static class Canvas {
		Figure main;
		List<Figure> figures;
		public Canvas() {
		}
	}

	public void testSealedTypes() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder().sealedTypes().add(Canvas.class).build();
		Canvas c = new Canvas();
		c.main = new Round(2);
		Triangle t = new Triangle();
		t.name = "t";
		c.figures = List.of(new Square(1), t);
		JsonObject json = reg.serialize(c);
		assertEquals(JsonObject.of("@type", "Round", "radius", 2.0), json.get("main"));
		assertEquals("Square", json.getArray("figures").getObject(0).get("@type"));
		assertEquals("Triangle", json.getArray("figures").getObject(1).get("@type"));
		Canvas back = reg.deserialize(Canvas.class, JsonObject.parse(json.stringify()));
		assertEquals(new Round(2), back.main);
		assertEquals(new Square(1), back.figures.get(0));
		assertEquals("t", ((Triangle)back.figures.get(1)).name);
		assertEquals(3, ((Triangle)back.figures.get(1)).sides);

		// Only the classes of the hierarchy
		JsonException e = assertThrows(JsonException.class, () -> reg.deserialize(Canvas.class, JsonObject.of("main", JsonObject.of("@type", "java.lang.Runtime"))));
		assertTrue(message(e), message(e).contains("Round"));
		assertThrows(JsonException.class, () -> reg.deserialize(Canvas.class, JsonObject.of("main", JsonObject.of("radius", 1))));

		// Custom property name
		SimpleRegistry kind = SimpleRegistry.newBuilder().sealedTypes("kind").add(Canvas.class).build();
		assertEquals("Round", ((JsonObject)((JsonObject)kind.serialize(c)).get("main")).get("kind"));

		// Not enabled: no adapter for the interface
		assertThrows(JsonException.class, () -> registry(Canvas.class).serialize(c));
	}

	// ------------------------------------------------------------------
	// 19. Small fixes
	// ------------------------------------------------------------------

	public static class Base {
		String id = "base";
		public Base() {
		}
	}
	public static class Derived extends Base {
		String id = "derived";
		public Derived() {
		}
	}

	public void testSmallFixes() throws Exception {
		SimpleRegistry reg = registry();
		// Primitive classes
		assertEquals(Integer.valueOf(5), reg.deserialize(int.class, 5));
		assertEquals(Boolean.TRUE, reg.deserialize(boolean.class, true));

		// A field rejected by the filter still hides the superclass field
		SimpleRegistry filtered = SimpleRegistry.newBuilder()
				.add(SimpleClassAdapter.newBuilder(Derived.class).reflection(f -> f.getDeclaringClass()!=Derived.class).build())
				.build();
		assertEquals(JsonObject.of(), filtered.serialize(new Derived()));

		// null is written as null
		StringWriter w = new StringWriter();
		reg.serialize(w, null, null, true);
		assertEquals("null", w.toString());

		// A null builder consumer
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SimpleRegistry.newBuilder().add(Item.class, null));
		assertTrue(message(e), message(e).contains("add(Class)"));

		// A concrete list adapter applies to its class only
		SimpleRegistry linked = SimpleRegistry.newBuilder().add(new ListClassAdapter(LinkedListOfStrings.class)).build();
		assertEquals(LinkedListOfStrings.class, linked.deserialize(LinkedListOfStrings.class, JsonArray.of("a")).getClass());
		assertEquals(ArrayList.class, linked.deserialize(List.class, JsonArray.of("a")).getClass());
	}
	public static class LinkedListOfStrings extends LinkedList<String> {
		private static final long serialVersionUID = 1L;
	}

	// ------------------------------------------------------------------
	// 20. Typed API, generic parameter count
	// ------------------------------------------------------------------

	public static class Props<V> extends HashMap<String,V> {
		private static final long serialVersionUID = 1L;
	}
	public static class PropsHolder {
		Props<Item> props;
		public PropsHolder() {
		}
	}

	public void testTypedApi() throws Exception {
		SimpleRegistry reg = registry(Item.class, PropsHolder.class);
		Object json = reg.toJson(List.of(new Item("a")));
		assertTrue(json instanceof JsonArray);
		List<Item> items = reg.fromJson(new TypeRef<List<Item>>() {}, json);
		assertEquals("a", items.get(0).sku);
		Type t = new TypeRef<Map<String,List<Item>>>() {}.getType();
		Map<String,List<Item>> m = reg.deserialize(t, JsonObject.of("k", JsonArray.of(JsonObject.of("sku", "b"))));
		assertEquals("b", m.get("k").get(0).sku);
		assertEquals(JsonObject.of("k", JsonArray.of(JsonObject.of("sku", "b"))), reg.toJson(m, t));
		Item i = reg.fromJson(Item.class, JsonObject.of("sku", "c"));
		assertEquals("c", i.sku);

		// A map subclass with its own generic parameters
		PropsHolder h = reg.deserialize(PropsHolder.class, JsonObject.of("props", JsonObject.of("x", JsonObject.of("sku", "d"))));
		assertEquals(Props.class, h.props.getClass());
		assertEquals("d", h.props.get("x").sku);

		// A wrong number of generic parameters
		ClassAdapter s = reg.findAdapter(String.class);
		JsonException e = assertThrows(JsonException.class, () -> reg.deserialize(Map.class, JsonObject.of("a", "b"), new ClassAdapter[] { s }));
		assertTrue(message(e), message(e).contains("generic parameter"));
	}
}
