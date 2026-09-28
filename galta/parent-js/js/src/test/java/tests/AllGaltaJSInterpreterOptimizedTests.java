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

import java.io.OutputStream;
import java.io.PrintStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.monflabs.util.Console;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import tests.javascript.JavaScriptStrictTestCase;

public class AllGaltaJSInterpreterOptimizedTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new AllGaltaJSInterpreterOptimizedTests();
		GaltaJSTestSuite.addStandardTests(suite);
		return suite;
	}

    static StringBuilder traceBuilder;
    static PrintStream traceStream;

    @BeforeClass
    public static void setUp() {
        traceBuilder = new StringBuilder();
        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) {
                // Append the character to the StringBuilder
            	traceBuilder.append((char) b);
            }
        };
        traceStream = new PrintStream(outputStream);
    }

    @AfterClass
    public static void tearDown() {
    	traceStream.flush();
    	Console.log("\n\n\n");
    	Console.log("***********************************************************************");
    	Console.log("************************** Optimizations ******************************");
    	Console.log(traceBuilder.toString());
    }
    
    // Looks like the annotations above don't work??
	@Override
    public void run(TestResult result) {
		setUp();
		super.run(result);
		tearDown();
    }

    
	@Override
    public void runTest(Test test, TestResult result) {
		JavaScriptStrictTestCase._ALLTESTS = true;
		JavaScriptStrictTestCase._EXECUTE_JAVATRANSPILER = false;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		JavaScriptStrictTestCase._EXECUTE_OPTIMIZED = true;
		JavaScriptStrictTestCase._OPTIMIZER_TRACE_STREAM = traceStream;
    	traceStream.println("\n--------------------------------- "+((TestSuite)test).getName());
        super.runTest(test, result);
    }
}
