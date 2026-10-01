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
package tests.json;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.parser.JsonParser.StringParser;

import tests.ProjectTestCase;

/**
 * The strict parser accepts standard JSON and rejects the relaxed syntax.
 */
public class JSONSimpleStrictParserTest extends ProjectTestCase {

	StringParser parser;
	
	@Override
    public void setUp() throws Exception {
		super.setUp();
		
		parser = new StringParser(JsonFactory.get());
		parser.setStrict(true);
	}
	
	public void testParser() throws Exception {
		Object o = parser.parse("[null]");
		assertEquals("[null]", JsonFactory.get().stringify(o, true));
		o = parser.parse("{\"a\": [1, 2.5, \"s\", true, false, null]}");
		assertEquals("{\"a\":[1,2.5,\"s\",true,false,null]}", JsonFactory.get().stringify(o, true));
	}
	
	public void testRelaxedSyntaxRejected() throws Exception {
		for(String s: new String[] {"[1,]", "{a:1}", "{'a':1}", "[1] // comment", "[01]"}) {
			try {
				parser.parse(s);
				fail("Strict parser accepted: "+s);
			} catch(Exception e) {
				// Expected
			}
		}
	}
}
