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

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.config.AbstractJsonConfig;
import org.monflabs.json.config.JsonFileConfig;
import org.monflabs.json.config.KeyEncryptor;
import org.monflabs.util.config.Config;
import org.monflabs.util.config.Config.ENUM_KEYS;
import org.monflabs.util.config.ConfigException;
import org.monflabs.util.iterators.Iterators;

import tests.ProjectTestCase;

public class JsonFileConfigTest extends ProjectTestCase {

	public void testLoad() throws Exception {
		File f = support.getTestResourcesDirectory("config");
		JsonFileConfig c = JsonFileConfig.newBuilder()
				.folder(f.toPath())
				.fileName("read.json")
				.readOnly(true)
				.build();
		
		support.assertJsonTemplate(c.getContent(), "read.json");
	}
	
	public void testRead() throws Exception {
		File f = support.getTestResourcesDirectory("config");
		JsonFileConfig c = JsonFileConfig.newBuilder()
				.folder(f.toPath())
				.fileName("read.json")
				.readOnly(true)
				.build();
		
		assertTrue (c.has("date"));
		assertFalse(c.has("date1"));
		assertTrue (c.has("computer/display/hres"));
		assertFalse(c.has("computer/display/hres1"));
		
		assertEquals( "2020-12-25", c.getString("date"));
		assertEquals( "1920", c.getString("computer/display/hres"));
		assertEquals( "1080", c.getString("computer/display/vres"));
		assertEquals( "en", c.getString("computer/language"));
		assertEquals( "true", c.getString("computer/connected"));
		assertEquals( "false", c.getString("computer/vpn"));
		
		assertTrue ( c.isValue("date") );
		assertTrue ( c.isValue("computer/language") );
		assertFalse( c.isFolder("") );
		assertFalse( c.isFolder("date2") );
		assertFalse( c.isFolder("date") );
		assertFalse( c.isFolder("computer/language") );
		
		assertTrue ( c.isFolder("computer") );
		assertTrue ( c.isFolder("computer/display") );
		assertFalse( c.isValue("") );
		assertFalse( c.isValue("computer") );
		assertFalse( c.isValue("computer2") );
		assertFalse( c.isValue("computer/display") );
	}
	
	public void testReadPrimitiveTypes() throws Exception {
		File f = support.getTestResourcesDirectory("config");
		JsonFileConfig c = JsonFileConfig.newBuilder()
				.folder(f.toPath())
				.fileName("types.json")
				.readOnly(true)
				.build();
		
		assertEquals( "79", c.getString("i1"));
		assertEquals( 79, c.getInt("i1"));
		assertEquals( "79", c.getString("i2"));
		assertEquals( 79, c.getInt("i2"));
		
		assertEquals( "-34", c.getString("l1"));
		assertEquals( -34, c.getLong("l1"));
		assertEquals( "-34", c.getString("l2"));
		assertEquals( -34, c.getLong("l2"));
		
		assertEquals( "79.34", c.getString("d1"));
		assertEquals( 79.34, c.getDouble("d1"));
		assertEquals( "79.34", c.getString("d2"));
		assertEquals( 79.34, c.getDouble("d2"));
		
		assertEquals( "true", c.getString("b1"));
		assertEquals( true, c.getBoolean("b1"));
		assertEquals( "true", c.getString("b2"));
		assertEquals( true, c.getBoolean("b2"));
	}
	
	public void testBrowseKeys() throws Exception {
		File f = support.getTestResourcesDirectory("config");
		JsonFileConfig c = JsonFileConfig.newBuilder()
				.folder(f.toPath())
				.fileName("keys.json")
				.readOnly(true)
				.build();
		
		assertArrayEquals( new String[] {}, readKey(c, "", ENUM_KEYS.ALL));
		assertArrayEquals( new String[] {}, readKey(c, "", ENUM_KEYS.VALUES));
		assertArrayEquals( new String[] {}, readKey(c, "", ENUM_KEYS.FOLDERS));
		
		assertArrayEquals( new String[] {"b","c","d","e"}, readKey(c, "a", ENUM_KEYS.ALL));
		assertArrayEquals( new String[] {"c","d"}, readKey(c, "a", ENUM_KEYS.VALUES));
		assertArrayEquals( new String[] {"b","e"}, readKey(c, "a", ENUM_KEYS.FOLDERS));
		
		assertArrayEquals( new String[] {"a"}, readKey(c, "b", ENUM_KEYS.ALL));
		assertArrayEquals( new String[] {}, readKey(c, "b", ENUM_KEYS.VALUES));
		assertArrayEquals( new String[] {"a"}, readKey(c, "b", ENUM_KEYS.FOLDERS));

		assertArrayEquals( new String[] {"a"}, readKey(c, "c", ENUM_KEYS.ALL));
		assertArrayEquals( new String[] {"a"}, readKey(c, "c", ENUM_KEYS.VALUES));
		assertArrayEquals( new String[] {}, readKey(c, "c", ENUM_KEYS.FOLDERS));
		
		assertArrayEquals( new String[] {}, readKey(c, "d", ENUM_KEYS.ALL));
		assertArrayEquals( new String[] {}, readKey(c, "d", ENUM_KEYS.VALUES));
		assertArrayEquals( new String[] {}, readKey(c, "d", ENUM_KEYS.FOLDERS));
	}
	private String[] readKey(JsonFileConfig c, String key, Config.ENUM_KEYS type) {
		List<String> val = Iterators.collect(c.keysOf(key,type));
		String[] a = val.toArray(new String[val.size()]);
		Arrays.sort(a);
		return a;
	}
	
