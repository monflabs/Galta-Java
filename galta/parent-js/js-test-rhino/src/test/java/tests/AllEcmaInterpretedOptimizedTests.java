package tests;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import tests.ecma.EcmaAllTest;
import tests.javascript.JavaScriptStrictTestCase;
import tests.rhino.JavaPackageTest;
import tests.rhino.RhinoShellTest;
import tests.rhino.RhinoTestCase;

public class AllEcmaInterpretedOptimizedTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		// Enable that to run all the tests
		//EcmaTest.RUN_ECMA_TESTS = false;
		RhinoTestCase._ALLTESTS = true;

		TestSuite suite = new TestSuite();

		suite.addTestSuite(RhinoShellTest.class);
		suite.addTestSuite(JavaPackageTest.class);
		suite.addTestSuite(EcmaAllTest.class);

		return suite;
	}
	
	@Override
    public void runTest(Test test, TestResult result) {
		RhinoTestCase._ALLTESTS = true;
		RhinoTestCase._EXECUTE_JAVATRANSPILER = false;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		RhinoTestCase._EXECUTE_OPTIMIZED = true;
		
        super.runTest(test, result);
    }
}