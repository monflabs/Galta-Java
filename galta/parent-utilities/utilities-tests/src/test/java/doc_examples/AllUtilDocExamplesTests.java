package doc_examples;

import junit.framework.TestSuite;

import doc_examples.util.CollectionsExamples;
import doc_examples.util.DateTimeExamples;
import doc_examples.util.IOExamples;
import doc_examples.util.NumbersAndTypesExamples;
import doc_examples.util.ReflectionExamples;
import doc_examples.util.RuntimeExamples;
import doc_examples.util.StringsExamples;

/**
 * Runs every sample of the docs/Utilities pages.
 */
public class AllUtilDocExamplesTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();
		suite.addTestSuite(StringsExamples.class);
		suite.addTestSuite(NumbersAndTypesExamples.class);
		suite.addTestSuite(DateTimeExamples.class);
		suite.addTestSuite(CollectionsExamples.class);
		suite.addTestSuite(IOExamples.class);
		suite.addTestSuite(ReflectionExamples.class);
		suite.addTestSuite(RuntimeExamples.class);
		return suite;
	}
}
