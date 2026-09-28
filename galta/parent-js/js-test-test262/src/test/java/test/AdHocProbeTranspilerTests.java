package test;

import java.io.File;

import org.monflabs.tests.UnitTestSupport;

import junit.framework.TestSuite;
import test.test262.Test262OneTest;
import tests.javascript.JavaScriptStrictTestCase;

public class AdHocProbeTranspilerTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		JavaScriptStrictTestCase._ALLTESTS = true;
		JavaScriptStrictTestCase._EXECUTE_JAVATRANSPILER = true;
		JavaScriptStrictTestCase._EXECUTE_DECOMPILER = false;
		JavaScriptStrictTestCase._EXECUTE_OPTIMIZED = true;

		UnitTestSupport support = new UnitTestSupport(AdHocProbeTranspilerTests.class);
		File packageDir = new File(support.getTestJavaDirectory(),"compiled");
		if(packageDir.exists()) {
			File[] files = packageDir.listFiles();
			for(int i=0; i<files.length; i++) {
				if(files[i].getPath().endsWith(".java")) {
					files[i].delete();
				}
			}
		}

		TestSuite suite = new TestSuite();
		suite.addTestSuite(Test262OneTest.class);
		return suite;
	}
}
