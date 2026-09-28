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

import org.monflabs.json.JsonArray;

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
		JsonArray p1 = JsonArray.parse(ARRAYS);

		assertEquals(JsonArray.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(0,5));
		assertEquals(JsonArray.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(0,null));
		assertEquals(JsonArray.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(null,5));
		
		assertEquals(JsonArray.of(),p1.slice(1,1));
		assertEquals(JsonArray.of("Stone"),p1.slice(1,2));
		assertEquals(JsonArray.of("Stone","Williams","Brown"),p1.slice(1,4));

		assertEquals(JsonArray.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(-5,null));
		assertEquals(JsonArray.of("Smith","Stone","Williams","Brown"),p1.slice(-5,-1));
		assertEquals(JsonArray.of("Williams","Brown"),p1.slice(-3,-1));

		assertEquals(JsonArray.of("Smith","Stone","Williams","Brown","Nelson"),p1.slice(null,null,1));
		assertEquals(JsonArray.of("Smith","Williams","Nelson"),p1.slice(null,null,2));
		assertEquals(JsonArray.of("Stone","Brown"),p1.slice(1,null,2));
		
		assertEquals(JsonArray.of("Smith","Brown"),p1.slice(0,null,3));
	}	
}
