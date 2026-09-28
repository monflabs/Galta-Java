package tests.rhinoes6_testsrc;

import java.util.TimeZone;

import org.monflabs.util.StringFormat;


public class JsTestOneTest extends JsTestBaseTest {

	//public static final String TEST_FILE = "es6/collection-iterator.js";
	public static final String TEST_FILE = "es6/symbols.js";

	public JsTestOneTest() {
	}

	public JsTestOneTest(boolean debugger) {
		super(debugger);
	}

	// We can enable these for individual tests, but we don't want to enable them for the entire test suite
	@Override
	protected boolean isTranspilerSaveClass() {
		return false;
	}
	@Override
	protected boolean isInterpreterSaveClass() {
		return false;
	}

	@Override
	protected boolean isJavaTranspiler() {
		if(_ALLTESTS) {
			return super.isJavaTranspiler();
		}
		return false;
	}
	@Override
	protected boolean consoleShowPassed() {
		return false;
	}

	
	//
	// JUnit test
	//

	public void testOneJs() throws Exception {
		int totalErrors = 0;

		totalErrors += exec(getRhinoTestDirectory(), TEST_FILE);
		
		if(totalErrors>0) {
			fail(StringFormat.format("{0} Error(s) in ECMA tests", totalErrors));
		}
	}


    //
    // Visual Debugger
    //
	public static void main(String[] args) {
		try {
			JsTestOneTest test = new JsTestOneTest(true);
			TimeZone PST = TimeZone.getTimeZone("PST");
			TimeZone.setDefault(PST);
			test.exec(test.getRhinoTestDirectory(), TEST_FILE);
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
