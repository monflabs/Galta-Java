package test;

import junit.framework.TestSuite;
import test.test262.Test262OneTest;
import tests.javascript.JavaScriptStrictTestCase;

public class AdHocProbeInterpreterTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		JavaScriptStrictTestCase._ALLTESTS = true;
		JavaScriptStrictTestCase._EXECUTE_JAVATRANSPILER = false;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		JavaScriptStrictTestCase._EXECUTE_OPTIMIZED = true;

		TestSuite suite = new TestSuite();
		suite.addTestSuite(Test262OneTest.class);
		return suite;
	}
}
