package doc_examples.json;

import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.JDBCType;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonType;
import org.monflabs.json.jsonschema.PathSchemaFactory;
import org.monflabs.json.jsonschema.SchemaNode;
import org.monflabs.json.jsonschema.SchemaType;

import tests.ProjectTestCase;

/**
 * Samples of docs/GaltaJSON/Schema.md
 */
public class SchemaExamples extends ProjectTestCase {

	static final String PERSON = """
		{
		  "$id": "person.json",
		  "$schema": "https://json-schema.org/draft/2020-12/schema",
		  "title": "Person",
		  "type": "object",
		  "properties": {
		    "name":    { "type": "string", "description": "Full name" },
		    "born":    { "type": "string", "format": "date" },
		    "updated": { "type": "string", "format": "date-time", "mf-format-ex": "date-time-tz" },
		    "age":     { "type": ["integer", "null"], "mf-jdbc": { "jdbc-type": "SMALLINT" } },
		    "salary":  { "type": "number", "mf-jdbc": { "jdbc-type": "DECIMAL" } },
		    "tags":    { "type": "array", "items": { "type": "string" } }
		  }
		}
		""";

	public void testSchemaNode() {
		SchemaNode person = new SchemaNode(JsonObject.parse(PERSON));

		assertEquals("person.json", person.getId());
		assertEquals("https://json-schema.org/draft/2020-12/schema", person.getSchema());
		assertEquals("Person", person.getTitle());
		assertEquals("object", person.getType());
		assertTrue(person.isObject());
		assertEquals(SchemaType.OBJECT, person.schemaType());

		Map<String,SchemaNode> props = person.getProperties();
		assertEquals(List.of("name", "born", "updated", "age", "salary", "tags"), List.copyOf(props.keySet()));
		assertEquals("Full name", props.get("name").getDescription());
		assertEquals("string", props.get("tags").wrapped().getObject("items").getString("type"));
	}

	public void testNullableTypes() {
		SchemaNode age = new SchemaNode(JsonObject.parse("{\"type\":[\"integer\",\"null\"]}"));
		assertEquals("integer", age.getType());        // the main type of a [type, "null"] pair
		assertEquals("integer", age.getMainType());
		assertTrue(age.isNullable());

		SchemaNode mixed = new SchemaNode(JsonObject.parse("{\"type\":[\"integer\",\"string\"]}"));
		assertNull(mixed.getMainType());                // ambiguous
		assertThrows(JsonException.class, () -> mixed.schemaType());

		SchemaNode untyped = new SchemaNode(JsonObject.create());
		assertFalse(untyped.isObject() || untyped.isArray());  // no type: neither
		assertTrue(new SchemaNode(JsonObject.parse("{\"properties\":{}}")).isObject());   // no type, but properties
		assertFalse(mixed.isObject() || mixed.isArray());      // an ambiguous type is neither
		assertNull(untyped.getProperties());
	}

	public void testSchemaAndJdbcTypes() {
		Map<String,SchemaNode> props = new SchemaNode(JsonObject.parse(PERSON)).getProperties();

		assertEquals(SchemaType.STRING, props.get("name").schemaType());
		assertEquals(JDBCType.VARCHAR, props.get("name").jdbcType());
		assertEquals(SchemaType.LOCALDATE, props.get("born").schemaType());
		assertEquals(JDBCType.DATE, props.get("born").jdbcType());
		assertEquals(SchemaType.OFFSETDATETIME, props.get("updated").schemaType());
		assertEquals(JDBCType.TIMESTAMP_WITH_TIMEZONE, props.get("updated").jdbcType());
		assertEquals(SchemaType.SHORT, props.get("age").schemaType());
		assertEquals(JDBCType.SMALLINT, props.get("age").jdbcType());
		assertEquals(SchemaType.BIGDECIMAL, props.get("salary").schemaType());
		assertEquals(JDBCType.DECIMAL, props.get("salary").jdbcType());
		assertEquals(SchemaType.ARRAY, props.get("tags").schemaType());
		assertThrows(JsonException.class, () -> props.get("tags").jdbcType());   // no JDBC type for containers

		assertEquals(JsonType.NUMBER, SchemaType.SHORT.getJsonType());
		assertTrue(SchemaType.LOCALDATE.isString());
	}

	public void testDefaults() {
		assertEquals(SchemaType.INTEGER, schemaType("{\"type\":\"integer\"}"));
		assertEquals(SchemaType.NUMBER, schemaType("{\"type\":\"number\"}"));
		assertEquals(SchemaType.LOCALTIME, schemaType("{\"type\":\"string\",\"format\":\"time\"}"));
		assertEquals(SchemaType.OFFSETTIME, schemaType("{\"type\":\"string\",\"format\":\"time\",\"mf-format-ex\":\"time-tz\"}"));
		assertEquals(SchemaType.LOCALDATETIME, schemaType("{\"type\":\"string\",\"format\":\"date-time\"}"));
		assertEquals(SchemaType.STRING, schemaType("{\"type\":\"string\",\"format\":\"email\"}"));
		assertEquals(SchemaType.LONG, schemaType("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"BIGINT\"}}"));
		assertEquals(SchemaType.FLOAT, schemaType("{\"type\":\"number\",\"mf-jdbc\":{\"jdbc-type\":\"REAL\"}}"));
		assertEquals(JDBCType.DOUBLE, new SchemaNode(JsonObject.parse("{\"type\":\"number\"}")).jdbcType());
		// A jdbc-type that does not fit the JSON type is ignored
		assertEquals(SchemaType.INTEGER, schemaType("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"DECIMAL\"}}"));
		// isNumber() is only for "number"
		assertFalse(new SchemaNode(JsonObject.parse("{\"type\":\"integer\"}")).isNumber());
	}

	public void testPathSchemaFactory() throws Exception {
		Path dir = Files.createTempDirectory("schemas");
		try {
			Files.writeString(dir.resolve("person.json"), PERSON);
			PathSchemaFactory factory = new PathSchemaFactory(dir, "https://example.com/schemas");

			SchemaNode person = factory.getSchema("https://example.com/schemas/person.json");
			assertEquals("Person", person.getTitle());
			assertNull(factory.getSchema("https://example.com/schemas/other.json"));   // no such file
			assertNull(factory.getSchema("https://elsewhere.com/person.json"));        // not under the base URI
		} finally {
			Files.delete(dir.resolve("person.json"));
			Files.delete(dir);
		}
	}

	private static SchemaType schemaType(String json) {
		return new SchemaNode(JsonObject.parse(json)).schemaType();
	}
}
