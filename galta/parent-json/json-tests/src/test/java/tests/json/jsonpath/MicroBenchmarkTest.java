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
package tests.json.jsonpath;

import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.tests.SuiteGuard;
import org.monflabs.util.Console;
import org.monflabs.util.datetime.PeriodFormatter;

import tests.ProjectTestCase;

@SuiteGuard.NotInSuite("benchmark, run manually: it prints timings and asserts nothing")
public class MicroBenchmarkTest extends ProjectTestCase {
	
	private static final Object JSON = JsonObject.parse(
"""
{
    "store": {
        "book": [
            {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            },
            {
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            },
            {
                "category": "fiction",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
            },
            {
                "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
            }
        ],
        "bicycle": {
            "color": "red",
            "price": 19.95
        }
    },
    "expensive": 10
}
""");
	
	
	public void testMicrobenchmark() {
		Console.log("=== COMPILE");
		compileBenchmark(true);
		for(int i=0; i<5; i++) {
			compileBenchmark(false);
		}

		Console.log("\n=== EXECUTE");
		executeBenchmark(true,false);
		for(int i=0; i<5; i++) {
			executeBenchmark(false,false);
		}

		Console.log("\n=== EXECUTE WITH JSON POINTERS");
		executeBenchmark(true,true);
		for(int i=0; i<5; i++) {
			executeBenchmark(false,true);
		}
	}
	
	// For profiling purposes
	public void testMicrobenchmarkCompile() {
		String path = "$.store.book[0].price[23].a.b.dasdsd.ocp";
		int COUNT =  3000000;

		long start1 = System.nanoTime();
		for(int i=0; i<COUNT; i++) {
			@SuppressWarnings("unused")
			Object o = new JsonPathFactory().createJsonPath(path);
		}
		long end1 = System.nanoTime();
		
		long t1 = end1-start1;
		Console.log("Simple: {0}", PeriodFormatter.formatPeriod(t1/1000000));
	}

	private void compileBenchmark(boolean warm) {
		String path = "$.store.book[0].price[23].a.b.dasdsd.ocp";
		int COUNT = warm ? 1000 : 3000000;
		
		long start1 = System.nanoTime();
		for(int i=0; i<COUNT; i++) {
			@SuppressWarnings("unused")
			Object o = JsonPathFactory.get().createJsonPath(path);
		}
		long end1 = System.nanoTime();
		
		long start2 = System.nanoTime();
		for(int i=0; i<COUNT; i++) {
			@SuppressWarnings("unused")
			Object o = com.jayway.jsonpath.JsonPath.compile(path);
		}
		long end2 = System.nanoTime();
		
		long t1 = end1-start1;
		long t2 = end2-start2;
		long d = t2-t1;
		long pc = t2*100L/t1;
		
		if(!warm) {
			Console.log("Simple: {0}, Jayway: {1}, Difference: {2} ({3}%)", PeriodFormatter.formatPeriod(t1/1000000), PeriodFormatter.formatPeriod(t2/1000000), PeriodFormatter.formatPeriod(d/1000000), pc);
		}
	}

	private void executeBenchmark(boolean warm, boolean pointer) {
		String path = "$.store.book[0].price";
		int COUNT = warm ? 1000 : 3000000;
		
		JsonPath p1 = new JsonPathFactory().createJsonPath(path);
		long start1 = System.nanoTime();
		for(int i=0; i<COUNT; i++) {
			@SuppressWarnings("unused")
			Object o = p1.read(JSON,pointer);
		}
		long end1 = System.nanoTime();

		long start2 = System.nanoTime();
		long end2 = -1;
		if(!pointer) { // JsnPointer not available in Jayway
			com.jayway.jsonpath.JsonPath p2 = com.jayway.jsonpath.JsonPath.compile(path);
			for(int i=0; i<COUNT; i++) {
				@SuppressWarnings("unused")
				Object o = p2.read(JSON);
			}
			end2 = System.nanoTime();
		}
		
		long t1 = end1-start1;
		long t2 = end2-start2;
		long d = t2-t1;
		long pc = t2*100L/t1;
		
		if(!warm) {
			if(end2<0) {
				Console.log("Simple: {0}", PeriodFormatter.formatPeriod(t1/1000000));
			} else {
				Console.log("Simple: {0}, Jayway: {1}, Difference: {2} ({3}%)", PeriodFormatter.formatPeriod(t1/1000000), PeriodFormatter.formatPeriod(t2/1000000), PeriodFormatter.formatPeriod(d/1000000), pc);
			}
		}
	}
}
