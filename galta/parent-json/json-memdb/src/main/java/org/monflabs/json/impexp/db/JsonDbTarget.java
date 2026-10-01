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

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.db.MemoryJsonDb.Transaction;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.replication.ReplicationTarget;

public class JsonDbTarget extends JsonTargetImpl implements ReplicationTarget {

	public static class Builder extends TargetBuilder<JsonDbTarget,Builder> {
		private MemoryJsonDb db;

		private Builder() {}
		public Builder db(MemoryJsonDb db) {
			this.db = db;
			return this;
		}
		@Override
		protected void validate() {
			super.validate();
			assertNotNull(db, "db");
		}
		@Override
		protected JsonDbTarget _build() {
			return new JsonDbTarget(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}
	
	private MemoryJsonDb db;	

	private Transaction transaction;

	protected JsonDbTarget(Builder builder) {
		super(builder);
		this.db = builder.db;
	}

	@Override
	public void init() {
	}

	@Override
	public void close() {
	}
	
	@Override
	public boolean supportsDeletions() {
		return true;
	}
	
	@Override
	public String getReplicationId() {
		return db.getReplicationId();
	}

	
	//
	// Transaction support
	@Override
	protected boolean supportsTransaction() {
		return true;
	}
	@Override
	protected void startTransaction() {
		transaction = db.beginTransaction();
	}
	@Override
	protected void commitTransaction() {
		if(transaction==null) {
			throw new JsonException(null,"Transaction has already been commited");
		}
		transaction.commit();
		transaction = null;
	}
	@Override
	protected void rollbackTransaction() {
		if(transaction==null) {
			throw new JsonException(null,"Transaction has already been commited");
		}
		transaction.rollback();
		transaction = null;
	}


	@Override
	protected JsonContent readJsonContent(JsonKey key) {
		if(transaction!=null) {
			return transaction.readJsonContent(key);
		} else {
			return db.readJsonContent(key);
		}
	}
	@Override
	public synchronized void saveJsonContent(JsonContent content) {
		if(transaction!=null) {
			transaction.saveJsonContent(content);
		} else {
			db.saveJsonContent(content);
		}
	}
}
