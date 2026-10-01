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
import java.util.stream.Stream;

import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.util.ObjectBuilder;

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
	 * selected collection, restricted to the range filter of the last stream (if any).
	 */
	@Override
	public long estimatedCount() {
		return estimatedCount(getRangeFilter());
	}
	
	/**
	 * Number of contents a stream with a range filter produces.
	 */
	@Override
	public long estimatedCount(RangeFilter filter) {
		return db.snapshotForReplication(collection).stream().filter((r) -> accept(r,filter)).count();
	}
	
	@Override
	public String getReplicationId() {
		return db.getReplicationId();
	}
	
	/**
	 * The range filter applies to the date the contents were stored in the DB
	 * ({@link JsonDbRecord#getDbAdded()}), not to their timestamp.
	 */
	@Override
	protected boolean handlesRangeFilter() {
		return true;
	}
	
	@Override
	protected Stream<JsonContent> createJsonContentStream(RangeFilter filter) {
		// Iterate a snapshot: the DB can be modified while the stream is consumed. The
		// filter is the one of this stream, so several streams can run concurrently.
		return db.snapshotForReplication(collection).stream()
				.filter((r) -> accept(r,filter))
				.map((r) -> (JsonContent)r);
	}
	
	private static boolean accept(JsonDbRecord r, RangeFilter filter) {
		if(filter!=null) {
			Instant t = r.getDbAdded(); 
			// Both ends are included: a content stored exactly at the date of the last
			// replication can be replicated twice, but cannot be missed when the clock
			// resolution is not high enough
			if(filter.getSince()!=null && t.compareTo(filter.getSince())<0) {
				return false;
			}
			if(filter.getUntil()!=null && t.compareTo(filter.getUntil())>0) {
				return false;
			}
		}
		return true;
	}
}
