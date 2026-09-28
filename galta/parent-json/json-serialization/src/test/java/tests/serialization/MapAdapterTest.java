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
package tests.serialization;

import java.util.HashMap;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

/**
 * Map adapter error paths: null keys, and a proper message for non-object values (A7).
 */
public class MapAdapterTest extends ProjectTestCase {

	public void testNullKeyReportsJsonException() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder().build();
		ClassAdapter[] params = new ClassAdapter[] { reg.findAdapter(String.class), reg.findAdapter(String.class) };

		Map<String,String> m = new HashMap<>();
		m.put(null, "v");
		try {
			reg.findAdapter(Map.class).serialize(m, params);
			fail("A null key should be rejected");
		} catch(JsonException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("null"));
		}
	}

	public void testNonObjectValueMessage() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder().build();
		ClassAdapter[] params = new ClassAdapter[] { reg.findAdapter(String.class), reg.findAdapter(String.class) };
		try {
			reg.deserialize(Map.class, JsonArray.of(1), params);
			fail("An array is not a map");
		} catch(JsonException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("not an object"));
		}
	}
}
