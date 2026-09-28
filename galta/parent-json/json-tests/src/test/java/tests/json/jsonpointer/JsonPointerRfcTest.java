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
package tests.json.jsonpointer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.jsonpointer.JsonPointer;

import tests.ProjectTestCase;

/**
 * JSON pointer: RFC 6901 conformance and the documented extensions.
 */
public class JsonPointerRfcTest extends ProjectTestCase {

	// The example document of RFC 6901, section 5
	private static final String RFC_DOC = """
		{
		  "foo": ["bar", "baz"],
		  "": 0,
		  "a/b": 1,
		  "c%d": 2,
		  "e^f": 3,
		  "g|h": 4,
		  "i\\\\j": 5,
		  "k\\"l": 6,
		  " ": 7,
		  "m~n": 8
		}
		""";

	public void testRfcExamples() {
		JsonObject doc = JsonObject.parse(RFC_DOC);
		assertSame(doc, JsonPointer.of("").read(doc));
		assertEquals(JsonArray.of("bar","baz"), JsonPointer.of("/foo").read(doc));
		assertEquals("bar", JsonPointer.of("/foo/0").read(doc));
		assertEquals(0, JsonPointer.of("/").read(doc));
		assertEquals(1, JsonPointer.of("/a~1b").read(doc));
		assertEquals(2, JsonPointer.of("/c%d").read(doc));
		assertEquals(3, JsonPointer.of("/e^f").read(doc));
		assertEquals(4, JsonPointer.of("/g|h").read(doc));
		assertEquals(5, JsonPointer.of("/i\\j").read(doc));
		assertEquals(6, JsonPointer.of("/k\"l").read(doc));
		assertEquals(7, JsonPointer.of("/ ").read(doc));
		assertEquals(8, JsonPointer.of("/m~0n").read(doc));
	}

	public void testEmptyTokens() {
		assertArrayEquals(new Object[] {""}, JsonPointer.of("/").getParts());
		assertArrayEquals(new Object[] {"",""}, JsonPointer.of("//").getParts());
		assertArrayEquals(new Object[] {"a",""}, JsonPointer.of("/a/").getParts());
		assertArrayEquals(new Object[] {"a","","b"}, JsonPointer.of("/a//b").getParts());

		JsonObject doc = JsonObject.parse("{\"\":{\"\":5},\"a\":{\"\":6}}");
		assertEquals(5, JsonPointer.of("//").read(doc));
		assertEquals(6, JsonPointer.of("/a/").read(doc));
		assertTrue(JsonPointer.of("/a/").exists(doc));
		assertEquals("/a/", JsonPointer.of("/a/").toJsonPointerString());
	}

	public void testInvalidEscapes() {
		// "~" must be followed by 0 or 1
		assertThrows(JsonException.class, () -> JsonPointer.of("/a~"));
		assertThrows(JsonException.class, () -> JsonPointer.of("/a~2"));
		assertThrows(JsonException.class, () -> JsonPointer.of("/~x/b"));
		// ~01 is "~1" (not "/"): the escapes are decoded in one pass
		assertEquals("~1", JsonPointer.of("/~01").getPart(0));
	}

	public void testNonCanonicalIndexes() {
		// "-0", "01" and "-01" are member names, never indexes
		JsonObject o = JsonObject.parse("{\"-0\":1,\"0\":2,\"-01\":3,\"-1\":4,\"01\":5}");
		assertEquals(1, JsonPointer.of("/-0").read(o));
		assertEquals(2, JsonPointer.of("/0").read(o));
		assertEquals(3, JsonPointer.of("/-01").read(o));
		assertEquals(4, JsonPointer.of("/-1").read(o));
		assertEquals(5, JsonPointer.of("/01").read(o));

		JsonArray a = JsonArray.of("x","y","z");
		assertNull(JsonPointer.of("/01").read(a));
		assertNull(JsonPointer.of("/-0").read(a));
		assertFalse(JsonPointer.of("/-01").exists(a));
		assertEquals("y", JsonPointer.of("/1").read(a));
		assertEquals("z", JsonPointer.of("/-1").read(a)); // extension: from the end
		assertEquals("x", JsonPointer.of("/-3").read(a));
		assertNull(JsonPointer.of("/-4").read(a));
		// A number too large for an index is a member name
		assertEquals("12345678901", JsonPointer.of("/12345678901").getPart(0));
	}

	public void testLastKeyNeverExists() {
		JsonObject doc = JsonObject.parse("{\"list\":[1,2,3]}");
		JsonPointer p = JsonPointer.of("/list/-");
		assertNull(p.read(doc));
		assertFalse(p.exists(doc));
		assertFalse(p.replace(doc, 9));
		assertFalse(p.remove(doc));
		assertFalse(JsonPointer.of("/list/-/a").add(doc, 9));
		assertEquals("[1,2,3]", doc.getArray("list").stringify());

		// add() and setValue() append
		assertTrue(p.add(doc, 4));
		assertTrue(p.setValue(doc, 5));
		assertEquals("[1,2,3,4,5]", doc.getArray("list").stringify());

		// On an object, "-" is a regular member name
		JsonObject o = JsonObject.parse("{\"-\":1}");
		assertEquals(1, JsonPointer.of("/-").read(o));
		assertTrue(JsonPointer.of("/-").replace(o, 2));
		assertEquals(2, o.get("-"));
	}

