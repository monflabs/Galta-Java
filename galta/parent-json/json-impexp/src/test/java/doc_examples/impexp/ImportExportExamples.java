package doc_examples.impexp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonContentStreamSource;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.container.StreamSource;
import org.monflabs.json.impexp.file.FileSource;
import org.monflabs.json.impexp.file.FileTarget;
import org.monflabs.json.impexp.file.ZipFileSource;
import org.monflabs.json.impexp.file.ZipTarget;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.pojo.PojoTarget;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.json.impexp.util.StaticDeletedContent;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/ImportExport.md
 */
public class ImportExportExamples extends ProjectTestCase {

	/** The temporary files go to target/temp (removed by mvn clean), not to the system temp folder. */
	private Path tempRoot() {
		return support.getTargetTempDirectory().toPath();
	}

	private static Set<String> listFiles(Path root) throws Exception {
		try(Stream<Path> s = Files.walk(root)) {
			return s.filter(Files::isRegularFile)
					.map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
					.collect(Collectors.toCollection(TreeSet::new));
		}
	}

	public void testJsonKey() throws Exception {
		JsonKey key = JsonKey.of("customers", "c-42");
		assertEquals("customers", key.getCollection());
		assertEquals("c-42", key.getId());
		assertEquals("customers!!c-42", key.keyString());
		assertEquals(key, JsonKey.parse("customers!!c-42"));

		JsonKey noCol = JsonKey.of(null, "c-42");
		assertEquals("", noCol.getCollection());           // null becomes ""
		assertEquals("c-42", noCol.keyString());
	}

	public void testContainerToFiles() throws Exception {
		JsonObject data = JsonObject.parse("""
				{
				  "customers": { "c1": { "name": "Ada" }, "c2": { "name": "Alan" } },
				  "orders":    { "o1": { "customer": "c1", "total": 12.5 } }
				}
				""");
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.container(data)
				.build();

		Path root = Files.createTempDirectory(tempRoot(), "galta-export");
		FileTarget target = FileTarget.newBuilder()
				.root(root.toFile())                            // cleared first, by default!
				.build();

		ImportResult result = source.exportTo(target);      // same as target.importFrom(source)
		assertEquals(3, result.getInserted());
		assertEquals(Set.of("@customers/c1.json", "@customers/c2.json", "@orders/o1.json"), listFiles(root));

		// And back
		FileSource files = FileSource.newBuilder()
				.root(root.toFile())
				.build();
		JsonContainerTarget memory = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.build();
		memory.importFrom(files);
		assertEquals(data, memory.getContainer());
	}

	public void testInMemoryFormats() throws Exception {
		JsonArray people = JsonArray.parse("""
				[ { "id": "p1", "team": "red", "name": "Ada" },
				  { "id": "p2", "team": "blue", "name": "Alan" } ]
				""");

		// RECORDS: an array of values; key and collection come from functions
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.format(JsonInMemoryFormat.RECORDS)
				.container(people)
				.collectionFunction(o -> ((JsonObject)o).getString("team"))
				.keyFunction(o -> ((JsonObject)o).getString("id"))
				.build();

		JsonContainerTarget byCol = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYCOL)
				.build();
		source.exportTo(byCol);
		// { "red": [ {...Ada} ], "blue": [ {...Alan} ] }
		assertEquals("Ada", ((JsonObject)byCol.getContainer()).getArray("red").getObject(0).getString("name"));

