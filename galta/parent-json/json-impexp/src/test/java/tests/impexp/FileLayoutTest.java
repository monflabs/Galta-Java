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
package tests.impexp;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.JsonSource;
import org.monflabs.json.impexp.file.FileSource;
import org.monflabs.json.impexp.file.FileTarget;
import org.monflabs.json.impexp.file.ZipFileSource;
import org.monflabs.json.impexp.file.ZipInputStreamSource;
import org.monflabs.json.impexp.file.ZipTarget;
import org.monflabs.json.impexp.replication.impl.FileReplicationTable;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.FileUtil;

import tests.ProjectTestCase;

/**
 * Names on disk: collection and id encoding, round trips, ordering, and the file based
 * replication table.
 */
public class FileLayoutTest extends ProjectTestCase {

	private Path tmp;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		tmp = Files.createTempDirectory("impexp-layout");
	}
	@Override
	public void tearDown() throws Exception {
		FileUtil.deleteFile(tmp.toFile());
		super.tearDown();
	}

	private static JsonSource source(JsonContent...contents) {
		return tests.util.ListSource.of(contents);
	}
	private static StaticContent doc(String col, String id, int v) {
		return new StaticContent(JsonKey.of(col,id), JsonObject.of("v",v), null);
	}
	private static List<String> keys(JsonSource source) {
		try(Stream<JsonContent> s = source.stream(null)) {
			return s.map( (c) -> c.getKey().getCollection()+"|"+c.getKey().getId()+"|"+((JsonObject)c.getJson()).getInt("v") )
					.sorted().collect(Collectors.toList());
		}
	}
	private static final List<String> TRICKY_KEYS = List.of(
		"../../evil|1|1",
		"@x|@abc|4",
		"a/b|k|2",
		"|@root|3"      // JsonKey normalizes a null collection to ""
	);
	private static JsonSource trickySource() {
		return source(
			doc("../../evil","1",1),
			doc("a/b","k",2),
			doc(null,"@root",3),
			doc("@x","@abc",4)
		);
	}

	// A collection name cannot escape the root folder, and names round trip
	public void testFileTargetCollectionEncoding() throws Exception {
		File root = tmp.resolve("root").toFile();
		trickySource().exportTo(FileTarget.newBuilder().root(root).build());

		// Nothing was written outside the root
		try(Stream<Path> s = Files.list(tmp)) {
			assertEquals(List.of("root"), s.map( (p) -> p.getFileName().toString() ).collect(Collectors.toList()));
		}
		assertTrue(new File(root,"@..%2F..%2Fevil/1.json").isFile());
		assertTrue(new File(root,"@a%2Fb/k.json").isFile());
		assertTrue(new File(root,"%40root.json").isFile());
		assertTrue(new File(root,"@%40x/%40abc.json").isFile());

		assertEquals(TRICKY_KEYS, keys(FileSource.newBuilder().root(root).build()));
	}

	public void testFileTargetHashedCollectionEncoding() throws Exception {
		File root = tmp.resolve("root").toFile();
		trickySource().exportTo(FileTarget.newBuilder().root(root).subdir(2).build());
		assertEquals(TRICKY_KEYS, keys(FileSource.newBuilder().root(root).build()));
	}

	public void testZipCollectionEncoding() throws Exception {
		File zip = tmp.resolve("t.zip").toFile();
		trickySource().exportTo(ZipTarget.newBuilder().root(zip).build());
		assertEquals(TRICKY_KEYS, keys(ZipFileSource.newBuilder().zipFile(zip).build()));
		assertEquals(TRICKY_KEYS, keys(ZipInputStreamSource.newBuilder().zipInputStream( () -> {
			try {
				return new FileInputStream(zip);
			} catch(Exception ex) {
				throw new RuntimeException(ex);
			}
		}).build()));

		File zip2 = tmp.resolve("t2.zip").toFile();
		trickySource().exportTo(ZipTarget.newBuilder().root(zip2).subdir(3).build());
		assertEquals(TRICKY_KEYS, keys(ZipFileSource.newBuilder().zipFile(zip2).build()));
	}

	// Legacy zip files: a root entry whose name starts with '@' is a document, not an error
	public void testZipLegacyRootAtEntry() throws Exception {
		File zip = tmp.resolve("legacy.zip").toFile();
		try(java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
			zos.putNextEntry(new java.util.zip.ZipEntry("@abc.json"));
			zos.write("{\"v\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			zos.closeEntry();
		}
		assertEquals(List.of("|@abc|1"), keys(ZipFileSource.newBuilder().zipFile(zip).build()));
	}

	// FileSource returns the documents in name order
	public void testFileSourceOrder() throws Exception {
		File root = tmp.resolve("root").toFile();
		source(doc(null,"c",3), doc(null,"a",1), doc("z","y",5), doc(null,"b",2), doc("m","x",4))
			.exportTo(FileTarget.newBuilder().root(root).build());
		List<String> order = new ArrayList<>();
		try(Stream<JsonContent> s = FileSource.newBuilder().root(root).build().stream(null)) {
			s.forEach( (c) -> order.add(c.getKey().getId()) );
		}
		// Collection folders start with '@', which sorts before the letters
		assertEquals(List.of("x","y","a","b","c"), order);
	}

	public void testFileSourceMissingRoot() throws Exception {
		FileSource src = FileSource.newBuilder().root(tmp.resolve("nope").toFile()).build();
		try {
			src.stream(null);
			fail();
		} catch(JsonException ex) {
		}
	}

	public void testFileContentTimestamp() throws Exception {
		File root = tmp.resolve("root").toFile();
		Instant ts = Instant.parse("2020-01-02T03:04:05Z");
		source(new StaticContent(JsonKey.of(null,"k"), JsonObject.of("v",1), ts))
			.exportTo(FileTarget.newBuilder().root(root).keepTimestamp(true).build());
		try(Stream<JsonContent> s = FileSource.newBuilder().root(root).build().stream(null)) {
			JsonContent c = s.findFirst().get();
			assertEquals(ts, c.getTimestamp().truncatedTo(ChronoUnit.SECONDS));
			assertEquals(JsonContent.TYPE.RECORD, c.getType());
			assertEquals(1, ((JsonObject)c.getJson()).getInt("v"));
		}
	}

	// The replication date is kept per source AND per target
	public void testFileReplicationTable() throws Exception {
		Path file = tmp.resolve("sub/dir/replication.json");
		FileReplicationTable table = new FileReplicationTable(file);
		assertNull(table.lastReplication("s", "t1"));

		Instant t1 = Instant.parse("2021-01-01T00:00:00Z");
		Instant t2 = Instant.parse("2022-01-01T00:00:00Z");
		table.saveReplication("s", "t1", t1, null);
		table.saveReplication("s", "t2", t2, null);
		assertEquals(t1, table.lastReplication("s", "t1"));
		assertEquals(t2, table.lastReplication("s", "t2"));
		assertNull(table.lastReplication("other", "t1"));

		// Persisted: a new instance reads the same values
		FileReplicationTable table2 = new FileReplicationTable(file);
		assertEquals(t1, table2.lastReplication("s", "t1"));
		assertEquals(t2, table2.lastReplication("s", "t2"));

		// Null ids are supported
		table2.saveReplication(null, null, t1, null);
		assertEquals(t1, table2.lastReplication(null, null));

		// Removing a date
		table2.setReplication("s", "t1", null);
		assertNull(table2.lastReplication("s", "t1"));
		assertEquals(t2, table2.lastReplication("s", "t2"));
	}

	// A bare file name (no parent folder) must not fail
	public void testFileReplicationTableBareFileName() throws Exception {
		String name = "replication-"+System.nanoTime()+".json";
		FileReplicationTable table = new FileReplicationTable(Path.of(name));
		try {
			Instant t = Instant.parse("2021-01-01T00:00:00Z");
			table.saveReplication("s", "t", t, null);
			assertEquals(t, table.lastReplication("s", "t"));
		} finally {
			Files.deleteIfExists(table.getFile());
		}
	}
}
