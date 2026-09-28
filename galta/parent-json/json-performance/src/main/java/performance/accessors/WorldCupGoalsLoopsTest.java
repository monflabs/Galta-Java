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
package performance.accessors;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.performance.MicroBenchmarkRunner;


public class WorldCupGoalsLoopsTest extends BaseJsonAccessorsPerformanceTest {
	
	public static final int WARMUP_ITERATIONS 		= 1;
	public static final int BENCHMARK_ITERATIONS 	= 10;

	public static final int GOAL_LOOPS 				= 80000;
	
	
	public static void main(String[] args) {
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.JSON_JAVA,"Worldcup 2018 JSON accessors using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.JSON_JAVA_INTERNED,"Worldcup 2018 JSON accessors using Java library and interned strings"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.GSON_NATIVE,"Worldcup 2018 JSON accessors using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.JSONORG_NATIVE,"Worldcup 2018 JSON accessors using Json Org native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	public static void allTests() {
		System.out.println("====================================================================");
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.JSON_JAVA,"Worldcup 2018 JSON accessors using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.JSON_JAVA_INTERNED,"Worldcup 2018 JSON accessors using Java library and interned strings"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.GSON_NATIVE,"Worldcup 2018 JSON accessors using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupGoalsLoopsTest(JsonLibrary.JSONORG_NATIVE,"Worldcup 2018 JSON accessors using Json Org native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	private Object worldcup;
	
	public WorldCupGoalsLoopsTest(JsonLibrary jsonLib, String title) {
		super(jsonLib,title);
	}
	
	@Override
	public void init() throws IOException {
		worldcup = readResourceAsJson("json/worldcup-2018/worldcup.json");
	}
	
	
	@Override
	public void run() {
		runBenchmark(GOAL_LOOPS);
	}

	
	//
	// Java Factory
	//
	
	@Override
	protected void accessJava(JsonFactory factory) {
		JsonObject root = (JsonObject)worldcup;
		int goals = javaCalculateGoals(root);
		if(goals!=233) {
			System.out.println("Goals="+goals);
			throw new IllegalStateException();
		}
	}
	private int javaCalculateGoals(JsonObject root) {
		int agg = 0;
		JsonArray rounds = root.getArray("rounds");
		int rsz = rounds.size();
		for(int ri=0; ri<rsz; ri++) {
			JsonArray matches = rounds.getObject(ri).getArray("matches");
			int msz = matches.size();
			for(int mi=0; mi<msz; mi++) {
				JsonObject match = matches.getObject(mi);
				int s1 = match.getInt("score1");
				int s2 = match.getInt("score2");
				int s3 = match.getInt("score1i");
				int s4 = match.getInt("score2i");
				agg += (s1+s2+s3+s4);
			}
		}
		return agg;
	}

	
	//
	// GSON
	//

	@Override
	protected void accessGson() {
		com.google.gson.JsonObject root = (com.google.gson.JsonObject)worldcup;		
		int goals = gsonCalculateGoals(root);
		if(goals!=233) {
			System.out.println("Goals="+goals);
			throw new IllegalStateException();
		}
	}
	private int gsonCalculateGoals(com.google.gson.JsonObject root) {
		int agg = 0;
		com.google.gson.JsonArray rounds = root.getAsJsonArray("rounds");
		int rsz = rounds.size();
		for(int ri=0; ri<rsz; ri++) {
			com.google.gson.JsonArray matches = rounds.get(ri).getAsJsonObject().getAsJsonArray("matches");
			int msz = matches.size();
			for(int mi=0; mi<msz; mi++) {
				com.google.gson.JsonObject match = matches.get(mi).getAsJsonObject();
				int s1 = match.get("score1").getAsInt();
				int s2 = match.get("score2").getAsInt();
				int s3 = match.get("score1i").getAsInt();
				int s4 = match.get("score2i").getAsInt();
				agg += (s1+s2+s3+s4);
			}
		}
		return agg;
	}
	
	
	//
	// Json Org
	//
	@Override
	protected void accessJsonOrg() {
		JSONObject root = (JSONObject)worldcup;		
		int goals = jsonOrgCalculateGoals(root);
		if(goals!=233) {
			System.out.println("Goals="+goals);
			throw new IllegalStateException();
		}
	}
	private int jsonOrgCalculateGoals(JSONObject root) {
		int agg = 0;
		JSONArray rounds = root.getJSONArray("rounds");
		int rsz = rounds.length();
		for(int ri=0; ri<rsz; ri++) {
			JSONArray matches = rounds.getJSONObject(ri).getJSONArray("matches");
			int msz = matches.length();
			for(int mi=0; mi<msz; mi++) {
				JSONObject match = matches.getJSONObject(mi);
				int s1 = match.getInt("score1");
				int s2 = match.getInt("score2");
				int s3 = match.getInt("score1i");
				int s4 = match.getInt("score2i");
				agg += (s1+s2+s3+s4);
			}
		}
		return agg;
	}

}

