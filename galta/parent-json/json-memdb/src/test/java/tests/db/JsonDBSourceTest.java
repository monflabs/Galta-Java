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

import java.time.Instant;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.db.JsonDbSource;
import org.monflabs.json.impexp.db.MemoryJsonDb;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.replication.RangeFilter;

import tests.ProjectTestCase;

public class JsonDBSourceTest extends ProjectTestCase {

	public void testSource() throws Exception {
		checkSource( 
				  "{ col1: { k1: 'a', k2: 'b', k3: 'c'}, col2: {k1:'d', k2:'e'} }", 
				  "src-records",
				  5);
	}
	private void checkSource(String json, String template, long estimatedCount) throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		JsonObject o = JsonObject.parse(json);
		o.forEach( (name,col) -> {
			JsonObject co = (JsonObject)col;
			co.forEach( (key,j) -> {
				db.insert(JsonKey.of(name,key), j);
			});
		});
		
		JsonDbSource source = JsonDbSource.newBuilder()
								.db(db)
								.build();
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		long cnt = source.estimatedCount();
		assertEquals(estimatedCount, cnt);
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContainer(), template);
	}

	public void testUpdateTs() throws Exception {
		MemoryJsonDb db = new MemoryJsonDb();
		
		Instant i1 = Instant.now();
		sleep();		
		db.insert(JsonKey.of("c1","k1"), JsonObject.of("v",10));
		db.insert(JsonKey.of("c1","k2"), JsonObject.of("v",20));
		sleep();		
		Instant i2 = Instant.now();
		sleep();		
		db.insert(JsonKey.of("c1","k3"), JsonObject.of("v",30));
		sleep();		
		Instant i3 = Instant.now();
		sleep();		

		RangeFilter r0 = new RangeFilter(null, null);
		
		RangeFilter r1 = new RangeFilter(i1, null);
		RangeFilter r2 = new RangeFilter(i2, null);
		RangeFilter r3 = new RangeFilter(i3, null);
		
		RangeFilter r12 = new RangeFilter(i1, i2);
		RangeFilter r13 = new RangeFilter(i1, i3);
		RangeFilter r23 = new RangeFilter(i2, i3);
		
		JsonDbSource s = JsonDbSource.newBuilder()
				.db(db)
				.build();
		JsonContainerTarget t = JsonContainerTarget.newBuilder()
				.notification(JsonTargetImpl.consoleLogger)
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.build();
		
		s.exportTo(t,r0);
		support.assertJsonTemplate(t.getContainer(), "instant0");

		s.exportTo(t,r1);
		support.assertJsonTemplate(t.getContainer(), "instant1");
		
		t.getContainer().clear();
		s.exportTo(t,r12);
		support.assertJsonTemplate(t.getContainer(), "instant12");
		
		t.getContainer().clear();
		s.exportTo(t,r13);
		support.assertJsonTemplate(t.getContainer(), "instant13");
		
		t.getContainer().clear();
		s.exportTo(t,r2);
		support.assertJsonTemplate(t.getContainer(), "instant2");
		
		t.getContainer().clear();
		s.exportTo(t,r23);
		support.assertJsonTemplate(t.getContainer(), "instant23");
		
		t.getContainer().clear();
		s.exportTo(t,r3);
		support.assertJsonTemplate(t.getContainer(), "instant3");
	}
}
