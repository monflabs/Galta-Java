package test.test262;

import java.io.File;

import org.monflabs.util.StringFormat;

public class Test262AllTest extends Test262BaseTest {

	public Test262AllTest() {
	}

	/**
	 * Runs the TC39 test262 suite against GaltaJS.
	 *
	 * <p>Walks {@code language/} and {@code built-ins/} as whole directories
	 * (recursively, via {@link #exec(File, String)}) rather than listing every
	 * individual subdirectory by name - this is deliberately dynamic so a new
	 * test262 submodule revision that adds a directory (a newly-standardized
	 * built-in, a new language-feature subdirectory) is picked up automatically
	 * on the next run, with no source change needed here. {@code intl402/} and
	 * {@code staging/} are NOT included (Internationalization API and
	 * pre-standardization proposals, outside GaltaJS's current scope).
	 * The suite is stored in the {@code test262/} git sub-module of this Maven
	 * module; the {@link #getTest262TestDirectory()} helper resolves its
	 * {@code test/} sub-directory.
	 */
	public void testAllEcma() throws Exception {
		int totalErrors = 0;

		totalErrors += execIfExists("annexB");
		totalErrors += execIfExists("language");
		totalErrors += execIfExists("built-ins");

		if (totalErrors > 0) {
			fail(StringFormat.format("{0} Error(s) in test262 tests", totalErrors));
		}
	}

	/**
	 * Like {@link #exec(File, String)} but silently skips sub-paths that do not
	 * exist in the test262 checkout (e.g. when running against an older revision
	 * of the submodule).
	 */
	private int execIfExists(String subPath) throws Exception {
		File dir = new File(getTest262TestDirectory(), subPath);
		if (!dir.isDirectory()) {
			return 0;
		}
		return exec(getTest262TestDirectory(), subPath);
	}
}
