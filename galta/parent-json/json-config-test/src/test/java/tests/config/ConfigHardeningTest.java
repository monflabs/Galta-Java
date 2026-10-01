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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.config.JsonFileConfig;
import org.monflabs.json.config.KeyEncryptor;
import org.monflabs.util.FileUtil;
import org.monflabs.util.config.ConfigException;

import tests.ProjectTestCase;
import tests.config.JsonFileConfigTest.InMemoryConfig;

/**
 * Hardening of the configuration: symbolic links, $ref fragments and cycles, shared
 * resources, failed saves, key bound encryption, permissions and concurrent instances.
 */
public class ConfigHardeningTest extends ProjectTestCase {

	private static JsonObject parse(String s) {
		return (JsonObject)JsonFactory.get().parse(s);
	}
	private Path dir(String name) throws Exception {
		Path d = support.getProjectDirectory("target/tests/config-hardening/"+name).toPath();
		FileUtil.prepareDirectory(d.toFile(), true);
		return d;
	}
	private static KeyEncryptor passwords() {
		return new KeyEncryptor("akey", (k) -> k[k.length-1].equals("password"));
	}
	private static JsonFileConfig config(Path folder, KeyEncryptor enc) {
		JsonFileConfig.Builder b = JsonFileConfig.newBuilder().folder(folder).fileName("main.json");
		if(enc!=null) {
			b.encryptor(enc);
		}
		return b.build();
	}
	private static boolean causedBy(Throwable t, String text) {
		for(; t!=null; t=t.getCause()) {
			if(t.getMessage()!=null && t.getMessage().contains(text)) {
				return true;
			}
		}
		return false;
	}

	//
	// $ref through a symbolic link
	//

	public void testRefThroughSymlinkIsRejected() throws Exception {
		Path root = dir("symlink-ref");
		Path folder = Files.createDirectories(root.resolve("conf"));
		Path outside = Files.createDirectories(root.resolve("outside"));
		Files.writeString(outside.resolve("secret.json"), "{ \"password\": \"x\" }");
		// A link to a file outside of the folder, and a link to a folder outside of it
		Files.createSymbolicLink(folder.resolve("link.json"), outside.resolve("secret.json"));
		Files.createSymbolicLink(folder.resolve("sub"), outside);

		for(String ref: List.of("link.json", "sub/secret.json", "sub/new.json")) {
			Files.writeString(folder.resolve("main.json"), "{ \"o\": { \"$ref\": \""+ref+"\" } }");
			try {
				config(folder, passwords());
				fail("Exception expected for "+ref);
			} catch(RuntimeException ex) {
				assertTrue(ex.toString(), causedBy(ex, "outside of the configuration folder"));
			}
		}
		// The outside files were not read, nor rewritten (encrypted)
		assertEquals("{ \"password\": \"x\" }", Files.readString(outside.resolve("secret.json")));
		assertFalse(Files.exists(outside.resolve("new.json")));

		// A link that stays inside the folder is fine
		Files.writeString(folder.resolve("real.json"), "{ \"v\": 1 }");
		Files.createSymbolicLink(folder.resolve("inside.json"), folder.resolve("real.json"));
		Files.writeString(folder.resolve("main.json"), "{ \"o\": { \"$ref\": \"inside.json\" } }");
		JsonFileConfig c = config(folder, null);
		assertEquals(1, c.getInt("o/v"));
		assertTrue(c.updateValues((u) -> u.put("o/v", 2)));
		// The link is kept, its target is updated
		assertTrue(Files.isSymbolicLink(folder.resolve("inside.json")));
		assertEquals(2, ((Number)parse(Files.readString(folder.resolve("real.json"))).get("v")).intValue());
	}

	// The main file can be a link (to a file outside of the folder): saving keeps the link
	public void testSymlinkedMainFileIsKept() throws Exception {
		Path root = dir("symlink-main");
		Path folder = Files.createDirectories(root.resolve("conf"));
		Path real = Files.createDirectories(root.resolve("dotfiles")).resolve("app.json");
		Files.writeString(real, "{ \"a\": 1 }");
		Files.createSymbolicLink(folder.resolve("main.json"), real);
		JsonFileConfig c = config(folder, null);
		assertTrue(c.updateValues((u) -> u.put("a", 2)));
		assertTrue(Files.isSymbolicLink(folder.resolve("main.json")));
		assertEquals(2, ((Number)parse(Files.readString(real)).get("a")).intValue());
	}

