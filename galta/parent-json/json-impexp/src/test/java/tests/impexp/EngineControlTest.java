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
package tests.impexp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.CancelException;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.pojo.PojoTarget;
import org.monflabs.json.impexp.replication.ConflictResolver;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.json.impexp.replication.ReplicationTable;
import org.monflabs.json.impexp.replication.ReplicationTarget;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.json.impexp.util.StaticDeletedContent;
import org.monflabs.json.impexp.util.StringDumpTarget;

import tests.ProjectTestCase;
import tests.util.StaticSource;

/**
 * Counting, cancellation and processing callbacks of the import and replication engines.
 */
public class EngineControlTest extends ProjectTestCase {

	/** Map based replication target. */
	private static class MapTarget extends JsonTargetImpl implements ReplicationTarget {
		static class Builder extends TargetBuilder<MapTarget,Builder> {
			@Override
			protected MapTarget _build() {
				return new MapTarget(this);
			}
		}
		final Map<JsonKey,JsonContent> contents = new HashMap<>();
		int closeCount;
		MapTarget(Builder builder) {
			super(builder);
		}
		@Override
		public String getReplicationId() {
			return "map-target";
		}
		@Override
		public boolean supportsDeletions() {
			return true;
		}
		@Override
		public void close() {
			closeCount++;
		}
		@Override
		protected JsonContent readJsonContent(JsonKey key) {
			return contents.get(key);
		}
		@Override
		protected void saveJsonContent(JsonContent content) {
			contents.put(content.getKey(), content);
		}
	}

	private static class NeverReplicatedTable implements ReplicationTable {
		@Override
		public Instant lastReplication(String source, String target) {
			return null;
		}
		@Override
		public void saveReplication(String source, String target, Instant newLastRep, ReplicationResult result) {
		}
	}

	private static class ListSource extends JsonSourceImpl implements ReplicationSource {
		final JsonContent[] contents;
		ListSource(JsonContent...contents) {
			this.contents = contents;
		}
		@Override
		public String getReplicationId() {
			return "list-source";
		}
		@Override
		protected Iterator<JsonContent> createJsonContentIterator() {
			return new Iterator<JsonContent>() {
				int idx;
				@Override
				public boolean hasNext() {
					return idx<contents.length;
				}
				@Override
				public JsonContent next() {
					if(!hasNext()) {
						throw new NoSuchElementException();
					}
					return contents[idx++];
				}
			};
		}
	}

	private static StaticContent record(String id, int v) {
		return new StaticContent(JsonKey.of("c",id), JsonObject.of("v",v), Instant.now());
	}

	// A resolved conflict is counted once, as a conflict, not also as an insert
	public void testResolvedConflictCountedOnce() throws Exception {
		MapTarget target = new MapTarget.Builder().replicationTable(new NeverReplicatedTable()).build();
		target.contents.put(JsonKey.of("c","k1"), record("k1",0));

		ListSource source = new ListSource(record("k1",1), record("k2",2));
		ReplicationResult r = target.replicate(source, ConflictResolver.SOURCE_WINS);
		assertEquals(1, r.getConflicts());
		assertEquals(1, r.getInserted());
		assertEquals(0, r.getDeleted());
		assertEquals(0, r.getIgnored());
		assertEquals(2, r.getProcessed());
		// The resolved content was written
		assertEquals(1, ((JsonObject)target.contents.get(JsonKey.of("c","k1")).getJson()).getInt("v"));
		assertEquals("Processed=2 (inserted=1, deleted=0, conflicts=1, ignored=0)", r.toString());
	}

	public void testResolverReturningSeveralContents() throws Exception {
		MapTarget target = new MapTarget.Builder().replicationTable(new NeverReplicatedTable()).build();
		target.contents.put(JsonKey.of("c","k1"), record("k1",0));
		ConflictResolver both = (s,t) -> new JsonContent[] {
			s, new StaticContent(JsonKey.of("conflicts","k1"), t.getJson(), t.getTimestamp())
		};
		ReplicationResult r = target.replicate(new ListSource(record("k1",1)), both);
		assertEquals(1, r.getConflicts());
		assertEquals(0, r.getInserted());
		assertEquals(1, r.getProcessed());
		assertEquals(2, target.contents.size());
	}

	public void testIgnoredNotCountedAsConflict() throws Exception {
		MapTarget target = new MapTarget.Builder().replicationTable(new NeverReplicatedTable()).build();
		target.contents.put(JsonKey.of("c","k1"), record("k1",1));
		target.contents.put(JsonKey.of("c","k2"), new StaticDeletedContent(JsonKey.of("c","k2")));
		ReplicationResult r = target.replicate(new ListSource(record("k1",1), new StaticDeletedContent(JsonKey.of("c","k2"))), null);
		assertEquals(2, r.getIgnored());
		assertEquals(0, r.getConflicts());
		assertEquals(2, r.getProcessed());
	}

