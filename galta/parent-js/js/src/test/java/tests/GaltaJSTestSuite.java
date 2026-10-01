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

public class GaltaJSTestSuite {
	
	public static TestSuite addStandardTests(TestSuite suite ) throws Exception {

		// api
		suite.addTestSuite(tests.javascript.api.InvocationTests.class);

		// Async
		suite.addTestSuite(tests.javascript.async.AsyncAwaitTest.class);
		suite.addTestSuite(tests.javascript.async.AsyncAwait2Test.class);
		suite.addTestSuite(tests.javascript.async.GeneratorLibTest.class);
		

		
		//////////////////////////////////////////////////////////////////////////////////////////
		// builtin
		//////////////////////////////////////////////////////////////////////////////////////////
		
		
		// Array
		suite.addTestSuite(tests.javascript.builtin.array.constructor.FromTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.constructor.IsArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.constructor.OfTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype._IteratorTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.AtTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ConcatTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.CopyWithinTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.EntriesTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.EveryTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FillTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FilterTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FindIndexTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FindLastIndexTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FindLastTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FindTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FlatMapTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.FlatTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ForEachTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.IncludesTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.IndexOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.JoinTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.KeysTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.LastIndexOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.MapTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.PopTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.PushTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ReduceRightTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ReduceTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ReverseTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ShiftTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.SliceTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.SomeTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.SortTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.SpliceTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ToLocaleStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ToReversedTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ToSortedTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ToSplicedTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.UnshiftTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.ValuesTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.prototype.WithTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.JsonArrayJavaTest.class);
		suite.addTestSuite(tests.javascript.builtin.array.JsonArrayTest.class);

		// Atomics
		suite.addTestSuite(tests.javascript.builtin.atomics.AtomicsTest.class);

		// BigInt
		suite.addTestSuite(tests.javascript.builtin.bigint.constructor.AsIntNTest.class);
		suite.addTestSuite(tests.javascript.builtin.bigint.constructor.AsUintNTest.class);
		suite.addTestSuite(tests.javascript.builtin.bigint.prototype.ToLocaleStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.bigint.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.bigint.prototype.ValueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.bigint.BigIntTest.class);

		// Boolean
		suite.addTestSuite(tests.javascript.builtin.bool.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.bool.prototype.ValueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.bool.BooleanTest.class);
		suite.addTestSuite(tests.javascript.builtin.bool.BooleanJavaTest.class);
		
		// Date
		suite.addTestSuite(tests.javascript.builtin.date.constructor.NowTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.constructor.ParseTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.constructor.UTCTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetDateTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetDayTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetFullYearTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetHoursTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetMillisecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetMinutesTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetMonthTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetSecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetTimeTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetTimezoneOffsetTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCDateTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCDayTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCFullYearTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCHoursTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCMillisecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCMinutesTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCMonthTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetUTCSecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.GetYearTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetDateTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetFullYearTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetHoursTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetMillisecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetMinutesTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetMonthTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetSecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetTimeTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetUTCDateTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetUTCFullYearTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetUTCHoursTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetUTCMillisecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetUTCMinutesTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetUTCMonthTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetUTCSecondsTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.SetYearTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToDateStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToGMTStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToISOStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToJSONTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToLocaleDateStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToLocaleStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToLocaleTimeStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToTimeStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.ToUTCStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.prototype.valueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.date.DateTest.class);
		
		// Errors
		suite.addTestSuite(tests.javascript.builtin.errors.ErrorTest.class);
		suite.addTestSuite(tests.javascript.builtin.errors.InternalErrorTest.class);
		suite.addTestSuite(tests.javascript.builtin.errors.RangeErrorTest.class);
		suite.addTestSuite(tests.javascript.builtin.errors.ReferenceErrorTest.class);
		suite.addTestSuite(tests.javascript.builtin.errors.SyntaxErrorTest.class);
		suite.addTestSuite(tests.javascript.builtin.errors.TypeErrorTest.class);
		suite.addTestSuite(tests.javascript.builtin.errors.URIErrorTest.class);

		// Function
		suite.addTestSuite(tests.javascript.builtin.function.constructor.FunctionConstructorTest.class);
		suite.addTestSuite(tests.javascript.builtin.function.constructor.FunctionConstructorStrictModeTest.class);
		suite.addTestSuite(tests.javascript.builtin.function.prototype.ApplyTest.class);
		suite.addTestSuite(tests.javascript.builtin.function.prototype.BindTest.class);
		suite.addTestSuite(tests.javascript.builtin.function.prototype.CallTest.class);
		suite.addTestSuite(tests.javascript.builtin.function.prototype.ToStringTest.class);
		
		// Iterators
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.DropTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.EveryTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.FilterTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.FindTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.FlatMapTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.ForEachTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.MapTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.ReduceTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.SomeTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.TakeTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.prototype.ToArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.iterator.IteratorTest.class);

