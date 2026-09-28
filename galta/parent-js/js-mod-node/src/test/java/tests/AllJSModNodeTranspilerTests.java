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

import java.io.File;

import org.monflabs.tests.UnitTestSupport;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import tests.javascript.JavaScriptStrictTestCase;

public class AllJSModNodeTranspilerTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		UnitTestSupport support = new UnitTestSupport(AllJSModNodeTranspilerTests.class);
		File packageDir = new File(support.getTestJavaDirectory(),"compiled");
		if(packageDir.exists()) {
			File[] files = packageDir.listFiles();
			for(int i=0; i<files.length; i++) {
				String path = files[i].getPath();
				if(path.endsWith(".java")) {
					boolean b = files[i].delete();
					if(!b) {
						System.out.println("Cannot delete file: "+files[i].getAbsolutePath());
					}
				}
			}
		}
		
		TestSuite suite = new AllJSModNodeTranspilerTests();
		JSModNodeTestSuite.addStandardTests(suite);
		return suite;
	}

	@Override
    public void runTest(Test test, TestResult result) {
		JavaScriptStrictTestCase._ALLTESTS = true;
		JavaScriptStrictTestCase._EXECUTE_JAVATRANSPILER = true;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		JavaScriptStrictTestCase._EXECUTE_OPTIMIZED = false;
        super.runTest(test, result);
    }
}
