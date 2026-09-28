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

import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Before;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.replication.ConflictResolver;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.json.impexp.replication.ReplicationTarget;
import org.monflabs.json.impexp.util.StaticContent;

import tests.ProjectTestCase;

public class ReplicationTest extends ProjectTestCase {
		
	Database db1;
	Database db2;

	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		Database.init(this);
		db1 = Database.getSourceDatabase();
		db2 = Database.getTargetDatabase();
	}
	@Override
	@After
    public void tearDown() throws Exception {
		db1.close();
		db2.close();
		
		super.tearDown();
    }
	
	private void initDB() throws Exception {
		db1.clear();
		db1.insert(JsonKey.of("c1","k1"), JsonObject.of("v",1));
		db1.insert(JsonKey.of("c1","k2"), JsonObject.of("v",2));
		db1.insert(JsonKey.of("c2","k3"), JsonObject.of("v",3));

		db2.clear();
		db2.insert(JsonKey.of("d1","k1"), JsonObject.of("v",101));
		db2.insert(JsonKey.of("d1","k2"), JsonObject.of("v",102));
		db2.insert(JsonKey.of("d2","k3"), JsonObject.of("v",103));
	}
	
	public void testRecordReplication() throws Exception {
		initDB();
		
		ReplicationSource db1Source = db1.createReplicationSource();
		ReplicationTarget db2Target = db2.createReplicationTarget();
		sleep();

		{
			ReplicationResult r1 = db2Target.replicate(db1Source, null);
			support.print("#1: {0}",r1);
			assertEquals(3, r1.getInserted());
			assertEquals(3, r1.getProcessed());
			assertEquals(0, r1.getConflicts());
			support.assertJsonTemplate(db2.serialize(), "rep1-1");

			ReplicationResult r2 = db2Target.replicate(db1Source, null);
			support.print("#2: {0}",r2);
			assertEquals(0, r2.getProcessed());
			support.assertJsonTemplate(db2.serialize(), "rep1-2");

			db1.insert(JsonKey.of("c1","k4"), JsonObject.of("v",4));
			sleep();
			ReplicationResult r3 = db2Target.replicate(db1Source, null);
			support.print("#3: {0}",r3);
			assertEquals(1, r3.getInserted());
			assertEquals(1, r3.getProcessed());
			support.assertJsonTemplate(db2.serialize(), "rep1-3");

			db1.delete(JsonKey.of("c1","k2"));
			sleep();
			ReplicationResult r4 = db2Target.replicate(db1Source, null);
			support.print("#4: {0}",r4);
			assertEquals(0, r4.getInserted());
			assertEquals(1, r4.getDeleted());
			assertEquals(1, r4.getProcessed());
			support.assertJsonTemplate(db2.serialize(), "rep1-4");

			db1.insert(JsonKey.of("c1","k6"), JsonObject.of("v",6)); // Will not be replication (transaction)
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5));
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55)); // Should not be erased
			sleep();
			assertThrows(JsonException.class, () -> db2Target.replicate(db1Source, null));
			support.print("#6: Replication conflict");
			support.assertJsonTemplate(db2.serialize(), "rep1-5");
		}
	}
	
	public void testConflictResolvers() throws Exception {
		ReplicationSource db1Source = db1.createReplicationSource();
		ReplicationTarget db2Target = db2.createReplicationTarget();
		
		{
			initDB();
			
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55));
			assertThrows(JsonException.class, () -> db2Target.replicate(db1Source, ConflictResolver.FAIL_EXCEPTION));
			support.assertJsonTemplate(db2.serialize(), "resolver-fail");
		}
		// Same value documents
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5));
			db2.delete(JsonKey.of("c1","k5"));
			assertThrows(JsonException.class, () -> db2Target.replicate(db1Source, ConflictResolver.FAIL_EXCEPTION));
			support.assertJsonTemplate(db2.serialize(), "resolver-fail-insdel");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			db1.delete(JsonKey.of("c1","k5"));
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5));
			assertThrows(JsonException.class, () -> db2Target.replicate(db1Source, ConflictResolver.FAIL_EXCEPTION));
			support.assertJsonTemplate(db2.serialize(), "resolver-fail-delins");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); // Doesn't fail as the value is the same (-> silent)
			db2Target.replicate(db1Source, ConflictResolver.FAIL_EXCEPTION);
			support.assertJsonTemplate(db2.serialize(), "resolver-samedoc-noerror");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",6)); // Fails as the value is different
			assertThrows(JsonException.class, () -> db2Target.replicate(db1Source, ConflictResolver.FAIL_EXCEPTION));
			support.assertJsonTemplate(db2.serialize(), "resolver-db2-unchanged");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			db1.delete(JsonKey.of("c1","k5"));
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",6));
			db2.delete(JsonKey.of("c1","k5"));
			db2Target.replicate(db1Source, ConflictResolver.FAIL_EXCEPTION);
			support.assertJsonTemplate(db2.serialize(), "resolver-samedelete-noerror");
		}

		
		
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55));
			ReplicationResult r = db2Target.replicate(db1Source, ConflictResolver.NO_ACTION);
			support.print("NO ACTION: {0}",r);
			support.assertJsonTemplate(db2.serialize(), "resolver-noaction");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55));
			ReplicationResult r = db2Target.replicate(db1Source, ConflictResolver.NEWER_WINS);
			support.print("NO ACTION: {0}",r);
			support.assertJsonTemplate(db2.serialize(), "resolver-newerwins");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55));
			ReplicationResult r = db2Target.replicate(db1Source, ConflictResolver.OLDER_WINS);
			support.print("NO ACTION: {0}",r);
			support.assertJsonTemplate(db2.serialize(), "resolver-olderwins");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55));
			ReplicationResult r = db2Target.replicate(db1Source, ConflictResolver.SOURCE_WINS);
			support.print("NO ACTION: {0}",r);
			support.assertJsonTemplate(db2.serialize(), "resolver-sourcewins");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55));
			ReplicationResult r = db2Target.replicate(db1Source, ConflictResolver.TARGET_WINS);
			support.print("NO ACTION: {0}",r);
			support.assertJsonTemplate(db2.serialize(), "resolver-targetwins");
		}
		{
			initDB();
			db1.insert(JsonKey.of("c1","k5"), JsonObject.of("v",5)); 
			sleep();
			db2.insert(JsonKey.of("c1","k5"), JsonObject.of("v",55));
			ReplicationResult r = db2Target.replicate(db1Source, (s,t) -> {
				JsonObject so = ((JsonObject)s.getJson()).deepClone();
				so.put("conflict", "Conflict SOURCE");
				JsonObject to = ((JsonObject)t.getJson()).deepClone();
				to.put("conflict", "Conflict TARGET");
				StaticContent sc = new StaticContent(JsonKey.of(s.getKey().getCollection(),s.getKey().getId()+"-sc"), so, s.getTimestamp());
				StaticContent tc = new StaticContent(JsonKey.of(s.getKey().getCollection(),s.getKey().getId()+"-tc"), to, s.getTimestamp());
				return new JsonContent[] {sc,tc};
			});
			support.print("NO ACTION: {0}",r);
			support.assertJsonTemplate(db2.serialize(), "resolver-custom");
		}
	}
}
