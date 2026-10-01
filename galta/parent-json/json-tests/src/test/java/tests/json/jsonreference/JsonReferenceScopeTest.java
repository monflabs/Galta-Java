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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonreference.JsonReference;
import org.monflabs.json.jsonschema.PathSchemaFactory;
import org.monflabs.json.jsonschema.SchemaNode;

import tests.ProjectTestCase;

/**
 * Reference resolution: document scope, caching, cycles across documents, fragments.
 */
public class JsonReferenceScopeTest extends ProjectTestCase {

	/** A resolver over in-memory documents, recording the loads. */
	private static class MapResolver extends JsonReference.Resolver {
		final Map<String,String> docs;
		final List<String> loads = new ArrayList<>();
		MapResolver(Object root, String base, Map<String,String> docs) {
			super(root, base);
			this.docs = docs;
		}
		@Override
		public Object apply(JsonFactory f, String u) {
			if(docs.containsKey(u)) {
				loads.add(u);
				return f.parse(docs.get(u));
			}
			return super.apply(f, u);
		}
	}

	private static Object resolve(JsonObject root, MapResolver r, boolean keep) {
		return JsonReference.resolve(JsonFactory.get(), root, r, keep);
	}

	public void testCrossDocumentCycleTerminates() {
		// a.json -> b.json -> a.json used to load a fresh copy at each step (StackOverflowError)
		JsonObject root = JsonObject.parse("{\"x\":{\"$ref\":\"a.json\"}}");
		MapResolver r = new MapResolver(root, null, Map.of(
			"a.json", "{\"name\":\"a\",\"next\":{\"$ref\":\"b.json\"}}",
			"b.json", "{\"name\":\"b\",\"next\":{\"$ref\":\"a.json\"}}"));
		resolve(root, r, false);
		JsonObject a = root.getObject("x");
		JsonObject b = a.getObject("next");
		assertEquals("b", b.get("name"));
		assertSame(a, b.get("next"));
		assertEquals(List.of("a.json","b.json"), r.loads);
	}

	public void testExternalDocumentLoadedOnce() {
		JsonObject root = JsonObject.parse("{\"p\":{\"$ref\":\"d.json#/a\"},\"q\":{\"$ref\":\"d.json#/b\"},\"r\":{\"$ref\":\"./d.json\"}}");
		MapResolver r = new MapResolver(root, null, Map.of("d.json", "{\"a\":{\"v\":1},\"b\":{\"v\":2}}"));
		resolve(root, r, false);
		assertEquals(List.of("d.json"), r.loads);
		// Same document: shared identity
		assertSame(root.getObject("r").get("a"), root.get("p"));
	}

	public void testRelativeToContainingDocument() {
		// "c.json" inside "dir/b.json" is "dir/c.json", not the root's "c.json"
		JsonObject root = JsonObject.parse("{\"x\":{\"$ref\":\"dir/b.json\"}}");
		MapResolver r = new MapResolver(root, null, Map.of(
			"dir/b.json", "{\"c\":{\"$ref\":\"c.json#/v\"}}",
			"dir/c.json", "{\"v\":\"dir-c\"}",
			"c.json", "{\"v\":\"root-c\"}"));
		resolve(root, r, false);
		assertEquals("dir-c", root.getObject("x").get("c"));
		// With a base URL for the root
		JsonObject root2 = JsonObject.parse("{\"x\":{\"$ref\":\"b.json\"}}");
		MapResolver r2 = new MapResolver(root2, "http://h/s/root.json", Map.of(
			"http://h/s/b.json", "{\"y\":{\"$ref\":\"../t/c.json\"}}",
			"http://h/t/c.json", "{\"z\":3}"));
		resolve(root2, r2, false);
		assertEquals(3, root2.getObject("x").getObject("y").get("z"));
	}

	public void testPointerThroughReference() {
		// The result used to depend on the key order: the pointer went through an unresolved $ref
		String[] docs = {
			"{\"a\":{\"$ref\":\"#/b/c\"},\"b\":{\"$ref\":\"#/d\"},\"d\":{\"c\":7}}",
			"{\"d\":{\"c\":7},\"b\":{\"$ref\":\"#/d\"},\"a\":{\"$ref\":\"#/b/c\"}}",
		};
		for(String s: docs) {
			JsonObject o = JsonObject.parse(s);
			JsonReference.resolve(JsonFactory.get(), o, new JsonReference.Resolver(o), false);
			assertEquals(7, o.get("a"));
		}
		// A cycle through a pointer
		JsonObject c = JsonObject.parse("{\"a\":{\"$ref\":\"#/a/x\"}}");
		assertThrows(JsonException.class, () -> JsonReference.resolve(JsonFactory.get(), c, new JsonReference.Resolver(c), false));
	}

	public void testNameFragmentRejected() {
		// "#foo" used to be read as the pointer "/foo"
		JsonObject o = JsonObject.parse("{\"a\":{\"$ref\":\"#foo\"},\"foo\":1}");
		assertThrows(JsonException.class, () -> JsonReference.resolve(JsonFactory.get(), o, new JsonReference.Resolver(o), false));
		// The empty fragment is the whole document
		JsonObject e = JsonObject.parse("{\"a\":{\"$ref\":\"#\"}}");
		JsonReference.resolve(JsonFactory.get(), e, new JsonReference.Resolver(e), false);
		assertSame(e, e.get("a"));
	}

