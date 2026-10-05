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
package performance.jmh;

import java.util.concurrent.TimeUnit;

import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorImpl;
import org.monflabs.util.iterators.IntIterator;
import org.monflabs.util.iterators.Iterators;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * The cost of a generator (a producer running in its own thread, every value handed over
 * twice through an Exchanger) compared with an iterator, over the same sequence of boxed
 * integers.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class GeneratorBenchmark {

	@Param({"1000", "100000"})
	public int count;

	@Benchmark
	public long iterator() {
		long total = 0;
		IntIterator it = Iterators.intSequence(0, count);
		while(it.hasNext()) {
			Integer i = it.next();   // boxed, as the generator values are
			total += i;
		}
		return total;
	}

	@Benchmark
	public long generator() {
		final int max = count;
		Generator<Integer,Void> it = GeneratorImpl.create( (Y) -> {
			for(int i=0; i<max; i++) {
				Y.yield(i);
			}
			return null;
		} );
		long total = 0;
		while(it.hasNext()) {
			total += it.next();
		}
		return total;
	}
}
