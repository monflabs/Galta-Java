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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Iterator;

import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorImpl;

import tests.ProjectTestCase;

public class GeneratorTest extends ProjectTestCase {

	public void testValues() throws Exception {
		Generator<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			g.yield(1);
			g.yield(2);
			return null;
		});
		assertArrayEquals(new Object[] {1,2}, values(gen));
	}

	public void testException() throws Exception {
		Generator<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			g.yield(1);
			throw new RuntimeException();
		});
		assertThrows( RuntimeException.class, () -> values(gen));
	}
	
	public Object[] values(Iterator<?> it) {
		ArrayList<Object> l = new ArrayList<>();
		while(it.hasNext()) {
			l.add(it.next());
		}
		return l.toArray();
	}

	public void testReturnWith() throws Exception {
		boolean[] finallyRan = new boolean[1];
		Generator<Integer,String> gen = GeneratorImpl.create( (g) -> {
			try {
				g.yield(1);
				g.yield(2);
				return "end";
			} finally {
				finallyRan[0] = true;
			}
		});
		assertEquals(1, (int)gen.next());
		// The forced return completes the generator with that value: no exception reaches the consumer
		assertThrows(java.util.NoSuchElementException.class, () -> gen.returnWith("forced"));
		assertFalse(gen.hasNext());
		assertEquals("forced", gen.getReturnValue());
		assertTrue(finallyRan[0]);
	}

	public void testClose() throws Exception {
		boolean[] finallyRan = new boolean[1];
		Generator<Integer,Void> gen = GeneratorImpl.create( (g) -> {
			try {
				g.yield(1);
				g.yield(2);
			} finally {
				finallyRan[0] = true;
			}
			return null;
		});
		assertEquals(1, (int)gen.next());
		gen.close();
		assertFalse(gen.hasNext());
		assertTrue(finallyRan[0]);
		// Closing again, or a never-started generator, is harmless
		gen.close();
		Generator<Integer,Void> unstarted = GeneratorImpl.create( (g) -> { g.yield(1); return null; });
		unstarted.close();
		assertFalse(unstarted.hasNext());
	}
}
