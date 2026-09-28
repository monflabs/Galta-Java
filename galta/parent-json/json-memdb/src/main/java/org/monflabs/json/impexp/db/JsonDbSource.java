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
import java.util.Iterator;

import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.iterators.Iterators;

public class JsonDbSource extends JsonSourceImpl implements ReplicationSource {

	public static class Builder extends ObjectBuilder<JsonDbSource> {
		private MemoryJsonDb db;
		private String collection;

		private Builder() {}
		public Builder db(MemoryJsonDb db) {
			this.db = db;
			return this;
		}
		public Builder collection(String collection) {
			this.collection = collection;
			return this;
		}
		@Override
		public void validate() {
			super.validate();
			assertNotNull(db, "db");
		}
		@Override
		protected JsonDbSource _build() {
			return new JsonDbSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	private MemoryJsonDb db;
	private String collection;

	protected JsonDbSource(Builder builder) {
		this.db = builder.db;
		this.collection = builder.collection;
	}

	/**
	 * Number of contents this source produces: the records and deletions of the
	 * selected collection, restricted to the range filter of the running stream (if
	 * any).
	 */
	@Override
	public long estimatedCount() {
		return db.snapshotForReplication().stream().filter(this::accept).count();
	}
	
	@Override
	public String getReplicationId() {
		return db.getReplicationId();
	}
	
	@Override
	protected Iterator<JsonContent> createJsonContentIterator() {
		// Iterate a snapshot: the DB can be modified while the stream is consumed
		Iterator<JsonDbRecord> it = Iterators.filter(db.snapshotForReplication().iterator(), this::accept);
		return Iterators.map(it, (r) -> {
			return (JsonContent)r; 
		});
	}
	
	private boolean accept(JsonDbRecord r) {
		if(collection!=null) {
			if(!collection.equals(r.getKey().getCollection())) {
				return false;
			}
		}
		RangeFilter filter = getRangeFilter();
		if(filter!=null) {
			Instant t = r.getDbAdded(); 
			if(filter.getSince()!=null) {
				// We use <= even though some exact time doc can be replicated twice
				// This is ensure than last save entries are also picked up when the clock resolution is not high enough
				if(t.compareTo(filter.getSince())<0) {
					return false;
				}
			}
			if(filter.getUntil()!=null) {
				if(t.compareTo(filter.getUntil())>0) {
					return false;
				}
			}
		}
		return true;
	}
}
