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


public class WorldCupJsonAccessorsTest extends BaseJsonAccessorsPerformanceTest {
	
	public static final int WARMUP_ITERATIONS 		= 1;
	public static final int BENCHMARK_ITERATIONS 	= 10;

	public static final int BASIC_LOOPS 			= 8000;
	
	public static void main(String[] args) {
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.JSON_JAVA,"Worldcup 2018 JSON accessors using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.JSON_JAVA_INTERNED,"Worldcup 2018 JSON accessors using Java library and interned strinsd"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.GSON_NATIVE,"Worldcup 2018 JSON accessors using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.JSONORG_NATIVE,"Worldcup 2018 JSON accessors using Json org native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	public static void allTests() {
		System.out.println("====================================================================");
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.JSON_JAVA,"Worldcup 2018 JSON accessors using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.JSON_JAVA_INTERNED,"Worldcup 2018 JSON accessors using Java library and interned strings"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.GSON_NATIVE,"Worldcup 2018 JSON accessors using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonAccessorsTest(JsonLibrary.JSONORG_NATIVE,"Worldcup 2018 JSON accessors using Json org native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	private Object worldcup;
	
	public WorldCupJsonAccessorsTest(JsonLibrary jsonLib, String title) {
		super(jsonLib,title);
	}
	
	@Override
	public void init() throws IOException {
		worldcup = readResourceAsJson("json/worldcup-2018/worldcup.json");
	}
	
	
	@Override
	public void run() {
		runBenchmark(BASIC_LOOPS);
	}

	
	//
	// Java Factory
	//
	
	@Override
	protected void accessJava(JsonFactory factory) {
		JsonObject root = (JsonObject)worldcup;
		
		for(int i=0; i<1000; i++) {
			// Typed access
			root.getArray("rounds").getObject(0).getArray("matches").getObject(0);
	
			// Dynamic access
			JsonArray rounds = (JsonArray)root.get("rounds");
			JsonObject v1 = (JsonObject)rounds.get(0);
			JsonArray  v2 = (JsonArray)v1.get("matches");
			v2.get(0);
		}
	}
	
	//
	// GSON
	//

	@Override
	protected void accessGson() {
		com.google.gson.JsonObject root = (com.google.gson.JsonObject)worldcup;
		
		for(int i=0; i<1000; i++) {
			// Typed access
			root.getAsJsonArray("rounds").get(0).getAsJsonObject().getAsJsonArray("matches").get(0).getAsJsonObject();
	
			// Dynamic access
			com.google.gson.JsonArray rounds = (com.google.gson.JsonArray)root.get("rounds");
			com.google.gson.JsonObject v1 = (com.google.gson.JsonObject)rounds.get(0);
			com.google.gson.JsonArray  v2 = (com.google.gson.JsonArray)v1.get("matches");
			v2.get(0);
		}
	}
	
	
	
	//
	// Json Org
	//
	@Override
	protected void accessJsonOrg() {
		JSONObject root = (JSONObject)worldcup;
		
		for(int i=0; i<1000; i++) {
			// Typed access
			root.getJSONArray("rounds").getJSONObject(0).getJSONArray("matches").getJSONObject(0);
	
			// Dynamic access
			JSONArray rounds = (JSONArray)root.get("rounds");
			JSONObject v1 = (JSONObject)rounds.get(0);
			JSONArray  v2 = (JSONArray)v1.get("matches");
			v2.get(0);
		}
	}
}

