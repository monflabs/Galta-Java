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
import org.monflabs.json.JsonUtil;

import tests.ProjectTestCase;

public class NoneMatchTest extends ProjectTestCase {
	
	
	private static final String ARRAY_NUMBER = 
"""
[
	4,5,3,8,2
]
""";
	
	JsonArray array;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		array = JsonArray.parse(ARRAY_NUMBER);
	}

	
	public void testAnyMatch() throws Exception {
		assertTrue(array.noneMatch( (v) -> JsonUtil.checkInt(v)>=9 ));
		assertFalse(array.noneMatch( (v) -> JsonUtil.checkInt(v)>=8 ));
		assertFalse(array.noneMatch( (v) -> JsonUtil.checkInt(v)>=2 ));
	}	
}
