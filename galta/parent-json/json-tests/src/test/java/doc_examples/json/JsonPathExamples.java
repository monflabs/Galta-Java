package doc_examples.json;

import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Locale;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.util.Reducers;

import tests.ProjectTestCase;

/**
 * Samples of docs/GaltaJSON/JsonPath.md
 */
public class JsonPathExamples extends ProjectTestCase {

	static final String STORE = """
		{
		  "store": {
		    "book": [
		      { "category": "reference", "author": "Nigel Rees", "title": "Sayings of the Century", "price": 8.95 },
		      { "category": "fiction", "author": "Evelyn Waugh", "title": "Sword of Honour", "price": 12.99 },
		      { "category": "fiction", "author": "Herman Melville", "title": "Moby Dick", "isbn": "0-553-21311-3", "price": 8.99 },
		      { "category": "fiction", "author": "J. R. R. Tolkien", "title": "The Lord of the Rings", "isbn": "0-395-19395-8", "price": 22.99 }
		    ],
		    "bicycle": { "color": "red", "price": 19.95 }
		  },
		  "expensive": 10
		}
		""";

	JsonObject json = JsonObject.parse(STORE);

	private JsonArray read(String path) {
		return JsonPathFactory.get().getJsonPath(path).read(json).toJsonArray();
	}

	public void testCompileAndRead() {
		JsonPath path = JsonPathFactory.get().getJsonPath("$.store.book[*].author");
		JsonValues authors = path.read(json);

		assertTrue(authors.isList());
		assertEquals(4, authors._size());
		assertEquals("Nigel Rees", authors._get(0));
		assertEquals("[\"Nigel Rees\",\"Evelyn Waugh\",\"Herman Melville\",\"J. R. R. Tolkien\"]",
				authors.toJsonArray().stringify());

		// The same, starting from a JsonValues
		assertEquals(authors, JsonValues.of(json).path("$.store.book[*].author"));
	}

	public void testChildrenAndDescendants() {
		assertEquals("[\"red\"]", read("$.store.bicycle.color").stringify());
		assertEquals("[\"red\"]", read("$['store']['bicycle']['color']").stringify());
		assertEquals(4, read("$..author").size());
		assertEquals("[8.95,12.99,8.99,22.99,19.95]", read("$.store..price").stringify());
		assertEquals(2, read("$.store.*").size());                 // the book array and the bicycle
		assertEquals(4, read("$.store.book.*").size());           // .* flattens arrays too
		assertEquals("[\"red\",19.95]", read("$.store.bicycle..*").stringify());
		assertEquals("[\"Sayings of the Century\"]", read("$..[0].title").stringify());
	}

	public void testIndexesAndSlices() {
		assertEquals("[\"Sayings of the Century\",\"Sword of Honour\"]", read("$..book[0,1].title").stringify());
		assertEquals("[\"Sayings of the Century\",\"Sword of Honour\"]", read("$..book[:2].title").stringify());
		assertEquals("[\"The Lord of the Rings\"]", read("$..book[-1].title").stringify());
		assertEquals("[\"Moby Dick\",\"The Lord of the Rings\"]", read("$..book[-2:].title").stringify());
		assertEquals("[\"Sayings of the Century\",\"Moby Dick\"]", read("$..book[::2].title").stringify());
		assertEquals("[22.99,8.99,12.99,8.95]", read("$..book[::-1].price").stringify());
		assertEquals("[\"red\",19.95]", read("$.store.bicycle['color','price']").stringify());
		assertEquals("[8.95,22.99]", read("$..book[0,-1:].price").stringify());
	}

	public void testFilters() {
		assertEquals("[\"Moby Dick\",\"The Lord of the Rings\"]", read("$..book[?(@.isbn)].title").stringify());
		assertEquals("[\"Sayings of the Century\",\"Sword of Honour\"]", read("$..book[?(!@.isbn)].title").stringify());
		assertEquals("[\"Sayings of the Century\",\"Moby Dick\"]", read("$..book[?(@.price < 10)].title").stringify());
		assertEquals("[\"Sword of Honour\",\"The Lord of the Rings\"]", read("$..book[?(@.price > $.expensive)].title").stringify());
		assertEquals("[\"The Lord of the Rings\"]",
				read("$..book[?(@.category == 'fiction' && @.price > 20)].title").stringify());
		assertEquals("[\"Sayings of the Century\",\"The Lord of the Rings\"]",
				read("$..book[?(@.price < 9 && @.category == 'reference' || @.price > 20)].title").stringify());
		// The parentheses are optional
		assertEquals(2, read("$..book[?@.isbn].title").size());
		// Filters apply to object members too
		assertEquals("[{\"color\":\"red\",\"price\":19.95}]", read("$.store[?(@.color == 'red')]").stringify());
	}

