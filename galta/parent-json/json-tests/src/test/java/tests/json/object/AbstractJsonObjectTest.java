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
package tests.json.object;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.java.AbstractJsonObject;
import org.monflabs.json.java.JavaJsonFactory;

import tests.ProjectTestCase;

/**
 * AbstractJsonObject, the base class of an adapter to another JSON library.
 */
public class AbstractJsonObjectTest extends ProjectTestCase {
	
	// A minimal adapter over a map
	private static class MapObject extends AbstractJsonObject {
		private final Map<String,Object> map = new LinkedHashMap<>();
		@Override
		public Object nativeGet(String key) {
			return map.get(key);
		}
		@Override
		public Object nativePut(String key, Object value) {
			return map.put(key, value);
		}
		@Override
		public Object nativeRemove(String key) {
			return map.remove(key);
		}
		@Override
		public Set<Map.Entry<String, Object>> entrySet() {
			return map.entrySet();
		}
		@Override
		public JsonFactory factory() {
			return JavaJsonFactory.instance;
		}
		@Override
		public Object toNativeJsonPrimitive() {
			return map;
		}
		@Override
		public MapObject clone() {
			MapObject o = new MapObject();
			o.map.putAll(map);
			return o;
		}
	}
	// With a direct key lookup
	private static class FastMapObject extends MapObject {
		int lookups;
		@Override
		public boolean nativeContainsKey(String key) {
			lookups++;
			return super.toNativeJsonPrimitive() instanceof Map<?,?> m && m.containsKey(key);
		}
	}

	public void testPutFollowsTheMapContract() {
		MapObject o = new MapObject();
		// Map.put(): the previous value, not the object itself
		assertNull(o.put("a", (Object)1));
		assertEquals(1, o.put("a", (Object)2));
		assertEquals(2, o.get("a"));
		// So do the defaults built on it
		assertEquals(2, o.putIfAbsent("a", 3));
		assertNull(o.putIfAbsent("b", 4));
		assertEquals(4, o.get("b"));
		assertThrows(NullPointerException.class, () -> o.put(null, (Object)1));
		assertEquals(2, o.remove("a"));
		assertFalse(o.containsKey("a"));
	}

	public void testEquality() {
		MapObject o = new MapObject();
		o.put("a", (Object)1);
		assertEquals(JsonObject.of("a", 1.0), o);
		assertEquals(o, JsonObject.of("a", 1));
		assertEquals(Map.of("a", 1), o);
		assertEquals(o, Map.of("a", 1));
		assertEquals(Map.of("a", 1).hashCode(), o.hashCode());
		assertEquals("{\"a\":1}", o.stringify());
	}

	public void testTypedPutsCheckTheKey() {
		MapObject o = new MapObject();
		assertThrows(NullPointerException.class, () -> o.put(null, 1));
		assertThrows(NullPointerException.class, () -> o.put(null, 1L));
		assertThrows(NullPointerException.class, () -> o.put(null, 1.5));
		assertThrows(NullPointerException.class, () -> o.put(null, true));
		assertThrows(NullPointerException.class, () -> o.put(null, "x"));
		assertThrows(NullPointerException.class, () -> o.put(null, (Number)1));
		assertThrows(NullPointerException.class, () -> o.put(null, JsonObject.create()));
		assertThrows(NullPointerException.class, () -> o.putValue(null, 1));
		assertThrows(NullPointerException.class, () -> o.putNull(null));
		assertEquals(0, o.size());
	}

	public void testNonStringKeys() {
		MapObject o = new MapObject();
		o.put("a", 1);
		// Map contract: not a ClassCastException
		assertNull(o.get(1));
		assertNull(o.remove(1));
		assertFalse(o.containsKey(1));
		assertNull(o.get(null));
		assertFalse(o.containsKey(null));
		assertEquals(1, o.size());
	}

	public void testContainsKey() {
		MapObject o = new MapObject();
		o.putNull("n");
		o.put("a", 1);
		assertTrue(o.containsKey("n"));
		assertTrue(o.has("n"));
		assertTrue(o.has("a"));
		assertFalse(o.has("x"));
		FastMapObject f = new FastMapObject();
		f.putNull("n");
		assertTrue(f.has("n"));
		assertFalse(f.containsKey("x"));
		assertEquals(2, f.lookups);
	}

