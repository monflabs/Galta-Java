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

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class FloatTest extends ProjectTestCase {

	public static String[] TRUE_NUMBERS = new String[] { "1.0", "123.456", "1.0E1", "123.456E12", "1.0E+1",
			"123.456E+12", "1.0E-1", "123.456E-12", "1.0e1", "123.456e12", "1.0e+1", "123.456e+12", "1.0e-1",
			"123.456e-12" };

	public static String[] FALSE_NUMBERS = new String[] { "1.0%", "123.45.6", "1.0E", "++123.456E12", "+-01",
			"1.0E+1.2" };

	public void testFloat() throws Exception {
		for (String s : TRUE_NUMBERS) {
			String json = "{v:" + s + "}";
			Double val = Double.valueOf(s.trim());
			JsonObject obj = (JsonObject)JsonFactory.get().parse(json);
			Object value = obj.get("v");
			assertEquals("Should be parse as double", val, value);
		}
	}

	public void testNonFloat() throws Exception {
		for (String s : FALSE_NUMBERS) {
			String json = "{v:" + s + "}";
			try {
				JsonFactory.get().parse(json);
				fail();
			} catch(Exception e) {
				// Expected
			}
		}
	}

	public void testUUID() {
		String UUID = "58860611416142319131902418361e88";
		JsonObject obj = JsonFactory.get().createObject();
		obj.put("uuid", UUID);
		String compressed = JsonFactory.get().stringify(obj);
		assertTrue(compressed.contains("uuid\":\""));
	}
}
