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
package tests.json.jsonpath;

import static org.junit.Assert.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonreference.JsonReference;

import tests.ProjectTestCase;

/**
 * RFC 9535 conformance: singular queries, exact numbers, index range, function
 * extensions, strict mode, cyclic graphs.
 */
public class JsonPathRfc9535Test extends ProjectTestCase {

	// The result as a JSON array, whatever the number of values
	private static String list(org.monflabs.json.jsonpath.JsonValues r) {
		JsonArray a = JsonArray.create();
		for(int i=0; i<r._size(); i++) {
			a.add(r._get(i));
		}
		return a.stringify();
	}
	private static String read(String path, Object json) {
		return list(JsonPathFactory.get().getJsonPath(path).read(json));
	}
	private static String readStrict(String path, Object json) {
		return list(JsonPathFactory.strict().getJsonPath(path).read(json));
	}
	private static void lenientOnly(String path) {
		JsonPathFactory.get().createJsonPath(path);
		assertThrows(path, JsonException.class, () -> JsonPathFactory.strict().createJsonPath(path));
	}
	private static void invalid(String path) {
		assertThrows(path, JsonException.class, () -> JsonPathFactory.get().createJsonPath(path));
		assertThrows(path, JsonException.class, () -> JsonPathFactory.strict().createJsonPath(path));
	}

	public void testNonSingularComparisonRejectedAtCompileTime() {
		// Used to compile, and to fail only at runtime on non-empty arrays
		invalid("$[?@.a[*] == 1]");
		invalid("$[?@..a == 1]");
		invalid("$[?@.a[0:2] == 1]");
		invalid("$[?@.a['x','y'] == 1]");
		invalid("$[?@.a[?@.b] == 1]");
		invalid("$[?1 == $.*]");
		// Singular queries are fine, even when they select nothing
		JsonArray a = JsonArray.parse("[{\"a\":[1]},{\"a\":[2]},{}]");
		assertEquals("[{\"a\":[1]}]", read("$[?@.a[0] == 1]", a));
		assertEquals("[{}]", read("$[?@.a[0] == @.b]", a));
		// Existence tests take any query
		assertEquals("[{\"a\":[1]},{\"a\":[2]}]", read("$[?@.a[*]]", a));
	}

	public void testExactNumberLiterals() {
		JsonArray a = JsonArray.of(9007199254740992L, 9007199254740993L, new BigInteger("123456789012345678901234567890"), 0.1, 1);
		// Number literals used to be parsed as doubles: both longs matched
		assertEquals("[9007199254740993]", read("$[?@ == 9007199254740993]", a));
		assertEquals("[123456789012345678901234567890]", read("$[?@ == 123456789012345678901234567890]", a));
		assertEquals("[0.1]", read("$[?@ == 0.1]", a));
		assertEquals("[1]", read("$[?@ == 1.0]", a));
		assertEquals("[1]", read("$[?@ == 1e0]", a));
		assertEquals("[9007199254740992]", read("$[?@ < 9007199254740993 && @ > 100]", a));
		// -0 equals 0
		assertEquals("[0]", read("$[?@ == -0]", JsonArray.of(0)));
	}

	public void testLargeIndexes() {
		JsonArray a = JsonArray.of(1, 2, 3);
		// Indexes used to be limited to the int range
		assertEquals("[]", read("$[4294967296]", a));
		assertEquals("[]", read("$[9007199254740991]", a));
		assertEquals("[]", read("$[-9007199254740991]", a));
		assertEquals("[1,2,3]", read("$[-9007199254740991:9007199254740991]", a));
		assertEquals("[1]", read("$[0:9007199254740991:9007199254740991]", a));
		assertEquals("[3]", read("$[::-9007199254740991]", a));
		assertEquals("[3,2,1]", read("$[9007199254740991:-9007199254740991:-1]", a));
		invalid("$[9007199254740992]");
		invalid("$[-9007199254740992]");
		invalid("$[0:9007199254740992]");
	}

