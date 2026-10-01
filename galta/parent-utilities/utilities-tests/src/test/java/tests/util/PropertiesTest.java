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
package tests.util;

import static org.junit.Assert.assertThrows;

import java.io.StringReader;
import java.util.Map;

import org.monflabs.util.Properties;

import tests.ProjectTestCase;

/**
 * The chained Properties.
 */
public class PropertiesTest extends ProjectTestCase {

	public void testChain() throws Exception {
		Properties parent = Properties.of("a", 1, "b", "parent");
		Properties child = new Properties(parent, Map.of("b", "child"));
		assertSame(parent, child.getParent());
		assertEquals("child", child.get("b"));
		assertEquals(1, child.get("a"));
		assertEquals("dflt", child.get("none", "dflt"));
		assertNull(child.get("none"));
		assertEquals(Boolean.TRUE, child.has("a"));
		assertEquals(Boolean.FALSE, child.has("none"));
		child.put("c", 3).putAll(Map.of("d", 4));
		assertEquals(Map.of("b", "child", "c", 3, "d", 4), child.getPropertyMap());
		assertEquals("3", child.getString("c"));
		assertEquals("x", child.getString("none", "x"));
		assertEquals("parent", parent.getString("b"));
	}

	public void testNumbers() throws Exception {
		Properties p = Properties.of("i", 12, "s", " 34 ", "l", 5_000_000_000L, "d", 2.0, "n", null);
		assertEquals(12, p.getInt("i"));
		assertEquals(34, p.getInt("s"));
		assertEquals(2, p.getInt("d"));
		assertEquals(7, p.getInt("none", 7));
		assertEquals(7, p.getInt("n", 7));                   // null: the default
		assertEquals(5_000_000_000L, p.getLong("l"));
		assertEquals(34L, p.getLong("s"));
		assertEquals(0L, new Properties(p).getLong("none"));
		assertEquals(12L, new Properties(p).getLong("i"));
	}

	public void testInvalidNumbers() throws Exception {
		Properties p = Properties.of("t", "abc", "l", 5_000_000_000L, "f", 3.7, "lt", "1e3");
		// A raw NumberFormatException used to escape, and 3.7/a long were silently truncated
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> p.getInt("t"));
		assertEquals("Invalid integer value \"abc\" for property t", e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> p.getInt("l"));
		assertThrows(IllegalArgumentException.class, () -> p.getInt("f"));
		assertThrows(IllegalArgumentException.class, () -> p.getLong("f"));
		e = assertThrows(IllegalArgumentException.class, () -> p.getLong("lt"));
		assertEquals("Invalid long value \"1e3\" for property lt", e.getMessage());
	}

	public void testBooleans() throws Exception {
		Properties p = Properties.of("t", "true", "T", " TRUE ", "f", "False", "b", Boolean.TRUE, "one", 1, "zero", 0, "yes", "yes");
		assertTrue(p.getBoolean("t"));
		assertTrue(p.getBoolean("T"));
		assertFalse(p.getBoolean("f", true));
		assertTrue(p.getBoolean("b"));
		assertTrue(p.getBoolean("one"));
		assertFalse(p.getBoolean("zero"));
		assertTrue(p.getBoolean("none", true));
		assertTrue(new Properties(p).getBoolean("t"));
		// "yes" used to be silently false
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> p.getBoolean("yes"));
		assertEquals("Invalid boolean value \"yes\" for property yes", e.getMessage());
	}

	public void testReadMap() throws Exception {
		Map<String,Object> m = Properties.readMap(new StringReader("a=1\n# comment\nb = two\n"));
		assertEquals(Map.of("a", "1", "b", "two"), m);
		assertEquals(1, new Properties(null, m).getInt("a"));
	}
}
