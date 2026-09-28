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
package tests.generators;

import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorImpl;
import org.monflabs.util.iterators.IntIterator;
import org.monflabs.util.iterators.Iterators;
import org.monflabs.util.performance.PerformanceWatch;

import tests.ProjectTestCase;

public class GeneratorPerformanceTest extends ProjectTestCase {

	public void testValues() throws Exception {
		// Warm up
		for(int i=0; i<5; i++) {
			iterator(10);
			generator(10);
		}
		
		// Measure.
		//
		// 1M (not 10M) yields: PerformanceWatch.run(r,5) is 1 warmup + 5
		// measured iterations, and every yield costs TWO Exchanger handoffs,
		// so 10M ran 60 million handoffs per benchmark. That took ~12 s on
		// JDK 21 but ~325 s on JDK 25, whose Exchanger is roughly 19x slower
		// (reproducible with a bare java.util.concurrent.Exchanger, no Galta
		// code involved, and equally on platform threads - so it is not a
		// virtual-thread effect). 325 s overshot surefire's
		// forkedProcessTimeoutInSeconds, so the fork was killed and the build
		// looked like it hung at tests.AllUtilTests.
		//
		// 1M keeps the iterator-vs-generator comparison meaningful while
		// costing ~5 s on JDK 25 and well under 1 s on JDK 21.
		PerformanceWatch w_it = new PerformanceWatch("Iterator");
		w_it.run( () -> iterator(1_000_000), 5);
		PerformanceWatch w_gen = new PerformanceWatch("Generator");
		w_gen.run( () -> generator(1_000_000), 5);
		
		w_it.dump();
		w_gen.dump();
	}
	
	private void iterator(int max) {
		IntIterator it = Iterators.intSequence(0, max);
		while(it.hasNext())  {
			// Convert to an object, to be fair
			Integer ii = it.next();
			f(ii);
		}
	}
	private void generator(int max) {
		Generator<Integer,Void> it = GeneratorImpl.create( (Y) -> {
			for(int i=0; i<max; i++) {
				Y.yield(i);
			}
			return null;
		} );
		while(it.hasNext())  {
			// Convert to an object, to be fair
			Integer ii = it.next();
			f(ii);
		}
	}
	
	private static void f(Integer ii) {
		// Just do nothing
	}
}
