package doc_examples.json;

import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpointer.JsonPointer;
import org.monflabs.json.jsonreference.JsonReference;
import org.monflabs.json.stringifier.JsonStringifier;

import tests.ProjectTestCase;

/**
 * Samples of docs/GaltaJSON/Pointers.md
 */
public class PointersExamples extends ProjectTestCase {

	public void testRead() {
		JsonObject doc = JsonObject.parse("{\"a\":{\"b\":[10,20,30]},\"x/y\":1,\"m~n\":2,\"0\":\"zero\"}");

		assertEquals(10, JsonPointer.of("/a/b/0").read(doc));
		assertEquals(30, JsonPointer.of("/a/b/-1").read(doc));     // extension: from the end
		assertEquals(1, JsonPointer.of("/x~1y").read(doc));         // ~1 is '/'
		assertEquals(2, JsonPointer.of("/m~0n").read(doc));         // ~0 is '~'
		assertEquals("zero", JsonPointer.of("/0").read(doc));       // a number is a key on an object
		assertSame(doc, JsonPointer.of("").read(doc));

		assertNull(JsonPointer.of("/a/c").read(doc));               // missing: null
		assertFalse(JsonPointer.of("/a/c").exists(doc));
		assertTrue(JsonPointer.of("/a/b/2").exists(doc));
		assertFalse(JsonPointer.of("/a/b/3").exists(doc));
	}

	public void testAddAndSetValue() {
		JsonObject doc = JsonObject.parse("{\"list\":[1,2,3]}");

		assertTrue(JsonPointer.of("/list/1").add(doc, 9));          // inserts
		assertTrue(JsonPointer.of("/list/-").add(doc, 4));          // '-' appends
		assertFalse(JsonPointer.of("/list/9").add(doc, 0));         // beyond the end
		assertEquals("[1,9,2,3,4]", doc.getArray("list").stringify());

		// add() does not create missing parents...
		assertFalse(JsonPointer.of("/config/server/port").add(doc, 8080));
		assertFalse(doc.has("config"));
		// ...setValue() does: an array before a numeric part, an object otherwise
		assertTrue(JsonPointer.of("/config/server/port").setValue(doc, 8080));
		assertTrue(JsonPointer.of("/config/hosts/0").setValue(doc, "a"));
		assertTrue(JsonPointer.of("/config/hosts/-").setValue(doc, "b"));   // '-' (or the size) appends
		assertFalse(JsonPointer.of("/config/hosts/5").setValue(doc, "c"));  // no padding with nulls
		assertEquals("{\"server\":{\"port\":8080},\"hosts\":[\"a\",\"b\"]}", doc.getObject("config").stringify());

		// setValue() overwrites an array item instead of inserting
		assertTrue(JsonPointer.of("/list/0").setValue(doc, 0));
		assertEquals("[0,9,2,3,4]", doc.getArray("list").stringify());
	}

	public void testReplaceAndRemove() {
		JsonObject doc = JsonObject.parse("{\"a\":1,\"list\":[1,2,3]}");

		assertTrue(JsonPointer.of("/a").replace(doc, 2));
		assertFalse(JsonPointer.of("/b").replace(doc, 2));          // must exist
		assertFalse(doc.has("b"));

		assertTrue(JsonPointer.of("/list/-1").remove(doc));
		assertFalse(JsonPointer.of("/list/5").remove(doc));
		assertEquals("{\"a\":2,\"list\":[1,2]}", doc.stringify());
	}

	public void testLastItemMarker() {
		JsonObject doc = JsonObject.parse("{\"list\":[1,2,3]}");
		// "-" is the (nonexistent) item after the last one (RFC 6901)
		assertNull(JsonPointer.of("/list/-").read(doc));
		assertFalse(JsonPointer.of("/list/-").replace(doc, 4));
		assertTrue(JsonPointer.of("/list/-").add(doc, 4));           // it appends
		// The last item is -1 (an extension)
		assertEquals(4, JsonPointer.of("/list/-1").read(doc));
		assertTrue(JsonPointer.of("/list/-1").replace(doc, 5));
		assertEquals("[1,2,3,5]", doc.getArray("list").stringify());
	}

