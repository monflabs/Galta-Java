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
package performance.parsing;

import java.io.IOException;

import org.monflabs.performance.MicroBenchmarkRunner;

public class SimpleJsonParsingTest extends BaseJsonParsingPerformanceTest {
	
	public static final int WARMUP_ITERATIONS 		= 1;
	public static final int BENCHMARK_ITERATIONS 	= 10;
	
	public static final int PARSING_LOOPS 			= 600000;

	
	public static void main(String[] args) {
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new SimpleJsonParsingTest(JsonLibrary.JSON_JAVA,"Simple JSON parsing using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new SimpleJsonParsingTest(JsonLibrary.JSON_JAVA,"Simple JSON parsing using Java library with string interned"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new SimpleJsonParsingTest(JsonLibrary.GSON_NATIVE,"Simple JSON parsing using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new SimpleJsonParsingTest(JsonLibrary.GSON_NATIVE,"Simple JSON parsing using JsonOrg native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	public static void allTests() {
		System.out.println("====================================================================");
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new SimpleJsonParsingTest(JsonLibrary.JSON_JAVA,"Simple JSON parsing using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new SimpleJsonParsingTest(JsonLibrary.GSON_NATIVE,"Simple JSON parsing using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new SimpleJsonParsingTest(JsonLibrary.GSON_NATIVE,"Simple JSON parsing using JsonOrg native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	private String json;
	
	public SimpleJsonParsingTest(JsonLibrary jsonLib, String title) {
		super(jsonLib,title);
	}
	
	@Override
	public void init() throws IOException {
		json = readResourceString("json/simple/json1.json");
	}
	
	@Override
	public void run() {
		benchmarkParseJson(json,PARSING_LOOPS);
	}

}