		// JSON
		suite.addTestSuite(tests.javascript.builtin.json.JsonIsRawJSONTest.class);
		suite.addTestSuite(tests.javascript.builtin.json.JsonParseTest.class);
		suite.addTestSuite(tests.javascript.builtin.json.JsonRawJSONTest.class);
		suite.addTestSuite(tests.javascript.builtin.json.JsonStringifyTest.class);
		
		// Map
		suite.addTestSuite(tests.javascript.builtin.map.constructor.GroupByTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype._IteratorTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.ClearTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.DeleteTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.EntriesTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.ForEachTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.GetTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.HasTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.IteratorTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.KeysTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.SetTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.prototype.ValuesTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.JSMapTest.class);
		suite.addTestSuite(tests.javascript.builtin.map.MapTest.class);
		
		// Math
		suite.addTestSuite(tests.javascript.builtin.math.Math_ConstantsTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAbsTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAcoshTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAcosTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAsinhTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAsinTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAtan2Test.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAtanhTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathAtanTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathCbrtTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathCeilTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathClz32Test.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathCoshTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathCosTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathExpm1Test.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathExpTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathF16roundTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathFloorTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathFroundTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathHypotTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathImulTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathLog10Test.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathLog1pTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathLog2Test.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathLogTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathMaxTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathMinTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathPowTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathRandomTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathRoundTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathSignTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathSinhTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathSinTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathSqrtTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathSumPreciseTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathTanhTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathTanTest.class);
		suite.addTestSuite(tests.javascript.builtin.math.MathTruncTest.class);
		
		// Number
		suite.addTestSuite(tests.javascript.builtin.number.constructor.isFiniteTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.constructor.isIntegerTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.constructor.isNaNTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.constructor.isSafeIntegerTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.constructor.parseFloatTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.constructor.parseIntTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.prototype.ToExponentialTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.prototype.ToFixedTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.prototype.ToPrecisionTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.prototype.ValueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.NumberJavaTest.class);
		suite.addTestSuite(tests.javascript.builtin.number.NumberTest.class);

		// Object
		suite.addTestSuite(tests.javascript.builtin.object.constructor.AccessorNoSetterTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.AssignTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.CreateTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.DefinePropertiesTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.DefinePropertyTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.EntriesTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.FreezeTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.FromEntriesTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.GetOwnPropertyDescriptorsTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.GetOwnPropertyDescriptorTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.GetOwnPropertyNamesTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.GetOwnPropertySymbolsTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.GetPrototypeOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.GroupByTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.HasOwnTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.IsExtensibleTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.IsFrozenTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.IsSealedTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.IsTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.KeysTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.PreventExtensionsTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.SealTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.SetPrototypeOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.ValueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.constructor.ValuesTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.DefineGetterTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.DefineSetterTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.HasOwnPropertyTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.IsPrototypeOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.LookupGetterTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.LookupSetterTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.PropertyIsEnumerableTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.ToLocaleStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.prototype.ValueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.JsonObjectJavaTest.class);
		suite.addTestSuite(tests.javascript.builtin.object.JsonObjectTest.class);

		// Performance
		suite.addTestSuite(tests.javascript.builtin.performance.AccessGlobalTest.class);
		suite.addTestSuite(tests.javascript.builtin.performance.PerformanceTest.class);
		suite.addTestSuite(tests.javascript.builtin.performance.VariousPerfTest.class);

		// Promise
		suite.addTestSuite(tests.javascript.builtin.promise.PromiseFromJavaTest.class);
		suite.addTestSuite(tests.javascript.builtin.promise.PromiseSpeciesTest.class);
		suite.addTestSuite(tests.javascript.builtin.promise.PromiseTest.class);

		// Proxy
		suite.addTestSuite(tests.javascript.builtin.proxy.ProxyHandlerTest.class);
		suite.addTestSuite(tests.javascript.builtin.proxy.ProxyPassthroughTest.class);
		
		// Reflect
		suite.addTestSuite(tests.javascript.builtin.reflect.ReflectTest.class);
		suite.addTestSuite(tests.javascript.builtin.reflect.ReflectExTest.class);

