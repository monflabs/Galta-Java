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
package tests.yaml;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.yaml.SnakeYaml;

import tests.ProjectTestCase;

/**
 * YAML limits, rejected values and round trips.
 */
public class SnakeYamlSafetyTest extends ProjectTestCase {

	// 25 levels of 2 aliases: 2^25 leaves once expanded
	private static String laughs(int levels) {
		StringBuilder b = new StringBuilder("a0: &a0 [x, y]\n");
		for(int i=1; i<=levels; i++) {
			b.append("a").append(i).append(": &a").append(i).append(" [*a").append(i-1).append(", *a").append(i-1).append("]\n");
		}
		return b.toString();
	}

	public void testAliasAmplificationIsRejected() {
		// Used to load, and to explode when copied or stringified
		assertThrows(JsonException.class, () -> SnakeYaml.parse(laughs(24)));
		// A small expansion is fine
		JsonObject o = (JsonObject)SnakeYaml.parse(laughs(4));
		assertEquals(5, o.size());
		// The budget is configurable
		SnakeYaml.Options small = new SnakeYaml.Options().setMaxExpandedSize(20);
		assertThrows(JsonException.class, () -> SnakeYaml.parse(JsonFactory.get(), laughs(4), small));
		SnakeYaml.Options big = new SnakeYaml.Options().setMaxExpandedSize(100_000_000L);
		assertNotNull(SnakeYaml.parse(JsonFactory.get(), laughs(20), big));
	}

	public void testCodePointLimit() {
		String yaml = "a: " + "x".repeat(2000) + "\n";
		assertNotNull(SnakeYaml.parse(yaml));
		SnakeYaml.Options small = new SnakeYaml.Options().setCodePointLimit(1000);
		assertThrows(JsonException.class, () -> SnakeYaml.parse(JsonFactory.get(), yaml, small));
	}

	public void testKeysCollidingAsStrings() {
		// 1 and "1" used to overwrite each other silently
		assertThrows(JsonException.class, () -> SnakeYaml.parse("1: a\n\"1\": b\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("true: a\n\"true\": b\n"));
		// A real duplicate is rejected too (by the parser)
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: 1\na: 2\n"));
		assertEquals("{\"1\":\"a\",\"2\":\"b\"}", ((JsonObject)SnakeYaml.parse("1: a\n\"2\": b\n")).stringify());
	}

	public void testNonJsonValuesAreJsonExceptions() {
		// These used to throw raw snakeyaml exceptions
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: !custom x\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: !!omap [b: 1]\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: !!pairs [b: 1]\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("? [a, b]\n: c\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("? {a: b}\n: c\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: [1, 2\n"));
		assertThrows(JsonException.class, () -> SnakeYaml.parse("a: b: c\n"));
	}

	public void testAmbiguousStringsAreQuoted() {
		String[] ambiguous = {"yes", "No", "on", "OFF", "~", "null", "0x1F", "0o17", "017", "2001-12-14",
				"2001-12-14 21:59:43.10 -5", "1_000", "12:30:00", "1:20", ".5", "1e3", ".inf", ".NaN", "<<", "true", "12"};
		JsonArray a = JsonArray.create();
		for(String s: ambiguous) {
			a.add(s);
		}
		String yaml = SnakeYaml.stringify(JsonObject.of("v", a, "yes", "plain text"));
		for(String s: ambiguous) {
			assertFalse(s+" not quoted in\n"+yaml, yaml.contains("- "+s+"\n"));
		}
		assertTrue(yaml, yaml.contains("'yes': plain text"));
		JsonObject back = (JsonObject)SnakeYaml.parse(yaml);
		assertEquals(a, back.get("v"));
	}

	public void testNaNIsNotStringified() {
		// .nan was emitted, which parse() rejects
		assertThrows(JsonException.class, () -> SnakeYaml.stringify(JsonObject.of("a", Double.NaN)));
		assertThrows(JsonException.class, () -> SnakeYaml.stringify(JsonArray.of(Double.POSITIVE_INFINITY)));
		assertThrows(JsonException.class, () -> SnakeYaml.stringify(JsonArray.of(Float.NEGATIVE_INFINITY)));
	}

	public void testExactDecimals() {
		// A BigDecimal used to come back as a (rounded) Double
		BigDecimal precise = new BigDecimal("0.12345678901234567890123");
		JsonObject o = JsonObject.of("d", precise, "f", 1.5, "w", new BigDecimal("5"), "e", new BigDecimal("1.5E-30"));
		JsonObject back = (JsonObject)SnakeYaml.parse(SnakeYaml.stringify(o));
		assertEquals(precise, back.get("d"));
		assertEquals(1.5, back.get("f"));
		assertEquals(0, new BigDecimal("5").compareTo(new BigDecimal(back.get("w").toString())));
		assertEquals(0, new BigDecimal("1.5E-30").compareTo(new BigDecimal(back.get("e").toString())));
		// In a document
		assertEquals(new BigDecimal("3.14159265358979323846264338327950288"), ((JsonObject)SnakeYaml.parse("pi: 3.14159265358979323846264338327950288\n")).get("pi"));
		assertEquals(0.1, ((JsonObject)SnakeYaml.parse("x: 0.1\n")).get("x"));
	}
}
