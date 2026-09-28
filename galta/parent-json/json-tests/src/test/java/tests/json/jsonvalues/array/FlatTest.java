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

import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class FlatTest extends ProjectTestCase {
		
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
	
	public void testFlatObject() throws Exception {
		JsonValues o = JsonValues.parse(OBJECT);
		JsonValues p1 = o.flat();
		assertEquals(JsonValues.of("John","doe",26),p1);
	}	

	public void testFlatArray1() throws Exception {
		JsonValues a = JsonValues.parse(ARRAY1);
		JsonValues p1 = a.flat();
		assertEquals(JsonValues.of("John","doe",27),p1);
	}	

	public void testFlatArray2() throws Exception {
		JsonValues p1 = JsonValues.parse(ARRAY2).flat().flat();
		assertEquals(JsonValues.of("John","doe",27),p1);
	}	

	public void testFlatArray3() throws Exception {
		JsonValues p1 = JsonValues.parse(ARRAY3).flat().flat();
		assertEquals(JsonValues.of("John","doe",27),p1);
	}	
}
