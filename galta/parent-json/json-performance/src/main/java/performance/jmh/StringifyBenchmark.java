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

import org.monflabs.json.JsonFactory;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * Serializing a tree back to text, compact and pretty printed, plus a parse+stringify
 * round trip.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class StringifyBenchmark {

	@Param({"small", "medium", "large", "numbers", "strings", "deep", "pretty", "worldcup"})
	public String dataset;

	private String text;
	private JsonFactory galta;
	private Object galtaTree;
	private ObjectWriter jacksonCompact;
	private ObjectWriter jacksonPretty;
	private JsonNode jacksonTree;
	private Gson gsonCompact;
	private Gson gsonPretty;
	private JsonElement gsonTree;

	@Setup
	public void setup() throws Exception {
		text = Datasets.get(dataset);
		galta = JsonFactory.get();
		galtaTree = galta.parse(text);
		ObjectMapper m = new ObjectMapper();
		jacksonTree = m.readTree(text);
		jacksonCompact = m.writer();
		jacksonPretty = m.writerWithDefaultPrettyPrinter();
		gsonTree = com.google.gson.JsonParser.parseString(text);
		gsonCompact = new Gson();
		gsonPretty = new GsonBuilder().setPrettyPrinting().create();
	}

	@Benchmark
	public String galtaCompact() {
		return galta.stringify(galtaTree, true);
	}

	@Benchmark
	public String galtaPretty() {
		return galta.stringify(galtaTree, false);
	}

	@Benchmark
	public String galtaRoundTrip() {
		return galta.stringify(galta.parse(text), true);
	}

	@Benchmark
	public String jacksonCompact() throws Exception {
		return jacksonCompact.writeValueAsString(jacksonTree);
	}

	@Benchmark
	public String jacksonPretty() throws Exception {
		return jacksonPretty.writeValueAsString(jacksonTree);
	}

	@Benchmark
	public String gsonCompact() {
		return gsonCompact.toJson(gsonTree);
	}

	@Benchmark
	public String gsonPretty() {
		return gsonPretty.toJson(gsonTree);
	}
}
