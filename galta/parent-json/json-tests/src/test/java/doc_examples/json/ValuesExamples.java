package doc_examples.json;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonType;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JsonArrayAsArrayList;
import org.monflabs.json.java.JsonObjectAsLinkedMap;

import tests.ProjectTestCase;

/**
 * Samples of docs/GaltaJSON/Values.md
 */
public class ValuesExamples extends ProjectTestCase {

	public void testValueModel() {
		JsonObject o = JsonObject.parse("""
			{ "s": "text", "b": true, "i": 42, "l": 12345678901, "d": 1.5,
			  "n": null, "o": {}, "a": [] }
			""");
		assertEquals(String.class, o.get("s").getClass());
		assertEquals(Boolean.class, o.get("b").getClass());
		assertEquals(Integer.class, o.get("i").getClass());
		assertEquals(Long.class, o.get("l").getClass());
		assertEquals(Double.class, o.get("d").getClass());
		assertNull(o.get("n"));
		assertTrue(o.get("o") instanceof JsonObject);
		assertTrue(o.get("a") instanceof JsonArray);

		assertEquals(JsonType.NUMBER, JsonType.typeOf(o.get("i")));
		assertEquals(JsonType.NULL, JsonType.typeOf(o.get("n")));
		assertEquals(JsonType.UNKNOWN, JsonType.typeOf(new java.util.Date()));
	}

	public void testDefaultImplementations() {
		JsonObject o = JsonObject.create();
		JsonArray a = JsonArray.create();
		assertTrue(o instanceof JsonObjectAsLinkedMap);   // a java.util.LinkedHashMap
		assertTrue(a instanceof JsonArrayAsArrayList);    // a java.util.ArrayList

		// They are plain Java collections
		Map<String,Object> map = o;
		List<Object> list = a;
		map.put("z", 1);
		map.put("a", 2);
		list.add("x");
		assertEquals("[z, a]", o.keySet().toString());   // insertion order is kept
		assertEquals(1, a.size());
	}

	public void testCreating() {
		JsonObject empty = JsonObject.create();
		JsonObject person = JsonObject.of("name", "Ada", "born", 1815);
		JsonArray numbers = JsonArray.of(1, 2, 3);
		JsonObject parsed = JsonObject.parse("{\"name\":\"Ada\",\"born\":1815}");

		assertTrue(empty.isEmpty());
		assertEquals("{\"name\":\"Ada\",\"born\":1815}", person.stringify());
		assertEquals("[1,2,3]", numbers.stringify());
		assertEquals(person, parsed);
	}

	public void testFluentPut() {
		JsonObject o = JsonObject.create()
			.put("name", "Ada")
			.put("born", 1815)
			.put("active", true)
			.putNull("died")
			.put("langs", (JsonArray a) -> a.add("en").add("fr"))
			.put("address", (JsonObject a) -> a.put("city", "London"));
		assertEquals("{\"name\":\"Ada\",\"born\":1815,\"active\":true,\"died\":null,"
				+ "\"langs\":[\"en\",\"fr\"],\"address\":{\"city\":\"London\"}}", o.stringify());

		JsonArray a = JsonArray.create()
			.add(1)
			.add("two")
			.addNull()
			.add((JsonObject item) -> item.put("id", 4));
		assertEquals("[1,\"two\",null,{\"id\":4}]", a.stringify());
	}

	public void testPutObjectOverload() {
		JsonObject o = JsonObject.create();
		Object value = "x";
		// Map.put(String,Object) returns the previous value, like any Map
		Object previous = o.put("k", value);
		assertNull(previous);
		// putValue() is the fluent form for an untyped value
		o.putValue("k", value).putValue("k2", 2);
		assertEquals(2, o.size());
	}

	public void testTypedGetters() {
		JsonObject o = JsonObject.parse("{\"count\":42,\"price\":9.99,\"name\":\"Ada\",\"ok\":true,\"nothing\":null}");

		assertEquals(42, o.getInt("count"));
		assertEquals(42L, o.getLong("count"));
		assertEquals(42.0, o.getDouble("count"), 0);
		assertEquals(9, o.getInt("price"));                       // Number.intValue(): truncated
		assertEquals(new BigDecimal("9.99"), o.getBigDecimal("price"));
		assertEquals("Ada", o.getString("name"));
		assertTrue(o.getBoolean("ok"));
		assertEquals(Integer.valueOf(42), o.getIntObject("count"));

		// A wrong type, null or a missing key throws
		assertThrows(JsonException.class, () -> o.getInt("name"));
		assertThrows(JsonException.class, () -> o.getString("count"));
		assertThrows(JsonException.class, () -> o.getInt("nothing"));
		assertThrows(JsonException.class, () -> o.getInt("missing"));
	}

