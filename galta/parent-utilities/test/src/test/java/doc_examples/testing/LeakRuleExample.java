package doc_examples.testing;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.monflabs.tests.SuiteGuard;
import org.monflabs.tests.leaks.ResourceLeakRule;

/**
 * Sample for docs/Utilities/Testing.md: the JUnit 4 rule.
 */
public class LeakRuleExample {

	@Rule
	public ResourceLeakRule leaks = new ResourceLeakRule();   // installs the agent on first use

	@Test
	public void readsAndCloses() throws Exception {
		Path file = Files.createTempFile("leak-rule", ".txt");
		try (var in = Files.newInputStream(file)) {   // closed: the rule is satisfied
			in.read();
		}
		new File(file.toString()).delete();
	}

	/** Run by TestingExamples.testRuleFailsLeakingTest, not by the suite: it leaks on purpose. */
	@SuiteGuard.NotInSuite("leaks on purpose: run by TestingExamples.testRuleFailsLeakingTest")
	public static class Leaking {
		/** The file and stream left open, cleaned up by TestingExamples after the run. */
		static Path file;
		static java.io.InputStream leaked;

		@Rule
		public ResourceLeakRule leaks = new ResourceLeakRule();

		@Test
		public void forgetsToClose() throws Exception {
			file = Files.createTempFile(Files.createDirectories(Path.of("target", "temp")), "leak-rule", ".txt");
			leaked = Files.newInputStream(file);
			leaked.read();   // never closed
		}
	}
}