	public void testWrite() throws Exception {
		String content = support.loadText("config/write.json");		
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content(content)
								.build();
		
		assertTrue (c.has("a"));
		assertFalse(c.has("b"));
		
		c.updateValues( (updater) -> {
			assertTrue ( updater.put("b", "123" ) );
			assertTrue ( updater.put("z/x/y", "321" ) );
			assertFalse( updater.put("f", "123" ) );
		});
		assertEquals("123", c.getString("b"));
		assertEquals("321", c.getString("z/x/y"));
		assertTrue  (c.has("f"));
		assertTrue  (c.isFolder("f") );
		
		c.updateValues( (updater) -> {
			assertTrue ( updater.put("b", "456" ) );
			updater.cancel();
		});
		assertTrue  (c.has("b"));
		assertEquals("123", c.getString("b"));
		
		c.updateValues( (updater) -> {
			assertTrue ( updater.remove("b" ) );
		});
		assertFalse(c.has("b"));

		c.updateValues( (updater) -> {
			assertTrue ( updater.remove("z/x" ) );
		});
		assertFalse(c.has("z/x/y"));
		assertFalse(c.has("z/x"));
		assertTrue (c.has("z"));
	}
	
	public void testRemoveMissing() throws Exception {
		String content = support.loadText("config/write.json");		
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content(content)
								.build();
		assertEquals("xyz", c.getString("a"));
		assertEquals("zyx", c.getString("f/a"));
		
		// Removing under a missing intermediate folder must not remove anything
		// from a parent folder, and must not mark the config as changed
		assertFalse( c.updateValues( (updater) -> {
			assertFalse( updater.remove("f/x/a" ) );
			assertFalse( updater.remove("q/a" ) );
			assertFalse( updater.remove("f/missing" ) );
			assertFalse( updater.remove("missing" ) );
			assertFalse( updater.remove("a/b" ) );
		}));
		assertEquals("xyz", c.getString("a"));
		assertEquals("zyx", c.getString("f/a"));
		assertTrue (c.isFolder("f"));
		
		// A real removal is still reported
		assertTrue( c.updateValues( (updater) -> {
			assertTrue( updater.remove("f/a" ) );
		}));
		assertFalse(c.has("f/a"));
		assertTrue (c.isFolder("f"));
	}
	
	public void testSaveFailure() throws Exception {
		String content = support.loadText("config/write.json");		
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content(content)
								.failOnSave(true)
								.build();
		try {
			c.save();
			fail("save() should have failed");
		} catch(ConfigException ex) {
			// expected
		}
		// An auto-saved update must surface the failure to the caller
		try {
			c.updateValues( (updater) -> {
				assertTrue( updater.put("b", "123" ) );
			});
			fail("updateValues() should have failed");
		} catch(ConfigException ex) {
			// expected
		}
	}
	
	public void testSave() throws Exception {
		File src = support.getTestResourcesDirectory("config/write.json");
		File tgt = new File( support.getTargetTempDirectory(), "write.json");
		tgt.getParentFile().mkdirs();
		tgt.delete();
		Files.copy(src.toPath(), tgt.toPath());
		
		JsonFileConfig c = JsonFileConfig.newBuilder()
				.folder(tgt.getParentFile().toPath())
				.fileName("write.json")
				.build();
		assertFalse(c.has("b"));
		c.updateValues( (updater) -> {
			assertTrue ( updater.put("b", "123" ) );
		});
		assertEquals("123", c.getString("b"));
		
		JsonFileConfig c2 = JsonFileConfig.newBuilder()
				.folder(tgt.getParentFile().toPath())
				.fileName("write.json")
				.build();
		assertTrue(c2.has("b"));
		assertEquals("123", c2.getString("b"));

	}
	
