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
package tests.json.regression;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.json.java.JavaJsonFactoryChecked;
import org.monflabs.json.java.JsonArrayAsArrayList;
import org.monflabs.json.java.JsonObjectAsLinkedMapChecked;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.util.JsonCollectors;

import tests.ProjectTestCase;

/**
 * Regression tests for the fixes of the core data model (containers, JsonUtil conversions).
 */
public class CoreModelFixesTest extends ProjectTestCase {

	//
	// getByte()/getShort() saturate like getInt()/getLong()
	//
	public void testByteShortClamp() {
		JsonObject o = JsonObject.of("a", 300, "b", -300, "c", 70000, "d", -70000L, "e", 1e10, "f", Double.NaN, "g", 12.9);
		assertEquals(Byte.MAX_VALUE, o.getByte("a"));
		assertEquals(Byte.MIN_VALUE, o.getByte("b"));
		assertEquals(Short.MAX_VALUE, o.getShort("c"));
		assertEquals(Short.MIN_VALUE, o.getShort("d"));
		assertEquals(Byte.MAX_VALUE, o.getByte("e"));
		assertEquals(Short.MAX_VALUE, o.getShort("e"));
		assertEquals(0, o.getByte("f"));
		assertEquals(12, o.getShort("g"));
		assertEquals(Byte.valueOf(Byte.MAX_VALUE), o.getByteObject("a"));
		assertEquals(Short.valueOf(Short.MIN_VALUE), o.getShortObject("d"));
		assertEquals(Byte.MAX_VALUE, o.getByte("a", (byte)0));
		assertEquals(Short.MAX_VALUE, o.getShort("c", (short)0));
		assertEquals(Byte.valueOf(Byte.MIN_VALUE), o.getByteObject("b", null));
		assertEquals(Short.valueOf(Short.MAX_VALUE), o.getShortObject("c", null));

		JsonArray a = JsonArray.of(300, -70000, BigInteger.TEN.pow(30));
		assertEquals(Byte.MAX_VALUE, a.getByte(0));
		assertEquals(Short.MIN_VALUE, a.getShort(1));
		assertEquals(Byte.MAX_VALUE, a.getByte(2));
		assertEquals(Byte.valueOf(Byte.MAX_VALUE), a.getByteObject(0));
		assertEquals(Short.valueOf(Short.MIN_VALUE), a.getShortObject(1));
		assertEquals(Byte.MAX_VALUE, a.getByte(0, (byte)0));
		assertEquals(Short.MIN_VALUE, a.getShort(1, (short)0));
		assertEquals(Byte.valueOf(Byte.MAX_VALUE), a.getByteObject(-1, null));
		assertEquals(Short.valueOf(Short.MIN_VALUE), a.getShortObject(-2, null));

		assertEquals(Byte.MAX_VALUE, JsonUtil.checkByte(300));
		assertEquals(Short.MIN_VALUE, JsonUtil.checkShort(-70000));
		assertEquals(Byte.MAX_VALUE, JsonUtil.clampToByte(Long.MAX_VALUE));
		assertEquals(Short.MIN_VALUE, JsonUtil.clampToShort(Double.NEGATIVE_INFINITY));
		// The factory's strict conversions (used by AbstractJsonObject) too
		assertEquals(Byte.MAX_VALUE, JavaJsonFactory.instance.asByte(300));
		assertEquals(Integer.MAX_VALUE, JavaJsonFactory.instance.asInt(Long.MAX_VALUE));
		assertEquals(Long.MAX_VALUE, JavaJsonFactory.instance.asLong(BigInteger.TEN.pow(30)));
	}

