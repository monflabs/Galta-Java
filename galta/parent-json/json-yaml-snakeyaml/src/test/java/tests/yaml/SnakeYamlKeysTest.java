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

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.yaml.SnakeYaml;

import tests.ProjectTestCase;

/**
 * E5: YAML mapping keys need not be strings, but the mappings are materialized as
 * JsonObjects (Map&lt;String,Object&gt;). Non-string keys must be converted to their
 * string form, the way JSON/JavaScript treat property names.
 */
public class SnakeYamlKeysTest extends ProjectTestCase {

	private static final String YAML = """
1: one
true: yes
2.5: float
null: nil
s: string
nested:
  3: three
  false: no
list:
  - 4: four
  - k: v
""";

	public void testNonStringKeysBecomeStrings() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse(YAML);

		assertEquals("one", o.get("1"));
		assertEquals("yes", o.get("true"));
		assertEquals("float", o.get("2.5"));
		assertEquals("nil", o.get("null"));
		assertEquals("string", o.get("s"));

		JsonObject nested = (JsonObject)o.get("nested");
		assertEquals("three", nested.get("3"));
		assertEquals("no", nested.get("false"));

		JsonArray list = (JsonArray)o.get("list");
		assertEquals("four", ((JsonObject)list.get(0)).get("4"));
		assertEquals("v", ((JsonObject)list.get(1)).get("k"));

		assertOnlyStringKeys(o);
	}

	public void testAllEntryPointsConvertKeys() throws Exception {
		assertOnlyStringKeys(SnakeYaml.parse(new StringReader(YAML)));
		assertOnlyStringKeys(SnakeYaml.parse(new java.io.ByteArrayInputStream(YAML.getBytes("UTF-8"))));
	}

	public void testValuesAreNotConverted() throws Exception {
		JsonObject o = (JsonObject)SnakeYaml.parse("a: 1\nb: true\nc: null\n");
		assertEquals(1, ((Number)o.get("a")).intValue());
		assertEquals(Boolean.TRUE, o.get("b"));
		assertTrue(o.containsKey("c"));
		assertNull(o.get("c"));
	}

	/** Iterates the raw key set: with heap pollution the keys would not be Strings. */
	private static void assertOnlyStringKeys(Object v) {
		if(v instanceof Map<?,?> m) {
			assertTrue(v instanceof JsonObject);
			for(Object k : m.keySet()) {
				assertTrue("non-string key " + k + " (" + (k==null ? "null" : k.getClass().getName()) + ")", k instanceof String);
			}
			for(Object c : m.values()) {
				assertOnlyStringKeys(c);
			}
		} else if(v instanceof List<?> l) {
			assertTrue(v instanceof JsonArray);
			for(Object c : l) {
				assertOnlyStringKeys(c);
			}
		}
	}
}
