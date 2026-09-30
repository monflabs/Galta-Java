package test.test262;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpEngine;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.RegExpEngineJdkJavascript;
import org.monflabs.galtajs.rt.builtins.standard.regexp.joni.RegExpEngineJoni;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;

import org.monflabs.galtajs.test.test262.GlobalTest262Environment;

/**
 * Runs every test262 test whose relative path contains "regexp" (case-insensitive)
 * against both regex engines (JDK, Joni) and writes
 * a markdown pass/fail report to {@code target/regexp-test262-report.md}.
 *
 * The test itself passes as long as the report is produced - it does NOT fail
 * on engine-specific test failures, since surfacing those failures IS the point.
 */
public class Test262RegexpReportTest extends Test262BaseTest {

	private static final String REPORT_FILE = "regexp-test262-report.md";

	// The regex-engine matrix. Each entry: display name -> engine factory.
	private static final LinkedHashMap<String, BiFunction<JSEnvironment,RegExp,RegExpEngine>> ENGINES =
			new LinkedHashMap<>();
	static {
		ENGINES.put("JDK", RegExpEngineJdkJavascript.factory());
		ENGINES.put("Joni", RegExpEngineJoni.factory());
	}

	// The regex-engine picked up by createEnvironment() for the current single-file
	// exec() call. Rebound between engines for each file.
	private BiFunction<JSEnvironment,RegExp,RegExpEngine> currentFactory = RegExpEngineJoni.factory();

	public Test262RegexpReportTest() {
	}

	// Interpreter mode only: switching between transpiler and interpreter for
	// every combination would multiply the wall-clock cost of an already-heavy
	// report and add little signal (regex engine correctness is orthogonal to
	// how the surrounding JS is executed).
	@Override
	protected boolean isJavaTranspiler() {
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

	// exec() prints per-file progress and per-error diagnostics via Console/System.out.
	// We want the harness to remain quiet so the top-level surefire log stays readable -
	// the full picture lives in the markdown report.
	@Override
	protected boolean consoleVerbose() {
		return false;
	}

	// The point of this report is to be exhaustive: DO NOT reuse Test262BaseTest.FILTER's
	// individually-listed known-broken RegExp tests - we want to see which of them each
	// engine actually passes or fails. Only structural, non-RegExp-core filters remain:
	//   - _FIXTURE       : fixture files loaded BY tests, not standalone tests themselves
	//   - test262/harness/ : the harness sources, same reason
	//   - intl402/       : ECMA-402 Internationalization (requires Intl - out of scope)
	//   - staging/       : proposal tests that may depend on unshipped features
	@Override
	protected String[] getFILTER() {
		return new String[] {
			"_FIXTURE",
			"test262/harness/",
			"intl402/",
			"staging/",
		};
	}

	// Rebind the regex engine at environment construction time so each exec()
	// call picks up the currently-selected engine.
	@Override
	protected JSEnvironment.Builder createEnvironment() {
		return JSEnvironment.newBuilder()
				.supportFloat16Array(true)
				.regexpEngineFactory(currentFactory)
				.registerLibrary(new StandardLibrary())
				.registerLibrary(new org.monflabs.galtajs.test.test262.Test262TestLibrary());
	}

	public void testRegexpReport() throws Exception {
		File rootDir = getTest262TestDirectory();
		if (!rootDir.isDirectory()) {
			// No test262 checkout - nothing to report, don't fail the module.
			return;
		}

		List<String> allPaths = collectRegexpTests(rootDir);
		String[] filter = getFILTER();

		// Apply the structural filter up front so skipped tests never enter the
		// results map, the per-engine counts, or the report - they're simply not
		// reported at all.
		List<String> relPaths = new ArrayList<>(allPaths.size());
		for (String rel : allPaths) {
			if (!matchesFilter(rel, filter)) {
				relPaths.add(rel);
			}
		}
		relPaths.sort(Comparator.naturalOrder());

		// engineName -> (relativePath -> outcome)
		LinkedHashMap<String, LinkedHashMap<String, Outcome>> results = new LinkedHashMap<>();
		for (String engineName : ENGINES.keySet()) {
			results.put(engineName, new LinkedHashMap<>());
		}

		long overallStart = System.currentTimeMillis();

		for (Map.Entry<String, BiFunction<JSEnvironment,RegExp,RegExpEngine>> e : ENGINES.entrySet()) {
			String engineName = e.getKey();
			currentFactory = e.getValue();

			LinkedHashMap<String, Outcome> perFile = results.get(engineName);
			long engineStart = System.currentTimeMillis();
			int passed = 0, failed = 0;

			for (String rel : relPaths) {
				Outcome outcome = runOne(rootDir, rel);
				if (outcome == Outcome.PASSED) {
					passed++;
				} else {
					failed++;
				}
				perFile.put(rel, outcome);
			}

			long engineMs = System.currentTimeMillis() - engineStart;
			// Small heartbeat so the run is visible in the surefire log without flooding it.
			System.out.println(String.format(
					"[Test262RegexpReport] engine=%s passed=%d failed=%d time=%dms",
					engineName, passed, failed, engineMs));
		}

		long overallMs = System.currentTimeMillis() - overallStart;

		File reportFile = new File(support.getTargetDirectory(), REPORT_FILE);
		writeReport(reportFile, rootDir, relPaths, results, overallMs);
		System.out.println("[Test262RegexpReport] wrote " + reportFile.getAbsolutePath());
	}

	// Runs a single test file with the currently-selected engine and returns whether
	// it passed. exec() returns the file's error count: 0 = pass, >0 = fail.
	// System.out/err are silenced for the duration so the surefire log stays clean.
	private Outcome runOne(File rootDir, String relPath) {
		PrintStream savedOut = System.out;
		PrintStream savedErr = System.err;
		PrintStream nullOut = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
		try {
			System.setOut(nullOut);
			System.setErr(nullOut);
			int errors = exec(rootDir, relPath);
			return errors == 0 ? Outcome.PASSED : Outcome.FAILED;
		} catch (Throwable t) {
			return Outcome.FAILED;
		} finally {
			System.setOut(savedOut);
			System.setErr(savedErr);
		}
	}

	private static boolean matchesFilter(String relPath, String[] filter) {
		if (filter == null) {
			return false;
		}
		// The exec() harness matches FILTER entries against the path form with a
		// leading '/' (see BaseTestSuiteTest.execFile: relativePath = childPath
		// stripped of parentPath). Reproduce the same comparison here so our
		// skip-vs-run classification matches what exec() actually does.
		String normalized = "/" + relPath;
		for (String f : filter) {
			if (normalized.indexOf(f) >= 0) {
				return true;
			}
		}
		return false;
	}

	private static List<String> collectRegexpTests(File rootDir) throws Exception {
		Path root = rootDir.toPath();
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.walk(root)) {
			s.filter(Files::isRegularFile)
			 .filter(p -> p.toString().endsWith(".js"))
			 .forEach(p -> {
				String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
				if (containsIgnoreCase(rel, "regexp") && !rel.endsWith("_FIXTURE.js")) {
					out.add(rel);
				}
			 });
		}
		return out;
	}

