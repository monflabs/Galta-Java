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
package tests.javascript.async;

import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorImpl;
import org.monflabs.util.generators.Yielder;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class GeneratorLibTest extends JavaScriptStrictTestCase {
	
	@FunctionalInterface
	interface GeneratorFactory {
		Generator<Integer,Long> create(Function<Yielder<Integer>,Long> body);
	}

	public void testBasicThread() {
		checkGenerator( (exec) -> GeneratorImpl.create(exec));
	}
	
	public void checkGenerator(GeneratorFactory factory) {
		checkEmptyGenerator(factory);
		checkOneEltGenerator(factory);
		checkTwoEltGenerator(factory);
		checkMultipleGenerator(factory);
		checkGeneratorRaisingExceptionH(factory);
	}
	
	public static <T> List<T> list(Iterator<T> iterator) {
		List<T> result = new ArrayList<T>();
		while (iterator.hasNext()) {
			result.add(iterator.next());
		}
		return result;
	}
	public void checkEmptyGenerator(GeneratorFactory factory) {
		Generator<Integer,Long> empty = factory.create( (yielder) -> { return 0L; } );
		assertEquals(new ArrayList<Object>(), list(empty));
	}

	public void checkOneEltGenerator(GeneratorFactory factory) {
		Generator<Integer,Long> one = factory.create( (yielder) -> { yielder.yield(1); return 1L; } );
		assertEquals( Arrays.asList(1), list(one));
	}

	public void checkTwoEltGenerator(GeneratorFactory factory) {
		Generator<Integer,Long> one = factory.create( (yielder) -> { yielder.yield(1); yielder.yield(2); return 3L; } );
		assertEquals( Arrays.asList(1,2), list(one));
	}
	public void checkMultipleGenerator(GeneratorFactory factory) {
		int COUNT = 100;
		Generator<Integer,Long> multiple = factory.create( (yielder) -> {
			long acc = 0;
			for(int i=0; i<COUNT; i++) {
				yielder.yield(i);
				acc += i;
			}
			return acc;
		} );
		Iterator<Integer> it = multiple;
		long acc = 0;
		for(int i=0; i<COUNT; i++) {
			Integer ii = it.next();
			assertEquals(i, ii.intValue());
			acc += i;
		}
		assertFalse(it.hasNext());
		assertEquals(acc,multiple.getReturnValue().longValue());
	}
	public void checkGeneratorRaisingExceptionH(GeneratorFactory factory) {
		Generator<Integer,Long> exception = factory.create( (yielder) -> { throw new RuntimeException(); } );
		Iterator<Integer> it = exception; // Should not yet be generated
		assertThrows(RuntimeException.class, () -> {it.next();} );
	}
}
