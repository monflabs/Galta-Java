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

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class DistinctTest extends ProjectTestCase {
	
	
	private static final String ARRAY_NUMBER = 
"""
[
	4,5,5,6,6.0,8
]
""";

	private static final String ARRAY_STRING = 
"""
[
	"c","b","a","b","c"
]
""";
	
	JsonArray numbers;
	JsonArray strings;

	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		numbers = JsonArray.parse(ARRAY_NUMBER);
		strings = JsonArray.parse(ARRAY_STRING);
	}


	public void testDistinct() throws Exception {
		assertEquals(JsonArray.of(4,5,6,8), numbers.distinct());
		assertEquals(JsonArray.of("c","b","a"), strings.distinct());
	}	

	public void testComp() throws Exception {
		JsonArray str = JsonArray.of("a","B","A","c","C");
		assertEquals(JsonArray.of("a","b","c"), str.distinct((a,b) -> ((String)a).compareToIgnoreCase((String)b) ).map((o) -> ((String)o).toLowerCase() ));
		assertEquals(JsonArray.of("a","b","a","c","c"), str.distinct().map((o) -> ((String)o).toLowerCase() ));
	}	

	public void testKey() throws Exception {
		JsonArray str = JsonArray.of(
			JsonObject.of("key","a","value",1),
			JsonObject.of("key","b","value",2),
			JsonObject.of("key","a","value",3),
			JsonObject.of("key","c","value",4)
		);
		JsonArray res = JsonArray.of(
				JsonObject.of("key","a","value",1),
				JsonObject.of("key","b","value",2),
				JsonObject.of("key","c","value",4)
			);
		assertEquals(res, str.distinct((v) -> ((JsonObject)v).get("key") ));
	}	
}
