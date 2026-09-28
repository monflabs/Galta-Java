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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class CloneTest extends ProjectTestCase {

	public void testCloneString() throws Exception {
		String v = "abc";
		String cv = (String)JsonFactory.get().deepClone(v);
		assertEquals(v, cv);
		assertSame(v, cv);
	}
	public void testCloneNumber() throws Exception {
		Integer v = 1234;
		Integer cv = (Integer)JsonFactory.get().deepClone(v);
		assertEquals(v, cv);
		assertSame(v, cv);
	}
	public void testCloneBoolean() throws Exception {
		Boolean v = true;
		Boolean cv = (Boolean)JsonFactory.get().deepClone(v);
		assertEquals(v, cv);
		assertSame(v, cv);
	}
	public void testCloneObject() throws Exception {
		JsonObject v = JsonFactory.get().of("a",1,"b",2,"c",3,"d",4);
		JsonObject cv = (JsonObject)JsonFactory.get().deepClone(v);
		assertEquals(v, cv);
		assertNotSame(v, cv);

		JsonObject cv2 = v.deepClone();
		assertEquals(v, cv2);
		assertNotSame(v, cv2);
	}
	public void testCloneArray() throws Exception {
		JsonArray v = JsonFactory.get().arrayOf(1,2,3);
		JsonArray cv = (JsonArray)JsonFactory.get().deepClone(v);
		assertEquals(v, cv);
		assertNotSame(v, cv);

		JsonArray cv2 = v.deepClone();
		assertEquals(v, cv2);
		assertNotSame(v, cv2);
	}

	public void testCloneComplex() throws Exception {
		String s = "{a: 1, b: { b1:3, b2: [4]}, c: [{c1: 3}, {c2: 4}] }";
		Object v = JsonFactory.get().parse(s);
		Object cv = JsonFactory.get().deepClone(v);
		assertEquals(v, cv);
	}
}
