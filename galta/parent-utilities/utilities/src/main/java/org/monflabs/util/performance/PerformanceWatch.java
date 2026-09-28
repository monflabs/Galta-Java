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
package org.monflabs.util.performance;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.util.StringFormat;

// CPU: https://algs4.cs.princeton.edu/14analysis/StopwatchCPU.java.html
public class PerformanceWatch {
	
	public static final long NANOSECONDS_PER_SECOND = 1000000000L;
	public static final long NANOSECONDS_PER_MIILI  = 1000000L;

	public static final int DEFAULT_WARMUP    = 1;
	public static final int DEFAULT_ITERATION = 1;
	
	public static class Iteration {
		public long start;
		public long end;
		public long cpuStart;
		public long cpuEnd;
		
		protected void close() {
		}

		public long getWallTime() {
			return end-start;
		}
		
		public long getCpuTime() {
			return cpuEnd-cpuStart;
		}
	}
	
	@FunctionalInterface
	public interface RunnableWithException {
	    void run() throws Exception;
	}
	
	
	private final ThreadMXBean threadTimer =  ManagementFactory.getThreadMXBean();
	
	private String title;
	private Iteration iteration;
	private List<Iteration> iterations;
	
	public PerformanceWatch(String title) {
		this.title = title;
		this.iterations = new ArrayList<>();
	}

	public PerformanceWatch(String title, Object...params) {
		this(StringFormat.format(title, params));
	}
	
	public void run(Runnable r) {
		run(r,DEFAULT_ITERATION);
	}
	public void run(Runnable r, int iterations) {
		run(r,iterations,DEFAULT_WARMUP);
	}
	public void run(Runnable r, int iterations, int warmup) {
		for(int i=0; i<warmup; i++) {
			r.run();
		}
		for(int i=0; i<iterations; i++) {
			startIteration();
			try {
				r.run();
			} finally {
				endIteration();
			}
		}
	}
	
	public void runWithException(RunnableWithException r) throws Exception {
		runWithException(r,DEFAULT_ITERATION);
	}
	public void runWithException(RunnableWithException r, int iterations) throws Exception {
		runWithException(r,iterations,DEFAULT_WARMUP);
	}
	public void runWithException(RunnableWithException r, int iterations, int warmup) throws Exception {
		for(int i=0; i<warmup; i++) {
			r.run();
		}
		for(int i=0; i<iterations; i++) {
			startIteration();
			try {
				r.run();
			} finally {
				endIteration();
			}
		}
	}

	public void startIteration() {
		if(this.iteration!=null) {
			throw new IllegalStateException("Iteration is already running");
		}
		this.iteration = new Iteration();
		iteration.start = System.nanoTime();
		iteration.cpuStart = threadTimer.getCurrentThreadCpuTime();
	}

	public void endIteration() {
		if(this.iteration==null) {
			throw new IllegalStateException("There is no iteration running");
		}
		iteration.end = System.nanoTime();
		iteration.cpuEnd = threadTimer.getCurrentThreadCpuTime();
		iterations.add(iteration);
		iteration.close();
		iteration = null;
	}
	
	public List<Iteration> getIterations() {
		return iterations;
	}
	
	public long getTotalWallTime() {
		long acc = 0;
		for(Iteration it: iterations) {
			long t = it.getWallTime();
			acc += t;
		}
		return acc;
	}
	public long getTotalWallTimeMs() {
		return getTotalWallTime()/PerformanceWatch.NANOSECONDS_PER_MIILI;
	}
	
	public long getAverageWallTime() {
		long acc = 0;
		if(!iterations.isEmpty()) {
			return getTotalWallTime()/iterations.size();
		}
		return acc;
	}
	public long getAverageWallTimeMs() {
		return getAverageWallTime()/PerformanceWatch.NANOSECONDS_PER_MIILI;
	}
	
	public long getTotalCpuTime() {
		long acc = 0;
		for(Iteration it: iterations) {
			long t = it.getCpuTime();
			acc += t;
		}
		return acc;
	}
	public long getTotalCpuTimeMs() {
		return getTotalCpuTime()/PerformanceWatch.NANOSECONDS_PER_MIILI;
	}
	
	public long getAverageCpuTime() {
		long acc = 0;
		if(!iterations.isEmpty()) {
			return getTotalCpuTime()/iterations.size();
		}
		return acc;
	}
	public long getAverageCpuTimeMs() {
		return getAverageCpuTime()/PerformanceWatch.NANOSECONDS_PER_MIILI;
	}
	
	
	public void dump() {
		print("----------");
		if(title!=null && title.length()>0) {
			print("{0}",title);
		}
		if(iterations.size()>1) {
			print("Number of Iterations: #{0}", iterations.size());
			print("    Total wall time : {0}ms", getTotalWallTime()/NANOSECONDS_PER_MIILI);
			print("    Total cpu time  : {0}ms", getTotalCpuTime()/NANOSECONDS_PER_MIILI);
			print("");
			print("    Average wall time : {0}ms", getAverageWallTime()/NANOSECONDS_PER_MIILI);
			print("    Average cpu time  : {0}ms", getAverageCpuTime()/NANOSECONDS_PER_MIILI);
			print("");
		} else {
			print("    Wall time : {0}ms", getTotalWallTime()/NANOSECONDS_PER_MIILI);
			print("    Cpu time  : {0}ms", getTotalCpuTime()/NANOSECONDS_PER_MIILI);
		}
	}
	
	public static void print(String s) {
		System.out.println(s);
	}
	public static void print(String msg, Object...params) {
		// StringFormat rather than MessageFormat: a quote in a title must not be swallowed,
		// and numbers must not get locale grouping separators
		String s = StringFormat.format(msg, params);
		System.out.println(s);
	}
}
