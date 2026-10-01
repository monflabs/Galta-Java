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

import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.db.JsonDbRecord;
import org.monflabs.json.impexp.db.JsonDbSource;
import org.monflabs.json.impexp.db.JsonDbTarget;
import org.monflabs.json.impexp.db.MemoryJsonDb;
import org.monflabs.json.impexp.db.MemoryJsonDb.Transaction;
import org.monflabs.json.impexp.db.MemoryReplicationTable;
import org.monflabs.json.impexp.replication.ReplicationResult;

import tests.ProjectTestCase;

/**
 * Transactions of the memory DB: replication of the committed changes, per record
 * conflicts, read-through, and the operations that must stay atomic.
 */
public class MemoryDbTransactionTest extends ProjectTestCase {

	private static JsonKey key(String col, String id) {
		return JsonKey.of(col, id);
	}
	private static int v(JsonDbRecord r) {
		return ((JsonObject)r.getJson()).getInt("v");
	}
	private static JsonDbTarget target(MemoryJsonDb db) {
		return JsonDbTarget.newBuilder().db(db).replicationTable(new MemoryReplicationTable()).build();
	}
	private static JsonDbSource source(MemoryJsonDb db) {
		return JsonDbSource.newBuilder().db(db).build();
	}

	// A record written in a transaction is replicated even when a replication ran
	// between the write and the commit
	public void testTransactionCommittedAfterAReplication() throws Exception {
		MemoryJsonDb db1 = new MemoryJsonDb();
		MemoryJsonDb db2 = new MemoryJsonDb();
		JsonDbSource source = source(db1);
		JsonDbTarget target = target(db2);

		Transaction tx = db1.beginTransaction();
		tx.insert(key("c","k1"), JsonObject.of("v",1));
		sleep();
		ReplicationResult r1 = target.replicate(source, null);
		assertEquals(0, r1.getInserted());
		sleep();
		tx.commit();
		sleep();
		ReplicationResult r2 = target.replicate(source, null);
		assertEquals(1, r2.getInserted());
		assertTrue(db2.exists(key("c","k1")));
	}

	// A -> B -> C: B receives the records in a transaction, while a replication from B to C runs
	public void testReplicationChain() throws Exception {
		MemoryJsonDb a = new MemoryJsonDb();
		MemoryJsonDb b = new MemoryJsonDb();
		MemoryJsonDb c = new MemoryJsonDb();
		for(int i=0; i<5; i++) {
			a.insert(key("c","k"+i), JsonObject.of("v",i));
		}
		JsonDbTarget toC = target(c);
		JsonDbSource fromB = source(b);
		List<ReplicationResult> midResults = new ArrayList<>();
		// The A -> B replication writes in a single transaction, and a B -> C replication
		// runs in the middle of it
		JsonDbTarget toB = JsonDbTarget.newBuilder().db(b).replicationTable(new MemoryReplicationTable())
				.afterProcessing( (content) -> {
					if(content.getKey().getId().equals("k2")) {
						sleep();
						midResults.add(toC.replicate(fromB, null));
						sleep();
					}
				})
				.build();
		sleep();
		toB.replicate(source(a), null);
		assertEquals(1, midResults.size());
		assertEquals(0, midResults.get(0).getInserted());
		assertEquals(5, b.count());
		sleep();
		toC.replicate(fromB, null);
		assertEquals(5, c.count());
	}

	// A commit only fails when a record it writes was changed: a concurrent change to
	// another record no longer aborts a whole (chunked) import
	public void testUnrelatedChangeDuringChunkedImport() throws Exception {
		MemoryJsonDb src = new MemoryJsonDb();
		for(int i=0; i<10; i++) {
			src.insert(key("c","k"+i), JsonObject.of("v",i));
		}
		MemoryJsonDb dst = new MemoryJsonDb();
		JsonDbTarget target = JsonDbTarget.newBuilder().db(dst).transactionThreshold(3)
				.afterProcessing( (content) -> dst.upsert(key("other", content.getKey().getId()), JsonObject.of("v",-1)) )
				.build();
		ImportResult r = source(src).exportTo(target);
		assertEquals(10, r.getInserted());
		assertEquals(20, dst.count());
	}