		// RegExp
		suite.addTestSuite(tests.javascript.builtin.regexp.RegExpJDKTest.class);
		suite.addTestSuite(tests.javascript.builtin.regexp.RegExpJoniTest.class);
		suite.addTestSuite(tests.javascript.builtin.regexp.RegExpJoniDuplicateNamedGroupsTest.class);
		suite.addTestSuite(tests.javascript.builtin.regexp.RegExpJoniEarlyErrorsTest.class);
		suite.addTestSuite(tests.javascript.builtin.regexp.RegExpJoniLoneSurrogateTest.class);
		suite.addTestSuite(tests.javascript.builtin.regexp.RegExpJoniRepeatCaptureResetTest.class);
		
		// Set
		suite.addTestSuite(tests.javascript.builtin.set.prototype._IteratorTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.AddTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.ClearTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.DeleteTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.DifferenceTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.EntriesTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.ForEachTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.HasTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.IntersectionTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.isDisjointFromTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.IsSubsetOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.IsSupersetOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.IteratorTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.KeysTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.SymmetricDifferenceTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.UnionTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.prototype.ValuesTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.JavaSetTest.class);
		suite.addTestSuite(tests.javascript.builtin.set.SetTest.class);

		// String
		suite.addTestSuite(tests.javascript.builtin.string.constructor.FromCharCodeTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.constructor.FromCodePointTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.deprecated.SubstrTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype._IteratorTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.AtTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.CharAtTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.CharCodeAtTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.CodePointAtTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ConcatTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.EndsWithTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.IncludesTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.IndexOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.IsWellFormedTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.LastIndexOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.LocaleCompareTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.MatchAllTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.MatchTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.NormalizeTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.PadEndTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.PadStartTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.RepeatTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ReplaceAllTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ReplaceCallbackThisTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ReplaceTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.SearchTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.SliceTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.SplitTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.StartsWithTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.SubstringTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ToLocaleLowerCaseTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ToLocaleUpperCaseTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ToLowerCaseTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ToUpperCaseTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ToWellFormedTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.TrimEndTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.TrimStartTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.TrimTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.prototype.ValueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.StringJavaTest.class);
		suite.addTestSuite(tests.javascript.builtin.string.StringTest.class);

		// Symbol
		suite.addTestSuite(tests.javascript.builtin.symbol.constructor.ForTest.class);
		suite.addTestSuite(tests.javascript.builtin.symbol.constructor.KeyForTest.class);
		suite.addTestSuite(tests.javascript.builtin.symbol.prototype.ToStringTest.class);
		suite.addTestSuite(tests.javascript.builtin.symbol.prototype.ValueOfTest.class);
		suite.addTestSuite(tests.javascript.builtin.symbol.SymbolTest.class);
		
		// Typed array
		suite.addTestSuite(tests.javascript.builtin.typedarrays.ArrayBufferTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.BigInt64ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.BigUint64ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.DataViewTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.DataViewFloat16Test.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Float16ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Float32ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Float64ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Int16ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Int32ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Int8ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.SharedArrayBufferTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.TypedArrayExtendedTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Uint16ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Uint32ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Uint8ArrayTest.class);
		suite.addTestSuite(tests.javascript.builtin.typedarrays.Uint8ClampedArrayTest.class);
		
		// WeakMap
		suite.addTestSuite(tests.javascript.builtin.weakmap.prototype.DeleteTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakmap.prototype.GetTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakmap.prototype.GetOrInsertTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakmap.prototype.GetOrInsertComputedTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakmap.prototype.HasTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakmap.prototype.SetTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakmap.JavaWeakMapTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakmap.WeakMapTest.class);

		// WeakRef
		suite.addTestSuite(tests.javascript.builtin.weakref.WeakRefTest.class);
		suite.addTestSuite(tests.javascript.builtin.temporal.TemporalTest.class);
		suite.addTestSuite(tests.javascript.builtin.shadowrealm.ShadowRealmTest.class);

		// WeakSet
		suite.addTestSuite(tests.javascript.builtin.weakset.prototype.AddTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakset.prototype.DeleteTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakset.prototype.HasTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakset.JavaWeakSetTest.class);
		suite.addTestSuite(tests.javascript.builtin.weakset.WeakSetTest.class);
		
		//////////////////////////////////////////////////////////////////////////////////////////
		
