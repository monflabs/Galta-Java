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
package org.monflabs.json.impexp.db;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;

/**
 * Simple, in memory JSON Database.
 * <p>
 * The records are indexed by collection, so selecting a collection only copies (and scans)
 * the records of that collection.
 * <p>
 * The JSON values are stored as is, they are not copied: a value must not be modified
 * once it has been stored (or once it has been read), as this would change the record
 * without updating its timestamps, and outside of any transaction.
 * <p>
 * <b>Transactions</b> ({@link #beginTransaction()}) only hold their own changes, and read
 * through to the database for the other records: they see the committed state of the
 * database plus their own changes. A commit fails, and changes nothing, if one of the
 * records the transaction writes was changed in the database after the transaction
 * started, or if the database was cleared after the transaction started. The changes of a
 * transaction are stamped with the commit time as their {@link JsonDbRecord#getDbAdded()}
 * date, so a replication that ran while the transaction was pending still picks them up.
 *
 * @author priand
 *
 */
public class MemoryJsonDb {

	// Versions of the stored entries and of the clears. A single, global sequence, so the
	// versions of a database and of its transactions (which read through to the database)
	// can always be compared.
	private static final AtomicLong VERSIONS = new AtomicLong();

	private abstract static class DbEntry implements JsonDbRecord, JsonContent {

		private final JsonKey key;
		private final Instant timestamp;
		private final Instant dbadded;
		private final long version;

		private DbEntry(JsonKey key, Instant timestamp, Instant dbadded, long version) {
			this.key = key;
			this.timestamp = timestamp;
			this.dbadded = dbadded;
			this.version = version;
		}

		@Override
		public JsonKey getKey() {
			return key;
		}

		@Override
		public Instant getTimestamp() {
			return timestamp;
		}

		@Override
		public Instant getDbAdded() {
			return dbadded;
		}

		/**
		 * The same entry, stored at another date (when a transaction is committed).
		 */
		abstract DbEntry restamp(Instant dbadded, long version);
	}
	private static class RecordEntry extends DbEntry {

		private final Object json;

		private RecordEntry(JsonKey key, Object json, Instant timestamp, Instant dbadded, long version) {
			super(key,timestamp,dbadded,version);
			this.json = json;
		}

		@Override
		public Object getJson() {
			return json;
		}

		@Override
		public TYPE getType() {
			return TYPE.RECORD;
		}

		@Override
		DbEntry restamp(Instant dbadded, long version) {
			return new RecordEntry(getKey(), json, getTimestamp(), dbadded, version);
		}
	}
	private static class DeletionEntry extends DbEntry {

		private DeletionEntry(JsonKey key, Instant timestamp, Instant dbadded, long version) {
			super(key,timestamp,dbadded,version);
		}

		@Override
		public Object getJson() {
			return null;
		}

		@Override
		public TYPE getType() {
			return TYPE.DELETION;
		}

		@Override
		DbEntry restamp(Instant dbadded, long version) {
			return new DeletionEntry(getKey(), getTimestamp(), dbadded, version);
		}
	}

	private final String replicationId;

	// The storage of the database (a transaction only holds its own changes, see Transaction)
	private final Map<JsonKey,RecordEntry> records = new LinkedHashMap<>();
	private final Map<JsonKey,DeletionEntry> deleted = new LinkedHashMap<>();
	// The same entries, by collection
	private final Map<String,Map<JsonKey,RecordEntry>> recordsByCollection = new HashMap<>();
	private final Map<String,Map<JsonKey,DeletionEntry>> deletedByCollection = new HashMap<>();
	// Version of the last clear()
	private long clearVersion;

	public MemoryJsonDb() {
		this(java.util.UUID.randomUUID().toString());
	}
	private MemoryJsonDb(String replicationId) {
		this.replicationId = replicationId;
	}

	protected void checkValid() {
	}

	private static long nextVersion() {
		return VERSIONS.incrementAndGet();
	}


	//
	// Storage primitives, overridden by a transaction.
	// They are synchronized: a transaction locks itself, then its parent.
	//

