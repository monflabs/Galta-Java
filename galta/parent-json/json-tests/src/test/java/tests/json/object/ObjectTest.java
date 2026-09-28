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
package tests.json.object;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class ObjectTest extends ProjectTestCase {

	public void testType() {
		JsonObject o = JsonFactory.get().createObject();
		assertTrue(o.isObject());
		assertFalse(o.isArray());
	}

	public void testGet() {
		JsonObject o = JsonFactory.get().of("a","AA", "b", "BB" , "c","CC");
		
		assertNull(o.get((Object)null));
		assertNull(o.get("z"));
		assertEquals("AA",o.get("a"));
		assertEquals(JsonArray.of("AA","CC"),o.getValues("a","c"));
	}

	public void testContains() {
		JsonObject o = JsonFactory.get().of("a","AA", "b",5, "c",JsonFactory.get().arrayOf("a","c"), "d",JsonFactory.get().of("a","A"));

		assertTrue(o.containsKey("a"));
		assertTrue(o.containsKey("b"));
		assertTrue(o.containsKey("c"));
		assertTrue(o.containsKey("d"));
		assertFalse(o.containsKey("z"));

		assertTrue(o.containsValue("AA"));
		assertTrue(o.containsValue(5));
		assertTrue(o.containsValue(JsonFactory.get().arrayOf("a","c")));
		assertTrue(o.containsValue(JsonFactory.get().of("a","A")));
		assertFalse(o.containsValue(2));

		Collection<Object> values = o.values();
		assertEquals(4, values.size());
		assertTrue(values.contains("AA"));
		assertTrue(values.contains(5));
		assertTrue(values.contains(JsonFactory.get().arrayOf("a","c")));
		assertTrue(values.contains(JsonFactory.get().of("a","A")));
		assertFalse(values.contains(2));

		Set<String> keys = o.keySet();
		assertEquals(4, keys.size());
		assertTrue(keys.contains("a"));
		assertTrue(keys.contains("b"));
		assertTrue(keys.contains("c"));
		assertTrue(keys.contains("d"));
		assertFalse(keys.contains("z"));

		Set<Map.Entry<String,Object>> entries = o.entrySet();
		assertEquals(4, entries.size());
		int ie=0;
		for(Map.Entry<String,Object> e: entries) {
			if(e.getKey().equals("a")) {
				assertEquals("AA",e.getValue());
			} else if(e.getKey().equals("b")) {
				assertEquals(5,e.getValue());
			} else if(e.getKey().equals("c")) {
				assertEquals(JsonFactory.get().arrayOf("a","c"),e.getValue());
			} else if(e.getKey().equals("d")) {
				assertEquals(JsonFactory.get().of("a","A"),e.getValue());
			} else {
				fail();
			}
			ie++;
		}
		assertEquals(4, ie);
	}
	
	
	public void testPut() {
		JsonObject o = JsonFactory.get().createObject();

		o.putNull("zz");
		assertEquals(null,o.get("zz"));

		o.put("a", false);
		assertEquals(Boolean.FALSE,o.get("a"));
		
		o.put("c", (byte)123);
		assertEquals(Byte.valueOf((byte)123),o.get("c"));
		
		o.put("d", (short)123);
		assertEquals(Short.valueOf((short)123),o.get("d"));
		
		o.put( "e", 123);
		assertEquals(Integer.valueOf(123),o.get("e"));
		
		o.put("f", 123L);
		assertEquals(Long.valueOf(123L),o.get("f"));
		
		o.put("g", 123.45f);
		assertEquals(Float.valueOf(123.45f),o.get("g"));
		
		o.put("h", 123.45);
		assertEquals(Double.valueOf(123.45),o.get("h"));

		o.put("c", Byte.valueOf((byte)123) );
		assertEquals(Byte.valueOf((byte)123),o.get("c"));
		
		o.put("d", Short.valueOf((short)123));
		assertEquals(Short.valueOf((short)123),o.get("d"));
		
		o.put( "e", Integer.valueOf(123));
		assertEquals(Integer.valueOf(123),o.get("e"));
		
		o.put("f", Long.valueOf(123L));
		assertEquals(Long.valueOf(123L),o.get("f"));
		
		o.put("g", Float.valueOf(123.45f));
		assertEquals(Float.valueOf(123.45f),o.get("g"));
		
		o.put("h", Double.valueOf(123.45));
		assertEquals(Double.valueOf(123.45),o.get("h"));
		
		o.put("i", new BigInteger("234"));
		assertEquals(new BigInteger("234"),o.get("i"));
		
		o.put("j", new BigDecimal("234.56"));
		assertEquals(new BigDecimal("234.56"),o.get("j"));
		
		o.put("k", "xyz");
		assertEquals("xyz",o.get("k"));
		
		o.put( "l", LocalDate.of(2020, 2, 20));
		assertEquals("2020-02-20",o.get("l"));
		
		o.put( "m", LocalTime.of(13, 44, 18));
		assertEquals("13:44:18",o.get("m"));
		
		o.put( "n", LocalDateTime.of(2020, 2, 20, 13, 44, 18));
		assertEquals("2020-02-20T13:44:18",o.get("n"));
		
		o.put( "o", OffsetTime.of(13, 44, 18, 0, ZoneOffset.ofHours(2)));
		assertEquals("13:44:18+02:00",o.get("o"));
		
		o.put( "p", OffsetDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneOffset.ofHours(2)));
		assertEquals("2020-02-20T13:44:18+02:00",o.get("p"));
		
		o.put( "p", ZonedDateTime.of(2020, 2, 20, 13, 44, 18, 0, ZoneId.of("US/Eastern")));
		assertEquals("2020-02-20T13:44:18-05:00[US/Eastern]",o.get("p"));

		o.put( "q", JsonFactory.get().of("a","AA"));
		assertEquals(JsonFactory.get().of("a","AA"),o.get("q"));
		o.put( "r", (JsonObject r) -> {
			r.put("b", "BB");
		});
		assertEquals(JsonFactory.get().of("b","BB"),o.get("r"));
		
		o.put( "t", JsonFactory.get().arrayOf("a","b"));
		assertEquals(JsonFactory.get().arrayOf("a","b"),o.get("t"));
		o.put( "u", (JsonArray r) -> {
			r.add("c").add("d");
		});
		assertEquals(JsonFactory.get().arrayOf("c","d"),o.get("u"));

		
		assertEquals("xyz",o.getString("k","abc"));
		assertEquals("abc",o.getString("kz","abc"));
		
		assertTrue(o.has("a"));
		assertTrue(o.has("c"));
		assertTrue(o.has("d"));
		assertTrue(o.has("e"));
		assertTrue(o.has("f"));
		assertTrue(o.has("g"));
		assertTrue(o.has("h"));
		assertTrue(o.has("i"));
		assertTrue(o.has("j"));
		assertTrue(o.has("k"));
		assertFalse(o.has("aa"));
		
		Set<String> all = o.keySet();
		assertEquals(20,all.size());
		assertTrue(all.contains("a"));
		assertTrue(!all.contains("aa"));
		
		o.remove("b");
		all = o.keySet();
		assertEquals(20,all.size());
		assertTrue(all.contains("a"));
		assertFalse(all.contains("b"));
		assertTrue(all.contains("c"));

		o.putValue( "d", o.get("k"));
		assertTrue(o.has("d"));
		assertEquals("xyz",o.getString("d"));

		JsonObject oo1n = o.getOrCreateObject("an");
		assertEquals(0,oo1n.size());
		JsonObject oo1 = o.getOrCreateObject("aa", null);
		assertEquals(0,oo1.size());
		oo1.put("one",1);
		
		JsonObject oo2n = o.getOrCreateObject("aa");
		assertEquals(1,oo2n.size());
		JsonObject oo2 = o.getOrCreateObject("aa", null);
		assertEquals(1,oo2.size());
		JsonObject oo3 = o.getOrCreateObject("bb", (JsonObject p) -> {
			p.put("a", 1)
			 .put("b", 2)
			 .put("c", 3);
		});
		assertEquals(3,oo3.size());
		
		JsonArray oa1n = o.getOrCreateArray("aa2n");
		assertEquals(0,oa1n.size());
		JsonArray oa1 = o.getOrCreateArray("aa2", null);
		assertEquals(0,oa1.size());
		oa1.add(1);
		JsonArray oa2n = o.getOrCreateArray("aa2");
		assertEquals(1,oa2n.size());
		JsonArray oa2 = o.getOrCreateArray("aa2", null);
		assertEquals(1,oa2.size());
		JsonArray oa3 = o.getOrCreateArray("bb2", (JsonArray p) -> {
			p.add(1)
			 .add(2)
			 .add(3);
		});
		assertEquals(3,oa3.size());


		o.clear();
		all = o.keySet();
		assertEquals(0,all.size());
	}
	
	public void testPutNull() {
		JsonObject o = JsonFactory.get().createObject();

		o.putNull("a");
		o.put("b", (Boolean)null );
		o.put("c", (Byte)null );
		o.put("d", (Short)null );
		o.put("e", (Integer)null );
		o.put("f", (Long)null );
		o.put("g", (Float)null );
		o.put("h", (Double)null );
		o.put("i", (BigInteger)null );
		o.put("j", (BigDecimal)null );
		o.put("k", (String)null );
		o.put("l", (LocalDate)null );
		o.put("m", (LocalTime)null );
		o.put("n", (LocalDateTime)null );
		o.put("o", (OffsetTime)null );
		o.put("p", (OffsetDateTime)null );
		o.put("p", (ZonedDateTime)null );
		o.put("u", (JsonObject)null );
		o.put("u", (JsonArray)null );
		for(String k: o.keySet()) {
			assertTrue(o.isNull(k));
		}

	}
	
	public void testAsMethods() {
		JsonObject o = JsonFactory.get().of(
			"a", 1,
			"b", 4L,
			"c", 56.6,
			"d", new BigInteger("45"),
			"e", new BigDecimal("45.78"),
			"f", true,
			"g", "xyz"
		);
		
		assertEquals(1,o.asInt("a"));
		assertEquals(4,o.asInt("b"));
		assertEquals(45,o.asInt("d"));
		assertEquals(0,o.asInt("g"));
		assertEquals(77,o.asInt("g",77));
		
		assertEquals(1L,o.asLong("a"));
		assertEquals(4L,o.asLong("b"));
		assertEquals(0L,o.asLong("g"));
		assertEquals(77L,o.asLong("g",77L));
		
		assertEquals(1.0,o.asDouble("a"));
		assertEquals(56.6,o.asDouble("c",44.6));
		assertEquals(45.78,o.asDouble("e"));
		assertEquals(0.0,o.asDouble("g"));
		assertEquals(77.36,o.asDouble("g",77.36));
		
		assertEquals(new BigInteger("1"),o.asBigInteger("a"));
		assertEquals(new BigInteger("4"),o.asBigInteger("b"));
		assertEquals(new BigInteger("45"),o.asBigInteger("d"));
		assertEquals(new BigInteger("0"),o.asBigInteger("g"));
		assertEquals(new BigInteger("77"),o.asBigInteger("g",new BigInteger("77")));

		assertEquals(new BigDecimal("1"),o.asBigDecimal("a"));
		assertEquals(new BigDecimal("56.6"),o.asBigDecimal("c",new BigDecimal("44.6")));
		assertEquals(new BigDecimal("45.78"),o.asBigDecimal("e"));
		assertEquals(new BigDecimal("0"),o.asBigDecimal("g"));
		assertEquals(new BigDecimal("77.36"),o.asBigDecimal("g",new BigDecimal("77.36")));
	}
}
