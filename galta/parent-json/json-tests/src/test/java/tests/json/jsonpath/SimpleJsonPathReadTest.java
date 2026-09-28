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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.jsonpath.JaywayJsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpath.JsonValues;

import com.jayway.jsonpath.JsonPath;

import tests.ProjectTestCase;

public class SimpleJsonPathReadTest extends ProjectTestCase {
	
	private static final String JSON = 
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
""";
	
	private static JsonPathFactory simpleFactory = new JsonPathFactory();

	
	public void testJsonPathRead() throws Exception {
		checkPath(JsonArray.of(8.95), "$.store.book[0].price");
		checkPath(JsonArray.of("Evelyn Waugh"), "$.store.book[1].author");
		checkPath(JsonArray.of(), "$.store.book[100].price");
	}
	
	private void checkPath(Object result, String jsonPath) throws Exception {
		Object sv = simpleFactory.getJsonPath(jsonPath).read(JsonFactory.get().parse(JSON)).toJsonArray();
		Object pv = new JaywayJsonPath(JsonPath.compile(jsonPath)).read(JsonFactory.get().parse(JSON)).toJsonArray();
		
		if(support.isVerbose()) {
			support.print("Test: "+jsonPath);
			support.print("  path: "+JsonFactory.getFactory().get().stringify(sv));
			support.print("  nav : "+JsonFactory.getFactory().get().stringify(pv));
			support.print("");
		}
		
		assertEquals(result, sv);
		assertEquals(sv, pv);
	}

	public void testAdvancedJson() throws Exception {
		checkAdvanced(
"""
[
  12.99,
  8.99
]
""",
"/store/book/1/price,/store/book/2/price", 
"$..book[1:3].price");

		checkAdvanced(
"""
[
  8.95,
  12.99,
  8.99
]
""",
"/store/book/0/price,/store/book/1/price,/store/book/2/price", 
"$..book[:3].price");

		checkAdvanced(
"""
[
  12.99,
  8.99,
  22.99
]
""",
"/store/book/1/price,/store/book/2/price,/store/book/3/price", 
"$..book[1:].price");

		checkAdvanced(
"""
[
  8.95,
  12.99,
  8.99,
  22.99
]
""",
"/store/book/0/price,/store/book/1/price,/store/book/2/price,/store/book/3/price", 
"$..book[:].price");
		
		checkAdvanced(
"""
[
  8.95,
  12.99
]
""",
"/store/book/0/price,/store/book/1/price", 
"$..book[0,1].price");		
		
		checkAdvanced(
"""
[
  8.99,
]
""",
"/store/book/2/price", 
"$..book[-2].price");		

		checkAdvanced(
"""
[
  [
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
  {
    "color": "red",
    "price": 19.95
  }
]
""",
"/store/book,/store/bicycle", 
"$.store.*");

		checkAdvanced(
"""
[
  "Nigel Rees",
  "Evelyn Waugh",
  "Herman Melville",
  "J. R. R. Tolkien"
]
""",
"/store/book/0/author,/store/book/1/author,/store/book/2/author,/store/book/3/author", 
"$..author");
		
		checkAdvanced(
"""
[
  "Nigel Rees",
  "Evelyn Waugh",
  "Herman Melville",
  "J. R. R. Tolkien"
]
""",
"/store/book/0/author,/store/book/1/author,/store/book/2/author,/store/book/3/author", 
"$.store.book[*].author");
		
		checkAdvanced(
"""
[
  8.95,
  12.99,
  8.99,
  22.99,
  19.95
]
""",
"/store/book/0/price,/store/book/1/price,/store/book/2/price,/store/book/3/price,/store/bicycle/price", 
"$.store..price");
		
		checkAdvanced(
"""
[
  {
    "category": "fiction",
    "author": "Herman Melville",
    "title": "Moby Dick",
    "isbn": "0-553-21311-3",
    "price": 8.99
  }
]
""",
"/store/book/2", 
"$..book[2]");
	}
	private void checkAdvanced(String result, String resultPath, String jsonPath) throws Exception {
		Object j = JsonFactory.get().parse(result);
		JsonValues pr = simpleFactory.getJsonPath(jsonPath).read(JsonFactory.get().parse(JSON), true);
		
		support.assertJsonEquals(j, pr.toJsonArray());
		
		StringBuilder b = new StringBuilder();
		for(int i=0; i<pr._size(); i++) {
			if(i>0) {
				b.append(',');
			}
			b.append(pr.getPointer(i));
		}
		String paths = b.toString();
		
		assertEquals(resultPath, paths);
	} 
}
