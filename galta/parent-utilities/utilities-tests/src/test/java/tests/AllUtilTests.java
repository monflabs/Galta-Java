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

import junit.framework.TestSuite;
import tests.collections.CacheProviderTest;
import tests.config.ConfigTest;
import tests.dependencies.DependencyEngineTest;
import tests.generators.GeneratorThreadsTest;
import tests.io.FastBufferedStreamsTest;
import tests.io.FileUtilTest;
import tests.io.StreamAdaptersTest;
import tests.model.ClassMetadataTest;
import tests.path.FilesUtilTest;
import tests.path.PathClassLoaderTest;
import tests.profiler.ProfilerConcurrencyTest;
import tests.util.BuilderAndPerformanceTest;
import tests.util.ConsoleAndExceptionTest;
import tests.util.HttpUtilsTest;
import tests.util.ScopedValueTest;
import tests.util.TextBuilderTest;
import tests.collections.LRUCacheTest;
import tests.datetime.ISO8601Test;
import tests.datetime.PeriodFormatterTest;
import tests.dependencies.CircularDependenciesTest;
import tests.dependencies.DependenciesTest;
import tests.dependencies.DependingTest;
import tests.dependencies.MixedDependenciesTest;
import tests.generators.GeneratorPerformanceTest;
import tests.generators.GeneratorTest;
import tests.io.LRUCharBufferTest;
import tests.io.ReaderInputStreamTest;
import tests.io.WriterOutputStreamTest;
import tests.iterators.IteratorsTest;
import tests.model.PojoAccessorTest;
import tests.profiler.ProfilerTest;
import tests.util.ArrayUtilTest;
import tests.util.DtoATest;
import tests.util.EnumUtilTest;
import tests.util.MiscUtilTest;
import tests.util.PathUtilTest;
import tests.util.StringFormatTest;
import tests.util.StringMatcherTest;
import tests.util.StringUtilTest;
import tests.util.TypeUtilTest;
import tests.util.VersionTest;
import tests.util._DtoALibraryTest;
import tests.util._DtoAPerformanceTest;
import tests.util.sort.QuickSortTest;

public class AllUtilTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();

		suite.addTestSuite(ArrayUtilTest.class);
		suite.addTestSuite(EnumUtilTest.class);
		suite.addTestSuite(StringUtilTest.class);
		suite.addTestSuite(StringFormatTest.class);
		suite.addTestSuite(StringMatcherTest.class);
		suite.addTestSuite(PathUtilTest.class);
		suite.addTestSuite(VersionTest.class);
		suite.addTestSuite(TypeUtilTest.class);
		suite.addTestSuite(MiscUtilTest.class);
		suite.addTestSuite(TextBuilderTest.class);
		suite.addTestSuite(ConsoleAndExceptionTest.class);
		suite.addTestSuite(ScopedValueTest.class);
		suite.addTestSuite(HttpUtilsTest.class);
		suite.addTestSuite(BuilderAndPerformanceTest.class);
		suite.addTestSuite(ConfigTest.class);
		suite.addTestSuite(FilesUtilTest.class);
		suite.addTestSuite(PathClassLoaderTest.class);

		suite.addTestSuite(PeriodFormatterTest.class);
		suite.addTestSuite(ISO8601Test.class);

		suite.addTestSuite(PojoAccessorTest.class);
		suite.addTestSuite(ClassMetadataTest.class);

		suite.addTestSuite(GeneratorTest.class);
		suite.addTestSuite(GeneratorThreadsTest.class);
		suite.addTestSuite(GeneratorPerformanceTest.class);

		suite.addTestSuite(LRUCharBufferTest.class);
		suite.addTestSuite(WriterOutputStreamTest.class);
		suite.addTestSuite(ReaderInputStreamTest.class);
		suite.addTestSuite(StreamAdaptersTest.class);
		suite.addTestSuite(FastBufferedStreamsTest.class);
		suite.addTestSuite(FileUtilTest.class);

		suite.addTestSuite(IteratorsTest.class);

		suite.addTestSuite(LRUCacheTest.class);
		suite.addTestSuite(CacheProviderTest.class);

		suite.addTestSuite(ProfilerTest.class);
		suite.addTestSuite(ProfilerConcurrencyTest.class);
		
		suite.addTestSuite(DependenciesTest.class);
		suite.addTestSuite(DependingTest.class);
		suite.addTestSuite(MixedDependenciesTest.class);
		suite.addTestSuite(CircularDependenciesTest.class);
		suite.addTestSuite(DependencyEngineTest.class);
		
		suite.addTestSuite(_DtoALibraryTest.class);
		suite.addTestSuite(_DtoAPerformanceTest.class);
		suite.addTestSuite(DtoATest.class);

		suite.addTestSuite(QuickSortTest.class);

		return suite;
	}

}
