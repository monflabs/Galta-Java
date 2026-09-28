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

public class SkipTest extends ProjectTestCase {

	private static final String JSON = 
"""
[11,22,33,44,55]
""";	
	

	JsonArray array;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		array = JsonArray.parse(JSON);
	}
	
	public void testSkip() throws Exception {
		assertEquals(JsonArray.create(), JsonArray.create().skip(0));
		assertEquals(JsonArray.create(), JsonArray.create().skip(2));
		assertEquals(JsonArray.create(), JsonArray.create().skip(5));
		assertEquals(JsonArray.create(), JsonArray.create().skip(6));

		assertEquals(JsonArray.of(11,22,33,44,55), array.skip(0));
		assertEquals(JsonArray.of(33,44,55), array.skip(2));
		assertEquals(JsonArray.create(), array.skip(5));
		assertEquals(JsonArray.create(), array.skip(6));
	}
}