	public void testMalformedPercentEncoding() {
		// Used to throw an IllegalArgumentException
		for(String ref: new String[] {"#/a%2", "#/a%zz", "#/%C3"}) {
			JsonObject o = JsonObject.parse("{\"a\":{\"$ref\":\""+ref+"\"}}");
			assertThrows(ref, JsonException.class, () -> JsonReference.resolve(JsonFactory.get(), o, new JsonReference.Resolver(o), false));
		}
		JsonObject ok = JsonObject.parse("{\"é+\":1,\"a\":{\"$ref\":\"#/%C3%A9+\"}}");
		JsonReference.resolve(JsonFactory.get(), ok, new JsonReference.Resolver(ok), false);
		assertEquals(1, ok.get("a"));
	}

	public void testSharedTargetKeepsFirstReference() {
		JsonObject o = JsonObject.parse("{\"p\":{\"$ref\":\"#/defs/a\"},\"q\":{\"$ref\":\"#/defs/b\"},\"defs\":{\"a\":{\"v\":1},\"b\":{\"$ref\":\"#/defs/a\"}}}");
		JsonReference.resolve(JsonFactory.get(), o, new JsonReference.Resolver(o), true);
		assertSame(o.get("p"), o.get("q"));
		assertEquals("#/defs/a", ((JsonContainer)o.get("p")).getReference());
	}

	public void testSchemaDataKeywordsUntouched() {
		JsonObject s = JsonObject.parse("""
			{"$defs":{"a":{"type":"string"}},
			 "properties":{"default":{"$ref":"#/$defs/a"},"x":{"const":{"$ref":"#/$defs/a"},"enum":[{"$ref":"#/nope"}],"default":{"$ref":"x"},"examples":[{"$ref":"y"}]}}}
			""");
		JsonReference.resolve(JsonFactory.get(), s, new JsonReference.Resolver(s), false, true);
		JsonObject props = s.getObject("properties");
		// A property named "default" is a schema
		assertSame(s.getObject("$defs").get("a"), props.get("default"));
		// The data keywords keep their "$ref" data
		JsonObject x = props.getObject("x");
		assertEquals("#/$defs/a", x.getObject("const").get("$ref"));
		assertEquals("#/nope", x.getArray("enum").getObject(0).get("$ref"));
		assertEquals("x", x.getObject("default").get("$ref"));
		// Not in schema mode, the same document fails (unresolvable data reference)
		JsonObject s2 = JsonObject.parse("{\"const\":{\"$ref\":\"#/nope\"}}");
		assertThrows(JsonException.class, () -> JsonReference.resolve(JsonFactory.get(), s2, new JsonReference.Resolver(s2), false));
	}

	public void testSchemaIdAndAnchor() {
		JsonObject s = JsonObject.parse("""
			{"$id":"http://ex.com/root.json",
			 "$defs":{"item":{"$id":"http://ex.com/item.json","type":"integer"},
			          "named":{"$anchor":"nm","type":"string"},
			          "old":{"$id":"#legacy","type":"boolean"}},
			 "properties":{"a":{"$ref":"http://ex.com/item.json"},"b":{"$ref":"#nm"},"c":{"$ref":"#legacy"}}}
			""");
		// No external load: the identifiers are known
		JsonReference.resolve(JsonFactory.get(), s, new JsonReference.Resolver(s, "root.json"), false, true);
		JsonObject props = s.getObject("properties");
		assertEquals("integer", props.getObject("a").get("type"));
		assertEquals("string", props.getObject("b").get("type"));
		assertEquals("boolean", props.getObject("c").get("type"));
	}

	public void testSchemaFactoryCycleAndRelativeRefs() throws Exception {
		Path dir = Files.createTempDirectory("schemas");
		try {
			Files.createDirectories(dir.resolve("sub"));
			Files.writeString(dir.resolve("a.json"), "{\"type\":\"object\",\"properties\":{\"b\":{\"$ref\":\"sub/b.json\"}}}");
			Files.writeString(dir.resolve("sub/b.json"), "{\"type\":\"object\",\"properties\":{\"c\":{\"$ref\":\"c.json\"},\"a\":{\"$ref\":\"../a.json\"}}}");
			Files.writeString(dir.resolve("sub/c.json"), "{\"type\":\"string\"}");
			SchemaNode a = new PathSchemaFactory(dir, null).getSchema("a.json");
			SchemaNode b = a.getProperties().get("b");
			assertEquals("string", b.getProperties().get("c").getType());
			// Cyclic graph: b.a is a itself
			assertSame(a.wrapped(), b.getProperties().get("a").wrapped());
		} finally {
			deleteTree(dir);
		}
	}

	public void testPathSchemaFactorySymlinkEscape() throws Exception {
		Path dir = Files.createTempDirectory("schemas");
		Path outside = Files.createTempDirectory("outside");
		try {
			Files.writeString(outside.resolve("secret.json"), "{\"type\":\"string\"}");
			try {
				Files.createSymbolicLink(dir.resolve("link.json"), outside.resolve("secret.json"));
				Files.createSymbolicLink(dir.resolve("linkdir"), outside);
			} catch(UnsupportedOperationException | java.io.IOException ex) {
				return; // No symbolic links on this file system
			}
			PathSchemaFactory f = new PathSchemaFactory(dir, null);
			assertNull(f.getSchema("link.json"));
			assertNull(f.getSchema("linkdir/secret.json"));
		} finally {
			deleteTree(dir);
			deleteTree(outside);
		}
	}

	private static void deleteTree(Path p) throws Exception {
		try(var s = Files.walk(p)) {
			s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
				try { Files.delete(f); } catch(Exception ex) { /* ignore */ }
			});
		}
	}
}
