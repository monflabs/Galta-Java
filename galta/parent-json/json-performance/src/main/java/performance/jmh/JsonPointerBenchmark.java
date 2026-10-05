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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpointer.JsonPointer;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON Pointer (RFC 6901): parsing a pointer, reading a value through it, building one
 * child by child (as JSONPath does for each match) and using pointers as map keys,
 * compared with Jackson's JsonPointer on the same document.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class JsonPointerBenchmark {

	private static final String POINTER = "/rounds/5/matches/1/goals1/0/minute";
	// Escaped tokens (~0, ~1) and a member name
	private static final String ESCAPED = "/a~1b/m~0n/x";

	private JsonObject worldcup;
	private JsonObject escapedDoc;
	private JsonPointer pointer;
	private JsonPointer missing;
	private Map<JsonPointer, Object> map;
	// Distinct pointer strings, more than JsonPointer.of() caches: each parse is a real one
	private String[] distinct;
	private int next;

	private JsonNode jacksonWorldcup;
	private com.fasterxml.jackson.core.JsonPointer jacksonPointer;

	@Setup
	public void setup() throws Exception {
		String json = Datasets.get("worldcup");
		worldcup = (JsonObject)JsonFactory.get().parse(json);
		escapedDoc = (JsonObject)JsonFactory.get().parse("{\"a/b\":{\"m~n\":{\"x\":1}}}");
		pointer = JsonPointer.of(POINTER);
		missing = JsonPointer.of("/rounds/5/matches/1/goals1/7/minute");
		map = new HashMap<>();
		for(int r=0; r<20; r++) {
			map.put(JsonPointer.of("/rounds/"+r+"/matches/1/goals1/0/minute"), r);
		}
		distinct = new String[1024];
		for(int i=0; i<distinct.length; i++) {
			distinct[i] = "/rounds/"+(i%20)+"/matches/"+(i/20%3)+"/goals1/0/minute"+(i/60==0 ? "" : "/x"+i/60);
		}
		jacksonWorldcup = new ObjectMapper().readTree(json);
		jacksonPointer = com.fasterxml.jackson.core.JsonPointer.compile(POINTER);
	}

	private String nextPointer() {
		String s = distinct[next];
		next = (next+1) & (distinct.length-1);
		return s;
	}

	@Benchmark
	public Object parse() {
		return JsonPointer.of(nextPointer());
	}

	@Benchmark
	public Object parseJackson() {
		return com.fasterxml.jackson.core.JsonPointer.compile(nextPointer());
	}

	// The same pointer string again: JsonPointer.of() caches it
	@Benchmark
	public Object parseRepeated() {
		return JsonPointer.of(POINTER);
	}

	@Benchmark
	public Object read() {
		return pointer.read(worldcup);
	}

	@Benchmark
	public Object readJackson() {
		return jacksonWorldcup.at(jacksonPointer);
	}

	@Benchmark
	public boolean readMissing() {
		return missing.exists(worldcup);
	}

	@Benchmark
	public Object parseAndRead() {
		return JsonPointer.of(nextPointer()).read(worldcup);
	}

	@Benchmark
	public Object parseAndReadJackson() {
		return jacksonWorldcup.at(nextPointer());
	}

	@Benchmark
	public Object parseAndReadRepeated() {
		return JsonPointer.of(POINTER).read(worldcup);
	}

	@Benchmark
	public Object parseAndReadEscaped() {
		return JsonPointer.of(ESCAPED).read(escapedDoc);
	}

	// JSONPath builds the pointer of every match child by child
	@Benchmark
	public Object childrenAndRead() {
		return JsonPointer.EMPTY.getChild("rounds").getChild(5).getChild("matches").getChild(1)
				.getChild("goals1").getChild(0).getChild("minute").read(worldcup);
	}

	// A pointer built for the lookup (not parsed: not cached)
	@Benchmark
	public Object mapLookup() {
		return map.get(JsonPointer.EMPTY.getChild("rounds").getChild(7).getChild("matches").getChild(1)
				.getChild("goals1").getChild(0).getChild("minute"));
	}

	@Benchmark
	public String toPointerString() {
		return JsonPointer.EMPTY.getChild("rounds").getChild(5).getChild("a/b").toJsonPointerString();
	}
}
