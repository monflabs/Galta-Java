package doc_examples.testing;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.tests.JavaAccessor;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.tests.leaks.ResourceLeakAgent;
import org.monflabs.tests.leaks.ResourceTracker;

/**
 * Samples for docs/Utilities/Testing.md.
 */
public class TestingExamples extends __BaseTestCase {

	public void testProjectDirectories() {
		// Everything is relative to the working directory, i.e. the module being tested
		File root = support.getProjectRoot();
		assertEquals(new File(System.getProperty("user.dir")), root);
		assertEquals(new File(root, "src/test/java"), support.getTestJavaDirectory());
		assertEquals(new File(root, "target"), support.getTargetDirectory());

		// A scratch folder under target/temp, created and emptied on demand
		File work = support.getTargetTempDirectory("doc-examples", true);
		assertEquals(new File(root, "target/temp/doc-examples"), work);
		assertTrue(work.isDirectory());
		assertEquals(0, work.list().length);
	}

	public void testClassResources() {
		// Resolved like Class.getResourceAsStream(): relative to the test class package
		assertEquals("Welcome!\n", support.loadClassResourceText("welcome.txt"));
		assertNull(support.loadClassResourceText("missing.txt", true));
	}

	public void testTextTemplate() {
		String report = "Total: 3\nFailed: 0\n";
		// Compares with tests/doc_examples/testing/TestingExamples/report.txt (line breaks normalized)
		// created from the result by a run with -Dmonflabs.tests.saveTemplates=missing
		support.assertTextResult(report, "report.txt");

		File template = support.resourceFile(getClass(), "report.txt", false);
		assertEquals(new File(support.getProjectRoot(), "tests/doc_examples/testing/TestingExamples/report.txt"), template);
		assertTrue(template.exists());
	}

	public void testJsonTemplate() {
		JsonObject person = JsonObject.of("name", "Ada", "languages", JsonArray.of("en", "fr"));
		// Compares with tests/doc_examples/testing/TestingExamples/person.json
		support.assertJsonTemplate(person, "person.json");

		// Properties are sorted before comparing: their order does not matter
		JsonObject reordered = JsonObject.of("languages", JsonArray.of("en", "fr"), "name", "Ada");
		assertTrue(support.checkJsonTemplate(reordered, "person.json"));

		// A String is parsed as JSON first
		assertTrue(support.checkJsonTemplate("{\"name\":\"Ada\",\"languages\":[\"en\",\"fr\"]}", "person.json"));
	}

	public void testJsonEquals() {
		support.assertJsonEquals("{\"a\":1,\"b\":[true,null]}", JsonObject.of("b", JsonArray.of(true, null), "a", 1));
		assertFalse(support.isJsonEquals("{\"a\":1}", "{\"a\":2}"));
		assertTrue(support.isNormalizedTextEquals("a\nb", "a\r\nb"));
	}

	static class Counter {
		private int count = 41;
		private int increment(int by) { return count += by; }
	}

	public void testPrivateAccess() {
		JavaAccessor acc = support.getObjectAccessor(new Counter());
		assertEquals(41, acc.getInt("count"));
		assertEquals(42, acc.callInt("increment", 1));
		assertEquals(42, acc.getInt("count"));
	}

	public void testLeakTracking() throws Exception {
		File file = new File(support.getTargetTempDirectory("doc-examples-leaks", true), "data.txt");
		Files.writeString(file.toPath(), "data");

		ResourceLeakAgent.install();                 // once per JVM; instruments JDK classes
		ResourceTracker tracker = ResourceTracker.getInstance();
		tracker.startTracking();
		try {
			FileInputStream in = new FileInputStream(file);
			List<ResourceTracker.LeakInfo> leaks = tracker.detectLeaks();
			assertEquals(1, leaks.size());
			assertEquals("FileInputStream", leaks.get(0).getAllocationInfo().getResourceType());
			assertTrue(leaks.get(0).getAllocationInfo().getContext().endsWith("data.txt"));

			in.close();
			assertTrue(tracker.detectLeaks().isEmpty());
		} finally {
			tracker.stopTracking();
		}
	}

	public void testRuleFailsLeakingTest() {
		org.junit.runner.Result result = org.junit.runner.JUnitCore.runClasses(LeakRuleExample.Leaking.class);
		assertEquals(1, result.getFailureCount());
		String message = result.getFailures().get(0).getMessage();
		assertTrue(message.contains("RESOURCE LEAK DETECTION FAILED!"));
		assertTrue(message.contains("Detected 1 leaked resource(s)"));
		assertTrue(message.contains("Resource Type: FileChannelImpl"));

		// The tracker is a JVM-wide singleton: clear what the nested run left behind,
		// or this test's own leak check (in __BaseTestCase.tearDown) reports it too
		ResourceTracker.getInstance().startTracking();
	}

	/** A test case with leak tracking on: installing the agent is enough. */
	public static class LeakyCase extends __BaseTestCase {
		static {
			ResourceLeakAgent.install();
		}

		private File file;

		@Override
		public void setUp() throws Exception {
			file = new File(support.getTargetTempDirectory("doc-examples-leaks", true), "leaky.txt");
			Files.writeString(file.toPath(), "data");
			super.setUp();   // starts tracking: create fixtures before, or they are tracked too
		}

		public void testForgetsToClose() throws Exception {
			new FileInputStream(file).read();   // never closed
		}
	}

	public void testBaseTestCaseChecksLeaks() {
		LeakyCase leaky = new LeakyCase();
		leaky.setName("testForgetsToClose");
		junit.framework.AssertionFailedError e = org.junit.Assert.assertThrows(junit.framework.AssertionFailedError.class, leaky::runBare);
		assertTrue(e.getMessage().contains("Resource leaks detected:"));
		assertTrue(e.getMessage().contains("Resource Type: FileInputStream"));

		ResourceTracker.getInstance().startTracking();   // clear the nested run's leftovers
	}
}
