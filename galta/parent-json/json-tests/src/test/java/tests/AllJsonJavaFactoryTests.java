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

import org.monflabs.json.JsonFactory;
import org.monflabs.json.java.JavaJsonFactory;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

public class AllJsonJavaFactoryTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new AllJsonJavaFactoryTests();
		JsonTestSuite.addStandardTests(suite);
		return suite;
	}

	@Override
    public void runTest(Test test, TestResult result) {
		JsonFactory.set(JavaJsonFactory.instance);
        super.runTest(test, result);
    }
}
