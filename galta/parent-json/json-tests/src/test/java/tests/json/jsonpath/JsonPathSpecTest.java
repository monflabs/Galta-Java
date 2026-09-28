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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

/**
 * JSON path filters, slices and syntax edge cases (RFC 9535).
 */
public class JsonPathSpecTest extends ProjectTestCase {

	private static final String ITEMS = "[{\"x\":1,\"q\":0},{\"x\":2},{\"y\":3},{\"x\":null}]";

	private static JsonValues read(String path, String json) {
		return JsonPathFactory.get().createJsonPath(path).read(JsonArray.parse(json));
	}
	private static JsonPath parse(String path) {
		return JsonPathFactory.get().createJsonPath(path);
	}

	public void testLiteralTruthiness() {
		// A literal operand of && / || / ! is evaluated as a boolean, not as "true"
		assertEquals(0, read("$[?(@.x && false)]", ITEMS)._size());
		assertEquals(3, read("$[?(@.x && true)]", ITEMS)._size());
		assertEquals(3, read("$[?(@.x || null)]", ITEMS)._size());
		assertEquals(4, read("$[?(@.x || 1)]", ITEMS)._size());
		assertEquals(3, read("$[?(@.x || 0)]", ITEMS)._size());
		assertEquals(3, read("$[?(@.x || '')]", ITEMS)._size());
		assertEquals(4, read("$[?(@.x || 'a')]", ITEMS)._size());
		assertEquals(4, read("$[?(!null)]", ITEMS)._size());
		assertEquals(0, read("$[?(!true)]", ITEMS)._size());
		// Existence of @.x (null counts as existing)
		assertEquals(3, read("$[?(@.x)]", ITEMS)._size());
		// A filter can't be a bare literal
		assertThrows(JsonException.class, () -> parse("$[?(true)]"));
	}

	public void testMissingOperands() {
		// Rejected when parsed, not a silent no-match, an NPE or an IllegalStateException
		for(String p: new String[] {
				"$[?@.x ==]", "$[?@.x !=]", "$[?@.x <]", "$[?@.x <=]", "$[?@.x >]", "$[?@.x >=]",
				"$[?@.x &&]", "$[?@.x ||]", "$[?!]", "$[?(@.x == )]",
				"$[?== 1]", "$[?&& @.x]"}) {
			assertThrows(p, JsonException.class, () -> parse(p));
		}
	}

	public void testComparisonOperands() {
		// A comparison compares literals and paths, not logical expressions
		assertThrows(JsonException.class, () -> parse("$[?(@.x==1) < 2]"));
		assertThrows(JsonException.class, () -> parse("$[?(@.x && @.y) == true]"));
		assertThrows(JsonException.class, () -> parse("$[?!@.x == 1]"));
		// ...parenthesized comparisons in logical expressions are fine
		assertEquals(1, read("$[?(@.x==1) && (@.q==0)]", ITEMS)._size());
	}

	public void testFilterComparisons() {
		assertEquals(1, read("$[?(@.x == 2)]", ITEMS)._size());
		assertEquals(3, read("$[?(@.x != 2)]", ITEMS)._size());
		assertEquals(1, read("$[?(@.x > 1)]", ITEMS)._size());
		assertEquals(2, read("$[?(@.x >= 1)]", ITEMS)._size());
		// Mixed types never compare
		assertEquals(0, read("$[?(@.x == '1')]", ITEMS)._size());
		assertEquals(0, read("$[?(@.x < 'a')]", ITEMS)._size());
		// Two missing values are equal (empty == empty), a missing value is not null
		assertEquals(2, read("$[?(@.z == @.w)]", "[{},{}]")._size());
		assertEquals(1, read("$[?(@.x == null)]", ITEMS)._size());
		// Path to path
		assertEquals(1, read("$[?(@.a == @.b)]", "[{\"a\":1,\"b\":1.0},{\"a\":1,\"b\":2}]")._size());
		// An indefinite path is not allowed in a comparison
		assertThrows(JsonException.class, () -> read("$[?(@.* == 1)]", ITEMS));
	}

	public void testStringComparisonByCodePoint() {
		// U+1F600 (a surrogate pair in UTF-16) is greater than U+FF61
		String json = "[\"😀\",\"｡\"]";
		JsonValues v = read("$[?(@ > '｡')]", json);
		assertEquals(1, v._size());
		assertEquals("😀", v.stringValue());
	}

	public void testSlices() {
		String a = "[0,1,2,3,4,5]";
		assertEquals(JsonValues.of(3,2,1), read("$[3:0:-1]", a));
		assertEquals(JsonValues.of(5,4,3,2,1,0), read("$[::-1]", a));
		assertEquals(JsonValues.of(5,3,1), read("$[::-2]", a));
		assertEquals(JsonValues.of(0,2,4), read("$[::2]", a));
		assertEquals(JsonValues.of(4,5), read("$[-2:]", a));
		assertEquals(JsonValues.of(0,1), read("$[-100:2]", a));
		assertTrue(read("$[10:20]", a).isEmpty());
		assertTrue(read("$[4:1]", a).isEmpty());
		assertTrue(read("$[-100:-50:-1]", a).isEmpty());
		// RFC 9535: a step of 0 selects nothing
		assertTrue(read("$[::0]", a).isEmpty());
		assertTrue(read("$[1:3:0]", a).isEmpty());
		// A large step does not overflow
		assertEquals(JsonValues.of(1), read("$[1::2147483647]", a));
		assertEquals(JsonValues.of(5), read("$[5::-2147483647]", a));
		// A slice on an object selects nothing
		assertTrue(JsonPathFactory.get().createJsonPath("$[0:2]").read(org.monflabs.json.JsonObject.parse("{\"a\":1}")).isEmpty());
	}

	public void testCanonicalPathOfSubPaths() {
		assertEquals("$[?(@.a.b==1)]", parse("$[?(@.a.b==1)]").canonicalPath());
		assertEquals("$[?(@.a[0].c==$.x.y)]", parse("$[?(@.a[0].c==$.x.y)]").canonicalPath());
		assertEquals("$[?(!@.a.b)]", parse("$[?(!@.a.b)]").canonicalPath());
	}

	public void testInvalidSyntax() {
		assertThrows(JsonException.class, () -> parse("$...a"));
		assertThrows(JsonException.class, () -> parse("$.-a"));
		assertThrows(JsonException.class, () -> parse("a"));
		assertThrows(JsonException.class, () -> parse("$[1"));
		assertThrows(JsonException.class, () -> parse("$[?]"));
	}

	public void testIsDefinite() {
		assertTrue(parse("$.a[0].b").isDefinite());
		assertTrue(parse("$['a']").isDefinite());
		assertFalse(parse("$.a[0,1]").isDefinite());
		assertFalse(parse("$.a['b','c']").isDefinite());
		assertFalse(parse("$..a").isDefinite());
		assertFalse(parse("$.a[*]").isDefinite());
		assertFalse(parse("$.a[1:2]").isDefinite());
		assertFalse(parse("$.a[?(@.b)]").isDefinite());
	}

	public void testPartialPath() {
		JsonPath p = JsonPathFactory.get().getPartialJsonPath("x = $.a.b + 1", 4);
		assertEquals("$.a.b", p.getJsonPath().trim());
		assertEquals(JsonValues.of(1), p.read(org.monflabs.json.JsonObject.parse("{\"a\":{\"b\":1}}")));
		JsonPath none = JsonPathFactory.get().getPartialJsonPath("x = 1", 4);
		assertTrue(none.isEmpty());
	}
}
