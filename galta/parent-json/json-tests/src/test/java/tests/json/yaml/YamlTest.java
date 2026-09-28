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
package tests.json.yaml;

import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.yaml.SnakeYaml;

import tests.ProjectTestCase;

public class YamlTest extends ProjectTestCase {
	
	public static final String YAML_OBJECT = """
# simple YAML file
a1:
  p1: A string
  p2: 345
  p3: true
  pa:
  - b1: B1
  - b2: B2
a2:
  p1: Another string
  p2: 567
  p3: false

""";

	public static final String YAML_ARRAY = """
# simple YAML file
- a1:
    p1: A string
    p2: 345
    p3: true
    pa:
    - python
    - perl
    - pascal
- a2:
    p1: Another String
    p2: 567
    p3: false
""";
	
	public void testParseObject() throws Exception {
		Object a = SnakeYaml.parse(YAML_OBJECT);
		JsonObject jp = (JsonObject)a;
		checkFactoryClasses(jp);
	}
	
	public void testParseArray() throws Exception {
		Object a = SnakeYaml.parse(YAML_ARRAY);
		JsonArray jp = (JsonArray)a;
		checkFactoryClasses(jp);
	}
	
	private void checkFactoryClasses(Object v) {
		if(v instanceof Map<?,?>) {
			assertTrue(v instanceof JsonObject);
			JsonObject o = (JsonObject)v;
			for(Object c: o.values()) {
				checkFactoryClasses(c);
			}
		}
		if(v instanceof List<?>) {
			assertTrue(v instanceof JsonArray);
			JsonArray a = (JsonArray)v;
			for(Object c: a.values()) {
				checkFactoryClasses(c);
			}
		}
	}
	
	public void testStringify() throws Exception {
		JsonObject o = JsonObject.of(
			"a", 1,
			"b", JsonObject.of(
				"xx", "xyz" 
			),
			"c", JsonArray.of(4,5,6)
		);
		String s = SnakeYaml.stringify(o);
		
		assertEquals(
"""
a: 1
b:
  xx: xyz
c:
- 4
- 5
- 6
""", s);
	}

}
