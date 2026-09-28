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
package tests.json.streams;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.monflabs.json.JsonArray;

import tests.ProjectTestCase;

public class ArrayStreamsTest extends ProjectTestCase {
	
	private static final String JSON = 
"""
[10, 11, 12, 11, 13]
""";	
	

	JsonArray json = JsonArray.parse(JSON);
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonArray.parse(JSON);
	}

	public void testArrayStream() throws Exception {
		List<Object> list = json.stream().collect(Collectors.toList());
		assertEquals(5,list.size());
		assertEquals(10,list.get(0));
		assertEquals(11,list.get(1));
		assertEquals(12,list.get(2));
		assertEquals(11,list.get(3));
		assertEquals(13,list.get(4));
	}
}
 