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
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.replication.ConflictResolver;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.json.impexp.replication.ReplicationTable;
import org.monflabs.json.impexp.replication.ReplicationTarget;
import org.monflabs.json.impexp.util.StaticContent;

import tests.ProjectTestCase;
import tests.util.StaticSource;

/**
 * Failure paths of the import and replication engines.
 */
public class EngineFailureTest extends ProjectTestCase {

	/** Non transactional target that records init()/close() and can fail on save. */
	private static class FlagTarget extends JsonTargetImpl implements ReplicationTarget {
		static class Builder extends TargetBuilder<FlagTarget,Builder> {
			@Override
			protected FlagTarget _build() {
				return new FlagTarget(this);
			}
		}
		int initCount;
		int closeCount;
		int saved;
		RuntimeException failure;
		JsonContent existing;

		FlagTarget(Builder builder) {
			super(builder);
		}
		@Override
		public String getReplicationId() {
			return "flag-target";
		}
		@Override
		public void init() {
			initCount++;
		}
		@Override
		public void close() {
			closeCount++;
		}
		@Override
		protected JsonContent readJsonContent(JsonKey key) {
			return existing;
		}
		@Override
		protected void saveJsonContent(JsonContent content) {
			if(failure!=null) {
				throw failure;
			}
			saved++;
		}
	}

	/** Source that throws once the first document has been read. */
	private static class FailingSource extends JsonSourceImpl {
		final RuntimeException failure;
		boolean closed;
		FailingSource(RuntimeException failure) {
			this.failure = failure;
		}
		@Override
		public void close() {
			closed = true;
		}
		@Override
		protected Iterator<JsonContent> createJsonContentIterator() {
			return new Iterator<JsonContent>() {
				int count;
				@Override
				public boolean hasNext() {
					return true;
				}
				@Override
				public JsonContent next() {
					if(count++>0) {
						throw failure;
					}
					return new StaticContent(JsonKey.of("c","k"+count), JsonObject.of("v",count), null);
				}
			};
		}
	}

	private static class FixedReplicationTable implements ReplicationTable {
		Instant lastReplication;
		int saved;
		FixedReplicationTable(Instant lastReplication) {
			this.lastReplication = lastReplication;
		}
		@Override
		public Instant lastReplication(String source, String target) {
			return lastReplication;
		}
		@Override
		public void saveReplication(String source, String target, Instant newLastRep, ReplicationResult result) {
			saved++;
		}
	}

	private static ReplicationSource replicationSource(JsonContent...contents) {
		return new StaticReplicationSource(contents);
	}
	private static class StaticReplicationSource extends JsonSourceImpl implements ReplicationSource {
		final JsonContent[] contents;
		StaticReplicationSource(JsonContent[] contents) {
			this.contents = contents;
		}
		@Override
		public String getReplicationId() {
			return "static-source";
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

	// C1: the original exception must not be replaced by the rollback failure
	public void testReplicationFailureOnNonTransactionalTarget() throws Exception {
		FlagTarget target = new FlagTarget.Builder()
					.replicationTable(new FixedReplicationTable(null))
					.build();
		IllegalArgumentException boom = new IllegalArgumentException("boom");
		target.failure = boom;

		ReplicationSource source = replicationSource(new StaticContent(JsonKey.of("c","k1"), JsonObject.of("v",1), Instant.now()));
		try {
			target.replicate(source, null);
			fail("Replication should have failed");
		} catch(JsonException ex) {
			assertSame(boom, ex.getCause());
		}
		assertEquals(1, target.initCount);
		assertEquals(1, target.closeCount);
	}

	// C2: a failing import must close the target
	public void testFailedImportClosesTarget() throws Exception {
		FlagTarget target = new FlagTarget.Builder().build();
		IllegalStateException boom = new IllegalStateException("source failure");
		FailingSource source = new FailingSource(boom);
		try {
			source.exportTo(target);
			fail("Import should have failed");
		} catch(JsonException ex) {
			assertSame(boom, ex.getCause());
		}
		assertEquals(1, target.initCount);
		assertEquals(1, target.closeCount);
		assertTrue(source.closed);
		assertEquals(1, target.saved);
	}

	public void testSuccessfulImportClosesTargetOnce() throws Exception {
		FlagTarget target = new FlagTarget.Builder().build();
		StaticSource.newBuilder().build().exportTo(target);
		assertEquals(1, target.initCount);
		assertEquals(1, target.closeCount);
	}

	// C2: a failing replication must close the source stream and the target
	public void testFailedReplicationClosesSourceAndTarget() throws Exception {
		FlagTarget target = new FlagTarget.Builder()
					.replicationTable(new FixedReplicationTable(null))
					.build();
		IllegalStateException boom = new IllegalStateException("source failure");
		FailingSource source = new FailingSource(boom);
		ReplicationSource repSource = new ReplicationSource() {
			@Override
			public String getReplicationId() {
				return "failing-source";
			}
			@Override
			public java.util.stream.Stream<JsonContent> stream(org.monflabs.json.impexp.replication.RangeFilter filter) {
				return source.stream(filter);
			}
		};
		try {
			target.replicate(repSource, null);
			fail("Replication should have failed");
		} catch(JsonException ex) {
			assertSame(boom, ex.getCause());
		}
		assertTrue(source.closed);
		assertEquals(1, target.closeCount);
	}

	// C3: a target record without a timestamp must not NPE
	public void testReplicationTargetWithoutTimestamp() throws Exception {
		FixedReplicationTable table = new FixedReplicationTable(Instant.now().minusSeconds(60));
		FlagTarget target = new FlagTarget.Builder()
					.replicationTable(table)
					.build();
		target.existing = new StaticContent(JsonKey.of("c","k1"), JsonObject.of("v",0), null);

		ReplicationSource source = replicationSource(new StaticContent(JsonKey.of("c","k1"), JsonObject.of("v",1), Instant.now()));
		ReplicationResult r = target.replicate(source, ConflictResolver.FAIL_EXCEPTION);
		assertEquals(0, r.getConflicts());
		assertEquals(1, target.saved);
		assertEquals(1, table.saved);
	}

	public void testConflictResolversWithoutTimestamp() throws Exception {
		StaticContent withTs = new StaticContent(JsonKey.of("c","k1"), JsonObject.of("v",1), Instant.now());
		StaticContent noTs = new StaticContent(JsonKey.of("c","k1"), JsonObject.of("v",2), null);

		assertSame(withTs, ConflictResolver.NEWER_WINS.resolve(withTs, noTs)[0]);
		assertSame(withTs, ConflictResolver.NEWER_WINS.resolve(noTs, withTs)[0]);
		assertSame(noTs, ConflictResolver.OLDER_WINS.resolve(withTs, noTs)[0]);
		assertSame(noTs, ConflictResolver.OLDER_WINS.resolve(noTs, withTs)[0]);
		// Both missing: the source wins in both cases
		assertSame(noTs, ConflictResolver.NEWER_WINS.resolve(noTs, noTs)[0]);
		assertSame(noTs, ConflictResolver.OLDER_WINS.resolve(noTs, noTs)[0]);
	}
}
