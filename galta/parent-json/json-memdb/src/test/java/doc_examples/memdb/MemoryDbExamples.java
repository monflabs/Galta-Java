package doc_examples.memdb;

import java.util.List;
import java.util.stream.Collectors;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.db.JsonDbRecord;
import org.monflabs.json.impexp.db.JsonDbSource;
import org.monflabs.json.impexp.db.JsonDbTarget;
import org.monflabs.json.impexp.db.MemoryJsonDb;
import org.monflabs.json.impexp.db.MemoryReplicationTable;
import org.monflabs.json.impexp.replication.ConflictResolver;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.util.StaticContent;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/MemoryDb.md
 * (and the replication part of ImportExport.md)
 */
public class MemoryDbExamples extends ProjectTestCase {

	public void testCrud() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		JsonKey ada = JsonKey.of("people", "ada");

		db.insert(ada, JsonObject.of("name", "Ada", "born", 1815));
		db.insert(JsonKey.of("people", "alan"), JsonObject.of("name", "Alan", "born", 1912));
		db.insert(JsonKey.of("teams", "t1"), JsonObject.of("name", "Engines"));
		assertEquals(3, db.count());
		assertEquals(List.of("people", "teams"), List.copyOf(db.getCollections()));

		JsonDbRecord r = db.select(ada);                         // null when missing
		assertEquals("Ada", ((JsonObject)r.getJson()).getString("name"));
		assertNotNull(r.getTimestamp());

		db.update(ada, JsonObject.of("name", "Ada Lovelace", "born", 1815));
		db.upsert(JsonKey.of("people", "grace"), JsonObject.of("name", "Grace", "born", 1906));
		db.delete(JsonKey.of("teams", "t1"));
		assertEquals(3, db.count());
		assertTrue(db.isDeleted(JsonKey.of("teams", "t1")));    // a tombstone is kept
		assertEquals(1, db.deletedCount());