		// Clazz (Class test)
		suite.addTestSuite(tests.javascript.clazz.ClassDeclarationAsiTest.class);
		suite.addTestSuite(tests.javascript.clazz.ClassMembersTest.class);
		suite.addTestSuite(tests.javascript.clazz.ClassMemberDescriptorTest.class);
		suite.addTestSuite(tests.javascript.clazz.PrivateMemberBrandCheckTest.class);
		suite.addTestSuite(tests.javascript.clazz.ClassGetterSetterTest.class);
		suite.addTestSuite(tests.javascript.clazz.InheritanceTest.class);
		suite.addTestSuite(tests.javascript.clazz.InheritanceNoStrictTest.class);
		suite.addTestSuite(tests.javascript.clazz.InheritFunctionTest.class);
		suite.addTestSuite(tests.javascript.clazz.ClassMiscTest.class);
		suite.addTestSuite(tests.javascript.clazz.PrivateAutoAccessorTest.class);

		// compiler
		suite.addTestSuite(tests.javascript.compiler.ConstantTest.class);
		suite.addTestSuite(tests.javascript.compiler.ContextualKeywordIdentifierTest.class);
		suite.addTestSuite(tests.javascript.compiler.EarlyErrorsNoStrictTest.class);
		suite.addTestSuite(tests.javascript.compiler.IdentifierEscapeNoStrictTest.class);
		suite.addTestSuite(tests.javascript.compiler.EmptyScriptNoNewLineTest.class);
		suite.addTestSuite(tests.javascript.compiler.EmptyScriptTest.class);
		suite.addTestSuite(tests.javascript.compiler.CompilerTest.class);
		suite.addTestSuite(tests.javascript.compiler.MultiLineCommentAsiTest.class);
		suite.addTestSuite(tests.javascript.compiler.ObjectAsExpressionTest.class);
		suite.addTestSuite(tests.javascript.compiler.RestrictedProductionAsiTest.class);
		suite.addTestSuite(tests.javascript.compiler.ShebangTest.class);
		suite.addTestSuite(tests.javascript.compiler.UnicodeIdentifierTest.class);
		
		// control
		suite.addTestSuite(tests.javascript.control.DoWhileTest.class);
		suite.addTestSuite(tests.javascript.control.ForInTest.class);
		suite.addTestSuite(tests.javascript.control.ForOfDestructTest.class);
		suite.addTestSuite(tests.javascript.control.ForOfIteratorTest.class);
		suite.addTestSuite(tests.javascript.control.ForOfTest.class);
		suite.addTestSuite(tests.javascript.control.ForTest.class);
		suite.addTestSuite(tests.javascript.control.GeneratorDispatcherSplitTest.class);
		suite.addTestSuite(tests.javascript.control.GeneratorParameterBindingTest.class);
		suite.addTestSuite(tests.javascript.control.GeneratorResumeValueTest.class);
		suite.addTestSuite(tests.javascript.control.GeneratorTest.class);
		suite.addTestSuite(tests.javascript.control.IfTest.class);
		suite.addTestSuite(tests.javascript.control.IteratorTest.class);
		suite.addTestSuite(tests.javascript.control.SwitchTest.class);

		// Engine regressions
		suite.addTestSuite(tests.javascript.regression.EngineRegressionTest.class);
		suite.addTestSuite(tests.javascript.regression.EngineRegression2Test.class);
		suite.addTestSuite(tests.javascript.regression.BuiltinsRegressionTest.class);
		suite.addTestSuite(tests.javascript.regression.EngineRegressionNoStrictTest.class);
		suite.addTestSuite(tests.javascript.regression.ParserRegressionTest.class);
		suite.addTestSuite(tests.javascript.regression.ConsoleFormatTest.class);
		suite.addTestSuite(tests.javascript.regression.RuntimeRegressionTest.class);
		suite.addTestSuite(tests.javascript.control.TryTest.class);
		suite.addTestSuite(tests.javascript.control.WhileTest.class);
		suite.addTestSuite(tests.javascript.control.WithNoStrictTest.class);
		suite.addTestSuite(tests.javascript.control.WithStrictTest.class);
		
		// executor
		suite.addTestSuite(tests.javascript.executor.JSScriptExecutorTest.class);
		
