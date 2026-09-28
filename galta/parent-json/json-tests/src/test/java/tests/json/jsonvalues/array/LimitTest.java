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
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class LimitTest extends ProjectTestCase {

	private static final String JSON = 
"""
[11,22,33,44,55]
""";	
	

	JsonValues array;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		array = JsonValues.parseAndFlat(JSON);
	}
	
	public void testLimit() throws Exception {
		assertEquals(JsonValues.EMPTY, JsonValues.EMPTY.limit(0));
		assertEquals(JsonValues.EMPTY, JsonValues.EMPTY.limit(2));
		assertEquals(JsonValues.EMPTY, JsonValues.EMPTY.limit(5));
		assertEquals(JsonValues.EMPTY, JsonValues.EMPTY.limit(6));

		assertEquals(JsonValues.EMPTY, array.limit(0));
		assertEquals(JsonValues.of(11,22), array.limit(2));
		assertEquals(JsonValues.of(11,22,33,44,55), array.limit(5));
		assertEquals(JsonValues.of(11,22,33,44,55), array.limit(6));
	}
}
