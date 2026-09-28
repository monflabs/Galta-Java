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
package tests.jsonpath;

import static org.junit.Assert.assertThrows;

import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.MonfLabsJsonProvider;
import org.monflabs.json.jsonpath.MonfLabsMappingProvider;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;

import tests.ProjectTestCase;

/**
 * Regression tests for the Jayway provider backed by Monflabs JSON containers.
 * The reference (json-smart) provider is used as an oracle where Jayway's own
 * semantics are the specification.
 */
public class MonfLabsJsonProviderTest extends ProjectTestCase {

	/** Explicit, non-global configuration so the test does not depend on Configuration.setDefaults. */
	private static final Configuration MONFLABS = Configuration.builder()
			.jsonProvider(new MonfLabsJsonProvider())
			.mappingProvider(new MonfLabsMappingProvider())
			.build();

	private static final Configuration JSONSMART = Configuration.builder()
			.jsonProvider(new JsonSmartJsonProvider())
			.mappingProvider(new JsonSmartMappingProvider())
			.build();

	private static int intAt(JsonArray a, int i) {
		return ((Number)a.get(i)).intValue();
	}

	// E1: JsonPath.set("$.a[0]") must replace element 0, not append
	public void testSetArrayIndexReplaces() throws Exception {
		JsonObject doc = JsonObject.parse("{\"a\":[1,2,3]}");
		Object result = JsonPath.parse(doc, MONFLABS).set("$.a[0]", "x").json();
		assertSame(doc, result);

		JsonArray a = (JsonArray)doc.get("a");
		assertEquals(3, a.size());
		assertEquals("x", a.get(0));
		assertEquals(2, intAt(a, 1));
		assertEquals(3, intAt(a, 2));

		// Last element as well, and a second set on the same index does not grow the array
		JsonPath.parse(doc, MONFLABS).set("$.a[2]", "z").set("$.a[2]", "zz");
		assertEquals(3, a.size());
		assertEquals("zz", a.get(2));
	}

	// E1: index == size appends (same contract as AbstractJsonProvider.setArrayIndex)
	public void testSetArrayIndexAppendsAtSize() throws Exception {
		MonfLabsJsonProvider p = new MonfLabsJsonProvider();
		JsonArray a = JsonArray.parse("[1,2]");
		p.setArrayIndex(a, 2, "x");
		assertEquals(3, a.size());
		assertEquals("x", a.get(2));
		assertThrows(Exception.class, () -> p.setArrayIndex(a, 10, "y"));
	}

	// E1: map() goes through getArrayIndex/setArrayIndex and must keep the array's size
	public void testMapArrayElementsInPlace() throws Exception {
		JsonObject doc = JsonObject.parse("{\"a\":[1,2,3]}");
		JsonPath.parse(doc, MONFLABS).map("$.a[*]", (v, conf) -> ((Number)v).intValue() * 10);
		JsonArray a = (JsonArray)doc.get("a");
		assertEquals(3, a.size());
		assertEquals(10, intAt(a, 0));
		assertEquals(20, intAt(a, 1));
		assertEquals(30, intAt(a, 2));
	}

	// E2: setProperty on an array replaces the element instead of inserting one
	public void testSetPropertyOnArrayReplaces() throws Exception {
		MonfLabsJsonProvider p = new MonfLabsJsonProvider();
		JsonArray a = JsonArray.parse("[1,2,3]");

		p.setProperty(a, 1, "x");
		assertEquals(3, a.size());
		assertEquals("x", a.get(1));
		assertEquals(3, intAt(a, 2));

		p.setProperty(a, "0", "y");             // string keys are parsed as indexes
		assertEquals(3, a.size());
		assertEquals("y", a.get(0));

		p.setProperty(a, 3, "end");             // index == size appends
		assertEquals(4, a.size());
		assertEquals("end", a.get(3));

		p.setProperty(a, null, "tail");         // null key appends
		assertEquals(5, a.size());
		assertEquals("tail", a.get(4));
	}

	// E3: an explicit JSON null is a present property
	public void testExplicitNullIsNotUndefined() throws Exception {
		JsonObject doc = JsonObject.parse("{\"a\":null,\"b\":1}");

		assertNull(JsonPath.parse(doc, MONFLABS).read("$.a"));
		// a genuinely missing property still reports PathNotFound
		assertThrows(PathNotFoundException.class, () -> JsonPath.parse(doc, MONFLABS).read("$.c"));

		MonfLabsJsonProvider p = new MonfLabsJsonProvider();
		assertNull(p.getMapValue(doc, "a"));
		assertSame(MonfLabsJsonProvider.UNDEFINED, p.getMapValue(doc, "c"));
	}

	// E3: filters comparing against null behave like the reference provider
	public void testNullFilters() throws Exception {
		String json = "{\"items\":[{\"a\":null},{\"a\":1},{},{\"a\":\"s\"}]}";
		assertSameCount(json, "$.items[?(@.a == null)]", 1);
		assertSameCount(json, "$.items[?(@.a != null)]", 3);  // Jayway: a missing property is also != null
		assertSameCount(json, "$.items[?(@.a)]", 3);   // exists: present-but-null counts
		assertSameCount(json, "$.items[?(!@.a)]", 1);
	}

	private void assertSameCount(String json, String path, int expected) {
		List<?> ours = JsonPath.parse(JsonObject.parse(json), MONFLABS).read(path);
		List<?> ref = JsonPath.parse(json, JSONSMART).read(path);
		assertEquals(path + " (reference provider)", expected, ref.size());
		assertEquals(path, ref.size(), ours.size());
	}

	// E4: toJson must produce JSON text for every value, including strings and null
	public void testToJson() throws Exception {
		MonfLabsJsonProvider p = new MonfLabsJsonProvider();
		assertEquals("\"abc\"", p.toJson("abc"));
		assertEquals("\"a\\\"b\"", p.toJson("a\"b"));
		assertEquals("null", p.toJson(null));
		assertEquals("1", p.toJson(1));
		assertEquals("true", p.toJson(true));

		JsonObject doc = JsonObject.parse("{\"a\":[1,\"x\",null]}");
		String s = p.toJson(doc);
		assertEquals(doc, p.parse(s));
		// through Jayway
		assertEquals(s, JsonPath.parse(doc, MONFLABS).jsonString());
	}
}