	public void testBuildingPointers() {
		JsonPointer p = JsonPointer.of("/store/book").getChild(0).getChild("a/b");
		assertEquals("/store/book/0/a~1b", p.toJsonPointerString());
		assertEquals("$.store.book[0]['a/b']", p.toJsonPathString());
		assertEquals("/store/book/0", p.getParent().toString());
		assertEquals(4, p.size());
		assertEquals(0, p.getPart(2));                               // indexes are Integers
		assertEquals(JsonPointer.of("/store/book/0/a~1b"), p);

		assertEquals("/store/book/0", JsonPointer.ofJsonPath("$.store.book[0]").toJsonPointerString());
		assertTrue(JsonPointer.of("").isEmpty());                   // the whole document
		assertEquals("", JsonPointer.of("/").getPart(0));            // the "" member
		assertEquals(2, JsonPointer.of("/a/").size());               // "a", then ""
		assertEquals("7", JsonPointer.EMPTY.getChild("7").getPart(0)); // a member name stays a name
		assertEquals(JsonPointer.of("/7"), JsonPointer.EMPTY.getChild("7"));   // the same token
	}

	public void testResolveLocalReferences() {
		JsonObject schema = JsonObject.parse("""
			{
			  "definitions": { "User": { "type": "object" } },
			  "properties": { "author": { "$ref": "#/definitions/User" } }
			}
			""");
		JsonReference.resolve(JsonFactory.get(), schema, new JsonReference.Resolver(schema), false);

		JsonObject author = schema.getObject("properties").getObject("author");
		assertEquals("object", author.getString("type"));
		assertSame(schema.getObject("definitions").getObject("User"), author);   // shared, not copied

		// The other properties of a reference object are ignored: the object is replaced
		JsonObject mixed = JsonObject.parse("{\"a\":{\"$ref\":\"#/b\",\"x\":1},\"b\":2}");
		JsonReference.resolve(JsonFactory.get(), mixed, new JsonReference.Resolver(mixed), false);
		assertEquals(2, mixed.get("a"));

		// A reference that cannot be resolved is an error, not a null
		JsonObject broken = JsonObject.parse("{\"a\":{\"$ref\":\"#/nope\"}}");
		assertThrows(JsonException.class, () -> JsonReference.resolve(JsonFactory.get(), broken, new JsonReference.Resolver(broken), false));
	}

	public void testKeepLocalReferences() throws Exception {
		JsonObject schema = JsonObject.parse("{\"defs\":{\"u\":{\"t\":1}},\"p\":{\"$ref\":\"#/defs/u\"}}");
		JsonReference.resolve(JsonFactory.get(), schema, new JsonReference.Resolver(schema), true);
		JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
		s.setOutputReferences(true);
		// The shared target carries the reference, at both places
		assertEquals("{\"defs\":{\"u\":{\"$ref\":\"#/defs/u\"}},\"p\":{\"$ref\":\"#/defs/u\"}}", s.stringify(schema));
	}

	public void testResolveExternalReferences() throws Exception {
		JsonObject doc = JsonObject.parse("{\"title\":\"Notes\",\"author\":{\"$ref\":\"people.json#/ada\"}}");
		Map<String,String> files = Map.of("people.json", "{\"ada\":{\"name\":\"Ada\"}}");

		JsonReference.Resolver resolver = new JsonReference.Resolver(doc) {
			@Override
			public Object apply(JsonFactory factory, String url) {
				if(files.containsKey(url)) {
					return factory.parse(files.get(url));
				}
				return super.apply(factory, url);   // "" is the root, anything else throws
			}
		};
		Object resolved = JsonReference.resolve(JsonFactory.get(), doc, resolver, true);

		assertSame(doc, resolved);
		assertEquals("{\"title\":\"Notes\",\"author\":{\"name\":\"Ada\"}}", doc.stringify());
		assertEquals("people.json#/ada", doc.getObject("author").getReference());

		// Stringify the references back instead of their content
		JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
		s.setOutputReferences(true);
		assertEquals("{\"title\":\"Notes\",\"author\":{\"$ref\":\"people.json#/ada\"}}", s.stringify(doc));

		List<String> refs = new ArrayList<>();
		JsonReference.findReferences(doc, (container, ref) -> refs.add(ref));
		assertEquals(List.of("people.json#/ada"), refs);
	}

	public void testUnresolvableReference() {
		JsonObject doc = JsonObject.parse("{\"a\":{\"$ref\":\"other.json\"}}");
		assertThrows(JsonException.class,
			() -> JsonReference.resolve(JsonFactory.get(), doc, new JsonReference.Resolver(doc), false));
	}
}
