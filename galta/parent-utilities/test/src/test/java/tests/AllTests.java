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

import doc_examples.testing.LeakRuleExample;
import doc_examples.testing.TestingExamples;
import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;
import tests.tests.AccessorTest;
import tests.tests.JavaAccessorConcurrencyTest;
import tests.tests.ReadStringTest;
import tests.tests.SuiteGuardTest;
import tests.tests.UnitTestSupportTest;
import tests.tests.leaks.FileIOLeakTest;
import tests.tests.leaks.FileSystemAPILeakTest;
import tests.tests.leaks.ResourceTrackerTest;
import tests.tests.leaks.ZipFileLeakTest;
import org.monflabs.tests.SuiteGuard;

public class AllTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();

		suite.addTestSuite(AccessorTest.class);
		suite.addTestSuite(ReadStringTest.class);
		suite.addTestSuite(UnitTestSupportTest.class);
		suite.addTestSuite(SuiteGuardTest.class);
		suite.addTestSuite(JavaAccessorConcurrencyTest.class);
		suite.addTestSuite(ResourceTrackerTest.class);
		suite.addTestSuite(FileIOLeakTest.class);
		suite.addTestSuite(FileSystemAPILeakTest.class);
		suite.addTestSuite(ZipFileLeakTest.class);


		// Samples of docs/Utilities/Testing.md
		suite.addTestSuite(TestingExamples.class);
		suite.addTest(new JUnit4TestAdapter(LeakRuleExample.class));

		// Fails when a test class of the module is missing from its suites
		suite.addTest(SuiteGuard.newTest(AllTests.class));

		return suite;
	}

}
