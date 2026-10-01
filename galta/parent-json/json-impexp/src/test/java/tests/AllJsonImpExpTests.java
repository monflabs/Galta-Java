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
package tests;

import doc_examples.impexp.ImportExportExamples;
import junit.framework.TestSuite;
import tests.impexp.ContainerTargetTest;
import tests.impexp.EngineControlTest;
import tests.impexp.ImpExpRegressionTest;
import tests.impexp.ImpExpRobustnessTest;
import tests.impexp.EngineFailureTest;
import tests.impexp.FileLayoutTest;
import tests.impexp.FileNameHashTest;
import tests.impexp.FileNameTest;
import tests.impexp.FileSourceTest;
import tests.impexp.FileTargetTest;
import tests.impexp.JsonKeyTest;
import tests.impexp.MemorySourceTest;
import tests.impexp.MemoryTargetTest;
import tests.impexp.StaticSourceTargetTest;
import tests.impexp.StreamSourceTest;
import tests.impexp.ZipFileSourceTest;
import tests.impexp.ZipFileTargetTest;
import tests.impexp.ZipInputStreamSourceTest;

public class AllJsonImpExpTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();
		
		suite.addTestSuite(JsonKeyTest.class);

		suite.addTestSuite(FileNameTest.class);
		suite.addTestSuite(FileNameHashTest.class);
		suite.addTestSuite(FileLayoutTest.class);
		suite.addTestSuite(StaticSourceTargetTest.class);
		suite.addTestSuite(EngineFailureTest.class);
		suite.addTestSuite(EngineControlTest.class);
		suite.addTestSuite(ImpExpRegressionTest.class);
		suite.addTestSuite(ImpExpRobustnessTest.class);
		suite.addTestSuite(ContainerTargetTest.class);
		suite.addTestSuite(MemorySourceTest.class);
		suite.addTestSuite(MemoryTargetTest.class);
		suite.addTestSuite(StreamSourceTest.class);
		suite.addTestSuite(FileSourceTest.class);
		suite.addTestSuite(FileTargetTest.class);
		suite.addTestSuite(ZipFileSourceTest.class);
		suite.addTestSuite(ZipInputStreamSourceTest.class);
		suite.addTestSuite(ZipFileTargetTest.class);

		suite.addTestSuite(ImportExportExamples.class);

		return suite;
	}
}
