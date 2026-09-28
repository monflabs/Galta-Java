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

import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.monflabs.util.config.Config;
import org.monflabs.util.config.ConfigException;

import tests.ProjectTestCase;

public class ConfigTest extends ProjectTestCase {

	/**
	 * A map backed Config. Its updater keeps the typed values as is, to check they are not
	 * turned into strings on the way.
	 */
	static class MapConfig implements Config {
		final TreeMap<String,Object> values = new TreeMap<>();
		final Map<String,byte[]> resources = new HashMap<>();
		boolean autoSave;
		MapConfig(boolean autoSave) {
			this.autoSave = autoSave;
		}
		@Override
		public Iterator<String> keysOf(String key, ENUM_KEYS type) {
			List<String> l = new ArrayList<>();
			for(String k: values.keySet()) {
				if(k.startsWith(key+"/")) {
					l.add(k.substring(key.length()+1));
				}
			}
			return l.iterator();
		}
		@Override
		public boolean has(String key) {
			return values.containsKey(key);
		}
		@Override
		public boolean isValue(String key) {
			return values.containsKey(key);
		}
		@Override
		public boolean isFolder(String key) {
			return values.keySet().stream().anyMatch(k -> k.startsWith(key+"/"));
		}
		@Override
		public Object getValue(String key) {
			return values.get(key);
		}
		@Override
		public boolean isReadOnly() {
			return false;
		}
		@Override
		public boolean isAutoSave() {
			return autoSave;
		}
		@Override
		public boolean updateValues(Consumer<Updater> c) {
			c.accept(new Updater() {
				@Override
				public boolean put(String key, String value) {
					values.put(key, value);
					return true;
				}
				@Override
				public boolean put(String key, int value) {
					values.put(key, value);
					return true;
				}
				@Override
				public boolean put(String key, long value) {
					values.put(key, value);
					return true;
				}
				@Override
				public boolean put(String key, double value) {
					values.put(key, value);
					return true;
				}
				@Override
				public boolean put(String key, boolean value) {
					values.put(key, value);
					return true;
				}
				@Override
				public boolean remove(String key) {
					return values.remove(key)!=null;
				}
				@Override
				public void cancel() {
				}
			});
			return true;
		}
		@Override
		public InputStream getResource(String path) {
			byte[] b = resources.get(path);
			return b!=null ? new ByteArrayInputStream(b) : null;
		}
		@Override
		public void setResource(String path, Consumer<OutputStream> save) {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			save.accept(os);
			resources.put(path, os.toByteArray());
		}
	}

	public void testGetBoolean() throws Exception {
		MapConfig c = new MapConfig(true);
		c.values.put("t", "true");
		c.values.put("T", " TRUE ");
		c.values.put("f", "false");
		c.values.put("b", Boolean.TRUE);
		c.values.put("yes", "yes");
		c.values.put("one", "1");
		assertTrue(c.getBoolean("t"));
		assertTrue(c.getBoolean("T"));
		assertFalse(c.getBoolean("f", true));
		assertTrue(c.getBoolean("b"));
		assertTrue(c.getBoolean("missing", true));
		assertFalse(c.getBoolean("missing"));
		// Boolean.parseBoolean() never fails: these silently became false
		assertThrows(ConfigException.class, () -> c.getBoolean("yes", true));
		assertThrows(ConfigException.class, () -> c.getBoolean("one"));
	}

	public void testGetInt() throws Exception {
		MapConfig c = new MapConfig(true);
		c.values.put("s", "42");
		c.values.put("n", 42L);
		c.values.put("d", 42.0);
		c.values.put("big", 5_000_000_000L);
		c.values.put("frac", 3.7);
		c.values.put("sfrac", "3.7");
		c.values.put("nan", Double.NaN);
		assertEquals(42, c.getInt("s"));
		assertEquals(42, c.getInt("n"));
		assertEquals(42, c.getInt("d"));
		assertEquals(7, c.getInt("missing", 7));
		// Out of range / fractional numbers used to be truncated silently (705032704, 3)
		ConfigException e = assertThrows(ConfigException.class, () -> c.getInt("big"));
		assertNotNull(e.getCause());   // the cause is kept
		assertThrows(ConfigException.class, () -> c.getInt("frac"));
		assertThrows(ConfigException.class, () -> c.getInt("sfrac"));
		assertThrows(ConfigException.class, () -> c.getInt("nan"));
	}

