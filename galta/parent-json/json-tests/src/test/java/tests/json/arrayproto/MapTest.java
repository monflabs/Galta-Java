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
package tests.json.arrayproto;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class MapTest extends ProjectTestCase {

	private static final String JSON = 
"""
{
  "o1": [10, 11, 12, 11, 13],
  "o2": [
    [23],
    [15],
    [7]
  ]
}
""";	
	

	JsonObject json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonObject.parse(JSON);
	}
	
	public void testMap() throws Exception {
		// Return a raw value (Integer)
		JsonArray p1 = json.getArray("o1").map( v -> JsonValues.of(v).intValue() * 2 );
		assertEquals(5,p1.size());
		assertArrayEquals(new Object[]{20,22,24,22,26},p1.toArray());

		// Return a JsonValue
		JsonArray p2 = json.getArray("o2").map( v -> JsonValues.of(v).get(0).intValue() );
		assertEquals(3,p2.size());
		assertArrayEquals(new Object[]{23,15,7},p2.toArray());
	}
}