	public void testResources() throws Exception {
		File tgt = new File( support.getTargetTempDirectory(), "write.json");
		tgt.getParentFile().mkdirs();
		tgt.delete();
		JsonFileConfig c = JsonFileConfig.newBuilder()
				.folder(tgt.getParentFile().toPath())
				.fileName("write.json")
				.build();
		
		String s = "My String"+Math.random();
		c.setResource("a.txt", s);
		assertEquals(s,c.getResourceAsString("a.txt"));
	}
	
	public void testReferences() throws Exception {
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		String content = 
				""" 
					{"$ref": "toto.json" } 
				""";		
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content(content)
								.resource("toto.json", "{ name: 'Joe Dalton'}")
								.build();
		
		assertEquals("Joe Dalton",c.getString("name"));
	}

	
	public void testReferencesSurviveUpdates() throws Exception {
		// An update used to deep-clone the content without its references: the main
		// resource was saved with the referenced content inlined and the referenced
		// resource was never updated
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content("{ \"a\": \"1\", \"db\": { \"$ref\": \"db.json\" } }")
								.resource("db.json", "{ \"host\": \"h\", \"password\": \"p\" }")
								.encryptor(new KeyEncryptor("akey", (k) -> k[k.length-1].equals("password")))
								.build();
		assertEquals("h", c.getString("db/host"));
		// The referenced resource is decrypted/encrypted like the main one
		assertEquals("p", c.getString("db/password"));
		JsonObject db = (JsonObject)JsonFactory.get().parse(c.getResourceAsString("db.json"));
		assertTrue(((String)db.get("password")).startsWith("[["));
		
		assertTrue( c.updateValues( (u) -> u.put("db/host", "h2") ) );
		JsonObject main = (JsonObject)JsonFactory.get().parse(c.getResourceAsString(null));
		assertEquals("db.json", ((JsonObject)main.get("db")).get("$ref"));
		db = (JsonObject)JsonFactory.get().parse(c.getResourceAsString("db.json"));
		assertEquals("h2", db.get("host"));
		assertTrue(((String)db.get("password")).startsWith("[["));
		assertEquals("p", c.getString("db/password"));
		
		// A root reference is kept as well
		InMemoryConfig r = InMemoryConfig.newBuilder()
								.content("{ \"$ref\": \"all.json\" }")
								.resource("all.json", "{ \"x\": \"1\" }")
								.build();
		assertTrue( r.updateValues( (u) -> u.put("x", "2") ) );
		main = (JsonObject)JsonFactory.get().parse(r.getResourceAsString(null));
		assertEquals("all.json", main.get("$ref"));
		assertEquals("2", ((JsonObject)JsonFactory.get().parse(r.getResourceAsString("all.json"))).get("x"));
	}
	
	public void testEncrypt() throws Exception {
		String content = support.loadText("config/encrypt.json");
		
		KeyEncryptor enc = new KeyEncryptor("akey", (k) -> k[k.length-1].equals("password"));
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content(content)
								.encryptor(enc)
								.build();
		
		support.assertJsonTemplate(c.getContent().stringify(false), "notencrypted.json");
		assertTrue( c.save() );
		support.assertJsonTemplate(c.getContent().stringify(false), "notencrypted.json");
		assertStoredEncrypted(enc, c.getResourceAsString(null));

		// A resource written with the legacy format (AES-CBC, zero IV) is still readable,
		// and is re-encrypted with the current format when saved
		String content2 = support.loadText("config/encrypted.json");
		InMemoryConfig c2 = InMemoryConfig.newBuilder()
								.content(content2)
								.encryptor(enc)
								.build();
		support.assertJsonTemplate(c2.getContent().stringify(false), "notencrypted.json");
		assertTrue( c2.save() );
		support.assertJsonTemplate(c2.getContent().stringify(false), "notencrypted.json");
		assertStoredEncrypted(enc, c2.getResourceAsString(null));
	}
	private static void assertStoredEncrypted(KeyEncryptor enc, String stored) {
		JsonObject o = (JsonObject)JsonFactory.get().parse(stored);
		assertEquals("12", o.get("b"));
		assertEquals("56", o.get("passw0rd"));
		String p1 = (String)o.get("password");
		String p2 = (String)((JsonObject)o.get("c")).get("password");
		assertTrue(p1, p1.startsWith("[[v3:"));
		assertTrue(p2, p2.startsWith("[[v3:"));
		assertEquals("34", enc.decrypt(new String[] {"password"}, p1));
		assertEquals("78", enc.decrypt(new String[] {"c","password"}, p2));
	}
	
