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
import java.util.Iterator;
import java.util.List;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.ReplicationTable;
import org.monflabs.util.StringUtil;

/**
 * In Memory Replication table.
 * 
 * This is for test, so performance is NOT a concern.
 */
public class MemoryReplicationTable implements ReplicationTable {
	
	public static class Entry {
		String source;
		String target;
		Instant repDate;
		JsonObject result;
		Entry(String source, String target, Instant repDate, ReplicationResult result) {
			this.source = source;
			this.target = target;
			this.repDate = repDate;
			this.result = result!=null ? result.toJson() : JsonObject.create();
		}
	}
	
	private List<Entry> entries = new ArrayList<>();
	
	public MemoryReplicationTable() {
	}

	public List<Entry> getEntries() {
		return entries;
	}
	
	public synchronized void clear() {
		entries.clear();
	}
	
	public synchronized void clear(String source, String target) {
		for(Iterator<Entry> it=entries.iterator(); it.hasNext(); ) {
			Entry e = it.next();
			if(StringUtil.equals(source, e.source) && StringUtil.equals(target, e.target)) {
				it.remove();
			}
		}
	}
	
	@Override
	public synchronized Instant lastReplication(String source, String target) {
		// We assume that the entries are added in order
		for(int i=entries.size()-1; i>=0; i--) {
			Entry e = entries.get(i);
			if(StringUtil.equals(source, e.source) && StringUtil.equals(target, e.target)) {
				return e.repDate;
			}
		}
		return null;
	}
	@Override
	public synchronized void saveReplication(String source, String target, Instant newLastRep, ReplicationResult result) {
		Entry e = new Entry(source, target, newLastRep, result);
		entries.add(e);
	}
}
