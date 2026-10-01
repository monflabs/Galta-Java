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

import doc_examples.filesystem.FileSystemsExamples;
import junit.framework.TestSuite;
import tests.filesystem.FileFileSystemTest;
import tests.filesystem.FileSystemFixesTest;
import tests.filesystem.JdkConformanceTest;
import tests.filesystem.MemoryFileSystemTest;
import tests.filesystem.PathDelegatingFileSystemTest;
import tests.filesystem.PathFileSystemTest;
import tests.filesystem.SandboxTest;
import tests.filesystem.UnsandboxedFileFileSystemTest;
import tests.filesystem.ResourceFileSystemTest;
import tests.filesystem.ZipFileSystemTest;
import org.monflabs.tests.SuiteGuard;

public class AllFilesystemTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();
		
		suite.addTestSuite(MemoryFileSystemTest.class);
		suite.addTestSuite(PathDelegatingFileSystemTest.class);
		suite.addTestSuite(FileFileSystemTest.class);
		suite.addTestSuite(ZipFileSystemTest.class);
		suite.addTestSuite(ResourceFileSystemTest.class);
		suite.addTestSuite(PathFileSystemTest.class);
		suite.addTestSuite(SandboxTest.class);
		suite.addTestSuite(UnsandboxedFileFileSystemTest.class);
		suite.addTestSuite(JdkConformanceTest.class);
		suite.addTestSuite(FileSystemFixesTest.class);

		// Samples of docs/Utilities/FileSystems.md
		suite.addTestSuite(FileSystemsExamples.class);

		// Fails when a test class of the module is missing from its suites
		suite.addTest(SuiteGuard.newTest(AllFilesystemTests.class));

		return suite;
	}

}
