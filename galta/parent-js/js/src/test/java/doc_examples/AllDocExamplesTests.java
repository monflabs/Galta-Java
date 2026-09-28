/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package doc_examples;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Runs every documentation sample. The samples are referenced from the
 * pages under {@code docs/GaltaJS}; keeping them in this suite (which the
 * surefire configuration includes) guarantees the documented code keeps
 * working.
 */
public class AllDocExamplesTests extends TestSuite {

	public static Test suite() {
		TestSuite suite = new TestSuite("GaltaJS documentation samples");
		// User's guide
		suite.addTestSuite(CreateEnvironment.class);
		suite.addTestSuite(GettingStartedExamples.class);
		suite.addTestSuite(ConfigurationExamples.class);
		suite.addTestSuite(ExecutingCodeExamples.class);
		suite.addTestSuite(TranspilerExamples.class);
		suite.addTestSuite(ValuesExamples.class);
		suite.addTestSuite(JavaInteropExamples.class);
		suite.addTestSuite(AsyncExamples.class);
		suite.addTestSuite(ModulesExamples.class);
		suite.addTestSuite(ErrorsExamples.class);
		suite.addTestSuite(RegExpExamples.class);
		suite.addTestSuite(PreprocessorExamples.class);
		suite.addTestSuite(DebuggerExamples.class);
		// Extending the engine
		suite.addTestSuite(LibrariesExamples.class);
		suite.addTestSuite(AccessorsExamples.class);
		suite.addTestSuite(BundledLibrariesExamples.class);
		// Extensions
		suite.addTestSuite(NumbersExamples.class);
		suite.addTestSuite(DecimalExamples.class);
		suite.addTestSuite(SequencesExamples.class);
		suite.addTestSuite(JsonPathExamples.class);
		suite.addTestSuite(TypeHintsExamples.class);
		suite.addTestSuite(SyntaxExtensionsExamples.class);
		suite.addTestSuite(JavaTypesExamples.class);
		return suite;
	}
}
