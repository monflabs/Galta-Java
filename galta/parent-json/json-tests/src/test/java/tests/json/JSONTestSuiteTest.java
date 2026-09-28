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

import java.io.File;
import java.util.Arrays;

import org.junit.Before;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.parser.JsonParser.StringParser;

import tests.ProjectTestCase;

//
//   https://github.com/nst/JSONTestSuite
//
public class JSONTestSuiteTest extends ProjectTestCase {

	StringParser parser;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		parser = new StringParser(JsonFactory.get());
		parser.setStrict(true);
	}
	
	
	public void testParser() throws Exception {
		// Browse all the tests
		File jsonDir = new File(support.getTestResourcesDirectory(),"JSONTestSuite");

		File parsingDir = new File(jsonDir,"test_parsing");
		checkFolder(parsingDir);
		
		File transformDir = new File(jsonDir,"test_transform");
		checkFolder(transformDir);
	}

	// To test individual files
//	public void testParserSingleFile() throws Exception {
//		File jsonDir = new File(support.getTestResourcesDirectory(),"JSONTestSuite");
//		File jsonFile = new File(jsonDir,"test_parsing/i_structure_UTF-8_BOM_empty_object.json");
//		checkFile(jsonFile);
//	}

	private void checkFolder(File dir) throws Exception {
		assertTrue(dir.exists());
	
		File[] files = dir.listFiles( (f) -> f.isFile() && f.getPath().endsWith(".json") );
		Arrays.sort(files);
		for(int i=0; i<files.length; i++) {
			checkFile(files[i]);
		}
	}
	
	private void checkFile(File file) throws Exception {
		assertTrue(file.exists());
		String json = support.readString(file);

		String fName = file.getName();
		ParseResult expectedResult = ParseResult.SUCCESS;
		if(fName.startsWith("n_")) {
			expectedResult = ParseResult.FAILURE;
		} else if(fName.startsWith("i_")) {
			expectedResult = ParseResult.UNDEFINED;
		}
		ParseResult result = parseJson(json);
		if(expectedResult!=ParseResult.UNDEFINED) {
			if(expectedResult!=result) {
				support.print("Parser error : {0}, was {1}, {2}  - {3}\n  {4}",expectedResult,result,fName,file.getPath(),json);
				fail();
			}
		} else {
			if(result!=ParseResult.SUCCESS) {
				support.print("Unsupported JSON Optional parser feature: {0} -  {1}\n  {2}",fName,file.getPath(),json);
			}
		}
	}

	enum ParseResult {SUCCESS, FAILURE, UNDEFINED}
	private ParseResult parseJson(String json) throws Exception {
		try {
			parser.parse(json);
			return ParseResult.SUCCESS;
		} catch(Throwable ex) {
			return ParseResult.FAILURE;
		}
	}
}
