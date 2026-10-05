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
package tests.json.jsonreference;

import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonreference.JsonReference;

import tests.ProjectTestCase;

/**
 * Reference resolution: errors, chains, cycles.
 */
public class JsonReferenceResolveTest extends ProjectTestCase {

	private static Object resolve(JsonObject root, boolean keep) {
		return JsonReference.resolve(JsonFactory.get(), root, new JsonReference.Resolver(root), keep);
	}

	public void testManyReferencesToOneTarget() {
		// A target designated by many references is located once, and they all get it
		StringBuilder b = new StringBuilder("{\"defs\":{\"t\":{\"v\":1}},\"list\":[");
		for(int i=0; i<50; i++) {
			b.append(i>0 ? "," : "").append("{\"$ref\":\"#/defs/t\"},{\"$ref\":\"#/alias\"}");
		}
		JsonObject root = JsonObject.parse(b.append("],\"alias\":{\"$ref\":\"#/defs/t\"}}").toString());
		resolve(root, false);
		Object target = root.getObject("defs").get("t");
		for(Object o: root.getArray("list")) {
			assertSame(target, o);
		}
		assertSame(target, root.get("alias"));
		// A cycle is still detected after other references resolved
		JsonObject cyclic = JsonObject.parse("{\"ok\":{\"$ref\":\"#/t\"},\"t\":1,\"a\":{\"$ref\":\"#/b\"},\"b\":{\"$ref\":\"#/a\"}}");
		assertThrows(JsonException.class, () -> resolve(cyclic, false));
	}

	public void testUnresolvableReference() {
		// An error, not a silent null
		JsonObject o = JsonObject.parse("{\"a\":{\"$ref\":\"#/nope\"}}");
		assertThrows(JsonException.class, () -> resolve(o, false));
		JsonObject o2 = JsonObject.parse("{\"a\":[{\"$ref\":\"#/x/5\"}],\"x\":[1]}");
		assertThrows(JsonException.class, () -> resolve(o2, false));
		// An external document the resolver doesn't know
		JsonObject o3 = JsonObject.parse("{\"a\":{\"$ref\":\"other.json#/x\"}}");
		assertThrows(JsonException.class, () -> resolve(o3, false));
	}

	public void testNullTargetIsValid() {
		// A reference to an existing null value resolves to null
		JsonObject o = JsonObject.parse("{\"a\":{\"$ref\":\"#/n\"},\"n\":null}");
		resolve(o, false);
		assertTrue(o.containsKey("a"));
		assertNull(o.get("a"));
	}

	public void testChainedReferences() {
		// Resolved whatever the key order
		JsonObject o1 = JsonObject.parse("{\"a\":{\"$ref\":\"#/b\"},\"b\":{\"$ref\":\"#/c\"},\"c\":1}");
		resolve(o1, false);
		assertEquals(1, o1.get("a"));
		assertEquals(1, o1.get("b"));
		JsonObject o2 = JsonObject.parse("{\"c\":{\"v\":1},\"b\":{\"$ref\":\"#/c\"},\"a\":{\"$ref\":\"#/b\"}}");
		resolve(o2, false);
		assertSame(o2.get("c"), o2.get("a"));
	}

	public void testSiblingsAreIgnored() {
		JsonObject o = JsonObject.parse("{\"a\":{\"$ref\":\"#/b\",\"description\":\"x\"},\"b\":{\"v\":1}}");
		resolve(o, false);
		assertSame(o.get("b"), o.get("a"));
		// A $ref that is not a string is not a reference
		JsonObject n = JsonObject.parse("{\"a\":{\"$ref\":5}}");
		resolve(n, false);
		assertEquals(5, n.getObject("a").get("$ref"));
	}