	public void testEncryptNestedKeyPath() throws Exception {
		String content = 
				"""
					{ "password": "top", "user": "joe", "db": { "password": "nested", "host": "h", "x": { "password": "deep" } } }
				""";
		Set<String> seen = new HashSet<>();
		KeyEncryptor enc = new KeyEncryptor("akey", (k) -> {
			String path = String.join("/", k);
			seen.add(path);
			return path.equals("db/password");
		});
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content(content)
								.encryptor(enc)
								.build();
		
		// The predicate must see the full key path of every value
		assertEquals(new HashSet<>(Arrays.asList("password","user","db/password","db/host","db/x/password")), seen);
		
		// In memory, everything is decrypted
		assertEquals("top",    c.getString("password"));
		assertEquals("nested", c.getString("db/password"));
		assertEquals("deep",   c.getString("db/x/password"));
		
		// Only the selected nested key is encrypted in the stored resource
		JsonObject stored = (JsonObject)JsonFactory.get().parse(c.getResourceAsString(null));
		assertEquals("top", stored.get("password"));
		assertEquals("deep", ((JsonObject)((JsonObject)stored.get("db")).get("x")).get("password"));
		String storedNested = (String)((JsonObject)stored.get("db")).get("password");
		assertTrue(enc.isEncrypted(storedNested));
		assertEquals("nested", enc.decrypt(new String[] {"db","password"}, storedNested));
		
		// Same after an explicit save
		assertTrue( c.save() );
		stored = (JsonObject)JsonFactory.get().parse(c.getResourceAsString(null));
		assertEquals("top", stored.get("password"));
		assertTrue(enc.isEncrypted((String)((JsonObject)stored.get("db")).get("password")));
	}
	
	
	public void testSubConfig() throws Exception {
		String content = support.loadText("config/read.json");		
		InMemoryConfig c = InMemoryConfig.newBuilder()
								.content(content)
								.build();

		assertTrue (c.has("computer/display/hres"));
		assertFalse(c.has("computer/display/hres1"));
		assertEquals( "1920", c.getString("computer/display/hres"));

		Config c1 = c.subConfig("computer");
		assertTrue (c1.has("display/hres"));
		assertFalse(c1.has("display/hres1"));
		assertEquals( "1920", c1.getString("display/hres"));
		c1.updateValues( (u) -> {
			u.put("display/hres",1921);
		});

		Config c2 = c1.subConfig("display");
		assertTrue (c2.has("hres"));
		assertFalse(c2.has("hres1"));
		assertEquals( "1921", c2.getString("hres"));
		c2.updateValues( (u) -> {
			u.put("hres",1922);
		});

		Config c22 = c.subConfig("computer/display");
		assertTrue (c22.has("hres"));
		assertFalse(c22.has("hres1"));
		assertEquals( "1922", c22.getString("hres"));
		c2.updateValues( (u) -> {
			u.put("hres",1923);
		});
		assertEquals( "1923", c22.getString("hres"));
	}
	
	public static class InMemoryConfig extends AbstractJsonConfig {

		public static class Builder extends ConfigBuilder<InMemoryConfig,Builder> {
			private Map<String,byte[]> resources = new HashMap<>();
			private boolean failOnSave;
			private Builder() {
			}
			public Builder failOnSave(boolean failOnSave) {
				this.failOnSave = failOnSave;
				return this;
			}
			public Builder content(String content) {
				this.resources.put(null, content.getBytes());
				return this;
			}
			public Builder resource(String name, String content) {
				resources.put(name, content.getBytes());
				return this;
			}
			@Override
			protected InMemoryConfig _build() {
				return new InMemoryConfig(this);
			}
		}
		public static Builder newBuilder() {
			return new Builder();
		}

		private Map<String,byte[]> resources;
		private boolean failOnSave;

		private InMemoryConfig(Builder b) {
			super(b);
			this.resources = b.resources;
			this.failOnSave = b.failOnSave;
			load();
		}

		@Override
		public boolean isReadOnly() {
			return false;
		}
		
		@Override
		public InputStream getResource(String path) {
			byte[] b = resources.get(path);
			if(b!=null) {
				return new ByteArrayInputStream(b);
			}
			return null;
		}
		@Override
		public void setResource(String path, Consumer<OutputStream> save) {
			if(isReadOnly()) {
				throw new ConfigException(null,"Config is readonly");
			}
			if(failOnSave) {
				throw new IllegalStateException("Simulated storage failure");
			}
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			save.accept(os);
			resources.put(path, os.toByteArray());
		}
	}

}