	// cancel() from a processing callback stops the import with a CancelException
	public void testCancelFromCallback() throws Exception {
		List<JsonTargetImpl.Event> events = new ArrayList<>();
		List<JsonContent> written = new ArrayList<>();
		PojoTarget<?>[] holder = new PojoTarget<?>[1];
		PojoTarget<Object> target = PojoTarget.newBuilder()
				.writer(written::add)
				.afterProcessing( (c) -> {
					if(written.size()==2) {
						assertTrue(holder[0].cancel());
					}
				})
				.notification(new JsonTargetImpl.TextNotification() {
					@Override
					public void notify(JsonTargetImpl.Event event, long processed, long deleted, long estimatedCount, long ellapsedMs) {
						events.add(event);
					}
					@Override
					public void log(String msg, Object... params) {
					}
				})
				.build();
		holder[0] = target;
		try {
			StaticSource.newBuilder().build().exportTo(target);
			fail("The import should have been cancelled");
		} catch(CancelException ex) {
			// Expected, not wrapped
		}
		assertEquals(2, written.size());
		assertTrue(events.contains(JsonTargetImpl.Event.CANCEL));
		assertFalse(events.contains(JsonTargetImpl.Event.END));
		// Not running anymore
		assertFalse(target.cancel());
		// The target can be reused after a cancellation
		written.clear();
		PojoTarget<Object> target2 = PojoTarget.newBuilder().writer(written::add).build();
		StaticSource.newBuilder().build().exportTo(target2);
		assertEquals(13, written.size());
	}

	public void testCancelWhenIdle() throws Exception {
		PojoTarget<Object> target = PojoTarget.newBuilder().build();
		assertFalse(target.cancel());
	}

	public void testCancelClosesTarget() throws Exception {
		MapTarget[] holder = new MapTarget[1];
		MapTarget target = new MapTarget.Builder()
				.afterProcessing( (c) -> holder[0].cancel() )
				.build();
		holder[0] = target;
		try {
			new ListSource(record("k1",1), record("k2",2)).exportTo(target);
			fail();
		} catch(CancelException ex) {
		}
		assertEquals(1, target.closeCount);
		assertEquals(1, target.contents.size());
	}

	// beforeProcessing can skip a content: it is still counted
	public void testBeforeProcessingSkipIsCounted() throws Exception {
		List<JsonContent> written = new ArrayList<>();
		PojoTarget<Object> target = PojoTarget.newBuilder()
				.writer(written::add)
				.beforeProcessing( (c) -> c.getKey().getId().startsWith("k1") ? c : null )
				.build();
		ImportResult r = StaticSource.newBuilder().build().exportTo(target);
		assertEquals(4, written.size());
		assertEquals(13, r.getInserted());
		assertEquals(13, r.getProcessed());
	}

	// PojoTarget receives the deletions as well
	public void testPojoTargetDeletions() throws Exception {
		List<JsonContent> written = new ArrayList<>();
		PojoTarget<Object> target = PojoTarget.newBuilder().writer(written::add).build();
		ImportResult r = StaticSource.newBuilder().content(StaticSource.CONTENT.MIXED).build().exportTo(target);
		assertEquals(6, written.size());
		assertEquals(4, r.getInserted());
		assertEquals(2, r.getDeleted());
		assertEquals(JsonContent.TYPE.DELETION, written.get(2).getType());
	}

	public void testPojoTargetInitClose() throws Exception {
		int[] calls = new int[2];
		PojoTarget<Object> target = PojoTarget.newBuilder()
				.init( () -> calls[0]++ )
				.close( () -> calls[1]++ )
				.build();
		StaticSource.newBuilder().build().exportTo(target);
		assertEquals(1, calls[0]);
		assertEquals(1, calls[1]);
	}

	public void testStringDumpTarget() throws Exception {
		StringDumpTarget target = StringDumpTarget.newBuilder().build();
		assertNull(target.getString());
		assertTrue(target.supportsDeletions());
		StaticSource.newBuilder().content(StaticSource.CONTENT.MIXED).build().exportTo(target);
		String s = target.getString();
		String[] lines = s.split("\n");
		assertEquals(6, lines.length);
		assertEquals(JsonKey.of("col","k06")+": {\"a\":\"v6\"}", lines[0]);
		assertEquals(JsonKey.of("col","k04")+": <DELETED>", lines[2]);
		// A new import starts from scratch
		StaticSource.newBuilder().content(StaticSource.CONTENT.DELETED).build().exportTo(target);
		assertEquals(2, target.getString().split("\n").length);
	}
}
