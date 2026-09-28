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

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.csv.CsvSource;
import org.monflabs.json.impexp.csv.CsvTarget;

import tests.ProjectTestCase;

/**
 * CSV edge cases: headers, nulls, separators, counts, writer ownership.
 */
public class CsvRoundTripTest extends ProjectTestCase {

	private static List<JsonContent> read(CsvSource source) {
		try(Stream<JsonContent> s = source.stream(null)) {
			return s.collect(Collectors.toList());
		}
	}
	private static CsvSource.Builder csv(String content) {
		return CsvSource.newBuilder().reader( () -> new StringReader(content) );
	}
	private static String export(String jsonArray, CsvTarget.Builder builder) {
		StringWriter out = new StringWriter();
		JsonContainerSource.newBuilder().container(JsonArray.parse(jsonArray)).build()
			.exportTo(builder.writer( () -> out ).build());
		return out.toString().replace("\r\n", "\n");
	}

	// A repeated column name in the header is reported, not silently merged
	public void testDuplicateHeader() throws Exception {
		CsvSource source = csv("a,b,a\n1,2,3\n").build();
		try {
			read(source);
			fail();
		} catch(JsonException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("\"a\""));
		}
	}

	// The count is the number of data rows: header, empty rows and quoted line breaks excluded
	public void testEstimatedCount() throws Exception {
		assertEquals(2, csv("a,b\n1,\"x\ny\"\n\n2,3\n").estimateCount(true).build().estimatedCount());
		assertEquals(3, csv("1,2\n3,4\n5,6").firstRowAsHeader(false).column("a").column("b").estimateCount(true).build().estimatedCount());
		assertEquals(0, csv("a,b\n").estimateCount(true).build().estimatedCount());
		assertEquals(0, csv("").estimateCount(true).build().estimatedCount());
		assertEquals(-1, csv("a,b\n1,2").build().estimatedCount());
		// Matches what is streamed
		CsvSource s = csv("a,b\n1,\"x\ny\"\n\n2,3\n").estimateCount(true).build();
		assertEquals(s.estimatedCount(), read(s).size());
	}

	// CSV has no null: null is an empty cell, read back as "" or as null with emptyAsNull
	public void testNullRoundTrip() throws Exception {
		String out = export("[{\"a\":null,\"b\":\"\",\"c\":1}]", CsvTarget.newBuilder());
		assertEquals("a,b,c\n,,1\n", out);

		JsonObject asString = (JsonObject)read(csv(out).build()).get(0).getJson();
		assertEquals("", asString.get("a"));
		assertEquals("", asString.get("b"));

		JsonObject asNull = (JsonObject)read(csv(out).emptyAsNull(true).column("c",JsonUtil::parseInt).build()).get(0).getJson();
		assertTrue(asNull.containsKey("a"));
		assertNull(asNull.get("a"));
		assertNull(asNull.get("b"));
		assertEquals(1, asNull.get("c"));
		// The cell reader is not called for an empty cell
		JsonObject noReader = (JsonObject)read(csv("c,d\n\"\",x\n").emptyAsNull(true).column("c",JsonUtil::parseInt).build()).get(0).getJson();
		assertNull(noReader.get("c"));
	}

	// Inferred columns: a property unknown to the first record is an error, not dropped
	public void testInferredColumnsExtraProperty() throws Exception {
		try {
			export("[{\"a\":1},{\"a\":2,\"b\":3}]", CsvTarget.newBuilder());
			fail();
		} catch(JsonException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("\"b\""));
		}
		// Missing properties are fine
		assertEquals("a,b\n1,2\n3,\n", export("[{\"a\":1,\"b\":2},{\"a\":3}]", CsvTarget.newBuilder()));
		// Declared columns are a projection
		assertEquals("a\n1\n2\n", export("[{\"a\":1},{\"a\":2,\"b\":3}]", CsvTarget.newBuilder().column("a")));
	}

	// The writer is closed by default, only flushed with closeWriter(false)
	public void testWriterOwnership() throws Exception {
		TrackingWriter w1 = new TrackingWriter();
		JsonContainerSource src = JsonContainerSource.newBuilder().container(JsonArray.parse("[{\"a\":1}]")).build();
		CsvTarget closing = CsvTarget.newBuilder().writer( () -> w1 ).build();
		assertTrue(closing.isCloseWriter());
		src.exportTo(closing);
		assertTrue(w1.closed);

		TrackingWriter w2 = new TrackingWriter();
		CsvTarget keeping = CsvTarget.newBuilder().writer( () -> w2 ).closeWriter(false).build();
		src.exportTo(keeping);
		assertFalse(w2.closed);
		assertTrue(w2.flushed);
		assertEquals("a\n1\n", w2.toString().replace("\r\n", "\n"));
	}
	private static class TrackingWriter extends StringWriter {
		boolean closed;
		boolean flushed;
		@Override
		public void flush() {
			flushed = true;
			super.flush();
		}
		@Override
		public void close() {
			closed = true;
		}
	}

	// Tab separated values, both ways
	public void testFieldSeparator() throws Exception {
		String out = export("[{\"a\":\"x,y\",\"b\":\"t\tu\"}]", CsvTarget.newBuilder().fieldSeparator('\t'));
		assertEquals("a\tb\nx,y\t\"t\tu\"\n", out);
		JsonObject o = (JsonObject)read(csv(out).fieldSeparator('\t').build()).get(0).getJson();
		assertEquals("x,y", o.get("a"));
		assertEquals("t\tu", o.get("b"));
	}

	// Headerless input with more or fewer cells than the declared columns
	public void testHeaderlessColumnCountMismatch() throws Exception {
		List<JsonContent> l = read(csv("1,2,3\n4\n").firstRowAsHeader(false).column("a").column("b").build());
		assertEquals(JsonObject.parse("{\"a\":\"1\",\"b\":\"2\"}"), l.get(0).getJson());
		assertEquals(JsonObject.parse("{\"a\":\"4\",\"b\":null}"), l.get(1).getJson());
	}

	public void testKeyCollectionTimestampFunctions() throws Exception {
		Instant ts = Instant.parse("2020-01-01T00:00:00Z");
		List<JsonContent> l = read(csv("id,col\nk1,c1\nk2,c2\n")
				.keyFunction( (r) -> (String)r.get("id") )
				.collectionFunction( (r) -> (String)r.get("col") )
				.timestampFunction( (r) -> ts )
				.build());
		assertEquals("c1", l.get(0).getKey().getCollection());
		assertEquals("k2", l.get(1).getKey().getId());
		assertEquals(ts, l.get(1).getTimestamp());
		// Default key: the row index, from 0
		assertEquals(List.of("0","1"), read(csv("a\nx\ny\n").build()).stream().map( (c) -> c.getKey().getId() ).collect(Collectors.toList()));
	}

	public void testNoHeaderRow() throws Exception {
		assertEquals("1,2\n", export("[{\"a\":1,\"b\":2}]", CsvTarget.newBuilder().firstRowAsHeader(false)));
	}
}
