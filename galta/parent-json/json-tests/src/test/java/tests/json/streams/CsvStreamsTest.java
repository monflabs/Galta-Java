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
package tests.json.streams;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.stream.CsvMapping;
import org.monflabs.json.stream.CsvMapping.QuoteStrategy;
import org.monflabs.json.util.JsonCollectors;

import tests.ProjectTestCase;

public class CsvStreamsTest extends ProjectTestCase {
	
	private static final String JSON = 
"""
[
	[
	  "AA",
	  "BB",
	  " C",
	  "D ",
	  "34\\\"",
	  "\\\"35",
	  "3\\\"6",
	  "",
      null,
	  true,
      10,
      79.34
	]
]
""";	

	JsonArray json = JsonArray.parse(JSON);
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonArray.parse(JSON);
	}
	
	public void testCsvStandard() throws Exception {
		JsonArray a = json.stream()
				.map( CsvMapping.toCsvStrings() )
				.collect(JsonCollectors.toJsonArray());	
		
		if(support.isVerbose()) {
			support.print(a.toString());
		}
		support.assertJsonTemplate(a, "csv-standard");
	}

	public void testCsvSeparator() throws Exception {
		JsonArray a = json.stream()
				.map( CsvMapping.toCsvStrings(';',QuoteStrategy.REQUIRED) )
				.collect(JsonCollectors.toJsonArray());	
		
		if(support.isVerbose()) {
			support.print(a.toString());
		}
		support.assertJsonTemplate(a, "csv-seperator");
	}

	public void testCsvForceQuote() throws Exception {
		JsonArray a = json.stream()
				.map( CsvMapping.toCsvStrings(',',QuoteStrategy.ALWAYS) )
				.collect(JsonCollectors.toJsonArray());	
		
		if(support.isVerbose()) {
			support.print(a.toString());
		}
		support.assertJsonTemplate(a, "csv-always");
	}

	public void testCsvForceEmpty() throws Exception {
		JsonArray a = json.stream()
				.map( CsvMapping.toCsvStrings(',',QuoteStrategy.EMPTY) )
				.collect(JsonCollectors.toJsonArray());	
		
		if(support.isVerbose()) {
			support.print(a.toString());
		}
		support.assertJsonTemplate(a, "csv-empty");
	}
}