	public void testGetLongAndDouble() throws Exception {
		MapConfig c = new MapConfig(true);
		c.values.put("big", 5_000_000_000L);
		c.values.put("s", " 12 ");
		c.values.put("frac", 3.5);
		c.values.put("bad", "x");
		assertEquals(5_000_000_000L, c.getLong("big"));
		assertEquals(12L, c.getLong("s"));
		assertThrows(ConfigException.class, () -> c.getLong("frac"));
		assertEquals(3.5, c.getDouble("frac"));
		assertEquals(5e9, c.getDouble("big"));
		assertEquals(1.5, c.getDouble("missing", 1.5));
		assertThrows(ConfigException.class, () -> c.getDouble("bad"));
		assertEquals("x", c.getString("bad"));
		assertEquals("d", c.getString("missing", "d"));
	}

	public void testSubConfig() throws Exception {
		MapConfig c = new MapConfig(false);
		c.values.put("s/k", "v");
		c.values.put("k", "top");
		Config sub = c.subConfig("s");
		assertEquals("v", sub.getString("k"));
		assertTrue(sub.has("k"));
		assertFalse(sub.isReadOnly());
		// isAutoSave() used to fall back to the interface default (true)
		assertFalse(sub.isAutoSave());
		assertTrue(new MapConfig(true).subConfig("s").isAutoSave());
		// A leading '/' is still relative to the sub configuration
		assertEquals("v", sub.getString("/k"));
		// ".." used to escape the sub configuration
		assertThrows(ConfigException.class, () -> sub.getValue("../k"));
		assertThrows(ConfigException.class, () -> sub.has("a/../../k"));
	}

	public void testSubConfigUpdatesAreTyped() throws Exception {
		MapConfig c = new MapConfig(true);
		Config sub = c.subConfig("s");
		sub.updateValues(u -> {
			u.put("str", "x");
			u.put("i", 5);
			u.put("l", 6L);
			u.put("d", 1.5);
			u.put("b", true);
		});
		assertEquals("x", c.values.get("s/str"));
		// The typed overloads used to be stored as strings through a sub configuration
		assertEquals(Integer.valueOf(5), c.values.get("s/i"));
		assertEquals(Long.valueOf(6), c.values.get("s/l"));
		assertEquals(Double.valueOf(1.5), c.values.get("s/d"));
		assertEquals(Boolean.TRUE, c.values.get("s/b"));
		sub.updateValues(u -> u.remove("str"));
		assertFalse(c.values.containsKey("s/str"));
	}

	public void testResources() throws Exception {
		MapConfig c = new MapConfig(true);
		c.setResource("r.txt", "été");
		assertEquals("été", new String(c.resources.get("r.txt"), StandardCharsets.UTF_8));
		assertEquals("été", c.getResourceAsString("r.txt"));
		c.setResource("l.txt", "é", StandardCharsets.ISO_8859_1);
		assertEquals(1, c.resources.get("l.txt").length);
		assertEquals("é", c.getResourceAsString("l.txt", StandardCharsets.ISO_8859_1));
		assertNull(c.getResourceAsString("missing"));
		// Sub configurations share the resources of their parent
		assertEquals("été", c.subConfig("s").getResourceAsString("r.txt"));
	}

	public void testReadOnlyDefaults() throws Exception {
		Config ro = new Config() {
			@Override public Iterator<String> keysOf(String key, ENUM_KEYS type) { return List.<String>of().iterator(); }
			@Override public boolean has(String key) { return false; }
			@Override public boolean isValue(String key) { return false; }
			@Override public boolean isFolder(String key) { return false; }
			@Override public Object getValue(String key) { return null; }
			@Override public InputStream getResource(String path) { return null; }
		};
		assertTrue(ro.isReadOnly());
		assertFalse(ro.updateValues(u -> {}));
		assertThrows(ConfigException.class, () -> ro.setResource("x", "y"));
	}
}
