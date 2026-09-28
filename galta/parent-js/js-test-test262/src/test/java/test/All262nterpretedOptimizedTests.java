package test;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import test.test262.Test262AllTest;
import tests.javascript.JavaScriptStrictTestCase;

public class All262nterpretedOptimizedTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new All262nterpretedOptimizedTests();
		suite.addTestSuite(Test262AllTest.class);
		return suite;
	}

	// See All262InterpretedTests's identical runTest() override doc comment.
	@Override
	public void runTest(Test test, TestResult result) {
		JavaScriptStrictTestCase._ALLTESTS = true;
		JavaScriptStrictTestCase._EXECUTE_JAVATRANSPILER = false;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		JavaScriptStrictTestCase._EXECUTE_OPTIMIZED = true;
		super.runTest(test, result);
	}
}
