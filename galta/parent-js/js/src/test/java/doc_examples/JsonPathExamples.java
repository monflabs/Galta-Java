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
package doc_examples;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extensions/JsonPath.md
 */
public class JsonPathExamples extends __BaseTestCase {

	/** The classic JSON Path "store" example. */
	private static final String STORE = """
		const $ = {
		  store: {
		    book: [
		      { category: 'reference', author: 'Nigel Rees',       title: 'Sayings of the Century', price: 8.95 },
		      { category: 'fiction',   author: 'Evelyn Waugh',     title: 'Sword of Honour',        price: 12.99 },
		      { category: 'fiction',   author: 'Herman Melville',  title: 'Moby Dick', isbn: '0-553-21311-3', price: 8.99 },
		      { category: 'fiction',   author: 'J. R. R. Tolkien', title: 'The Lord of the Rings', isbn: '0-395-19395-8', price: 22.99 }
		    ],
		    bicycle: { color: 'red', price: 19.95 }
		  }
		};
		""";

	private final JSEnvironment env = GaltaJSEnvironment.create();

	private Object eval(String path) {
		return env.evaluateScript(STORE + path);
	}

	public void testChildrenAndDescendants() {
		assertEquals(List.of("Nigel Rees", "Evelyn Waugh", "Herman Melville", "J. R. R. Tolkien"), list(eval("$.store.book[*].author")));
		assertEquals(List.of("Nigel Rees", "Evelyn Waugh", "Herman Melville", "J. R. R. Tolkien"), list(eval("$..author")));
		assertEquals(List.of(8.95, 12.99, 8.99, 22.99, 19.95), list(eval("$.store..price")));
		assertEquals(2L, eval("$.store.*[].length"));                   // all children of store
		assertEquals("Sayings of the Century", eval("$.store.book.0.title"));   // numeric member names
	}

	public void testIndexesAndSlices() {
		assertEquals(List.of("Sayings of the Century", "Sword of Honour"), list(eval("$..book[0,1].title")));
		assertEquals(List.of("Sayings of the Century", "Sword of Honour"), list(eval("$..book[0:2].title")));
		assertEquals("The Lord of the Rings", eval("$..book[-1].title"));
		assertEquals(List.of("Sayings of the Century", "Moby Dick"), list(eval("$..book[::2].title")));
		assertEquals(List.of(8.95, 22.99), list(eval("$.store.book[0,-1].price")));
		assertEquals(List.of("red", 19.95), list(eval("$.store.bicycle['color','price']")));
	}

	public void testFilters() {
		assertEquals(List.of("Moby Dick", "The Lord of the Rings"), list(eval("$..book[?(@.isbn)].title")));
		assertEquals(List.of("Sayings of the Century", "Moby Dick"), list(eval("$..book[?(@.price < 10)].title")));
		assertEquals("Sword of Honour", eval("$..book[?(@.author == 'Evelyn Waugh')].title"));
		// The filter can be any function
		assertEquals(List.of("Sword of Honour", "Moby Dick", "The Lord of the Rings"), list(eval("$..book[?(b => b.category == 'fiction')].title")));
		// @ is the whole item when the sequence holds primitives
		assertEquals(List.of(4, 5), list(env.evaluateExpression("[1,4,2,5][?(@ >= 3)]")));
	}

	public void testMapOperator() {
		assertEquals(List.of(10, 20, 30), list(env.evaluateExpression("[1,2,3][*].(@ * 10)")));
		assertEquals(List.of(17.9, 25.98), list(eval("$..book[0:2].(@.price * 2)")));
		assertEquals(List.of("NIGEL REES", "EVELYN WAUGH"), list(eval("$..book[0:2].author.toUpperCase()")));
	}

	public void testMissingPathsAndArrays() {
		// A missing path is an empty sequence, i.e. undefined; use [] to force an Array
		assertSame(org.monflabs.galtajs.rt.RuntimeUtil.UNDEFINED, eval("$..bookz"));
		assertEquals(List.of(), list(eval("$..bookz[]")));
		// A single hit is the item itself; [] keeps it an Array
		assertEquals("red", eval("$..color"));
		assertEquals(List.of("red"), list(eval("$..color[]")));
	}

	public void testUpdatingThroughAPath() {
		Object r = eval("$..book[?(@.price > 10)].price = 9.99; $..book[*].price");
		assertEquals(List.of(8.95, 9.99, 8.99, 9.99), list(r));
		Object r2 = eval("$.store.book[*].price *= 2; $.store.book[*].price");
		assertEquals(List.of(17.9, 25.98, 17.98, 45.98), list(r2));
	}

	public void testWorldCupMatchesPerDay() throws Exception {
		String json = new String(getClass().getResourceAsStream("/json/worldcup-2018/worldcup.json").readAllBytes(), StandardCharsets.UTF_8);
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("worldCupJson", json);
		JSEnvironment env = GaltaJSEnvironment.newBuilder().registerLibrary(globals).build();

		Object r = env.evaluateScript("""
			const worldCup = JSON.parse(worldCupJson);
			// All the matches, across all rounds
			const matches = worldCup.rounds[*].matches[*];
			// Unique, sorted dates
			const dates = [...new Set(matches[*].date[])].sort();
			// One entry per date with the matches played that day
			const perDay = dates.map(date => ({
				date,
				matches: matches[?(@.date == date)][].map(m => `${m.team1.name} vs ${m.team2.name}`)
			}));
			[matches.length, dates.length, perDay[0].date, perDay[0].matches, perDay[1].matches.length]
			""");
		assertEquals(64L, list(r).get(0));
		assertEquals(25L, list(r).get(1));
		assertEquals("2018-06-14", list(r).get(2));
		assertEquals(List.of("Russia vs Saudi Arabia"), list(list(r).get(3)));
		assertEquals(3L, list(r).get(4));
	}

	public void testJsonPathNeedsTheSequenceExtensions() {
		try {
			JavaScriptEnvironment.create().evaluateScript(STORE + "$..author");
			fail();
		} catch(JSRuntimeException e) {
			// Without the extension `..` is not an operator; the parser reports it
		} catch(org.monflabs.galtajs.JSParseException e) {
			assertTrue(e.getMessage().contains("Error while parsing"));
		}
	}
}