	public void testGettersWithDefault() {
		JsonObject o = JsonObject.parse("{\"count\":42,\"name\":\"Ada\",\"nothing\":null}");

		assertEquals(42, o.getInt("count", -1));
		assertEquals(-1, o.getInt("missing", -1));    // missing key: the default
		assertEquals(-1, o.getInt("nothing", -1));    // null value: the default
		assertEquals("?", o.getString("nothing", "?"));
		// ...but a value of the wrong type still throws
		assertThrows(JsonException.class, () -> o.getInt("name", -1));
	}

	public void testAsConversions() {
		JsonObject o = JsonObject.parse("""
			{ "int": 42, "dec": 12.7, "sint": "42", "sdec": "12.7", "text": "abc",
			  "t": true, "f": false, "szero": "0", "sfalse": "FALSE", "empty": "",
			  "nothing": null, "obj": {} }
			""");

		// Numbers from numbers, numeric strings and booleans
		assertEquals(42, o.asInt("int"));
		assertEquals(12, o.asInt("dec"));          // truncated
		assertEquals(42, o.asInt("sint"));
		assertEquals(12, o.asInt("sdec"));         // parsed as a number, then truncated
		assertEquals(12.7, o.asDouble("sdec"), 0);
		assertEquals(12.7, o.asNumber("sdec"));    // parsed as a JSON number
		assertEquals(1, o.asInt("t"));
		assertEquals(0, o.asInt("f"));
		assertEquals(0, o.asInt("text"));
		assertEquals(-1, o.asInt("text", -1));
		assertEquals(0, o.asInt("nothing"));
		assertEquals(0, o.asInt("missing"));
		assertEquals(0, o.asInt("obj"));
		assertNull(o.asNumber("text"));

		// Booleans: 0, NaN, "", "0" and "false" (any case) are false
		assertTrue(o.asBoolean("int"));
		assertTrue(o.asBoolean("text"));
		assertFalse(o.asBoolean("szero"));
		assertFalse(o.asBoolean("sfalse"));
		assertFalse(o.asBoolean("empty"));
		assertFalse(o.asBoolean("nothing"));
		assertTrue(o.asBoolean("missing", true));
		assertTrue(o.asBoolean("obj", true));      // not a primitive: the default

		// Strings
		assertEquals("42", o.asString("int"));
		assertEquals("12.7", o.asString("dec"));
		assertEquals("true", o.asString("t"));
		assertEquals("", o.asString("nothing"));
		assertEquals("", o.asString("obj"));       // containers are not converted
		assertEquals("n/a", o.asString("missing", "n/a"));

		assertEquals(BigDecimal.ONE, o.asBigDecimal("t"));
		assertEquals(new BigInteger("42"), o.asBigInteger("sint"));
		assertEquals(BigDecimal.ZERO, o.asBigDecimal("text"));
		assertEquals(42, JsonUtil.asInt("42"));
	}

	public void testDateTime() {
		JsonObject o = JsonObject.create()
			.put("day", LocalDate.of(2026, 9, 26))
			.put("at", OffsetDateTime.of(2026, 9, 26, 14, 30, 0, 0, ZoneOffset.ofHours(2)));

		// Stored as ISO-8601 strings
		assertEquals("2026-09-26", o.get("day"));
		assertEquals("2026-09-26T14:30:00+02:00", o.get("at"));

		assertEquals(LocalDate.of(2026, 9, 26), o.getLocalDate("day"));
		assertEquals(14, o.getOffsetDateTime("at").getHour());
		assertEquals(LocalDate.of(2000, 1, 1), o.getLocalDate("missing", LocalDate.of(2000, 1, 1)));
		assertThrows(JsonException.class, () -> o.getLocalDate("at"));   // not an ISO local date
	}

