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
package org.monflabs.performance;

import org.monflabs.util.performance.PerformanceWatch;

public class MicroBenchmarkRunner {
	
	public MicroBenchmarkRunner() {
	}

	public PerformanceWatch run(BasePerformanceTest perfTest, int warmupIterations, int benchmarkIterations) throws Exception {
		perfTest.init();
		try {
			runWarmup(perfTest,warmupIterations);
			return runBenchmark(perfTest,benchmarkIterations);
		} finally {
			perfTest.cleanup();
		}
	}
	private void runWarmup(BasePerformanceTest perfTest, int iterations) throws Exception {
		for(int i=0; i<iterations; i++) {
			perfTest.initIteration(true);
			try {
				perfTest.run();
			} finally {
				perfTest.cleanupIteration();
			}
		}
	}
	private PerformanceWatch runBenchmark(BasePerformanceTest perfTest, int iterations) throws Exception {
		PerformanceWatch w = new PerformanceWatch(perfTest.getTitle()+" ["+perfTest.getClass()+".java]");
		for(int i=0; i<iterations; i++) {
			perfTest.initIteration(false);
			try {
				w.startIteration();
				perfTest.run();
				w.endIteration();
			} finally {
				perfTest.cleanupIteration();
			}
		}
		return w;
	}
}