	private static boolean containsIgnoreCase(String haystack, String needle) {
		final int hl = haystack.length(), nl = needle.length();
		if (nl == 0) return true;
		if (nl > hl) return false;
		char first = Character.toLowerCase(needle.charAt(0));
		outer:
		for (int i = 0; i <= hl - nl; i++) {
			if (Character.toLowerCase(haystack.charAt(i)) != first) continue;
			for (int j = 1; j < nl; j++) {
				if (Character.toLowerCase(haystack.charAt(i + j)) != Character.toLowerCase(needle.charAt(j))) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	private static void writeReport(File reportFile, File rootDir, List<String> relPaths,
			LinkedHashMap<String, LinkedHashMap<String, Outcome>> results, long overallMs) throws Exception {
		reportFile.getParentFile().mkdirs();

		StringBuilder md = new StringBuilder(1 << 20);
		md.append("# test262 RegExp compatibility report\n\n");
		md.append("Root: `").append(rootDir.getAbsolutePath()).append("`  \n");
		md.append("Tests executed (path contains `regexp`, case-insensitive; ")
		  .append("excluding intl402/, staging/, harness/, fixture files): ")
		  .append(relPaths.size()).append("  \n");
		md.append("Total wall time: ").append(overallMs).append(" ms  \n\n");

		// Per-engine totals table
		md.append("## Summary\n\n");
		md.append("| Engine | Passed | Failed |\n");
		md.append("|---|---:|---:|\n");
		for (String engineName : results.keySet()) {
			int p = 0, f = 0;
			for (Outcome o : results.get(engineName).values()) {
				if (o == Outcome.PASSED) p++;
				else if (o == Outcome.FAILED) f++;
			}
			md.append("| ").append(engineName)
			  .append(" | ").append(p)
			  .append(" | ").append(f)
			  .append(" |\n");
		}
		md.append("\n");

		// Full per-file matrix.
		md.append("## Per-file results\n\n");
		md.append("Legend: ").append(Outcome.PASSED.label).append(" = passed, ")
		  .append(Outcome.FAILED.label).append(" = failed.\n\n");
		md.append("| Test");
		for (String engineName : results.keySet()) {
			md.append(" | ").append(engineName);
		}
		md.append(" |\n");
		md.append("|---");
		for (int i = 0; i < results.size(); i++) {
			md.append("|:---:");
		}
		md.append("|\n");

		for (String rel : relPaths) {
			md.append("| `").append(rel).append("`");
			for (String engineName : results.keySet()) {
				Outcome o = results.get(engineName).get(rel);
				md.append(" | ").append(o == null ? " " : o.label);
			}
			md.append(" |\n");
		}

		Files.writeString(reportFile.toPath(), md.toString(), StandardCharsets.UTF_8);
	}

	private enum Outcome {
		// GitHub / most markdown renderers display these as a green check and a red cross.
		PASSED("✅"),
		FAILED("❌");
		final String label;
		Outcome(String label) { this.label = label; }
	}
}
