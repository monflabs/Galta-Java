package doc_examples.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.config.CustomJsonConfig;
import org.monflabs.json.config.JsonFileConfig;
import org.monflabs.json.config.KeyEncryptor;
import org.monflabs.util.config.Config;
import org.monflabs.util.config.ConfigException;
import org.monflabs.util.iterators.Iterators;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/Config.md
 */
public class ConfigExamples extends ProjectTestCase {

	/** The temporary files go to target/temp (removed by mvn clean), not to the system temp folder. */
	private Path tempRoot() {
		return support.getTargetTempDirectory().toPath();
	}

	private static JsonObject readJson(Path p) throws Exception {
		return (JsonObject)JsonFactory.get().parse(Files.readString(p));
	}

	// ------------------------------------------------------------------
	// Loading and reading
	// ------------------------------------------------------------------

	public void testFileConfig() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config");
		Files.writeString(folder.resolve("app.json"), """
				{
				  "server": { "host": "localhost", "port": 8080, "secure": "true" },
				  "timeout": 2.5,
				  "tags": ["a", "b"]
				}
				""");

		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.build();

		assertEquals("localhost", config.getString("server/host"));
		assertEquals(8080, config.getInt("server/port"));
		assertTrue(config.getBoolean("server/secure"));        // strings are converted
		assertEquals(2.5, config.getDouble("timeout"));
		assertEquals("8080", config.getString("server/port"));  // any value reads as a string
		assertEquals(30, config.getInt("server/retries", 30));  // default when missing
		assertNull(config.getString("server"));                // a folder is not a value
		assertNull(config.getValue("tags"));                   // neither is an array
		assertEquals("a", config.getContent().getArray("tags").getString(0));   // raw JSON
	}

	public void testBrowse() throws Exception {
		CustomJsonConfig config = CustomJsonConfig.newBuilder()
				.resourceReader(path -> path == null
						? new ByteArrayInputStream("""
							{ "server": { "host": "h", "port": 1, "tls": { "enabled": true } } }
							""".getBytes(StandardCharsets.UTF_8))
						: null)
				.build();

		assertTrue(config.has("server/tls/enabled"));
		assertTrue(config.isFolder("server/tls"));
		assertTrue(config.isValue("server/port"));
		assertEquals(List.of("host", "port", "tls"), Iterators.collect(config.keysOf("server")));
		assertEquals(List.of("host", "port"), Iterators.collect(config.keysOf("server", Config.ENUM_KEYS.VALUES)));
		assertEquals(List.of("tls"), Iterators.collect(config.keysOf("server", Config.ENUM_KEYS.FOLDERS)));
		assertFalse(config.keysOf("").hasNext());               // the root cannot be listed by key

		Config server = config.subConfig("server");
		assertEquals("h", server.getString("host"));
		assertTrue(server.getBoolean("tls/enabled"));
	}

	// ------------------------------------------------------------------
	// Updates
	// ------------------------------------------------------------------

	public void testUpdates() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config");
		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")                              // does not exist yet
				.build();
		assertFalse(config.has("server/host"));

		boolean changed = config.updateValues(u -> {
			u.put("server/host", "example.com");                   // folders are created
			u.put("server/port", 443);
			u.put("debug", false);
		});
		assertTrue(changed);
		assertEquals(443, config.getInt("server/port"));

		// Auto-save wrote the file; typed values keep their JSON type
		JsonObject saved = readJson(folder.resolve("app.json"));
		assertEquals(443, ((Number)saved.getObject("server").get("port")).intValue());
		assertEquals(Boolean.FALSE, saved.get("debug"));

		// All or nothing: a cancelled update changes nothing
		assertFalse(config.updateValues(u -> {
			u.put("server/host", "other.com");
			u.cancel();
		}));
		assertEquals("example.com", config.getString("server/host"));

		// put() refuses to replace a folder, or to go through a value
		config.updateValues(u -> {
			assertFalse(u.put("server", "x"));
			assertFalse(u.put("debug/level", "1"));
			assertTrue(u.remove("debug"));
		});
		assertFalse(config.has("debug"));
	}

	public void testNoAutoSave() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config");
		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.autoSave(false)
				.build();

		config.updateValues(u -> u.put("a", "1"));
		assertEquals("1", config.getString("a"));
		assertFalse(Files.exists(folder.resolve("app.json")));   // only in memory

		assertTrue(config.save());
		assertEquals("1", readJson(folder.resolve("app.json")).get("a"));
	}

	// ------------------------------------------------------------------
	// Read-only configurations
	// ------------------------------------------------------------------

	public void testReadOnly() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config");
		Files.writeString(folder.resolve("app.json"), "{ \"a\": \"1\" }");
		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.readOnly(true)
				.build();

		assertTrue(config.isReadOnly());
		assertFalse(config.save());                              // nothing to do
		try {
			config.updateValues(u -> u.put("b", "2"));
			fail();
		} catch(ConfigException e) {
			// Storage is readonly
		}
	}

	public void testCustomConfig() throws Exception {
		Map<String,byte[]> store = new HashMap<>();              // resource name -> bytes; null is the main one
		store.put(null, "{ \"a\": \"1\" }".getBytes(StandardCharsets.UTF_8));

		Function<String,InputStream> reader = path -> {
			byte[] b = store.get(path);
			return b != null ? new ByteArrayInputStream(b) : null;
		};
		BiFunction<String,Consumer<OutputStream>,InputStream> writer = (path, save) -> {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			save.accept(os);
			store.put(path, os.toByteArray());
			return null;
		};

		// Without a writer, the configuration is read-only
		CustomJsonConfig readOnly = CustomJsonConfig.newBuilder()
				.resourceReader(reader)
				.build();
		assertTrue(readOnly.isReadOnly());
		assertEquals("1", readOnly.getString("a"));

		CustomJsonConfig config = CustomJsonConfig.newBuilder()
				.resourceReader(reader)
				.resourceWriter(writer)
				.build();
		config.updateValues(u -> u.put("b", "2"));
		assertTrue(new String(store.get(null), StandardCharsets.UTF_8).contains("\"b\":\"2\""));

		// Other named resources go through the same functions
		config.setResource("notes.txt", "hello");
		assertEquals("hello", config.getResourceAsString("notes.txt"));
	}

	// ------------------------------------------------------------------
	// $ref
	// ------------------------------------------------------------------

	public void testReferences() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config");
		Files.writeString(folder.resolve("app.json"), """
				{ "name": "demo", "db": { "$ref": "db.json" } }
				""");
		Files.writeString(folder.resolve("db.json"), """
				{ "host": "localhost", "port": 5432 }
				""");

		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.build();
		assertEquals("localhost", config.getString("db/host"));

		// Values read through a reference are written back to the referenced resource
		config.updateValues(u -> u.put("db/host", "db.internal"));
		assertEquals(JsonObject.of("name", "demo", "db", JsonObject.of("$ref", "db.json")),
				readJson(folder.resolve("app.json")));
		assertEquals("db.internal", readJson(folder.resolve("db.json")).get("host"));

		JsonFileConfig reloaded = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.build();
		assertEquals("db.internal", reloaded.getString("db/host"));
	}

	public void testReferenceLimits() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config");
		Files.writeString(folder.resolve("app.json"), """
				{ "db": { "$ref": "common.json#/db" }, "nested": { "$ref": "outer.json" } }
				""");
		Files.writeString(folder.resolve("common.json"), """
				{ "db": { "host": "h" }, "other": 1 }
				""");
		Files.writeString(folder.resolve("outer.json"), """
				{ "inner": { "$ref": "other.json" } }
				""");
		Files.writeString(folder.resolve("other.json"), """
				{ "other": 1 }
				""");
		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.build();
		assertEquals("h", config.getString("db/host"));                   // fragment: read
		assertEquals(1, config.getInt("nested/inner/other"));              // nested: followed

		config.updateValues(u -> u.put("db/host", "h2"));
		assertEquals("h2", config.getString("db/host"));
		JsonObject common = readJson(folder.resolve("common.json"));
		assertEquals("h2", common.getObject("db").get("host"));                     // written back to its part
		assertEquals(1, common.getInt("other"));                                     // the rest is kept
		assertEquals("common.json#/db", readJson(folder.resolve("app.json")).getObject("db").get("$ref"));
	}

	// ------------------------------------------------------------------
	// Encryption
	// ------------------------------------------------------------------

	public void testEncryption() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config");
		Files.writeString(folder.resolve("app.json"), """
				{ "user": "joe", "password": "top", "db": { "password": "s3cret", "host": "h" } }
				""");

		KeyEncryptor encryptor = new KeyEncryptor("my-master-key",
				keys -> keys[keys.length - 1].equals("password"));    // keys = full path, e.g. [db, password]

		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.encryptor(encryptor)
				.build();

		// In memory, values are in clear
		assertEquals("s3cret", config.getString("db/password"));

		// Loading a writable configuration encrypted the matching values in the file
		JsonObject stored = readJson(folder.resolve("app.json"));
		String enc = stored.getObject("db").getString("password");
		assertTrue(enc.startsWith("[[") && enc.endsWith("]]"));
		assertEquals("joe", stored.getString("user"));
		assertEquals("s3cret", encryptor.decrypt(new String[] {"db", "password"}, enc));  // bound to its key path

		// Updated values are encrypted when saved
		config.updateValues(u -> u.put("password", "new"));
		assertTrue(encryptor.isEncrypted(readJson(folder.resolve("app.json")).getString("password")));
		assertEquals("new", config.getString("password"));
	}

	public void testEncryptorStandalone() throws Exception {
		KeyEncryptor encryptor = new KeyEncryptor("my-master-key", keys -> true);
		String enc = encryptor.encryptValue("hello");
		assertTrue(encryptor.isEncrypted(enc));                  // "[[v3:" + base64 + "]]"
		assertEquals("hello", encryptor.decryptValue(enc));
		assertFalse(enc.equals(encryptor.encryptValue("hello"))); // random IV: never the same text
	}

	public void testMissingFolder() throws Exception {
		Path folder = Files.createTempDirectory(tempRoot(), "galta-config").resolve("sub/dir");
		JsonFileConfig config = JsonFileConfig.newBuilder()
				.folder(folder)
				.fileName("app.json")
				.build();
		assertTrue(Files.isDirectory(folder));                   // created by the builder
		assertTrue(config.getContent().isEmpty());
		config.setResource("certs/ca.pem", "---");
		assertEquals("---", Files.readString(folder.resolve("certs/ca.pem")));
	}
}
