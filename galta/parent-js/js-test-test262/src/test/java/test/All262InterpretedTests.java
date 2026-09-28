package test;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import test.test262.Test262AllTest;
import tests.javascript.JavaScriptStrictTestCase;

public class All262InterpretedTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new All262InterpretedTests();
		suite.addTestSuite(Test262AllTest.class);
		return suite;
	}

	// Overriding the INSTANCE method runTest() (invoked by JUnit once per
	// child test, whenever THIS instance - not just a class Maven/Surefire
	// directly targets - is run) re-applies the mode flags right before each
	// individual test, instead of once at suite-composition time - see
	// All262Tests.suite()'s own doc comment for why the latter breaks when
	// several of these suites are composed together (mirrors
	// AllGaltaJSInterpreterTests's identical pattern).
	@Override
	public void runTest(Test test, TestResult result) {
		JavaScriptStrictTestCase._ALLTESTS = true;
		JavaScriptStrictTestCase._EXECUTE_JAVATRANSPILER = false;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		JavaScriptStrictTestCase._EXECUTE_OPTIMIZED = false;
		super.runTest(test, result);
	}
}
