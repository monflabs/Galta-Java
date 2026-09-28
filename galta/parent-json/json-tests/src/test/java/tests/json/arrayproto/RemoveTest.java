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
import org.monflabs.json.JsonObject;
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
	
	JsonObject json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonObject.parse(JSON);
	}

	public void testRemove() throws Exception {
		JsonArray p1 = json.getArray("phoneNumbers");
		
		JsonArray f1 = p1.remove( v -> JsonValues.of(v).get("type").eq("home") );
		assertEquals(2,f1.size());
		assertEquals("mobile",f1.getObject(0).getString("type"));
		assertEquals("work",f1.getObject(1).getString("type"));

		JsonArray f2 = p1.remove( v -> JsonValues.of(v).get("type").in("home","mobile") );
		assertEquals(1,f2.size());
		assertEquals("work",f2.jsonValues(0).get("type").stringValue());
	}	

}
