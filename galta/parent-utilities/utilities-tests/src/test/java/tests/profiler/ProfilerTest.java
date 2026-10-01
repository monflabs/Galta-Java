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

import org.monflabs.util.profiler.Aggregator;
import org.monflabs.util.profiler.Profiler;
import org.monflabs.util.profiler.ProfilerSnapshot;

import tests.ProjectTestCase;

public class ProfilerTest extends ProjectTestCase {

	public void testEnable() throws Exception {
		assertFalse( Profiler.isStarted() );

		Profiler.start();
		assertTrue( Profiler.isStarted() );

		Profiler.stop();
		assertFalse( Profiler.isStarted() );
	}

	public void testWallProfiler() throws Exception {
		Profiler.reset();
		Profiler.start();
		try {
			Profiler.profile("Test 1","2*30 ms wait", () -> {
	        	Thread.sleep(30);
	        	Profiler.profile("Test 2","20 ms wait", () -> {
	                Thread.sleep(20);
	            });
	        	Profiler.profile("Test 2","10 ms wait", () -> {
	                Thread.sleep(10);
	            });
	        	Thread.sleep(30);
	        });
	        
	        ProfilerSnapshot d = Profiler.createSnapshot("Sample");
	
	        Aggregator agg = d.getMainAggregator().getChildren().iterator().next();
	        Aggregator aggChild = agg.getChildren().iterator().next();
	        
	        assertEquals("Sample", d.getNotes());
	        assertEquals(1, agg.getCount());
	
	        assertTrue(toMillis(agg.getTotalWallTime())>=80);
	        assertTrue(toMillis(aggChild.getTotalWallTime())>=20);
	        
	        assertTrue(toMillis(agg.getTotalCpuTime())<100);
	        assertTrue(toMillis(aggChild.getTotalCpuTime())<100);
	        
	        d.dump();
		} finally {
			Profiler.stop();
		}
	}
	
	private static long toMillis(long nanos) {
		return nanos/1_000_000L;
	}

	public void testCallableNesting() throws Exception {
		Profiler.reset();
		Profiler.start();
		try {
			// A profile(Callable) must become the current aggregator so nested profiles attach to it
			int r = Profiler.profile("Outer", () -> {
				Profiler.profile("Inner", () -> {
					Thread.sleep(1);
				});
				return 42;
			});
			assertEquals(42, r);
			ProfilerSnapshot d = Profiler.createSnapshot("Callable");
			Aggregator outer = d.getMainAggregator().getChildren().iterator().next();
			assertEquals("Outer", outer.getType());
			assertEquals(1, outer.getChildren().size());
			assertEquals("Inner", outer.getChildren().iterator().next().getType());
		} finally {
			Profiler.stop();
		}
	}

	public void testCpuTimeNotAvailableOnVirtualThreads() throws Exception {
		org.monflabs.util.profiler.JavaProfiler p = new org.monflabs.util.profiler.impl.JavaProfilerImpl();
		p.start();
		try {
			Thread t = Thread.ofVirtual().start(() -> p.profile("virtual", () -> {
				long s = System.nanoTime();
				while(System.nanoTime()-s < 2_000_000L) {
					// busy
				}
			}));
			t.join();
			org.monflabs.util.profiler.ProfilerSnapshot snap = p.createSnapshot("vt");
			org.monflabs.util.profiler.Aggregator a = snap.getMainAggregator().getChildren().get(0);
			assertEquals("virtual", a.getType());
			assertEquals(1, a.getCount());
			assertTrue(a.getTotalWallTime() > 0);
			// The JVM has no CPU time for a virtual thread: reported as not available (-1),
			// it used to be a bogus 0 (-1 minus -1)
			assertEquals(-1, a.getTotalCpuTime());
			assertEquals(-1, a.getAvgCpuTime());
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			snap.dump(new java.io.PrintStream(out, true, "UTF-8"));
			assertTrue(out.toString("UTF-8"), out.toString("UTF-8").contains("n/a"));
		} finally {
			p.stop();
		}
	}
}
