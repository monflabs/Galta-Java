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

import doc_examples.jackson.JacksonExamples;
import junit.framework.TestSuite;
import tests.jackson.JacksonInteropTest;
import org.monflabs.tests.SuiteGuard;

public class AllJsonJacksonTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();

		suite.addTestSuite(JacksonInteropTest.class);

		suite.addTestSuite(JacksonExamples.class);

		// Fails when a test class of the module is missing from its suites
		suite.addTest(SuiteGuard.newTest(AllJsonJacksonTests.class));

		return suite;
	}

}
