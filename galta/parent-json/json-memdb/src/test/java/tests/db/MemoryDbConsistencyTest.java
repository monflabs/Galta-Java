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
package tests.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.CancelException;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.db.JsonDbSource;
import org.monflabs.json.impexp.db.JsonDbTarget;
import org.monflabs.json.impexp.db.MemoryJsonDb;
import org.monflabs.json.impexp.db.MemoryJsonDb.Transaction;
import org.monflabs.json.impexp.db.MemoryReplicationTable;
import org.monflabs.json.impexp.pojo.PojoTarget;
import org.monflabs.json.impexp.replication.ConflictResolver;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.impl.FileReplicationTable;

import tests.ProjectTestCase;

/**
 * Consistency of the memory DB under concurrent changes, and of its source/target.
 */
public class MemoryDbConsistencyTest extends ProjectTestCase {

	private static MemoryJsonDb db(int count) {
		MemoryJsonDb db = new MemoryJsonDb();
		for(int i=0; i<count; i++) {
			db.insert(JsonKey.of(i%2==0 ? "even" : "odd","k"+i), JsonObject.of("v",i));
		}
		return db;
	}

	// The target of an export can write into the source DB: the source streams a snapshot
	public void testExportWhileWritingSameDb() throws Exception {
		MemoryJsonDb db = db(10);
		JsonDbSource source = JsonDbSource.newBuilder().db(db).build();
		PojoTarget<Object> target = PojoTarget.newBuilder()
				.writer( (c) -> db.upsert(JsonKey.of("copy",c.getKey().getId()), c.getJson()) )
				.build();
		ImportResult r = source.exportTo(target);
		assertEquals(10, r.getInserted());
		assertEquals(20, db.count());
	}

	// Writes from another thread while a source is being consumed
	public void testConcurrentWritesDuringExport() throws Exception {
		MemoryJsonDb db = db(2000);
		JsonDbSource source = JsonDbSource.newBuilder().db(db).build();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		CountDownLatch started = new CountDownLatch(1);
		Thread writer = new Thread( () -> {
			try {
				started.await();
				for(int i=0; i<2000; i++) {
					db.upsert(JsonKey.of("w","k"+i), JsonObject.of("v",i));
					if(i%3==0) {
						db.delete(JsonKey.of(i%2==0 ? "even" : "odd","k"+i));
					}
				}
			} catch(Throwable t) {
				failure.set(t);
			}
		});
		writer.start();
		long count;
		try(Stream<JsonContent> s = source.stream(null)) {
			count = s.peek( (c) -> {
				started.countDown();
				Thread.yield();
			}).count();
		}
		writer.join();
		assertNull(failure.get());
		// The snapshot taken when the stream started
		assertEquals(2000, count);
	}

	// estimatedCount() counts what the source streams: collection, deletions
	public void testEstimatedCountHonoursFilters() throws Exception {
		MemoryJsonDb db = db(10);
		db.delete(JsonKey.of("even","k0"));
		JsonDbSource all = JsonDbSource.newBuilder().db(db).build();
		assertEquals(10, all.estimatedCount());      // 9 records + 1 deletion
		assertEquals(10, count(all, null));

		JsonDbSource even = JsonDbSource.newBuilder().db(db).collection("even").build();
		assertEquals(5, even.estimatedCount());
		assertEquals(5, count(even, null));

		// With a range filter, the count follows the filter of the stream
		sleep();
		Instant since = Instant.now();
		sleep();
		db.insert(JsonKey.of("even","new"), JsonObject.of("v",1));
		RangeFilter f = new RangeFilter(since, null);
		assertEquals(1, count(even, f));
		assertEquals(1, even.estimatedCount());
	}
	private static long count(JsonDbSource source, RangeFilter filter) {
		try(Stream<JsonContent> s = source.stream(filter)) {
			return s.count();
		}
	}

	// delete(Collection) is all or nothing
	public void testDeleteCollectionIsAtomic() throws Exception {
		MemoryJsonDb db = db(4);
		try {
			db.delete(List.of(JsonKey.of("even","k0"), JsonKey.of("even","missing"), JsonKey.of("odd","k1")));
			fail();
		} catch(JsonException ex) {
		}
		assertEquals(4, db.count());
		assertEquals(0, db.deletedCount());
		db.delete(List.of(JsonKey.of("even","k0"), JsonKey.of("odd","k1")));
		assertEquals(2, db.count());
		assertEquals(2, db.deletedCount());
	}

