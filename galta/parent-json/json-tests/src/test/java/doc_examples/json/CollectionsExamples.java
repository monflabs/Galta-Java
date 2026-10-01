package doc_examples.json;

import java.util.List;
import java.util.stream.Collectors;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.stream.CsvMapping;
import org.monflabs.json.stream.CsvMapping.QuoteStrategy;
import org.monflabs.json.util.JsonCollectors;
import org.monflabs.json.util.Reducers;

import tests.ProjectTestCase;

/**
 * Samples of docs/GaltaJSON/Collections.md
 */
public class CollectionsExamples extends ProjectTestCase {

	public void testCopies() {
		JsonArray a = JsonArray.of(5, 3, 8, 1, 3);

		JsonArray big = a.filter(v -> JsonUtil.asInt(v) > 2);
		JsonArray tens = a.map(v -> JsonUtil.asInt(v) * 10);
		JsonArray sorted = a.sorted();

		assertEquals("[5,3,8,3]", big.stringify());
		assertEquals("[50,30,80,10,30]", tens.stringify());
		assertEquals("[1,3,3,5,8]", sorted.stringify());
		assertEquals("[5,3,8,1,3]", a.stringify());   // the source is untouched
		assertNotSame(a, a.filter(v -> true));         // always a new array
		assertSame(a, a.peek(v -> {}));                // except peek()

		a.removeIf(v -> JsonUtil.asInt(v) == 3);       // Collection.removeIf modifies the array
		assertEquals("[5,8,1]", a.stringify());
	}

	public void testSlicing() {
		JsonArray a = JsonArray.of(0, 1, 2, 3, 4, 5);

		assertEquals("[1,2]", a.slice(1, 3).stringify());
		assertEquals("[4,5]", a.slice(-2, null).stringify());
		assertEquals("[0,2,4]", a.slice(0, 6, 2).stringify());
		assertEquals("[5,4,3,2,1,0]", a.slice(null, null, -1).stringify());
		assertEquals("[2,3,4,5]", a.skip(2).stringify());
		assertEquals("[0,1]", a.limit(2).stringify());
		assertEquals("[1,2,3]", a.skipLimit(1, 3).stringify());
		assertEquals("[0,1,2]", a.takeWhile(v -> JsonUtil.asInt(v) < 3).stringify());
		assertEquals("[3,4,5]", a.dropWhile(v -> JsonUtil.asInt(v) < 3).stringify());
		assertEquals("[1,3,5]", a.remove(v -> JsonUtil.asInt(v) % 2 == 0).stringify());   // a copy without the matches
		assertEquals(6, a.size());
	}

	public void testSortingAndDistinct() {
		JsonArray a = JsonArray.of(3, 1.0, "b", 1, 2L, "a");
		assertEquals("[3,2,1,1,\"b\",\"a\"]", a.sorted(false).stringify());
		assertEquals("[3,1,\"b\",2,\"a\"]", a.distinct().stringify());    // 1.0 and 1 are the same number
		assertEquals("a", a.min());                     // strings sort before numbers
		assertEquals(3, a.max());

		JsonArray n = JsonArray.of(3, 1.0, 1, 2L);
		assertEquals(1.0, n.min());                      // the first of the smallest
		assertEquals(3, n.max());

		JsonArray books = JsonArray.parse("""
			[ {"cat":"fiction","price":12}, {"cat":"reference","price":9},
			  {"cat":"fiction","price":8} ]
			""");
		JsonArray byCatThenPrice = books.sorted(JsonUtil.objectComparator().add("cat").add("price", false));
		assertEquals("[12,8,9]", byCatThenPrice.map(b -> ((JsonObject)b).get("price")).stringify());
		assertEquals(2, books.distinct(b -> ((JsonObject)b).get("cat")).size());
	}

	public void testAggregates() {
		JsonArray a = JsonArray.of(1, 2, 3, 4);

		Integer sum = a.reduce((Integer acc, Object v) -> acc + JsonUtil.asInt(v), 0);
		assertEquals(Integer.valueOf(10), sum);
		assertEquals(Integer.valueOf(10), a.reduce(Reducers.sumInt()));
		assertEquals(Double.valueOf(10), a.reduce(Reducers.sumDouble()));
		assertTrue(a.anyMatch(v -> JsonUtil.asInt(v) > 3));
		assertTrue(a.allMatch(v -> v instanceof Integer));
		assertTrue(a.noneMatch(v -> v == null));
		assertEquals("1,2,3,4", a.join(','));
		assertEquals(Integer.valueOf(4), a.process(arr -> arr.size()));
		assertNull(JsonArray.create().min());
	}

	public void testFlat() {
		JsonArray nested = JsonArray.parse("[[1,2],3,{\"a\":4,\"b\":[5]}]");
		assertEquals("[1,2,3,4,[5]]", nested.flat().stringify());          // one level only
		assertEquals("[4,[5]]", JsonObject.parse("{\"a\":4,\"b\":[5]}").flat().stringify());
	}

