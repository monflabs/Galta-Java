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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class NumberTest extends ProjectTestCase {

	public void testParse() throws Exception {
		assertEquals( 12, JsonFactory.get().parseInteger("12") );
		assertEquals( 18, JsonFactory.get().parseInteger("12",16) );
		
		assertEquals( Integer.MAX_VALUE, JsonFactory.get().parseInteger( Long.toString( Integer.MAX_VALUE) ));
		assertEquals( ((long)Integer.MAX_VALUE)+1L, JsonFactory.get().parseInteger( Long.toString( ((long)Integer.MAX_VALUE)+1L ) ));
		
		assertEquals( 12.4, JsonFactory.get().parseDecimal("12.4") );

		assertEquals( 12, JsonFactory.get().parseNumber("12",0) );
		assertEquals( 12.4, JsonFactory.get().parseNumber("12.4",0) );
	}

	
	public void testIntMax() throws Exception {
		String s = "{t:" + Integer.MAX_VALUE + "}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.getInt("t"), Integer.MAX_VALUE);
	}

	public void testIntMin() throws Exception {
		String s = "{t:" + Integer.MIN_VALUE + "}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.getInt("t"), Integer.MIN_VALUE);
	}

	public void testIntResult() throws Exception {
		String s = "{\"t\":1}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.getInt("t"), 1);
	}

	public void testInt() throws Exception {
		String s = "{t:90}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.getInt("t"), 90);
	}

	public void testLong() throws Exception {
		String s = "{t:123456789123456}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.getLong("t"), 123456789123456L);
	}

	public void testDouble() throws Exception {
		String s = "{t:1.2}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), 1.2);
	}

	public void testIntNeg() throws Exception {
		String s = "{t:-90}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), -90);
	}

	public void testBigInt() throws Exception {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10; i++)
			sb.append(Integer.MAX_VALUE);
		String bigText = sb.toString();
		BigInteger big = new BigInteger(bigText, 10);
		String s = "{t:" + bigText + "}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), big);
	}

	public void testBigDoubleInt() throws Exception {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10; i++)
			sb.append(Integer.MAX_VALUE);
		sb.append('.');
		for (int i = 0; i < 10; i++)
			sb.append(Integer.MAX_VALUE);

		String bigText = sb.toString();
		BigDecimal big = new BigDecimal(bigText);
		String s = "{\"t\":" + bigText + "}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), big);
		o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), big);
	}
	
	public void testMaxLong() {		
		Long v = Long.MAX_VALUE;
		String s = "[" + v + "]";
		JsonArray array = (JsonArray)JsonFactory.get().parse(s);
		Object r = array.get(0);
		assertEquals(v, r);
	}

	public void testMinLong() {
		Long v = Long.MIN_VALUE;
		String s = "[" + v + "]";
		JsonArray array = (JsonArray)JsonFactory.get().parse(s);
		Object r = array.get(0);
		assertEquals(v, r);
	}
}
