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

import static org.junit.Assert.assertThrows;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Iterator;
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
import org.monflabs.json.impexp.replication.RangeFilter;

import tests.ProjectTestCase;

/**
 * CSV robustness: single column files, invalid content, headers from spreadsheets,
 * formula injection, error reporting and concurrent streams.
 */
public class CsvRobustnessTest extends ProjectTestCase {

	private static List<Object> values(CsvSource source) {
		try(Stream<JsonContent> s = source.stream(null)) {
			return s.map(JsonContent::getJson).collect(Collectors.toList());
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

	// A single column CSV keeps its empty and null values
	public void testSingleColumnRoundTrip() throws Exception {
		String out = export("[{\"a\":\"x\"},{\"a\":\"\"},{\"a\":null},{\"a\":\"y\"}]", CsvTarget.newBuilder());
		assertEquals("a\nx\n\"\"\n\"\"\ny\n", out);
		assertEquals(List.of(JsonObject.of("a","x"), JsonObject.of("a",""), JsonObject.of("a",""), JsonObject.of("a","y")), values(csv(out).build()));
		List<Object> l = values(csv(out).emptyAsNull(true).build());
		assertEquals(4, l.size());
		assertNull(((JsonObject)l.get(1)).get("a"));

		// Empty lines written by another tool are values too, with a single column
		assertEquals(3, values(csv("a\nx\n\ny\n").build()).size());
		assertEquals(2, values(csv("a\nx\n\ny\n").skipEmptyLines(true).build()).size());
	}

	// With several columns, the empty lines are skipped, unless asked otherwise
	public void testEmptyLinesWithSeveralColumns() throws Exception {
		assertEquals(2, values(csv("a,b\n1,2\n\n3,4\n").build()).size());
		List<Object> l = values(csv("a,b\n1,2\n\n3,4\n").skipEmptyLines(false).emptyAsNull(true).build());
		assertEquals(3, l.size());
		assertEquals(JsonObject.of("a",null,"b",null), l.get(1));
	}

	// A quote that is never closed is reported with its line, instead of swallowing the rest of the file
	public void testUnterminatedQuote() throws Exception {
		JsonException ex = assertThrows(JsonException.class, () -> values(csv("a,b\n1,2\n3,\"oops\n5,6\n7,8\n").build()));
		assertTrue(ex.getMessage(), ex.getMessage().contains("line 3"));
	}

	// Spreadsheets add trailing separators to the header
	public void testTrailingEmptyHeaderColumns() throws Exception {
		assertEquals(List.of(JsonObject.of("a","1","b","2")), values(csv("a,b,,\n1,2,,\n").build()));
		// An empty column in the middle is still a column
		assertEquals(List.of(JsonObject.of("a","1","","2","b","3")), values(csv("a,,b,,\n1,2,3,,\n").build()));
	}

	public void testHeaderNamesAreTrimmed() throws Exception {
		assertEquals(List.of(JsonObject.of("a","1","b","2")), values(csv("a , b\n1,2\n").build()));
		assertEquals(List.of(JsonObject.of("a ","1"," b","2")), values(csv("a , b\n1,2\n").trimHeader(false).build()));
	}

	// A column defined in the source must be in the header
	public void testMissingDeclaredColumn() throws Exception {
		JsonException ex = assertThrows(JsonException.class, () -> values(csv("a,b\n1,2\n").column("c",JsonUtil::parseInt).build()));
		assertTrue(ex.getMessage(), ex.getMessage().contains("\"c\""));
		assertEquals(List.of(JsonObject.of("a","1","b","2")), values(csv("a,b\n1,2\n").column("c",JsonUtil::parseInt).allowMissingColumns(true).build()));
	}

	// A cell that cannot be converted reports its line and column
	public void testCellConversionError() throws Exception {
		JsonException ex = assertThrows(JsonException.class, () -> values(csv("a,b\n1,2\n3,x\n").column("b",JsonUtil::parseInt).build()));
		assertTrue(ex.getMessage(), ex.getMessage().contains("line 3"));
		assertTrue(ex.getMessage(), ex.getMessage().contains("\"b\""));
	}

	// A record that is not an object is reported, not a ClassCastException
	public void testNonObjectRecord() throws Exception {
		JsonException ex = assertThrows(JsonException.class, () -> export("[\"abc\"]", CsvTarget.newBuilder()));
		assertTrue(ex.getMessage(), ex.getMessage().contains("not a JSON object"));
		// Unless every column computes its value
		assertEquals("v\nabc\n", export("[\"abc\"]", CsvTarget.newBuilder().column("v", (c) -> (String)c.getJson())));
	}

	public void testEscapeFormulas() throws Exception {
		String json = "[{\"a\":\"=SUM(A1)\",\"b\":\"+1\",\"c\":\"-2\",\"d\":\"@x\",\"e\":\"ok\"}]";
		assertEquals("a,b,c,d,e\n=SUM(A1),+1,-2,@x,ok\n", export(json, CsvTarget.newBuilder()));
		assertEquals("a,b,c,d,e\n'=SUM(A1),'+1,'-2,'@x,ok\n", export(json, CsvTarget.newBuilder().escapeFormulas(true)));
	}

	// Each stream has its own reader: a source can be streamed concurrently
	public void testConcurrentStreams() throws Exception {
		CsvSource source = csv("a\n1\n2\n3\n").build();
		Stream<JsonContent> s1 = source.stream(null);
		Iterator<JsonContent> it = s1.iterator();
		assertEquals(JsonObject.of("a","1"), it.next().getJson());
		assertEquals(3, values(source).size());
		assertEquals(JsonObject.of("a","2"), it.next().getJson());
		s1.close();
	}

	// The range filter applies to the timestamps of the rows
	public void testRangeFilter() throws Exception {
		CsvSource source = csv("id,ts\n1,2024-01-01T00:00:00Z\n2,2025-01-01T00:00:00Z\n")
				.timestampFunction((row) -> Instant.parse((String)row.get("ts")))
				.build();
		try(Stream<JsonContent> s = source.stream(new RangeFilter(Instant.parse("2024-06-01T00:00:00Z"), null))) {
			assertEquals(List.of("2"), s.map((c) -> ((JsonObject)c.getJson()).getString("id")).toList());
		}
	}

	// The error of a target reports the key of the content
	public void testTargetErrorReportsTheKey() throws Exception {
		StringWriter out = new StringWriter();
		CsvTarget target = CsvTarget.newBuilder().writer(() -> out).build();
		JsonContainerSource source = JsonContainerSource.newBuilder().container(JsonArray.parse("[{\"a\":1},{\"a\":2,\"b\":3}]")).build();
		JsonException ex = assertThrows(JsonException.class, () -> source.exportTo(target));
		assertTrue(ex.getMessage(), ex.getMessage().contains("Error while importing :1"));
	}
}
