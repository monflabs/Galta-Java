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

import static org.junit.Assert.assertThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.container.StreamSource;
import org.monflabs.json.impexp.file.FileSource;
import org.monflabs.json.impexp.file.FileTarget;
import org.monflabs.json.impexp.file.ZipFileSource;
import org.monflabs.json.impexp.file.ZipInputStreamSource;
import org.monflabs.json.impexp.file.ZipTarget;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.impl.FileReplicationTable;
import org.monflabs.json.impexp.util.FileNameUtil;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.FileUtil;

import tests.ProjectTestCase;
import tests.util.ListSource;

/**
 * Crash safety, reentrancy, range filters and error reporting of the sources and targets.
 */
public class ImpExpRobustnessTest extends ProjectTestCase {

	private static JsonContent record(String col, String id) {
		return new StaticContent(JsonKey.of(col,id), JsonObject.of("id",id), null);
	}
	private static JsonContent record(String col, String id, Instant ts) {
		return new StaticContent(JsonKey.of(col,id), JsonObject.of("id",id), ts);
	}
	// A record that fails when it is written
	private static JsonContent failing(String col, String id) {
		return new JsonContent() {
			@Override
			public TYPE getType() {
				return TYPE.RECORD;
			}
			@Override
			public JsonKey getKey() {
				return JsonKey.of(col,id);
			}
			@Override
			public Object getJson() {
				throw new IllegalStateException("boom");
			}
		};
	}

	private File dir(String name) {
		File d = support.getProjectDirectory("target/tests/json-robust/"+name);
		FileUtil.deleteFile(d);
		d.getParentFile().mkdirs();
		return d;
	}
	private static List<String> keys(Stream<JsonContent> s) {
		try(s) {
			return s.map((c) -> c.getKey().keyString()).collect(Collectors.toList());
		}
	}

	//
	// Crash safety of the exports
	//

	public void testZipTargetFailureKeepsPreviousZip() throws Exception {
		File zip = new File(dir("zip-fail"),"export.zip");
		zip.getParentFile().mkdirs();
		ZipTarget target = ZipTarget.newBuilder().root(zip).build();
		target.importFrom(ListSource.of(record("c","1"), record("c","2")));
		byte[] before = Files.readAllBytes(zip.toPath());

		JsonException ex = assertThrows(JsonException.class, () -> target.importFrom(ListSource.of(record("c","3"), failing("c","4"))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("c:4"));
		// The previous zip is untouched, and no temporary file is left
		assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(zip.toPath())));
		assertEquals(List.of("export.zip"), List.of(zip.getParentFile().list()));
		assertEquals(List.of("c!!1","c!!2"), keys(ZipFileSource.newBuilder().zipFile(zip).build().stream()));

