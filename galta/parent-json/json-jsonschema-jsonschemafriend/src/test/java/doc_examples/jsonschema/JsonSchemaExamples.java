package doc_examples.jsonschema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonschema.JsonSchema;
import org.monflabs.json.jsonschema.JsonSchemaFactory;

import net.jimblackler.jsonschemafriend.ListValidationException;
import net.jimblackler.jsonschemafriend.ValidationError;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/JsonSchema.md
 */
public class JsonSchemaExamples extends ProjectTestCase {

	private static final String PERSON = """
			{
			  "$schema": "https://json-schema.org/draft/2020-12/schema",
			  "type": "object",
			  "properties": {
			    "name": { "type": "string", "minLength": 2 },
			    "age":  { "type": "integer", "minimum": 0 },
			    "tags": { "type": "array", "items": { "$ref": "#/$defs/tag" } }
			  },
			  "required": ["name"],
			  "$defs": { "tag": { "type": "string" } }
			}
			""";

	public void testValidate() throws Exception {
		JsonSchema schema = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(PERSON));

		schema.validate(JsonObject.parse("{ \"name\": \"Ada\", \"age\": 36, \"tags\": [\"math\"] }"));   // returns: valid

		try {
			schema.validate(JsonObject.parse("{ \"name\": \"A\", \"age\": -1 }"));
			fail();
		} catch(JsonException e) {
			assertEquals("""
					Validation errors: "A" at #/name failed against #/properties/name with "Shorter than minLength: 2"
					-1 at #/age failed against #/properties/age with "Less than minimum: 0\"""", e.getMessage());
		}
	}

	public void testErrorDetails() throws Exception {
		JsonSchema schema = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(PERSON));
		try {
			schema.validate(JsonObject.parse("{ \"name\": \"A\", \"age\": -1, \"tags\": [1] }"));
			fail();
		} catch(JsonException e) {
			// The cause is the jsonschemafriend exception, with one entry per error
			ListValidationException cause = (ListValidationException)e.getCause();
			List<String> errors = new ArrayList<>();
			for(ValidationError error: cause.getErrors()) {
				errors.add(error.getUri() + ": " + error.getMessage());
			}
			assertEquals(List.of(
					"#/name: Shorter than minLength: 2",
					"#/age: Less than minimum: 0",
					"#/tags/0: Expected: [string] Found: [number, integer]"), errors);
		}
	}

	public void testAnyValue() throws Exception {
		JsonSchema schema = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(PERSON));
		try {
			schema.validate("text");                              // any JSON value can be validated
			fail();
		} catch(JsonException e) {
			// Validation errors: "text" at root failed with "Expected: [object] Found: [string]"
			assertTrue(e.getMessage().contains("Expected: [object] Found: [string]"));
		}
		try {
			schema.validate(JsonObject.parse("{ \"age\": 3 }"));
			fail();
		} catch(JsonException e) {
			assertTrue(e.getMessage().contains("Missing property name"));
		}
	}

	public void testDraftDetection() throws Exception {
		// With "$schema": 2020-12, a number with a zero fraction is an integer
		JsonSchema modern = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(PERSON));
		modern.validate(JsonObject.parse("{ \"name\": \"Ada\", \"age\": 36.0 }"));
		modern.validate(JsonObject.of("name", "Ada", "age", 36L));

		// Without "$schema" (and without "$id"), the schema is read as draft-04, where it is not
		JsonSchema legacy = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("""
				{ "type": "object", "properties": { "age": { "type": "integer" } } }
				"""));
		legacy.validate(JsonObject.parse("{ \"age\": 36 }"));
		try {
			legacy.validate(JsonObject.parse("{ \"age\": 36.0 }"));
			fail();
		} catch(JsonException e) {
			// 36.0 at #/age failed against #/properties/age with "Expected: [integer] Found: [number]"
			assertTrue(e.getMessage().contains("Expected: [integer] Found: [number]"));
		}
	}

	public void testFormatsAreAsserted() throws Exception {
		JsonSchema email = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("{ \"type\": \"string\", \"format\": \"email\" }"));
		email.validate("ada@example.com");
		try {
			email.validate("not-an-email");
			fail();
		} catch(JsonException e) {
			assertTrue(e.getMessage().contains("Not compliant with format: email"));
		}
	}

	public void testSchemaIsNotValidated() throws Exception {
		// The schema itself is not checked: an invalid keyword value is silently ignored
		JsonSchema odd = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("{ \"type\": 12 }"));
		odd.validate("anything");
	}

	public void testUriSchemas() throws Exception {
		Path dir = Files.createTempDirectory("galta-schema");
		Files.writeString(dir.resolve("address.json"), """
				{ "type": "object", "properties": { "city": { "type": "string" } }, "required": ["city"] }
				""");
		Files.writeString(dir.resolve("person.json"), """
				{ "type": "object", "properties": { "address": { "$ref": "address.json" } } }
				""");

		JsonSchema person = JsonSchemaFactory.get().getJsonSchema(dir.resolve("person.json").toUri());
		person.validate(JsonObject.parse("{ \"address\": { \"city\": \"Paris\" } }"));
		try {
			person.validate(JsonObject.parse("{ \"address\": { } }"));   // relative $ref resolved
			fail();
		} catch(JsonException e) {
			assertTrue(e.getMessage().contains("Missing property city"));
		}

		// URI-addressed schemas are cached by the factory: later changes to the file are not seen
		Files.writeString(dir.resolve("address.json"), "{ \"type\": \"object\" }");
		JsonSchema again = JsonSchemaFactory.get().getJsonSchema(dir.resolve("person.json").toUri().toString());
		try {
			again.validate(JsonObject.parse("{ \"address\": { } }"));
			fail();
		} catch(JsonException e) {
			// still the first version
		}
		// A new factory has its own cache
		new JsonSchemaFactory().getJsonSchema(dir.resolve("person.json").toUri())
				.validate(JsonObject.parse("{ \"address\": { } }"));
	}

	public void testMissingSchemaAcceptsEverything() throws Exception {
		Path dir = Files.createTempDirectory("galta-schema");
		// jsonschemafriend logs a warning and uses a schema that permits everything
		JsonSchema missing = JsonSchemaFactory.get().getJsonSchema(dir.resolve("nope.json").toUri());
		missing.validate(JsonObject.of("any", "thing"));
		// Same for a $ref to a missing document
		JsonSchema withRef = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(
				"{ \"properties\": { \"a\": { \"$ref\": \"" + dir.resolve("gone.json").toUri() + "\" } } }"));
		withRef.validate(JsonObject.of("a", 42));

		try {
			JsonSchemaFactory.get().getJsonSchema("not a uri");
			fail();
		} catch(JsonException e) {
			// Illegal character in path at index 3: not a uri
		}
	}

	public void testSharedSchema() throws Exception {
		JsonSchema schema = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(PERSON));
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for(int t = 0; t < 4; t++) {
				futures.add(pool.submit(() -> {
					for(int i = 0; i < 100; i++) {
						schema.validate(JsonObject.of("name", "Ada", "age", i));
					}
					return null;
				}));
			}
			for(Future<?> f: futures) {
				f.get(1, TimeUnit.MINUTES);
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
