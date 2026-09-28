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

import static org.junit.Assert.assertThrows;

import java.text.MessageFormat;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;


public class JsonParserTest extends ProjectTestCase {
	
    public void testParseLiteral() throws Exception {
    	checkValue("'aaa'", "aaa");
    	checkValue("1", 1);
    	checkValue("-2", -2);
    	checkValue("+2", 2);
    	checkValue("79.34", 79.34);
    	checkValue("-79.34", -79.34);
    	checkValue("1e5", 100000.0);
    	checkValue("10E5", 1000000.0);
    	checkValue("true", true);
    	checkValue("false", false);
    }
    
    public void testParseObjectLiteral() throws Exception {
    	JsonObject o = JsonFactory.get().createObject();
    	o.put("a",1);
    	assertEquals(1,o.get("a"));
    	checkValue("{a:1}", o);
    	checkValue("{'a':1}", o);
    	checkValue("{\"a\":1}", o);
    	checkValue("{ a : 1 }", o);
    	checkValue("{ 'a' : 1 }", o);
    	checkValue("{ \"a\" : 1 }", o);

    	o.put("b",true);
    	o.put("c",false);
    	o.putValue("d",(Object)null);
    	o.put("e","str");
    	checkValue("{a:1, b:true, c:false, d:null, e:'str'}", o);
    	checkValue("{e: 'str', a : 1,b: true, c   : false, d   :null}", o);
    }
    public void testParseObjectLiteralNullName() throws Exception {
    	// An unquoted name is a name, even "null": JSON keys are never null
    	JsonObject o = JsonFactory.get().createObject();
    	o.put("null",1);
    	checkValue("{null:1}", o);
    	// A null key is rejected by every way of storing one
    	assertFalse( JsonFactory.get().supportsNullKeys() );
    	JsonObject n = JsonFactory.get().createObject();
    	assertThrows( NullPointerException.class, () -> n.put(null,1) );
    	assertThrows( NullPointerException.class, () -> n.putValue(null,1) );
    	assertThrows( NullPointerException.class, () -> n.putIfAbsent(null,1) );
    	assertThrows( NullPointerException.class, () -> n.putAll(java.util.Collections.singletonMap(null,1)) );
    	assertThrows( NullPointerException.class, () -> n.computeIfAbsent(null, k -> 1) );
    	assertThrows( NullPointerException.class, () -> n.compute(null, (k,v) -> 1) );
    	assertThrows( NullPointerException.class, () -> n.merge(null, 1, (a,b) -> b) );
    	assertTrue( n.isEmpty() );
    }
    public void testParseObjectLiteralExtraComma() throws Exception {
    	JsonObject o = JsonFactory.get().createObject();
    	o.put("a",1);
    	checkValue("{a:1}", o);
    	checkValue("{a:1,}", o);
    	o.put("b",2);
    	checkValue("{a:1,b:2}", o);
    	checkValue("{a:1,b:2,}", o);
    }
    
    public void testNestedObjectLiteral() throws Exception {
    	JsonObject o1 = JsonFactory.get().createObject();
    	o1.put("a",1);
    	checkValue("{a:1}", o1);
    	
    	JsonObject o2 = JsonFactory.get().createObject();
    	o2.put("b",2);
    	checkValue("{b:2}", o2);

    	o1.put("oo", o2);
    	checkValue("{a:1, oo:{b:2}}", o1);
    }
    
    public void testParseArrayLiteral() throws Exception {
    	JsonArray o = JsonFactory.get().createArray();
    	checkValue("[]", o);
    	o.add(1);
    	checkValue("[1]", o);
    	o.add(2);
    	checkValue("[1,2]", o);
    	o.add(true);
    	checkValue("[1,2,true]", o);
    	o.add(false);
    	checkValue("[1,2,true,false]", o);
    	o.addValue((Object)null);
    	checkValue("[1,2,true,false,null]", o);
    	o.add("str");
    	checkValue("[1,2, true,false, null , 'str']", o);
    }
    public void testParseArrayLiteralExtraComma() throws Exception {
    	JsonArray o = JsonFactory.get().createArray();
    	checkValue("[]", o);
    	o.add(1);
    	checkValue("[1]", o);
    	checkValue("[1,]", o);
    	o.add(2);
    	checkValue("[1,2]", o);
    	checkValue("[1,2,]", o);
    }
    
    public void testParseError() throws Exception {
    	checkParseError("1ab efg");
    	checkParseError("<xml</xml>");
    }
	
    public void testParseComment() throws Exception {
    	checkParse("//Comment\n{a:1}//Another\n",  JsonFactory.get().of("a",1));
    	checkParse("//ok?\n{a:1}", JsonFactory.get().of("a",1));
    	checkParse("{a: 1 //ok?\n}/*Another*/\n", JsonFactory.get().of("a",1));
    	checkParse("/*Comment*/{a:1}/*Another*/\n", JsonFactory.get().of("a",1));
    	checkParse("/*Comment*/{a: /*here*/ 1 //ok?\n}/*Another*/\n", JsonFactory.get().of("a",1));
    	checkParse("/*Comment*/{a: /*here*/ '1/*1*/' //ok?\n}/*Another*/\n", JsonFactory.get().of("a","1/*1*/"));
    }
    
	public void testBigFile() {
    	JsonObject o = createObject(0);
    	String json = JsonFactory.get().stringify(o);
    	JsonObject o2 = (JsonObject)JsonFactory.get().parse(json);
    	assertTrue(o.equals(o2));
    }
    private JsonObject createObject(int level) {
    	JsonObject m = JsonFactory.get().createObject();
    	for(int i=0; i<26; i++) {
    		String key = String.valueOf((char)(i+'A'));
    		String value = "jkashkjsajkdsajkdjksajkdasjk";
    		m.put(key, value);
    	}
    	if(level<3) {
    		m.put("inner", createObject(level+1));
    	}
    	return m;
    }
    
    private void checkValue(String json, Object value) throws Exception {
    	Object o = JsonFactory.get().parse(json);
    	assertEquals(o,value);
    	//dumpJson(o);
    }
    private void checkParse(String json, Object result) throws Exception {
		Object o = JsonFactory.get().parse(json);
    	assertEquals(o,result);
    	//dumpJson(o);
    }
    private void checkParseError(String json) throws Exception {
    	try {
	    	@SuppressWarnings("unused")
			Object o = JsonFactory.get().parse(json);
			assertTrue(MessageFormat.format("JSON should be an error: {0}", json), false);
    	} catch(JsonException ex) {
    		// Desired....
    	}
    }
}