		// A successful import replaces it
		target.importFrom(ListSource.of(record("c","5")));
		assertEquals(List.of("c!!5"), keys(ZipFileSource.newBuilder().zipFile(zip).build().stream()));
		assertEquals(List.of("export.zip"), List.of(zip.getParentFile().list()));
	}

	public void testZipTargetRootIsADirectory() throws Exception {
		File d = dir("zip-dir");
		d.mkdirs();
		ZipTarget target = ZipTarget.newBuilder().root(d).build();
		assertThrows(JsonException.class, () -> target.importFrom(ListSource.of(record("c","1"))));
		assertTrue(d.isDirectory());
	}

	public void testFileTargetFailureKeepsPreviousContent() throws Exception {
		File root = dir("file-fail");
		FileTarget target = FileTarget.newBuilder().root(root).build();
		target.importFrom(ListSource.of(record("c","1"), record("c","2")));

		assertThrows(JsonException.class, () -> target.importFrom(ListSource.of(record("c","3"), failing("c","4"))));
		// The previous export is untouched, and the staging folder is removed
		assertEquals(List.of("c!!1","c!!2"), keys(FileSource.newBuilder().root(root).build().stream()));
		assertEquals(List.of(root.getName()), List.of(root.getParentFile().list((d,n) -> n.contains("file-fail"))));

		target.importFrom(ListSource.of(record("c","5")));
		assertEquals(List.of("c!!5"), keys(FileSource.newBuilder().root(root).build().stream()));
		assertEquals(List.of(root.getName()), List.of(root.getParentFile().list((d,n) -> n.contains("file-fail"))));
	}

	public void testFileTargetIncrementalWritesInPlace() throws Exception {
		File root = dir("file-incremental");
		FileTarget target = FileTarget.newBuilder().root(root).clearOnStart(false).build();
		target.importFrom(ListSource.of(record("c","1")));
		target.importFrom(ListSource.of(record("c","2")));
		assertEquals(List.of("c!!1","c!!2"), keys(FileSource.newBuilder().root(root).build().stream()));
		// No temporary file left in the collection folder
		assertEquals(List.of("1.json","2.json"), List.of(new File(root,"@c").list()).stream().sorted().toList());
	}

	public void testFileTargetRootIsARegularFile() throws Exception {
		File d = dir("file-root");
		d.mkdirs();
		File root = new File(d,"data.txt");
		Files.writeString(root.toPath(), "precious");
		FileTarget target = FileTarget.newBuilder().root(root).build();
		JsonException ex = assertThrows(JsonException.class, () -> target.importFrom(ListSource.of(record("c","1"))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("not a directory"));
		// The file is not deleted
		assertEquals("precious", Files.readString(root.toPath()));
	}

	//
	// Same id in two collections, when the collections are ignored
	//

	public void testDuplicatePathsWithIgnoredCollections() throws Exception {
		File root = dir("dup-file");
		FileTarget ft = FileTarget.newBuilder().root(root).ignoreCollection(true).build();
		JsonException ex = assertThrows(JsonException.class, () -> ft.importFrom(ListSource.of(record("a","1"), record("b","1"))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("overwrite"));

		File zip = new File(dir("dup-zip"),"dup.zip");
		zip.getParentFile().mkdirs();
		ZipTarget zt = ZipTarget.newBuilder().root(zip).ignoreCollection(true).build();
		ex = assertThrows(JsonException.class, () -> zt.importFrom(ListSource.of(record("a","1"), record("b","1"))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("Duplicate document"));
		assertFalse(zip.exists());
	}

	//
	// File names
	//

	public void testWindowsReservedNames() throws Exception {
		for(String id: List.of("CON","con","NUL.txt","com1","LPT9","abc.","abc ")) {
			String enc = FileNameUtil.encodeFilename(id);
			assertFalse(enc, enc.equalsIgnoreCase(id));
			assertEquals(id, FileNameUtil.decodeFilename(enc));
		}
		// Regular names are not changed
		assertEquals("CONSOLE", FileNameUtil.encodeFilename("CONSOLE"));
		assertEquals("a.b", FileNameUtil.encodeFilename("a.b"));

		File root = dir("reserved");
		FileTarget.newBuilder().root(root).build().importFrom(ListSource.of(record("NUL","CON"), record("c","aux.json")));
		assertEquals(List.of("NUL!!CON","c!!aux.json"), keys(FileSource.newBuilder().root(root).build().stream()).stream().sorted().toList());
	}

	public void testIdTooLongForAFileName() throws Exception {
		File root = dir("long-id");
		String id = "x".repeat(300);
		FileTarget target = FileTarget.newBuilder().root(root).build();
		JsonException ex = assertThrows(JsonException.class, () -> target.importFrom(ListSource.of(record("c",id))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("too long"));
		// A zip entry name can be longer
		File zip = new File(dir("long-id-zip"),"long.zip");
		zip.getParentFile().mkdirs();
		ZipTarget.newBuilder().root(zip).build().importFrom(ListSource.of(record("c",id)));
		assertEquals(List.of("c!!"+id), keys(ZipFileSource.newBuilder().zipFile(zip).build().stream()));
	}

	public void testFileSourceSymlinkLoop() throws Exception {
		File root = dir("symlink-loop");
		FileTarget.newBuilder().root(root).build().importFrom(ListSource.of(record("c","1")));
		File sub = new File(root,"@c");
		// A link to the root inside the collection folder, and a second link to the collection
		Files.createSymbolicLink(new File(sub,"loop").toPath(), root.toPath().toAbsolutePath());
		Files.createSymbolicLink(new File(root,"@d").toPath(), sub.toPath().toAbsolutePath());
		FileSource source = FileSource.newBuilder().root(root).estimateCount(true).build();
		assertEquals(List.of("c!!1"), keys(source.stream()));
		assertEquals(1, source.estimatedCount());
	}

	//
	// Reentrancy
	//

	public void testZipFileSourceConcurrentStreams() throws Exception {
		File zip = new File(dir("zip-reentrant"),"r.zip");
		zip.getParentFile().mkdirs();
		ZipTarget.newBuilder().root(zip).build().importFrom(ListSource.of(record("c","1"), record("c","2"), record("c","3")));
		ZipFileSource source = ZipFileSource.newBuilder().zipFile(zip).build();
		Stream<JsonContent> s1 = source.stream();
		Stream<JsonContent> s2 = source.stream();
		Iterator<JsonContent> it1 = s1.iterator();
		assertEquals("1", it1.next().getKey().getId());
		// Closing the second stream must not close the first one
		assertEquals(List.of("c!!1","c!!2","c!!3"), keys(s2));
		List<String> rest = new ArrayList<>();
		it1.forEachRemaining((c) -> rest.add(c.getKey().getId()));
		s1.close();
		assertEquals(List.of("2","3"), rest);

		ZipInputStreamSource zs = ZipInputStreamSource.newBuilder().zipInputStream(() -> {
			try {
				return Files.newInputStream(zip.toPath());
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}).build();
		Stream<JsonContent> z1 = zs.stream();
		Iterator<JsonContent> zit = z1.iterator();
		assertEquals("1", zit.next().getKey().getId());
		assertEquals(3, keys(zs.stream()).size());
		assertEquals("2", zit.next().getKey().getId());
		z1.close();
	}

	public void testStreamSourceConcurrentStreams() throws Exception {
		StreamSource source = StreamSource.newBuilder().streamFactory(() -> Stream.<Object>of("a","b")).build();
		Stream<JsonContent> s1 = source.stream();
		Iterator<JsonContent> it1 = s1.iterator();
		assertEquals("a", it1.next().getJson());
		assertEquals(2, keys(source.stream()).size());
		assertEquals("b", it1.next().getJson());
		s1.close();
	}

	public void testTargetReentryIsRejected() throws Exception {
		JsonContainerTarget[] holder = new JsonContainerTarget[1];
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.afterProcessing((c) -> holder[0].importFrom(ListSource.of(record("x","1"))))
				.build();
		holder[0] = target;
		JsonException ex = assertThrows(JsonException.class, () -> target.importFrom(ListSource.of(record("c","1"))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("already running"));
		// The target is still usable
		JsonContainerTarget t2 = JsonContainerTarget.newBuilder().build();
		assertEquals(1, t2.importFrom(ListSource.of(record("c","1"))).getInserted());
	}

	//
	// Range filters
	//

	public void testRangeFilterOnFileSource() throws Exception {
		File root = dir("range-file");
		Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
		Instant t2 = Instant.parse("2025-01-01T00:00:00Z");
		FileTarget.newBuilder().root(root).keepTimestamp(true).build()
			.importFrom(ListSource.of(record("c","old",t1), record("c","new",t2)));
		FileSource source = FileSource.newBuilder().root(root).build();
		assertEquals(List.of("c!!new"), keys(source.stream(new RangeFilter(Instant.parse("2024-06-01T00:00:00Z"), null))));
		assertEquals(List.of("c!!old"), keys(source.stream(new RangeFilter(null, Instant.parse("2024-06-01T00:00:00Z")))));
		assertEquals(2, keys(source.stream(null)).size());

		// Through an import
		JsonContainerTarget target = JsonContainerTarget.newBuilder().build();
		ImportResult r = target.importFrom(source, new RangeFilter(Instant.parse("2024-06-01T00:00:00Z"), null));
		assertEquals(1, r.getInserted());
	}

	public void testRangeFilterOnContainerSource() throws Exception {
		JsonArray data = JsonArray.parse("[{\"id\":\"a\",\"ts\":\"2024-01-01T00:00:00Z\"},{\"id\":\"b\",\"ts\":\"2025-01-01T00:00:00Z\"},{\"id\":\"c\"}]");
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(data)
				.keyFunction((o) -> ((JsonObject)o).getString("id"))
				.timestampFunction((o) -> ((JsonObject)o).containsKey("ts") ? Instant.parse(((JsonObject)o).getString("ts")) : null)
				.build();
		// A content without timestamp is always kept
		assertEquals(List.of("b","c"), keys(source.stream(new RangeFilter(Instant.parse("2024-06-01T00:00:00Z"), null))));
	}

	//
	// Error reporting and results
	//

	public void testErrorReportsTheKey() throws Exception {
		JsonContainerTarget target = JsonContainerTarget.newBuilder().build();
		JsonException ex = assertThrows(JsonException.class, () -> target.importFrom(ListSource.of(record("c","1"), failing("c","2"))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("Error while importing c:2"));
		assertTrue(ex.getMessage(), ex.getMessage().contains("boom"));
	}

	public void testEstimatedCountFromTheSource() throws Exception {
		AtomicLong estimate = new AtomicLong(-2);
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.notification(new JsonTargetImpl.Notification() {
					@Override
					public void notify(JsonTargetImpl.Event event, long processed, long deleted, long estimatedCount, long ellapsedMs) {
						if(event==JsonTargetImpl.Event.START) {
							estimate.set(estimatedCount);
						}
					}
					@Override
					public void information(String msg, Object... params) {
					}
				})
				.build();
		target.importFrom(ListSource.of(record("c","1"), record("c","2"), record("c","3")));
		assertEquals(3, estimate.get());
	}

	public void testReplicationResultJson() throws Exception {
		ReplicationResult r = new ReplicationResult();
		r.addInserted();
		r.addInserted();
		r.addIgnored();
		r.setRangeFilter(new RangeFilter(Instant.parse("2025-01-01T10:00:00Z"), null));
		JsonObject o = r.toJson();
		assertEquals(2, o.getInt("inserted"));
		assertEquals(2, o.getInt("created"));
		assertEquals(1, o.getInt("ignored"));
		// UTC by default, whatever the system time zone
		assertTrue(o.toString(), o.getObject("rangeFilter").getString("since").startsWith("2025-01-01T10:00"));
	}

	public void testFileReplicationTableEmptyFile() throws Exception {
		File d = dir("reptable-empty");
		d.mkdirs();
		Path file = d.toPath().resolve("table.json");
		Files.writeString(file, "", StandardCharsets.UTF_8);
		FileReplicationTable table = new FileReplicationTable(file);
		assertNull(table.lastReplication("s","t"));
		Instant now = Instant.parse("2025-01-01T00:00:00Z");
		table.saveReplication("s","t",now,null);
		assertEquals(now, table.lastReplication("s","t"));
	}

	public void testFileReplicationTableConcurrentInstances() throws Exception {
		File d = dir("reptable-concurrent");
		d.mkdirs();
		Path file = d.toPath().resolve("table.json");
		// Two instances on the same file, updated from several threads: no update is lost
		FileReplicationTable t1 = new FileReplicationTable(file);
		FileReplicationTable t2 = new FileReplicationTable(file);
		List<Thread> threads = new ArrayList<>();
		for(int i=0; i<8; i++) {
			int n = i;
			FileReplicationTable t = i%2==0 ? t1 : t2;
			threads.add(new Thread(() -> t.saveReplication("s","t"+n,Instant.ofEpochSecond(n),null)));
		}
		threads.forEach(Thread::start);
		for(Thread t: threads) {
			t.join();
		}
		for(int i=0; i<8; i++) {
			assertEquals(Instant.ofEpochSecond(i), t1.lastReplication("s","t"+i));
		}
	}
}