	// A saved file keeps its permissions; a new one is only readable by its owner
	public void testPermissionsAreKept() throws Exception {
		Path folder = dir("permissions");
		Path main = folder.resolve("main.json");
		Files.writeString(main, "{ \"a\": 1 }");
		try {
			Files.setPosixFilePermissions(main, PosixFilePermissions.fromString("rw-r--r--"));
		} catch(UnsupportedOperationException ex) {
			return; // Not a POSIX file system
		}
		JsonFileConfig c = config(folder, null);
		assertTrue(c.updateValues((u) -> u.put("a", 2)));
		assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(main)));

		// A new file (a new configuration)
		Path folder2 = dir("permissions-new");
		JsonFileConfig c2 = config(folder2, null);
		assertTrue(c2.updateValues((u) -> u.put("a", 1)));
		assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder2.resolve("main.json"))));
	}

	//
	// $ref fragments, shared resources and cycles
	//

	public void testSecretsUnderAFragmentRef() throws Exception {
		KeyEncryptor enc = passwords();
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"db\": { \"$ref\": \"secrets.json#/prod\" } }")
								.resource("secrets.json", "{ \"prod\": { \"user\": \"u\", \"password\": \"p\" }, \"dev\": { \"password\": \"d\" } }")
								.encryptor(enc)
								.build();
		assertEquals("p", c.getString("db/password"));
		// Encrypted at rest, in its resource, right after the load; the rest of the resource is kept
		JsonObject secrets = parse(c.getResourceAsString("secrets.json"));
		String stored = (String)secrets.getObject("prod").get("password");
		assertTrue(stored, enc.isEncrypted(stored));
		assertEquals("p", enc.decrypt(new String[] {"db","password"}, stored));
		assertEquals("u", secrets.getObject("prod").get("user"));
		assertEquals("d", secrets.getObject("dev").get("password"));
		assertEquals("secrets.json#/prod", parse(c.getResourceAsString(null)).getObject("db").get("$ref"));

		// An update under the fragment is written back to its resource, encrypted
		assertTrue(c.updateValues((u) -> { u.put("db/password", "p2"); u.put("db/user", "u2"); }));
		secrets = parse(c.getResourceAsString("secrets.json"));
		assertEquals("p2", enc.decrypt(new String[] {"db","password"}, (String)secrets.getObject("prod").get("password")));
		assertEquals("u2", secrets.getObject("prod").get("user"));
		assertEquals("d", secrets.getObject("dev").get("password"));
		assertEquals("p2", c.getString("db/password"));
	}

	public void testResourceReferencedTwice() throws Exception {
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"a\": { \"$ref\": \"db.json\" }, \"b\": { \"$ref\": \"db.json\" } }")
								.resource("db.json", "{ \"host\": \"h\" }")
								.build();
		assertTrue(c.updateValues((u) -> u.put("a/host", "h2")));
		// One resource, one value: the update is not overwritten by the other reference
		assertEquals("h2", parse(c.getResourceAsString("db.json")).get("host"));
		assertEquals("h2", c.getString("b/host"));
		assertTrue(c.updateValues((u) -> u.put("b/host", "h3")));
		assertEquals("h3", parse(c.getResourceAsString("db.json")).get("host"));
		assertEquals("h3", c.getString("a/host"));
	}

	public void testCircularResources() throws Exception {
		try {
			InMemoryConfig.newBuilder()
				.content("{ \"a\": { \"$ref\": \"a.json\" } }")
				.resource("a.json", "{ \"b\": { \"$ref\": \"b.json\" } }")
				.resource("b.json", "{ \"a\": { \"$ref\": \"a.json\" } }")
				.build();
			fail("Exception expected");
		} catch(ConfigException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("Circular $ref"));
		}
		// A resource referencing itself
		try {
			InMemoryConfig.newBuilder()
				.content("{ \"a\": { \"$ref\": \"a.json\" } }")
				.resource("a.json", "{ \"x\": { \"$ref\": \"a.json#/y\" }, \"y\": 1 }")
				.build();
			fail("Exception expected");
		} catch(ConfigException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("Circular $ref"));
		}
	}

	//
	// Failed saves
	//

	public void testFailedSaveLeavesTheConfigUnchanged() throws Exception {
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"a\": \"1\" }")
								.failOnSave(true)
								.build();
		try {
			c.updateValues((u) -> u.put("a", "2"));
			fail("Exception expected");
		} catch(RuntimeException ex) {
			// expected
		}
		assertEquals("1", c.getString("a"));
	}

	//
	// Encryption
	//

	// An encrypted value is bound to its key: it cannot be moved to another key
	public void testEncryptedValueIsBoundToItsKey() throws Exception {
		Path folder = dir("aad");
		Files.writeString(folder.resolve("main.json"), "{ \"a\": { \"password\": \"top-secret\" }, \"b\": { } }");
		KeyEncryptor enc = passwords();
		config(folder, enc);
		JsonObject stored = parse(Files.readString(folder.resolve("main.json")));
		String ct = (String)stored.getObject("a").get("password");
		assertTrue(ct, ct.startsWith("[[v3:"));
		// Moved to another key
		Files.writeString(folder.resolve("main.json"), "{ \"a\": { }, \"b\": { \"password\": \""+ct+"\" } }");
		try {
			config(folder, enc);
			fail("Exception expected");
		} catch(ConfigException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("b,password") || ex.getMessage().contains("b/password"));
			// The error does not leak the secret
			for(Throwable t=ex; t!=null; t=t.getCause()) {
				assertFalse(String.valueOf(t.getMessage()).contains("top-secret"));
			}
		}
	}

	// A v2 value is still decrypted, and encrypted again as v3 (bound to its key)
	public void testV2ValueIsReencrypted() throws Exception {
		// "secret" encrypted by the previous version (v2, no key path) with "akey"
		KeyEncryptor enc = passwords();
		String v2 = "[[v2:"+java.util.Base64.getEncoder().encodeToString(v2Payload("akey", "secret"))+"]]";
		assertTrue(enc.isEncrypted(v2));
		assertTrue(enc.needsReencryption(v2));
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"password\": \""+v2+"\" }")
								.encryptor(enc)
								.build();
		assertEquals("secret", c.getString("password"));
		String stored = (String)parse(c.getResourceAsString(null)).get("password");
		assertTrue(stored, stored.startsWith("[[v3:"));
		assertFalse(enc.needsReencryption(stored));
	}
	// The v2 format: salt(16) | iv(12) | AES-GCM, PBKDF2 65536 iterations, no AAD
	private static byte[] v2Payload(String password, String value) throws Exception {
		java.security.SecureRandom r = new java.security.SecureRandom();
		byte[] salt = new byte[16];
		byte[] iv = new byte[12];
		r.nextBytes(salt);
		r.nextBytes(iv);
		javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		byte[] k = f.generateSecret(new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 65536, 256)).getEncoded();
		javax.crypto.Cipher aes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
		aes.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(k, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv));
		byte[] ct = aes.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return java.nio.ByteBuffer.allocate(28+ct.length).put(salt).put(iv).put(ct).array();
	}

	// A plain value shaped like a legacy encrypted value can be stored
	public void testPlainValueShapedLikeLegacyCiphertext() throws Exception {
		String plain = "[[AAAAAAAAAAAAAAAAAAAAAA==]]";
		Path folder = dir("legacy-shape");
		Files.writeString(folder.resolve("main.json"), "{ \"password\": \"x\" }");
		KeyEncryptor enc = passwords();
		assertTrue(enc.isEncrypted(plain));
		JsonFileConfig c = config(folder, enc);
		assertTrue(c.updateValues((u) -> u.put("password", plain)));
		assertEquals(plain, c.getString("password"));
		// Stored encrypted, and read back as is
		String stored = (String)parse(Files.readString(folder.resolve("main.json"))).get("password");
		assertTrue(stored, stored.startsWith("[[v3:"));
		assertEquals(plain, config(folder, passwords()).getString("password"));

		// Without the legacy format, such a value in a file is a plain value
		Files.writeString(folder.resolve("main.json"), "{ \"password\": \""+plain+"\" }");
		KeyEncryptor noLegacy = new KeyEncryptor("akey", (k) -> k[k.length-1].equals("password"), false);
		assertFalse(noLegacy.isEncrypted(plain));
		assertEquals(plain, config(folder, noLegacy).getString("password"));
		stored = (String)parse(Files.readString(folder.resolve("main.json"))).get("password");
		assertTrue(stored, stored.startsWith("[[v3:"));
	}

	//
	// Several instances on one folder
	//

	public void testTwoInstancesOnOneFolder() throws Exception {
		Path folder = dir("two-instances");
		Files.writeString(folder.resolve("main.json"), "{ }");
		JsonFileConfig c1 = config(folder, null);
		JsonFileConfig c2 = config(folder, null);
		assertTrue(c1.updateValues((u) -> u.put("a", "1")));
		assertTrue(c2.updateValues((u) -> u.put("b", "2")));
		JsonObject stored = parse(Files.readString(folder.resolve("main.json")));
		assertEquals("1", stored.get("a"));
		assertEquals("2", stored.get("b"));
		assertEquals("1", c2.getString("a"));

		// Concurrent updates from several threads
		List<Thread> threads = new ArrayList<>();
		for(int i=0; i<10; i++) {
			int n = i;
			JsonFileConfig c = i%2==0 ? c1 : c2;
			threads.add(new Thread(() -> c.updateValues((u) -> u.put("k"+n, n))));
		}
		threads.forEach(Thread::start);
		for(Thread t: threads) {
			t.join();
		}
		stored = parse(Files.readString(folder.resolve("main.json")));
		for(int i=0; i<10; i++) {
			assertEquals(i, ((Number)stored.get("k"+i)).intValue());
		}
	}
}
