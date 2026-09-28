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
package tests.json.regression;

import static org.junit.Assert.assertThrows;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.jsonpointer.JsonPointer;
import org.monflabs.json.jsonreference.JsonReference;
import org.monflabs.json.jsonschema.SchemaNode;
import org.monflabs.json.model.JsonModelAccessor;
import org.monflabs.json.stringifier.JsonStringifier;
import org.monflabs.json.util.JsonCollectors;

import tests.ProjectTestCase;

/**
 * Regression tests for defects found in the core json module audit.
 */
public class CoreRegressionTest extends ProjectTestCase {

	private static final JsonPathFactory paths = new JsonPathFactory();

	private JsonArray path(String jsonPath, String json) {
		return paths.getJsonPath(jsonPath).read(JsonFactory.get().parse(json)).toJsonArray();
	}

	public void testNativeEqualsNulls() {
		JsonFactory f = JsonFactory.get();
		assertTrue(f.nativeEquals(null, null));
		assertFalse(f.nativeEquals(null, "x"));
		assertFalse(f.nativeEquals("x", null));
		assertTrue(f.nativeEquals("x", "x"));
	}

	public void testStringifySharedEmptyContainers() throws Exception {
		// The same empty object/array referenced twice is not a cycle
		JsonObject e = JsonObject.create();
		JsonObject o = JsonObject.create().put("a", e).put("b", e);
		assertEquals("{\"a\":{},\"b\":{}}", o.stringify(true));
		JsonArray ea = JsonArray.create();
		JsonArray a = JsonArray.create().add(ea).add(ea);
		assertEquals("[[],[]]", a.stringify(true));
		// A stringifier can be reused on the same (empty) container
		JsonStringifier.StringSerializer w = new JsonStringifier.StringSerializer();
		w.setCompact(true);
		assertEquals("{}", w.stringify(e));
		assertEquals("{}", w.stringify(e));
		// A real cycle is still detected
		JsonObject cyclic = JsonObject.create();
		cyclic.put("self", cyclic);
		assertThrows(JsonException.CircularReference.class, () -> cyclic.stringify(true));
	}

	public void testJoinKeepsSeparators() {
		assertEquals(",a", JsonArray.of("", "a").join(','));
		assertEquals(",x", JsonArray.of((Object) null, "x").join(','));
		assertEquals("a,,b", JsonArray.of("a", "", "b").join(','));
	}

	public void testHashCodeConsistentWithEquals() {
		JsonArray a1 = JsonArray.of(1, "x");
		JsonArray a2 = JsonArray.of(1.0, "x");
		assertEquals(a1, a2);
		assertEquals(a1.hashCode(), a2.hashCode());
		JsonObject o1 = JsonObject.of("a", 1L, "b", JsonArray.of(2));
		JsonObject o2 = JsonObject.of("b", JsonArray.of(2.0), "a", 1);
		assertEquals(o1, o2);
		assertEquals(o1.hashCode(), o2.hashCode());
		Set<Object> set = new HashSet<>();
		set.add(a1);
		assertTrue(set.contains(a2));
		assertEquals(JsonUtil.hashCode(0), JsonUtil.hashCode(-0.0));
	}

	public void testAsBoolean() {
		assertTrue(JsonUtil.asBoolean(0.5));
		assertTrue(JsonUtil.asBoolean(4294967296L));
		assertFalse(JsonUtil.asBoolean(Double.NaN));
		assertFalse(JsonUtil.asBoolean(0));
		assertFalse(JsonUtil.asBoolean(0.0));
	}

	public void testEncodeStringQuotes() {
		// "\'" is not valid JSON: the other quote character is not escaped
		assertEquals("\"it's\"", JsonUtil.encodeString("it's", '\"'));
		assertEquals("'say \"hi\"'", JsonUtil.encodeString("say \"hi\"", '\''));
		assertEquals("\"say \\\"hi\\\"\"", JsonUtil.encodeString("say \"hi\"", '\"'));
		assertEquals("it's", JsonFactory.get().parse(JsonUtil.encodeString("it's", '\"')));
	}

	public void testNullSafeTypeChecks() {
		assertFalse(JsonUtil.isBoolean(null));
		assertFalse(JsonUtil.isString(null));
		assertTrue(JsonUtil.isBoolean(Boolean.TRUE));
	}

	public void testGetValueMessageContainsKey() {
		JsonObject o = JsonObject.create();
		JsonException e = assertThrows(JsonException.class, () -> o.getValue("missingKey"));
		assertTrue(e.getMessage(), e.getMessage().contains("missingKey"));
	}

