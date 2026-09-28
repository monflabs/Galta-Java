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

import junit.framework.TestSuite;

public class AllGaltaJSTests extends TestSuite {
	
	private static boolean INTERPRETED	= true;
	private static boolean COMPILED		= true;
	private static boolean OPTIMIZED	= true;
	private static boolean DECOMPILER	= true;

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();

		// Pure Java checks, independent of the execution mode
		suite.addTestSuite(tests.javascript.builtin.number.DtoAOracleTest.class);
		suite.addTestSuite(tests.javascript.regression.EngineRegressionJavaTest.class);
		suite.addTestSuite(tests.javascript.regression.EngineRegression2JavaTest.class);
		suite.addTestSuite(tests.debug.DebuggerImplTest.class);
		suite.addTestSuite(tests.debug.TranspiledDebuggerImplTest.class);
		suite.addTestSuite(tests.debug.DebugHookMechanismTest.class);
		suite.addTestSuite(tests.cdp.TranspiledCdpProtocolTest.class);
		suite.addTestSuite(tests.cdp.CdpProtocolTest.class);
		suite.addTestSuite(tests.cdp.InProcessCdpProtocolTest.class);
		suite.addTestSuite(tests.cdp.CdpSafetyTest.class);
		suite.addTestSuite(tests.debug.DebuggerSafetyTest.class);
		suite.addTestSuite(tests.javascript.regression.LibraryRegressionJavaTest.class);
		suite.addTestSuite(org.monflabs.galtajs.rt.interpreter.PICConcurrencyTest.class);
		suite.addTestSuite(tests.javascript.types.JSTypeInferenceTest.class);
		suite.addTestSuite(tests.javascript.javatranspiler.TranspilerOptimizationsTest.class);
		suite.addTestSuite(tests.javascript.optimizer.LoopCounterMathSpecializationTest.class);
		suite.addTestSuite(tests.javascript.optimizer.ConstantFoldingAndUnreachableCodeOptimizerTest.class);
		suite.addTestSuite(tests.extras.CheckPropertiesTest.class);

		if(INTERPRETED) {
			TestSuite javaSuite = new AllGaltaJSInterpreterTests();
			GaltaJSTestSuite.addStandardTests(javaSuite);
			suite.addTest(javaSuite);
		}

		if(OPTIMIZED) {
			TestSuite javaSuite = new AllGaltaJSInterpreterOptimizedTests();
			GaltaJSTestSuite.addStandardTests(javaSuite);
			suite.addTest(javaSuite);
		}

		if(COMPILED) {
			TestSuite javaSuite = new AllGaltaJSTranspilerTests();
			GaltaJSTestSuite.addStandardTests(javaSuite);
			suite.addTest(javaSuite);
		}

		if(DECOMPILER) {
			TestSuite javaSuite = new AllGaltaJSDecompiledTests();
			GaltaJSTestSuite.addStandardTests(javaSuite);
			suite.addTest(javaSuite);
		}
		
		return suite;
	}
}
