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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonContentStreamSource;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.container.StreamSource;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.json.impexp.util.StaticDeletedContent;

import tests.ProjectTestCase;
import tests.util.ListSource;

/**
 * In-memory containers and stream based sources.
 */
public class ContainerTargetTest extends ProjectTestCase {

	private static StaticContent doc(String col, String id, int v) {
		return new StaticContent(JsonKey.of(col,id), JsonObject.of("v",v), null);
	}

	// RECORDSWITHKEYS is keyed: importing the same key again replaces its value
	public void testRecordsWithKeysUpsert() throws Exception {
		JsonContainerTarget target = JsonContainerTarget.newBuilder().format(JsonInMemoryFormat.RECORDSWITHKEYS).build();
		ListSource.of(doc("c","k1",1), doc("c","k2",2), doc(null,"k1",3)).exportTo(target);
		ListSource.of(doc("c","k1",10), doc(null,"k1",30), doc("c","k3",4)).exportTo(target);
		JsonArray a = (JsonArray)target.getContainer();
		assertEquals(4, a.size());
		assertEquals(JsonArray.parse("""
			[ {"collection":"c","id":"k1","value":{"v":10}},
			  {"collection":"c","id":"k2","value":{"v":2}},
			  {"collection":"","id":"k1","value":{"v":30}},
			  {"collection":"c","id":"k3","value":{"v":4}} ]
			"""), a);
		// Duplicates inside one import
		ListSource.of(doc("c","k9",1), doc("c","k9",2)).exportTo(target);
		assertEquals(5, a.size());
		assertEquals(2, a.getObject(4).getObject("value").getInt("v"));
	}

	public void testRecordsWithKeysDeleteThenInsert() throws Exception {
		JsonArray existing = JsonArray.parse("[{\"collection\":\"c\",\"id\":\"k1\",\"value\":1}]");
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSWITHKEYS)
				.container(existing)
				.build();
		// The pre-existing entry is found
		ListSource.of(doc("c","k1",5)).exportTo(target);
		assertEquals(1, existing.size());
		ListSource.of(new StaticDeletedContent(JsonKey.of("c","k1"))).exportTo(target);
		assertEquals(0, existing.size());
		ListSource.of(doc("c","k1",6)).exportTo(target);
		assertEquals(1, existing.size());
		assertEquals(6, existing.getObject(0).getObject("value").getInt("v"));
	}

	// Round trip through every format that keeps both the collection and the id
	public void testRoundTrip() throws Exception {
		for(JsonInMemoryFormat f: List.of(JsonInMemoryFormat.RECORDSWITHKEYS, JsonInMemoryFormat.RECORDSBYCOLKEY)) {
			JsonContainerTarget target = JsonContainerTarget.newBuilder().format(f).build();
			ListSource.of(doc("a","k1",1), doc("b","k2",2)).exportTo(target);
			JsonContainerSource source = JsonContainerSource.newBuilder().format(f).container(target.getContainer()).build();
			assertEquals(2, source.estimatedCount());
			JsonContainerTarget t2 = JsonContainerTarget.newBuilder().format(JsonInMemoryFormat.RECORDSWITHKEYS).build();
			source.exportTo(t2);
			assertEquals(f.toString(), 2, t2.getContainer().size());
			assertEquals(f.toString(), JsonArray.parse("""
				[ {"collection":"a","id":"k1","value":{"v":1}},
				  {"collection":"b","id":"k2","value":{"v":2}} ]
				"""), t2.getContainer());
		}
	}

	// StreamSource.close() is safe before init, after a null stream, and twice
	public void testStreamSourceClose() throws Exception {
		StreamSource never = StreamSource.newBuilder().streamFactory( () -> Stream.of(1) ).build();
		never.close();

		StreamSource nullStream = StreamSource.newBuilder().streamFactory( () -> null ).build();
		try(Stream<JsonContent> s = nullStream.stream(null)) {
			assertEquals(0, s.count());
		}
		nullStream.close();

		AtomicInteger closed = new AtomicInteger();
		StreamSource src = StreamSource.newBuilder().streamFactory( () -> Stream.<Object>of(1,2).onClose(closed::incrementAndGet) ).build();
		try(Stream<JsonContent> s = src.stream(null)) {
			assertEquals(2, s.count());
		}
		src.close();
		assertEquals(1, closed.get());
	}

	public void testJsonContentStreamSource() throws Exception {
		AtomicInteger closed = new AtomicInteger();
		JsonContentStreamSource src = JsonContentStreamSource.newBuilder()
				.streamFactory( () -> Stream.<JsonContent>of(doc("c","k1",1), new StaticDeletedContent(JsonKey.of("c","k0"))).onClose(closed::incrementAndGet) )
				.estimatedCount(2)
				.build();
		assertEquals(2, src.estimatedCount());
		JsonContainerTarget target = JsonContainerTarget.newBuilder().format(JsonInMemoryFormat.RECORDSBYCOLKEY).build();
		src.exportTo(target);
		assertEquals(JsonObject.parse("{\"c\":{\"k1\":{\"v\":1}}}"), target.getContainer());
		assertEquals(1, closed.get());

		// Streamed but never consumed: closing the stream closes the factory stream
		AtomicInteger closed2 = new AtomicInteger();
		JsonContentStreamSource src2 = JsonContentStreamSource.newBuilder()
				.streamFactory( () -> Stream.<JsonContent>empty().onClose(closed2::incrementAndGet) )
				.build();
		assertEquals(-1, src2.estimatedCount());
		src2.stream(null).close();
		assertEquals(1, closed2.get());
	}

	public void testTimestampsKept() throws Exception {
		Instant ts = Instant.parse("2020-01-01T00:00:00Z");
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(JsonArray.parse("[{\"a\":1}]"))
				.timestampFunction( (o) -> ts )
				.build();
		try(Stream<JsonContent> s = source.stream(null)) {
			assertEquals(ts, s.findFirst().get().getTimestamp());
		}
	}
}
