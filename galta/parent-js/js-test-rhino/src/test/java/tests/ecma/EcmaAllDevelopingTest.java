package tests.ecma;

import org.monflabs.util.StringFormat;

public class EcmaAllDevelopingTest extends EcmaBaseTest {
	
	public EcmaAllDevelopingTest() {
	}

	public EcmaAllDevelopingTest(boolean debugger) {
		super(debugger);
	}

	@Override
	protected boolean filterFilesWithKnownErrors() {
		return true;
	}
	
	public void testAllEcma() throws Exception {
		int totalErrors = 0;
		
		totalErrors += exec(getRhinoTestsDirectory(), "ecma_3");		
		
		// Don't fail yet, until they all pass!
		if(totalErrors>0) {
			fail(StringFormat.format("{0} Error(s) in ECMA tests", totalErrors));
		}
    }
}
