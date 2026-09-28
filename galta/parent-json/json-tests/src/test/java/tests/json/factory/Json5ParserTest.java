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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.parser.JsonParser;

import tests.ProjectTestCase;


public class Json5ParserTest extends ProjectTestCase {
	
	// https://json5.org/
    public void testJson5() throws Exception {
    	// Objects
    	assertConstruct( "{ a: 1 }", JsonObject.of("a",1));
    	assertConstruct( "{ a: 1, }", JsonObject.of("a",1));
    	
    	// Arrays
    	assertConstruct( "[1,]", JsonArray.of(1));
    	
    	// Strings
    	assertConstruct( "'abc'", "abc");
    	assertConstruct( "'abc\\\naxy'", "abcaxy");
    	assertConstruct( "'a\\nb\\rc'", "a\nb\rc");
    	
    	// Numbers
    	assertConstruct( "0x1", 1);
    	assertConstruct( ".1", 0.1);
    	assertConstruct( "1.", 1.0);
    	assertConstruct( "-01", -1);

    	// Comments
    	assertConstruct( "//here\n\"xyz\"", "xyz");
    	assertConstruct( "/*here*/\"xyz\"", "xyz");
    }

    public void assertConstruct(String json, Object value) throws Exception {
    	checkValueStrict( json, value );
    	checkValueNoStrict( json, value );
    }

    private void checkValueStrict(String json, Object value) throws Exception {
		JsonParser.StringParser parser = new JsonParser.StringParser(JsonFactory.get());
		parser.setStrict(true);
    	assertThrows( Exception.class, () -> { parser.parse(json); } );
    }
    
    private void checkValueNoStrict(String json, Object value) throws Exception {
		JsonParser.StringParser parser = new JsonParser.StringParser(JsonFactory.get());
		parser.setStrict(false);
		Object o = parser.parse(json);
    	assertEquals(o,value);
    }
}
