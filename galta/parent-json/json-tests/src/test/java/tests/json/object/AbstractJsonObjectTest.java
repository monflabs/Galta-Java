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
package tests.json.object;

import static org.junit.Assert.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.java.AbstractJsonObject;
import org.monflabs.json.java.JavaJsonFactory;

import tests.ProjectTestCase;

/**
 * AbstractJsonObject, the base class of an adapter to another JSON library.
 */
public class AbstractJsonObjectTest extends ProjectTestCase {
	
	// A minimal adapter over a map
	private static class MapObject extends AbstractJsonObject {
		private final Map<String,Object> map = new LinkedHashMap<>();
		@Override
		public Object nativeGet(String key) {
			return map.get(key);
		}
		@Override
		public Object nativePut(String key, Object value) {
			return map.put(key, value);
		}
		@Override
		public Object nativeRemove(String key) {
			return map.remove(key);
		}
		@Override
		public Set<Map.Entry<String, Object>> entrySet() {
			return map.entrySet();
		}
		@Override
		public JsonFactory factory() {
			return JavaJsonFactory.instance;
		}
		@Override
		public Object toNativeJsonPrimitive() {
			return map;
		}
	}

	public void testPutFollowsTheMapContract() {
		MapObject o = new MapObject();
		// Map.put(): the previous value, not the object itself
		assertNull(o.put("a", (Object)1));
		assertEquals(1, o.put("a", (Object)2));
		assertEquals(2, o.get("a"));
		// So do the defaults built on it
		assertEquals(2, o.putIfAbsent("a", 3));
		assertNull(o.putIfAbsent("b", 4));
		assertEquals(4, o.get("b"));
		assertThrows(NullPointerException.class, () -> o.put(null, (Object)1));
		assertEquals(2, o.remove("a"));
		assertFalse(o.containsKey("a"));
	}

	public void testEquality() {
		MapObject o = new MapObject();
		o.put("a", (Object)1);
		assertEquals(JsonObject.of("a", 1.0), o);
		assertEquals(o, JsonObject.of("a", 1));
		assertEquals(Map.of("a", 1), o);
		assertEquals(o, Map.of("a", 1));
		assertEquals(Map.of("a", 1).hashCode(), o.hashCode());
		assertEquals("{\"a\":1}", o.stringify());
	}
}