	public void testFilterMissingMembers() {
		// A comparison with a missing member is false, so != matches it
		assertEquals(3, read("$..book[?(@.isbn != '0-553-21311-3')]").size());
		// Values of different types are never equal, and only numbers and strings are ordered
		assertEquals(0, read("$..book[?(@.price == '8.95')]").size());
		assertEquals(0, read("$..book[?(@.title > 10)]").size());

		// == null matches an explicit null, not a missing member
		JsonObject items = JsonObject.parse("{\"items\":[{\"x\":null},{}]}");
		assertEquals("[{\"x\":null}]", JsonPathFactory.get().getJsonPath("$.items[?(@.x == null)]").read(items).toJsonArray().stringify());
		// An existence test is true for a null value
		assertEquals("[{\"x\":null}]", JsonPathFactory.get().getJsonPath("$.items[?(@.x)]").read(items).toJsonArray().stringify());
	}

	public void testFilterPrimitives() {
		JsonArray numbers = JsonArray.of(1, 4, 2, 5);
		assertEquals("[4,5]", JsonPathFactory.get().getJsonPath("$[?(@ >= 3)]").read(numbers).stringify());
	}

	public void testFunctions() {
		assertEquals("[\"Sayings of the Century\",\"The Lord of the Rings\"]", read("$..book[?length(@.title) > 15].title").stringify());
		assertEquals("[\"Moby Dick\",\"The Lord of the Rings\"]", read("$..book[?count(@.*) == 5].title").stringify());
		assertEquals("[\"Moby Dick\"]", read("$..book[?match(@.isbn, '0-553-.*')].title").stringify());
		assertEquals("[\"Moby Dick\"]", read("$..book[?search(@.author, 'Mel')].title").stringify());
		assertEquals("[\"Sayings of the Century\",\"Moby Dick\"]", read("$..book[?value(@..price) < 9].title").stringify());
	}

	public void testStrictMode() {
		JsonObject odd = JsonObject.parse("{\"a-b\":1}");
		assertEquals(1, JsonPathFactory.get().getJsonPath("$.a-b").read(odd).intValue());
		assertThrows(JsonException.class, () -> JsonPathFactory.strict().getJsonPath("$.a-b"));
		assertEquals(1, JsonPathFactory.strict().getJsonPath("$['a-b']").read(odd).intValue());
	}

	public void testUnsupported() {
		assertThrows(JsonException.class, () -> JsonPathFactory.get().getJsonPath("$[?(@.d in [2, 3])]"));
		assertThrows(JsonException.class, () -> JsonPathFactory.get().getJsonPath("$[?(@.a =~ /x/)]"));
		assertThrows(JsonException.class, () -> JsonPathFactory.get().getJsonPath("$[(@.length-1)]"));
		assertThrows(JsonException.class, () -> JsonPathFactory.get().getJsonPath("$..book[?(@.title.length() > 5)]"));
	}

	public void testFilterIndefinitePath() {
		// A comparison needs a singular query: rejected when the path is compiled
		assertThrows(JsonException.class, () -> JsonPathFactory.get().getJsonPath("$.store[?(@..price > 10)]"));
		assertThrows(JsonException.class, () -> JsonPathFactory.get().getJsonPath("$.store.book[?(@.tags[*] == 'x')]"));
	}

	public void testResultShapes() {
		JsonValues single = JsonValues.of(json).path("$.store.bicycle.color");
		JsonValues many = JsonValues.of(json).path("$..price");
		JsonValues none = JsonValues.of(json).path("$.store.car");
		JsonValues array = JsonValues.of(json).path("$.store.book");

		assertEquals(JsonValues.TYPE.VALUE, single.getType());
		assertEquals(JsonValues.TYPE.LIST, many.getType());
		assertEquals(JsonValues.TYPE.EMPTY, none.getType());
		// A path ending on an array yields that array as a single value
		assertTrue(array.isValue() && array.isArray());
		assertEquals(4, array.flat()._size());

		assertEquals("red", single.stringValue());
		assertEquals("none", none.stringValue("none"));
		assertThrows(JsonException.class, () -> many.doubleValue());   // not a single value
		assertThrows(JsonException.class, () -> none.stringValue());
	}

	public void testNumericMemberIsNotAnIndex() {
		// In the Java engine a dotted number is a member name, not an array index
		assertEquals(0, read("$.store.book.0.title").size());
		assertEquals(1, read("$.store.book[0].title").size());
		JsonObject map = JsonObject.parse("{\"0\":\"zero\"}");
		assertEquals("zero", JsonPathFactory.get().getJsonPath("$.0").read(map).stringValue());

		JsonObject odd = JsonObject.parse("{\"a-b\":1,\"a b\":2}");
		assertEquals(1, JsonPathFactory.get().getJsonPath("$.a-b").read(odd).intValue());
		assertEquals(2, JsonPathFactory.get().getJsonPath("$['a b']").read(odd).intValue());
	}

