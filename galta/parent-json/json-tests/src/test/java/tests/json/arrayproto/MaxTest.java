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

import tests.ProjectTestCase;

public class MaxTest extends ProjectTestCase {
	
	
	private static final String ARRAY_NUMBER = 
"""
[
	4,5,3,8,2
]
""";

	private static final String ARRAY_STRING = 
"""
[
	"c","d","a","b"
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

	public void testMax() throws Exception {
		assertNull(JsonArray.create().max());
		assertNull(JsonArray.create().max( (x,y) -> {
			return ((Number)y).intValue() - ((Number)x).intValue();
		}));
		
		assertEquals(8, numbers.max());
		// A natural-order comparator yields the maximum...
		assertEquals(8, numbers.max( (x,y) -> {
			return ((Number)x).intValue() - ((Number)y).intValue();
		}));
		// ...and a reversed one the minimum (max() used to return the minimum, and the
		// test compensated with a reversed comparator)
		assertEquals(numbers.min(), numbers.max( (x,y) -> {
			return ((Number)y).intValue() - ((Number)x).intValue();
		}));
		assertEquals("d", strings.max());
	}	
}
