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
import org.monflabs.json.JsonUtil;

import tests.ProjectTestCase;

public class SortedTest extends ProjectTestCase {

	private static final String NUMBERS = 
"""
[22,11,44,55,33]
""";	
	
	private static final String STRINGS = 
"""
["aa","cc","dd","bb"]
""";	
	
	private static final String OBJECTS = 
"""
[
	{name: "aa", type: 1},
	{name: "bb", type: 1},
	{name: "aa", type: 2},
	{name: "bb", type: 2},
	{name: "cc", type: 1},
]
""";	

	JsonArray numbers;
	JsonArray strings;
	JsonArray objects;

	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		numbers = JsonArray.parse(NUMBERS);
		strings = JsonArray.parse(STRINGS);
		objects = JsonArray.parse(OBJECTS);
	}
	
	public void testSorted() throws Exception {
		assertEquals(JsonArray.of(11,22,33,44,55), numbers.sorted());
		assertEquals(JsonArray.of(11,22,33,44,55), numbers.sorted(true));
		assertEquals(JsonArray.of(55,44,33,22,11), numbers.sorted(false));

		assertEquals(JsonArray.of("aa","bb","cc","dd"), strings.sorted());
		assertEquals(JsonArray.of("aa","bb","cc","dd"), strings.sorted(true));
		assertEquals(JsonArray.of("dd","cc","bb","aa"), strings.sorted(false));

		assertEquals(
			JsonArray.of(
				JsonObject.of("name","aa","type",1),
				JsonObject.of("name","aa","type",2),
				JsonObject.of("name","bb","type",1),
				JsonObject.of("name","bb","type",2),
				JsonObject.of("name","cc","type",1)
			)
		, objects.sorted(JsonUtil.objectComparator().add("name").add("type")));
		assertEquals(
				JsonArray.of(
					JsonObject.of("name","cc","type",1),
					JsonObject.of("name","bb","type",1),
					JsonObject.of("name","bb","type",2),
					JsonObject.of("name","aa","type",1),
					JsonObject.of("name","aa","type",2)
				)
			, objects.sorted(JsonUtil.objectComparator().add("name",false).add("type")));
	}
}
