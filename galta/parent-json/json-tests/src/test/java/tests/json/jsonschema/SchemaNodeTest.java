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
package tests.json.jsonschema;

import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.JDBCType;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonschema.PathSchemaFactory;
import org.monflabs.json.jsonschema.SchemaNode;
import org.monflabs.json.jsonschema.SchemaType;

import tests.ProjectTestCase;

/**
 * The core module's SchemaNode and schema factories.
 */
public class SchemaNodeTest extends ProjectTestCase {

	private static SchemaNode node(String json) {
		return new SchemaNode(JsonObject.parse(json));
	}

	public void testObjectAndArray() {
		assertTrue(node("{\"type\":\"object\"}").isObject());
		assertFalse(node("{\"type\":\"object\"}").isArray());
		assertTrue(node("{\"type\":\"array\"}").isArray());
		assertTrue(node("{\"type\":[\"array\",\"null\"]}").isArray());
		// No type: from the keywords
		assertTrue(node("{\"properties\":{}}").isObject());
		assertTrue(node("{\"additionalProperties\":false}").isObject());
		assertTrue(node("{\"items\":{}}").isArray());
		assertFalse(node("{}").isObject());
		assertFalse(node("{}").isArray());
		// Ambiguous: neither
		assertFalse(node("{\"type\":[\"string\",\"object\"]}").isObject());
		assertFalse(node("{\"type\":[\"string\",\"integer\"]}").isArray());
		assertFalse(node("{\"type\":\"string\",\"properties\":{}}").isObject());
	}

	public void testMalformedTypes() {
		// A non string type is no type, not a ClassCastException
		assertNull(node("{\"type\":[1]}").getMainType());
		assertNull(node("{\"type\":[null,{}]}").getMainType());
		assertNull(node("{\"type\":5}").getMainType());
		assertThrows(JsonException.class, () -> node("{\"type\":[1]}").schemaType());
		assertThrows(JsonException.class, () -> node("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"NOPE\"}}").jdbcType());
	}

	public void testJdbcTypes() {
		assertEquals(JDBCType.VARCHAR, node("{\"type\":\"string\"}").jdbcType());
		assertEquals(JDBCType.DATE, node("{\"type\":\"string\",\"format\":\"date\"}").jdbcType());
		assertEquals(JDBCType.TIME, node("{\"type\":\"string\",\"format\":\"time\"}").jdbcType());
		assertEquals(JDBCType.TIME_WITH_TIMEZONE, node("{\"type\":\"string\",\"format\":\"time\",\"mf-format-ex\":\"time-tz\"}").jdbcType());
		assertEquals(JDBCType.TIMESTAMP, node("{\"type\":\"string\",\"format\":\"date-time\"}").jdbcType());
		assertEquals(JDBCType.TIMESTAMP_WITH_TIMEZONE, node("{\"type\":\"string\",\"format\":\"date-time\",\"mf-format-ex\":\"date-time-tz\"}").jdbcType());
		assertEquals(JDBCType.INTEGER, node("{\"type\":\"integer\"}").jdbcType());
		assertEquals(JDBCType.BIGINT, node("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"BIGINT\"}}").jdbcType());
		// A JDBC type that doesn't match the JSON type is ignored
		assertEquals(JDBCType.INTEGER, node("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"VARCHAR\"}}").jdbcType());
		assertEquals(JDBCType.DOUBLE, node("{\"type\":\"number\"}").jdbcType());
		assertEquals(JDBCType.DECIMAL, node("{\"type\":\"number\",\"mf-jdbc\":{\"jdbc-type\":\"DECIMAL\"}}").jdbcType());
		assertEquals(JDBCType.BOOLEAN, node("{\"type\":\"boolean\"}").jdbcType());
		assertThrows(JsonException.class, () -> node("{\"type\":\"object\"}").jdbcType());
		assertEquals(SchemaType.LONG, node("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"BIGINT\"}}").schemaType());
		assertEquals(SchemaType.BIGDECIMAL, node("{\"type\":\"number\",\"mf-jdbc\":{\"jdbc-type\":\"NUMERIC\"}}").schemaType());
	}

	public void testPathSchemaFactoryStaysInItsFolder() throws Exception {
		Path root = Files.createTempDirectory("schemas");
		try {
			Path dir = Files.createDirectories(root.resolve("inner"));
			Files.writeString(dir.resolve("a.json"), "{\"title\":\"A\",\"type\":\"object\",\"properties\":{\"b\":{\"$ref\":\"b.json\"},\"self\":{\"$ref\":\"#/properties/x\"},\"x\":{\"type\":\"string\"}}}");
			Files.writeString(dir.resolve("b.json"), "{\"title\":\"B\",\"type\":\"integer\"}");
			Files.writeString(root.resolve("secret.json"), "{\"title\":\"secret\"}");
			PathSchemaFactory f = new PathSchemaFactory(dir, "https://ex.com/s");
			assertEquals("A", f.getSchema("https://ex.com/s/a.json").getTitle());
			// No escape out of the schema folder
			assertNull(f.getSchema("https://ex.com/s/../secret.json"));
			assertNull(f.getSchema("https://ex.com/s/" + root.resolve("secret.json").toAbsolutePath()));
			assertNull(f.getSchema("https://ex.com/s/"));
			assertNull(f.getSchema("https://ex.com/s/ftp://x/a.json"));
			// resolve=true resolves the references, local and relative
			SchemaNode resolved = f.getSchema("https://ex.com/s/a.json", true);
			assertEquals("B", resolved.getProperties().get("b").getTitle());
			assertEquals("string", resolved.getProperties().get("self").getType());
			// resolve=false keeps them
			SchemaNode raw = f.getSchema("https://ex.com/s/a.json", false);
			assertEquals("b.json", raw.getProperties().get("b").wrapped().getString("$ref"));
			// A reference that cannot be resolved
			Files.writeString(dir.resolve("bad.json"), "{\"properties\":{\"x\":{\"$ref\":\"missing.json\"}}}");
			assertThrows(JsonException.class, () -> f.getSchema("https://ex.com/s/bad.json", true));
			assertNotNull(f.getSchema("https://ex.com/s/bad.json", false));
		} finally {
			try(java.util.stream.Stream<Path> w = Files.walk(root)) {
				w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}
}
