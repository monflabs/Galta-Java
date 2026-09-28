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

import java.time.Instant;

import org.monflabs.json.impexp.db.MemoryReplicationTable;

import tests.ProjectTestCase;

public class MemoryReplicationTableTest extends ProjectTestCase {

	public void testMemoryReplicationTable() {
		MemoryReplicationTable t = new MemoryReplicationTable();
		
		String s1 = "SOURCE 1";
		String s2 = "SOURCE 2";
		String t1  = "TARGET 1";
		String t2  = "TARGET 2";
		
		Instant start = t.now();
		assertEquals( 0, t.getEntries().size() );
		assertEquals( null, t.lastReplication(s1, t1) );
		assertEquals( null, t.lastReplication(s1, t2) );
		
		t.saveReplication(s1, t1, start, null);
		assertEquals( 1, t.getEntries().size() );
		assertEquals( start, t.lastReplication(s1, t1) );

		sleep();
		Instant next = t.now();
		t.saveReplication(s1, t1, next, null);
		assertEquals( 2, t.getEntries().size() );
		assertEquals( next, t.lastReplication(s1, t1) );
		
		sleep();
		Instant next2 = t.now();
		t.saveReplication(s1, t2, next2, null);
		assertEquals( 3, t.getEntries().size() );
		assertEquals( next, t.lastReplication(s1, t1) );
		assertEquals( next2, t.lastReplication(s1, t2) );
		
		sleep();
		Instant next3 = t.now();
		t.saveReplication(s2, t1, next3, null);
		assertEquals( 4, t.getEntries().size() );
		assertEquals( next, t.lastReplication(s1, t1) );
		assertEquals( next2, t.lastReplication(s1, t2) );
		assertEquals( next3, t.lastReplication(s2, t1) );
		
		t.clear(s1, t1);
		assertEquals( 2, t.getEntries().size() );
		
		t.clear();
		assertEquals( 0, t.getEntries().size() );
	}
}
