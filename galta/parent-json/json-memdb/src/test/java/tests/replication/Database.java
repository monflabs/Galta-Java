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
package tests.replication;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.db.JsonDbSource;
import org.monflabs.json.impexp.db.JsonDbTarget;
import org.monflabs.json.impexp.db.MemoryJsonDb;
import org.monflabs.json.impexp.db.MemoryReplicationTable;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.json.impexp.replication.ReplicationTarget;

import tests.ProjectTestCase;

/**
 * Encapsulation of a database to be used by the replication test class.
 * 
 * The replication test class can easily be copied to other projects to test
 * different stores.
 * 
 * @author priand
 *
 */
public class Database {

	private static Database db1;
	private static Database db2;

	public static void init(ProjectTestCase test) {
		{
			db1 = new Database(new MemoryJsonDb());
		}
		{
			db2 = new Database(new MemoryJsonDb());
		}
	}
	public static Database getSourceDatabase() {
		return db1;
	}
	public static Database getTargetDatabase() {
		return db2;
	}

	private MemoryJsonDb db;
	
	public Database(MemoryJsonDb db) {
		this.db = db;
	}
	public void close() {
	}
	public void clear() {
		db.clear();
	}
	public void insert(JsonKey key, Object json) {
		if(json instanceof String) {
			json = JsonFactory.get().parse((String)json);
		}
		db.insert(key, json);
	}
	public void update(JsonKey key, Object json) {
		if(json instanceof String) {
			json = JsonFactory.get().parse((String)json);
		}
		db.update(key, json);
	}
	public void delete(JsonKey key) {
		db.delete(key);
	}
	public Object serialize() {
		return db.serialize(true);
	}
	public ReplicationSource createReplicationSource() {
		JsonDbSource source = JsonDbSource.newBuilder()
				.db(db)
				.build();
		return source;
	}
	public ReplicationTarget createReplicationTarget() {
		JsonDbTarget target = JsonDbTarget.newBuilder()
				.db(db)
				.replicationTable(new MemoryReplicationTable())
				.notification(JsonTargetImpl.consoleLogger)
	   			.build();
		return target;
	}
}
