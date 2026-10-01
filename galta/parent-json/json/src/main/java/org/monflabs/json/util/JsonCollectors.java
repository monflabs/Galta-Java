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
package org.monflabs.json.util;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.jsonpath.JsonValues;

/**
 * Predefined Json stream collector.
 * <p>
 * The collectors that create their own array ({@link #toJsonArray()},
 * {@link #toJsonArray(JsonFactory)}...) can be reused: each collection returns a new array.
 * The ones that fill an explicit target array ({@link #toJsonArray(JsonArray)},
 * {@link #toJsonArrayValues(JsonArray)}) append to that same array every time they are used,
 * so they are meant to be used once.
 */
public final class JsonCollectors {

	private JsonCollectors() {}

	private static final Set<Collector.Characteristics> IDENTITY = Set.of(Collector.Characteristics.IDENTITY_FINISH);

	/**
	 * Collects into the given array (the values are appended to it). Single use: a
	 * second collection appends to the same array again.
	 */
	public static final Collector<Object,JsonArray,JsonArray> toJsonArray(JsonArray array) {
		// The supplier must create a fresh accumulator each time (a parallel stream
		// calls it once per split, then combines): returning the target array itself
		// made the combiner do left.addAll(left). The target is filled by the finisher.
		return new CollectorImpl<Object,JsonArray,JsonArray>(
			() -> array.factory().createArray(),
			(JsonArray a, Object v) -> a.addValue(v),
			(JsonArray left, JsonArray right) -> { left.addAll(right); return left; },
			(JsonArray a) -> { array.addAll(a); return array; },
			Set.of()
		);
	}
	public static final Collector<Object,JsonArray,JsonArray> toJsonArray() {
		return toJsonArray(JsonFactory.get());
	}
	/**
	 * Collects into a new array created by the factory, for each collection.
	 */
	public static final Collector<Object,JsonArray,JsonArray> toJsonArray(JsonFactory factory) {
		return new CollectorImpl<Object,JsonArray,JsonArray>(
			factory::createArray,
			(JsonArray a, Object v) -> a.addValue(v),
			(JsonArray left, JsonArray right) -> { left.addAll(right); return left; },
			Function.identity(),
			IDENTITY
		);
	}


	/**
	 * Collects the values into the given array. Single use: a second collection appends
	 * to the same array again.
	 */
	public static final Collector<JsonValues,JsonValues,JsonValues> toJsonArrayValues(JsonArray array) {
		return new CollectorImpl<JsonValues,JsonValues,JsonValues>(
			() -> JsonValues.of(array.factory().createArray()),
			// Every value of v: none for an empty JsonValues, all of them for a list
			(JsonValues a, JsonValues v) ->
				v.rawForEach(o -> a.arrayValue().addValue(o)),
			(left, right) -> {
				left.arrayValue().addAll(right.arrayValue()); return left;
			},
			(a) -> { array.addAll(a.arrayValue()); return JsonValues.of(array); },
			Set.of()
		);
	}
	public static final Collector<JsonValues,JsonValues,JsonValues> toJsonArrayValues() {
		return toJsonArrayValues(JsonFactory.get());
	}
	/**
	 * Collects the values into a new array created by the factory, for each collection.
	 */
	public static final Collector<JsonValues,JsonValues,JsonValues> toJsonArrayValues(JsonFactory factory) {
		return new CollectorImpl<JsonValues,JsonValues,JsonValues>(
			() -> JsonValues.of(factory.createArray()),
			(JsonValues a, JsonValues v) ->
				v.rawForEach(o -> a.arrayValue().addValue(o)),
			(left, right) -> {
				left.arrayValue().addAll(right.arrayValue()); return left;
			},
			Function.identity(),
			IDENTITY
		);
	}
}