	// The transaction reads through to the DB: its view is the committed state plus its own changes
	public void testReadThrough() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(key("a","1"), JsonObject.of("v",1));
		db.insert(key("b","1"), JsonObject.of("v",2));
		db.insert(key("a","2"), JsonObject.of("v",3));
		Transaction tx = db.beginTransaction();
		tx.update(key("a","1"), JsonObject.of("v",10));
		tx.delete(key("b","1"));
		tx.insert(key("a","3"), JsonObject.of("v",4));
		assertEquals(3, tx.count());
		assertEquals(1, tx.deletedCount());
		assertEquals(3, db.count());
		assertEquals(0, db.deletedCount());
		// Order: the DB order, with the changes in place, then the new records
		assertEquals(List.of(key("a","1"), key("a","2"), key("a","3")), tx.select().keys());
		assertEquals(List.of(key("a","1"), key("a","2"), key("a","3")), tx.select().collection("a").keys());
		assertEquals(List.of(), tx.select().collection("b").keys());
		assertEquals(10, v(tx.select(key("a","1"))));
		assertTrue(tx.isDeleted(key("b","1")));
		// A record committed by another writer is visible to the transaction
		db.insert(key("c","1"), JsonObject.of("v",5));
		assertTrue(tx.exists(key("c","1")));
		assertEquals(4, tx.count());
		tx.commit();
		assertEquals(4, db.count());
		assertEquals(10, v(db.select(key("a","1"))));
		assertTrue(db.isDeleted(key("b","1")));
		assertEquals(List.of(key("a","1"), key("a","2"), key("c","1"), key("a","3")), db.select().keys());
	}

	public void testClearInTransaction() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(key("a","1"), JsonObject.of("v",1));
		db.delete(key("a","1"));
		db.insert(key("a","2"), JsonObject.of("v",2));
		Transaction tx = db.beginTransaction();
		tx.clear();
		tx.insert(key("a","3"), JsonObject.of("v",3));
		assertEquals(1, tx.count());
		assertEquals(0, tx.deletedCount());
		assertEquals(1, db.count());
		tx.commit();
		assertEquals(List.of(key("a","3")), db.select().keys());
		assertEquals(0, db.deletedCount());
	}

	public void testNestedTransaction() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(key("a","1"), JsonObject.of("v",1));
		Transaction tx = db.beginTransaction();
		tx.insert(key("a","2"), JsonObject.of("v",2));
		Transaction nested = tx.beginTransaction();
		nested.insert(key("a","3"), JsonObject.of("v",3));
		assertEquals(3, nested.count());
		nested.commit();
		assertEquals(3, tx.count());
		assertEquals(1, db.count());
		tx.commit();
		assertEquals(3, db.count());

		// A conflict on the grand parent
		Transaction t1 = db.beginTransaction();
		Transaction t2 = t1.beginTransaction();
		t2.upsert(key("a","1"), JsonObject.of("v",100));
		db.upsert(key("a","1"), JsonObject.of("v",50));
		assertThrows(JsonException.class, () -> t2.commit());
	}

	// delete(Collection) is atomic, even with duplicate keys
	public void testDeleteDuplicateKeys() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(key("a","1"), JsonObject.of("v",1));
		db.insert(key("a","2"), JsonObject.of("v",2));
		db.delete(List.of(key("a","1"), key("a","1")));
		assertEquals(1, db.count());
		assertEquals(1, db.deletedCount());
		// A missing key fails before anything is deleted
		assertThrows(JsonException.class, () -> db.delete(List.of(key("a","2"), key("a","2"), key("a","missing"))));
		assertTrue(db.exists(key("a","2")));
	}

	// clear(true) records the deletions, so they are replicated; clear() does not
	public void testClearReplication() throws Exception {
		MemoryJsonDb db1 = new MemoryJsonDb();
		MemoryJsonDb db2 = new MemoryJsonDb();
		JsonDbSource source = source(db1);
		JsonDbTarget target = target(db2);
		db1.insert(key("a","1"), JsonObject.of("v",1));
		db1.insert(key("a","2"), JsonObject.of("v",2));
		sleep();
		target.replicate(source, null);
		assertEquals(2, db2.count());

		sleep();
		db1.clear();
		sleep();
		target.replicate(source, null);
		assertEquals(2, db2.count());

		db1.insert(key("a","1"), JsonObject.of("v",1));
		db1.insert(key("a","2"), JsonObject.of("v",2));
		sleep();
		db1.clear(true);
		assertEquals(0, db1.count());
		assertEquals(2, db1.deletedCount());
		sleep();
		ReplicationResult r = target.replicate(source, null);
		assertEquals(2, r.getDeleted());
		assertEquals(0, db2.count());
	}

	// Several replications from the same source can run concurrently
	public void testConcurrentReplicationsFromOneSource() throws Exception {
		MemoryJsonDb src = new MemoryJsonDb();
		for(int i=0; i<500; i++) {
			src.insert(key("c","k"+i), JsonObject.of("v",i));
		}
		JsonDbSource source = source(src);
		List<MemoryJsonDb> dbs = new ArrayList<>();
		List<Thread> threads = new ArrayList<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		sleep();
		for(int i=0; i<4; i++) {
			MemoryJsonDb db = new MemoryJsonDb();
			dbs.add(db);
			JsonDbTarget t = target(db);
			threads.add(new Thread(() -> {
				try {
					t.replicate(source, null);
				} catch(Throwable e) {
					failure.set(e);
				}
			}));
		}
		threads.forEach(Thread::start);
		for(Thread t: threads) {
			t.join();
		}
		assertNull(failure.get());
		for(MemoryJsonDb db: dbs) {
			assertEquals(500, db.count());
		}
	}

	// Chunked imports into a large DB no longer copy the whole DB for every chunk
	public void testChunkedImportIntoALargeDb() throws Exception {
		MemoryJsonDb src = new MemoryJsonDb();
		MemoryJsonDb dst = new MemoryJsonDb();
		for(int i=0; i<20000; i++) {
			src.insert(key("c","k"+i), JsonObject.of("v",i));
			dst.insert(key("d","k"+i), JsonObject.of("v",i));
		}
		JsonDbTarget target = JsonDbTarget.newBuilder().db(dst).transactionThreshold(10).build();
		long start = System.currentTimeMillis();
		source(src).exportTo(target);
		long duration = System.currentTimeMillis()-start;
		assertEquals(40000, dst.count());
		// 2000 commits: with a copy of the DB per transaction, this took minutes
		assertTrue("Took "+duration+"ms", duration<20000);

		// A select of a collection only reads that collection
		assertEquals(20000, dst.select().collection("c").count());
		assertEquals(20000, dst.select().collection("d").keys().size());
	}
}