	public void testFind() {
		JsonObject o = JsonObject.parse("""
			{ "id": 1, "child": { "id": 2, "child": { "id": 3 } }, "list": [ { "id": 4 } ] }
			""");
		assertEquals("[1,2,3,4]", o.find("id").stringify());

		// Not deep: stop descending into a value once it matched
		JsonObject nested = JsonObject.parse("{\"a\":{\"a\":{\"a\":1}}}");
		assertEquals(3, nested.find("a").size());
		assertEquals("[{\"a\":{\"a\":1}}]", nested.find("a", false).stringify());

		// By index, in every array
		JsonObject matrix = JsonObject.parse("{\"m\":[[1,2],[3,4]]}");
		assertEquals("[[1,2],1,3]", matrix.find(0).stringify());
		assertEquals("[[3,4],2]", matrix.find(-1, false).stringify());

		// A null key matches every value, like $..*
		assertEquals("[1,{\"c\":2},2]", JsonObject.parse("{\"a\":1,\"b\":{\"c\":2}}").find(null).stringify());
	}

	public void testFindAndSet() {
		JsonObject o = JsonObject.parse("{\"id\":1,\"child\":{\"id\":2},\"list\":[{\"id\":3}]}");
		o.findAndSet("id", 0);      // in place
		assertEquals("{\"id\":0,\"child\":{\"id\":0},\"list\":[{\"id\":0}]}", o.stringify());
	}

	public void testJavaStreams() {
		JsonArray a = JsonArray.of(5, 3, 8, 1);

		JsonArray evens = a.stream()
			.filter(v -> JsonUtil.asInt(v) % 2 == 0)
			.collect(JsonCollectors.toJsonArray());
		assertEquals("[8]", evens.stringify());

		JsonArray target = JsonArray.of("first");
		a.stream().map(v -> "n" + v).collect(JsonCollectors.toJsonArray(target));   // appends
		assertEquals("[\"first\",\"n5\",\"n3\",\"n8\",\"n1\"]", target.stringify());

		List<Object> list = a.parallelStream().sorted().collect(Collectors.toList());
		assertEquals(List.of(1, 3, 5, 8), list);
		assertEquals(a, a.parallelStream().collect(JsonCollectors.toJsonArray()));   // order kept

		JsonValues titles = JsonValues.of(JsonArray.parse("[{\"t\":\"a\"},{\"t\":\"b\"}]")).flat().get("t");
		JsonValues collected = java.util.stream.StreamSupport.stream(titles.spliterator(), false)
			.collect(JsonCollectors.toJsonArrayValues());
		assertEquals("[\"a\",\"b\"]", collected.stringify());
	}

	public void testCsv() {
		JsonArray rows = JsonArray.parse("""
			[ ["id", "name", "note"],
			  [1, "Ada", "says \\"hi\\""],
			  [2, "Grace", "a,b"],
			  [3, "", null],
			  [4.0, " padded", true] ]
			""");

		JsonArray lines = rows.stream()
			.map(CsvMapping.toCsvStrings())
			.collect(JsonCollectors.toJsonArray());
		assertEquals(List.of(
			"id,name,note",
			"1,Ada,\"says \"\"hi\"\"\"",
			"2,Grace,\"a,b\"",
			"3,,",
			"4,\" padded\",true"), lines);

		JsonArray semicolons = rows.stream()
			.map(CsvMapping.toCsvStrings(';', QuoteStrategy.EMPTY))
			.collect(JsonCollectors.toJsonArray());
		assertEquals("2;Grace;a,b", semicolons.get(2));
		assertEquals("3;\"\";", semicolons.get(3));

		JsonArray quoted = rows.stream()
			.map(CsvMapping.toCsvStrings(',', QuoteStrategy.ALWAYS))
			.collect(JsonCollectors.toJsonArray());
		assertEquals("\"3\",\"\",\"\"", quoted.get(3));
	}

	public void testCsvFromObjects() {
		JsonArray people = JsonArray.parse("[{\"id\":1,\"name\":\"Ada\"},{\"id\":2,\"name\":\"Grace\"}]");
		List<String> lines = people.stream()
			.map(p -> List.of(((JsonObject)p).get("id"), ((JsonObject)p).get("name")))
			.map(CsvMapping.toCsvStrings())
			.collect(Collectors.toList());
		assertEquals(List.of("1,Ada", "2,Grace"), lines);

		// An object is not a row
		assertEquals("", CsvMapping.toCsvStrings().apply(people.get(0)));
		// A Java array is a row
		assertEquals("1,a", CsvMapping.toCsvStrings().apply(new Object[] {1, "a"}));
	}
}
