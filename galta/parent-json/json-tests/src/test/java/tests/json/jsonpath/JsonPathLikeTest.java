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

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.jsonpath.MonfLabsJsonPathConfiguration;

import com.jayway.jsonpath.JsonPath;

import tests.ProjectTestCase;

public class JsonPathLikeTest extends ProjectTestCase {
	
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
	
	private JsonValues books;
	
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		
		MonfLabsJsonPathConfiguration.initialize();
		
		//jsonObject = (JsonObject)support.loadText("json/worldcup.json");
		//jsonObject = (JsonObject)support.loadJson("json/jsonpath.json");
		books = JsonValues.parse(JSON);
	}
	
	/*
		$.store..price							The price of everything
		$..book[2]								The third book
		$..book[-2]								The second to last book
		$..book[0,1]							The first two books
		$..book[:2]								All books from index 0 (inclusive) until index 2 (exclusive)
		$..book[1:2]							All books from index 1 (inclusive) until index 2 (exclusive)
		$..book[-2:]							Last two books
		$..book[2:]								Book number two from tail
		$..book[?(@.isbn)]						All books with an ISBN number
		$.store.book[?(@.price < 10)]			All books in store cheaper than 10
		$..book[?(@.price <= $['expensive'])]	All books in store that are not "expensive"
		$..book[?(@.author =~ /.*REES/i)]		All books matching regex (ignore case)
		$..*									Give me everything
		$..book.length()						The number of books
	 */

	public void testPath1() throws Exception {
		JsonValues v = books
				.get("store")
				.find("price");
		checkPath(v, "$.store..price");
	}
	public void testPath2() throws Exception {
		JsonValues v = books
				.find("book")
				.get(2);
		checkPath(v, "$..book[2]");
	}
	public void testPath3() throws Exception {
		JsonValues v = books
				.find("book")
				.get(-2);
		checkPath(v, "$..book[-2]");
	}
	public void testPath4() throws Exception {
		JsonValues v = books
				.find("book")
				.get(0,1);
		checkPath(v, "$..book[0,1]");
	}
	public void testPath5() throws Exception {
		JsonValues v = books
				.find("book").flat()
				.slice(null,2);
		checkPath(v, "$..book[:2]");
	}
	public void testPath6() throws Exception {
		JsonValues v = books
				.find("book").flat()
				.slice(1,2);
		checkPath(v, "$..book[1:2]");
	}
	public void testPath7() throws Exception {
		JsonValues v = books
				.find("book").flat()
				.slice(-2,null);
		checkPath(v, "$..book[-2:]");
	}
	public void testPath8() throws Exception {
		JsonValues v = books
				.find("book").flat()
				.slice(2,null);
		checkPath(v, "$..book[2:]");
	}
	public void testPath9() throws Exception {
		JsonValues v = books
				.find("book").flat()
				.filter((jv) -> {
					return jv.has("isbn");
				});
		checkPath(v, "$..book[?(@.isbn)]");
	}
	public void testPath10() throws Exception {
		// lt() will only return true if price exists
		JsonValues v = books
				.find("book").flat()
				.filter((jv) -> {
					return jv.get("price").lt(10);
				});
		checkPath(v, "$.store.book[?(@.price < 10)]");
	}
	public void testPath10_2() throws Exception {
		// <10 will be true even if price doesn't exist (default to 0)
		JsonValues v = books
				.find("book").flat()
				.filter((jv) -> {
					return jv.getInt("price")<10;
				});
		checkPath(v, "$.store.book[?(@.price < 10)]");
	}
	public void testPath11() throws Exception {
		int expensive = JsonUtil.checkInt(books.get("expensive").intValue());
		JsonValues v = books
				.find("book").flat()
				.filter((jv) -> {
					return jv.getInt("price")<expensive;
				});
		checkPath(v, "$..book[?(@.price <= $['expensive'])]");
	}
	public void testPath12() throws Exception {
		JsonValues v = books
				.find("book").flat()
				.filter((jv) -> {
					return jv.get("author").matches("(?i).*REES");
				});
		checkPath(v, "$..book[?(@.author =~ /.*REES/i)]");
	}
	public void testPath13() throws Exception {
		JsonValues v = books
				.find(null,true);
		checkPath(v, "$..*");
	}
	public void testPath14() throws Exception {
		JsonValues v = JsonValues.of(books.find("book").flat()._size());
		checkPath(v, "$..book.length()");
	}
	
	private void checkPath(JsonValues result, String jsonPath) throws Exception {
		Object pathResult = JsonPath.parse(JSON).read(jsonPath);
		JsonValues r;
		if(pathResult instanceof Integer) {
			r = JsonValues.of(pathResult);
		} else if(pathResult instanceof String) {
			r = JsonValues.of(pathResult);
		} else if(pathResult instanceof Boolean) {
			r = JsonValues.of(pathResult);
		} else {
			r = JsonValues.flat((JsonArray)pathResult);
			//int v = 1/0; //JsonValues.of(pathResult);
		}
		
		if(support.isVerbose()) {
			support.print("Test: "+jsonPath);
			support.print("  path: "+r.stringify());
			support.print("  nav : "+result.stringify());
			support.print("");
		}
		
		assertEquals(r, result);
	}
}
