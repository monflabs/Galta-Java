package doc_examples.yaml;

import java.io.StringReader;
import java.math.BigInteger;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.yaml.SnakeYaml;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/Yaml.md
 */
public class YamlExamples extends ProjectTestCase {

	public void testParse() throws Exception {
		Object value = SnakeYaml.parse("""
				server:
				  host: example.com
				  ports: [80, 443]
				  tls: true
				owner: null
				""");
		JsonObject o = (JsonObject)value;                        // a regular GaltaJSON object
		assertEquals("example.com", o.getObject("server").getString("host"));
		assertEquals(443, o.getObject("server").getArray("ports").getInt(1));
		assertTrue(o.getObject("server").getBoolean("tls"));
		assertTrue(o.containsKey("owner"));
		assertNull(o.get("owner"));

		// Readers and input streams are accepted as well
		assertEquals(o, SnakeYaml.parse(new StringReader("{ server: { host: example.com, ports: [80, 443], tls: true }, owner: null }")));
	}

	public void testStringify() throws Exception {
		JsonObject o = JsonObject.parse("""
				{ "name": "Ada", "age": 36, "tags": ["math", "code"],
				  "address": { "city": "London" }, "zip": "75001", "notes": "line 1\\nline 2" }
				""");
		assertEquals("""
				name: Ada
				age: 36
				tags:
				- math
				- code
				address:
				  city: London
				zip: '75001'
				notes: |-
				  line 1
				  line 2
				""", SnakeYaml.stringify(o));
		assertEquals(o, SnakeYaml.parse(SnakeYaml.stringify(o)));   // round trip
	}

	public void testNonStringKeys() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse("""
				1: one
				true: yes
				2.5: float
				null: nil
				""");
		assertEquals("one", o.get("1"));
		assertEquals("yes", o.get("true"));
		assertEquals("float", o.get("2.5"));
		assertEquals("nil", o.get("null"));
	}

	public void testScalars() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse("""
				int: 12
				long: 12345678901
				big: 123456789012345678901234567890
				float: 1.5
				exp: 1e3
				bool: true
				nothing: null
				tilde: ~
				capital: True
				yes: yes
				hex: 0x1F
				date: 2024-01-15
				quoted: '012'
				""");
		assertEquals(Integer.valueOf(12), o.get("int"));
		assertEquals(Long.valueOf(12345678901L), o.get("long"));
		assertEquals(new BigInteger("123456789012345678901234567890"), o.get("big"));
		assertEquals(Double.valueOf(1.5), o.get("float"));
		assertEquals(Double.valueOf(1000), o.get("exp"));
		// JSON has no infinity or NaN
		try {
			SnakeYaml.parse("inf: .inf");
			fail();
		} catch(org.monflabs.json.JsonException e) {
			// The YAML value Infinity cannot be represented in JSON
		}
		assertEquals(Boolean.TRUE, o.get("bool"));
		assertNull(o.get("nothing"));
		// Only the JSON forms are resolved: everything else is a string
		assertEquals("~", o.get("tilde"));
		assertEquals("True", o.get("capital"));
		assertEquals("yes", o.get("yes"));
		assertEquals("0x1F", o.get("hex"));
		assertEquals("2024-01-15", o.get("date"));
		assertEquals("012", o.get("quoted"));
	}

	public void testDocumentFeatures() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse("""
				text: |
				  line 1
				  line 2
				folded: >
				  a
				  b
				base: &base { x: 1 }
				copy: *base
				merged:
				  <<: *base
				  y: 2
				""");
		assertEquals("line 1\nline 2\n", o.get("text"));
		assertEquals("a b\n", o.get("folded"));
		assertSame(o.get("base"), o.get("copy"));                 // an alias is the same instance
		assertEquals(JsonObject.of("<<", JsonObject.of("x", 1), "y", 2), o.get("merged"));   // no merge keys

		// Any root value
		assertEquals(JsonArray.of("a", "b"), SnakeYaml.parse("- a\n- b\n"));
		assertEquals("hello", SnakeYaml.parse("hello"));
		assertNull(SnakeYaml.parse(""));

		// One document only
		try {
			SnakeYaml.parse("a: 1\n---\nb: 2\n");
			fail();
		} catch(RuntimeException e) {
			// expected a single document in the stream
		}

		// A set is an array; values JSON cannot hold are rejected
		assertEquals(JsonArray.of("a", "b"), SnakeYaml.parse("!!set { a, b }"));
		try {
			SnakeYaml.parse("!!binary aGVsbG8=");
			fail();
		} catch(org.monflabs.json.JsonException e) {
			// a byte[] is not a JSON value
		}
	}

	public void testDumpSettings() throws Exception {
		org.snakeyaml.engine.v2.api.DumpSettings flow = org.snakeyaml.engine.v2.api.DumpSettings.builder()
				.setDefaultFlowStyle(org.snakeyaml.engine.v2.common.FlowStyle.FLOW)
				.build();
		assertEquals("{a: 1, b: [x, y]}\n", SnakeYaml.stringify(JsonObject.of("a", 1, "b", JsonArray.of("x", "y")), flow));
	}
}
