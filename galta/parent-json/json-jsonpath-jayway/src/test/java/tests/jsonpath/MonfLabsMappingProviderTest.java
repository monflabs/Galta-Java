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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JaywayJsonPath;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpath.MonfLabsJsonPathConfiguration;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingException;

import tests.ProjectTestCase;

/**
 * Typed reads through the Galta mapping provider.
 */
public class MonfLabsMappingProviderTest extends ProjectTestCase {

	private static final Configuration CONF = MonfLabsJsonPathConfiguration.configuration();
	
	private static final String JSON = "{\"i\":1,\"big\":5000000000,\"d\":1.5,\"whole\":2.0,\"s\":\"12\",\"b\":true,"
			+ "\"o\":{\"z\":1,\"a\":2,\"m\":3},\"arr\":[3,1,2]}";

	private static <T> T read(String path, Class<T> type) {
		return JsonPath.parse(JsonObject.parse(JSON), CONF).read(path, type);
	}
	
	public void testNumericConversions() {
		assertEquals(Integer.valueOf(1), read("$.i", Integer.class));
		assertEquals(Long.valueOf(1), read("$.i", Long.class));
		assertEquals(Double.valueOf(1), read("$.i", Double.class));
		assertEquals(Float.valueOf(1), read("$.i", Float.class));
		assertEquals(Short.valueOf((short)1), read("$.i", Short.class));
		assertEquals(Byte.valueOf((byte)1), read("$.i", Byte.class));
		assertEquals(BigInteger.ONE, read("$.i", BigInteger.class));
		assertEquals(0, BigDecimal.ONE.compareTo(read("$.i", BigDecimal.class)));
		assertEquals(Long.valueOf(5000000000L), read("$.big", Long.class));
		assertEquals(Double.valueOf(1.5), read("$.d", Double.class));
		// A whole double converts exactly
		assertEquals(Integer.valueOf(2), read("$.whole", Integer.class));
		assertEquals(Integer.valueOf(2), read("$.whole", int.class));
		// A numeric string converts to a number
		assertEquals(Integer.valueOf(12), read("$.s", Integer.class));
	}
	
	public void testLossyConversionsFail() {
		// Fraction lost, or out of range: never a silent truncation
		assertThrows(MappingException.class, () -> read("$.d", Integer.class));
		assertThrows(MappingException.class, () -> read("$.d", Long.class));
		assertThrows(MappingException.class, () -> read("$.big", Integer.class));
		assertThrows(MappingException.class, () -> read("$.i", Boolean.class));
		assertThrows(MappingException.class, () -> read("$.o", Integer.class));
		assertThrows(MappingException.class, () -> read("$.arr", Map.class));
	}
	
	public void testStringAndBoolean() {
		assertEquals("1", read("$.i", String.class));
		assertEquals("1.5", read("$.d", String.class));
		assertEquals("12", read("$.s", String.class));
		assertEquals("true", read("$.b", String.class));
		assertEquals(Boolean.TRUE, read("$.b", Boolean.class));
		assertEquals(Boolean.TRUE, read("$.b", boolean.class));
	}
	
	public void testContainers() {
		// Galta containers are returned as is
		assertTrue(read("$.o", JsonObject.class) instanceof JsonObject);
		assertTrue(read("$.arr", JsonArray.class) instanceof JsonArray);
		// Map keeps the JSON key order
		Map<?,?> m = read("$.o", Map.class);
		assertEquals(List.of("z","a","m"), new ArrayList<>(m.keySet()));
		List<?> l = read("$.arr", List.class);
		assertEquals(3, l.size());
		assertEquals(3, ((Number)l.get(0)).intValue());
		// Object gives Java collections
		assertTrue(read("$.o", Object.class) instanceof Map);
	}
	
	public void testTypeRef() {
		List<Object> l = JsonPath.parse(JsonObject.parse(JSON), CONF).read("$.arr", new TypeRef<List<Object>>() {});
		assertEquals(3, l.size());
		Map<String,Object> m = JsonPath.parse(JsonObject.parse(JSON), CONF).read("$.o", new TypeRef<Map<String,Object>>() {});
		assertEquals(List.of("z","a","m"), new ArrayList<>(m.keySet()));
		Long v = JsonPath.parse(JsonObject.parse(JSON), CONF).read("$.i", new TypeRef<Long>() {});
		assertEquals(Long.valueOf(1), v);
	}
	
