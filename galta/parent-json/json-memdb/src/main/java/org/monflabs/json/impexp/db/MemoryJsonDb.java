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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;

/**
 * Simple, in memory JSON Database.
 * 
 * @author priand
 *
 */
public class MemoryJsonDb {
	
	private abstract static class DbEntry implements JsonDbRecord, JsonContent {

		private JsonKey key;
		private Instant timestamp;
		private Instant dbadded;
		
		private DbEntry(JsonKey key, Instant timestamp, Instant dbadded) {
			this.key = key;
			this.timestamp = timestamp;
			this.dbadded = dbadded;
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
	}
	private static class RecordEntry extends DbEntry {

		private Object json;

		private RecordEntry(JsonKey key, Object json, Instant timestamp, Instant dbadded) {
			super(key,timestamp,dbadded);
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
	}
	private static class DeletionEntry extends DbEntry {

		private DeletionEntry(JsonKey key, Instant timestamp, Instant dbadded) {
			super(key,timestamp,dbadded);
		}

		@Override
		public Object getJson() {
			return null;
		}
		
		@Override
		public TYPE getType() {
			return TYPE.DELETION;
		}
	}
	
	protected String replicationId;
	protected int transactionId;
	protected Map<JsonKey,RecordEntry> records = new LinkedHashMap<>();
	protected Map<JsonKey,DeletionEntry> deleted = new LinkedHashMap<>();
	protected Map<String,Instant> replicationDates = new HashMap<>();

	public MemoryJsonDb() {		
		this.replicationId = java.util.UUID.randomUUID().toString();
	}

	protected void checkValid() {		
	}
	
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
		return records.size();
	}
	
	public synchronized int deletedCount() {
		checkValid();
		return deleted.size();
	}
	
	public synchronized void clear() {
		checkValid();
		// A transaction started before the clear must not silently overwrite it
		transactionId++;
		records.clear();
		deleted.clear();
	}
	
	public synchronized boolean exists(JsonKey key) {
		checkValid();
		return records.containsKey(key);
	}
	
	public synchronized boolean isDeleted(JsonKey key) {
		checkValid();
		return deleted.containsKey(key);
	}
	
	public synchronized JsonDbRecord select(JsonKey key) {
		checkValid();
		return records.get(key);
	}
	
	public synchronized JsonSelect select() {
		checkValid();
		return new JsonSelect(this);
	}
	
	public synchronized Set<String> getCollections() {
		checkValid();
		Set<String> cols = new LinkedHashSet<>();
		records.keySet().forEach( (k) -> {
			String c = k.getCollection();
			if(!cols.contains(c)) {
				cols.add(c);
			}
		});
		return cols;
	}
	
	public synchronized JsonDbRecord insert(JsonKey key, Object content) {
		checkValid();
		if(records.containsKey(key)) {
			throw new JsonException(null, "Record with key {0} already exists", key);
		}
		transactionId++;
		deleted.remove(key);
		Instant now = now();
		RecordEntry r = new RecordEntry(key,content,now,now);
		records.put(key,r);
		return r;
	}
	
	public synchronized JsonDbRecord update(JsonKey key, Object content) {
		checkValid();
		if(!records.containsKey(key)) {
			throw new JsonException(null, "Record with key {0} does not exist", key);
		}
		transactionId++;
		deleted.remove(key);
		Instant now = now();
		RecordEntry r = new RecordEntry(key,content,now,now);
		records.put(key,r);
		return r;
	}
	
	public synchronized JsonDbRecord upsert(JsonKey key, Object content) {
		checkValid();
		transactionId++;
		deleted.remove(key);
		Instant now = now();
		RecordEntry r = new RecordEntry(key,content,now,now);
		records.put(key,r);
		return r;
	}
	
	public synchronized void delete(JsonKey key) {
		checkValid();
		if(!records.containsKey(key)) {
			throw new JsonException(null, "Record with key {0} does not exist", key);
		}
		transactionId++;
		records.remove(key);
		Instant now = now();
		DeletionEntry r = new DeletionEntry(key,now,now);
		deleted.put(key,r);
	}
	
	/**
	 * Deletes a set of records.
	 * <p>
	 * All the keys are checked first: if one of them does not exist, an exception is
	 * thrown and no record is deleted.
	 */
	public synchronized void delete(Collection<JsonKey> keys) {
		checkValid();
		for(JsonKey key: keys) {
			if(!records.containsKey(key)) {
				throw new JsonException(null, "Record with key {0} does not exist", key);
			}
		}
		keys.forEach( (key) -> {
			delete(key);
		});
	}
	
	public synchronized void delete(Predicate<JsonDbRecord> filter) {
		checkValid();
		// Collect first, then go through the regular deletion path so the deletions
		// are recorded (replication) and the transaction id is bumped
		List<JsonKey> keys = new ArrayList<>();
		for(Iterator<RecordEntry> it=records.values().iterator(); it.hasNext(); ) {
			JsonDbRecord r = it.next();
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
	
	public class Transaction extends MemoryJsonDb {
		
		private int parentTransaction;
		private boolean committed;
		
		public Transaction() {
			this.parentTransaction = MemoryJsonDb.this.transactionId;
			this.records.putAll(MemoryJsonDb.this.records);
			this.deleted.putAll(MemoryJsonDb.this.deleted);
		}
		@Override
		protected void checkValid() {
			if(committed) {
				throw new JsonException(null,"Transaction has already been committed or rollback");
			}
		}
		
		public synchronized void commit() {
			checkValid();
			synchronized(MemoryJsonDb.this) {
				if(parentTransaction!=MemoryJsonDb.this.transactionId) {
					throw new JsonException(null,"Database has been modified since the transaction started");
				}
				MemoryJsonDb.this.records = this.records;
				MemoryJsonDb.this.deleted = this.deleted;
				MemoryJsonDb.this.transactionId++;
			}
			committed = true;
		}
		
		public synchronized void rollback() {
			checkValid();
			committed = true;
		}
	}
	
	public synchronized Transaction beginTransaction() {
		return new Transaction();
	}
	
	
	//
	// Content import and replication
	// This method do not generate error
	// 
	
	/**
	 * Snapshot of the records, so a select can be iterated while the DB is modified.
	 */
	synchronized List<JsonDbRecord> snapshotRecords() {
		checkValid();
		return new ArrayList<>(records.values());
	}
	/**
	 * Snapshot of the records followed by the deletions, so a source can be streamed
	 * while the DB is modified (including by the target of the same import).
	 */
	synchronized List<JsonDbRecord> snapshotForReplication() {
		checkValid();
		List<JsonDbRecord> l = new ArrayList<>(records.size()+deleted.size());
		l.addAll(records.values());
		l.addAll(deleted.values());
		return l;
	}

	public synchronized JsonContent readJsonContent(JsonKey key) {
		checkValid();
		if(records.containsKey(key)) {
			return records.get(key);
		} else if(deleted.containsKey(key)) {
			return deleted.get(key);
		}
		return null;
	}
	public synchronized void saveJsonContent(JsonContent content) {
		checkValid();
		transactionId++;
		JsonKey key = content.getKey();
		// Just save in the DB
		switch(content.getType()) {
			case RECORD -> {
				deleted.remove(key);
				Instant now = now();
				RecordEntry r = new RecordEntry(key,content.getJson(),content.getTimestamp(),now);
				records.put(key,r);
			}
			case DELETION -> {
				records.remove(key);
				Instant now = now();
				DeletionEntry r = new DeletionEntry(key,content.getTimestamp(),now);
				deleted.put(key,r);	
			}
		}
	}
}
