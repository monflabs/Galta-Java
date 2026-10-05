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
import org.monflabs.json.java.JavaJsonFactory;
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
	
	// The numbers follow the rules of the factory, as when it parses JSON: the same literal
	// gives the same type and value with both parsers
	private static final String[] NUMBERS = {
		"0", "-0", "12", "-12", "2147483647", "2147483648", "-2147483649", "12345678901",
		"9223372036854775807", "9223372036854775808", "123456789012345678901234567890",
		"1.5", "-1.5", "1e3", "1E3", "1e-7", "0.1", "0.0", "-0.0", "3.141592653589793",
		"3.14159265358979323846", "1e400", "1e-400", "123456789012345678901234567890.5"
	};
	private static JsonFactory[] numberFactories() {
		return new JsonFactory[] {
			JsonFactory.get(),
			new JavaJsonFactory() {
				@Override public INTEGER defaultInteger() { return INTEGER.LONG; }
				@Override public DECIMAL defaultDecimal() { return DECIMAL.BIGDEC; }
			},
			new JavaJsonFactory() {
				@Override public INTEGER defaultInteger() { return INTEGER.BIGINT; }
			},
			new JavaJsonFactory() {
				@Override public boolean useLongIntegers() { return false; }
				@Override public OVERFLOW_INTEGER overflowInteger() { return OVERFLOW_INTEGER.DOUBLE; }
				@Override public OVERFLOW_DECIMAL overflowDecimal() { return OVERFLOW_DECIMAL.DOUBLE; }
			},
		};
	}
	public void testNumbersFollowTheFactory() throws Exception {
		for(JsonFactory f: numberFactories()) {
			for(String n: NUMBERS) {
				Object json = ((JsonArray)f.parse("["+n+"]")).get(0);
				Object yaml = ((JsonArray)SnakeYaml.parse(f, "- "+n+"\n")).get(0);
				String what = n+" with "+f.defaultInteger()+"/"+f.defaultDecimal();
				assertEquals(what, json.getClass(), yaml.getClass());
				assertEquals(what, json, yaml);
			}
		}
	}
	public void testYamlNumberForms() throws Exception {
		JsonFactory exact = new JavaJsonFactory() {
			@Override public INTEGER defaultInteger() { return INTEGER.LONG; }
			@Override public DECIMAL defaultDecimal() { return DECIMAL.BIGDEC; }
		};
		JsonObject o = (JsonObject)SnakeYaml.parse(exact, "h: !!int 0x1F\no: !!int 0o17\nd: !!float .5\ne: !!float 1.\n");
		assertEquals(Long.valueOf(31), o.get("h"));
		assertEquals(Long.valueOf(15), o.get("o"));
		assertEquals(new BigDecimal(".5"), o.get("d"));
		assertEquals(new BigDecimal("1."), o.get("e"));
		// The infinities are still rejected
		assertThrows(JsonException.class, () -> SnakeYaml.parse(exact, "x: .inf\n"));
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
