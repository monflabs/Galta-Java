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

import java.util.LinkedHashMap;

import org.monflabs.util.performance.PerformanceWatch.RunnableWithException;

public class PerformanceWatchCollection {

	private String title;
	private LinkedHashMap<String,PerformanceWatch> watches;
	
	public PerformanceWatchCollection(String title) {
		this.title = title;
		clear();
	}
	
	public String getTitle() {
		return title;
	}
	
	public void clear() {
		watches = new LinkedHashMap<>();
	}
	
	public void run(String key, Runnable r) {
		run(key,r,PerformanceWatch.DEFAULT_ITERATION,PerformanceWatch.DEFAULT_WARMUP);
	}
	public void run(String key, Runnable r, int iterations) {
		run(key,r,iterations,PerformanceWatch.DEFAULT_WARMUP);
	}
	public void run(String key, Runnable r, int iterations, int warmup) {
		PerformanceWatch w = watches.get(key);
		if(w==null) {
			w = new PerformanceWatch(key);
			watches.put(key,w);
		}
		w.run(r,iterations,warmup);
	}
	
	public void runWithException(String key, RunnableWithException r) throws Exception {
		runWithException(key,r,PerformanceWatch.DEFAULT_ITERATION,PerformanceWatch.DEFAULT_WARMUP);
	}
	public void runWithException(String key, RunnableWithException r, int iterations) throws Exception {
		runWithException(key,r,iterations,PerformanceWatch.DEFAULT_WARMUP);
	}
	public void runWithException(String key, RunnableWithException r, int iterations, int warmup) throws Exception {
		PerformanceWatch w = watches.get(key);
		if(w==null) {
			w = new PerformanceWatch(key);
			watches.put(key,w);
		}
		w.runWithException(r,iterations,warmup);
	}

	public void dump() {
		PerformanceWatch.print("===========================================================================");
		if(title!=null && title.length()>0) {
			PerformanceWatch.print("{0}",title);
		}
		PerformanceWatch.print("");
		for(PerformanceWatch w: watches.values()) {
			w.dump();
		}
	}
}
