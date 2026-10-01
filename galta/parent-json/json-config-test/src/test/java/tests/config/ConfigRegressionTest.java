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

import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.config.KeyEncryptor;

import tests.ProjectTestCase;
import tests.config.JsonFileConfigTest.InMemoryConfig;

/**
 * Regression tests for the JSON configuration: encryption across $ref resources,
 * values looking like encrypted ones, typed updates.
 */
public class ConfigRegressionTest extends ProjectTestCase {

	private static JsonObject parse(String s) {
		return (JsonObject)JsonFactory.get().parse(s);
	}
	
	public void testEncryptionUsesFullKeyPathInReferencedResource() throws Exception {
		// The predicate selects "db/password": the value lives in db.json, referenced from
		// the main resource, and must be encrypted with its full key path
		List<String> seen = new ArrayList<>();
		KeyEncryptor enc = new KeyEncryptor("akey", (k) -> {
			String p = String.join("/", k);
			seen.add(p);
			return p.equals("db/password");
		});
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"password\": \"top\", \"db\": { \"$ref\": \"db.json\" } }")
								.resource("db.json", "{ \"host\": \"h\", \"password\": \"secret\" }")
								.encryptor(enc)
								.build();
		assertTrue(seen.contains("db/password"));
		assertFalse(seen.contains("password") && !seen.contains("db/password"));
		
		// In memory, decrypted
		assertEquals("secret", c.getString("db/password"));
		assertEquals("top", c.getString("password"));
		
		// Stored: the referenced resource is encrypted right after the load...
		JsonObject db = parse(c.getResourceAsString("db.json"));
		assertTrue((String)db.get("password"), enc.isEncrypted((String)db.get("password")));
		assertEquals("secret", enc.decryptValue((String)db.get("password")));
		assertEquals("h", db.get("host"));
		// ...the main one keeps its reference and its (not selected) top level password
		JsonObject main = parse(c.getResourceAsString(null));
		assertEquals("top", main.get("password"));
		assertEquals("db.json", ((JsonObject)main.get("db")).get("$ref"));
		
		// ...and after an explicit save
		assertTrue(c.save());
		db = parse(c.getResourceAsString("db.json"));
		assertTrue(enc.isEncrypted((String)db.get("password")));
		
