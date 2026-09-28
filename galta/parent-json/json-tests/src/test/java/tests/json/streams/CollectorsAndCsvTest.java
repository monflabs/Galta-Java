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
package tests.json.streams;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.monflabs.json.JsonArray;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.stream.CsvMapping;
import org.monflabs.json.stream.CsvMapping.QuoteStrategy;
import org.monflabs.json.util.CollectorImpl;
import org.monflabs.json.util.JsonCollectors;

import tests.ProjectTestCase;

/**
 * Stream collectors and the CSV line mapping.
 */
public class CollectorsAndCsvTest extends ProjectTestCase {

	public void testCollectorImplAppliesTheFinisher() {
		// The 4-argument constructor has no characteristic, so the finisher always runs
		CollectorImpl<Integer,List<Integer>,Integer> c = new CollectorImpl<>(ArrayList::new, List::add, (a,b) -> { a.addAll(b); return a; }, List::size);
		assertTrue(c.characteristics().isEmpty());
		assertFalse(c.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH));
		assertEquals(Integer.valueOf(3), Stream.of(1,2,3).collect(c));
		assertEquals(Integer.valueOf(1000), IntStream.range(0, 1000).boxed().parallel().collect(c));
	}

	public void testToJsonArrayInParallel() {
		JsonArray a = IntStream.range(0, 10000).boxed().parallel().map(i -> (Object)i).collect(JsonCollectors.toJsonArray());
		assertEquals(10000, a.size());
		for(int i=0; i<a.size(); i++) {
			assertEquals(i, a.get(i));
		}
		// Into an existing array
		JsonArray target = JsonArray.of("x");
		assertSame(target, Stream.of((Object)1, 2).collect(JsonCollectors.toJsonArray(target)));
		assertEquals(JsonArray.of("x", 1, 2), target);
	}

	public void testToJsonArrayValues() {
		// An empty JsonValues adds nothing, a list adds all its values
		JsonValues r = Stream.of(JsonValues.of(1), JsonValues.of(), JsonValues.of(2, 3), JsonValues.of((Object)null))
				.collect(JsonCollectors.toJsonArrayValues());
		assertEquals(JsonArray.parse("[1,2,3,null]"), r.arrayValue());
		JsonValues p = IntStream.range(0, 2000).boxed().parallel().map(i -> JsonValues.of(i, -i))
				.collect(JsonCollectors.toJsonArrayValues());
		assertEquals(4000, p.arrayValue().size());
		assertEquals(-1999, p.arrayValue().get(3999));
	}

	public void testCsvStringsAreThreadSafe() {
		Function<Object,String> f = CsvMapping.toCsvStrings();
		List<List<Object>> rows = new ArrayList<>();
		for(int i=0; i<20000; i++) {
			rows.add(List.of(i, "name "+i, "quote\"" + i, i%2==0 ? "a,b" : ""));
		}
		List<String> lines = rows.parallelStream().map(f).collect(Collectors.toList());
		for(int i=0; i<lines.size(); i++) {
			String expected = i + ",name " + i + ",\"quote\"\"" + i + "\"," + (i%2==0 ? "\"a,b\"" : "");
			assertEquals(expected, lines.get(i));
		}
	}

	public void testCsvQuoting() {
		Object[] row = new Object[] {1.5, "", null, "x y", " lead", "tab\t", "line\nbreak", "semi;colon"};
		assertEquals("1.5,,,x y,\" lead\",\"tab\t\",\"line\nbreak\",semi;colon", CsvMapping.toCsvStrings().apply(row));
		assertEquals("1.5,\"\",,x y,\" lead\",\"tab\t\",\"line\nbreak\",semi;colon", CsvMapping.toCsvStrings(',', QuoteStrategy.EMPTY).apply(row));
		assertEquals("\"1.5\",\"\",\"\",\"x y\",\" lead\",\"tab\t\",\"line\nbreak\",\"semi;colon\"", CsvMapping.toCsvStrings(',', QuoteStrategy.ALWAYS).apply(row));
		assertEquals("1.5;;;x y;\" lead\";\"tab\t\";\"line\nbreak\";\"semi;colon\"", CsvMapping.toCsvStrings(';', QuoteStrategy.REQUIRED).apply(row));
		// A long value, and a non row value
		String big = "v".repeat(5000);
		assertEquals(big+",\""+big+",\"", CsvMapping.toCsvStrings().apply(List.of(big, big+",")));
		assertEquals("", CsvMapping.toCsvStrings().apply("not a row"));
		assertEquals("", CsvMapping.toCsvStrings().apply(null));
	}
}
