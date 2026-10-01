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

import doc_examples.jsonschema.JsonSchemaExamples;
import junit.framework.TestSuite;
import tests.schema.JsonSchemaFactoryTest;
import tests.schema.JsonSchemaTest;
import tests.schema.JsonSchemaValuesTest;
import org.monflabs.tests.SuiteGuard;

public class AllJsonSchemaTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();
		
		suite.addTestSuite(JsonSchemaTest.class);
		suite.addTestSuite(JsonSchemaFactoryTest.class);
		suite.addTestSuite(JsonSchemaValuesTest.class);

		suite.addTestSuite(JsonSchemaExamples.class);

		// Fails when a test class of the module is missing from its suites
		suite.addTest(SuiteGuard.newTest(AllJsonSchemaTests.class));

		return suite;
	}

}