	/** The record or the deletion of a key, or null. */
	synchronized DbEntry entry(JsonKey key) {
		RecordEntry r = records.get(key);
		return r!=null ? r : deleted.get(key);
	}
	/** Stores a record or a deletion, replacing the current entry of the key. */
	synchronized void store(DbEntry e) {
		JsonKey key = e.getKey();
		String col = key.getCollection();
		if(e instanceof RecordEntry r) {
			removeEntry(deleted, deletedByCollection, key);
			records.put(key, r);
			recordsByCollection.computeIfAbsent(col, (c) -> new LinkedHashMap<>()).put(key, r);
		} else {
			DeletionEntry d = (DeletionEntry)e;
			removeEntry(records, recordsByCollection, key);
			deleted.put(key, d);
			deletedByCollection.computeIfAbsent(col, (c) -> new LinkedHashMap<>()).put(key, d);
		}
	}
	private static <E> void removeEntry(Map<JsonKey,E> all, Map<String,Map<JsonKey,E>> byCollection, JsonKey key) {
		if(all.remove(key)!=null) {
			Map<JsonKey,E> m = byCollection.get(key.getCollection());
			if(m!=null) {
				m.remove(key);
				if(m.isEmpty()) {
					byCollection.remove(key.getCollection());
				}
			}
		}
	}
	/**
	 * The records of a collection (all when null), in insertion order. This can be a live
	 * view: it must be consumed while the lock is held.
	 */
	synchronized Collection<? extends DbEntry> recordEntries(String collection) {
		if(collection==null) {
			return records.values();
		}
		Map<JsonKey,RecordEntry> m = recordsByCollection.get(collection);
		return m!=null ? m.values() : List.of();
	}
	/** Like {@link #recordEntries(String)}, for the deletions. */
	synchronized Collection<? extends DbEntry> deletionEntries(String collection) {
		if(collection==null) {
			return deleted.values();
		}
		Map<JsonKey,DeletionEntry> m = deletedByCollection.get(collection);
		return m!=null ? m.values() : List.of();
	}
	synchronized int recordCount() {
		return records.size();
	}
	synchronized int deletionCount() {
		return deleted.size();
	}
	/** Removes all the entries, including the deletions. */
	synchronized void clearEntries() {
		records.clear();
		deleted.clear();
		recordsByCollection.clear();
		deletedByCollection.clear();
		clearVersion = nextVersion();
	}
	/** The version of the last clear (of the entries seen by this DB). */
	synchronized long lastClearVersion() {
		return clearVersion;
	}
	/** The current version: everything stored from now on gets a higher version. */
	synchronized long currentVersion() {
		return VERSIONS.get();
	}
	/**
	 * Applies the changes of a transaction, or throws an exception (and changes nothing)
	 * if they conflict with changes made since the transaction started.
	 */
	synchronized void apply(Transaction t) {
		checkValid();
		if(lastClearVersion()>t.startVersion) {
			throw new JsonException(null,"Database has been cleared since the transaction started");
		}
		for(JsonKey key: t.writes.keySet()) {
			DbEntry current = entry(key);
			if(current!=null && current.version>t.startVersion) {
				throw new JsonException(null,"Database has been modified since the transaction started (record {0})", key);
			}
		}
		if(t.cleared) {
			clearEntries();
		}
		// Stamped with the commit date: a replication that ran while the transaction was
		// pending did not see these changes, the next one must pick them up
		Instant now = Instant.now();
		for(DbEntry e: t.writes.values()) {
			store(e.restamp(now, nextVersion()));
		}
	}


	//
	// Public API
	//

	public synchronized String getReplicationId() {
		checkValid();
		return "MemoryDb:"+replicationId;
	}

	public synchronized Instant now() {
		checkValid();
		return Instant.now();
	}

	public synchronized int count() {
		checkValid();
		return recordCount();
	}

	public synchronized int deletedCount() {
		checkValid();
		return deletionCount();
	}

	/**
	 * Removes all the records and the deletions, without recording the deletions: a
	 * replication from this DB does not propagate a clear. Use {@link #clear(boolean)}
	 * to record them.
	 * <p>
	 * A transaction started before the clear can no longer be committed.
	 */
	public synchronized void clear() {
		clear(false);
	}

	/**
	 * Removes all the records.
	 * @param recordDeletions true to record a deletion for every record, like
	 * {@link #delete(JsonKey)} does, so the clear is replicated. When false, the records and
	 * the existing deletions are dropped, see {@link #clear()}.
	 */
	public synchronized void clear(boolean recordDeletions) {
		checkValid();
		if(recordDeletions) {
			List<JsonKey> keys = new ArrayList<>(recordCount());
			for(DbEntry e: recordEntries(null)) {
				keys.add(e.getKey());
			}
			Instant now = now();
			for(JsonKey key: keys) {
				store(new DeletionEntry(key,now,now,nextVersion()));
			}
		} else {
			clearEntries();
		}
	}

	public synchronized boolean exists(JsonKey key) {
		checkValid();
		return entry(key) instanceof RecordEntry;
	}

	public synchronized boolean isDeleted(JsonKey key) {
		checkValid();
		return entry(key) instanceof DeletionEntry;
	}

	public synchronized JsonDbRecord select(JsonKey key) {
		checkValid();
		return entry(key) instanceof RecordEntry r ? r : null;
	}

	public synchronized JsonSelect select() {
		checkValid();
		return new JsonSelect(this);
	}