		// functions
		suite.addTestSuite(tests.javascript.functions.ArgumentsMappingTest.class);
		suite.addTestSuite(tests.javascript.functions.TailCallTest.class);
		suite.addTestSuite(tests.javascript.functions.ArgumentsNoStrictTest.class);
		suite.addTestSuite(tests.javascript.functions.ArgumentsTest.class);
		suite.addTestSuite(tests.javascript.functions.ArrowArgumentsTest.class);
		suite.addTestSuite(tests.javascript.functions.ArrowFunctionShapeTest.class);
		suite.addTestSuite(tests.javascript.functions.ArrowFunctionTest.class);
		suite.addTestSuite(tests.javascript.functions.BasicFunctionTest.class);
		suite.addTestSuite(tests.javascript.functions.DestructFunctionTest.class);
		suite.addTestSuite(tests.javascript.functions.FunctionContextTest.class);
		suite.addTestSuite(tests.javascript.functions.FunctionSpreadTest.class);
		suite.addTestSuite(tests.javascript.functions.FunctionThisNoStrictTest.class);
		suite.addTestSuite(tests.javascript.functions.FunctionThisStrictTest.class);
		suite.addTestSuite(tests.javascript.functions.FunctionThisTest.class);
		suite.addTestSuite(tests.javascript.functions.FunctionTranspilerTest.class);
		suite.addTestSuite(tests.javascript.functions.GetterSetterNameTest.class);
		suite.addTestSuite(tests.javascript.functions.JavaInterfaceDefaultTest.class);
		suite.addTestSuite(tests.javascript.functions.JavaInterfaceStaticTest.class);
		suite.addTestSuite(tests.javascript.functions.JavaInterfaceTest.class);
		suite.addTestSuite(tests.javascript.functions.NamedFunctionExpressionSelfRefTest.class);
		suite.addTestSuite(tests.javascript.functions.JavaRunnableTest.class);
		suite.addTestSuite(tests.javascript.functions.PipelineFunctionTest.class);
		suite.addTestSuite(tests.javascript.functions.PrototypeTest.class);
		suite.addTestSuite(tests.javascript.functions.RestParameterPatternTest.class);
		suite.addTestSuite(tests.javascript.functions.ReturnTest.class);
		
		// java
		suite.addTestSuite(tests.javascript.java.AccessorTest.class);
		suite.addTestSuite(tests.javascript.java.ClassAccessTest.class);
		suite.addTestSuite(tests.javascript.java.EnumTest.class);
		suite.addTestSuite(tests.javascript.java.FieldReadTest.class);
		suite.addTestSuite(tests.javascript.java.FieldWriteTest.class);
		suite.addTestSuite(tests.javascript.java.JavaListTest.class);
		suite.addTestSuite(tests.javascript.java.JavaMapTest.class);
		suite.addTestSuite(tests.javascript.java.MethodCallTest.class);
		suite.addTestSuite(tests.javascript.java.NewTest.class);
		suite.addTestSuite(tests.javascript.java.PropertyTest.class);
		
		// javascript
		suite.addTestSuite(tests.javascript.javascript.UndeclaredVariableNoStrictTest.class);
		suite.addTestSuite(tests.javascript.javascript.VariablesTest.class);

		// libraries
		suite.addTestSuite(tests.javascript.libraries.TimersTest.class);
		suite.addTestSuite(tests.javascript.libraries.FetchTest.class);
		suite.addTestSuite(tests.javascript.libraries.RhinoShellTest.class);
		suite.addTestSuite(tests.javascript.libraries.NodeFsTest.class);
		
		// javatranspiler
		suite.addTestSuite(tests.javascript.javatranspiler.JavaTranspilerTest.class);
		suite.addTestSuite(tests.javascript.javatranspiler.JavaTranspilerUtilTest.class);
		suite.addTestSuite(tests.javascript.javatranspiler.TranspilerModuleTest.class);
		// TranspilerOptimizationsTest runs once, from AllGaltaJSTests (it transpiles itself)

		// literals
		suite.addTestSuite(tests.javascript.literals.ArrayLiteralTest.class);
		suite.addTestSuite(tests.javascript.literals.LiteralNoStrictTest.class);
		suite.addTestSuite(tests.javascript.literals.LiteralTest.class);
		suite.addTestSuite(tests.javascript.literals.ObjectLiteralTest.class);
		suite.addTestSuite(tests.javascript.literals.StringContinuationTest.class);
		suite.addTestSuite(tests.javascript.literals.TemplateStringTest.class);

		// modules
		suite.addTestSuite(tests.javascript.modules.ExportTest.class);
		suite.addTestSuite(tests.javascript.modules.ImportExportTest.class);
		suite.addTestSuite(tests.javascript.modules.ImportFilesTest.class);
		suite.addTestSuite(tests.javascript.modules.ImportSourceTest.class);
		suite.addTestSuite(tests.javascript.modules.ImportTest.class);
		suite.addTestSuite(tests.javascript.modules.DynamicImportTest.class);
		suite.addTestSuite(tests.javascript.modules.ModuleUtilTest.class);
		suite.addTestSuite(tests.javascript.modules.RequireTest.class);

