package test.test262;

import java.util.TimeZone;

import org.monflabs.util.StringFormat;


public class Test262OneTest extends Test262BaseTest {

	// Comma-separated test262 sub-paths, overridable with -Dtest262.path=language/statements/switch,built-ins/Map
	public static final String TEST_FILE = System.getProperty("test262.path", "built-ins/String");

	public Test262OneTest() {
	}

	public Test262OneTest(boolean debugger) {
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
	@Override
	protected boolean stopOnFirstErrors() {
		return false;
	}

	public void testOneJs() throws Exception {
		int totalErrors = 0;

		for(String path: TEST_FILE.split(",")) {
			totalErrors += exec(getTest262TestDirectory(), path.trim());
		}
		
		if(totalErrors>0) {
			fail(StringFormat.format("{0} Error(s) in Test 262 tests", totalErrors));
		}
	}

	public static void main(String[] args) {
		try {
			Test262OneTest test = new Test262OneTest(true);
			TimeZone PST = TimeZone.getTimeZone("PST");
			TimeZone.setDefault(PST);
			test.exec(test.getTest262TestDirectory(), TEST_FILE);
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
