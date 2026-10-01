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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.file.FileTarget;
import org.monflabs.json.impexp.file.ZipFileSource;
import org.monflabs.json.impexp.file.ZipTarget;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.json.impexp.replication.ReplicationTable;
import org.monflabs.json.impexp.replication.ReplicationTarget;
import org.monflabs.json.impexp.replication.impl.FileReplicationTable;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.json.impexp.util.StaticDeletedContent;

import tests.ProjectTestCase;

/**
 * Regression tests for defects found in the import/export engine review.
 */
public class ImpExpRegressionTest extends ProjectTestCase {

	/** Transactional replication target with injectable failures. */
	private static class TxTarget extends JsonTargetImpl implements ReplicationTarget {
		static class Builder extends TargetBuilder<TxTarget,Builder> {
			@Override
			protected TxTarget _build() {
				return new TxTarget(this);
			}
		}
		RuntimeException startFailure;
		RuntimeException commitFailure;
		int started, committed, rolledBack;
		TxTarget(Builder b) {
			super(b);
		}
		@Override
		public String getReplicationId() {
			return "tx";
		}
		@Override
		protected boolean supportsTransaction() {
			return true;
		}
		@Override
		protected void startTransaction() {
			if(startFailure!=null) {
				throw startFailure;
			}
			started++;
		}
		@Override
		protected void commitTransaction() {
			if(commitFailure!=null) {
				throw commitFailure;
			}
			committed++;
		}
		@Override
		protected void rollbackTransaction() {
			rolledBack++;
		}
		@Override
		protected JsonContent readJsonContent(JsonKey key) {
			return null;
		}
		@Override
		protected void saveJsonContent(JsonContent content) {
		}
	}

	private static class CountingTable implements ReplicationTable {
		int saved;
		@Override
		public Instant lastReplication(String source, String target) {
			return null;
		}
		@Override
		public void saveReplication(String source, String target, Instant newLastRep, ReplicationResult result) {
			saved++;
		}
	}

	private static class ListSource extends JsonSourceImpl implements ReplicationSource {
		final List<JsonContent> contents;
		ListSource(JsonContent...contents) {
			this.contents = List.of(contents);
		}
		@Override
		public String getReplicationId() {
			return "list";
		}
		@Override
		protected Iterator<JsonContent> createJsonContentIterator() {
			return contents.iterator();
		}
	}

	private static JsonContent record(String col, String id) {
		return new StaticContent(JsonKey.of(col,id), JsonObject.of("id",id), null);
	}

	public void testWatermarkNotSavedWhenCommitFails() {
		CountingTable table = new CountingTable();
		TxTarget target = new TxTarget.Builder().replicationTable(table).build();
		target.commitFailure = new IllegalStateException("commit failed");
		assertThrows(JsonException.class, () -> target.replicate(new ListSource(record("c","1")), null));
		// The changes were lost: the next replication must start from the same point
		assertEquals(0, table.saved);

		target.commitFailure = null;
		target.replicate(new ListSource(record("c","1")), null);
		assertEquals(1, table.saved);
	}

	public void testNoRollbackWithoutOpenTransaction() {
		TxTarget target = new TxTarget.Builder().build();
		IllegalStateException failure = new IllegalStateException("cannot start");
		target.startFailure = failure;
		RuntimeException ex = assertThrows(RuntimeException.class, () -> target.importFrom(new ListSource(record("c","1"))));
		assertSame(failure, ex.getCause()!=null ? ex.getCause() : ex);
		assertEquals(0, target.rolledBack);

		// A failure after the commit does not roll back a committed transaction either
		target.startFailure = null;
		target.importFrom(new ListSource(record("c","1")));
		assertEquals(1, target.committed);
		assertEquals(0, target.rolledBack);
	}

	public void testReplicateOnNonReplicationTarget() {
		JsonContainerTarget target = JsonContainerTarget.newBuilder().replicationTable(new CountingTable()).build();
		JsonException ex = assertThrows(JsonException.class, () -> target.replicate(new ListSource(record("c","1")), null));
		assertTrue(ex.getMessage(), ex.getMessage().contains("ReplicationTarget"));
	}

	public void testDeletionsIgnoredByTargetsThatCannotDelete() {
		JsonContainerTarget records = JsonContainerTarget.newBuilder().format(JsonInMemoryFormat.RECORDS).build();
		assertFalse(records.supportsDeletions());
		ImportResult r = records.importFrom(new ListSource(record("c","1"), new StaticDeletedContent(JsonKey.of("c","1"))));
		assertEquals(1, r.getInserted());
		assertEquals(0, r.getDeleted());

		JsonContainerTarget byKey = JsonContainerTarget.newBuilder().format(JsonInMemoryFormat.RECORDSBYKEY).build();
		assertTrue(byKey.supportsDeletions());
		r = byKey.importFrom(new ListSource(record("c","1"), new StaticDeletedContent(JsonKey.of("c","1"))));
		assertEquals(1, r.getDeleted());
		assertTrue(((JsonObject)byKey.getContainer()).isEmpty());
	}

