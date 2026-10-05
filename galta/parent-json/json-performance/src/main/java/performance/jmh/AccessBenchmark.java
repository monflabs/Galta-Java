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
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Typed access to the values of a parsed tree, with Galta, Jackson (JsonNode), Gson
 * (JsonElement) and org.json: the total score of the World Cup matches (4 integers in each
 * of the 64 matches), and a short path read repeatedly.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class AccessBenchmark {

	private static final int GOALS = 233;

	private JsonObject galta;
	private JsonNode jackson;
	private com.google.gson.JsonObject gson;
	private org.json.JSONObject jsonOrg;

	@Setup
	public void setup() throws Exception {
		String text = Datasets.get("worldcup");
		galta = (JsonObject)JsonFactory.get().parse(text);
		jackson = new ObjectMapper().readTree(text);
		gson = JsonParser.parseString(text).getAsJsonObject();
		jsonOrg = new org.json.JSONObject(text);
		// The four implementations read the same document
		for(int total: new int[] {galtaScores(), jacksonScores(), gsonScores(), jsonOrgScores()}) {
			if(total!=GOALS) {
				throw new IllegalStateException("Total score "+total+", expected "+GOALS);
			}
		}
	}

	//
	// The total score: a loop over the rounds and their matches
	//

	@Benchmark
	public int galtaScores() {
		int total = 0;
		JsonArray rounds = galta.getArray("rounds");
		for(int r=0; r<rounds.size(); r++) {
			JsonArray matches = rounds.getObject(r).getArray("matches");
			for(int m=0; m<matches.size(); m++) {
				JsonObject match = matches.getObject(m);
				total += match.getInt("score1")+match.getInt("score2")+match.getInt("score1i")+match.getInt("score2i");
			}
		}
		return total;
	}

	@Benchmark
	public int jacksonScores() {
		int total = 0;
		JsonNode rounds = jackson.get("rounds");
		for(int r=0; r<rounds.size(); r++) {
			JsonNode matches = rounds.get(r).get("matches");
			for(int m=0; m<matches.size(); m++) {
				JsonNode match = matches.get(m);
				total += match.get("score1").asInt()+match.get("score2").asInt()+match.get("score1i").asInt()+match.get("score2i").asInt();
			}
		}
		return total;
	}

	@Benchmark
	public int gsonScores() {
		int total = 0;
		com.google.gson.JsonArray rounds = gson.getAsJsonArray("rounds");
		for(int r=0; r<rounds.size(); r++) {
			com.google.gson.JsonArray matches = rounds.get(r).getAsJsonObject().getAsJsonArray("matches");
			for(int m=0; m<matches.size(); m++) {
				com.google.gson.JsonObject match = matches.get(m).getAsJsonObject();
				total += match.get("score1").getAsInt()+match.get("score2").getAsInt()+match.get("score1i").getAsInt()+match.get("score2i").getAsInt();
			}
		}
		return total;
	}

	@Benchmark
	public int jsonOrgScores() {
		int total = 0;
		org.json.JSONArray rounds = jsonOrg.getJSONArray("rounds");
		for(int r=0; r<rounds.length(); r++) {
			org.json.JSONArray matches = rounds.getJSONObject(r).getJSONArray("matches");
			for(int m=0; m<matches.length(); m++) {
				org.json.JSONObject match = matches.getJSONObject(m);
				total += match.getInt("score1")+match.getInt("score2")+match.getInt("score1i")+match.getInt("score2i");
			}
		}
		return total;
	}

	//
	// A short path (rounds[0].matches[0]), read with the typed getters
	//

	@Benchmark
	public Object galtaPath() {
		return galta.getArray("rounds").getObject(0).getArray("matches").getObject(0);
	}

	@Benchmark
	public Object jacksonPath() {
		return jackson.get("rounds").get(0).get("matches").get(0);
	}

	@Benchmark
	public JsonElement gsonPath() {
		return gson.getAsJsonArray("rounds").get(0).getAsJsonObject().getAsJsonArray("matches").get(0);
	}

	@Benchmark
	public Object jsonOrgPath() {
		return jsonOrg.getJSONArray("rounds").getJSONObject(0).getJSONArray("matches").getJSONObject(0);
	}
}
