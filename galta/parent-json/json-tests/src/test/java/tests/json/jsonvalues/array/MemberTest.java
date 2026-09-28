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

public class MemberTest extends ProjectTestCase {
	
	
	private static final String OBJECTS = 
"""
[
    {
		name: "Smith",
		first: "Paul", 
		address: [
			{type: "home", city: "Boston"},
			{type: "work", city: "Lowell"}
		]
	},
    {
		name: "Stone", 
		first: "Mike", 
		address: [
			{type: "home", city: "Lowell"},
			{type: "work", city: "Burlington"}
		]
	},
    {
		name: "Nelson", 
		address: [
			{type: "home", city: "Boston"},
			{type: "work", city: "Cambridge"}
		]
	}
]
""";
	
	private static final String ARRAYS = 
"""
[
	[ "Smith", "a" ],
	[ "Stone", "b", "c" ],
	[ "Nelson" ]
]
""";
	
	private static final String MIXED = 
"""
[
	[ {name: "Smith"} ],
	[ {name: "Stone"} ],
	[ {name: "Nelson"} ]
]
""";
	
	public void testMemberString() throws Exception {
		JsonValues p1 = JsonValues.parseAndFlat(OBJECTS);

		assertEquals(JsonValues.of("Smith","Stone","Nelson"),p1.get("name"));
		assertEquals(JsonValues.of("Paul","Mike"),p1.get("first"));
		assertEquals(JsonValues.of("Smith","Paul","Stone","Mike","Nelson"),p1.get("name","first"));
	}	
	
	public void testMemberIndex() throws Exception {
		JsonValues p1 = JsonValues.parseAndFlat(ARRAYS);

		assertEquals(JsonValues.of("Smith","Stone","Nelson"),p1.get(0));
		assertEquals(JsonValues.of("a","b"),p1.get(1));
		assertEquals(JsonValues.of("Smith","a","Stone","b","Nelson"),p1.get(0,1));
	}	
	
	public void testMemberMixed() throws Exception {
		JsonValues p1 = JsonValues.parseAndFlat(MIXED);

		assertEquals(JsonValues.of("Smith","Stone","Nelson"),p1.get(0).get("name"));
	}	
}
