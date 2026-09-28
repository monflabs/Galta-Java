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

public class BigValueTest extends ProjectTestCase {

	String bigStr = "12345678901234567890123456789";

	/**
	 * test BigDecimal serialization
	 */
	public void testBigDecimal() {
		JsonObject map = JsonFactory.get().createObject();
		BigDecimal bigDec = new BigDecimal(bigStr + "." + bigStr);
		map.put("big", bigDec);
		String test = JsonFactory.get().stringify(map);
		String result = "{\"big\":" + bigStr + "." +bigStr + "}";
		assertEquals(result, test);
		JsonObject obj = (JsonObject)JsonFactory.get().parse(test);
		assertEquals(bigDec, obj.get("big"));
		assertEquals(bigDec.getClass(), obj.get("big").getClass());
	}

	/**
	 * test BigInteger serialization
	 */
	public void testBigInteger() {
		JsonObject map = JsonFactory.get().createObject();
		BigInteger bigInt = new BigInteger(bigStr);
		map.put("big", bigInt);
		String test = JsonFactory.get().stringify(map);
		String result = "{\"big\":" + bigStr + "}";
		assertEquals(result, test);
		JsonObject obj = (JsonObject)JsonFactory.get().parse(test);
		assertEquals(bigInt, obj.get("big"));
		assertEquals(bigInt.getClass(), obj.get("big").getClass());
	}
	

	public void testMinBig() {
		BigInteger v = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);
		String s = "[" + v + "]";
		JsonArray array = (JsonArray)JsonFactory.get().parse(s);
		Object r = array.get(0);
		assertEquals(v, r);
	}

	public void testMaxBig() {
		BigInteger v = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
		String s = "[" + v + "]";
		JsonArray array = (JsonArray)JsonFactory.get().parse(s);
		Object r = array.get(0);
		assertEquals(v, r);
	}

}
