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

import java.util.Set;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class KeySetTest extends ProjectTestCase {
	
	public void testKeySet() throws Exception {
		assertEquals(Set.of(), JsonValues.of().keySet());
		assertEquals(Set.of("a"), JsonValues.of( JsonObject.of("a",1)).keySet());
		assertEquals(Set.of("a","b"), JsonValues.of( JsonObject.of("a",1,"b",2)).keySet());
		assertEquals(Set.of("a","b","c"), JsonValues.of( JsonObject.of("a",1,"b",2), JsonObject.of("c",3)).keySet());
		assertEquals(Set.of("a","b","c"), JsonValues.of( JsonObject.of("a",1,"b",2), JsonObject.of("c",3,"b",2)).keySet());
		assertEquals(Set.of("a","b","c"), JsonValues.of( JsonObject.of("a",1,"b",2), "A", JsonObject.of("c",3,"b",2), 4, false, JsonArray.of("z")).keySet());
	}
}