	public void testDeepScanOnCyclicGraph() {
		// A recursive structure resolved to a cyclic graph used to make $.. recurse forever
		JsonObject o = JsonObject.parse("{\"n\":{\"name\":\"n\",\"child\":{\"$ref\":\"#/n\"}}}");
		JsonReference.resolve(JsonFactory.get(), o, new JsonReference.Resolver(o), false);
		assertSame(o.getObject("n"), o.getObject("n").get("child"));
		assertEquals("[\"n\"]", read("$..name", o));
		assertEquals(3, JsonPathFactory.get().getJsonPath("$..*").read(o)._size());
		// With pointers too
		assertEquals(1, JsonPathFactory.get().getJsonPath("$..name").read(o, true)._size());
		// A shared, non cyclic container is reported at each location
		JsonObject shared = JsonObject.of("v", 1);
		JsonObject dag = JsonObject.of("a", shared, "b", shared);
		assertEquals("[1,1]", read("$..v", dag));
	}

	public void testFunctionExtensions() {
		// RFC 9535 section 2.4 examples
		JsonArray a = JsonArray.parse("[\"ab\",\"abc\",[1,2],{\"x\":1},5,\"\\uD83D\\uDE00\"]");
		assertEquals("[\"ab\",[1,2],{\"x\":1},\"\\uD83D\\uDE00\"]".replace("\\uD83D\\uDE00", "\uD83D\uDE00"), read("$[?length(@) < 3]", a));
		JsonArray b = JsonArray.parse("[{\"a\":1},{\"a\":1,\"b\":2},{}]");
		assertEquals("[{\"a\":1}]", read("$[?count(@.*) == 1]", b));
		JsonArray d = JsonArray.parse("[{\"date\":\"1974-05-11\"},{\"date\":\"1974-05-1x\"},{\"date\":\"1974-05-110\"},{\"date\":5}]");
		assertEquals("[{\"date\":\"1974-05-11\"},{\"date\":\"1974-05-1x\"}]", read("$[?match(@.date, '1974-05-..')]", d));
		JsonArray au = JsonArray.parse("[{\"author\":\"Bob\"},{\"author\":\"Rob Roy\"},{\"author\":\"bob\"}]");
		assertEquals("[{\"author\":\"Bob\"},{\"author\":\"Rob Roy\"}]", read("$[?search(@.author, '[BR]ob')]", au));
		assertEquals("[{\"author\":\"bob\"}]", read("$[?!search(@.author, '[BR]ob')]", au));
		JsonArray c = JsonArray.parse("[{\"c\":{\"color\":\"red\"}},{\"c\":[{\"color\":\"red\"},{\"color\":\"red\"}]},{\"color\":\"blue\"}]");
		assertEquals("[{\"c\":{\"color\":\"red\"}}]", read("$[?value(@..color) == 'red']", c));
		// The regular expression can be a query; an invalid one matches nothing
		JsonArray r = JsonArray.parse("[{\"s\":\"aa\",\"p\":\"a+\"},{\"s\":\"b\",\"p\":\"(\"}]");
		assertEquals("[{\"s\":\"aa\",\"p\":\"a+\"}]", read("$[?match(@.s, @.p)]", r));
		// I-Regexp: '.' doesn't match a line break, '^' and '$' are ordinary characters
		JsonArray lb = JsonArray.of("a\nb", "a-b", "^a$");
		assertEquals("[\"a-b\"]", read("$[?match(@, 'a.b')]", lb));
		assertEquals("[\"^a$\"]", read("$[?match(@, '^a$')]", lb));
		// Nested functions
		assertEquals("[\"abc\"]", read("$[?length(value(@)) == 3]", JsonArray.of("ab", "abc")));
		// Strict mode accepts them too
		assertEquals("[{\"a\":1}]", readStrict("$[?count(@.*) == 1]", b));
	}

	public void testFunctionWellTypedness() {
		invalid("$[?length(@.*) < 3]");           // not a singular query
		invalid("$[?count(1) == 1]");             // not a query
		invalid("$[?count(@.*) == 1 == 1]");
		invalid("$[?match(@.a, 'x') == true]");   // a logical result compared
		invalid("$[?value(@..color)]");            // a value used as a test
		invalid("$[?length(@)]");
		invalid("$[?length(@.a, @.b) == 1]");     // arity
		invalid("$[?length() == 1]");
		invalid("$[?foo(@) == 1]");               // unknown function
		invalid("$[?length(@.a == 1) == 1]");
	}