	public void testGetOrCreate() {
		JsonObject config = JsonObject.create();
		config.getOrCreateObject("server").put("port", 8080);
		config.getOrCreateObject("server").put("host", "localhost");   // the same object
		config.getOrCreateArray("users").add("ada");
		assertEquals("{\"server\":{\"port\":8080,\"host\":\"localhost\"},\"users\":[\"ada\"]}", config.stringify());

		// The consumer only runs when the object is created
		config.getOrCreateObject("limits", (JsonObject l) -> l.put("max", 10));
		config.getOrCreateObject("limits", (JsonObject l) -> l.put("max", 99));
		assertEquals(10, config.getObject("limits").getInt("max"));
	}

	public void testArrayIndexes() {
		JsonArray a = JsonArray.of("a", "b", "c", "d");

		// The methods follow the java.util.List contract: no negative index...
		assertThrows(IndexOutOfBoundsException.class, () -> a.getString(-1));
		assertFalse(a.has(-1));
		// ...the at*() methods count a negative index from the end
		assertEquals("d", a.atString(-1));
		assertEquals("c", a.atString(-2));
		assertTrue(a.hasAt(-4));
		assertFalse(a.hasAt(4));
		assertFalse(a.hasAt(-5));

		a.setAt(-1, "D");                  // set(size()-1, "D")
		a.removeAt(-2);                    // removes "c"
		a.addAt(-1, "x");                  // inserts before the last item
		assertEquals("[\"a\",\"b\",\"x\",\"D\"]", a.stringify());

		assertEquals("z", a.getString(10, "z"));    // out of range: the default
		assertEquals("a", a.firstValue());
		assertEquals("D", a.lastValue());
		assertEquals("none", JsonArray.create().firstValueOrDefault("none"));
		assertThrows(JsonException.class, () -> JsonArray.create().firstValue());
	}

	public void testHas() {
		JsonObject o = JsonObject.parse("{\"a\":null}");
		assertTrue(o.has("a"));          // present, even with a null value
		assertFalse(o.has("b"));
		assertTrue(o.isNull("a"));
		assertTrue(o.isNull("b"));       // a missing key reads as null
	}

	public void testNumberEquality() {
		JsonArray a1 = JsonArray.of(1, 2.5);
		JsonArray a2 = JsonArray.of(1L, new BigDecimal("2.50"));
		assertEquals(a1, a2);
		assertEquals(a1.hashCode(), a2.hashCode());

		JsonObject o1 = JsonObject.of("n", 1, "big", new BigInteger("10"));
		JsonObject o2 = JsonObject.of("big", 10.0, "n", 1.0);   // key order does not matter
		assertEquals(o1, o2);
		assertEquals(o1.hashCode(), o2.hashCode());

		Set<Object> set = new HashSet<>();
		set.add(JsonArray.of(1));
		assertTrue(set.contains(JsonArray.of(1.0)));

		assertTrue(JsonUtil.eq(1, 1.0));
		assertFalse(JsonUtil.eq(1, "1"));
		assertFalse(Integer.valueOf(1).equals(1.0));   // plain Java values keep Java semantics
	}

	public void testCompare() {
		assertTrue(JsonUtil.compare(1, 2.5) < 0);
		assertTrue(JsonUtil.compare("b", "a") > 0);
		assertTrue(JsonUtil.compare(false, true) < 0);
		assertTrue(JsonUtil.compare(null, 0) < 0);       // null first
		assertEquals(0, JsonUtil.compare(JsonArray.of(1, 2), JsonArray.of(1L, 2.0)));
		assertTrue(JsonUtil.compare(JsonArray.of(1), JsonArray.of(1, 2)) < 0);    // then by length

		// Across types: null < array < object < string < number < boolean
		JsonArray mixed = JsonArray.of(true, 1, "a", JsonObject.create(), JsonArray.create(), null);
		assertEquals("[null,[],{},\"a\",1,true]", mixed.sorted().stringify());
	}

	public void testClone() {
		JsonObject o = JsonObject.parse("{\"user\":{\"name\":\"Ada\"}}");
		JsonObject shallow = o.clone();
		JsonObject deep = o.deepClone();

		o.getObject("user").put("name", "Grace");
		assertEquals("Grace", shallow.getObject("user").getString("name"));   // shares the children
		assertEquals("Ada", deep.getObject("user").getString("name"));        // full copy

		JsonObject withDate = JsonObject.create();
		withDate.putValue("d", new java.util.Date());
		assertThrows(JsonException.class, () -> withDate.deepClone());
	}
}
