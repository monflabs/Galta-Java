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
package tests;

import doc_examples.csv.CsvExamples;
import junit.framework.TestSuite;
import tests.impexp.CsvRoundTripTest;
import tests.impexp.CsvSourceTest;
import tests.impexp.CsvTargetTest;

public class AllJsonImpExpCsvTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();

		suite.addTestSuite(CsvSourceTest.class);
		suite.addTestSuite(CsvTargetTest.class);
		suite.addTestSuite(CsvRoundTripTest.class);
		suite.addTestSuite(CsvExamples.class);

		return suite;
	}

}