		try {
			db.insert(ada, JsonObject.create());                  // insert: must not exist
			fail();
		} catch(JsonException e) {
			// Record with key people:ada already exists
		}
		try {
			db.update(JsonKey.of("people", "nobody"), JsonObject.create());   // update/delete: must exist
			fail();
		} catch(JsonException e) {
			// Record with key people:nobody does not exist
		}
	}

	public void testValuesAreNotCopied() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		JsonObject doc = JsonObject.of("v", 1);
		db.insert(JsonKey.of("c", "k"), doc);
		doc.put("v", 2);                                          // changes the stored value too
		assertEquals(2, ((JsonObject)db.select(JsonKey.of("c", "k")).getJson()).getInt("v"));
	}

	public void testSelect() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(JsonKey.of("people", "ada"), JsonObject.of("name", "Ada", "born", 1815));
		db.insert(JsonKey.of("people", "alan"), JsonObject.of("name", "Alan", "born", 1912));
		db.insert(JsonKey.of("people", "grace"), JsonObject.of("name", "Grace", "born", 1906));
		db.insert(JsonKey.of("teams", "t1"), JsonObject.of("name", "Engines"));

		List<String> modern = db.select()
				.collection("people")
				.filter(r -> ((JsonObject)r.getJson()).getInt("born") > 1900)
				.stream()
				.map(r -> ((JsonObject)r.getJson()).getString("name"))
				.collect(Collectors.toList());
		assertEquals(List.of("Alan", "Grace"), modern);            // insertion order

		assertEquals(3, db.select().collection("people").count());
		assertEquals(JsonKey.of("teams", "t1"), db.select().collection("teams").keys().get(0));
		assertEquals("Ada", ((JsonObject)db.select().first().getJson()).getString("name"));

		// Iterating a select works on a snapshot: the DB can be changed meanwhile
		db.select().collection("people").forEach(r -> db.delete(r.getKey()));
		assertEquals(1, db.count());

		// Deleting by predicate
		db.delete(r -> r.getKey().getCollection().equals("teams"));
		assertEquals(0, db.count());
	}

	public void testSerialize() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(JsonKey.of("people", "ada"), JsonObject.of("name", "Ada"));
		db.insert(JsonKey.of(null, "settings"), JsonObject.of("theme", "dark"));
		assertEquals(JsonObject.parse("""
				{ "people!!ada": { "name": "Ada" }, "settings": { "theme": "dark" } }
				"""), db.serialize());
	}

	public void testTransactions() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		db.insert(JsonKey.of("c", "k1"), JsonObject.of("v", 1));

		MemoryJsonDb.Transaction tx = db.beginTransaction();
		tx.insert(JsonKey.of("c", "k2"), JsonObject.of("v", 2));
		tx.delete(JsonKey.of("c", "k1"));
		assertEquals(1, tx.count());
		assertTrue(db.exists(JsonKey.of("c", "k1")));             // not visible until committed
		tx.commit();
		assertFalse(db.exists(JsonKey.of("c", "k1")));
		assertTrue(db.exists(JsonKey.of("c", "k2")));

		// Rollback: nothing happened
		MemoryJsonDb.Transaction tx2 = db.beginTransaction();
		tx2.insert(JsonKey.of("c", "k3"), JsonObject.of("v", 3));
		tx2.rollback();
		assertFalse(db.exists(JsonKey.of("c", "k3")));

		// Optimistic: a commit fails if a record it writes changed since the transaction began
		MemoryJsonDb.Transaction tx3 = db.beginTransaction();
		tx3.upsert(JsonKey.of("c", "k4"), JsonObject.of("v", 4));
		db.insert(JsonKey.of("c", "k4"), JsonObject.of("v", 40));
		try {
			tx3.commit();
			fail();
		} catch(JsonException e) {
			// Database has been modified since the transaction started (record c:k4)
		}
		assertEquals(40, ((JsonObject)db.select(JsonKey.of("c", "k4")).getJson()).getInt("v"));
		tx3.rollback();                                           // a failed commit leaves it open
		try {
			tx3.count();                                          // a finished transaction cannot be used
			fail();
		} catch(JsonException e) {
			// Transaction has already been committed or rollback
		}
	}

	public void testExecute() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		// Several operations under the DB lock
		int n = db.execute(d -> {
			if(!d.exists(JsonKey.of("c", "counter"))) {
				d.insert(JsonKey.of("c", "counter"), 0);
			}
			int v = (Integer)d.select(JsonKey.of("c", "counter")).getJson() + 1;
			d.update(JsonKey.of("c", "counter"), v);
			return v;
		});
		assertEquals(1, n);
	}

	public void testImportExport() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		JsonDbTarget target = JsonDbTarget.newBuilder()
				.db(db)
				.build();
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.container(JsonObject.parse("""
						{ "people": { "ada": { "name": "Ada" }, "alan": { "name": "Alan" } },
						  "teams":  { "t1": { "name": "Engines" } } }
						"""))
				.build();
		target.importFrom(source);
		assertEquals(3, db.count());

		// Export one collection
		JsonDbSource people = JsonDbSource.newBuilder()
				.db(db)
				.collection("people")
				.build();
		JsonContainerTarget out = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYKEY)
				.build();
		people.exportTo(out);
		assertEquals(JsonObject.parse("{ \"ada\": { \"name\": \"Ada\" }, \"alan\": { \"name\": \"Alan\" } }"),
				out.getContainer());
	}

	public void testImportIsTransactional() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		JsonDbTarget target = JsonDbTarget.newBuilder()
				.db(db)
				.afterProcessing(c -> {
					if(c.getKey().getId().equals("2")) {
						throw new IllegalStateException("boom");
					}
				})
				.build();
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(org.monflabs.json.JsonArray.of("a", "b", "c", "d"))
				.build();
		try {
			target.importFrom(source);
			fail();
		} catch(JsonException e) {
			// the whole import is rolled back
		}
		assertEquals(0, db.count());

		// With a threshold, the import commits every N documents
		JsonDbTarget chunked = JsonDbTarget.newBuilder()
				.db(db)
				.transactionThreshold(2)
				.afterProcessing(c -> {
					if(c.getKey().getId().equals("2")) {
						throw new IllegalStateException("boom");
					}
				})
				.build();
		try {
			chunked.importFrom(source);
			fail();
		} catch(JsonException e) {
			// the documents of the committed chunks stay
		}
		assertEquals(2, db.count());
	}

	public void testReplication() throws Exception {
		MemoryJsonDb master = new MemoryJsonDb();
		MemoryJsonDb replica = new MemoryJsonDb();
		master.insert(JsonKey.of("c", "k1"), JsonObject.of("v", 1));
		master.insert(JsonKey.of("c", "k2"), JsonObject.of("v", 2));
		// The replicated documents keep their source timestamp: it must be strictly before the
		// replication date, or the next replication reports them as conflicts
		sleep();

		JsonDbSource source = JsonDbSource.newBuilder().db(master).build();
		JsonDbTarget target = JsonDbTarget.newBuilder()
				.db(replica)
				.replicationTable(new MemoryReplicationTable())   // remembers the last replication
				.build();

		ReplicationResult r1 = target.replicate(source, ConflictResolver.FAIL_EXCEPTION);
		assertEquals(2, r1.getInserted());
		assertEquals(2, r1.getProcessed());
		assertEquals(2, replica.count());

		// Next time, only the changes since the last replication are sent, deletions included
		sleep();
		master.update(JsonKey.of("c", "k1"), JsonObject.of("v", 10));
		master.delete(JsonKey.of("c", "k2"));
		sleep();
		ReplicationResult r2 = target.replicate(source, ConflictResolver.FAIL_EXCEPTION);
		assertEquals(1, r2.getInserted());
		assertEquals(1, r2.getDeleted());
		assertEquals(2, r2.getProcessed());
		assertEquals(JsonObject.of("v", 10), replica.select(JsonKey.of("c", "k1")).getJson());
		assertFalse(replica.exists(JsonKey.of("c", "k2")));
	}

	public void testConflicts() throws Exception {
		MemoryJsonDb master = new MemoryJsonDb();
		MemoryJsonDb replica = new MemoryJsonDb();
		JsonDbSource source = JsonDbSource.newBuilder().db(master).build();
		JsonDbTarget target = JsonDbTarget.newBuilder()
				.db(replica)
				.replicationTable(new MemoryReplicationTable())
				.build();
		target.replicate(source, null);                           // first, empty, replication

		// Both sides change the same document between two replications
		sleep();
		master.insert(JsonKey.of("c", "k"), JsonObject.of("v", "master"));
		sleep();
		replica.insert(JsonKey.of("c", "k"), JsonObject.of("v", "replica"));
		sleep();

		try {
			target.replicate(source, null);                       // null means FAIL_EXCEPTION
			fail();
		} catch(JsonException e) {
			// Replication conflict on c:k - and the replication is rolled back
		}
		assertEquals("replica", ((JsonObject)replica.select(JsonKey.of("c", "k")).getJson()).getString("v"));

		ReplicationResult r = target.replicate(source, ConflictResolver.SOURCE_WINS);
		assertEquals(1, r.getConflicts());
		assertEquals(0, r.getInserted());                       // the resolved document is counted once, as a conflict
		assertEquals(1, r.getProcessed());
		assertEquals("master", ((JsonObject)replica.select(JsonKey.of("c", "k")).getJson()).getString("v"));
	}

	public void testCustomResolver() throws Exception {
		MemoryJsonDb master = new MemoryJsonDb();
		MemoryJsonDb replica = new MemoryJsonDb();
		JsonDbSource source = JsonDbSource.newBuilder().db(master).build();
		JsonDbTarget target = JsonDbTarget.newBuilder()
				.db(replica)
				.replicationTable(new MemoryReplicationTable())
				.build();
		master.insert(JsonKey.of("c", "k"), JsonObject.of("v", 1));
		replica.insert(JsonKey.of("c", "k"), JsonObject.of("v", 2));

		// Keep both versions: the resolver returns the contents to save
		ReplicationResult r = target.replicate(source, (src, tgt) -> new JsonContent[] {
				new StaticContent(JsonKey.of("c", "k"), src.getJson(), src.getTimestamp()),
				new StaticContent(JsonKey.of("conflicts", "k"), tgt.getJson(), tgt.getTimestamp())
		});
		assertEquals(1, r.getConflicts());
		assertEquals(1, r.getProcessed());
		assertEquals(JsonObject.of("v", 1), replica.select(JsonKey.of("c", "k")).getJson());
		assertEquals(JsonObject.of("v", 2), replica.select(JsonKey.of("conflicts", "k")).getJson());

		// Same value on both sides: not a conflict
		MemoryJsonDb replica2 = new MemoryJsonDb();
		replica2.insert(JsonKey.of("c", "k"), JsonObject.of("v", 1));
		ReplicationResult r2 = JsonDbTarget.newBuilder()
				.db(replica2)
				.replicationTable(new MemoryReplicationTable())
				.build()
				.replicate(source, null);
		assertEquals(0, r2.getConflicts());
		assertEquals(1, r2.getIgnored());
	}
}
