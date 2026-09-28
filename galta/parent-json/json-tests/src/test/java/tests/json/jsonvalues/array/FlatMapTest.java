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

import org.monflabs.json.JsonArray;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class FlatMapTest extends ProjectTestCase {
		
	private static final String OBJECT = 
"""
{
  "firstName": "John",
  "lastName" : "doe",
  "age"      : 26
}
""";

	private static final String ARRAY1 = 
"""
[
  "John",
  "doe",
  27
]
""";
	private static final String ARRAY2 = 
"""
[
  ["John"],
  ["doe"],
  [27]
]
""";
	private static final String ARRAY3 = 
"""
[
  "John",
  ["doe"],
  [27]
]
""";
	
	// flatMap() is Stream.flatMap(): map each value, then flatten the mapper's result.
	// Flattening the input first is flat().map()
	public void testFlatObject() throws Exception {
		JsonValues o = JsonValues.parse(OBJECT);
		JsonValues p1 = o.flat().map( (v) -> v.isString() ? v.stringValue().toUpperCase() : v );
		assertEquals(JsonValues.of("JOHN","DOE",26),p1);
		// The single object is mapped as a whole
		JsonValues p2 = o.flatMap( (v) -> v.get("age") );
		assertEquals(JsonValues.of(26),p2);
	}	

	public void testFlatArray1() throws Exception {
		JsonValues a = JsonValues.parse(ARRAY1).flat();
		JsonValues p1 = a.flatMap( (v) -> v.isString()? v.stringValue().toUpperCase() : v );
		assertEquals(JsonValues.of("JOHN","DOE",27),p1);
	}	

	public void testFlatArray2() throws Exception {
		// The items are arrays: flatMap() flattens the arrays the mapper returns
		JsonValues p1 = JsonValues.parse(ARRAY2).flat().flatMap( (v) -> v.arrayValue() );
		assertEquals(JsonValues.of("John","doe",27),p1);
		// Returning the array wrapped in a JsonValues keeps it as one value
		JsonValues p2 = JsonValues.parse(ARRAY2).flat().flatMap( (v) -> v );
		assertEquals(3,p2._size());
		assertEquals(JsonArray.of("John"),p2._get(0));
	}	

	public void testFlatArray3() throws Exception {
		JsonValues p1 = JsonValues.parse(ARRAY3).flat().flatMap( (v) -> v.isArray() ? v.arrayValue() : v );
		assertEquals(JsonValues.of("John","doe",27),p1);
	}	
	
	public void testFlatMapExpands() throws Exception {
		// Each value can produce several values, or none
		JsonValues p1 = JsonValues.of(1,2).flatMap( (v) -> JsonArray.of(v.value(), v.value()) );
		assertEquals(JsonValues.of(1,1,2,2),p1);
		JsonValues p2 = JsonValues.of("a","b").flatMap( (v) -> JsonValues.of(v.value(),"x") );
		assertEquals(JsonValues.of("a","x","b","x"),p2);
		JsonValues p3 = JsonValues.of(1,2,3).flatMap( (v) -> v.intValue()==2 ? JsonValues.of() : v );
		assertEquals(JsonValues.of(1,3),p3);
		assertTrue(JsonValues.of().flatMap( (v) -> v ).isEmpty());
	}
}
