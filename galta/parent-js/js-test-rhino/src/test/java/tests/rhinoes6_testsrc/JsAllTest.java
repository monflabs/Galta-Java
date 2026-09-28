package tests.rhinoes6_testsrc;

import org.monflabs.util.StringFormat;

public class JsAllTest extends JsTestBaseTest {
	
	public JsAllTest() {
	}

	public JsAllTest(boolean debugger) {
		super(debugger);
	}

	public void testAllJsa() throws Exception {
		int totalErrors = 0;

		// Checks pre-es6 standard 
		//totalErrors += exec(getRhinoTestDirectory(), null, false);

//		totalErrors += exec(getRhinoTestDirectory(), "", false);

		totalErrors += exec(getRhinoTestDirectory(), "es6");
		totalErrors += exec(getRhinoTestDirectory(), "es2019");
		totalErrors += exec(getRhinoTestDirectory(), "es2020");
		totalErrors += exec(getRhinoTestDirectory(), "es2020");
		totalErrors += exec(getRhinoTestDirectory(), "es2023");
		totalErrors += exec(getRhinoTestDirectory(), "es2025");

//		totalErrors += exec(getRhinoTestDirectory(), "harmony");
		
		// Uses undesired Rhino extensions
		//totalErrors += exec(getRhinoTestDirectory(), "harmony");

		// Don't fail yet, until they all pass!
		if(totalErrors>0) {
			fail(StringFormat.format("{0} Error(s) in JS tests", totalErrors));
		}
    }
}
