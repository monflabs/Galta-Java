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
package tests.util;

import java.util.Iterator;
import java.util.List;

import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.ReplicationSource;

/**
 * Source streaming a fixed list of contents.
 */
public class ListSource extends JsonSourceImpl implements ReplicationSource {

	private final List<JsonContent> contents;
	private final String replicationId;
	public int closeCount;

	public static ListSource of(JsonContent...contents) {
		return new ListSource("list-source", contents);
	}

	public ListSource(String replicationId, JsonContent...contents) {
		this.replicationId = replicationId;
		this.contents = List.of(contents);
	}

	@Override
	public String getReplicationId() {
		return replicationId;
	}

	@Override
	public long estimatedCount() {
		return contents.size();
	}

	@Override
	public void close() {
		closeCount++;
	}

	@Override
	protected Iterator<JsonContent> createJsonContentIterator() {
		return contents.iterator();
	}
}
