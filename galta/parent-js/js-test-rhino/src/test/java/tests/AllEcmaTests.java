package tests;

import junit.framework.TestSuite;
import tests.ecma.EcmaAllTest;
import tests.rhino.JavaPackageTest;
import tests.rhino.RhinoShellTest;
import tests.rhinoes6_testsrc.JsAllTest;

public class AllEcmaTests extends TestSuite {
	
	private static boolean INTERPRETED	= true;
	private static boolean OPTIMIZED	= true;
	private static boolean COMPILED		= true;

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();

		if(INTERPRETED) {
			TestSuite javaSuite = new AllEcmaInterpretedTests();
			suite.addTestSuite(RhinoShellTest.class);
			suite.addTestSuite(JavaPackageTest.class);
			suite.addTestSuite(EcmaAllTest.class);
			suite.addTestSuite(JsAllTest.class);
			suite.addTest(javaSuite);
		}

		if(OPTIMIZED) {
			TestSuite javaSuite = new AllEcmaInterpretedOptimizedTests();
			suite.addTestSuite(RhinoShellTest.class);
			suite.addTestSuite(JavaPackageTest.class);
			suite.addTestSuite(EcmaAllTest.class);
			suite.addTestSuite(JsAllTest.class);
			suite.addTest(javaSuite);
		}

		if(COMPILED) {
			TestSuite javaSuite = new AllEcmaTranspilerTests();
			suite.addTestSuite(RhinoShellTest.class);
			suite.addTestSuite(JavaPackageTest.class);
			suite.addTestSuite(EcmaAllTest.class);
			suite.addTestSuite(JsAllTest.class);
			suite.addTest(javaSuite);
		}
		
		return suite;
	}
}