		// object
		suite.addTestSuite(tests.javascript.object.GlobalTest.class);
		suite.addTestSuite(tests.javascript.object.IterationTest.class);
		suite.addTestSuite(tests.javascript.object.SuperTest.class);
		suite.addTestSuite(tests.javascript.object.SuperReceiverTest.class);
		suite.addTestSuite(tests.javascript.object.ThisFunctionTest.class);
		suite.addTestSuite(tests.javascript.object.ThisTest.class);
		
		
		
		//////////////////////////////////////////////////////////////////////////////////////////
		// op
		//////////////////////////////////////////////////////////////////////////////////////////

		// assign
		suite.addTestSuite(tests.javascript.op.assign.AssignAndTest.class);
		suite.addTestSuite(tests.javascript.op.assign.AssignArrayTest.class);
		suite.addTestSuite(tests.javascript.op.assign.AssignComputedKeyOrderTest.class);
		suite.addTestSuite(tests.javascript.op.assign.DestructuredAssignAutoGlobalTest.class);
		suite.addTestSuite(tests.javascript.op.assign.AnnexBHoistingStrictModeTest.class);
		suite.addTestSuite(tests.javascript.op.assign.AssignEvalOrderTest.class);
		suite.addTestSuite(tests.javascript.op.assign.AssignNullCoalescingTest.class);
		suite.addTestSuite(tests.javascript.op.assign.AssignOrTest.class);
		suite.addTestSuite(tests.javascript.op.assign.AssignTest.class);
		suite.addTestSuite(tests.javascript.op.assign.ConstAssignTest.class);
		suite.addTestSuite(tests.javascript.op.assign.ConstRedeclareFailCompileTest.class);
		suite.addTestSuite(tests.javascript.op.assign.ConstVariablesTest.class);
		suite.addTestSuite(tests.javascript.op.assign.DestructuredArrayTest.class);
		suite.addTestSuite(tests.javascript.op.assign.DestructuredExamplesTest.class);
		suite.addTestSuite(tests.javascript.op.assign.DestructuredIteratorProtocolTest.class);
		suite.addTestSuite(tests.javascript.op.assign.DestructuredObjectTest.class);
		suite.addTestSuite(tests.javascript.op.assign.HoistingTest.class);
		suite.addTestSuite(tests.javascript.op.assign.InferredNameTest.class);
		suite.addTestSuite(tests.javascript.op.assign.LocalVariablesOverride2Test.class);
		suite.addTestSuite(tests.javascript.op.assign.LocalVariablesOverrideTest.class);
		suite.addTestSuite(tests.javascript.op.assign.LocalVariablesTest.class);
		suite.addTestSuite(tests.javascript.op.assign.ScopedVariablesTest.class);
		suite.addTestSuite(tests.javascript.op.assign.TemporalDeadZoneTest.class);

		// binary
		suite.addTestSuite(tests.javascript.op.binary.AddTest.class);
		suite.addTestSuite(tests.javascript.op.binary.AndTest.class);
		suite.addTestSuite(tests.javascript.op.binary.BitAndTest.class);
		suite.addTestSuite(tests.javascript.op.binary.BitOrTest.class);
		suite.addTestSuite(tests.javascript.op.binary.BitXorTest.class);
		suite.addTestSuite(tests.javascript.op.binary.DivTest.class);
		suite.addTestSuite(tests.javascript.op.binary.EqStrictTest.class);
		suite.addTestSuite(tests.javascript.op.binary.EqTest.class);
		suite.addTestSuite(tests.javascript.op.binary.GeTest.class);
		suite.addTestSuite(tests.javascript.op.binary.GtTest.class);
		suite.addTestSuite(tests.javascript.op.binary.InstanceofTest.class);
		suite.addTestSuite(tests.javascript.op.binary.InTest.class);
		suite.addTestSuite(tests.javascript.op.binary.LeTest.class);
		suite.addTestSuite(tests.javascript.op.binary.LtTest.class);
		suite.addTestSuite(tests.javascript.op.binary.ModTest.class);
		suite.addTestSuite(tests.javascript.op.binary.MulTest.class);
		suite.addTestSuite(tests.javascript.op.binary.NeTest.class);
		suite.addTestSuite(tests.javascript.op.binary.NullCoalescingTest.class);
		suite.addTestSuite(tests.javascript.op.binary.OrTest.class);
		suite.addTestSuite(tests.javascript.op.binary.PowerTest.class);
		suite.addTestSuite(tests.javascript.op.binary.ShiftTest.class);
		suite.addTestSuite(tests.javascript.op.binary.SubTest.class);

