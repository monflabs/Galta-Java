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
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class FindTest extends ProjectTestCase {
	
	
	private static final String OBJECTS = 
"""
[
    {
		name: "Smith", 
		address: [
			{type: "home", city: "Boston"},
			{type: "work", city: "Lowell"}
		]
	},
    {
		name: "Stone", 
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
	
	private static final String OBJECT = 
"""
{
    a: {
		name: "Smith", 
		address: [
			{type: "home", city: "Boston"},
			{type: "work", city: "Lowell"}
		]
	},
    b: {
		name: "Stone", 
		address: [
			{type: "home", city: "Lowell"},
			{type: "work", city: "Burlington"}
		]
	},
    c: {
		name: "Nelson", 
		address: [
			{type: "home", city: "Boston"},
			{type: "work", city: "Cambridge"}
		]
	}
}
""";
	
	private static final String ARRAYAA = 
"""
[
	{a: 1, b:2, c:3},
	{a: 4, b: 5},
	{a: 6, b: {a: 7, b:8, c:9}, c:10 }
]
""";
	
	private static final String OBJECTA = 
"""
{
	a: [1,2,3],
	b: [4,5],
    c: [6,[7,8,9],10]
}
""";	
	
	public void testFindNullMember() throws Exception {
		JsonArray p1 = JsonArray.parse(ARRAYAA);

		String RESULT = 
				"""
[
  {
    "a":1,
    "b":2,
    "c":3
  },
  {
    "a":4,
    "b":5
  },
  {
    "a":6,
    "b":{
      "a":7,
      "b":8,
      "c":9
    },
    "c":10
  },
  1,
  2,
  3,
  4,
  5,
  6,
  {
    "a":7,
    "b":8,
    "c":9
  },
  10,
  7,
  8,
  9
]
				""";

		assertEquals(JsonArray.parse(RESULT) ,p1.find(null));
	}	
	

	public void testFindMembers() throws Exception {
		JsonArray p1 = JsonArray.parse(ARRAYAA);

		assertEquals(JsonArray.of(1,4,6,7),p1.find("a"));
		assertEquals(JsonArray.of(2,5,JsonObject.of("a",7,"b",8,"c",9),8),p1.find("b"));
		assertEquals(JsonArray.of(3,10,9),p1.find("c"));

		assertEquals(JsonArray.of(1,4,6,7),p1.find("a",false));
		assertEquals(JsonArray.of(2,5,JsonObject.of("a",7,"b",8,"c",9)),p1.find("b",false));
		assertEquals(JsonArray.of(3,10,9),p1.find("c",false));
	}	

	public void testFindAndSetMembers() throws Exception {
		JsonArray p1 = JsonArray.parse(ARRAYAA);
		p1.findAndSet("a", 88);
		assertEquals(JsonArray.parse("[{a:88, b:2, c:3},{a: 88, b: 5},{a: 88, b: {a: 88, b:8, c:9}, c:10 }]"),p1);

		JsonArray p2 = JsonArray.parse(ARRAYAA);
		p2.findAndSet("b", 77);
		assertEquals(JsonArray.parse("[{a: 1, b:77, c:3},{a: 4, b: 77},{a: 6, b: 77, c:10 }]"),p2);

		JsonArray p3 = JsonArray.parse(ARRAYAA);
		p3.findAndSet("b", JsonObject.of("a",4,"b",5));
		assertEquals(JsonArray.parse("[{a: 1, b:{a:4,b:5}, c:3},{a: 4, b: {a:4,b:5}},{a: 6, b:{a:4,b:5}, c:10 }]"),p3);
	}	

	public void testFindIndexes() throws Exception {
		JsonObject p1 = JsonObject.parse(OBJECTA);

		assertEquals(JsonArray.of(1,4,6,7),p1.find(0));
		assertEquals(JsonArray.of(2,5,JsonArray.of(7,8,9),8),p1.find(1));
		assertEquals(JsonArray.of(3,10,9),p1.find(2));

		assertEquals(JsonArray.of(1,4,6,7),p1.find(0,false));
		assertEquals(JsonArray.of(2,5,JsonArray.of(7,8,9)),p1.find(1,false));
		assertEquals(JsonArray.of(3,10,9),p1.find(2,false));
	}	

	public void testFindAndSetIndexes() throws Exception {
		JsonObject p1 = JsonObject.parse(OBJECTA);
		p1.findAndSet(0, 88);
		assertEquals(JsonObject.parse("{a:[88,2,3],b: [88,5],c: [88,[88,8,9],10]}"),p1);

		JsonObject p2 = JsonObject.parse(OBJECTA);
		p2.findAndSet(1, 77);
		assertEquals(JsonObject.parse("{a:[1,77,3],b: [4,77],c: [6,77,10]}"),p2);

		JsonObject p3 = JsonObject.parse(OBJECTA);
		p3.findAndSet(1, JsonArray.of(12,13));
		assertEquals(JsonObject.parse("{a:[1,[12,13],3],b: [4,[12,13]],c: [6,[12,13],10]}"),p3);

		JsonObject m1 = JsonObject.parse(OBJECTA);
		m1.findAndSet(-1, 88);
		assertEquals(JsonObject.parse("{a:[1,2,88],b: [4,88],c: [6,[7,8,88],88]}"),m1);
	}	

	
	//
	// More friendly example
	//
	
	public void testFindInArray() throws Exception {
		JsonArray p1 = JsonArray.parse(OBJECTS);

		assertEquals(JsonArray.of("Smith","Stone","Nelson"),p1.find("name"));
		assertEquals(JsonArray.of("Boston","Lowell","Lowell","Burlington","Boston","Cambridge"),p1.find("city"));
	}	
	
	public void testFindInObject() throws Exception {
		JsonObject p1 = JsonObject.parse(OBJECT);

		assertEquals(JsonArray.of("Smith","Stone","Nelson"),p1.find("name"));
		assertEquals(JsonArray.of("Boston","Lowell","Lowell","Burlington","Boston","Cambridge"),p1.find("city"));
	}	
}
