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

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.yaml.SnakeYaml;
import org.monflabs.util.Console;

import tests.ProjectTestCase;

public class LetfReferenceTest extends ProjectTestCase {

	public void testOne() throws Exception {
		execute(
				JsonFactory.get().parse("[{\"id\":42,\"name\":\"forty-two\"},{\"id\":1,\"name\":\"one\"}]"), 
				"$[?(@.id==42)].name", 
				JsonFactory.get().parse("[\"forty-two\"]")
		);
	}

	// https://github.com/cburgmer/json-path-comparison/blob/master/regression_suite/regression_suite.yaml
	// https://github.com/cburgmer/json-path-comparison/blob/master/proposals/Proposal_A/test_suite.yaml
	public void testPath() throws Exception {
		//JsonObject tests = (JsonObject)Yaml.parse(support.loadText("json/regression-suite.yaml"));
		JsonObject tests = (JsonObject)SnakeYaml.parse(support.loadText("json/test-suite.yaml"));
		//Console.log(tests.toString());
		
		tests.getArray("queries").forEach( (o) -> {
			JsonObject q = (JsonObject)o;
			Object doc = q.get("document");
			String path = q.getString("selector");
			Object expected = q.get("consensus");
			execute(doc, path, expected);
		});
	}
	
	private void execute(Object doc, String path, Object expected) {
		if(doc!=null && path!=null) {
			try {
				JsonPath p = JsonPathFactory.get().createJsonPath(path);
				Object res = p.read(doc).toJsonArray();
				if(expected!=null) {
					boolean eq = !expected.equals("NOT_SUPPORTED") && support.isJsonEquals(expected, res);
					if(!eq) {
						Console.log("Running JSON Path test: {0}", path);
						Console.log("     FAILED!");
						Console.log("     Doc: {0}", JsonFactory.get().stringify(doc, true));
						Console.log("     Exp: {0}", JsonFactory.get().stringify(expected, true));
						Console.log("     Res: {0}", JsonFactory.get().stringify(res, true));
					}
				}
			} catch(JsonException ex) {
				if(expected==null || !expected.equals("NOT_SUPPORTED") ) {
					Console.log("Running JSON Path test: {0}", path);
					Console.log("     EXCEPTION: {0}", ex.getLocalizedMessage());
					Console.log("     Doc: {0}", JsonFactory.get().stringify(doc, true));
					if(expected!=null) {
						Console.log("     Exp: {0}", JsonFactory.get().stringify(expected, true));
					}
				}
			}
		}
	}	
}
