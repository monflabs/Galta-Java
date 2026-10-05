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
import tests.json.JSONSimpleStrictParserTest;
import tests.json.JSONTestSuiteTest;
import tests.json.array.ArrayAsListTest;
import tests.json.array.ArrayIndexTest;
import tests.json.array.ArrayTest;
import tests.json.factory.BigValueTest;
import tests.json.factory.CloneTest;
import tests.json.factory.FloatTest;
import tests.json.factory.InvalidTest;
import tests.json.factory.JavaFactoryCheckedTest;
import tests.json.factory.JavaFactoryOptionsTest;
import tests.json.factory.Json5ParserTest;
import tests.json.factory.JsonParserTest;
import tests.json.factory.JsonStringifierTest;
import tests.json.factory.JsonStringifyTest;
import tests.json.factory.KeywordTest;
import tests.json.factory.NumberTest;
import tests.json.factory.StringParsingTest;
import tests.json.factory._CurrentFactoryTest;
import tests.json.jsonpath.JsonPathLikeTest;
import tests.json.jsonpath.LetfReferenceTest;
import tests.json.jsonpath.SimpleJsonPathCompilerTest;
import tests.json.jsonpath.SimpleJsonPathExpressionTest;
import tests.json.jsonpath.SimpleJsonPathReadTest;
import tests.json.jsonpath.SimpleJsonPathWriteTest;
import tests.json.jsonpointer.JsonPointerTest;
import tests.json.jsonreference.JsonReferenceTest;
import tests.json.regression.CoreRegressionTest;
import tests.json.jsonschema.JsonSchemaTest;
import tests.json.jsonvalues.MiscellaneousTest;
import tests.json.navigator.WorldCupQueryTest;
import tests.json.object.ObjectAsMapTest;
import tests.json.object.ObjectPropertyTest;
import tests.json.object.ObjectTest;
import tests.json.serializers.SerializersTest;
import tests.json.streams.ArrayStreamsTest;
import tests.json.streams.CsvStreamsTest;
import tests.json.util.JsonDateTimeTest;
import tests.json.util.JsonUtilTest;
import tests.json.yaml.YamlTest;

public class JsonTestSuite  {

