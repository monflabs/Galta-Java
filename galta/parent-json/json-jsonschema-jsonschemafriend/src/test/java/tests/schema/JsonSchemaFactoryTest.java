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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonschema.JsonSchema;
import org.monflabs.json.jsonschema.JsonSchemaFactory;

import net.jimblackler.jsonschemafriend.SchemaStore;
import tests.ProjectTestCase;

/**
 * E6: the factory's shared SchemaStore must not grow with every ad-hoc schema, and the
 * factory must be usable from several threads at once.
 */
public class JsonSchemaFactoryTest extends ProjectTestCase {

	private static final String SCHEMA = 
"""
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "name": { "type": "string", "minLength": 2 },
    "tags": { "type": "array", "items": { "$ref": "#/$defs/tag" } }
  },
  "required": ["name"],
  "additionalProperties": false,
  "$defs": {
    "tag": { "type": "string" }
  }
}
""";
	private static final String VALID = "{\"name\":\"Bill\",\"tags\":[\"a\",\"b\"]}";
	private static final String INVALID = "{\"name\":\"B\",\"tags\":[1]}";

	/**
	 * White-box: reads the size of the shared store's document map through reflection. The
	 * SchemaStore has no size accessor; this is the very map that used to gain one entry per
	 * getJsonSchema(JsonObject) call. Pinned to jsonschemafriend 0.12.x field names.
	 */
	private static int sharedStoreDocuments(JsonSchemaFactory factory) throws Exception {
		Field storeField = JsonSchemaFactory.class.getDeclaredField("schemaStore");
		storeField.setAccessible(true);
		SchemaStore store = (SchemaStore)storeField.get(factory);
		Field mapField = SchemaStore.class.getDeclaredField("canonicalUriToObject");
		mapField.setAccessible(true);
		return ((Map<?,?>)mapField.get(store)).size();
	}

	public void testAdHocSchemasAreNotRetained() throws Exception {
		JsonSchemaFactory factory = new JsonSchemaFactory();
		int before = sharedStoreDocuments(factory);

		for(int i=0; i<200; i++) {
			JsonSchema sc = factory.getJsonSchema(JsonObject.parse(SCHEMA));
			sc.validate(JsonObject.parse(VALID));
			try {
				sc.validate(JsonObject.parse(INVALID));
				fail("invalid document accepted");
			} catch(JsonException expected) {
			}
		}

		assertEquals("shared store grew with ad-hoc schemas", before, sharedStoreDocuments(factory));
	}

	// Local $refs inside an ad-hoc document must still resolve against its own store
	public void testAdHocLocalRefResolves() throws Exception {
		JsonSchema sc = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(SCHEMA));
		sc.validate(JsonObject.parse(VALID));
		try {
			sc.validate(JsonObject.parse("{\"name\":\"Bill\",\"tags\":[1]}"));
			fail("$ref to $defs/tag was not applied");
		} catch(JsonException expected) {
		}
	}

	public void testConcurrentUse() throws Exception {
		JsonSchemaFactory factory = new JsonSchemaFactory();
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for(int t=0; t<threads; t++) {
				futures.add(pool.submit(() -> {
					for(int i=0; i<50; i++) {
						JsonSchema sc = factory.getJsonSchema(JsonObject.parse(SCHEMA));
						sc.validate(JsonObject.parse(VALID));
						try {
							sc.validate(JsonObject.parse(INVALID));
							throw new AssertionError("invalid document accepted");
						} catch(JsonException expected) {
						}
					}
					return null;
				}));
			}
			for(Future<?> f : futures) {
				f.get(2, TimeUnit.MINUTES);
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
