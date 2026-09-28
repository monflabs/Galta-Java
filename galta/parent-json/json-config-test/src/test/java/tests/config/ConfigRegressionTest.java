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
}
