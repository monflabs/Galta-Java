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
package tests.collections;

import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.util.cache.CacheProvider;
import org.monflabs.util.cache.LRUCache;
import org.monflabs.util.cache.MapCacheProvider;

import tests.ProjectTestCase;

public class CacheProviderTest extends ProjectTestCase {

	public void testFactory() throws Exception {
		CacheProvider<String,Integer> cache = new MapCacheProvider<>(new HashMap<>());
		AtomicInteger calls = new AtomicInteger();
		assertEquals(Integer.valueOf(3), cache.get("abc", k -> { calls.incrementAndGet(); return k.length(); }));
		assertEquals(Integer.valueOf(3), cache.get("abc", k -> { calls.incrementAndGet(); return -1; }));
		assertEquals(1, calls.get());
		assertNull(cache.get("x", null));
		assertFalse(cache.contains("x"));
		// A null value is cached (a negative entry)
		assertNull(cache.get("n", k -> null));
		assertTrue(cache.contains("n"));
		assertNull(cache.get("n", k -> { fail("must not be called"); return 1; }));
		cache.remove("abc");
		assertFalse(cache.contains("abc"));
		cache.clear();
		assertFalse(cache.contains("n"));
	}

	public void testRecursiveFactory() throws Exception {
		CacheProvider<String,Integer> cache = new MapCacheProvider<>(new HashMap<>());
		// Asking for the key being created used to recurse until a StackOverflowError
		assertThrows(IllegalStateException.class, () -> cache.get("a", k -> cache.get("a", k2 -> 1)));
		assertFalse(cache.contains("a"));
		// A factory using another key is fine
		assertEquals(Integer.valueOf(2), cache.get("b", k -> cache.get("c", k2 -> 1)+1));
		assertEquals(Integer.valueOf(1), cache.get("c", null));
	}

	public void testFactoryDoesNotBlockReaders() throws Exception {
		CacheProvider<String,Integer> cache = new MapCacheProvider<>(new HashMap<>());
		cache.put("other", 1);
		CountDownLatch inFactory = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread t = new Thread(() -> cache.get("slow", k -> {
			inFactory.countDown();
			try {
				release.await();
			} catch(InterruptedException e) {
			}
			return 2;
		}));
		t.start();
		assertTrue(inFactory.await(5, TimeUnit.SECONDS));
		// The factory used to run under the cache lock: this read blocked until it returned,
		// which never happens before the read returns (the factory waits for 'release').
		// The read runs in another thread, so a regression fails on the timeout, not by hanging.
		try {
			java.util.concurrent.CompletableFuture<Integer> read = java.util.concurrent.CompletableFuture.supplyAsync(() -> cache.get("other", null));
			assertEquals(Integer.valueOf(1), read.get(60, TimeUnit.SECONDS));
		} finally {
			release.countDown();
			t.join();
		}
		assertEquals(Integer.valueOf(2), cache.get("slow", null));
	}

	public void testConcurrentCreationFirstWins() throws Exception {
		CacheProvider<String,Object> cache = new MapCacheProvider<>(new HashMap<>());
		int n = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Object> results = java.util.Collections.synchronizedList(new ArrayList<>());
		List<Thread> threads = new ArrayList<>();
		for(int i=0; i<n; i++) {
			Thread t = new Thread(() -> {
				try {
					start.await();
				} catch(InterruptedException e) {
				}
				results.add(cache.get("k", k -> new Object()));
			});
			threads.add(t);
			t.start();
		}
		start.countDown();
		for(Thread t: threads) {
			t.join();
		}
		// Every thread gets the one value stored
		Object v = cache.get("k", null);
		for(Object r: results) {
			assertSame(v, r);
		}
	}

	public void testLRUContainsDoesNotRefresh() throws Exception {
		LRUCache<String,Integer> cache = new LRUCache<>(2);
		cache.put("a", 1);
		cache.put("b", 2);
		assertTrue(cache.contains("a"));   // contains() is not an access
		cache.put("c", 3);                 // evicts "a", the least recently used
		assertFalse(cache.contains("a"));
		cache.get("b");                    // get() is
		cache.put("d", 4);                 // evicts "c"
		assertTrue(cache.contains("b"));
		assertFalse(cache.contains("c"));
	}

	public void testCreationTrackingIsReleased() throws Exception {
		org.monflabs.util.cache.MapCacheProvider<String,String> c = new org.monflabs.util.cache.MapCacheProvider<>(new java.util.HashMap<>());
		assertEquals("A", c.get("a", k -> k.toUpperCase()));
		// The per-thread "being created" set used to stay behind, empty, after every creation
		java.lang.reflect.Field f = Class.forName("org.monflabs.util.cache.CacheProviderSupport").getDeclaredField("CREATING");
		f.setAccessible(true);
		assertNull(((ThreadLocal<?>)f.get(null)).get());
	}

	public void testGetMapIsASnapshot() throws Exception {
		org.monflabs.util.cache.MapCacheProvider<String,String> c = new org.monflabs.util.cache.MapCacheProvider<>(new java.util.HashMap<>());
		c.put("a", "1");
		java.util.Map<String,String> m = c.getMap();
		org.junit.Assert.assertThrows(UnsupportedOperationException.class, () -> m.put("b", "2"));
		c.put("b", "2");
		assertEquals(1, m.size());
		assertEquals(2, c.getMap().size());
	}
}
