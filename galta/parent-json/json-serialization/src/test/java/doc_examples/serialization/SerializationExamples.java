package doc_examples.serialization;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.net.URI;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.SimpleRegistry;
import org.monflabs.json.serialization.TypeRef;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/Serialization.md
 */
public class SerializationExamples extends ProjectTestCase {

	// ------------------------------------------------------------------
	// Getting started
	// ------------------------------------------------------------------

	public static class Person {
		String name;
		int age;
		List<String> tags;
		public Person() {
		}
	}

	public void testSerializeDeserialize() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Person.class)
				.build();

		Person p = new Person();
		p.name = "Ada";
		p.age = 36;
		p.tags = List.of("math", "code");

		JsonObject json = registry.serialize(p);
		assertEquals("Ada", json.getString("name"));
		assertEquals(36, json.getInt("age"));
		assertEquals("code", json.getArray("tags").getString(1));

		Person copy = registry.deserialize(Person.class, json);
		assertEquals("Ada", copy.name);
		assertEquals(List.of("math", "code"), copy.tags);
	}

	public void testReaderWriter() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Person.class)
				.build();

		Person p = registry.deserialize(Person.class,
				new java.io.StringReader("{\"name\":\"Alan\",\"age\":41}"));
		assertEquals("Alan", p.name);
		assertNull(p.tags);                               // absent keys leave the field untouched

		java.io.StringWriter w = new java.io.StringWriter();
		registry.serialize(w, p, null, true);             // compact
		JsonObject back = (JsonObject)JsonFactory.get().parse(w.toString());
		assertEquals(41, back.getInt("age"));
		assertTrue(back.containsKey("tags"));             // null fields are written as null
		assertNull(back.get("tags"));
	}

	public void testUnknownKeyFails() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Person.class)
				.build();
		try {
			registry.deserialize(Person.class, JsonObject.of("name", "Ada", "email", "ada@example.com"));
			fail();
		} catch(JsonException e) {
			// Object doesn't have a field named email
			assertTrue(e.getMessage(), e.getMessage().contains("email"));
		}
		// A null value for an unknown key is ignored
		Person p = registry.deserialize(Person.class, JsonObject.of("name", "Ada", "email", null));
		assertEquals("Ada", p.name);
	}

	// ------------------------------------------------------------------
	// Fields picked up
	// ------------------------------------------------------------------

	public static class Base {
		String id;
		public Base() {
		}
	}
	public static class Account extends Base {
		static final String KIND = "account";           // static: skipped
		transient String password;                       // transient: skipped
		private BigDecimal balance;                      // private is fine
		public Account() {
		}
	}

	public void testFieldsPickedUp() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Account.class)
				.build();

		Account a = new Account();
		a.id = "A-1";
		a.password = "secret";
		a.balance = new BigDecimal("12.50");

		JsonObject json = registry.serialize(a);
		assertEquals(Set.of("id", "balance"), json.keySet());   // inherited 'id' included
		assertEquals(new BigDecimal("12.50"), json.get("balance"));
	}

	public void testTypedResultPitfall() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Person.class)
				.build();
		Person p = new Person();
		p.name = "Ada";
		// serialize() is generic in its result: nested directly in deserialize(),
		// Java infers the Reader overload and the call fails with a ClassCastException
		try {
			registry.deserialize(Person.class, registry.serialize(p));
			fail();
		} catch(ClassCastException e) {
			// expected
		}
		// Assign to a variable (or cast to Object) first
		Object json = registry.serialize(p);
		assertEquals("Ada", registry.deserialize(Person.class, json).name);
		// Or use toJson(), which returns an Object
		assertEquals("Ada", registry.fromJson(Person.class, registry.toJson(p)).name);
	}

	public void testFieldFilter() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(SimpleClassAdapter.newBuilder(Account.class)
						.reflection(f -> !f.getName().equals("balance"))
						.build())
				.build();

		Account a = new Account();
		a.id = "A-1";
		a.balance = BigDecimal.TEN;
		assertEquals(Set.of("id"), registry.<JsonObject>serialize(a).keySet());
	}

	// ------------------------------------------------------------------
	// Supported types
	// ------------------------------------------------------------------

	public static class Item {
		String sku;
		public Item() {
		}
		public Item(String sku) {
			this.sku = sku;
		}
	}
	public static class Order {
		long number;
		Double discount;
		char[] unused;                                   // see limitations: no char support
		int[] quantities;
		String[][] grid;
		Item[] items;
		Set<String> labels;
		Map<String, List<Item>> byWarehouse;
		public Order() {
		}
	}

	public void testSupportedTypes() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Item.class)
				.add(SimpleClassAdapter.newBuilder(Order.class)
						.reflection(f -> !f.getName().equals("unused"))
						.build())
				.build();

		Order o = new Order();
		o.number = 42L;
		o.discount = 0.1;
		o.quantities = new int[] { 1, 2 };
		o.grid = new String[][] { { "a", "b" }, { "c" } };
		o.items = new Item[] { new Item("X") };
		o.labels = Set.of("urgent");
		o.byWarehouse = Map.of("north", List.of(new Item("Y"), new Item("Z")));

		JsonObject json = registry.serialize(o);
		// {"number":42,"discount":0.1,"quantities":[1,2],"grid":[["a","b"],["c"]],
		//  "items":[{"sku":"X"}],"labels":["urgent"],
		//  "byWarehouse":{"north":[{"sku":"Y"},{"sku":"Z"}]}}
		assertEquals("c", json.getArray("grid").getArray(1).getString(0));
		assertEquals("Z", json.getObject("byWarehouse").getArray("north").getObject(1).getString("sku"));

		Order back = registry.deserialize(Order.class, json);
		assertEquals(42L, back.number);
		assertEquals(String[][].class, back.grid.getClass());
		assertEquals("X", back.items[0].sku);
		assertTrue(back.labels.contains("urgent"));
		assertEquals("Z", back.byWarehouse.get("north").get(1).sku);
	}

	public static class Page<T> {
		int total;
		List<T> rows;
		public Page() {
		}
	}
	public static class ItemPage extends Page<Item> {
		public ItemPage() {
		}
	}
	public static class Catalog {
		Page<Item> items;
		public Catalog() {
		}
	}

	public void testGenerics() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Item.class)
				.add(Page.class)
				.add(ItemPage.class)
				.add(Catalog.class)
				.build();

		// A field declared as Page<Item>: T is bound by the field's type
		Catalog c = new Catalog();
		c.items = new Page<>();
		c.items.rows = List.of(new Item("A"));
		JsonObject cj = registry.serialize(c);
		Catalog c2 = registry.deserialize(Catalog.class, cj);
		assertEquals("A", c2.items.rows.get(0).sku);

		// A subclass binding T through 'extends Page<Item>'
		ItemPage ip = new ItemPage();
		ip.rows = List.of(new Item("B"));
		JsonObject ipj = registry.serialize(ip);
		ItemPage ip2 = registry.deserialize(ItemPage.class, ipj);
		assertEquals("B", ip2.rows.get(0).sku);

		// A top-level generic object: pass the type arguments explicitly
		Page<Item> page = new Page<>();
		page.rows = List.of(new Item("C"));
		ClassAdapter[] itemParam = { registry.findAdapter(Item.class) };
		JsonObject json = registry.serialize(page, itemParam);
		Page<Item> page2 = registry.deserialize(Page.class, json, itemParam);
		assertEquals("C", page2.rows.get(0).sku);
	}

	// ------------------------------------------------------------------
	// Custom adapters
	// ------------------------------------------------------------------

	public static class LocalDateAdapter extends BaseClassAdapter {
		public LocalDateAdapter() {
			super(LocalDate.class);
		}
		@Override
		public Object serialize(Object value, ClassAdapter[] genericParams) {
			return value != null ? value.toString() : null;
		}
		@Override
		public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
			return jsonValue != null ? LocalDate.parse((String)jsonValue) : null;
		}
	}
	public static class Event {
		String title;
		LocalDate date;
		public Event() {
		}
	}

	public void testCustomClassAdapter() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(new LocalDateAdapter())
				.add(Event.class)
				.build();

		Event e = new Event();
		e.title = "Launch";
		e.date = LocalDate.of(2026, 9, 26);
		JsonObject json = registry.serialize(e);
		assertEquals("2026-09-26", json.getString("date"));
		assertEquals(e.date, registry.deserialize(Event.class, json).date);
	}

	public static class Point {
		private final int x;
		private final int y;
		private int[] cache;                              // not exposed
		public Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
	public static class MutablePoint {
		int x, y;
	}

	public void testLambdaFields() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(MutablePoint.class, b -> b
						.add("x", (MutablePoint p) -> p.x, (p, v) -> p.x = ((Number)v).intValue())
						.add("y", (MutablePoint p) -> p.y, (p, v) -> p.y = ((Number)v).intValue())
						.add("sum", (MutablePoint p) -> p.x + p.y, (p, v) -> { /* computed: ignored */ }))
				.build();

		MutablePoint p = new MutablePoint();
		p.x = 2;
		p.y = 3;
		JsonObject json = registry.serialize(p);
		assertEquals(JsonObject.of("x", 2, "y", 3, "sum", 5), json);
		assertEquals(3, registry.deserialize(MutablePoint.class, json).y);
	}

	public void testFactory() throws Exception {
		// Point has no no-arg constructor: give the adapter a factory
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(SimpleClassAdapter.newBuilder(Point.class)
						.factory(() -> new Point(0, 0))
						.reflection(f -> !f.getName().equals("cache"))
						.build())
				.build();

		Point p = registry.deserialize(Point.class, JsonObject.of("x", 4, "y", 5));
		assertEquals(4, p.x);                             // reflection writes final fields too
		assertEquals(5, p.y);
	}

	// ------------------------------------------------------------------
	// Class factory
	// ------------------------------------------------------------------

	public static class Node {
		String name;
		Node next;
		public Node() {
		}
	}

	public void testClassFactory() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.classFactory(clazz -> clazz.getName().startsWith("doc_examples.")
						? SimpleClassAdapter.newBuilder(clazz).reflection().build()
						: null)
				.build();

		Node n = new Node();
		n.name = "a";
		n.next = new Node();
		n.next.name = "b";

		JsonObject json = registry.serialize(n);         // no explicit add(Node.class)
		assertEquals("b", json.getObject("next").getString("name"));
		assertSame(registry.findAdapter(Node.class), registry.findAdapter(Node.class));   // created once
	}

	// ------------------------------------------------------------------
	// Limitations
	// ------------------------------------------------------------------

	public void testExactClassLookup() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Base.class)
				.build();

		// Collections, maps and enums are found under their interface, even at the top level
		Object list = registry.serialize(new ArrayList<>(List.of("a")));
		assertEquals(org.monflabs.json.JsonArray.of("a"), list);

		// Registered classes are looked up by their exact runtime class
		Account acc = new Account();
		acc.id = "A";
		try {
			registry.serialize(acc);                      // Account extends Base
			fail();
		} catch(JsonException e) {
			// Missing adapter for Java class class ...Account
			assertTrue(e.getMessage(), e.getMessage().contains("Missing adapter"));
		}
		// Explicit parameters for a top-level generic collection
		ClassAdapter[] strings = { registry.findAdapter(String.class) };
		Object json = registry.findAdapter(List.class).serialize(new ArrayList<>(List.of("a")), strings);
		assertEquals(org.monflabs.json.JsonArray.of("a"), json);
	}

	public static class Box {
		ArrayList<String> concrete;                       // concrete collection type
		public Box() {
		}
	}
	public enum Color { RED, GREEN }
	public static class Paint {
		Color color;
		public Paint() {
		}
	}

	public void testCollectionsAndEnums() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Box.class)
				.add(Paint.class)
				.build();

		// A field declared with a concrete collection type is read back as that type
		Box b = new Box();
		b.concrete = new ArrayList<>(List.of("x"));
		JsonObject json = registry.serialize(b);
		assertEquals(org.monflabs.json.JsonArray.of("x"), json.get("concrete"));
		Box b2 = registry.deserialize(Box.class, json);
		assertEquals(ArrayList.class, b2.concrete.getClass());
		assertEquals(List.of("x"), b2.concrete);

		// An enum is its constant name
		Paint p = new Paint();
		p.color = Color.RED;
		JsonObject pj = registry.serialize(p);
		assertEquals("RED", pj.get("color"));
		assertEquals(Color.RED, registry.deserialize(Paint.class, pj).color);
		try {
			registry.deserialize(Paint.class, JsonObject.of("color", "BLUE"));
			fail();
		} catch(JsonException e) {
			// BLUE is not a constant of ...Color
		}
	}

	public static class Holder {
		Base ref;
		Item a;
		Item b;
		public Holder() {
		}
	}

	public void testDeclaredTypesAndSharedReferences() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Base.class)
				.add(Item.class)
				.add(Holder.class)
				.build();
		Holder h = new Holder();
		Account acc = new Account();
		acc.id = "A";
		acc.balance = BigDecimal.ONE;
		h.ref = acc;                                      // declared as Base
		h.a = h.b = new Item("shared");

		JsonObject json = registry.serialize(h);
		assertEquals(JsonObject.of("id", "A"), json.getObject("ref"));   // 'balance' is lost: Account has no adapter
		Holder back = registry.deserialize(Holder.class, json);
		assertEquals(Base.class, back.ref.getClass());
		assertNotSame(back.a, back.b);                    // shared reference read back twice
		assertEquals("shared", back.b.sku);
	}

	public void testCyclesDetected() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Node.class)
				.build();
		Node n = new Node();
		n.name = "loop";
		n.next = n;
		try {
			registry.serialize(n);
			fail();
		} catch(JsonException e) {
			// Cannot serialize a cyclic object graph: an instance of ...Node contains itself
			assertTrue(e.getMessage(), e.getMessage().contains("cyclic"));
		}
	}

	public void testNoArgConstructorRequired() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Point.class)
				.build();
		assertNotNull(registry.serialize(new Point(1, 2)));
		try {
			registry.deserialize(Point.class, JsonObject.of("x", 1));
			fail();
		} catch(JsonException e) {
			assertTrue(e.getCause() instanceof NoSuchMethodException);
		}
	}

	// ------------------------------------------------------------------
	// Null values
	// ------------------------------------------------------------------

	public static class Settings {
		String theme = "dark";
		int fontSize = 12;
		Optional<String> locale = Optional.of("en");
		public Settings() {
		}
	}

	public void testNullValues() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Settings.class)
				.build();

		Settings s = registry.deserialize(Settings.class, JsonObject.of("theme", null, "locale", null));
		assertNull(s.theme);                              // null replaces the default
		assertEquals(Optional.empty(), s.locale);         // an Optional becomes empty
		assertEquals(12, s.fontSize);                     // absent: untouched

		try {
			registry.deserialize(Settings.class, JsonObject.of("fontSize", null));
			fail();
		} catch(JsonException e) {
			// A null JSON value cannot be assigned to the int field fontSize (at $.fontSize)
			assertTrue(e.getMessage(), e.getMessage().contains("fontSize"));
		}
	}

	// ------------------------------------------------------------------
	// NaN and infinities
	// ------------------------------------------------------------------

	public static class Sample {
		double value;
		public Sample() {
		}
	}

	public void testNonFiniteNumbers() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.nonFiniteNumbersAsStrings(true)
				.add(Sample.class)
				.build();

		Sample s = new Sample();
		s.value = Double.NaN;
		JsonObject json = registry.serialize(s);
		assertEquals("NaN", json.get("value"));          // {"value":"NaN"}
		assertTrue(Double.isNaN(registry.deserialize(Sample.class, JsonObject.parse(json.stringify())).value));
	}

	// ------------------------------------------------------------------
	// Value types
	// ------------------------------------------------------------------

	public static class Meeting {
		UUID id;
		LocalDate day;
		LocalTime start;
		Duration length;
		ZoneId zone;
		Optional<URI> link;
		public Meeting() {
		}
	}

	public void testValueTypes() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Meeting.class)
				.build();

		Meeting m = new Meeting();
		m.id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
		m.day = LocalDate.of(2026, 9, 26);
		m.start = LocalTime.of(9, 30);
		m.length = Duration.ofMinutes(45);
		m.zone = ZoneId.of("Europe/Paris");
		m.link = Optional.empty();

		JsonObject json = registry.serialize(m);
		// {"id":"123e4567-e89b-12d3-a456-426614174000","day":"2026-09-26","start":"09:30:00",
		//  "length":"PT45M","zone":"Europe/Paris","link":null}
		assertEquals("2026-09-26", json.get("day"));
		assertEquals("09:30:00", json.get("start"));
		assertEquals("PT45M", json.get("length"));
		assertNull(json.get("link"));

		Meeting back = registry.deserialize(Meeting.class, json);
		assertEquals(m.start, back.start);
		assertEquals(Optional.empty(), back.link);
	}

	// ------------------------------------------------------------------
	// Declared collection types
	// ------------------------------------------------------------------

	public static class Index {
		SortedMap<String, Integer> counts;               // read back as a TreeMap
		Deque<String> history;                           // read back as an ArrayDeque
		EnumSet<Color> colors;                           // EnumSet.noneOf(Color.class)
		public Index() {
		}
	}

	public void testDeclaredCollectionTypes() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Index.class)
				.build();
		Index i = registry.deserialize(Index.class, JsonObject.of(
				"counts", JsonObject.of("b", 2, "a", 1),
				"history", org.monflabs.json.JsonArray.of("x", "y"),
				"colors", org.monflabs.json.JsonArray.of("GREEN")));
		assertEquals(TreeMap.class, i.counts.getClass());
		assertEquals("a", i.counts.firstKey());
		assertEquals(ArrayDeque.class, i.history.getClass());
		assertEquals(EnumSet.of(Color.GREEN), i.colors);
	}

	// ------------------------------------------------------------------
	// Sealed types
	// ------------------------------------------------------------------

	public sealed interface Shape permits Circle, Square {
	}
	public record Circle(double radius) implements Shape {
	}
	public record Square(double side) implements Shape {
	}
	public static class Drawing {
		List<Shape> shapes;
		public Drawing() {
		}
	}

	public void testSealedTypes() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.sealedTypes()                            // "@type", or sealedTypes("kind")
				.add(Drawing.class)
				.build();

		Drawing d = new Drawing();
		d.shapes = List.of(new Circle(1), new Square(2));
		JsonObject json = registry.serialize(d);
		// {"shapes":[{"@type":"Circle","radius":1.0},{"@type":"Square","side":2.0}]}
		assertEquals("Circle", json.getArray("shapes").getObject(0).getString("@type"));

		Drawing back = registry.deserialize(Drawing.class, json);
		assertEquals(new Square(2), back.shapes.get(1));
	}

	// ------------------------------------------------------------------
	// Generic types at the top level
	// ------------------------------------------------------------------

	public void testTypeRef() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Item.class)
				.build();

		Object json = registry.toJson(List.of(new Item("A"), new Item("B")));
		List<Item> items = registry.fromJson(new TypeRef<List<Item>>() {}, json);
		assertEquals("B", items.get(1).sku);

		Map<String, Item> byId = registry.fromJson(new TypeRef<Map<String, Item>>() {},
				JsonObject.of("a", JsonObject.of("sku", "A")));
		assertEquals("A", byId.get("a").sku);
	}

	// ------------------------------------------------------------------
	// Typed lambda properties
	// ------------------------------------------------------------------

	public static class Ticket {
		Item item;
		LocalDate due;
		public Ticket() {
		}
	}

	public void testTypedLambdaFields() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Item.class)
				.add(Ticket.class, b -> b
						.add("item", Item.class, (Ticket t) -> t.item, (t, v) -> t.item = v)
						.add("due", LocalDate.class, (Ticket t) -> t.due, (t, v) -> t.due = v))
				.build();

		Ticket t = new Ticket();
		t.item = new Item("X");
		t.due = LocalDate.of(2026, 10, 1);
		JsonObject json = registry.serialize(t);
		// {"item":{"sku":"X"},"due":"2026-10-01"}
		assertEquals("2026-10-01", json.get("due"));
		assertEquals("X", registry.deserialize(Ticket.class, json).item.sku);
	}

	// ------------------------------------------------------------------
	// Errors
	// ------------------------------------------------------------------

	public static class Basket {
		List<Line> lines;
		public Basket() {
		}
	}
	public static class Line {
		int quantity;
		public Line() {
		}
	}

	public void testErrorPaths() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.add(Basket.class)
				.add(Line.class)
				.build();
		try {
			registry.deserialize(Basket.class, JsonObject.of("lines",
					org.monflabs.json.JsonArray.of(JsonObject.of("quantity", 1), JsonObject.of("quantity", 1.5))));
			fail();
		} catch(SerializationException e) {
			// Number 1.5 has a fraction, cannot convert it to int (at $.lines[1].quantity)
			assertEquals("$.lines[1].quantity", e.getPath());
		}
	}

	public void testMaxDepth() throws Exception {
		SimpleRegistry registry = SimpleRegistry.newBuilder()
				.maxDepth(100)                            // 1000 by default
				.add(Node.class)
				.build();
		Node head = new Node();
		Node n = head;
		for(int i=0; i<200; i++) {
			n = n.next = new Node();
		}
		try {
			registry.serialize(head);
			fail();
		} catch(JsonException e) {
			// Cannot serialize an object graph nested deeper than 100 levels (at $.next.next...)
			assertTrue(e.getMessage(), e.getMessage().contains("deeper than 100"));
		}
	}
}
