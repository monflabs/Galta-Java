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
package tests.yaml;

import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.yaml.SnakeYaml;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

import tests.ProjectTestCase;

/**
 * YAML values that have no direct JSON equivalent.
 */
public class SnakeYamlValuesTest extends ProjectTestCase {

	public void testSetsBecomeArrays() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse("s: !!set {b: null, a: null, c: null}\nn:\n  inner: !!set {x: null}\n");
		Object s = o.get("s");
		assertTrue(s instanceof JsonArray);
		JsonArray a = (JsonArray)s;
		assertEquals(3, a.size());
		assertEquals("b", a.get(0));
		assertEquals("a", a.get(1));
		assertEquals("c", a.get(2));
		assertEquals("[\"x\"]", JsonFactory.get().stringify(((JsonObject)o.get("n")).get("inner")));
		// Valid JSON all the way
		String json = o.stringify(true);
		assertEquals(o, JsonObject.parse(json));
		
		// A top level set
		Object top = SnakeYaml.parse("!!set {1: null, 2: null}\n");
		assertTrue(top instanceof JsonArray);
		assertEquals(2, ((JsonArray)top).size());
	}
	
	public void testNaNAndInfinityAreRejected() throws Exception {
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: .nan\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: .inf\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: -.inf\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("- [1, .inf]\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse(".nan"));
		// Finite floats are fine
		JsonObject o = (JsonObject)SnakeYaml.parse("a: 1.5\nb: -2e3\n");
		assertEquals(1.5, ((Number)o.get("a")).doubleValue());
		assertEquals(-2000.0, ((Number)o.get("b")).doubleValue());
	}
	
	public void testRecursiveAliasIsRejected() throws Exception {
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: &x\n  self: *x\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("&x [1, *x]\n"));
	}
	
	public void testSharedAliasIsAccepted() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse("base: &b {k: v}\nx: *b\ny: *b\nl: &l [1,2]\nm: *l\n");
		assertEquals("v", ((JsonObject)o.get("x")).get("k"));
		assertEquals("v", ((JsonObject)o.get("y")).get("k"));
		assertEquals(2, ((JsonArray)o.get("m")).size());
		// Stringifies without a false circular reference error
		assertEquals(o, JsonObject.parse(o.stringify(false)));
	}
	
	public void testBinaryIsRejected() throws Exception {
		assertThrows(JsonException.class, () -> SnakeYaml.parse("b: !!binary aGVsbG8=\n"));
	}
	
	public void testBigNumbers() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse("i: 123456789012345678901234567890\nl: 5000000000\n");
		Object i = o.get("i");
		assertTrue(i.getClass().getName(), i instanceof BigInteger);
		assertEquals(new BigInteger("123456789012345678901234567890"), i);
		assertEquals(5000000000L, ((Number)o.get("l")).longValue());
		// Round trip through YAML text
		JsonObject back = (JsonObject)SnakeYaml.parse(SnakeYaml.stringify(o));
		assertEquals(new BigInteger("123456789012345678901234567890"), back.get("i"));
		
		JsonObject d = JsonObject.create();
		d.put("d", new BigDecimal("1.25"));
		JsonObject dback = (JsonObject)SnakeYaml.parse(SnakeYaml.stringify(d));
		assertEquals(1.25, ((Number)dback.get("d")).doubleValue());
	}
	
	public void testReaderAndInputStream() throws Exception {
		String yaml = "a: 1\ns: !!set {x: null}\nt: café\n";
		for(Object o: new Object[] {
				SnakeYaml.parse(new StringReader(yaml)),
				SnakeYaml.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))),
				SnakeYaml.parse(JsonFactory.get(), yaml) }) {
			JsonObject jo = (JsonObject)o;
			assertEquals(1, ((Number)jo.get("a")).intValue());
			assertTrue(jo.get("s") instanceof JsonArray);
			assertEquals("café", jo.get("t"));
		}
		assertThrows(JsonException.class, () -> SnakeYaml.parse(new StringReader("a: .nan")));
		assertThrows(JsonException.class, () -> SnakeYaml.parse(new ByteArrayInputStream("a: .nan".getBytes(StandardCharsets.UTF_8))));
	}
	
	public void testCustomDumpSettings() throws Exception {
		JsonObject o = JsonObject.parse("{\"a\":[1,2],\"b\":{\"c\":\"d\"}}");
		String flow = SnakeYaml.stringify(o, DumpSettings.builder().setDefaultFlowStyle(FlowStyle.FLOW).build());
		assertEquals("{a: [1, 2], b: {c: d}}", flow.trim());
		assertEquals(o, SnakeYaml.parse(flow));
		String block = SnakeYaml.stringify(o);
		assertTrue(block, block.contains("\n"));
		assertEquals(o, SnakeYaml.parse(block));
	}
}
