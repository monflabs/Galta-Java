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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Before;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.parser.JsonParser.StringParser;

import tests.ProjectTestCase;

//
//   https://github.com/nst/JSONTestSuite
//
// Every file is parsed twice by the strict parser: from the text decoded by the test
// (a String), and from the raw bytes through JsonFactory.parse(InputStream, UTF-8, strict),
// which also rejects the invalid UTF-8 byte sequences. A rejection must be a
// JsonException (a ParseException...): any other exception is a parser bug.
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
		checkFolder(parsingDir, true);

		// The transform tests have no expected result for the invalid UTF-8 ones
		File transformDir = new File(jsonDir,"test_transform");
		checkFolder(transformDir, false);
	}

	// To test individual files
//	public void testParserSingleFile() throws Exception {
//		File jsonDir = new File(support.getTestResourcesDirectory(),"JSONTestSuite");
//		File jsonFile = new File(jsonDir,"test_parsing/i_structure_UTF-8_BOM_empty_object.json");
//		checkFile(jsonFile);
//	}

	private void checkFolder(File dir, boolean bytes) throws Exception {
		assertTrue(dir.exists());

		File[] files = dir.listFiles( (f) -> f.isFile() && f.getPath().endsWith(".json") );
		Arrays.sort(files);
		for(int i=0; i<files.length; i++) {
			checkFile(files[i], bytes);
		}
	}

	private void checkFile(File file, boolean checkBytes) throws Exception {
		assertTrue(file.exists());
		String json = support.readString(file);

		String fName = file.getName();
		ParseResult expectedResult = ParseResult.SUCCESS;
		if(fName.startsWith("n_")) {
			expectedResult = ParseResult.FAILURE;
		} else if(fName.startsWith("i_")) {
			expectedResult = ParseResult.UNDEFINED;
		}
		check(file, expectedResult, parseJson(json), json, "string");
		if(checkBytes) {
			byte[] bytes = Files.readAllBytes(file.toPath());
			check(file, expectedResult, parseBytes(bytes), json, "bytes");
		}
	}
	private void check(File file, ParseResult expectedResult, ParseResult result, String json, String source) {
		String fName = file.getName();
		if(expectedResult!=ParseResult.UNDEFINED) {
			if(expectedResult!=result) {
				support.print("Parser error ({0}): {1}, was {2}, {3}  - {4}\n  {5}",source,expectedResult,result,fName,file.getPath(),json);
				fail();
			}
		} else {
			if(result!=ParseResult.SUCCESS) {
				support.print("Unsupported JSON Optional parser feature ({0}): {1} -  {2}\n  {3}",source,fName,file.getPath(),json);
			}
		}
	}

	enum ParseResult {SUCCESS, FAILURE, UNDEFINED}
	private ParseResult parseJson(String json) throws Exception {
		try {
			parser.parse(json);
			return ParseResult.SUCCESS;
		} catch(JsonException ex) {
			return ParseResult.FAILURE;
		}
	}
	private ParseResult parseBytes(byte[] bytes) throws Exception {
		try {
			JsonFactory.get().parse(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8, true);
			return ParseResult.SUCCESS;
		} catch(JsonException ex) {
			return ParseResult.FAILURE;
		}
	}
}
