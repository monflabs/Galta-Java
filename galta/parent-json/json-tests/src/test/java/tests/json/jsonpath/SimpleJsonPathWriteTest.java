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
import org.monflabs.json.jsonpath.JsonPathFactory;

import tests.ProjectTestCase;

public class SimpleJsonPathWriteTest extends ProjectTestCase {
	
	private static final String _JSON = 
"""
{
    "A": {
		"B": {
			"C": 12
		},
        "D": [
			34
		]
	},
	"Z": 56
}
""";
	
	public void testJsonPathWrite() throws Exception {
		JsonObject json = JsonObject.parse(_JSON);
		
		assertTrue( JsonPathFactory.get().createJsonPath("$.A.B.C").write(json,21) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.A.D[0]").write(json,43) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.Z").write(json,65) );
		
		assertTrue( JsonPathFactory.get().createJsonPath("$.X").write(json,11) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.Y.A").write(json,22) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.Y.B[0]").write(json,33) );
		// An index can address an item or append, but not leave a gap
		assertFalse( JsonPathFactory.get().createJsonPath("$.Y.C[5]").write(json,44) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.Y.C[0]").write(json,44) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.A.B.N").write(json,21) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.A.T").write(json,22) );
		assertTrue( JsonPathFactory.get().createJsonPath("$.A.D[0]").write(json,43) );
		
		assertTrue( JsonPathFactory.get().createJsonPath("$.A.B[3]").write(json,55) );
		
		assertFalse( JsonPathFactory.get().createJsonPath("$.Y.B.W").write(json,9999) );
		
		
	}
}