	public synchronized Set<String> getCollections() {
		checkValid();
		Set<String> cols = new LinkedHashSet<>();
		for(DbEntry e: recordEntries(null)) {
			cols.add(e.getKey().getCollection());
		}
		return cols;
	}

	public synchronized JsonDbRecord insert(JsonKey key, Object content) {
		return put(key, content, Boolean.FALSE);
	}

	public synchronized JsonDbRecord update(JsonKey key, Object content) {
		return put(key, content, Boolean.TRUE);
	}

	public synchronized JsonDbRecord upsert(JsonKey key, Object content) {
		return put(key, content, null);
	}

	/**
	 * Stores a record.
	 * @param mustExist true if the record must already exist (update), false if it must
	 * not (insert), null if it does not matter (upsert)
	 */
	private JsonDbRecord put(JsonKey key, Object content, Boolean mustExist) {
		checkValid();
		if(mustExist!=null && (entry(key) instanceof RecordEntry)!=mustExist) {
			throw new JsonException(null, mustExist ? "Record with key {0} does not exist" : "Record with key {0} already exists", key);
		}
		Instant now = now();
		RecordEntry r = new RecordEntry(key,content,now,now,nextVersion());
		store(r);
		return r;
	}

	public synchronized void delete(JsonKey key) {
		checkValid();
		if(!(entry(key) instanceof RecordEntry)) {
			throw new JsonException(null, "Record with key {0} does not exist", key);
		}
		Instant now = now();
		store(new DeletionEntry(key,now,now,nextVersion()));
	}

	/**
	 * Deletes a set of records.
	 * <p>
	 * All the keys are checked first: if one of them does not exist, an exception is
	 * thrown and no record is deleted. A key present more than once is deleted once.
	 */
	public synchronized void delete(Collection<JsonKey> keys) {
		checkValid();
		// A duplicate key would fail on its second deletion, after the first keys were deleted
		Set<JsonKey> unique = new LinkedHashSet<>(keys);
		for(JsonKey key: unique) {
			if(!(entry(key) instanceof RecordEntry)) {
				throw new JsonException(null, "Record with key {0} does not exist", key);
			}
		}
		for(JsonKey key: unique) {
			delete(key);
		}
	}

	public synchronized void delete(Predicate<JsonDbRecord> filter) {
		checkValid();
		// Collect first, then go through the regular deletion path so the deletions
		// are recorded (replication)
		List<JsonKey> keys = new ArrayList<>();
		for(DbEntry r: recordEntries(null)) {
			if(filter.test(r)) {
				keys.add(r.getKey());
			}
		}
		delete(keys);
	}

	/**
	 * Execute a DB operation while synchronized.
	 * This prevents other threads from changing the DB at the same time.
	 * @param <T>
	 * @param db
	 * @return
	 */
	public synchronized <T> T execute(Function<MemoryJsonDb,T> db) {
		return db.apply(this);
	}

	public synchronized JsonObject serialize() {
		checkValid();
		JsonObject ca = JsonObject.create();
		select().records().forEachRemaining( (r) -> {
			ca.put(r.getKey().keyString(), r.getJson());
		});
		return ca;
	}

	public synchronized JsonObject serialize(boolean sorted) {
		checkValid();
		JsonObject o = serialize();
		if(sorted) {
			return JsonObject.parse(o.factory().stringifySorted(o));
		}
		return o;
	}


	//
	// Transaction management
	//

	/**
	 * A transaction holds its own changes (the records and deletions it writes) and reads
	 * through to its parent DB for the other records, see the class documentation.
	 */
	public class Transaction extends MemoryJsonDb {

		private final MemoryJsonDb parent;
		private final long startVersion;
		// The records and deletions written by the transaction
		private final LinkedHashMap<JsonKey,DbEntry> writes = new LinkedHashMap<>();
		// True when the transaction cleared the DB: the parent entries are no longer visible
		private boolean cleared;
		private long txClearVersion;
		private boolean finished;

		public Transaction() {
			super(MemoryJsonDb.this.replicationId);
			this.parent = MemoryJsonDb.this;
			this.startVersion = parent.currentVersion();
		}
		@Override
		protected void checkValid() {
			if(finished) {
				throw new JsonException(null,"Transaction has already been committed or rollback");
			}
		}

