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
package tests.json.jsonvalues.array;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class PeekTest extends ProjectTestCase {

	private static final String JSON = 
"""
{
  "o1": [10, 11, 12, 11, 13]
}
""";	

	JsonValues json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonValues.parse(JSON);
	}

	public void testPeek() throws Exception {
		// Return a raw value (Integer)
		JsonArray res = JsonArray.create();
		JsonValues a1 = json.getAndFlat("o1");
		JsonValues a = a1.peek( v -> res.add(v.intValue() * 2) );
		assertEquals(5,a._size());
		assertSame(a,a1);
		assertEquals(JsonArray.of(10,11,12,11,13),a.toJsonArray());
		assertEquals(5,res.size());
		assertEquals(JsonArray.of(20,22,24,22,26),res);
	}
}
