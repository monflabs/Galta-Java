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
package tests.profiler;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.monflabs.util.profiler.Aggregator;
import org.monflabs.util.profiler.JavaProfiler.ProfileRunnable;
import org.monflabs.util.profiler.ProfilerException;
import org.monflabs.util.profiler.ProfilerSnapshot;
import org.monflabs.util.profiler.impl.JavaProfilerImpl;

import tests.ProjectTestCase;

/**
 * Uses its own JavaProfilerImpl, independent from the global Profiler used by ProfilerTest.
 */
public class ProfilerConcurrencyTest extends ProjectTestCase {

	private static Aggregator child(Aggregator parent, String type) {
		for(Aggregator a: parent.getChildren()) {
			if(a.getType().equals(type)) {
				return a;
			}
		}
		return null;
	}

	public void testConcurrentCounts() throws Exception {
		JavaProfilerImpl p = new JavaProfilerImpl();
		p.start();
		int threads = 8;
		int calls = 20_000;
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> list = new ArrayList<>();
		for(int t=0; t<threads; t++) {
			Thread th = new Thread(() -> {
				try {
					go.await();
				} catch(InterruptedException e) {
				}
				for(int i=0; i<calls; i++) {
					p.profile("work", () -> {});
				}
			});
			list.add(th);
			th.start();
		}
		go.countDown();
		for(Thread th: list) {
			th.join();
		}
		ProfilerSnapshot s = p.createSnapshot("c");
		Aggregator work = child(s.getMainAggregator(), "work");
		// The shared counters used to be updated without synchronization: updates were lost
		assertEquals(threads*calls, work.getCount());
		assertTrue(work.getMinWallTime()<=work.getMaxWallTime());
		assertTrue(work.getTotalWallTime()>=work.getMaxWallTime());
		p.stop();
	}

	public void testResetDuringProfile() throws Exception {
		JavaProfilerImpl p = new JavaProfilerImpl();
		p.start();
		p.profile("outer", () -> {
			p.profile("inner", () -> {});
			p.reset();
		});
		// The in-flight profile() used to put the discarded tree into the new ThreadLocal:
		// everything profiled afterwards on this thread was lost
		p.profile("after", () -> {});
		p.profile("after", () -> {});
		ProfilerSnapshot s = p.createSnapshot("r");
		Aggregator after = child(s.getMainAggregator(), "after");
		assertNotNull(after);
		assertEquals(2, after.getCount());
		assertNull(child(s.getMainAggregator(), "outer"));
		p.stop();
	}

	public void testExceptionRestoresTheParent() throws Exception {
		JavaProfilerImpl p = new JavaProfilerImpl();
		p.start();
		assertThrows(IllegalStateException.class, () -> p.profile("failing", (ProfileRunnable) () -> {
			throw new IllegalStateException();
		}));
		ProfilerException pe = assertThrows(ProfilerException.class, () -> p.profile("checked", (ProfileRunnable) () -> {
			throw new IOException("io");
		}));
		assertTrue(pe.getCause() instanceof IOException);
		p.profile("next", () -> {});
		ProfilerSnapshot s = p.createSnapshot("e");
		// "next" is a root child, not nested under the failed profiles
		assertNotNull(child(s.getMainAggregator(), "next"));
		assertEquals(1, child(s.getMainAggregator(), "failing").getCount());
		p.stop();
	}

	public void testNestingAndGrouping() throws Exception {
		JavaProfilerImpl p = new JavaProfilerImpl();
		p.start();
		String r = p.profile("parent", null, () -> {
			p.profile("child", "a", () -> {});
			p.profile("child", "b", () -> {});
			p.profile("child", "a", () -> {});
			return "result";
		});
		assertEquals("result", r);
		ProfilerSnapshot s = p.createSnapshot("n");
		Aggregator parent = child(s.getMainAggregator(), "parent");
		assertEquals(1, parent.getCount());
		// The children of a same type are grouped under an aggregate node
		Aggregator group = child(parent, "child");
		assertEquals(3, group.getCount());
		assertEquals(2, group.getChildren().size());
		assertTrue(parent.getSpecificWallTime()<=parent.getTotalWallTime());
		p.stop();
	}

	public void testNotStarted() throws Exception {
		JavaProfilerImpl p = new JavaProfilerImpl();
		assertFalse(p.isStarted());
		assertEquals("x", p.profile("t", () -> "x"));
		assertEquals(0, p.createSnapshot("empty").getMainAggregator().getChildren().size());
	}
}
