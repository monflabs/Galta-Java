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
package tests.json.factory;

import java.io.StringWriter;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;


public class JsonStringifyTest extends ProjectTestCase {

    public void testStringify() throws Exception {
    	_test(null);
    	_test("a string");
    	_test(12);
    	_test(12.345);
    	_test(true);
    	_test(JsonFactory.get().of("a",1,"b",true));
    	_test(JsonFactory.get().arrayOf("a",1,true));
    	_test(JsonFactory.get().parse(
// Java15    	
"""
{
  a: 34,
  b: '897abc',
  c: {
    d: [
     1, 2, 3
    ]
  }
}
"""
    	));
    }    

    private void _test(Object o) {
    	String s = JsonFactory.get().stringify(o, false);
    	Object clone = JsonFactory.get().parse(s);
    	String s2 = JsonFactory.get().stringify(clone, false);
    	
    	assertEquals(s, s2);
    }
    
    
    public void testFormat() throws Exception {
    	JsonObject m = JsonFactory.get().createObject();
    	m.put("a", true);
    	m.put("b", 235);
    	m.put("c", "xyz");
    	m.put("d", JsonFactory.get().of("a",1,"b",2) );
    	m.put("e", JsonFactory.get().createObject() );
    	m.put("f", JsonFactory.get().arrayOf(1,2) );

    	String s = JsonFactory.get().stringify(m, false);
    	assertEquals(
"{\n  \"a\": true,\n  \"b\": 235,\n  \"c\": \"xyz\",\n  \"d\": {\n    \"a\": 1,\n    \"b\": 2\n  },\n  \"e\": {},\n  \"f\": [\n    1,\n    2\n  ]\n}"
    	, s);
    }
    
    public void testWriter() {
    	JsonObject o = JsonFactory.get().createObject();
    	o.put("a", 123);

    	StringWriter w1 = new StringWriter();
    	JsonFactory.get().stringify(w1, o);
    	String s1 = w1.toString();

    	StringWriter w2 = new StringWriter();
    	JsonFactory.get().stringify(w2, o, true);
    	String s2 = w2.toString();
    	
    	assertEquals("{\"a\":123}", s1);
    	assertEquals(s1, s2);
    }
    
    public void testSorted() {
    	JsonObject o = JsonFactory.get().createObject();
    	o.put("a", 123);
    	o.put("c", 789);
    	o.put("b", 456);

    	String s1 = JsonFactory.get().stringifySorted(o);
    	String s2 = JsonFactory.get().stringifySorted(o, true);
    	
    	assertEquals("{\"a\":123,\"b\":456,\"c\":789}", s1);
    	assertEquals(s1, s2);
    }
}
