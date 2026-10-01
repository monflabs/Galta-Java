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

import static org.junit.Assert.assertThrows;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpointer.JsonPointer;

import tests.ProjectTestCase;

/**
 * JsonPointer: immutability, large pointers, fragments, child pointers.
 */
public class JsonPointerSafetyTest extends ProjectTestCase {

	public void testPartsAreNotShared() {
		JsonPointer p = JsonPointer.of("/a/b");
		Object[] parts = p.getParts();
		parts[0] = "x";
		assertEquals("/a/b", p.toString());
		// The pointer of a cached JSON Path used to be corrupted this way
		JsonPath path = JsonPathFactory.get().getJsonPath("$.shared.ptr");
		path.toJsonPointer().getParts()[0] = "changed";
		assertEquals("/shared/ptr", JsonPathFactory.get().getJsonPath("$.shared.ptr").toJsonPointer().toString());
	}

	public void testDashHasNoJsonPath() {
		// Used to give "$['a'][-1]", the last element instead of past the last one
		assertThrows(JsonException.class, () -> JsonPointer.of("/a/-").toJsonPathString());
		// A "-" member name is fine
		assertEquals("$.a['-']", JsonPointer.EMPTY.getChild("a").getChild("-").toJsonPathString());
	}

	public void testManySegments() {
		// The parser used to recurse once per segment (StackOverflowError)
		StringBuilder b = new StringBuilder();
		for(int i=0; i<100_000; i++) {
			b.append("/a");
		}
		JsonPointer p = JsonPointer.of(b.toString());
		assertEquals(100_000, p.size());
		JsonPointer c = JsonPointer.EMPTY;
		for(int i=0; i<100_000; i++) {
			c = c.getChild("a");
		}
		assertEquals(p, c);
		assertEquals(p.hashCode(), c.hashCode());
		assertEquals(b.toString(), c.toString());
	}

	public void testChildPointers() {
		JsonPointer base = JsonPointer.of("/x");
		JsonPointer a = base.getChild("a").getChild(1);
		JsonPointer b = base.getChild("b");
		assertEquals("/x/a/1", a.toString());
		assertEquals("/x/b", b.toString());
		assertSame(base, b.getParent());
		assertEquals(1, a.getLastPart());
		assertEquals("a", a.getPart(1));
		assertEquals(3, a.size());
		JsonObject doc = JsonObject.parse("{\"x\":{\"a\":[0,7],\"b\":2}}");
		assertEquals(7, a.read(doc));
		assertTrue(base.contains(base));
		assertTrue(a.contains(base));
	}

	public void testFragment() {
		assertEquals(JsonPointer.of("/a b/c%d"), JsonPointer.ofFragment("#/a%20b/c%25d"));
		assertEquals(JsonPointer.of("/é+"), JsonPointer.ofFragment("#/%C3%A9+"));
		assertEquals(JsonPointer.EMPTY, JsonPointer.ofFragment("#"));
		assertEquals("a~b", JsonPointer.ofFragment("/a~0b").getPart(0));
		assertThrows(JsonException.class, () -> JsonPointer.ofFragment("#/a%2"));
		assertThrows(JsonException.class, () -> JsonPointer.ofFragment("#/a%G0"));
		assertThrows(JsonException.class, () -> JsonPointer.ofFragment("#/%FF"));
	}

	public void testJsonPathMemberNamesOnArrays() {
		JsonArray arr = JsonArray.of(1, 2);
		JsonObject doc = JsonObject.of("arr", arr);
		// A JSON Path member name never addresses an array element...
		JsonPointer member = JsonPointer.ofJsonPath("$.arr['0']");
		assertNull(member.read(doc));
		assertFalse(member.exists(doc));
		assertFalse(member.setValue(doc, 9));
		// ... while a RFC 6901 pointer token does
		assertEquals(1, JsonPointer.of("/arr/0").read(doc));
		assertEquals(1, JsonPointer.ofParts("arr", "0").read(doc));
		assertEquals(1, JsonPointer.ofJsonPath("$.arr[0]").read(doc));
	}
}
