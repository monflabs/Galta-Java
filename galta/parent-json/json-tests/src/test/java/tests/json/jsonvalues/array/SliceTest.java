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

public class SliceTest extends ProjectTestCase {

	
	private static final String ARRAYS = 
"""
[
	"Smith",
	"Stone",
	"Williams",
	"Brown",
	"Nelson"
]
""";

	
	public void testSlice() throws Exception {
		JsonValues p1 = JsonValues.parseAndFlat(ARRAYS);

		assertEquals(JsonValues.of("Smith"),p1.slice(0));
		assertEquals(JsonValues.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(0,5));
		assertEquals(JsonValues.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(0,null));
		assertEquals(JsonValues.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(null,5));
		
		assertEquals(JsonValues.of(),p1.slice(1,1));
		assertEquals(JsonValues.of("Stone"),p1.slice(1));
		assertEquals(JsonValues.of("Stone"),p1.slice(1,2));
		assertEquals(JsonValues.of("Stone","Williams","Brown"),p1.slice(1,4));

		assertEquals(JsonValues.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(-5,null));
		assertEquals(JsonValues.of("Smith","Stone","Williams","Brown"),p1.slice(-5,-1));
		assertEquals(JsonValues.of("Williams","Brown"),p1.slice(-3,-1));

		assertEquals(JsonValues.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(null,null,1));
		assertEquals(JsonValues.of("Smith","Williams","Nelson"),p1.slice(null,null,2));
		assertEquals(JsonValues.of("Stone","Brown"),p1.slice(1,null,2));
		
		assertEquals(JsonValues.of("Smith","Brown"),p1.slice(0,null,3));
	}	
}
