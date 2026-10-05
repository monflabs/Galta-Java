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

public class KeywordTest extends ProjectTestCase {

	public void testBool() throws Exception {
		String s = "{t:true}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), true);

		s = "{t:false}";
		o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), false);
	}

	public void testNull() throws Exception {
		String s = "{t:null}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertNull(o.get("t"));
	}

	public void testNaN() throws Exception {
		String s = "{t:NaN}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), Double.NaN);
	}

	public void testInfinity() throws Exception {
		String s = "{t:Infinity}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), Double.POSITIVE_INFINITY);
	}

	public void testInfinityP() throws Exception {
		String s = "{t:+Infinity}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), Double.POSITIVE_INFINITY);
	}

	public void testInfinityN() throws Exception {
		String s = "{t:-Infinity}";
		JsonObject o = (JsonObject)JsonFactory.get().parse(s);
		assertEquals(o.get("t"), Double.NEGATIVE_INFINITY);
	}
}
