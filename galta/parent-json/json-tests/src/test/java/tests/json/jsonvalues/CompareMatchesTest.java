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
package tests.json.jsonvalues;

import org.junit.Before;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class CompareMatchesTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
{
  "v_null": null,
  "v_int": 33,
  "v_double": 33.0,
  "v_boolean": true,
  "v_string": "abc",
}
""";

	JsonValues json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonValues.of(JsonObject.parse(JSON));
	}	
	
	public void testMatches() throws Exception {
		assertFalse(json.get("v_null").matches(".*bc.*"));
		assertFalse(json.get("v_int").matches(".*bc.*"));
		assertFalse(json.get("v_boolean").matches(".*bc.*"));
		
		assertTrue(json.get("v_string").matches(".*bc.*"));
		assertFalse(json.get("v_string").matches(".*bz.*"));
	}
}
