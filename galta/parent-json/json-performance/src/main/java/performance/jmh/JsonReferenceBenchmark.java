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
import org.monflabs.json.jsonreference.JsonReference;
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
 * "$ref" resolution on schema-like documents: definitions referring to each other
 * (recursively too), in one document with and without a base URL, and across documents
 * referring to each other by relative URLs. Resolution changes the document, so every
 * operation parses it first: subtract the parse* benchmarks to get the resolution alone.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class JsonReferenceBenchmark {

	private static final int DEFINITIONS = 200;
	private static final int DOCUMENTS = 10;
	private static final String BASE = "https://example.com/schemas/";

	private JsonFactory factory;
	private String local;
	private Map<String,String> documents;

	@Setup
	public void setup() {
		factory = JsonFactory.get();
		local = document(-1, DEFINITIONS);
		documents = new HashMap<>();
		for(int d=0; d<DOCUMENTS; d++) {
			documents.put(BASE+"doc"+d+".json", document(d, DEFINITIONS/DOCUMENTS));
		}
	}

	// Definitions with two references each (one to a following definition, so that the
	// graph is recursive) and a plain property. In a set of documents, the second
	// reference designates a definition of the next document.
	private static String document(int doc, int count) {
		StringBuilder b = new StringBuilder();
		b.append("{\"root\":{\"$ref\":\"#/definitions/D0\"},\"definitions\":{");
		for(int i=0; i<count; i++) {
			if(i>0) {
				b.append(',');
			}
			String other = doc<0 ? "#/definitions/D"+((i*7+3)%count)
					: "doc"+((doc+1)%DOCUMENTS)+".json#/definitions/D"+((i*7+3)%count);
			b.append("\"D").append(i).append("\":{\"type\":\"object\",\"properties\":{")
				.append("\"next\":{\"$ref\":\"#/definitions/D").append((i+1)%count).append("\"},")
				.append("\"other\":{\"$ref\":\"").append(other).append("\"},")
				.append("\"name\":{\"type\":\"string\",\"maxLength\":").append(i).append("}}}");
		}
		return b.append("}}").toString();
	}

	@Benchmark
	public Object parseLocal() {
		return factory.parse(local);
	}

	@Benchmark
	public Object resolveLocal() {
		Object doc = factory.parse(local);
		return JsonReference.resolve(factory, doc, new JsonReference.Resolver(doc), false);
	}

	@Benchmark
	public Object resolveLocalWithBaseUrl() {
		Object doc = factory.parse(local);
		return JsonReference.resolve(factory, doc, new JsonReference.Resolver(doc, BASE+"root.json"), false);
	}

	@Benchmark
	public Object parseDocuments() {
		Object last = null;
		for(String s: documents.values()) {
			last = factory.parse(s);
		}
		return last;
	}

	@Benchmark
	public Object resolveDocuments() {
		String rootUrl = BASE+"doc0.json";
		Object doc = factory.parse(documents.get(rootUrl));
		JsonReference.Resolver resolver = new JsonReference.Resolver(doc, rootUrl) {
			@Override
			public Object apply(JsonFactory f, String url) {
				String s = documents.get(url);
				return s!=null ? f.parse(s) : super.apply(f, url);
			}
		};
		return JsonReference.resolve(factory, doc, resolver, false);
	}
}
