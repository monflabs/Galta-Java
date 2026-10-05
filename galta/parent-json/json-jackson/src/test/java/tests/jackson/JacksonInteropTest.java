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
package tests.jackson;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jackson.GaltaJsonModule;
import org.monflabs.json.java.JavaJsonFactory;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import tests.ProjectTestCase;

public class JacksonInteropTest extends ProjectTestCase {

	private final ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());

	// The same value, with the same number classes, at every level
	private static void assertSameValue(String path, Object expected, Object actual) {
		if(expected instanceof JsonObject eo) {
			assertTrue(path, actual instanceof JsonObject);
			JsonObject ao = (JsonObject)actual;
			assertEquals(path, List.copyOf(eo.keySet()), List.copyOf(ao.keySet()));
			for(Map.Entry<String,Object> e: eo.entrySet()) {
				assertSameValue(path+"."+e.getKey(), e.getValue(), ao.get(e.getKey()));
			}
		} else if(expected instanceof JsonArray ea) {
			assertTrue(path, actual instanceof JsonArray);
			JsonArray aa = (JsonArray)actual;
			assertEquals(path, ea.size(), aa.size());
			for(int i=0; i<ea.size(); i++) {
				assertSameValue(path+"["+i+"]", ea.get(i), aa.get(i));
			}
		} else {
			assertEquals(path, expected==null ? null : expected.getClass(), actual==null ? null : actual.getClass());
			assertEquals(path, expected, actual);
		}
	}

	private static String randomNumber(Random r) {
		switch(r.nextInt(7)) {
			case 0: return Integer.toString(r.nextInt());
			case 1: return Long.toString(r.nextLong());
			case 2: return "123456789012345678901234567890";
			case 3: return Double.toString(r.nextDouble()*Math.pow(10, r.nextInt(40)-20));
			case 4: return "0.1000000000000000055511151231257827";
			case 5: return r.nextInt(1000)+"."+r.nextInt(1000)+"e"+(r.nextInt(80)-40);
			default: return "-0.0";
		}
	}
	private static void randomValue(Random r, StringBuilder b, int depth) {
		switch(depth>4 ? r.nextInt(4) : r.nextInt(6)) {
			case 0: b.append(randomNumber(r)); break;
			case 1: b.append('"').append("s\\u00e9\\n\\\"x").append(r.nextInt(100)).append('"'); break;
			case 2: b.append(r.nextBoolean()); break;
			case 3: b.append("null"); break;
			case 4: {
				b.append('{');
				int n = r.nextInt(5);
				for(int i=0; i<n; i++) {
					if(i>0) b.append(',');
					b.append("\"k").append(i).append("\":");
					randomValue(r, b, depth+1);
				}
				b.append('}');
				break;
			}
			default: {
				b.append('[');
				int n = r.nextInt(5);
				for(int i=0; i<n; i++) {
					if(i>0) b.append(',');
					randomValue(r, b, depth+1);
				}
				b.append(']');
			}
		}
	}

	// Jackson reading into Galta containers gives what Galta's own parser gives
	public void testReadMatchesGaltaParser() throws Exception {
		Random r = new Random(31);
		for(int i=0; i<5000; i++) {
			StringBuilder b = new StringBuilder("{\"root\":");
			randomValue(r, b, 0);
			String text = b.append('}').toString();
			Object galta = JsonFactory.get().parse(text);
			JsonObject jackson = mapper.readValue(text, JsonObject.class);
			assertSameValue(text, galta, jackson);
			// And the containers are the ones of the factory
			assertEquals(text, galta.getClass(), jackson.getClass());
		}
	}

	public void testNumbersFollowTheFactory() throws Exception {
		JsonArray a = mapper.readValue("[1, 3000000000, 123456789012345678901234567890, 1.5, 0.1000000000000000055511151231257827, -0.0]", JsonArray.class);
		assertEquals(Integer.class, a.get(0).getClass());
		assertEquals(Long.class, a.get(1).getClass());
		assertEquals(BigInteger.class, a.get(2).getClass());
		assertEquals(Double.class, a.get(3).getClass());
		assertEquals(BigDecimal.class, a.get(4).getClass());   // a double can't hold it
		assertEquals(-0.0, a.get(5));
	}

	public void testWriteAndReadBack() throws Exception {
		Random r = new Random(37);
		for(int i=0; i<2000; i++) {
			StringBuilder b = new StringBuilder("{\"root\":");
			randomValue(r, b, 0);
			JsonObject o = (JsonObject)JsonFactory.get().parse(b.append('}').toString());
			String written = mapper.writeValueAsString(o);
			assertSameValue(written, o, JsonFactory.get().parse(written));
		}
	}

	public void testSpecialValuesWhenWriting() throws Exception {
		JsonObject o = JsonObject.create().put("nan", Double.NaN).put("inf", Double.POSITIVE_INFINITY)
				.putValue("date", new java.util.Date(0)).putValue("bean", new Point(1, 2));
		assertEquals("{\"nan\":null,\"inf\":null,\"date\":\"1970-01-01T00:00:00Z\",\"bean\":{\"x\":1,\"y\":2}}",
				mapper.writeValueAsString(o));
	}

	public void testCycleIsAnError() {
		JsonArray a = JsonArray.create();
		a.add(1);
		a.addValue(a);
		JsonMappingException e = assertThrows(JsonMappingException.class, () -> mapper.writeValueAsString(a));
		assertTrue(e.getMessage(), e.getMessage().contains("Circular reference"));
		// The same container twice is not a cycle
		JsonObject shared = JsonObject.create().put("v", 1);
		JsonArray twice = JsonArray.create().add(shared).add(shared);
		assertEquals("[{\"v\":1},{\"v\":1}]", writeQuietly(twice));
	}
	private String writeQuietly(Object o) {
		try {
			return mapper.writeValueAsString(o);
		} catch(Exception e) {
			throw new AssertionError(e);
		}
	}

	public record Point(int x, int y) {}
	public static class Order {
		public String id;
		public List<Point> points;
		public Map<String,Object> extra;
		public JsonObject data;    // a Galta value inside a Java object
		public BigDecimal total;
	}

	public void testJavaObjectsBothWays() throws Exception {
		Order order = new Order();
		order.id = "A1";
		order.points = List.of(new Point(1, 2), new Point(3, 4));
		order.extra = Map.of("n", 1);
		order.data = JsonObject.create().put("tags", JsonArray.of("a", "b"));
		order.total = new BigDecimal("10.50");
		JsonObject o = mapper.convertValue(order, JsonObject.class);
		assertEquals("A1", o.getString("id"));
		assertEquals(3, o.getArray("points").getObject(1).getInt("x"));
		assertEquals("b", o.getObject("data").getArray("tags").getString(1));
		assertEquals(new BigDecimal("10.50"), o.get("total"));     // kept as a BigDecimal
		assertTrue(o.getArray("points") instanceof JsonArray);
		Order back = mapper.convertValue(o, Order.class);
		assertEquals("A1", back.id);
		assertEquals(new Point(3, 4), back.points.get(1));
		assertEquals(JsonArray.of("a", "b"), back.data.getArray("tags"));
		assertEquals(new BigDecimal("10.50"), back.total);
		// A JsonObject field read from a text is a Galta object, its content too
		Order parsed = mapper.readValue("{\"id\":\"B\",\"data\":{\"x\":[{\"y\":1}]}}", Order.class);
		assertTrue(parsed.data.getArray("x").get(0) instanceof JsonObject);
	}

	public void testJsonNodeBothWays() throws Exception {
		JsonObject o = (JsonObject)JsonFactory.get().parse("{\"a\":[1,2.5,\"x\",null,true],\"b\":{\"c\":12345678901}}");
		JsonNode node = mapper.valueToTree(o);
		assertEquals(2.5, node.get("a").get(1).asDouble());
		assertEquals(12345678901L, node.get("b").get("c").asLong());
		JsonObject back = mapper.convertValue(node, JsonObject.class);
		assertSameValue("node", o, back);
	}

	public void testTypeMismatch() {
		assertThrows(MismatchedInputException.class, () -> mapper.readValue("[1]", JsonObject.class));
		assertThrows(MismatchedInputException.class, () -> mapper.readValue("{}", JsonArray.class));
		assertThrows(MismatchedInputException.class, () -> mapper.readValue("1", JsonContainer.class));
	}

	private static final class MarkerFactory extends JavaJsonFactory {
		@Override
		public INTEGER defaultInteger() {
			return INTEGER.LONG;
		}
		@Override
		public Object exportValue(Object value) {
			return "hidden".equals(value) ? NO_VALUE : value;
		}
		@Override
		public JsonObject createObject() {
			MarkerFactory f = this;
			return new org.monflabs.json.java.JsonObjectAsLinkedMap() {
				private static final long serialVersionUID = 1L;
				@Override
				public JavaJsonFactory factory() {
					return f;
				}
			};
		}
		@Override
		public JsonArray createArray() {
			MarkerFactory f = this;
			return new org.monflabs.json.java.JsonArrayAsArrayList() {
				private static final long serialVersionUID = 1L;
				@Override
				public JavaJsonFactory factory() {
					return f;
				}
			};
		}
	}

	public void testCustomFactory() throws Exception {
		// A factory giving Long integers, and leaving a marker value out of the output. When
		// a container is written, its own factory decides (factory()): the containers of
		// this factory report it
		JsonFactory longs = new MarkerFactory();
		ObjectMapper m = new ObjectMapper().registerModule(new GaltaJsonModule(longs));
		JsonArray a = m.readValue("[1, {\"k\": 2}]", JsonArray.class);
		assertEquals(Long.class, a.get(0).getClass());
		assertEquals(Long.class, a.getObject(1).get("k").getClass());
		JsonObject o = longs.createObject().put("shown", 1).put("secret", "hidden");
		JsonArray l = longs.createArray().add("hidden").add(2);
		assertEquals("{\"shown\":1}", m.writeValueAsString(o));
		assertEquals("[null,2]", m.writeValueAsString(l));
	}
}
