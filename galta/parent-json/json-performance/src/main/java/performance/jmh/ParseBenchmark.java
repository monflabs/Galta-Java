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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import com.google.gson.JsonElement;

/**
 * Parsing to a tree: Galta (default factory), Jackson tree model, Gson tree model.
 * "String" parses a String, "Stream" parses UTF-8 bytes from an InputStream.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ParseBenchmark {

	@Param({"small", "medium", "large", "numbers", "strings", "deep", "pretty", "worldcup"})
	public String dataset;

	private String text;
	private byte[] bytes;
	private JsonFactory galta;
	private ObjectMapper jackson;

	@Setup
	public void setup() {
		text = Datasets.get(dataset);
		bytes = text.getBytes(StandardCharsets.UTF_8);
		galta = JsonFactory.get();
		jackson = new ObjectMapper();
	}

	@Benchmark
	public Object galtaString() {
		return galta.parse(text);
	}

	@Benchmark
	public Object galtaStream() {
		return galta.parse(new ByteArrayInputStream(bytes));
	}

	@Benchmark
	public JsonNode jacksonString() throws Exception {
		return jackson.readTree(text);
	}

	@Benchmark
	public JsonNode jacksonStream() throws Exception {
		return jackson.readTree(new ByteArrayInputStream(bytes));
	}

	@Benchmark
	public JsonElement gsonString() {
		return com.google.gson.JsonParser.parseString(text);
	}

	@Benchmark
	public JsonElement gsonStream() {
		return com.google.gson.JsonParser.parseReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
	}
}