	public void testStreamClosesSourceWhenInitFails() {
		boolean[] closed = new boolean[1];
		JsonSourceImpl source = new JsonSourceImpl() {
			@Override
			public void init(RangeFilter rangeFilter) {
				super.init(rangeFilter);
				throw new JsonException(null,"init failed");
			}
			@Override
			public void close() {
				closed[0] = true;
			}
		};
		assertThrows(JsonException.class, () -> source.stream());
		assertTrue(closed[0]);

		closed[0] = false;
		try(Stream<JsonContent> s = new ListSource(record("c","1")) {
			@Override
			public void close() {
				closed[0] = true;
			}
		}.stream()) {
			assertEquals(1, s.count());
		}
		assertTrue(closed[0]);
	}

	public void testJsonKeyEncodingIsUnambiguous() {
		JsonKey[] keys = {
			JsonKey.of(null, "a!!b"),
			JsonKey.of("a", "b"),
			JsonKey.of("a!!b", "c"),
			JsonKey.of("a", "b!!c"),
			JsonKey.of("x\\", "!y"),
			JsonKey.of("", "!"),
			JsonKey.of("customers", "c-42"),
		};
		List<String> strings = new ArrayList<>();
		for(JsonKey k: keys) {
			String s = k.keyString();
			assertFalse(s, strings.contains(s));
			strings.add(s);
			assertEquals(k, JsonKey.parse(s));
		}
		// Plain keys are unchanged
		assertEquals("customers!!c-42", JsonKey.of("customers","c-42").keyString());
		// Null and empty are the same, and the hash is not symmetric
		assertEquals(JsonKey.of(null,null), JsonKey.of("",""));
		assertEquals(JsonKey.of(null,"x").hashCode(), JsonKey.of("","x").hashCode());
		assertFalse(JsonKey.of("ab","cd").equals(JsonKey.of("cd","ab")));
		assertFalse(JsonKey.of("ab","cd").hashCode()==JsonKey.of("cd","ab").hashCode());
	}

	public void testContainerIndexWithMissingCollection() {
		// An entry without a collection property is found by a key without a collection
		JsonArray container = JsonArray.of(JsonObject.of("id","1","value",JsonObject.of("v",1)));
		JsonContainerTarget target = JsonContainerTarget.newBuilder().format(JsonInMemoryFormat.RECORDSWITHKEYS).container(container).build();
		target.importFrom(new ListSource(new StaticContent(JsonKey.of(null,"1"), JsonObject.of("v",2), null)));
		assertEquals(1, container.size());
		assertEquals(2, container.getObject(0).getObject("value").getInt("v"));

		// Deletion uses the index
		target.importFrom(new ListSource(record(null,"2"), new StaticDeletedContent(JsonKey.of(null,"1"))));
		assertEquals(1, container.size());
		assertEquals("2", container.getObject(0).getString("id"));
	}

	public void testFileTargetCaseCollision() throws Exception {
		File root = support.getProjectDirectory("target/tests/json-case-collision");
		root.getParentFile().mkdirs();
		FileTarget target = FileTarget.newBuilder().root(root).clearOnStart(true).build();
		JsonException ex = assertThrows(JsonException.class, () -> target.importFrom(new ListSource(record("c","Abc"), record("c","abc"))));
		assertTrue(ex.getMessage(), ex.getMessage().contains("case-insensitive"));
		// Collections that only differ by their case would share a folder
		assertThrows(JsonException.class, () -> target.importFrom(new ListSource(record("Col","a"), record("col","b"))));
		// The same document written twice, or after a deletion, is fine
		target.importFrom(new ListSource(record("c","Abc"), record("c","Abc")));
		target.importFrom(new ListSource(record("c","Abc"), new StaticDeletedContent(JsonKey.of("c","Abc")), record("c","abc")));
	}

	public void testFileReplicationTableAtomicWrite() throws Exception {
		Path dir = support.getProjectDirectory("target/tests/json-reptable").toPath();
		Files.createDirectories(dir);
		Path file = dir.resolve("table.json");
		Files.deleteIfExists(file);
		FileReplicationTable table = new FileReplicationTable(file);
		OffsetDateTime ts = OffsetDateTime.of(2026,1,1,10,0,0,0,ZoneOffset.UTC);
		table.setReplication("s","t",ts);
		table.setReplication("s","u",ts);
		assertEquals(ts.toInstant(), table.lastReplication("s","t"));
		try(Stream<Path> files = Files.list(dir)) {
			// No temporary file left behind (the .lock companion file is kept)
			assertEquals(List.of(file), files.filter((f) -> !f.getFileName().toString().endsWith(".lock")).toList());
		}
	}

	public void testZipTargetKeepsTimestamp() throws Exception {
		File zip = support.getProjectDirectory("target/tests/json-zip/timestamp.zip");
		zip.getParentFile().mkdirs();
		Instant ts = Instant.parse("2025-06-01T12:34:56Z");
		ZipTarget target = ZipTarget.newBuilder().root(zip).build();
		target.importFrom(new ListSource(new StaticContent(JsonKey.of("c","1"), JsonObject.of("v",1), ts)));
		try(Stream<JsonContent> s = ZipFileSource.newBuilder().zipFile(zip).build().stream()) {
			assertEquals(ts, s.findFirst().get().getTimestamp());
		}
	}
}