	public void testPointers() {
		JsonValues r = JsonPathFactory.get().getJsonPath("$..book[?(@.price > 20)].title").read(json, true);
		assertEquals("The Lord of the Rings", r.stringValue());
		assertEquals("/store/book/3/title", r.getPointer().toJsonPointerString());

		JsonValues all = JsonPathFactory.get().getJsonPath("$.store..color").read(json, true);
		assertEquals("[/store/bicycle/color]", all.getPointers().toString());
	}

	public void testWrite() {
		JsonPath gears = JsonPathFactory.get().getJsonPath("$.store.bicycle.gears");
		assertTrue(gears.write(json, 21));
		assertEquals(21, json.getObject("store").getObject("bicycle").get("gears"));

		// Missing parents are created: an object, or an array before an index
		JsonPathFactory.get().getJsonPath("$.store.car.wheels[0].size").write(json, 17);
		assertEquals("{\"wheels\":[{\"size\":17}]}", json.getObject("store").getObject("car").stringify());

		// Only definite paths can be written
		JsonPath all = JsonPathFactory.get().getJsonPath("$..price");
		assertFalse(all.isDefinite());
		assertThrows(JsonException.class, () -> all.write(json, 0));
	}

	public void testPathInfo() {
		JsonPath p = JsonPathFactory.get().getJsonPath("$['store'].book[0]");
		assertTrue(p.isDefinite());
		assertEquals("$.store.book[0]", p.canonicalPath());
		assertEquals("/store/book/0", p.toJsonPointer().toJsonPointerString());
		assertSame(p, JsonPathFactory.get().getJsonPath("$['store'].book[0]"));   // cached

		assertThrows(JsonException.class, () -> JsonPathFactory.get().getJsonPath("store.book"));
	}

	public void testNavigation() {
		JsonValues v = JsonValues.of(json);
		assertEquals("Moby Dick", v.get("store").get("book").get(2).get("title").stringValue());
		assertEquals("Moby Dick", v.get("store").get("book").get(-2).getString("title"));

		// On a list, get() applies to every item and skips the ones without the member
		JsonValues books = v.get("store").get("book").flat();
		assertEquals("[\"0-553-21311-3\",\"0-395-19395-8\"]", books.get("isbn").stringify());
		assertEquals("[\"red\"]", v.path("$.store.*").get("color").toJsonArray().stringify());
		assertTrue(v.get("store").keySet().contains("bicycle"));
		assertTrue(v.get("nothing").isEmpty());
	}

	public void testTypedAccessors() {
		JsonValues book = JsonValues.of(json).path("$.store.book[0]");
		assertEquals(8.95, book.getDouble("price"), 0);
		assertEquals("reference", book.getString("category"));
		assertEquals("n/a", book.getString("isbn", "n/a"));
		assertEquals(8, book.get("price").intValue());
		assertEquals(8, book.get("price").asInt());
		assertEquals(0, JsonValues.of("x").asInt());

		JsonValues price = book.get("price");
		assertTrue(price.eq(8.95) && price.lt(9.0) && price.gt(8.9));
		// Numbers compare exactly: 8.95 is not 8
		assertFalse(price.eq(8));
		assertTrue(price.gt(8));
		assertTrue(book.get("category").in("fiction", "reference"));
		assertTrue(book.get("author").matches("N.* Rees"));

		// All the comparisons are false on a missing value, eq(Object) included
		assertFalse(book.get("isbn").eq("x"));
		assertFalse(book.get("isbn").gt(1.0));
		assertFalse(book.get("isbn").eq((Object)"x"));
	}

	public void testStreamLike() {
		JsonValues prices = JsonValues.of(json).path("$..price");

		assertEquals("[8.95,8.99]", prices.filter(p -> p.lt(9.0)).stringify());
		assertEquals("[8.95,8.99,12.99]", prices.sorted().limit(3).stringify());
		assertEquals(22.99, prices.max().doubleValue(), 0);
		assertEquals(73.87, prices.rawReduce(Reducers.sumDouble()), 1e-9);
		assertEquals(73.87, prices.reduce(0.0, (sum, p) -> sum + p.doubleValue()), 1e-9);
		assertTrue(prices.anyMatch(p -> p.gt(20)));

		JsonValues titles = JsonValues.of(json).path("$..book[*]")
			.map(b -> b.getString("title").toUpperCase(Locale.ROOT))
			.skip(2);
		assertEquals("[\"MOBY DICK\",\"THE LORD OF THE RINGS\"]", titles.stringify());

		List<String> categories = new java.util.ArrayList<>();
		JsonValues.of(json).path("$..category").distinct().forEach(c -> categories.add(c.stringValue()));
		assertEquals(List.of("reference", "fiction"), categories);
	}

	public void testFind() {
		JsonValues v = JsonValues.of(json);
		assertEquals(5, v.find("price")._size());
		v.findAndSet("price", 0);
		assertEquals("[0,0,0,0,0]", v.path("$..price").stringify());
	}
}