		JsonContainerTarget withKeys = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSWITHKEYS)
				.build();
		source.exportTo(withKeys);
		// [ { "collection": "red", "id": "p1", "value": {...} }, ... ]
		JsonObject first = ((JsonArray)withKeys.getContainer()).getObject(0);
		assertEquals("red", first.getString("collection"));
		assertEquals("p1", first.getString("id"));
		assertEquals("Ada", first.getObject("value").getString("name"));

		JsonContainerTarget byKey = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYKEY)
				.build();
		source.exportTo(byKey);
		// { "p1": {...}, "p2": {...} }: the collection is lost
		assertEquals("Alan", ((JsonObject)byKey.getContainer()).getObject("p2").getString("name"));
	}

	public void testDefaultKeys() throws Exception {
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(JsonArray.of("a", "b"))                 // RECORDS is the default format
				.build();
		List<String> keys = new ArrayList<>();
		source.stream().forEach(c -> keys.add(c.getKey().keyString()));
		assertEquals(List.of("0", "1"), keys);                 // the index, without a key function
	}

	public void testStreamSourceAndPojoTarget() throws Exception {
		List<Object> rows = List.of(JsonObject.of("sku", "A", "qty", 1), JsonObject.of("sku", "B", "qty", 0));
		StreamSource source = StreamSource.newBuilder()
				.streamFactory(rows::stream)
				.keyFunction(o -> ((JsonObject)o).getString("sku"))
				.valueFunction(o -> JsonObject.of("quantity", ((JsonObject)o).getInt("qty")))
				.build();

		List<String> seen = new ArrayList<>();
		PojoTarget<Void> target = PojoTarget.<Void>newBuilder()
				.writer(c -> seen.add(c.getKey().getId() + "=" + ((JsonObject)c.getJson()).getInt("quantity")))
				.beforeProcessing(c -> ((JsonObject)c.getJson()).getInt("quantity") > 0 ? c : null)   // null skips it
				.build();
		ImportResult r = source.exportTo(target);
		assertEquals(List.of("A=1"), seen);
		assertEquals(2, r.getInserted());                         // counted before beforeProcessing
	}

	public void testFileTargetOptions() throws Exception {
		Path root = Files.createTempDirectory(tempRoot(), "galta-export");
		Files.writeString(root.resolve("keep.txt"), "x");

		FileTarget target = FileTarget.newBuilder()
				.root(root.toFile())
				.clearOnStart(false)                              // keep what is there
				.subdir(2)                                        // 2 levels of hashed folders
				.keepTimestamp(true)                              // file date = content timestamp
				.build();
		Instant ts = Instant.parse("2020-01-01T00:00:00Z");
		JsonContent doc = new StaticContent(JsonKey.of("docs", "a/b:c"), JsonObject.of("v", 1), ts);
		target.importFrom(JsonContentStreamSource.newBuilder()
				.streamFactory(() -> Stream.of(doc))
				.build());

		Set<String> files = listFiles(root);
		assertTrue(files.contains("keep.txt"));
		String written = files.stream().filter(f -> f.endsWith(".json")).findFirst().get();
		// "@docs/<h1>/<h2>/a%2Fb%3Ac.json": ids are encoded to be valid file names
		assertTrue(written, written.matches("@docs/[0-9A-F]{2}/[0-9A-F]{2}/a%2Fb%3Ac\\.json"));
		assertEquals(ts.toEpochMilli(), root.resolve(written).toFile().lastModified());

		// FileSource walks the sub-folders and decodes the id
		FileSource source = FileSource.newBuilder().root(root.toFile()).build();
		JsonContent read = source.stream().findFirst().get();
		assertEquals(JsonKey.of("docs", "a/b:c"), read.getKey());
		assertEquals(ts, read.getTimestamp());
	}

	public void testZip() throws Exception {
		Path zip = Files.createTempFile(tempRoot(), "galta-export", ".zip");
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.container(JsonObject.parse("{ \"c\": { \"k1\": { \"v\": 1 }, \"k2\": { \"v\": 2 } } }"))
				.build();

		ZipTarget target = ZipTarget.newBuilder()
				.root(zip.toFile())
				.build();
		source.exportTo(target);
		try(ZipFile zf = new ZipFile(zip.toFile())) {
			assertNotNull(zf.getEntry("@c/k1.json"));
		}

		ZipFileSource zipSource = ZipFileSource.newBuilder()
				.zipFile(zip.toFile())
				.estimateCount(true)
				.build();
		assertEquals(2, zipSource.estimatedCount());
		JsonContainerTarget back = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.build();
		back.importFrom(zipSource);
		assertEquals(2, ((JsonObject)back.getContainer()).getObject("c").getObject("k2").getInt("v"));
	}

	public void testDeletions() throws Exception {
		JsonObject store = JsonObject.parse("{ \"k1\": 1, \"k2\": 2 }");
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYKEY)
				.container(store)                                 // update an existing container
				.build();

		List<JsonContent> changes = List.of(
				new StaticContent(JsonKey.of(null, "k3"), 3, null),
				new StaticDeletedContent(JsonKey.of(null, "k1")));
		JsonContentStreamSource source = JsonContentStreamSource.newBuilder()
				.streamFactory(changes::stream)
				.build();
		ImportResult r = target.importFrom(source);
		assertEquals(1, r.getInserted());
		assertEquals(1, r.getDeleted());
		assertEquals(JsonObject.of("k2", 2, "k3", 3), store);
	}

	public void testNotificationAndFailure() throws Exception {
		List<String> events = new ArrayList<>();
		JsonTargetImpl.Notification notification = new JsonTargetImpl.TextNotification() {
			@Override
			public void log(String msg, Object... params) {
				events.add(org.monflabs.util.StringFormat.format(msg, params));
			}
		};
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(JsonArray.of("a", "b"))
				.build();
		PojoTarget<Void> target = PojoTarget.<Void>newBuilder()
				.writer(c -> { })
				.estimatedCount(source::estimatedCount)
				.notification(notification)
				.build();
		source.exportTo(target);
		assertEquals("Start processing ~2 documents", events.get(0));
		assertTrue(events.get(events.size() - 1), events.get(events.size() - 1).startsWith("Finished processing 2 records (0 deleted)"));

		// A failure is rethrown as a JsonException, after the target is closed
		boolean[] closed = { false };
		PojoTarget<Void> failing = PojoTarget.<Void>newBuilder()
				.writer(c -> { throw new IllegalStateException("disk full"); })
				.close(() -> closed[0] = true)
				.build();
		try {
			source.exportTo(failing);
			fail();
		} catch(JsonException e) {
			assertTrue(closed[0]);
			assertTrue(e.getMessage(), e.getMessage().startsWith("Error while importing :0: disk full"));
		}
	}

	public void testRangeFilter() throws Exception {
		JsonArray events = JsonArray.parse("""
				[ { "id": "e1", "at": "2024-03-01T10:00:00Z" },
				  { "id": "e2", "at": "2024-09-01T10:00:00Z" },
				  { "id": "e3" } ]
				""");
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(events)
				.keyFunction(o -> ((JsonObject)o).getString("id"))
				.timestampFunction(o -> ((JsonObject)o).containsKey("at") ? Instant.parse(((JsonObject)o).getString("at")) : null)
				.build();
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYKEY)
				.build();
		target.importFrom(source, new RangeFilter(Instant.parse("2024-06-01T00:00:00Z"), null));
		// e2: in the range, e3: no timestamp, always kept
		assertEquals(List.of("e2", "e3"), new ArrayList<>(((JsonObject)target.getContainer()).keySet()));
	}
}
