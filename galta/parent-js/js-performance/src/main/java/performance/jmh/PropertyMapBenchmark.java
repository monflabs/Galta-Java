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
package performance.jmh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.monflabs.galtajs.jsonfactory.StringPropertyMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * The property map of the GaltaJS objects (StringPropertyMap) compared with a LinkedHashMap,
 * which keeps the insertion order too: filling a map, reading every key, replacing every
 * value and iterating the keys.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class PropertyMapBenchmark {

	@Param({"10", "1000", "100000"})
	public int size;

	@Param({"galta", "linkedHashMap"})
	public String map;

	private List<String> keys;
	private Supplier<Map<String,Object>> factory;
	private Map<String,Object> filled;

	@Setup
	public void setup() {
		Random r = new Random(42);
		keys = new ArrayList<>(size);
		for(int i=0; i<size; i++) {
			keys.add("key"+Long.toHexString(r.nextLong()));
		}
		factory = map.equals("galta") ? StringPropertyMap::new : LinkedHashMap::new;
		filled = fill();
	}

	private Map<String,Object> fill() {
		Map<String,Object> m = factory.get();
		for(String k: keys) {
			m.put(k, k);
		}
		return m;
	}

	@Benchmark
	public Map<String,Object> put() {
		return fill();
	}

	@Benchmark
	public int get() {
		int found = 0;
		for(String k: keys) {
			if(filled.get(k)!=null) {
				found++;
			}
		}
		return found;
	}

	@Benchmark
	public Map<String,Object> replace() {
		for(String k: keys) {
			filled.put(k, k);
		}
		return filled;
	}

	@Benchmark
	public int iterateKeys() {
		int h = 0;
		for(String k: filled.keySet()) {
			h += k.length();
		}
		return h;
	}
}
