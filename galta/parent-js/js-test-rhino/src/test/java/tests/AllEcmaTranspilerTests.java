package tests;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import tests.ecma.EcmaAllTest;
import tests.rhino.JavaPackageTest;
import tests.rhino.RhinoShellTest;
import tests.rhino.RhinoTestCase;

public class AllEcmaTranspilerTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		// Enable that to run all the tests
		//EcmaTest.RUN_ECMA_TESTS = false;

		TestSuite suite = new TestSuite();

		suite.addTestSuite(RhinoShellTest.class);
		suite.addTestSuite(JavaPackageTest.class);
		suite.addTestSuite(EcmaAllTest.class);

		return suite;
	}
	
	@Override
    public void runTest(Test test, TestResult result) {
		RhinoTestCase._ALLTESTS = true;
		RhinoTestCase._EXECUTE_JAVATRANSPILER = true;
		RhinoTestCase._EXECUTE_DECOMPILER = false;
		RhinoTestCase._EXECUTE_OPTIMIZED = true; // Eliminate unused code
		
        super.runTest(test, result);
    }

}