		@Override
		synchronized DbEntry entry(JsonKey key) {
			DbEntry e = writes.get(key);
			if(e!=null || cleared) {
				return e;
			}
			return parent.entry(key);
		}
		@Override
		synchronized void store(DbEntry e) {
			writes.put(e.getKey(), e);
		}
		@Override
		synchronized Collection<? extends DbEntry> recordEntries(String collection) {
			return merge(collection, RecordEntry.class);
		}
		@Override
		synchronized Collection<? extends DbEntry> deletionEntries(String collection) {
			return merge(collection, DeletionEntry.class);
		}
		/**
		 * The parent entries of a type, as changed by the transaction, followed by the
		 * entries of that type the transaction added.
		 */
		private List<DbEntry> merge(String collection, Class<? extends DbEntry> type) {
			synchronized(parent) {
				Collection<? extends DbEntry> pe = cleared ? List.of() : (type==RecordEntry.class ? parent.recordEntries(collection) : parent.deletionEntries(collection));
				List<DbEntry> l = new ArrayList<>(pe.size()+writes.size());
				for(DbEntry e: pe) {
					DbEntry w = writes.get(e.getKey());
					if(w==null) {
						l.add(e);
					} else if(type.isInstance(w)) {
						l.add(w);
					}
				}
				for(DbEntry w: writes.values()) {
					if(type.isInstance(w) && (collection==null || collection.equals(w.getKey().getCollection()))) {
						if(cleared || !type.isInstance(parent.entry(w.getKey()))) {
							l.add(w);
						}
					}
				}
				return l;
			}
		}
		@Override
		synchronized int recordCount() {
			return count(RecordEntry.class);
		}
		@Override
		synchronized int deletionCount() {
			return count(DeletionEntry.class);
		}
		private int count(Class<? extends DbEntry> type) {
			synchronized(parent) {
				int count = cleared ? 0 : (type==RecordEntry.class ? parent.recordCount() : parent.deletionCount());
				for(DbEntry w: writes.values()) {
					boolean was = !cleared && type.isInstance(parent.entry(w.getKey()));
					boolean is = type.isInstance(w);
					if(was && !is) {
						count--;
					} else if(!was && is) {
						count++;
					}
				}
				return count;
			}
		}
		@Override
		synchronized void clearEntries() {
			writes.clear();
			cleared = true;
			txClearVersion = nextVersion();
		}
		@Override
		synchronized long lastClearVersion() {
			return cleared ? txClearVersion : parent.lastClearVersion();
		}
		@Override
		synchronized long currentVersion() {
			return parent.currentVersion();
		}

		/**
		 * Commits the changes to the parent DB.
		 * @throws JsonException if a record written by the transaction was changed in the DB
		 * since the transaction started, or if the DB was cleared. The transaction is then
		 * still open, and has to be rolled back.
		 */
		public synchronized void commit() {
			checkValid();
			parent.apply(this);
			finished = true;
		}

		public synchronized void rollback() {
			checkValid();
			finished = true;
		}
	}

	public synchronized Transaction beginTransaction() {
		checkValid();
		return new Transaction();
	}


	//
	// Content import and replication
	// This method do not generate error
	//

	/**
	 * Number of records of a collection (all when null) matching a filter (null for all),
	 * without copying them. The filter is called while the DB is locked.
	 */
	synchronized int countRecords(String collection, Predicate<JsonDbRecord> filter) {
		checkValid();
		if(filter==null && collection==null) {
			return recordCount();
		}
		int count = 0;
		for(DbEntry r: recordEntries(collection)) {
			if(filter==null || filter.test(r)) {
				count++;
			}
		}
		return count;
	}
	/**
	 * First record of a collection (all when null) matching a filter (null for all), or
	 * null, without copying the records. The filter is called while the DB is locked.
	 */
	synchronized JsonDbRecord firstRecord(String collection, Predicate<JsonDbRecord> filter) {
		checkValid();
		for(DbEntry r: recordEntries(collection)) {
			if(filter==null || filter.test(r)) {
				return r;
			}
		}
		return null;
	}
	/**
	 * Snapshot of the records of a collection (all when null), so a select can be iterated
	 * while the DB is modified.
	 */
	synchronized List<JsonDbRecord> snapshotRecords(String collection) {
		checkValid();
		return new ArrayList<>(recordEntries(collection));
	}
	/**
	 * Snapshot of the records followed by the deletions of a collection (all when null), so
	 * a source can be streamed while the DB is modified (including by the target of the
	 * same import).
	 */
	synchronized List<JsonDbRecord> snapshotForReplication(String collection) {
		checkValid();
		Collection<? extends DbEntry> r = recordEntries(collection);
		Collection<? extends DbEntry> d = deletionEntries(collection);
		List<JsonDbRecord> l = new ArrayList<>(r.size()+d.size());
		l.addAll(r);
		l.addAll(d);
		return l;
	}

	public synchronized JsonContent readJsonContent(JsonKey key) {
		checkValid();
		return entry(key);
	}
	public synchronized void saveJsonContent(JsonContent content) {
		checkValid();
		JsonKey key = content.getKey();
		// Just save in the DB
		Instant now = now();
		switch(content.getType()) {
			case RECORD -> {
				store(new RecordEntry(key,content.getJson(),content.getTimestamp(),now,nextVersion()));
			}
			case DELETION -> {
				store(new DeletionEntry(key,content.getTimestamp(),now,nextVersion()));
			}
		}
	}
}
