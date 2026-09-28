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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class MiscellaneousTest extends ProjectTestCase {

	public void testLiteralValues() {
		assertTrue(JsonValues.EMPTY.isEmpty());
		assertTrue(JsonValues.NULL.isNull());
		assertTrue(JsonValues.ZERO.isNumber());

		assertFalse(JsonValues.of(1).isEmpty());
		assertEquals(1, JsonValues.of(1)._size());
		
		assertFalse(JsonValues.of(1,2).isEmpty());
		assertEquals(2, JsonValues.of(1,2)._size());
	}
		
	public void testMultiValuePath() {
		JsonValues v = JsonValues.of(JsonArray.parse("[{a: {b:1}}, {a: {b:2}}]"));
		
		JsonValues a = v.path("$.*.a");
		assertEquals(JsonValues.of(JsonObject.of("b",1),JsonObject.of("b",2)),a);
		
		JsonValues b = a.path("$.b");
		assertEquals(JsonValues.of(1,2),b);
	}
}
