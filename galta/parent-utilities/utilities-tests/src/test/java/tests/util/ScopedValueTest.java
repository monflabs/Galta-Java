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
package tests.util;

import static org.junit.Assert.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.util.scoped._ScopedValue;

import tests.ProjectTestCase;

public class ScopedValueTest extends ProjectTestCase {

	public void testBindAndRestore() throws Exception {
		_ScopedValue<String> sv = _ScopedValue.newInstance();
		assertNull(sv.get());
		_ScopedValue.where(sv, "a").run(() -> {
			assertEquals("a", sv.get());
			// Nested binding, restored afterwards
			assertEquals("b", _ScopedValue.where(sv, "b").get(() -> sv.get()));
			assertEquals("a", sv.get());
		});
		assertNull(sv.get());
	}

	public void testRestoredOnException() throws Exception {
		_ScopedValue<String> sv = _ScopedValue.newInstance();
		_ScopedValue.where(sv, "outer").run(() -> {
			assertThrows(IllegalStateException.class, () -> _ScopedValue.where(sv, "inner").run(() -> {
				throw new IllegalStateException();
			}));
			assertEquals("outer", sv.get());
		});
		assertThrows(Exception.class, () -> _ScopedValue.where(sv, "x").call(() -> {
			throw new Exception();
		}));
		assertNull(sv.get());
	}

	public void testCall() throws Exception {
		_ScopedValue<Integer> sv = _ScopedValue.newInstance();
		assertEquals(Integer.valueOf(3), _ScopedValue.where(sv, 2).call(() -> sv.get()+1));
		assertNull(sv.get());
	}

	public void testThreadInheritance() throws Exception {
		_ScopedValue<String> sv = _ScopedValue.newInstance();
		AtomicReference<String> seen = new AtomicReference<>("unset");
		_ScopedValue.where(sv, "a").run(() -> {
			Thread t = new Thread(() -> seen.set(sv.get()));
			t.start();
			try {
				t.join();
			} catch(InterruptedException e) {
			}
		});
		// Documented: a thread created inside the scope inherits the value (GaltaJS relies on it)
		assertEquals("a", seen.get());
		// ... while a thread created after the scope doesn't see it
		AtomicReference<String> seen2 = new AtomicReference<>("unset");
		Thread t2 = new Thread(() -> seen2.set(sv.get()));
		t2.start();
		t2.join();
		assertNull(seen2.get());
	}
}
