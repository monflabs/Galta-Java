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
package doc_examples.jackson;

import java.math.BigDecimal;
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jackson.GaltaJsonModule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tests.ProjectTestCase;

/**
 * The samples of docs/GaltaJSON/Modules/Jackson.md.
 */
public class JacksonExamples extends ProjectTestCase {

	public void testReadAndWrite() throws Exception {
		ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());

		JsonObject o = mapper.readValue("{\"name\":\"Ann\",\"tags\":[\"a\",\"b\"],\"score\":1.5}", JsonObject.class);
		assertEquals("b", o.getArray("tags").getString(1));          // regular Galta values
		o.put("active", true);
		assertEquals("{\"name\":\"Ann\",\"tags\":[\"a\",\"b\"],\"score\":1.5,\"active\":true}", mapper.writeValueAsString(o));
	}

	public record Line(String product, int quantity) {}
	public static class Order {
		public String id;
		public List<Line> lines;
		public BigDecimal total;
	}

	public void testJavaObjects() throws Exception {
		ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());

		Order order = new Order();
		order.id = "A1";
		order.lines = List.of(new Line("pen", 3));
		order.total = new BigDecimal("4.50");

		// A Java object as Galta values, with Jackson's mapping rules and annotations
		JsonObject o = mapper.convertValue(order, JsonObject.class);
		assertEquals("pen", o.getArray("lines").getObject(0).getString("product"));
		assertEquals(new BigDecimal("4.50"), o.get("total"));

		// And back
		o.getArray("lines").getObject(0).put("quantity", 5);
		Order changed = mapper.convertValue(o, Order.class);
		assertEquals(5, changed.lines.get(0).quantity());
	}

	public void testJsonNode() throws Exception {
		ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());

		JsonArray a = JsonArray.of(1, "x", JsonObject.of("k", true));
		JsonNode node = mapper.valueToTree(a);                  // Galta -> Jackson tree
		assertTrue(node.get(2).get("k").asBoolean());
		JsonArray back = mapper.convertValue(node, JsonArray.class);   // Jackson tree -> Galta
		assertEquals(a, back);
	}

	public static class Event {
		public String type;
		public JsonObject payload;   // any JSON content, as Galta values
	}

	public void testGaltaValuesInJavaObjects() throws Exception {
		ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());

		Event e = mapper.readValue("{\"type\":\"login\",\"payload\":{\"user\":\"ann\",\"roles\":[\"admin\"]}}", Event.class);
		assertEquals("admin", e.payload.getArray("roles").getString(0));
	}
}
