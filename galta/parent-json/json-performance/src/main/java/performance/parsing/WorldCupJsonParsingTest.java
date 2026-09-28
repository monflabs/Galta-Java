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

public class WorldCupJsonParsingTest extends BaseJsonParsingPerformanceTest {
	
	public static final int WARMUP_ITERATIONS 		= 1;
	public static final int BENCHMARK_ITERATIONS 	= 10;

	public static final int PARSING_LOOPS 			= 1000;

	
	public static void main(String[] args) {
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new WorldCupJsonParsingTest(JsonLibrary.JSON_JAVA,"Worldcup 2018 JSON parsing using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonParsingTest(JsonLibrary.GSON_NATIVE,"Worldcup 2018 JSON parsing using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonParsingTest(JsonLibrary.JSONORG_NATIVE,"Worldcup 2018 JSON parsing using Json Org native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	public static void allTests() {
		System.out.println("====================================================================");
		try {
			MicroBenchmarkRunner r = new MicroBenchmarkRunner();
			r.run(new WorldCupJsonParsingTest(JsonLibrary.JSON_JAVA,"Worldcup 2018 JSON parsing using Java library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonParsingTest(JsonLibrary.GSON_NATIVE,"Worldcup 2018 JSON parsing using GSON native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
			r.run(new WorldCupJsonParsingTest(JsonLibrary.JSONORG_NATIVE,"Worldcup 2018 JSON parsing using Json Org native library"),WARMUP_ITERATIONS,BENCHMARK_ITERATIONS).dump();
		} catch(Exception e) { e.printStackTrace(); }
	}

	private String worldcup;
	private String worldcup_groups;
	private String worldcup_stadiums;
	private String worldcup_standings;
	private String worldcup_teams;
	
	public WorldCupJsonParsingTest(JsonLibrary jsonLib, String title) {
		super(jsonLib,title);
	}
	
	@Override
	public void init() throws IOException {
		// https://github.com/openfootball/worldcup.json
		worldcup = readResourceString("json/worldcup-2018/worldcup.json");
		worldcup_groups = readResourceString("json/worldcup-2018/worldcup.groups.json");
		worldcup_stadiums = readResourceString("json/worldcup-2018/worldcup.stadiums.json");
		worldcup_standings = readResourceString("json/worldcup-2018/worldcup.standings.json");
		worldcup_teams = readResourceString("json/worldcup-2018/worldcup.teams.json");
	}
	
	@Override
	public void run() {
		benchmarkParseJson(worldcup,PARSING_LOOPS);
		benchmarkParseJson(worldcup_groups,PARSING_LOOPS);
		benchmarkParseJson(worldcup_stadiums,PARSING_LOOPS);
		benchmarkParseJson(worldcup_standings,PARSING_LOOPS);
		benchmarkParseJson(worldcup_teams,PARSING_LOOPS);
	}

}

