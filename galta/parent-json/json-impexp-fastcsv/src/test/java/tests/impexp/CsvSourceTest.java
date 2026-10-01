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

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.csv.CsvSource;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

import tests.ProjectTestCase;

public class CsvSourceTest extends ProjectTestCase {
	
	public void testWithHeader() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.column("c1",JsonUtil::parseBoolean)
						.column("c2",JsonUtil::parseNumber)
						.column("c2-1",JsonUtil::parseByte)
						.column("c2-2",JsonUtil::parseShort)
						.column("c2-3",JsonUtil::parseInt)
						.column("c2-4",JsonUtil::parseLong)
						.column("c2-5",JsonUtil::parseFloat)
						.column("c2-6",JsonUtil::parseDouble)
						.column("c2-7",JsonUtil::parseBigInteger)
						.column("c2-8",JsonUtil::parseBigDecimal)
						.column("c3",JsonUtil::parseString)
						.column("c4",JsonUtil::parseObject)
						.column("c5",JsonUtil::parseArray)
						.column("c6")
						.estimateCount(true)
						.reader(lines(
							"c1,c2,c2-1,c2-2,c2-3,c2-4,c2-5,c2-6,c2-7,c2-8,c3,c4,c5,c6",
							"true,79,3,4,5,7,67.6,78.9,456,678.89,mystr,{a:1},\"[2,3,4]\",defstr"
						))
						.build();
		// 1 data row: the header is not counted
		runImport(source, "csv-source-alltypes.json",JsonInMemoryFormat.RECORDSBYCOLKEY,1);
	}
	public void testWithHeaderNoString() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.column("c1",JsonUtil::parseBoolean)
						.column("c2",JsonUtil::parseNumber)
						.column("c4",JsonUtil::parseObject)
						.column("c5",JsonUtil::parseArray)
						.reader(lines(
							"c1,c2,c3,c4,c5,c6",
							"true,79,mystr,{a:1},\"[2,3,4]\",defstr"
						))
						.build();
		runImport(source, "csv-source-alltypes-wh.json",JsonInMemoryFormat.RECORDSBYCOLKEY,-1);
	}
	public void testWithoutHeader() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.column("c1",JsonUtil::parseBoolean)
						.column("c2",JsonUtil::parseNumber)
						.column("c3",JsonUtil::parseString)
						.column("c4",JsonUtil::parseObject)
						.column("c5",JsonUtil::parseArray)
						.column("c6")
						.firstRowAsHeader(false)
						.reader(lines(
							"true,79,mystr,{a:1},\"[2,3,4]\",defstr"
						))
						.build();
		runImport(source, "csv-source-alltypes-wh.json",JsonInMemoryFormat.RECORDSBYCOLKEY,-1);
	}
	public void testWithoutHeaderNotSorted() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.column("c2",JsonUtil::parseNumber)
						.column("c1",JsonUtil::parseBoolean)
						.column("c6")
						.column("c4",JsonUtil::parseObject)
						.column("c5",JsonUtil::parseArray)
						.column("c3",JsonUtil::parseString)
						.reader(lines(
								"c1,c2,c3,c4,c5,c6",
								"true,79,mystr,{a:1},\"[2,3,4]\",defstr"
							))
						.build();
		assertEquals(-1, source.estimatedCount());
		runImport(source, "csv-source-alltypes-notsorted.json",JsonInMemoryFormat.RECORDS,-1);
	}
	public void testWithoutColumns() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.reader(lines(
							"c1,c2,c3,c4,c5,c6",
							"true,79,mystr,{a:1},\"[2,3,4]\",defstr"
						))
						.build();
		runImport(source, "csv-source-alltypes-nocol.json",JsonInMemoryFormat.RECORDSBYCOLKEY,-1);
	}
	public void testWithoutHeaderAndColumns() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.firstRowAsHeader(false)
						.reader(lines(
							"true,79,mystr,{a:1},\"[2,3,4]\",defstr"
						))
						.build();
		try {
			runImport(source, "csv-source-alltypes-nohdrcol.json",JsonInMemoryFormat.RECORDSBYCOLKEY,-1);
			fail();
		} catch(JsonException ex) {
			// Desired exception
		}
	}

	public void testMultiLines() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.column("c1",JsonUtil::parseString)
						.column("c2",JsonUtil::parseString)
						.column("c3",JsonUtil::parseString)
						.estimateCount(true)
						.reader(lines(
							"c1,c2,c3",
							"a,\"b\",c",
							"d,\"e\",f",
							"\"g\",h,\"i\""
						))
						.build();
		// 3 data rows: the header is not counted
		runImport(source, "csv-source-multi.json",JsonInMemoryFormat.RECORDSBYCOLKEY,3);
	}

	// C8: closing the source must close the underlying reader
	public void testCloseClosesReader() throws Exception {
		TrackingReader reader = new TrackingReader("c1,c2\na,b");
		CsvSource source = CsvSource.newBuilder()
						.reader(() -> reader)
						.build();
		try (Stream<JsonContent> s = source.stream(null)) {
			assertEquals(1, s.count());
		}
		assertTrue(reader.closed);

		TrackingReader reader2 = new TrackingReader("c1,c2\na,b");
		CsvSource source2 = CsvSource.newBuilder()
						.reader(() -> reader2)
						.build();
		// A stream that is never consumed closes its reader too
		source2.stream(null).close();
		assertTrue(reader2.closed);
	}
	// A duplicate header column fails stream() after the reader was opened: it must be closed
	public void testStreamClosesReaderOnDuplicateHeader() throws Exception {
		TrackingReader reader = new TrackingReader("c1,c1\na,b");
		CsvSource source = CsvSource.newBuilder()
						.reader(() -> reader)
						.build();
		try {
			source.stream(null).close();
			fail("Duplicate header expected");
		} catch(JsonException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("Duplicate"));
		}
		assertTrue(reader.closed);
	}
	private static class TrackingReader extends StringReader {
		boolean closed;
		TrackingReader(String s) {
			super(s);
		}
		@Override
		public void close() {
			closed = true;
			super.close();
		}
	}

	// C9: a row shorter than the header yields nulls for the missing columns
	public void testShortRow() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.column("c3",JsonUtil::parseInt)
						.reader(lines(
							"c1,c2,c3",
							"a,b",
							"d,e,7"
						))
						.build();
		List<JsonContent> l = collect(source);
		assertEquals(2, l.size());
		JsonObject r0 = (JsonObject)l.get(0).getJson();
		assertEquals("a", r0.get("c1"));
		assertEquals("b", r0.get("c2"));
		assertNull(r0.get("c3"));
		JsonObject r1 = (JsonObject)l.get(1).getJson();
		assertEquals(7, r1.get("c3"));
	}

	// C10: Row.size() works when the columns come from the header
	public void testRowSizeFromHeader() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.keyFunction( (row) -> "k"+row.size()+"-"+row.get("c1") )
						.reader(lines(
							"c1,c2,c3",
							"a,b,c"
						))
						.build();
		List<JsonContent> l = collect(source);
		assertEquals(1, l.size());
		assertEquals("k3-a", l.get(0).getKey().getId());
	}

	// C12: an empty input yields an empty stream
	public void testEmptyInput() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.reader(lines())
						.build();
		assertEquals(0, collect(source).size());

		CsvSource source2 = CsvSource.newBuilder()
						.column("c1")
						.firstRowAsHeader(false)
						.reader(lines())
						.build();
		assertEquals(0, collect(source2).size());
	}

	// C13: a UTF-8 BOM before the header is ignored
	public void testByteOrderMark() throws Exception {
		CsvSource source = CsvSource.newBuilder()
						.column("c1",JsonUtil::parseInt)
						.reader(lines(
							"\uFEFFc1,c2",
							"1,b"
						))
						.build();
		List<JsonContent> l = collect(source);
		assertEquals(1, l.size());
		JsonObject r0 = (JsonObject)l.get(0).getJson();
		assertEquals(2, r0.size());
		assertEquals(1, r0.get("c1"));
		assertEquals("b", r0.get("c2"));

		CsvSource source2 = CsvSource.newBuilder()
						.column("c1")
						.column("c2")
						.firstRowAsHeader(false)
						.reader(lines(
							"\uFEFFa,b"
						))
						.build();
		l = collect(source2);
		assertEquals("a", ((JsonObject)l.get(0).getJson()).get("c1"));
	}

	private static List<JsonContent> collect(CsvSource source) {
		try (Stream<JsonContent> s = source.stream(null)) {
			return s.collect(Collectors.toList());
		}
	}

	private void runImport(CsvSource source, String template, JsonInMemoryFormat format, long estimatedCount) throws Exception {
		assertEquals(estimatedCount, source.estimatedCount());
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
											.format(format)
											.estimatedCount(source::estimatedCount)
											.notification(JsonTargetImpl.consoleLogger)
											.build();
		source.exportTo(target);
		support.assertJsonTemplate(target.getContainer(), template);
	}
	
	
	public static Supplier<Reader> lines(String...lines) {
		StringBuilder b = new StringBuilder();
		for(int i=0; i<lines.length; i++) {
			if(i>0) {
				b.append('\n');
			}
			b.append(lines[i]);
		}
		final var s = b.toString();
		return () -> new StringReader(s);
	}

}
