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

import org.junit.Before;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.parser.JsonParser.StringParser;

import tests.ProjectTestCase;

//
// This class is used to easily run experiments in dev mode
// This is not a test meant to run as part of the autonated suite
//
public class JSONSimpleStrictParserTest extends ProjectTestCase {

	StringParser parser;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		parser = new StringParser(JsonFactory.get());
		parser.setStrict(true);
	}
	
	String JSON = 
"""
[null]
""";
	
	public void testParser() throws Exception {
		try {
			Object o = parser.parse(JSON);
			support.print("SUCCESS\n{0}",o!=null ? o.toString() : "<null>");
		} catch(Throwable ex) {
			support.print("FAILURE");
			ex.printStackTrace();
		}
	}
}
