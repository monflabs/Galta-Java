package tests.ecma;

import org.monflabs.util.StringFormat;

public class EcmaAllTest extends EcmaBaseTest {
	
	public EcmaAllTest() {
	}

	public EcmaAllTest(boolean debugger) {
		super(debugger);
	}

	public void testAllEcma() throws Exception {
		int totalErrors = 0;
		
		totalErrors += exec(getRhinoTestsDirectory(), "ecma");
		totalErrors += exec(getRhinoTestsDirectory(), "ecma_2");
		//totalErrors += exec(getRhinoTestDirectory(), "ecma_3");
		//totalErrors += exec(getRhinoTestDirectory(), "ecma_3_1");
				
		// Don't fail yet, until they all pass!
		if(totalErrors>0) {
			fail(StringFormat.format("{0} Error(s) in ECMA tests", totalErrors));
		}
    }
}
