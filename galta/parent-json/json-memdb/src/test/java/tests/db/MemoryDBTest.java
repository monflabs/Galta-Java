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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.db.JsonDbRecord;
import org.monflabs.json.impexp.db.JsonDbSource;
import org.monflabs.json.impexp.db.JsonDbTarget;
import org.monflabs.json.impexp.db.JsonSelect;
import org.monflabs.json.impexp.db.MemoryJsonDb;
import org.monflabs.json.impexp.db.MemoryJsonDb.Transaction;
import org.monflabs.json.impexp.db.MemoryReplicationTable;

import tests.ProjectTestCase;

public class MemoryDBTest extends ProjectTestCase {

	public void testCollections() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		
		Set<String> cols = db.getCollections();
		assertEquals(0, cols.size());
		
		db.insert(JsonKey.of("c1","k1"), JsonObject.create());
		cols = db.getCollections();
		assertEquals(1, cols.size());
		assertTrue(cols.contains("c1"));
	}

	public void testCRUD() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();

		db.insert(JsonKey.of("c1","k1"), JsonObject.of("v",1));
		assertEquals(1, db.count());
		db.insert(JsonKey.of("c1","k2"), JsonObject.of("v",2));
		assertEquals(2, db.count());
		assertThrows(JsonException.class, () -> db.insert(JsonKey.of("c1","k1"), JsonObject.create()) );
		assertEquals(2, db.count());
		
		assertTrue  (db.exists(JsonKey.of("c1","k1")));
		assertTrue  (db.exists(JsonKey.of("c1","k2")));
		assertFalse (db.exists(JsonKey.of("c1","k3")));
		assertEquals(JsonObject.of("v",1), db.select(JsonKey.of("c1","k1")).getJson());
		assertEquals(JsonObject.of("v",2), db.select(JsonKey.of("c1","k2")).getJson());
		assertNull  (db.select(JsonKey.of("c1","k3")));

		assertEquals(JsonKey.of("c1","k1"), db.select(JsonKey.of("c1","k1")).getKey());
		assertEquals(JsonKey.of("c1","k2"), db.select(JsonKey.of("c1","k2")).getKey());

		db.update(JsonKey.of("c1","k1"), JsonObject.of("v",11));
		assertEquals(2, db.count());
		assertEquals(JsonObject.of("v",11), db.select(JsonKey.of("c1","k1")).getJson());
		assertTrue  (db.exists(JsonKey.of("c1","k1")));
		assertThrows(JsonException.class, () -> db.update(JsonKey.of("c1","k11"), JsonObject.create()) );

		db.delete(JsonKey.of("c1","k1"));
		assertEquals(1, db.count());
		assertFalse (db.exists(JsonKey.of("c1","k1")));
		assertEquals(JsonObject.of("v",2), db.select(JsonKey.of("c1","k2")).getJson());
	}

	public void testDelete() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();

		db.insert(JsonKey.of("c1","k1"), JsonObject.of("v",1));
		db.insert(JsonKey.of("c1","k2"), JsonObject.of("v",2));
		db.insert(JsonKey.of("c1","k3"), JsonObject.of("v",3));
		assertEquals(3, db.count());
		assertEquals(0, db.deletedCount());

		db.delete(JsonKey.of("c1","k2"));
		assertEquals(2, db.count());
		assertEquals(1, db.deletedCount());
		assertFalse(db.isDeleted(JsonKey.of("c1","k1")));
		assertTrue (db.isDeleted(JsonKey.of("c1","k2")));
		assertFalse(db.isDeleted(JsonKey.of("c1","k3")));
		
		db.clear();
		assertEquals(0, db.count());
		assertEquals(0, db.deletedCount());
	}

	public void testSelect() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();

		db.insert(JsonKey.of("c1","k1"), JsonObject.of("v",10));
		db.insert(JsonKey.of("c1","k2"), JsonObject.of("v",20));
		db.insert(JsonKey.of("c1","k3"), JsonObject.of("v",30));
		
		db.insert(JsonKey.of("c2","k1"), JsonObject.of("v",11));
		db.insert(JsonKey.of("c2","k2"), JsonObject.of("v",21));

		assertEquals( JsonObject.of("v",10), db.select().collection("c1").first().getJson() );
		assertEquals( 3, db.select().collection("c1").count() );

		assertEquals( List.of(JsonObject.of("v",10), JsonObject.of("v",20), JsonObject.of("v",30)), list(db.select().collection("c1")) );
		assertEquals( List.of(JsonObject.of("v",11), JsonObject.of("v",21) ), list(db.select().collection("c2")) );
		
		assertEquals( List.of(JsonObject.of("v",10), JsonObject.of("v",11) ), list(db.select().filter(r->r.getKey().getId().equals("k1"))) );

		List<JsonDbRecord> c1 = db.select().collection("c1").collect();
		assertEquals( 3, c1.size() );

		AtomicInteger ct1 = new AtomicInteger(); 
		db.select().collection("c1").forEach( r-> ct1.set(ct1.get()+1));
	}
	
	// D1: delete(Predicate) records the deletions, replicates them and invalidates open transactions
	public void testDeleteWithPredicate() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(JsonKey.of("c1","k1"), JsonObject.of("v",10));
		db.insert(JsonKey.of("c1","k2"), JsonObject.of("v",20));
		db.insert(JsonKey.of("c1","k3"), JsonObject.of("v",30));

		Transaction t = db.beginTransaction();
		db.delete( r -> ((JsonObject)r.getJson()).getInt("v")>=20 );
		assertEquals(1, db.count());
		assertEquals(2, db.deletedCount());
		assertTrue (db.exists(JsonKey.of("c1","k1")));
		assertFalse(db.isDeleted(JsonKey.of("c1","k1")));
		assertTrue (db.isDeleted(JsonKey.of("c1","k2")));
		assertTrue (db.isDeleted(JsonKey.of("c1","k3")));

		// The transaction was opened before the deletion, it cannot resurrect the records
		assertThrows(JsonException.class, () -> t.commit() );
		assertEquals(1, db.count());
		assertFalse(db.exists(JsonKey.of("c1","k2")));

		// Deleting nothing does not change the DB
		Transaction t2 = db.beginTransaction();
		db.delete( r -> false );
		assertEquals(1, db.count());
		assertEquals(2, db.deletedCount());
		t2.commit();
	}

	public void testDeleteWithPredicateReplicates() throws Exception {
		MemoryJsonDb db1 = new MemoryJsonDb();
		MemoryJsonDb db2 = new MemoryJsonDb();
		JsonDbSource source = JsonDbSource.newBuilder().db(db1).build();
		JsonDbTarget target = JsonDbTarget.newBuilder().db(db2).replicationTable(new MemoryReplicationTable()).build();

		db1.insert(JsonKey.of("c1","k1"), JsonObject.of("v",10));
		db1.insert(JsonKey.of("c1","k2"), JsonObject.of("v",20));
		db1.insert(JsonKey.of("c1","k3"), JsonObject.of("v",30));
		sleep();
		target.replicate(source, null);
		assertEquals(3, db2.count());

		db1.delete( r -> r.getKey().getId().equals("k2") );
		sleep();
		target.replicate(source, null);
		assertEquals(2, db2.count());
		assertFalse(db2.exists(JsonKey.of("c1","k2")));
		assertTrue (db2.isDeleted(JsonKey.of("c1","k2")));
		assertTrue (db2.exists(JsonKey.of("c1","k1")));
		assertTrue (db2.exists(JsonKey.of("c1","k3")));
	}

	// D3: the DB can be modified while a select is being iterated
	public void testDeleteWhileIterating() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(JsonKey.of("c1","k1"), JsonObject.of("v",10));
		db.insert(JsonKey.of("c1","k2"), JsonObject.of("v",20));
		db.insert(JsonKey.of("c1","k3"), JsonObject.of("v",30));

		db.select().forEach( r -> {
			if(!r.getKey().getId().equals("k1")) {
				db.delete(r.getKey());
			}
		});
		assertEquals(1, db.count());
		assertEquals(2, db.deletedCount());

		db.select().stream().forEach( r -> db.upsert(JsonKey.of("c1","k4"), JsonObject.of("v",40)) );
		assertEquals(2, db.count());

		for(Iterator<JsonDbRecord> it=db.select().records(); it.hasNext(); ) {
			db.delete(it.next().getKey());
		}
		assertEquals(0, db.count());
	}

	private static List<Object> list(JsonSelect select) {
		List<Object> l = new ArrayList<>();
		select.records().forEachRemaining( r -> l.add(r.getJson()) );
		return l;
	}
	
	public void testTransactions() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();

		db.insert(JsonKey.of("c1","k1"), JsonObject.of("v",10));
		db.insert(JsonKey.of("c1","k2"), JsonObject.of("v",20));
		db.insert(JsonKey.of("c1","k3"), JsonObject.of("v",30));
		
		{ // Basic commit & rollback, transaction no longer valid afterwards
			Transaction t1 = db.beginTransaction();
			assertTrue(t1.exists(JsonKey.of("c1","k1")));
			t1.commit();
			assertThrows(JsonException.class, () -> t1.exists(JsonKey.of("c1","k1")) );

			Transaction t2 = db.beginTransaction();
			assertTrue(t2.exists(JsonKey.of("c1","k1")));
			t2.commit();
			assertThrows(JsonException.class, () -> t2.exists(JsonKey.of("c1","k1")) );
		}
		
		{ // Test an insertion in a transaction
			Transaction t = db.beginTransaction();
			t.insert(JsonKey.of("c1","k4"), JsonObject.of("v",40));
			
			assertEquals(3, db.count());
			assertFalse (db.exists(JsonKey.of("c1","k4")));
			assertEquals(4, t.count());
			assertTrue  (t.exists(JsonKey.of("c1","k4")));
			
			t.commit();
			assertEquals(4, db.count());
			assertTrue  (db.exists(JsonKey.of("c1","k4")));
		}
		
		{ // Test a deletion in a transaction
			Transaction t = db.beginTransaction();
			t.delete(JsonKey.of("c1","k2"));
			
			assertEquals(4, db.count());
			assertTrue  (db.exists(JsonKey.of("c1","k2")));
			assertEquals(3, t.count());
			assertFalse (t.exists(JsonKey.of("c1","k2")));
			
			t.commit();
			assertEquals(3, db.count());
			assertFalse (db.exists(JsonKey.of("c1","k2")));
		}

		{ // Check for a conflict between the DB and a transaction
			Transaction t = db.beginTransaction();
			db.insert(JsonKey.of("c1","k5"), JsonObject.of("v",50));
			t.insert(JsonKey.of("c1","k6"), JsonObject.of("v",60));

			assertThrows(JsonException.class, () -> t.commit() );
			assertTrue  (db.exists(JsonKey.of("c1","k5")));
			assertFalse (db.exists(JsonKey.of("c1","k6")));
		}

		{ // Check for a conflict between the DB 2 transactions
			Transaction t1 = db.beginTransaction();
			Transaction t2 = db.beginTransaction();
			t1.insert(JsonKey.of("c1","t1"), JsonObject.of("v",11));
			t2.insert(JsonKey.of("c1","t2"), JsonObject.of("v",22));

			t1.commit();
			assertThrows(JsonException.class, () -> t2.commit() );
			
			assertTrue  (db.exists(JsonKey.of("c1","t1")));
			assertFalse (db.exists(JsonKey.of("c1","t2")));
		}


	}
}
