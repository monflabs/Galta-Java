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

import static org.junit.Assert.assertThrows;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonschema.JsonSchema;
import org.monflabs.json.jsonschema.JsonSchemaFactory;

import tests.ProjectTestCase;

public class JsonSchemaTest extends ProjectTestCase {

	private static String SCHEMA = 
"""
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "properties": {
    "name": {
      "type": "string",
      "minLength": 2
    }
  },
  "additionalProperties": false
}	
""";
	private static String DOC1 = 
"""
{
  "name": "Bill"
}
""";
	private static String DOC2 = 
"""
{
  "name2": "John"
}
""";

	public void testJsonSchema() throws Exception {
		JsonSchema sc = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse(SCHEMA));
		
		sc.validate(JsonObject.parse(DOC1));
		assertThrows(JsonException.class, () -> sc.validate(JsonObject.parse(DOC2)) );
	}
}
