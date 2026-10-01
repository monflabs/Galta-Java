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
package org.monflabs.util.iterators;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Predicate;

/**
 * 
 */
public final class Iterables {
	
	
	//
	// Iterablr utilities
	//

	private static Iterable<Object> empty = new Iterable<Object>() {
		@Override
		public Iterator<Object> iterator() {
			return Iterators.empty();
		}
	};
	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> empty() {
		return (Iterable<T>) empty;
	}
	

	//
	// Read only Iterable
	//
	

	//
	// Single value Iterable
	//
	
	public static <T> Iterable<T> single(T value) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return Iterators.single(value);
			}
		};
	}

	
	//
	// Static Iterable
	//
	
	@SafeVarargs
	public static <T> Iterable<T> staticValues(T...values) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return Iterators.staticValues(values);
			}
		};
	}

	//
	// Filtering Iteratble
	//


	public static <T> Iterable<T> filter(Iterable<T> iterable, Predicate<T> filter) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return Iterators.filter(iterable.iterator(),filter);
			}
		};
	}
	public static <T> Iterable<T> filter(Iterable<T> iterable, IteratorPredicate<T> filter) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return Iterators.filter(iterable.iterator(),filter);
			}
		};
	}

	//
	// Aggregating Iterables
	//


	@SafeVarargs
	public static <T> Iterable<T> concat(Iterable<T>... iterables) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				// Built per call: a single shared outer iterator was exhausted after the first iteration
				Iterator<Iterator<T>> it = Iterators.map( Iterators.array((Iterable<T>[])iterables), (iterable)-> iterable!=null ? iterable.iterator() : null);
				return Iterators.concat(it);
			}
		};
	}

	//
	// Wrapping Iterable
	//

	public static <T, R> Iterable<R> map(Iterable<T> it, Function<T, R> mapper) {
		return new Iterable<R>() {
			@Override
			public Iterator<R> iterator() {
				return Iterators.map(it.iterator(),mapper);
			}
		};
	}
	public static <T, R> Iterable<R> map(Iterable<T> it, IteratorMap<T, R> mapper) {
		return new Iterable<R>() {
			@Override
			public Iterator<R> iterator() {
				return Iterators.map(it.iterator(),mapper);
			}
		};
	}
	

	//
	// Array iterables
	//
	
	//
	// Nested iterable
	//
	

	//
	// Flattened iterable
	//

}