	public void testSetValueDoesNotPad() {
		JsonObject doc = JsonObject.parse("{\"list\":[1]}");
		// Beyond the end: rejected, the array is unchanged
		assertFalse(JsonPointer.of("/list/3").setValue(doc, 9));
		assertFalse(JsonPointer.of("/list/5000000").setValue(doc, 9));
		assertEquals(1, doc.getArray("list").size());
		// The size appends
		assertTrue(JsonPointer.of("/list/1").setValue(doc, 2));
		assertEquals("[1,2]", doc.getArray("list").stringify());
		// A missing intermediate part is created, "-" appends a new container
		assertTrue(JsonPointer.of("/list/-/name").setValue(doc, "n"));
		assertTrue(JsonPointer.of("/list/3/0").setValue(doc, true));
		assertEquals("[1,2,{\"name\":\"n\"},[true]]", doc.getArray("list").stringify());
		// A negative index addresses an existing item
		assertTrue(JsonPointer.of("/list/-4").setValue(doc, 0));
		assertEquals(0, doc.getArray("list").get(0));
		assertFalse(JsonPointer.of("/list/-9").setValue(doc, 0));
	}

	public void testAddNegativeIntermediate() {
		JsonObject doc = JsonObject.parse("{\"list\":[{\"a\":1},{\"a\":2}]}");
		assertTrue(JsonPointer.of("/list/-1/b").add(doc, 3));
		assertEquals(3, doc.getArray("list").getObject(1).get("b"));
		assertTrue(JsonPointer.of("/list/-1").add(doc, "x")); // inserted before the last one
		assertEquals("x", doc.getArray("list").get(1));
		assertFalse(JsonPointer.of("/list/7").add(doc, "y"));
	}

	public void testStringTokensAddressArrays() {
		// A member name that is a canonical index addresses an array item (RFC 6901)
		JsonArray a = JsonArray.of("x","y");
		JsonPointer p = JsonPointer.ofParts("1");
		assertEquals("y", p.read(a));
		assertTrue(p.exists(a));
		assertTrue(p.replace(a, "Y"));
		assertEquals("Y", a.get(1));
	}

	public void testOfParts() {
		JsonPointer p = JsonPointer.ofParts("a", 0, "b");
		assertEquals("/a/0/b", p.toJsonPointerString());
		assertSame(JsonPointer.EMPTY, JsonPointer.ofParts());
		assertThrows(JsonException.class, () -> JsonPointer.ofParts("a", 1.5));
	}

	public void testEqualsAndHashCode() {
		// Index 0 and member "0" are the same reference token
		JsonPointer i = JsonPointer.EMPTY.getChild("a").getChild(0);
		JsonPointer s = JsonPointer.EMPTY.getChild("a").getChild("0");
		assertEquals(i, s);
		assertEquals(i.hashCode(), s.hashCode());
		assertTrue(JsonPointer.of("/a/0/b").contains(s));
		// The order matters, for equals and (in practice) for hashCode
		JsonPointer ab = JsonPointer.of("/a/b");
		JsonPointer ba = JsonPointer.of("/b/a");
		assertFalse(ab.equals(ba));
		assertFalse(ab.hashCode()==ba.hashCode());
	}

	public void testGetChildKeepsNames() {
		JsonPointer p = JsonPointer.EMPTY.getChild("12").getChild("-");
		assertEquals("12", p.getPart(0));
		assertEquals("-", p.getPart(1));
		assertEquals("$['12']['-']", p.toJsonPathString());
	}

	public void testNumericKeysRoundTrip() {
		JsonObject doc = JsonObject.parse("{\"12\":\"twelve\",\"a\":{\"0\":\"zero\"}}");
		JsonValues v = JsonPathFactory.get().getJsonPath("$['12']").read(doc, true);
		JsonPointer p = v.getPointer();
		assertEquals("/12", p.toJsonPointerString());
		// The JSON path of the pointer designates the member, not an index
		assertEquals("$['12']", p.toJsonPathString());
		assertEquals("twelve", JsonPathFactory.get().getJsonPath(p.toJsonPathString()).read(doc).stringValue());
		assertEquals("twelve", p.read(doc));
		// Deep scan pointers too
		List<JsonPointer> all = JsonPathFactory.get().getJsonPath("$..*").read(doc, true).getPointers();
		assertTrue(all.contains(JsonPointer.of("/a/0")));
	}

	public void testJsonPathWriteUsesSameSyntax() {
		// The pointer is built from the parsed path: what JsonPath accepts, write() accepts
		JsonObject doc = JsonObject.create();
		assertTrue(JsonPathFactory.get().createJsonPath("$[ 'a' ]").write(doc, 1));
		assertEquals(1, doc.get("a"));
		assertTrue(JsonPathFactory.get().createJsonPath("$[\"b c\"].d").write(doc, 2));
		assertEquals(2, doc.getObject("b c").get("d"));
		// A quoted numeric name creates an object member, not an array
		assertTrue(JsonPathFactory.get().createJsonPath("$.x['0']").write(doc, 3));
		assertTrue(doc.get("x") instanceof JsonObject);
		assertEquals(3, doc.getObject("x").get("0"));
		// An index creates an array
		assertTrue(JsonPathFactory.get().createJsonPath("$.y[0]").write(doc, 4));
		assertEquals(JsonArray.of(4), doc.get("y"));
		// Indefinite paths cannot be written
		assertThrows(JsonException.class, () -> JsonPathFactory.get().createJsonPath("$..a").write(doc, 1));
		assertThrows(JsonException.class, () -> JsonPathFactory.get().createJsonPath("$.a[*]").write(doc, 1));
	}

	public void testOfJsonPathMembers() {
		assertEquals("0", JsonPointer.ofJsonPath("$['0']").getPart(0));
		assertEquals(0, JsonPointer.ofJsonPath("$[0]").getPart(0));
		assertEquals(-1, JsonPointer.ofJsonPath("$[-1]").getPart(0));
	}
}
