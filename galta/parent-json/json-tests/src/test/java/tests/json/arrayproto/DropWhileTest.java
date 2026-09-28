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

import static org.junit.Assert.assertArrayEquals;

import org.junit.Before;
import org.monflabs.json.JsonObject;
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
	
	JsonObject json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonObject.parse(JSON);
	}

	static boolean beforeC(Object v) {
		return JsonValues.of(v).lt("C");
	}
	static boolean untilC(Object v) {
		return JsonValues.of(v).lte("C");
	}

	public void testDropWhile() throws Exception {
		assertArrayEquals( new Object[] {}, json.getArray("empty").dropWhile(DropWhileTest::beforeC).toArray() );
		
		assertArrayEquals( new Object[] {"C"}, json.getArray("unique").dropWhile(DropWhileTest::beforeC).toArray() );
		assertArrayEquals( new Object[] {}, json.getArray("unique").dropWhile(DropWhileTest::untilC).toArray() );

		assertArrayEquals( new Object[] {"C","D","E"}, json.getArray("list").dropWhile(DropWhileTest::beforeC).toArray() );
		assertArrayEquals( new Object[] {"D","E"}, json.getArray("list").dropWhile(DropWhileTest::untilC).toArray() );
	}	
}