	public void testCloneHasItsOwnStorage() {
		MapObject o = new MapObject();
		o.put("a", 1);
		JsonObject c = o.clone();
		c.put("b", 2);
		o.put("a", 5);
		assertFalse(o.containsKey("b"));
		assertEquals(1, c.get("a"));
	}

	public void testTypedGettersAndPuts() {
		MapObject o = new MapObject();
		o.put("bool", true).put("byte", (byte)1).put("short", (short)2).put("int", 3).put("long", 4L)
		 .put("float", 1.5f).put("double", 2.5).put("str", "s").put("num", (Number)new BigDecimal("1e23"))
		 .put("obj", JsonObject.of("x", 1)).put("arr", JsonArray.of(1)).put("bigint", 300).put("nan", Double.NaN)
		 .putNull("null");
		o.put("nb", (Boolean)null).put("ns", (String)null);
		assertTrue(o.getBoolean("bool"));
		assertEquals(1, o.getByte("byte"));
		assertEquals(2, o.getShort("short"));
		assertEquals(3, o.getInt("int"));
		assertEquals(4L, o.getLong("long"));
		assertEquals(1.5f, o.getFloat("float"));
		assertEquals(2.5, o.getDouble("double"));
		assertEquals("s", o.getString("str"));
		assertEquals(JsonObject.of("x", 1), o.getObject("obj"));
		assertEquals(JsonArray.of(1), o.getArray("arr"));
		assertEquals(Boolean.TRUE, o.getBooleanObject("bool"));
		assertEquals(Integer.valueOf(3), o.getIntObject("int"));
		assertEquals(Long.valueOf(4), o.getLongObject("long"));
		assertEquals(Byte.valueOf((byte)1), o.getByteObject("byte"));
		assertEquals(Short.valueOf((short)2), o.getShortObject("short"));
		assertEquals(Float.valueOf(1.5f), o.getFloatObject("float"));
		assertEquals(Double.valueOf(2.5), o.getDoubleObject("double"));
		assertEquals(new BigInteger("100000000000000000000000"), o.getBigInteger("num"));
		assertEquals(new BigDecimal("1e23"), o.getBigDecimal("num"));
		assertEquals(new BigDecimal("2.5"), o.getBigDecimal("double"));
		assertEquals(new BigDecimal("1e23"), o.getNumber("num"));
		// Saturated like the JSON objects
		assertEquals(Byte.MAX_VALUE, o.getByte("bigint"));
		assertEquals(Integer.MAX_VALUE, o.getInt("num"));
		assertThrows(JsonException.class, () -> o.getBigInteger("nan"));
		assertThrows(JsonException.class, () -> o.getInt("str"));
		assertThrows(JsonException.class, () -> o.getString("int"));
		// Defaults on null or missing
		assertTrue(o.isNull("null"));
		assertTrue(o.isNull("nb"));
		assertTrue(o.isNull("ns"));
		assertTrue(o.isBoolean("bool"));
		assertTrue(o.isNumber("int"));
		assertTrue(o.isString("str"));
		assertTrue(o.isObject("obj"));
		assertTrue(o.isArray("arr"));
		assertTrue(o.isContainer("arr"));
		assertEquals(9, o.getInt("null", 9));
		assertEquals(9L, o.getLong("missing", 9L));
		assertEquals("d", o.getString("missing", "d"));
		assertEquals(Integer.valueOf(7), o.getIntObject("missing", 7));
		assertFalse(o.getBoolean("missing", false));
		assertEquals(1.0, o.getDouble("missing", 1.0));
		assertEquals(BigInteger.ONE, o.getBigInteger("missing", BigInteger.ONE));
		assertEquals(3, o.getInt("int", 9));
		// Lambdas
		o.put("lo", (JsonObject x) -> x.put("k", 1));
		o.put("la", (JsonArray x) -> x.add(2));
		assertEquals(JsonObject.of("k", 1), o.getObject("lo"));
		assertEquals(JsonArray.of(2), o.getArray("la"));
	}
}
