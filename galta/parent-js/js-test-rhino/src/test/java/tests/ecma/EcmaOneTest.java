package tests.ecma;

import java.util.TimeZone;

import org.monflabs.util.StringFormat;


public class EcmaOneTest extends EcmaBaseTest {

	//public static final String TEST_FILE = "ecma/Date/15.9.5.37-4.js";
	public static final String TEST_FILE = "ecma/Boolean/15.6.2.js";

	public EcmaOneTest() {
	}

	public EcmaOneTest(boolean debugger) {
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

	public void testOneEcma() throws Exception {
		int totalErrors = 0;
		MAX_DISPLAY = 200; // Just to debug one test


		totalErrors += exec(getRhinoTestsDirectory(), TEST_FILE);
		
		if(totalErrors>0) {
			fail(StringFormat.format("{0} Error(s) in ECMA tests", totalErrors));
		}
	}


    //
    // Visual Debugger
    //
	public static void main(String[] args) {
		try {
			EcmaOneTest test = new EcmaOneTest(true);
			TimeZone PST = TimeZone.getTimeZone("PST");
			TimeZone.setDefault(PST);
			test.exec(test.getRhinoTestsDirectory(), TEST_FILE);
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