		// chain
		suite.addTestSuite(tests.javascript.op.chain.OptionalChainingTest.class);

		// nary
		suite.addTestSuite(tests.javascript.op.nary.ComaTest.class);
		
		// ternary
		suite.addTestSuite(tests.javascript.op.ternary.TernaryTest.class);
		
		// unary
		suite.addTestSuite(tests.javascript.op.unary.BitNotTest.class);
		suite.addTestSuite(tests.javascript.op.unary.DeleteTest.class);
		suite.addTestSuite(tests.javascript.op.unary.DeleteNoStrictTest.class);
		suite.addTestSuite(tests.javascript.op.unary.IncDecTest.class);
		suite.addTestSuite(tests.javascript.op.unary.MinusTest.class);
		suite.addTestSuite(tests.javascript.op.unary.NotTest.class);
		suite.addTestSuite(tests.javascript.op.unary.ParenTest.class);
		suite.addTestSuite(tests.javascript.op.unary.PlusTest.class);
		suite.addTestSuite(tests.javascript.op.unary.SpreadTest.class);
		suite.addTestSuite(tests.javascript.op.unary.TypeofTest.class);
		suite.addTestSuite(tests.javascript.op.unary.VoidTest.class);

		// otherOp
		suite.addTestSuite(tests.javascript.op.CallExpressionTest.class);
		suite.addTestSuite(tests.javascript.op.NewExpressionTest.class);
		suite.addTestSuite(tests.javascript.op.NewTargetTest.class);
		suite.addTestSuite(tests.javascript.op.OverflowTest.class);
		suite.addTestSuite(tests.javascript.op.BoxedPrimitiveMapsTest.class);

		//////////////////////////////////////////////////////////////////////////////////////////

		
		// optimizer
		suite.addTestSuite(tests.javascript.optimizer.ConstantFoldingTest.class);
		suite.addTestSuite(tests.javascript.optimizer.UnreachableCodeRemovalTest.class);

		// protocol
		suite.addTestSuite(tests.javascript.protocols.IteratorTest.class);
		
		// stdlib
		suite.addTestSuite(tests.javascript.stdlib.ConsoleTest.class);
		suite.addTestSuite(tests.javascript.stdlib.ConsoleToStringTest.class);
		suite.addTestSuite(tests.javascript.stdlib.EvalCompletionValueTest.class);
		suite.addTestSuite(tests.javascript.stdlib.EvalGlobalConfigurableTest.class);
		suite.addTestSuite(tests.javascript.stdlib.EvalNonStrictTest.class);
		suite.addTestSuite(tests.javascript.stdlib.EvalTest.class);
		suite.addTestSuite(tests.javascript.stdlib.GlobalFunctionsTest.class);
		suite.addTestSuite(tests.javascript.stdlib.JSONTest.class);

		// strict
		suite.addTestSuite(tests.javascript.strict.StrictModeExhaustiveTest.class);
		suite.addTestSuite(tests.javascript.strict.ArgumentsCalleeStrictTest.class);
		suite.addTestSuite(tests.javascript.strict.DirectivePrologueTest.class);
		suite.addTestSuite(tests.javascript.strict.StrictModeExhaustiveStrictTest.class);
		suite.addTestSuite(tests.javascript.strict.EvalStrictCallerScriptTest.class);
		suite.addTestSuite(tests.javascript.strict.StrictModeNoStrictTest.class);
		suite.addTestSuite(tests.javascript.strict.StrictModeTest.class);
		suite.addTestSuite(tests.javascript.strict.StrictModeUseStrictTest.class);
		suite.addTestSuite(tests.javascript.strict.StrictModeUseStrictTest2.class);

		// util
		suite.addTestSuite(tests.javascript.util.ConsStringTest.class);
		suite.addTestSuite(tests.javascript.util.GaltaJSMapTest.class);
		suite.addTestSuite(tests.javascript.util.JSValueTest.class);
		suite.addTestSuite(tests.javascript.util.PrimitivePropertyMapTest.class);
		suite.addTestSuite(tests.javascript.util.SparseListTest.class);
		suite.addTestSuite(tests.javascript.util.JSArrayImplTest.class);
		suite.addTestSuite(tests.javascript.util.RuntimeUtilTest.class);

		
		
		
		//////////////////////////////////////////////////////////////////////////////////////////
		// Galta JS
		//////////////////////////////////////////////////////////////////////////////////////////


