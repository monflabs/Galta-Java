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
package tests.schema;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonschema.JsonSchema;
import org.monflabs.json.jsonschema.JsonSchemaFactory;

import tests.ProjectTestCase;

/**
 * Integer-valued numbers, and URI addressed schemas.
 */
public class JsonSchemaValuesTest extends ProjectTestCase {

	private static void assertValid(JsonSchema s, Object v) {
		s.validate(v);
	}
	private static void assertInvalid(JsonSchema s, Object v) {
		try {
			s.validate(v);
			fail(v+" should be invalid");
		} catch(JsonException ex) {
			// expected
		}
	}
	
	private static final String DRAFT_2020 = "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",";
	
	public void testIntegerValuedNumbers() throws Exception {
		// Draft 6 and later: an integer is a number with a zero fractional part, in any notation
		JsonSchema s = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("{"+DRAFT_2020+"\"type\":\"integer\",\"minimum\":5}"));
		assertValid(s, 100);
		assertValid(s, 100.0);
		assertValid(s, JsonFactory.get().parse("1e2"));
		assertValid(s, JsonFactory.get().parse("100.0"));
		assertInvalid(s, 1.5);
		assertInvalid(s, 4.0);                     // an integer, but below the minimum
		assertInvalid(s, "100");
		
		JsonSchema nested = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(
				"{"+DRAFT_2020+"\"type\":\"object\",\"properties\":{\"n\":{\"type\":\"integer\"},\"l\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}}}"));
		JsonObject doc = JsonObject.parse("{\"n\":1e2,\"l\":[1,2.0,3e0]}");
		assertValid(nested, doc);
		assertInvalid(nested, JsonObject.parse("{\"l\":[1,2.5]}"));
		assertInvalid(nested, JsonObject.parse("{\"n\":0.5}"));
		
		// Without $schema, jsonschemafriend applies draft-04, where 1e2 (a Double) is not an integer
		JsonSchema legacy = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("{\"type\":\"integer\"}"));
		assertValid(legacy, 100);
		assertInvalid(legacy, JsonFactory.get().parse("1e2"));
	}
	
	public void testNumberStillAcceptsFractions() throws Exception {
		JsonSchema s = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("{\"type\":\"number\",\"multipleOf\":0.5}"));
		assertValid(s, 1.5);
		assertValid(s, 2.0);
		assertInvalid(s, 1.25);
	}
	
	public void testUriSchemasAreCachedAndThreadSafe() throws Exception {
		File dir = Files.createTempDirectory("schema").toFile();
		File f = new File(dir, "person.json");
		Files.writeString(f.toPath(), "{\"type\":\"object\",\"properties\":{\"name\":{\"$ref\":\"#/$defs/n\"}},\"$defs\":{\"n\":{\"type\":\"string\"}},\"required\":[\"name\"]}");
		JsonSchemaFactory factory = new JsonSchemaFactory();
		
		JsonSchema a = factory.getJsonSchema(f.toURI());
		JsonSchema b = factory.getJsonSchema(f.toURI().toString());
		assertValid(a, JsonObject.parse("{\"name\":\"x\"}"));
		assertInvalid(b, JsonObject.parse("{\"name\":1}"));
		assertInvalid(b, JsonObject.parse("{}"));
		
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for(int t=0; t<8; t++) {
				futures.add(pool.submit(() -> {
					for(int i=0; i<50; i++) {
						JsonSchema s = factory.getJsonSchema(f.toURI());
						s.validate(JsonObject.parse("{\"name\":\"x\"}"));
						try {
							s.validate(JsonObject.parse("{\"name\":1}"));
							throw new AssertionError("invalid document accepted");
						} catch(JsonException expected) {
						}
					}
					return null;
				}));
			}
			for(Future<?> fu: futures) {
				fu.get(2, TimeUnit.MINUTES);
			}
		} finally {
			pool.shutdownNow();
		}
	}
	
	public void testInvalidUri() throws Exception {
		try {
			JsonSchemaFactory.get().getJsonSchema("not a uri with spaces");
			fail();
		} catch(JsonException ex) {
			// expected
		}
	}
}