	//
	// A checked object checks the values set through every view
	//
	public void testCheckedMapViews() {
		JsonObjectAsLinkedMapChecked o = new JsonObjectAsLinkedMapChecked();
		o.put("a", 1);
		o.put("b", 2);
		Object bad = new Object();
		assertThrows(JsonException.class, () -> o.sequencedEntrySet().iterator().next().setValue(bad));
		assertThrows(JsonException.class, () -> o.sequencedEntrySet().reversed().iterator().next().setValue(bad));
		assertThrows(JsonException.class, () -> o.sequencedEntrySet().getFirst().setValue(bad));
		assertThrows(JsonException.class, () -> o.entrySet().iterator().next().setValue(bad));
		SequencedMap<String,Object> r = o.reversed();
		assertThrows(JsonException.class, () -> r.entrySet().iterator().next().setValue(bad));
		assertThrows(JsonException.class, () -> r.sequencedEntrySet().iterator().next().setValue(bad));
		assertThrows(JsonException.class, () -> r.replaceAll((k,v) -> bad));
		assertThrows(JsonException.class, () -> r.put("c", bad));
		assertThrows(JsonException.class, () -> r.putFirst("c", bad));
		assertThrows(JsonException.class, () -> r.compute("a", (k,v) -> bad));
		assertThrows(JsonException.class, () -> r.merge("a", 1, (v1,v2) -> bad));
		assertThrows(JsonException.class, () -> o.putFirst("c", bad));
		assertThrows(JsonException.class, () -> o.putLast("c", bad));
		// The compute*/replace*/putIfAbsent paths
		assertThrows(JsonException.class, () -> o.compute("a", (k,v) -> bad));
		assertThrows(JsonException.class, () -> o.computeIfPresent("a", (k,v) -> bad));
		assertThrows(JsonException.class, () -> o.computeIfAbsent("z", k -> bad));
		assertThrows(JsonException.class, () -> o.replace("a", bad));
		assertThrows(JsonException.class, () -> o.replace("a", 1, bad));
		assertThrows(JsonException.class, () -> o.replaceAll((k,v) -> bad));
		assertThrows(JsonException.class, () -> o.putIfAbsent("z", bad));
		assertThrows(JsonException.class, () -> o.merge("a", 5, (v1,v2) -> bad));
		assertThrows(JsonException.class, () -> o.putAll(Map.of("z", bad)));
		// Nothing was stored, and the valid writes still work through the views
		assertEquals(JsonObject.of("a", 1, "b", 2), o);
		r.entrySet().iterator().next().setValue("x");
		o.sequencedEntrySet().getFirst().setValue(true);
		o.compute("c", (k,v) -> 3);
		o.computeIfAbsent("d", k -> "d");
		o.replace("c", 4);
		assertEquals("{\"a\":true,\"b\":\"x\",\"c\":4,\"d\":\"d\"}", o.stringify());
		assertSame(o, o.reversed().reversed());
		assertEquals(List.of("d","c","b","a"), List.copyOf(r.keySet()));
	}

	//
	// find(int) with a negative index out of range
	//
	public void testFindNegativeIndexOutOfRange() {
		JsonArray a = JsonArray.parse("[1,2,[3,4,5,6,7]]");
		assertEquals(JsonArray.of(JsonArray.of(3,4,5,6,7)), a.find(-1, false));
		assertEquals(JsonArray.of(JsonArray.of(3,4,5,6,7), 7), a.find(-1));
		// -5 is out of range for the outer array (size 3), in range for the inner one
		assertEquals(JsonArray.of(3), a.find(-5));
		assertEquals(JsonArray.of(), a.find(-10));
		a.findAndSet(-5, "x");
		assertEquals(JsonArray.parse("[1,2,[\"x\",4,5,6,7]]"), a);
		a.findAndSet(-10, "y");
		assertEquals(JsonArray.parse("[1,2,[\"x\",4,5,6,7]]"), a);
	}

	//
	// JsonCollectors: reusable, no double copy
	//
	public void testCollectorsReusable() {
		Collector<Object,JsonArray,JsonArray> c = JsonCollectors.toJsonArray();
		JsonArray a1 = Stream.of(1, 2).collect(c);
		JsonArray a2 = Stream.of(3).collect(c);
		assertNotSame(a1, a2);
		assertEquals(JsonArray.of(1, 2), a1);
		assertEquals(JsonArray.of(3), a2);
		JsonArray p = IntStream.range(0, 1000).boxed().parallel().map(i -> (Object)i).collect(c);
		assertEquals(1000, p.size());
		assertEquals(999, p.getInt(999));
		assertTrue(c.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH));

