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

import java.util.Arrays;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class ForEachTest extends ProjectTestCase {

	private static final String JSON = 
"""
{
  "o1": [10, 11, 12, 11, 13]
}
""";	

	JsonValues json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonValues.parse(JSON);
	}
	
	public void testForEach() throws Exception {
		// Return a raw value (Integer)
		JsonArray res = JsonArray.create();
		json.getAndFlat("o1").forEach( v -> res.add(v.intValue() * 2) );
		assertEquals(5,res.size());
		assertEquals(Arrays.asList(20,22,24,22,26),res);
	}
}
