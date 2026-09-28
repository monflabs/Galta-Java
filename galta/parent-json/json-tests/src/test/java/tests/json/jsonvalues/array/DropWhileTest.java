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

public class DropWhileTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
{
  "empty": [
  ],
  "unique": [
	"C"
  ],
  "list": [
    "A",
    "B",
	"C",
	"D",
	"E"
  ],
}
""";
	
	JsonValues json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonValues.parse(JSON);
	}

	static boolean beforeC(JsonValues v) {
		return v.lt("C");
	}
	static boolean untilC(JsonValues v) {
		return v.lte("C");
	}

	public void testDropWhile() throws Exception {
		assertEquals( JsonValues.of(), json.getAndFlat("empty").dropWhile(DropWhileTest::beforeC) );
		
		assertEquals( JsonValues.of("C"), json.getAndFlat("unique").dropWhile(DropWhileTest::beforeC) );
		assertEquals( JsonValues.of(), json.getAndFlat("unique").dropWhile(DropWhileTest::untilC) );

		assertEquals( JsonValues.of("C","D","E"), json.getAndFlat("list").dropWhile(DropWhileTest::beforeC) );
		assertEquals( JsonValues.of("D","E"), json.getAndFlat("list").dropWhile(DropWhileTest::untilC) );
	}	
}
