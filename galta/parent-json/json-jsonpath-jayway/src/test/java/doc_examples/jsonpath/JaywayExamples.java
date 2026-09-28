package doc_examples.jsonpath;

import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JaywayJsonPath;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.jsonpath.MonfLabsJsonPathConfiguration;
import org.monflabs.json.jsonpath.MonfLabsJsonProvider;
import org.monflabs.json.jsonpath.MonfLabsMappingProvider;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/JsonPathJayway.md
 */
public class JaywayExamples extends ProjectTestCase {

	private static final String STORE = """
			{ "store": {
			    "book": [
			      { "title": "Sayings", "author": "Nigel Rees", "price": 8.95, "isbn": null },
			      { "title": "Sword", "author": "Evelyn Waugh", "price": 12.99 },
			      { "title": "Moby Dick", "author": "Herman Melville", "price": 8.99, "isbn": "0-553-21311-3" }
			    ],
			    "bicycle": { "color": "red", "price": 19.95 }
			} }
			""";

	public void testConfiguration() throws Exception {
		Configuration conf = Configuration.builder()
				.jsonProvider(new MonfLabsJsonProvider())
				.mappingProvider(new MonfLabsMappingProvider())
				.build();

		JsonObject doc = JsonObject.parse(STORE);
		List<Object> titles = JsonPath.using(conf).parse(doc).read("$.store.book[?(@.price < 10)].title");
		assertEquals(JsonArray.of("Sayings", "Moby Dick"), titles);   // the result list is a JsonArray
		assertTrue(titles instanceof JsonArray);

		Object bicycle = JsonPath.using(conf).parse(doc).read("$.store.bicycle");
		assertSame(doc.getObject("store").getObject("bicycle"), bicycle);   // no copy

		// Parsing text with this provider produces GaltaJSON values as well
		Object parsed = JsonPath.using(conf).parse(STORE).json();
		assertTrue(parsed instanceof JsonObject);
	}

	public void testGlobalDefaults() throws Exception {
		MonfLabsJsonPathConfiguration.initialize();              // JVM-wide Jayway defaults
		try {
			JsonObject doc = JsonObject.parse(STORE);
			Double price = JsonPath.read(doc, "$.store.bicycle.price");
			assertEquals(19.95, price);
			assertEquals(3, (int)JsonPath.read(doc, "$.store.book.length()"));
		} finally {
			Configuration.setDefaults(null);                     // back to Jayway's own defaults
		}
	}

	public void testNullVersusMissing() throws Exception {
		Configuration conf = Configuration.builder()
				.jsonProvider(new MonfLabsJsonProvider())
				.mappingProvider(new MonfLabsMappingProvider())
				.build();
		JsonObject doc = JsonObject.parse(STORE);

		assertNull(JsonPath.using(conf).parse(doc).read("$.store.book[0].isbn"));   // present, null
		try {
			JsonPath.using(conf).parse(doc).read("$.store.book[1].isbn");         // missing
			fail();
		} catch(PathNotFoundException e) {
			// No results for path: $['store']['book'][1]['isbn']
		}
		// Indefinite paths skip what is missing
		List<Object> isbns = JsonPath.using(conf).parse(doc).read("$.store.book[*].isbn");
		assertEquals(2, isbns.size());                                               // null, "0-553-21311-3"

		// Jayway options still apply
		Configuration lenient = conf.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
		assertNull(JsonPath.using(lenient).parse(doc).read("$.store.book[1].isbn"));
		Configuration quiet = conf.addOptions(Option.SUPPRESS_EXCEPTIONS);
		assertNull(JsonPath.using(quiet).parse(doc).read("$.store.book[1].isbn"));

		// Filters: an existence test counts a present null
		List<Object> withIsbn = JsonPath.using(conf).parse(doc).read("$.store.book[?(@.isbn)].title");
		assertEquals(JsonArray.of("Sayings", "Moby Dick"), withIsbn);
		List<Object> nullIsbn = JsonPath.using(conf).parse(doc).read("$.store.book[?(@.isbn == null)].title");
		assertEquals(JsonArray.of("Sayings"), nullIsbn);
	}

	public void testUpdates() throws Exception {
		Configuration conf = Configuration.builder()
				.jsonProvider(new MonfLabsJsonProvider())
				.mappingProvider(new MonfLabsMappingProvider())
				.build();
		JsonObject doc = JsonObject.parse(STORE);

		JsonPath.using(conf).parse(doc)
				.set("$.store.bicycle.color", "blue")
				.put("$.store.bicycle", "gears", 21)
				.add("$.store.book", JsonObject.of("title", "New"))
				.delete("$.store.book[0]")
				.map("$.store.book[*].price", (v, c) -> v == null ? null : ((Number)v).doubleValue() * 2);

		// The document itself is modified
		assertEquals("blue", doc.getObject("store").getObject("bicycle").getString("color"));
		assertEquals(21, doc.getObject("store").getObject("bicycle").getInt("gears"));
		JsonArray books = doc.getObject("store").getArray("book");
		assertEquals(3, books.size());
		assertEquals("Sword", books.getObject(0).getString("title"));
		assertEquals(25.98, books.getObject(0).getDouble("price"));
		assertEquals("New", books.getObject(2).getString("title"));
	}

	public void testTypedReads() throws Exception {
		Configuration conf = Configuration.builder()
				.jsonProvider(new MonfLabsJsonProvider())
				.mappingProvider(new MonfLabsMappingProvider())
				.build();
		JsonObject doc = JsonObject.parse(STORE);

		// A target type of Map, List or Object copies the result into plain Java collections
		Object copy = JsonPath.using(conf).parse(doc).read("$.store.bicycle", Map.class);
		assertFalse(copy instanceof JsonObject);
		assertEquals("red", ((Map<?,?>)copy).get("color"));
		// Any value converts to its string form
		String color = JsonPath.using(conf).parse(doc).read("$.store.bicycle.color", String.class);
		assertEquals("red", color);
	}

	public void testJaywayJsonPath() throws Exception {
		// JaywayJsonPath does not depend on Jayway's JVM-wide defaults
		try {
			JsonObject doc = JsonObject.parse(STORE);
			JaywayJsonPath path = new JaywayJsonPath(JsonPath.compile("$.store.book[*].author"));
			assertFalse(path.isDefinite());

			// Results come back as JsonValues, like the built-in engine
			JsonValues authors = path.read(doc);
			assertEquals(3, authors._size());
			assertEquals("Herman Melville", authors._get(2));

			// A missing path gives an empty result instead of an exception
			JsonValues none = new JaywayJsonPath(JsonPath.compile("$.store.missing")).read(doc);
			assertTrue(none.isEmpty());

			try {
				path.read(doc, true);
				fail();
			} catch(JsonException e) {
				// Jayway JsonPath does not support JsonPointers for now
			}
		} finally {
			Configuration.setDefaults(null);
		}
	}
}
