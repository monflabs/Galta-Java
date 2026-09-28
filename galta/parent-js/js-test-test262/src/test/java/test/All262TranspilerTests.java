package test;

import java.io.File;

import org.monflabs.tests.UnitTestSupport;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import test.test262.Test262AllTest;
import tests.javascript.JavaScriptStrictTestCase;

public class All262TranspilerTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		// Stale generated sources from a previous run sit under src/test/java/compiled/
		// and get picked up by Maven's OWN testCompile step on the NEXT invocation
		// (before any test even runs) - see AllGaltaJSTranspilerTests for the same
		// pattern. Clear them so a leftover/broken generated file can't fail the build.
		UnitTestSupport support = new UnitTestSupport(All262TranspilerTests.class);
		File packageDir = new File(support.getTestJavaDirectory(),"compiled");
		if(packageDir.exists()) {
			File[] files = packageDir.listFiles();
			for(int i=0; i<files.length; i++) {
				if(files[i].getPath().endsWith(".java")) {
					if(!files[i].delete()) {
						System.out.println("Cannot delete file: "+files[i].getAbsolutePath());
					}
				}
			}
		}

		TestSuite suite = new All262TranspilerTests();
		suite.addTestSuite(Test262AllTest.class);
		return suite;
	}

	// See All262InterpretedTests's identical runTest() override doc comment.
	@Override
	public void runTest(Test test, TestResult result) {
		JavaScriptStrictTestCase._ALLTESTS = true;
		JavaScriptStrictTestCase._EXECUTE_JAVATRANSPILER = true;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		JavaScriptStrictTestCase._EXECUTE_OPTIMIZED = true; // Eliminate unused code
		super.runTest(test, result);
	}
}
