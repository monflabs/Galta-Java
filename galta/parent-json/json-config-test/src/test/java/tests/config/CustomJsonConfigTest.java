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
package tests.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.config.CustomJsonConfig;
import org.monflabs.util.config.ConfigException;

import tests.ProjectTestCase;

public class CustomJsonConfigTest extends ProjectTestCase {

	private static Function<String,InputStream> reader(Map<String,byte[]> store) {
		return (path) -> {
			byte[] b = store.get(path);
			return b!=null ? new ByteArrayInputStream(b) : null;
		};
	}
	private static BiFunction<String,Consumer<OutputStream>,InputStream> writer(Map<String,byte[]> store) {
		return (path,save) -> {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			save.accept(os);
			store.put(path, os.toByteArray());
			return null;
		};
	}
	
	public void testNoReaderNoWriter() throws Exception {
		CustomJsonConfig c = CustomJsonConfig.newBuilder().build();
		assertTrue(c.isReadOnly());
		assertNotNull(c.getContent());
		assertFalse(c.has("a"));
		assertFalse(c.save());
	}
	
	public void testReadOnlyWithoutWriter() throws Exception {
		Map<String,byte[]> store = new HashMap<>();
		store.put(null, "{\"a\":\"1\"}".getBytes());
		CustomJsonConfig c = CustomJsonConfig.newBuilder()
								.resourceReader(reader(store))
								.build();
		assertTrue(c.isReadOnly());
		assertEquals("1", c.getString("a"));
		
		// Without a writer the config is read only: no save, no NPE
		assertFalse(c.save());
		try {
			c.updateValues( (u) -> u.put("b","2") );
			fail("updateValues() should have failed");
		} catch(ConfigException ex) {
			// expected
		}
		try {
			c.setResource("x.txt", "abc");
			fail("setResource() should have failed");
		} catch(ConfigException ex) {
			// expected
		}
		assertFalse(c.has("b"));
	}
	
	public void testWritableWithWriter() throws Exception {
		Map<String,byte[]> store = new HashMap<>();
		store.put(null, "{\"a\":\"1\"}".getBytes());
		CustomJsonConfig c = CustomJsonConfig.newBuilder()
								.resourceReader(reader(store))
								.resourceWriter(writer(store))
								.build();
		assertFalse(c.isReadOnly());
		assertEquals("1", c.getString("a"));
		
		// Auto-save goes through the writer
		assertTrue( c.updateValues( (u) -> u.put("b","2") ) );
		assertEquals("2", c.getString("b"));
		JsonObject saved = (JsonObject)JsonFactory.get().parse(new String(store.get(null)));
		assertEquals("1", saved.get("a"));
		assertEquals("2", saved.get("b"));
		
		// And an explicit save works as well
		assertTrue( c.save() );
		
		// A fresh config over the same store sees the saved value
		CustomJsonConfig c2 = CustomJsonConfig.newBuilder()
								.resourceReader(reader(store))
								.resourceWriter(writer(store))
								.build();
		assertEquals("2", c2.getString("b"));
		
		// Named resources go through the writer too
		c2.setResource("a.txt", "My String");
		assertEquals("My String", c2.getResourceAsString("a.txt"));
	}
}