		// ...and after an update of the referenced resource
		assertTrue(c.updateValues( (u) -> u.put("db/password", "changed") ));
		db = parse(c.getResourceAsString("db.json"));
		assertTrue(enc.isEncrypted((String)db.get("password")));
		assertEquals("changed", enc.decryptValue((String)db.get("password")));
		assertEquals("changed", c.getString("db/password"));
	}
	
	public void testEncryptedReferencedResourceIsDecrypted() throws Exception {
		KeyEncryptor enc = new KeyEncryptor("akey", (k) -> String.join("/", k).equals("db/password"));
		String stored = enc.encryptValue("secret");
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"db\": { \"$ref\": \"db.json\" } }")
								.resource("db.json", "{ \"password\": \""+stored+"\" }")
								.encryptor(enc)
								.build();
		assertEquals("secret", c.getString("db/password"));
	}
	
	public void testPlainValueLookingEncryptedRoundTrips() throws Exception {
		// "[[x]]" is a plain value: it must be encrypted on save, and read back as is
		KeyEncryptor enc = new KeyEncryptor("akey", (k) -> k[k.length-1].equals("password"));
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"password\": \"[[x]]\" }")
								.encryptor(enc)
								.build();
		assertEquals("[[x]]", c.getString("password"));
		String stored = (String)parse(c.getResourceAsString(null)).get("password");
		assertTrue(stored, stored.startsWith("[[v2:"));
		assertEquals("[[x]]", enc.decryptValue(stored));
		
		assertTrue(c.updateValues( (u) -> u.put("password", "[[y]]") ));
		assertEquals("[[y]]", c.getString("password"));
		stored = (String)parse(c.getResourceAsString(null)).get("password");
		assertEquals("[[y]]", enc.decryptValue(stored));
	}
	
	public void testTypedUpdatesKeepTheirType() throws Exception {
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"port\": 8080 }")
								.build();
		assertTrue(c.updateValues( (u) -> {
			u.put("port", 9090);
			u.put("big", 5000000000L);
			u.put("ratio", 0.5);
			u.put("enabled", true);
			u.put("name", "joe");
			u.put("sub/level", 3);
		}));
		JsonObject saved = parse(c.getResourceAsString(null));
		assertTrue(saved.get("port") instanceof Number);
		assertEquals(9090, ((Number)saved.get("port")).intValue());
		assertTrue(saved.get("big") instanceof Number);
		assertEquals(5000000000L, ((Number)saved.get("big")).longValue());
		assertTrue(saved.get("ratio") instanceof Number);
		assertEquals(0.5, ((Number)saved.get("ratio")).doubleValue());
		assertEquals(Boolean.TRUE, saved.get("enabled"));
		assertEquals("joe", saved.get("name"));
		assertTrue(((JsonObject)saved.get("sub")).get("level") instanceof Number);
		
		// And the typed getters read them back
		assertEquals(9090, c.getInt("port"));
		assertEquals(5000000000L, c.getLong("big"));
		assertEquals(0.5, c.getDouble("ratio"));
		assertTrue(c.getBoolean("enabled"));
	}
	
	public void testTypedUpdateOnFolderIsRejected() throws Exception {
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"f\": { \"a\": 1 }, \"v\": 2 }")
								.build();
		assertFalse(c.updateValues( (u) -> {
			assertFalse(u.put("f", 1));
			assertFalse(u.put("v/x", true));
			assertFalse(u.put("", 1L));
		}));
	}
	
	public void testReadOnlyConfigIsNotRewrittenOnLoad() throws Exception {
		// With a read only config, a plain value selected for encryption is only decrypted
		// in memory, never written back
		java.util.Map<String,byte[]> store = new java.util.HashMap<>();
		store.put(null, "{ \"password\": \"plain\" }".getBytes());
		org.monflabs.json.config.CustomJsonConfig c = org.monflabs.json.config.CustomJsonConfig.newBuilder()
				.resourceReader( (p) -> store.get(p)!=null ? new java.io.ByteArrayInputStream(store.get(p)) : null )
				.encryptor(new KeyEncryptor("akey", (k) -> true))
				.build();
		assertTrue(c.isReadOnly());
		assertEquals("plain", c.getString("password"));
		assertEquals("{ \"password\": \"plain\" }", new String(store.get(null)));
		assertFalse(c.save());
	}

	private static final String LEGACY = "[[+hMf4ve48Pr2N6q4ZQ6dVn/6bfkOzPSgiQGLyyIC/XI=]]";

	public void testLegacyValueReencryptedOnLoad() throws Exception {
		KeyEncryptor enc = new KeyEncryptor("akey", (k) -> k[k.length-1].equals("password"));
		assertTrue(enc.needsReencryption(LEGACY));
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"password\": \""+LEGACY+"\" }")
								.encryptor(enc)
								.build();
		assertEquals("A value to encrypt", c.getString("password"));
		String stored = (String)parse(c.getResourceAsString(null)).get("password");
		assertTrue(stored, stored.startsWith("[[v2:"));
		assertFalse(enc.needsReencryption(stored));
		assertEquals("A value to encrypt", enc.decryptValue(stored));
	}

	public void testArrayValuesAreEncrypted() throws Exception {
		KeyEncryptor enc = new KeyEncryptor("akey", (k) -> k[0].equals("secrets"));
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"secrets\": [ \"a\", [ \"b\" ], { \"x\": \"c\" }, 3 ], \"plain\": [ \"d\" ] }")
								.encryptor(enc)
								.build();
		JsonObject stored = parse(c.getResourceAsString(null));
		org.monflabs.json.JsonArray a = (org.monflabs.json.JsonArray)stored.get("secrets");
		assertEquals("a", enc.decryptValue((String)a.get(0)));
		assertEquals("b", enc.decryptValue((String)((org.monflabs.json.JsonArray)a.get(1)).get(0)));
		assertEquals("c", enc.decryptValue((String)((JsonObject)a.get(2)).get("x")));
		assertEquals(3, ((Number)a.get(3)).intValue());
		assertEquals("d", ((org.monflabs.json.JsonArray)stored.get("plain")).get(0));
		// Decrypted in memory
		org.monflabs.json.JsonArray m = (org.monflabs.json.JsonArray)c.getContent().get("secrets");
		assertEquals("a", m.get(0));
		assertEquals("c", ((JsonObject)m.get(2)).get("x"));
	}

	public void testFailedReloadKeepsContent() throws Exception {
		java.util.Map<String,byte[]> store = new java.util.HashMap<>();
		store.put(null, "{ \"a\": 1 }".getBytes());
		boolean[] fail = new boolean[1];
		org.monflabs.json.config.CustomJsonConfig c = org.monflabs.json.config.CustomJsonConfig.newBuilder()
				.resourceReader( (p) -> {
					if(fail[0]) {
						throw new IllegalStateException("unavailable");
					}
					return new java.io.ByteArrayInputStream(store.get(p));
				})
				.build();
		JsonObject before = c.getContent();
		fail[0] = true;
		try {
			c.load();
			fail("Exception expected");
		} catch(IllegalStateException ex) {
			// expected
		}
		assertSame(before, c.getContent());
		assertEquals(1, c.getInt("a"));
	}

	public void testFileConfigRejectsRefOutsideFolder() throws Exception {
		java.nio.file.Path root = support.getProjectDirectory("target/tests/config-containment").toPath();
		org.monflabs.util.FileUtil.prepareDirectory(root.toFile(), true);
		java.nio.file.Path folder = java.nio.file.Files.createDirectories(root.resolve("conf"));
		java.nio.file.Path outside = root.resolve("outside.json");
		java.nio.file.Files.writeString(outside, "{ \"password\": \"x\" }");
		java.nio.file.Files.writeString(folder.resolve("main.json"), "{ \"o\": { \"$ref\": \"../outside.json\" } }");
		try {
			org.monflabs.json.config.JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("main.json")
				.encryptor(new KeyEncryptor("akey", (k) -> true))
				.build();
			fail("Exception expected");
		} catch(RuntimeException ex) {
			// expected: the reference is outside of the configuration folder
		}
		// ...and the outside file was not rewritten (encrypted)
		assertEquals("{ \"password\": \"x\" }", java.nio.file.Files.readString(outside));
	}

	public void testFileConfigSaveIsAtomic() throws Exception {
		java.nio.file.Path folder = support.getProjectDirectory("target/tests/config-atomic").toPath();
		org.monflabs.util.FileUtil.prepareDirectory(folder.toFile(), true);
		java.nio.file.Files.writeString(folder.resolve("main.json"), "{ \"a\": 1, \"sub\": { \"$ref\": \"sub/s.json\" } }");
		java.nio.file.Files.createDirectories(folder.resolve("sub"));
		java.nio.file.Files.writeString(folder.resolve("sub/s.json"), "{ \"b\": 2 }");
		org.monflabs.json.config.JsonFileConfig c = org.monflabs.json.config.JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("main.json")
				.build();
		assertEquals(2, c.getInt("sub/b"));
		assertTrue(c.updateValues( (u) -> { u.put("a", 3); u.put("sub/b", 4); } ));
		assertEquals(3, parse(java.nio.file.Files.readString(folder.resolve("main.json"))).get("a") instanceof Number n ? n.intValue() : -1);
		assertEquals(4, ((Number)parse(java.nio.file.Files.readString(folder.resolve("sub/s.json"))).get("b")).intValue());
		try(java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(folder)) {
			assertTrue(files.noneMatch(p -> p.toString().endsWith(".tmp")));
		}
	}
}
