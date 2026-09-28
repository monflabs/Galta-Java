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
package tests.json.factory;

import static org.junit.Assert.assertThrows;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.parser.JsonParser;
import org.monflabs.json.stringifier.JsonStringifier;

import tests.ProjectTestCase;

/**
 * JsonStringifier options and edge cases.
 */
public class StringifierOptionsTest extends ProjectTestCase {

	private static JsonStringifier.StringSerializer compact() {
		return new JsonStringifier.StringSerializer();
	}

	public void testReuseAfterCircularReference() throws Exception {
		JsonStringifier.StringSerializer s = compact();
		JsonArray cyclic = JsonArray.create();
		cyclic.add(cyclic);
		assertThrows(JsonException.CircularReference.class, () -> s.stringify(cyclic));
		// Not a false cycle for the next (non circular) value, even the same containers
		JsonObject shared = JsonObject.of("a", 1);
		JsonArray twice = JsonArray.of(shared, shared);
		assertEquals("[{\"a\":1},{\"a\":1}]", s.stringify(twice));
		JsonObject o = JsonObject.of("x", JsonArray.of(1));
		JsonObject outer = JsonObject.of("o", o);
		o.put("self", outer);
		assertThrows(JsonException.CircularReference.class, () -> s.stringify(outer));
		o.remove("self");
		assertEquals("{\"o\":{\"x\":[1]}}", s.stringify(outer));
	}

	public void testNaNAndInfinityAreWrittenAsNull() throws Exception {
		JsonArray a = JsonArray.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY, 1.5);
		String json = compact().stringify(a);
		assertEquals("[null,null,null,null,null,1.5]", json);
		// The result is valid strict JSON
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setStrict(true);
		assertEquals(6, ((JsonArray)p.parse(json)).size());
	}

	public void testNonJsonValues() throws Exception {
		JsonObject o = JsonObject.create();
		if(o.factory().getClass().getSimpleName().contains("Checked")) {
			return; // the checked factory refuses them
		}
		o.putValue("date", new Date(0));
		o.putValue("sb", new StringBuilder("abc"));
		o.putValue("day", java.time.LocalDate.of(2020, 1, 2));
		// A Date is written as an ISO instant, whatever the locale/time zone
		assertEquals("{\"date\":\"1970-01-01T00:00:00Z\",\"sb\":\"abc\",\"day\":\"2020-01-02\"}", compact().stringify(o));
	}

	public void testReplacerAndPropertyList() throws Exception {
		JsonObject o = JsonObject.parse("{\"a\":1,\"b\":2,\"c\":{\"a\":3,\"d\":4}}");
		JsonStringifier.StringSerializer s = compact();
		List<String> seen = new ArrayList<>();
		s.setReplacer((container,key,value) -> {
			seen.add(key);
			if(key.equals("b")) {
				return JsonStringifier.Replacer.IGNORE;
			}
			if(value instanceof Integer i) {
				return i*10;
			}
			return value;
		});
		assertEquals("{\"a\":10,\"c\":{\"a\":30,\"d\":40}}", s.stringify(o));
		assertEquals(List.of("","a","b","c","a","d"), seen);
		// The property list selects and orders the members of every object
		JsonStringifier.StringSerializer p = compact();
		p.setPropertyList(List.of("c","a","zz"));
		assertEquals("{\"c\":{\"a\":3},\"a\":1}", p.stringify(o));
		// The root container is given to the first replacer call
		JsonStringifier.StringSerializer r = compact();
		Object root = new Object();
		List<Object> containers = new ArrayList<>();
		r.setRootContainer(root);
		assertSame(root, r.getRootContainer());
		r.setReplacer((container,key,value) -> { containers.add(container); return value; });
		r.stringify(JsonArray.of(1));
		assertSame(root, containers.get(0));
	}

	public void testRawJson() throws Exception {
		JsonStringifier.StringSerializer s = compact();
		s.setReplacer((container,key,value) -> key.equals("raw") ? (JsonStringifier.ReplacerRawJSON)() -> "[1,2]" : value);
		assertEquals("{\"raw\":[1,2],\"b\":true}", s.stringify(JsonObject.parse("{\"raw\":0,\"b\":true}")));
	}

	public void testNullsSortAndIndent() throws Exception {
		JsonObject o = JsonObject.parse("{\"b\":null,\"a\":[1,{\"c\":2}]}");
		JsonStringifier.StringSerializer s = compact();
		s.setSerializeNulls(false);
		assertFalse(s.isSerializeNulls());
		assertEquals("{\"a\":[1,{\"c\":2}]}", s.stringify(o));
		s.setSerializeNulls(true);
		s.setSortProperties(true);
		assertEquals("{\"a\":[1,{\"c\":2}],\"b\":null}", s.stringify(o));
		s.setSortProperties(false);
		s.setCompact(false);
		s.setIndentString("\t");
		assertEquals("\t", s.getIndentString());
		String pretty = s.stringify(JsonObject.of("a", JsonArray.of(1)));
		assertTrue(pretty, pretty.contains("\n\t\"a\""));
		assertEquals(JsonObject.of("a", JsonArray.of(1)), JsonObject.parse(pretty));
		s.setInitialIndentLevel(2);
		assertEquals(2, s.getInitialIndentLevel());
		assertTrue(s.stringify(JsonObject.of("a", 1)).startsWith("\t\t{"));
	}

	public void testStringEscaping() throws Exception {
		JsonStringifier.StringSerializer s = compact();
		// A complete surrogate pair is kept, a lone surrogate is escaped
		assertEquals("\"\uD83D\uDE00\"", s.stringify("\uD83D\uDE00"));
		assertEquals("\"\\ud83d\"", s.stringify("\uD83D").toLowerCase());
		assertEquals("\"\\u0000\\u001f\\b\\f\\n\\r\\t\\\"\\\\/\"", s.stringify("\u0000\u001f\b\f\n\r\t\"\\/").replace("\\u001F","\\u001f"));
	}

	public void testWriterAndLimited() throws Exception {
		StringWriter w = new StringWriter();
		new JsonStringifier.WriterSerializer().stringify(w, JsonArray.of(1, "a"));
		assertEquals("[1,\"a\"]", w.toString());
		JsonStringifier.LimitedStringSerializer l = new JsonStringifier.LimitedStringSerializer(5);
		assertEquals("[1,\"a", l.stringify(JsonArray.of(1, "abc")));
		assertTrue(l.isTruncated());
		assertEquals("[1]", l.stringify(JsonArray.of(1)));
		assertFalse(l.isTruncated());
	}
}