	public void testStrictMode() {
		JsonObject o = JsonObject.parse("{\"a-b\":1,\"1\":2,\"$a\":3,\"a\":{\"b\":4},\"'\":5,\"\\\"\":6}");
		lenientOnly("");
		lenientOnly("$.a ");
		lenientOnly("$. a");
		lenientOnly("$.a-b");
		lenientOnly("$.1");
		lenientOnly("$.$a");
		lenientOnly("$[-0]");
		lenientOnly("$['\\\"']");
		lenientOnly("$[\"\\'\"]");
		lenientOnly("$['\\uD800']");
		lenientOnly("$['\\uDC00']");
		lenientOnly("$['\\x41']");
		lenientOnly("$[?@.a == .5]");
		lenientOnly("$['\u0001']");
		// Valid RFC 9535 queries
		assertEquals("[1]", readStrict("$['a-b']", o));
		assertEquals("[4]", readStrict("$ .a .b", o));
		assertEquals("[4]", readStrict("$[ 'a' ][ 'b' ]", o));
		assertEquals("[5]", readStrict("$['\\'']", o));
		assertEquals("[6]", readStrict("$[\"\\\"\"]", o));
		assertEquals("[\"\uD83D\uDE00\"]", readStrict("$[0]", JsonArray.of("\uD83D\uDE00")));
		assertEquals("[1]", readStrict("$['\\uD83D\\uDE00']", JsonObject.of("\uD83D\uDE00", 1)));
		assertEquals("[1]", readStrict("$.\u00e9t\u00e9", JsonObject.of("\u00e9t\u00e9", 1)));
		assertEquals("[0.5]", readStrict("$[?@ == 0.5]", JsonArray.of(0.5, 1)));
		assertEquals("[0]", readStrict("$[?@ == -0]", JsonArray.of(0, 1)));
		// The lenient results are unchanged
		assertEquals("[1]", read("$.a-b", o));
		assertEquals("[2]", read("$.1", o));
		assertEquals("[3]", read("$.$a", o));
	}

	public void testQuotedMemberOnArrayIsNotAnIndex() {
		JsonObject o = JsonObject.parse("{\"arr\":[10,20]}");
		// Reads nothing...
		assertEquals("[]", read("$.arr['0']", o));
		// ... and used to write arr[0]
		JsonPath p = JsonPathFactory.get().getJsonPath("$.arr['0']");
		assertFalse(p.write(o, 99));
		assertEquals("[10,20]", o.getArray("arr").stringify());
		// An index still writes
		assertTrue(JsonPathFactory.get().getJsonPath("$.arr[0]").write(o, 99));
		assertEquals("[99,20]", o.getArray("arr").stringify());
		// A member on an object
		JsonObject m = JsonObject.parse("{\"o\":{}}");
		assertTrue(JsonPathFactory.get().getJsonPath("$.o['0']").write(m, 1));
		assertEquals("{\"0\":1}", m.getObject("o").stringify());
	}

	public void testFactoryCacheIsConcurrentAndBounded() throws Exception {
		JsonPathFactory f = new JsonPathFactory(16);
		assertSame(f.getJsonPath("$.a"), f.getJsonPath("$.a"));
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for(int t=0; t<8; t++) {
				int tt = t;
				futures.add(pool.submit(() -> {
					for(int i=0; i<2000; i++) {
						String path = "$.p"+((i*7+tt)%100);
						assertEquals(path, f.getJsonPath(path).getJsonPath());
					}
					return null;
				}));
			}
			for(Future<?> fu: futures) {
				fu.get(1, TimeUnit.MINUTES);
			}
		} finally {
			pool.shutdownNow();
		}
		int cached = 0;
		for(int i=0; i<100; i++) {
			if(f.getCacheProvider().contains("$.p"+i)) {
				cached++;
			}
		}
		assertTrue("cache not bounded: "+cached, cached<=16);
	}
}