	public static TestSuite addStandardTests(TestSuite suite ) throws Exception {
		// array
		suite.addTestSuite(ArrayAsListTest.class);
		suite.addTestSuite(ArrayIndexTest.class);
		suite.addTestSuite(ArrayTest.class);

		// arrayproto
		suite.addTestSuite(tests.json.arrayproto.AllMatchTest.class);
		suite.addTestSuite(tests.json.arrayproto.AnyMatchTest.class);
		suite.addTestSuite(tests.json.arrayproto.DistinctTest.class);
		suite.addTestSuite(tests.json.arrayproto.DropWhileTest.class);
		suite.addTestSuite(tests.json.arrayproto.FilterTest.class);
		suite.addTestSuite(tests.json.arrayproto.FindTest.class);
		suite.addTestSuite(tests.json.arrayproto.FlatTest.class);
		suite.addTestSuite(tests.json.arrayproto.ForEachTest.class);
		suite.addTestSuite(tests.json.arrayproto.JoinTest.class);
		suite.addTestSuite(tests.json.arrayproto.LimitTest.class);
		suite.addTestSuite(tests.json.arrayproto.MapTest.class);
		suite.addTestSuite(tests.json.arrayproto.MaxTest.class);
		suite.addTestSuite(tests.json.arrayproto.MinTest.class);
		suite.addTestSuite(tests.json.arrayproto.NoneMatchTest.class);
		suite.addTestSuite(tests.json.arrayproto.PeekTest.class);
		suite.addTestSuite(tests.json.arrayproto.ProcessTest.class);
		suite.addTestSuite(tests.json.arrayproto.ReduceTest.class);
		suite.addTestSuite(tests.json.arrayproto.RemoveTest.class);
		suite.addTestSuite(tests.json.arrayproto.SkipTest.class);
		suite.addTestSuite(tests.json.arrayproto.SkipLimitTest.class);
		suite.addTestSuite(tests.json.arrayproto.SliceTest.class);
		suite.addTestSuite(tests.json.arrayproto.SortedTest.class);
		suite.addTestSuite(tests.json.arrayproto.TakeWhileTest.class);
		
		// factory
		suite.addTestSuite(_CurrentFactoryTest.class);
		suite.addTestSuite(BigValueTest.class);
		suite.addTestSuite(CloneTest.class);
		suite.addTestSuite(FloatTest.class);
		suite.addTestSuite(InvalidTest.class);
		suite.addTestSuite(JavaFactoryCheckedTest.class);
		suite.addTestSuite(JavaFactoryOptionsTest.class);
		suite.addTestSuite(JsonParserTest.class);
		suite.addTestSuite(Json5ParserTest.class);
		suite.addTestSuite(JsonStringifierTest.class);
		suite.addTestSuite(JsonStringifyTest.class);
		suite.addTestSuite(KeywordTest.class);
		suite.addTestSuite(NumberTest.class);
		suite.addTestSuite(StringParsingTest.class);
		suite.addTestSuite(tests.json.factory.ParserEdgeCasesTest.class);
		suite.addTestSuite(tests.json.factory.NumberParsingTest.class);
		suite.addTestSuite(tests.json.factory.StringifierOptionsTest.class);
		suite.addTestSuite(tests.json.factory.JsonTextHardeningTest.class);
				
		// jsonpath
		suite.addTestSuite(JsonPathLikeTest.class);
		suite.addTestSuite(MiscellaneousTest.class);
		suite.addTestSuite(LetfReferenceTest.class);
		suite.addTestSuite(SimpleJsonPathExpressionTest.class);
		suite.addTestSuite(SimpleJsonPathCompilerTest.class);
		suite.addTestSuite(SimpleJsonPathReadTest.class);
		suite.addTestSuite(SimpleJsonPathWriteTest.class);
		suite.addTestSuite(tests.json.jsonpath.JsonPathSpecTest.class);
		suite.addTestSuite(tests.json.jsonpath.JsonPathRfc9535Test.class);

		// jsonpointer
		suite.addTestSuite(JsonPointerTest.class);
		suite.addTestSuite(tests.json.jsonpointer.JsonPointerRfcTest.class);
		suite.addTestSuite(tests.json.jsonpointer.JsonPointerSafetyTest.class);

		// jsonreference
		suite.addTestSuite(JsonReferenceTest.class);
		suite.addTestSuite(tests.json.jsonreference.JsonReferenceResolveTest.class);
		suite.addTestSuite(tests.json.jsonreference.JsonReferenceScopeTest.class);
		suite.addTestSuite(CoreRegressionTest.class);
		suite.addTestSuite(tests.json.regression.CoreModelFixesTest.class);

		// jsonschema
		suite.addTestSuite(JsonSchemaTest.class);
		suite.addTestSuite(tests.json.jsonschema.SchemaNodeTest.class);

		// jsonvalues
		suite.addTestSuite(tests.json.jsonvalues.KeySetTest.class);
		suite.addTestSuite(tests.json.jsonvalues.AccessIndexTest.class);
		suite.addTestSuite(tests.json.jsonvalues.AccessPropertyTest.class);
		suite.addTestSuite(tests.json.jsonvalues.CompareEqTest.class);
		suite.addTestSuite(tests.json.jsonvalues.CompareGtTest.class);
		suite.addTestSuite(tests.json.jsonvalues.CompareLtTest.class);
		suite.addTestSuite(tests.json.jsonvalues.CompareMatchesTest.class);
		suite.addTestSuite(tests.json.jsonvalues.ConvertValueTest.class);
		suite.addTestSuite(tests.json.jsonvalues.CreateTest.class);
		suite.addTestSuite(tests.json.jsonvalues.GetterIndexTest.class);
		suite.addTestSuite(tests.json.jsonvalues.GetterPropertyTest.class);
		suite.addTestSuite(tests.json.jsonvalues.MiscellaneousTest.class);
		suite.addTestSuite(tests.json.jsonvalues.StreamLikeTest.class);
		suite.addTestSuite(tests.json.jsonvalues.JsonValuesContractTest.class);

		suite.addTestSuite(tests.json.jsonvalues.array.AllMatchTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.AnyMatchTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.DistinctTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.DropWhileTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.FilterTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.FindTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.FlatMapTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.FlatTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.ForEachTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.LimitTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.MapTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.MaxTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.MemberTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.MinTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.NoneMatchTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.PeekTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.ProcessTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.ReduceTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.RemoveTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.SkipLimitTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.SkipTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.SliceTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.SortedTest.class);
		suite.addTestSuite(tests.json.jsonvalues.array.TakeWhileTest.class);

		// navigator
		suite.addTestSuite(WorldCupQueryTest.class);

		// object
		suite.addTestSuite(ObjectAsMapTest.class);
		suite.addTestSuite(ObjectPropertyTest.class);
		suite.addTestSuite(ObjectTest.class);
		suite.addTestSuite(tests.json.object.CollectionContractTest.class);
		
		// Serialization
		
		// Serializers
		suite.addTestSuite(SerializersTest.class);
		
		// streams
		suite.addTestSuite(ArrayStreamsTest.class);
		suite.addTestSuite(CsvStreamsTest.class);
		suite.addTestSuite(tests.json.streams.CollectorsAndCsvTest.class);

		// util
		suite.addTestSuite(JsonDateTimeTest.class);
		suite.addTestSuite(JsonUtilTest.class);
		suite.addTestSuite(tests.json.factory.FastPathsTest.class);
		suite.addTestSuite(tests.json.util.NumberEqualityTest.class);

		// wrappers
		suite.addTestSuite(tests.json.wrapper.WrapperlTest.class);

		// yaml
		suite.addTestSuite(YamlTest.class);

		suite.addTestSuite(JSONSimpleStrictParserTest.class);
		suite.addTestSuite(JSONTestSuiteTest.class);

		return suite;
	}

}