		Collector<JsonValues,JsonValues,JsonValues> cv = JsonCollectors.toJsonArrayValues();
		JsonValues v1 = Stream.of(JsonValues.of(1), JsonValues.of(2)).collect(cv);
		JsonValues v2 = Stream.of(JsonValues.of(3)).collect(cv);
		assertEquals(JsonArray.of(1, 2), v1.arrayValue());
		assertEquals(JsonArray.of(3), v2.arrayValue());

		// The explicit target: appended to
		JsonArray target = JsonArray.of(0);
		Stream.of(1).collect(JsonCollectors.toJsonArray(target));
		assertEquals(JsonArray.of(0, 1), target);
	}

	//
	// getOrCreateObject()/getOrCreateArray() on a null value
	//
	public void testGetOrCreateOnNull() {
		JsonObject o = JsonObject.parse("{\"a\":null,\"b\":null,\"c\":5}");
		JsonObject a = o.getOrCreateObject("a");
		assertNotNull(a);
		assertSame(a, o.get("a"));
		JsonArray b = o.getOrCreateArray("b", x -> x.add(1));
		assertEquals(JsonArray.of(1), b);
		assertSame(b, o.get("b"));
		assertSame(a, o.getOrCreateObject("a"));
		assertThrows(JsonException.class, () -> o.getOrCreateObject("c"));
	}

	//
	// getBigInteger()/getBigDecimal(): one conversion rule, NaN/Infinity rejected
	//
	public void testBigConversions() {
		JsonObject o = JsonObject.of("a", 1e23, "b", 0.1, "c", 12.7f, "n", Double.NaN, "i", Double.POSITIVE_INFINITY, "f", Float.NEGATIVE_INFINITY);
		assertEquals(new BigInteger("100000000000000000000000"), o.getBigInteger("a"));
		assertEquals(0, new BigDecimal("1E+23").compareTo(o.getBigDecimal("a")));
		assertEquals(o.getBigDecimal("a").toBigInteger(), o.getBigInteger("a"));
		assertEquals(new BigDecimal("0.1"), o.getBigDecimal("b"));
		assertEquals(BigInteger.valueOf(12), o.getBigInteger("c"));
		for(String k: new String[] {"n","i","f"}) {
			assertThrows(k, JsonException.class, () -> o.getBigInteger(k));
			assertThrows(k, JsonException.class, () -> o.getBigDecimal(k));
			assertThrows(k, JsonException.class, () -> o.getBigInteger(k, BigInteger.ONE));
		}
		JsonArray a = JsonArray.of(1e23, Double.NaN);
		assertEquals(new BigInteger("100000000000000000000000"), a.getBigInteger(0));
		assertThrows(JsonException.class, () -> a.getBigDecimal(1));
		// as*() keep the saturation
		assertEquals(BigInteger.ZERO, o.asBigInteger("n"));
		assertEquals(BigInteger.valueOf(Long.MAX_VALUE), o.asBigInteger("i"));
		assertEquals(BigDecimal.valueOf(Double.MAX_VALUE), o.asBigDecimal("i"));
		assertEquals(new BigInteger("100000000000000000000000"), o.asBigInteger("a"));
	}

	//
	// as*() parse strings one way
	//
	public void testAsStringConversions() {
		// Hexadecimal: the same for every conversion
		assertEquals(16, JsonUtil.asInt("0x10"));
		assertEquals(16L, JsonUtil.asLong("0x10"));
		assertEquals(16.0, JsonUtil.asDouble("0x10"));
		assertEquals(BigInteger.valueOf(16), JsonUtil.asBigInteger("0x10"));
		assertEquals(0, BigDecimal.valueOf(16).compareTo(JsonUtil.asBigDecimal("0x10")));
		// Java float suffixes are not numbers
		assertEquals(-1.0, JsonUtil.asDouble("1f", -1));
		assertEquals(-1.0, JsonUtil.asDouble("1d", -1));
		assertEquals(-1, JsonUtil.asInt("1f", -1));
		// Trimmed everywhere
		assertEquals(12.5, JsonUtil.asDouble(" 12.5 "));
		assertEquals(new BigDecimal("12.5"), JsonUtil.asBigDecimal(" 12.5 "));
		assertEquals(BigInteger.valueOf(12), JsonUtil.asBigInteger(" 12 "));
		// Truncation, the same for int and BigInteger
		assertEquals(12, JsonUtil.asInt("12.7"));
		assertEquals(BigInteger.valueOf(12), JsonUtil.asBigInteger("12.7"));
		assertEquals(BigInteger.valueOf(-12), JsonUtil.asBigInteger("-12.7"));
		// Precision is kept
		assertEquals(new BigDecimal("0.12345678901234567890123"), JsonUtil.asBigDecimal("0.12345678901234567890123"));
		assertEquals(new BigInteger("123456789012345678901234567890"), JsonUtil.asBigInteger("123456789012345678901234567890"));
		// Not a number: the default
		assertEquals(7, JsonUtil.asInt("abc", 7));
		assertEquals(7.0, JsonUtil.asDouble("", 7));
		assertEquals(BigInteger.TEN, JsonUtil.asBigInteger("  ", BigInteger.TEN));
		assertNull(JsonUtil.asNumber("1_0"));
		// JsonObject.asNumber with a Number default
		JsonObject o = JsonObject.of("a", "x");
		assertEquals(2.5, o.asNumber("a", (Number)2.5));
		assertEquals(3, o.asNumber("a", 3));
	}

	//
	// asBoolean() of big numbers
	//
	public void testAsBooleanBigNumbers() {
		assertTrue(JsonUtil.asBoolean(new BigDecimal("1e-400")));
		assertTrue(JsonUtil.asBoolean(new BigDecimal("-1e-400")));
		assertFalse(JsonUtil.asBoolean(new BigDecimal("0.000")));
		assertTrue(JsonUtil.asBoolean(BigInteger.TEN.pow(400)));
		assertFalse(JsonUtil.asBoolean(BigInteger.ZERO));
		assertFalse(JsonUtil.asBoolean(0.0));
		assertFalse(JsonUtil.asBoolean(Double.NaN));
		// Documented: a string is only false when empty, "0" or "false"
		assertTrue(JsonUtil.asBoolean("0.0"));
		assertFalse(JsonUtil.asBoolean("0"));
		assertFalse(JsonUtil.asBoolean("FALSE"));
	}

	//
	// Typed parse shortcuts and of()
	//
	public void testTypedParseAndOf() {
		JsonException e = assertThrows(JsonException.class, () -> JsonObject.parse("[1]"));
		assertTrue(e.getMessage(), e.getMessage().contains("not an object but array"));
		e = assertThrows(JsonException.class, () -> JsonArray.parse("{}"));
		assertTrue(e.getMessage(), e.getMessage().contains("not an array but object"));
		e = assertThrows(JsonException.class, () -> JsonContainer.parse("1"));
		assertTrue(e.getMessage(), e.getMessage().contains("not an object or an array but number"));
		assertThrows(JsonException.class, () -> JsonObject.parse(new java.io.StringReader("\"x\"")));
		assertNull(JsonObject.parse("null"));
		assertEquals(JsonObject.of("a", 1), JsonObject.parse("{\"a\":1}"));

		e = assertThrows(JsonException.class, () -> JsonObject.of(1, 2));
		assertTrue(e.getMessage(), e.getMessage().contains("position 0"));
		e = assertThrows(JsonException.class, () -> JsonObject.of("a"));
		assertTrue(e.getMessage(), e.getMessage().contains("odd number"));
		assertThrows(JsonException.class, () -> JsonObject.of("a", 1, null, 2));
		assertEquals(0, JsonObject.of().size());
		assertTrue(JsonObject.of("a", null).containsKey("a"));
	}

	//
	// Error messages and out of range behaviour
	//
	public void testErrorMessagesAndRanges() {
		JsonObject o = JsonObject.of("s", "x", "n", 1);
		JsonException e = assertThrows(JsonException.class, () -> o.getInt("s"));
		assertTrue(e.getMessage(), e.getMessage().contains("key \"s\""));
		e = assertThrows(JsonException.class, () -> o.getString("missing"));
		assertTrue(e.getMessage(), e.getMessage().contains("key \"missing\""));
		e = assertThrows(JsonException.class, () -> o.getArray("n", null));
		assertTrue(e.getMessage(), e.getMessage().contains("key \"n\""));
		JsonArray a = JsonArray.of("x", 1);
		e = assertThrows(JsonException.class, () -> a.getBoolean(-1));
		assertTrue(e.getMessage(), e.getMessage().contains("index -1"));
		e = assertThrows(JsonException.class, () -> a.getObject(0));
		assertTrue(e.getMessage(), e.getMessage().contains("index 0"));
		// An index out of range is still an IndexOutOfBoundsException for the getters
		assertThrows(IndexOutOfBoundsException.class, () -> a.getInt(5));
		// but the is*() tests answer like for a missing key
		assertTrue(a.isNull(-5));
		assertTrue(a.isNull(2));
		assertFalse(a.isString(7));
		assertFalse(a.isNumber(-3));
		assertFalse(a.isContainer(10));
		assertTrue(o.isNull("missing"));
	}

	//
	// Cyclic containers
	//
	public void testCyclicContainers() {
		JsonArray a = JsonArray.of(1);
		a.add(a);
		assertThrows(JsonException.CircularReference.class, () -> a.hashCode());
		assertThrows(JsonException.CircularReference.class, () -> JsonUtil.hashCode(a));
		assertThrows(JsonException.CircularReference.class, () -> a.deepClone());
		JsonArray b = JsonArray.of(1);
		b.add(b);
		assertThrows(JsonException.CircularReference.class, () -> a.equals(b));
		assertTrue(a.equals(a));
		JsonObject o = JsonObject.create();
		o.put("self", o);
		assertThrows(JsonException.CircularReference.class, () -> o.hashCode());
		assertThrows(JsonException.CircularReference.class, () -> o.deepClone());
		JsonObject o2 = JsonObject.create();
		o2.put("self", o2);
		assertThrows(JsonException.CircularReference.class, () -> JsonUtil.eq(o, o2));
		// A deep, but acyclic, document still works (and shares no cycle state)
		JsonArray deep = JsonArray.create();
		JsonArray cur = deep;
		for(int i=0; i<1000; i++) {
			JsonArray n = JsonArray.of(i);
			cur.add(n);
			cur = n;
		}
		JsonArray copy = deep.deepClone();
		assertEquals(deep, copy);
		assertEquals(deep.hashCode(), copy.hashCode());
		// The same sub-container twice is not a cycle
		JsonObject shared = JsonObject.of("x", 1);
		JsonArray twice = JsonArray.of(shared, shared);
		assertEquals(twice, twice.deepClone());
	}

	//
	// deepClone() keeps the references, as clone() does
	//
	public void testDeepCloneKeepsReference() {
		JsonObject o = JsonObject.parse("{\"a\":{\"b\":[1]}}");
		o.setReference("#/root");
		o.getObject("a").setReference("#/a");
		o.getObject("a").getArray("b").setReference("#/b");
		assertEquals("#/root", o.clone().getReference());
		JsonObject c = o.deepClone();
		assertEquals("#/root", c.getReference());
		assertEquals("#/a", c.getObject("a").getReference());
		assertEquals("#/b", c.getObject("a").getArray("b").getReference());
		assertNotSame(o.getObject("a"), c.getObject("a"));
	}

	//
	// Checked factory: createArray(int)
	//
	public void testCheckedFactoryCapacity() {
		JsonArray a = JavaJsonFactoryChecked.instance.createArray(100);
		assertTrue(a.getClass().getName(), a.getClass().getSimpleName().equals("JsonArrayAsArrayListChecked"));
		assertThrows(JsonException.class, () -> a.add(new Object()));
		assertSame(JavaJsonFactoryChecked.instance, a.factory());
	}

	//
	// Coverage: JsonArrayAsArrayList get*Object(int)
	//
	public void testArrayGetObjectAccessors() {
		JsonArray a = JsonArray.of(true, 1, 2L, 1.5f, 2.5, (byte)3, (short)4, "s", null);
		assertEquals(Boolean.TRUE, a.getBooleanObject(0));
		assertEquals(Integer.valueOf(1), a.getIntObject(1));
		assertEquals(Long.valueOf(2), a.getLongObject(2));
		assertEquals(Long.valueOf(1), a.getLongObject(1));
		assertEquals(Float.valueOf(1.5f), a.getFloatObject(3));
		assertEquals(Double.valueOf(2.5), a.getDoubleObject(4));
		assertEquals(Double.valueOf(1.0), a.getDoubleObject(1));
		assertEquals(Integer.valueOf(2), a.getIntObject(4));
		assertEquals(Byte.valueOf((byte)3), a.getByteObject(5));
		assertEquals(Short.valueOf((short)4), a.getShortObject(6));
		assertEquals(Float.valueOf(2.5f), a.getFloatObject(4));
		assertNull(a.getIntObject(8, null));
		assertEquals(Integer.valueOf(9), a.getIntObject(8, 9));
		assertEquals(Long.valueOf(9), a.getLongObject(100, 9L));
		assertEquals(Double.valueOf(9), a.getDoubleObject(-100, 9.0));
		assertEquals(Boolean.FALSE, a.getBooleanObject(8, false));
		assertEquals(Float.valueOf(1.5f), a.getFloatObject(-6, null));
		assertThrows(JsonException.class, () -> a.getIntObject(7));
		assertThrows(JsonException.class, () -> a.getBooleanObject(1));
		assertThrows(JsonException.class, () -> a.getDoubleObject(8));
		assertTrue(a instanceof JsonArrayAsArrayList);
	}

	//
	// Coverage: JsonUtil.encodeString \\u path and toStringValue
	//
	public void testEncodeStringAndToStringValue() {
		String bs = String.valueOf((char)92);
		assertEquals("\"a" + bs + "u0001b\"", JsonUtil.encodeString("a\u0001b", '"'));
		assertEquals("\"" + bs + "u00E9" + bs + "u20AC\"", JsonUtil.encodeString("" + (char)0xE9 + (char)0x20AC, '"'));
		assertEquals("'it" + bs + "'s'", JsonUtil.encodeString("it's", '\''));
		assertEquals("\"it's\"", JsonUtil.encodeString("it's", '"'));
		assertEquals("a" + bs + "tb", JsonUtil.encodeString("a\tb", (char)0));
		assertNull(JsonUtil.encodeString(null, '"'));

		assertEquals("", JsonUtil.toStringValue(null));
		assertEquals("x", JsonUtil.toStringValue("x"));
		assertEquals("true", JsonUtil.toStringValue(true));
		assertEquals("1.5", JsonUtil.toStringValue(1.5));
		assertEquals("100", JsonUtil.toStringValue(100.0));
		assertEquals("[1,2]", JsonUtil.toStringValue(JsonArray.of(1, 2)));
		assertEquals("{\"a\":1}", JsonUtil.toStringValue(JsonObject.of("a", 1)));
		assertThrows(IllegalArgumentException.class, () -> JsonUtil.toStringValue(new Object()));
	}

	//
	// Coverage: JsonContainer defaults
	//
	public void testContainerDefaults() {
		JsonArray a = JsonArray.of(1, 2, 3);
		assertEquals(Integer.valueOf(3), a.lastValue());
		assertEquals(Integer.valueOf(1), a.firstValue());
		assertEquals("d", JsonArray.of().lastValueOrDefault("d"));
		assertThrows(JsonException.class, () -> JsonArray.of().lastValue());
		JsonObject o = JsonObject.of("a", 1, "b", "x");
		assertEquals("x", o.lastValue());
		AtomicInteger count = new AtomicInteger();
		JsonContainer c = o;
		assertSame(c, c.forEachValue(v -> count.addAndGet(v.isNumber() ? 10 : 1)));
		assertEquals(11, count.get());
		JsonContainer dc = ((JsonContainer)JsonObject.of("a", JsonArray.of(1))).deepClone();
		assertEquals(JsonObject.of("a", JsonArray.of(1)), dc);
		assertSame(JsonFactory.get().getClass(), dc.factory().getClass());
	}
}