		// BigDecimal
		suite.addTestSuite(tests.galtajs.builtin.bigdecimal.prototype.ToLocaleStringTest.class);
		suite.addTestSuite(tests.galtajs.builtin.bigdecimal.prototype.ToStringTest.class);
		suite.addTestSuite(tests.galtajs.builtin.bigdecimal.prototype.ValueOfTest.class);
		suite.addTestSuite(tests.galtajs.builtin.bigdecimal.BigDecimalTest.class);
		
		// Set
		suite.addTestSuite(tests.galtajs.builtin.set.JavaSetTest.class);

		// assign
		suite.addTestSuite(tests.galtajs.op.assign.AssignSequenceTest.class);
		
		// binary
		suite.addTestSuite(tests.galtajs.op.binary.AddTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.DivTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.AddTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.ModTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.MulTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.PowerTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.SubTest.class);

		suite.addTestSuite(tests.galtajs.op.binary.AddListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.BitAndListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.BitOrListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.BitXorListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.DivListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.EqStrictListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.EqStrictTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.EqListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.GeListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.GtListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.InListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.InstanceofListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.LeListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.LtListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.ModListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.MulListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.NeListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.ShiftListTest.class);
		suite.addTestSuite(tests.galtajs.op.binary.SubListTest.class);
		
		// unary
		suite.addTestSuite(tests.galtajs.op.unary.DeleteListTest.class);
		suite.addTestSuite(tests.galtajs.op.unary.IncDecListTest.class);
		suite.addTestSuite(tests.galtajs.op.unary.SpreadListTest.class);
		suite.addTestSuite(tests.galtajs.op.unary.TypeofListTest.class);
		
		// path
		suite.addTestSuite(tests.galtajs.path.ArrayDeepScanTest.class);
		suite.addTestSuite(tests.galtajs.path.ArrayFilterTest.class);
		suite.addTestSuite(tests.galtajs.path.ArrayFindTest.class);
		suite.addTestSuite(tests.galtajs.path.ArrayMapTest.class);
		suite.addTestSuite(tests.galtajs.path.ArrayMultiTest.class);
		suite.addTestSuite(tests.galtajs.path.ArraySliceTest.class);
		suite.addTestSuite(tests.galtajs.path.ArrayTest.class);
		suite.addTestSuite(tests.galtajs.path.ChainingTest.class);
		suite.addTestSuite(tests.galtajs.path.FunctionCallTest.class);
		suite.addTestSuite(tests.galtajs.path.JsonArrayPathTest.class);
		suite.addTestSuite(tests.galtajs.path.JsonNullTest.class);
		suite.addTestSuite(tests.galtajs.path.JsonPathTest.class);
		suite.addTestSuite(tests.galtajs.path.JsonPathExtensionTest.class);
		suite.addTestSuite(tests.galtajs.path.MemberAccessTest.class);
		suite.addTestSuite(tests.galtajs.path.SequenceTest.class);
		suite.addTestSuite(tests.galtajs.path.WorldCupExampleTest.class);

		// type hints
		suite.addTestSuite(tests.galtajs.typehints.TypeHintsTest.class);
		suite.addTestSuite(tests.galtajs.typehints.TypeHintsDisabledTest.class);
		// top-level object literal
		suite.addTestSuite(tests.galtajs.TopLevelObjectLiteralTest.class);

		// Math
		suite.addTestSuite(tests.galtajs.builtin.math.Math_ConstantsTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAbsTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAcoshTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAcosTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAsinhTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAsinTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAtan2Test.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAtanhTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathAtanTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathCbrtTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathCeilTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathClz32Test.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathCoshTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathCosTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathExpm1Test.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathExpTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathFloorTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathFroundTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathHypotTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathImulTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathLog10Test.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathLog1pTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathLog2Test.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathLogTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathMaxTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathMinTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathPowTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathRandomTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathRoundTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathSignTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathSinhTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathSinTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathSqrtTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathTanhTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathTanTest.class);
		suite.addTestSuite(tests.galtajs.builtin.math.MathTruncTest.class);

		// otherOp
		suite.addTestSuite(tests.galtajs.op.ForceBigDecimalTest.class);
		suite.addTestSuite(tests.galtajs.op.OverflowBigIntPromotionTest.class);
		suite.addTestSuite(tests.galtajs.op.OverflowTest.class);
		
		// Pre-Processor
		suite.addTestSuite(tests.galtajs.preprocessor.DirectivesTest.class);
		suite.addTestSuite(tests.galtajs.preprocessor.NoDirectivesTest.class);
		
		return suite;
	}
}
