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

import java.util.concurrent.TimeUnit;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Reading values from an already parsed tree (sanity check that the containers stay
 * fast to navigate): a JSONPath query and a hand-written loop over the same data.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ReadBenchmark {

	private JsonObject worldcup;
	private JsonPath goalsPath;

	@Setup
	public void setup() {
		worldcup = (JsonObject)JsonFactory.get().parse(Datasets.get("worldcup"));
		goalsPath = JsonPathFactory.get().createJsonPath("$.rounds[*].matches[*].goals1[*].minute");
	}

	@Benchmark
	public Object jsonPathGoals() {
		return goalsPath.read(worldcup).toJsonArray();
	}

	@Benchmark
	public int loopGoals() {
		int total = 0;
		JsonArray rounds = worldcup.getArray("rounds");
		for(int r=0; r<rounds.size(); r++) {
			JsonArray matches = rounds.getObject(r).getArray("matches");
			for(int m=0; m<matches.size(); m++) {
				if(matches.getObject(m).get("goals1") instanceof JsonArray goals) {
					for(int g=0; g<goals.size(); g++) {
						total += goals.getObject(g).getInt("minute");
					}
				}
			}
		}
		return total;
	}
}