	public void testArraySliceAndSkip() {
		JsonArray a = JsonArray.of(1, 2, 3, 4);
		assertEquals(JsonArray.of(4, 3, 2, 1), a.slice(null, null, -1));
		assertEquals(JsonArray.of(3, 2), a.slice(-2, 0, -1));
		assertEquals(JsonArray.of(1, 3), a.slice(0, 4, 2));
		assertThrows(JsonException.class, () -> a.slice(0, 4, 0));
		assertEquals(JsonArray.of(2, 3, 4), a.skipLimit(1, Integer.MAX_VALUE));
		assertEquals(a, a.skip(-1));
		JsonValues v = JsonValues.parseAndFlat("[1,2,3,4]");
		assertEquals(4, v.slice(-1).value());
		assertEquals(JsonArray.of(4, 3, 2, 1), v.slice(null, null, -1).toJsonArray());
		assertEquals(4, v.skipLimit(0, Integer.MAX_VALUE).toJsonArray().size());
	}

	public void testMultilineCommentEndingWithDoubleStar() {
		assertEquals(1, JsonFactory.get().parse("/* x **/ 1"));
		assertEquals(JsonArray.of(1, 2), JsonFactory.get().parse("[1 /* c **/, 2]"));
		assertEquals(JsonArray.of(1, 2), JsonFactory.get().parse("[1 /***/, 2]"));
	}

	public void testControlCharsKeptInEscapedString() {
		// The slow path (after an escape) used to drop raw tabs/newlines
		assertEquals("\n a\tb", JsonFactory.get().parse("\"\\n a\tb\""));
		assertEquals("a\tb", JsonFactory.get().parse("\"a\tb\""));
	}

	public void testMalformedExponentIsParseError() {
		// Used to escape as a NullPointerException from the exception constructor
		JsonException e = assertThrows(JsonException.class, () -> JsonFactory.get().parse("[1e]"));
		assertNotNull(e.getMessage());
		assertThrows(JsonException.class, () -> JsonFactory.get().parse("{v:1.0E}"));
	}

	public void testPointerEscapeAndEdgeCases() {
		JsonObject o = JsonObject.parse("{\"a~b\":1,\"c/d\":2,\"\":3,\"12345678901\":4,\"arr\":[10,20,30]}");
		JsonPointer p = JsonPointer.of("/a~0b");
		assertEquals(1, p.read(o));
		assertEquals("/a~0b", p.toJsonPointerString());
		assertEquals("/c~1d", JsonPointer.of("/c~1d").toJsonPointerString());
		assertEquals(3, JsonPointer.EMPTY.getChild("").read(o));
		// Too large for an index: it is an object key, not a negative Integer
		assertEquals(4, JsonPointer.of("/12345678901").read(o));
		assertEquals(JsonPointer.EMPTY, JsonPointer.of("/a~0b").getParent());
		assertThrows(JsonException.class, () -> JsonPointer.EMPTY.getParent());
		// "-" appends, add at index==size appends, negative indexes on replace/remove
		assertTrue(JsonPointer.of("/arr/-").setValue(o, 40));
		assertEquals(JsonArray.of(10, 20, 30, 40), o.getArray("arr"));
		assertTrue(JsonPointer.of("/arr/4").add(o, 50));
		assertEquals(50, o.getArray("arr").get(4));
		assertTrue(JsonPointer.of("/arr/-1").replace(o, 51));
		assertEquals(51, o.getArray("arr").get(4));
		assertTrue(JsonPointer.of("/arr/-1").remove(o));
		assertEquals(JsonArray.of(10, 20, 30, 40), o.getArray("arr"));
	}

	public void testJsonValuesFindByIndexAndSet() {
		JsonArray a = JsonArray.parse("[[1,2],[3,4]]");
		JsonValues found = JsonValues.of(a).find(1);
		assertFalse(found.isEmpty());
		assertTrue(found.toJsonArray().contains(4));
		JsonObject o = JsonObject.parse("{\"a\":1,\"b\":{\"a\":2}}");
		JsonValues.of(o).findAndSet("a", 9);
		assertEquals(9, o.get("a"));
		assertEquals(9, o.getObject("b").get("a"));
		// Sets index 0 of the array and, deeply, of the arrays it contains
		JsonValues.of(a).findAndSet(0, 7);
		assertEquals(7, a.get(0));
		assertEquals(7, a.getArray(1).get(0));
	}

	public void testCollectorsAreParallelSafe() {
		JsonArray r = IntStream.range(0, 2000).boxed().parallel().collect(JsonCollectors.toJsonArray());
		assertEquals(2000, r.size());
		JsonArray target = JsonArray.create();
		JsonArray same = IntStream.range(0, 5).boxed().collect(JsonCollectors.toJsonArray(target));
		assertSame(target, same);
		assertEquals(5, target.size());
	}

