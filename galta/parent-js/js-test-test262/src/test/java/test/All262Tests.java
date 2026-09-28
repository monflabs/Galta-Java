package test;

import junit.framework.TestSuite;

public class All262Tests extends TestSuite {
	
	private static boolean INTERPRETED	= true;
	private static boolean OPTIMIZED	= true;
	private static boolean COMPILED		= true;

	public static TestSuite suite() throws Exception {
		// Each All262*Tests class's own suite() sets the JavaScriptStrictTestCase
		// mode flags (_EXECUTE_JAVATRANSPILER/_EXECUTE_OPTIMIZED/...) before
		// building its Test262AllTest suite - `new All262InterpretedTests()`
		// (a plain constructor call) never reaches that static suite() factory,
		// so the flags were never actually set differently per mode: all three
		// "modes" below silently ran identically, under whatever the flags
		// last happened to be. Must call suite() directly.
		TestSuite suite = new TestSuite();

		if(INTERPRETED) {
			suite.addTest(All262InterpretedTests.suite());
		}

		if(OPTIMIZED) {
			suite.addTest(All262nterpretedOptimizedTests.suite());
		}

		if(COMPILED) {
			suite.addTest(All262TranspilerTests.suite());
		}

		return suite;
	}
}
