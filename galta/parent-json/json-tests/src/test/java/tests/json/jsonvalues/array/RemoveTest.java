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

public class RemoveTest extends ProjectTestCase {
	
	
	private static final String JSON = 
"""
{
  "firstName": "John",
  "lastName" : "doe",
  "age"      : 26,
  "address"  : {
    "streetAddress": "naist street",
    "city"         : "Nara",
    "postalCode"   : "630-0192"
  },
  "phoneNumbers": [
    {
      "type"  : "mobile",
      "number": "0123-4567-8888"
    },
    {
      "type"  : "home",
      "number": "0123-4567-8910"
    },
    {
      "type"  : "work",
      "number": "0123-4567-8999"
    }
  ]
}
""";
	
	JsonValues json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonValues.parse(JSON);
	}

	public void testRemove() throws Exception {
		JsonValues p1 = json.getAndFlat("phoneNumbers");
		
		JsonValues f1 = p1.reject( v -> v.get("type").eq("home") );
		assertEquals(2,f1._size());
		assertEquals("mobile",f1.slice(0).get("type").stringValue());
		assertEquals("work",f1.slice(1).get("type").stringValue());

		JsonValues f2 = p1.reject( v -> v.get("type").in("home","mobile") );
		assertEquals(1,f2._size());
		assertEquals("work",f2.slice(0).get("type").stringValue());
	}	

}