	public void testNull() {
		assertNull(JsonPath.parse(JsonObject.parse("{\"a\":null}"), CONF).read("$.a", Integer.class));
	}
	
	public void testJaywayJsonPathIgnoresGlobalDefaults() {
		// Even with json-smart as the JVM-wide defaults, JaywayJsonPath reads Galta containers
		Configuration.setDefaults(new Configuration.Defaults() {
			@Override public com.jayway.jsonpath.spi.json.JsonProvider jsonProvider() { return new JsonSmartJsonProvider(); }
			@Override public com.jayway.jsonpath.spi.mapper.MappingProvider mappingProvider() { return new JsonSmartMappingProvider(); }
			@Override public java.util.Set<com.jayway.jsonpath.Option> options() { return java.util.EnumSet.noneOf(com.jayway.jsonpath.Option.class); }
		});
		try {
			JaywayJsonPath p = new JaywayJsonPath(JsonPath.compile("$.o.a"));
			JsonValues v = p.read(JsonObject.parse(JSON));
			assertEquals(2, v.intValue());
			
			JaywayJsonPath all = new JaywayJsonPath(JsonPath.compile("$.arr[*]"));
			assertEquals(3, all.read(JsonObject.parse(JSON))._size());
			
			JaywayJsonPath missing = new JaywayJsonPath(JsonPath.compile("$.nope"));
			assertTrue(missing.read(JsonObject.parse(JSON)).isEmpty());
		} finally {
			MonfLabsJsonPathConfiguration.initialize();
		}
	}

	// Lossy Long/BigDecimal -> Double/Float conversions used to be accepted
	public void testFloatingConversionsAreExact() {
		JsonObject o = JsonObject.parse("{\"l\":9007199254740993,\"bd\":0.1,\"f\":0.1,\"big\":1e300}");
		o.put("l", 9007199254740993L);
		o.put("bd", new BigDecimal("0.1"));
		o.put("prec", new BigDecimal("0.12345678901234567890123"));
		com.jayway.jsonpath.DocumentContext ctx = JsonPath.parse(o, CONF);
		try {
			ctx.read("$.l", Double.class);
			fail("2^53+1 converted to a double");
		} catch(MappingException expected) {
		}
		try {
			ctx.read("$.prec", Double.class);
			fail("23 digits converted to a double");
		} catch(MappingException expected) {
		}
		try {
			ctx.read("$.big", Float.class);
			fail("1e300 converted to a float");
		} catch(MappingException expected) {
		}
		assertEquals(Double.valueOf(0.1), ctx.read("$.bd", Double.class));
		assertEquals(Float.valueOf(0.1f), ctx.read("$.f", Float.class));
		assertEquals(Double.valueOf(9007199254740992.0), JsonPath.parse(JsonObject.of("l", 9007199254740992L), CONF).read("$.l", Double.class));
	}

	// An indefinite path used to always give a LIST, even for no or one match
	public void testIndefiniteResultShapes() {
		JsonObject doc = JsonObject.parse(JSON);
		JsonValues none = new JaywayJsonPath(JsonPath.compile("$.arr[?(@ > 10)]")).read(doc);
		assertTrue(none.isEmpty());
		assertEquals(JsonValues.TYPE.EMPTY, none.getType());
		JsonValues one = new JaywayJsonPath(JsonPath.compile("$.arr[?(@ > 2)]")).read(doc);
		assertEquals(JsonValues.TYPE.VALUE, one.getType());
		assertEquals(3, one.intValue());
		JsonValues many = new JaywayJsonPath(JsonPath.compile("$.arr[*]")).read(doc);
		assertEquals(JsonValues.TYPE.LIST, many.getType());
		// The same shapes as the built-in engine
		assertEquals(JsonPathFactory.get().getJsonPath("$.arr[?(@ > 2)]").read(doc).getType(), one.getType());
	}
}