	public void testModelAccessorPutAtIndex() {
		JsonModelAccessor acc = new JsonModelAccessor();
		JsonArray a = JsonArray.of(1, 2, 3);
		assertTrue(acc.putMember(a, 1, 9));
		assertEquals(JsonArray.of(1, 9, 3), a);
		assertTrue(acc.putMember(a, 4, 5));
		assertEquals(5, a.size());
		assertEquals(5, a.get(4));
	}

	public void testNegativeStepSlicePath() {
		assertEquals(JsonArray.create(), path("$[::-1]", "[]"));
		assertEquals(JsonArray.create(), path("$[-10::-1]", "[1,2,3]"));
		assertEquals(JsonArray.of(3, 2, 1), path("$[::-1]", "[1,2,3]"));
	}

	public void testFilterOverNullDoesNotCorruptSingleton() {
		JsonArray r = path("$[?(@.a)]", "[null,{\"a\":1},{\"b\":2}]");
		assertEquals(1, r.size());
		// The shared JsonValues.NULL must still mean "null" afterwards
		assertTrue(JsonValues.of(null).isNull());
		assertTrue(JsonValues.NULL.isNull());
		assertTrue(JsonValues.of().isEmpty());
		// Filters and paths have a printable form
		assertNotNull(paths.getJsonPath("$[?(@.a == null)]").toString());
		assertTrue(paths.getJsonPath("$[?(@.a == 1)]").toString().contains("@"));
	}

	public void testSchemaNodeArrayTypes() {
		SchemaNode n = new SchemaNode(JsonObject.parse("{\"type\":[\"string\",\"null\"]}"));
		assertTrue(n.isNullable());
		assertEquals("string", n.getType());
		assertEquals("string", n.getMainType());
		assertFalse(n.isObject());
		assertNull(new SchemaNode(JsonObject.parse("{\"type\":\"object\"}")).getProperties());
		assertThrows(JsonException.class, () -> new SchemaNode(JsonObject.create()).schemaType());
	}

	public void testReferencesInsideArrays() {
		JsonObject root = JsonObject.parse("{\"defs\":{\"a\":{\"x\":1}},\"a%b\":5,\"items\":[{\"$ref\":\"#/defs/a\"},[{\"$ref\":\"#/a%25b\"}]]}");
		JsonReference.resolve(JsonFactory.get(), root, new JsonReference.Resolver(root), false);
		assertEquals(1, root.getArray("items").getObject(0).get("x"));
		// The fragment is percent-decoded
		assertEquals(5, root.getArray("items").getArray(1).get(0));
	}

	public void testJsonValuesNegativeIndexOnList() {
		JsonValues list = JsonValues.of(JsonArray.of(1, 2), JsonArray.of(3, 4));
		assertEquals(JsonValues.of(2, 4), list.get(-1));
	}

	public void testArrayAsConversionsOutOfRange() {
		JsonArray a = JsonArray.of("12");
		assertEquals(12, a.asInt(0));
		assertEquals(0, a.asInt(5));
		assertEquals(-1, a.asInt(5, -1));
		assertEquals("", a.asString(-3));
		assertFalse(a.asBoolean(1));
	}

	public void testSchemaTypeForContainers() {
		assertEquals(org.monflabs.json.jsonschema.SchemaType.OBJECT, new SchemaNode(JsonObject.parse("{\"type\":\"object\"}")).schemaType());
		assertEquals(org.monflabs.json.jsonschema.SchemaType.ARRAY, new SchemaNode(JsonObject.parse("{\"type\":[\"array\",\"null\"]}")).schemaType());
		assertThrows(JsonException.class, () -> new SchemaNode(JsonObject.parse("{\"type\":\"null\"}")).schemaType());
	}

	public void testParseErrorMessageShowsTheCharacter() {
		org.monflabs.json.parser.ParseException e = assertThrows(org.monflabs.json.parser.ParseException.class,
				() -> JsonFactory.get().parse("[1,}"));
		assertTrue(e.getMessage(), e.getMessage().startsWith("JsonParser: Unexpected character '}' (125) at position 3."));
	}

	public void testCheckedContainersCreateCheckedChildren() {
		org.monflabs.json.java.JavaJsonFactoryChecked f = org.monflabs.json.java.JavaJsonFactoryChecked.instance;
		JsonObject o = f.createObject();
		assertSame(f, o.factory());
		assertSame(f, f.createArray().factory());
		JsonObject child = o.getOrCreateObject("child");
		assertThrows(JsonException.class, () -> child.putValue("x", new Object()));
		JsonArray list = o.getOrCreateArray("list");
		assertThrows(JsonException.class, () -> list.addValue(new Object()));
		JsonObject copy = o.deepClone();
		assertThrows(JsonException.class, () -> copy.putValue("x", new Object()));
	}
}