	// A transaction cannot overwrite a concurrent change, including a clear()
	public void testTransactionConflicts() throws Exception {
		MemoryJsonDb db = db(2);
		Transaction t = db.beginTransaction();
		t.insert(JsonKey.of("t","k"), JsonObject.of("v",1));
		db.insert(JsonKey.of("x","k"), JsonObject.of("v",1));
		try {
			t.commit();
			fail();
		} catch(JsonException ex) {
		}
		assertFalse(db.exists(JsonKey.of("t","k")));

		Transaction t2 = db.beginTransaction();
		t2.insert(JsonKey.of("t","k"), JsonObject.of("v",1));
		db.clear();
		try {
			t2.commit();
			fail("A clear() must invalidate the running transactions");
		} catch(JsonException ex) {
		}
		assertEquals(0, db.count());

		Transaction t3 = db.beginTransaction();
		t3.insert(JsonKey.of("t","k"), JsonObject.of("v",1));
		t3.commit();
		assertTrue(db.exists(JsonKey.of("t","k")));
		try {
			t3.insert(JsonKey.of("t","k2"), JsonObject.of("v",1));
			fail();
		} catch(JsonException ex) {
		}
	}

	// transactionThreshold commits every N documents
	public void testTransactionThresholdBatches() throws Exception {
		MemoryJsonDb src = db(5);
		MemoryJsonDb dst = new MemoryJsonDb();
		List<Integer> countsAfterEachDoc = new ArrayList<>();
		JsonDbTarget target = JsonDbTarget.newBuilder()
				.db(dst)
				.transactionThreshold(2)
				.afterProcessing( (c) -> countsAfterEachDoc.add(dst.count()) )
				.build();
		JsonDbSource.newBuilder().db(src).build().exportTo(target);
		// The target DB is only updated when a batch is committed
		assertEquals(List.of(0,0,2,2,4), countsAfterEachDoc);
		assertEquals(5, dst.count());
	}

	// Cancelling an import rolls back the pending transaction
	public void testCancelRollsBack() throws Exception {
		MemoryJsonDb src = db(5);
		MemoryJsonDb dst = new MemoryJsonDb();
		JsonDbTarget[] holder = new JsonDbTarget[1];
		holder[0] = JsonDbTarget.newBuilder()
				.db(dst)
				.transactionThreshold(2)
				.afterProcessing( (c) -> {
					if(c.getKey().getId().equals("k2")) {
						holder[0].cancel();
					}
				})
				.build();
		try {
			JsonDbSource.newBuilder().db(src).build().exportTo(holder[0]);
			fail();
		} catch(CancelException ex) {
		}
		// k0,k1 were committed, k2 was rolled back
		assertEquals(2, dst.count());
	}

	// The file replication table keeps one date per source/target pair
	public void testReplicationWithFileTable() throws Exception {
		Path dir = Files.createTempDirectory("memdb-rep");
		try {
			FileReplicationTable table = new FileReplicationTable(dir.resolve("rep.json"));
			MemoryJsonDb master = db(2);
			MemoryJsonDb replica1 = new MemoryJsonDb();
			MemoryJsonDb replica2 = new MemoryJsonDb();
			JsonDbSource source = JsonDbSource.newBuilder().db(master).build();
			JsonDbTarget t1 = JsonDbTarget.newBuilder().db(replica1).replicationTable(table).build();
			JsonDbTarget t2 = JsonDbTarget.newBuilder().db(replica2).replicationTable(table).build();

			assertEquals(2, t1.replicate(source, ConflictResolver.FAIL_EXCEPTION).getInserted());
			sleep();
			master.insert(JsonKey.of("c","new"), JsonObject.of("v",1));
			sleep();
			// replica2 was never replicated: it gets everything, not only the last change
			ReplicationResult r2 = t2.replicate(source, ConflictResolver.FAIL_EXCEPTION);
			assertEquals(3, r2.getInserted());
			ReplicationResult r1 = t1.replicate(source, ConflictResolver.FAIL_EXCEPTION);
			assertEquals(1, r1.getInserted());
			assertNotNull(table.lastReplication(master.getReplicationId(), replica1.getReplicationId()));
			assertNotNull(table.lastReplication(master.getReplicationId(), replica2.getReplicationId()));
		} finally {
			try(Stream<Path> s = Files.walk(dir)) {
				for(Path p: s.sorted( (a,b) -> b.compareTo(a) ).collect(Collectors.toList())) {
					Files.delete(p);
				}
			}
		}
	}

	public void testMemoryReplicationTableClear() throws Exception {
		MemoryReplicationTable table = new MemoryReplicationTable();
		Instant t = Instant.now();
		table.saveReplication("s", "t1", t, null);
		table.saveReplication("s", "t2", t, null);
		table.saveReplication("s", "t1", t.plusSeconds(1), null);
		assertEquals(t.plusSeconds(1), table.lastReplication("s", "t1"));
		table.clear("s", "t1");
		assertNull(table.lastReplication("s", "t1"));
		assertEquals(t, table.lastReplication("s", "t2"));
		assertEquals(1, table.getEntries().size());
		table.clear();
		assertNull(table.lastReplication("s", "t2"));
	}
}
