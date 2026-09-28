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
	
	JsonValues numbers;
	JsonValues strings;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		numbers = JsonValues.parseAndFlat(ARRAY_NUMBER);
		strings = JsonValues.parseAndFlat(ARRAY_STRING);
	}

	public void testMax() throws Exception {
		assertEquals(JsonValues.NULL,JsonValues.NULL.max());
		assertEquals(JsonValues.NULL,JsonValues.NULL.max( (x,y) -> {
			return y.intValue() - x.intValue();
		}));
		
		assertEquals(JsonValues.of(8), numbers.max());
		// A natural-order comparator yields the maximum, a reversed one the minimum
		assertEquals(JsonValues.of(8), numbers.max( (x,y) -> {
			return x.intValue() - y.intValue();
		}));
		assertEquals(numbers.min(), numbers.max( (x,y) -> {
			return y.intValue() - x.intValue();
		}));
		assertEquals(JsonValues.of("d"), strings.max());
	}	
}