	public void testLocalReferenceInExternalDocument() {
		// "#/..." inside another document refers to that document, not to the root
		JsonObject other = JsonObject.parse("{\"x\":{\"$ref\":\"#/y\"},\"y\":\"other-y\"}");
		JsonObject root = JsonObject.parse("{\"a\":{\"$ref\":\"other.json#/x\"},\"y\":\"root-y\",\"b\":{\"$ref\":\"other.json\"}}");
		JsonReference.Resolver r = new JsonReference.Resolver(root) {
			@Override
			public Object apply(JsonFactory f, String u) {
				return "other.json".equals(u) ? other : super.apply(f, u);
			}
		};
		JsonReference.resolve(JsonFactory.get(), root, r, false);
		assertEquals("other-y", root.get("a"));
		assertEquals("other-y", root.getObject("b").get("x"));
		// The same local pointer in two documents is not a cycle
		JsonObject d2 = JsonObject.parse("{\"p\":{\"$ref\":\"#/q\"},\"q\":2}");
		JsonObject r2 = JsonObject.parse("{\"p\":{\"$ref\":\"d2.json#/p\"},\"q\":1}");
		JsonReference.resolve(JsonFactory.get(), r2, new JsonReference.Resolver(r2) {
			@Override
			public Object apply(JsonFactory f, String u) {
				return "d2.json".equals(u) ? d2 : super.apply(f, u);
			}
		}, false);
		assertEquals(2, r2.get("p"));
	}

	public void testReferenceCycle() {
		JsonObject o = JsonObject.parse("{\"a\":{\"$ref\":\"#/b\"},\"b\":{\"$ref\":\"#/a\"}}");
		assertThrows(JsonException.class, () -> resolve(o, false));
		JsonObject self = JsonObject.parse("{\"a\":{\"$ref\":\"#/a\"}}");
		assertThrows(JsonException.class, () -> resolve(self, false));
	}

	public void testRecursiveStructure() {
		// A node referring to its parent: a legal recursive schema, giving a cyclic graph
		JsonObject o = JsonObject.parse("{\"n\":{\"type\":\"object\",\"child\":{\"$ref\":\"#/n\"}}}");
		resolve(o, true);
		JsonObject n = o.getObject("n");
		assertSame(n, n.get("child"));
		// findReferences() terminates and reports it once
		List<String> refs = new ArrayList<>();
		JsonReference.findReferences(o, (c,r) -> refs.add(r));
		assertEquals(List.of("#/n"), refs);
	}

	public void testKeepReferences() {
		JsonObject kept = JsonObject.parse("{\"a\":{\"$ref\":\"#/b\"},\"b\":{\"v\":1}}");
		resolve(kept, true);
		assertEquals("#/b", ((JsonContainer)kept.get("a")).getReference());
		JsonObject notKept = JsonObject.parse("{\"a\":{\"$ref\":\"#/b\"},\"b\":{\"v\":1}}");
		resolve(notKept, false);
		assertNull(((JsonContainer)notKept.get("a")).getReference());
	}

	public void testReferencesInsideExternalDocuments() {
		// The resolved external content is resolved too
		JsonObject root = JsonObject.parse("{\"a\":{\"$ref\":\"ext.json\"}}");
		JsonObject ext = JsonObject.parse("{\"y\":{\"$ref\":\"ext.json#/z\"},\"z\":3}");
		JsonReference.resolve(JsonFactory.get(), root, new JsonReference.Resolver(root) {
			@Override
			public Object apply(JsonFactory f, String u) {
				return u.equals("ext.json") ? ext : super.apply(f, u);
			}
		}, false);
		assertSame(ext, root.get("a"));
		assertEquals(3, ext.get("y"));
	}

	public void testPercentEncodedFragment() {
		JsonObject o = JsonObject.parse("{\"a%b\":1,\"c d\":2,\"r\":[{\"$ref\":\"#/a%25b\"},{\"$ref\":\"#/c%20d\"}]}");
		resolve(o, false);
		assertEquals(1, o.getArray("r").get(0));
		assertEquals(2, o.getArray("r").get(1));
	}
}
