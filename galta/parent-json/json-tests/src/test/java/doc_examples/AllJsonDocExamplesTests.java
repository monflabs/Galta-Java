package doc_examples;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.java.JavaJsonFactory;

import doc_examples.json.CollectionsExamples;
import doc_examples.json.GettingStartedExamples;
import doc_examples.json.JsonPathExamples;
import doc_examples.json.ParsingExamples;
import doc_examples.json.PointersExamples;
import doc_examples.json.SchemaExamples;
import doc_examples.json.ValuesExamples;
import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

/**
 * Runs the samples of the GaltaJSON documentation (docs/GaltaJSON).
 * 
 * Every code snippet on those pages is a test method of one of these classes,
 * so a sample that stops compiling or stops producing the documented result
 * fails the build.
 * The samples document the default factory, so it is (re)set before each test.
 */
public class AllJsonDocExamplesTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new AllJsonDocExamplesTests();
		suite.addTestSuite(GettingStartedExamples.class);
		suite.addTestSuite(ValuesExamples.class);
		suite.addTestSuite(ParsingExamples.class);
		suite.addTestSuite(JsonPathExamples.class);
		suite.addTestSuite(PointersExamples.class);
		suite.addTestSuite(CollectionsExamples.class);
		suite.addTestSuite(SchemaExamples.class);
		return suite;
	}

	@Override
	public void runTest(Test test, TestResult result) {
		JsonFactory.set(JavaJsonFactory.instance);
		super.runTest(test, result);
	}
}
