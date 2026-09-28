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
package tests.json.factory;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.parser.JsonParser;
import org.monflabs.json.parser.ParseException;
import org.monflabs.json.parser.SourceCode;

import tests.ProjectTestCase;

/**
 * Parser: strict mode, error reporting, nesting depth, reviver context.
 */
public class ParserEdgeCasesTest extends ProjectTestCase {

	private static Object strict(String json) throws IOException {
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setStrict(true);
		return p.parse(json);
	}
	private static Object strictReader(String json) throws IOException {
		JsonParser.ReaderParser p = new JsonParser.ReaderParser(JsonFactory.get());
		p.setStrict(true);
		return p.parse(new StringReader(json));
	}
	private static Object lenient(String json) throws IOException {
		return new JsonParser.StringParser(JsonFactory.get()).parse(json);
	}

	public void testStrictRejectsControlCharactersEverywhere() throws Exception {
		// Fast path
		assertThrows(ParseException.class, () -> strict("\"a\u0001b\""));
		// Slow path, after an escape sequence
		assertThrows(ParseException.class, () -> strict("\"\\n\u0001\""));
		assertThrows(ParseException.class, () -> strict("\"\\n\u001f\""));
		// A string longer than the parser buffer
		String longString = "\"" + "x".repeat(3000) + "\u0002\"";
		assertThrows(ParseException.class, () -> strict(longString));
		assertThrows(ParseException.class, () -> strictReader(longString));
		// With a reviver (always the slow path)
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setStrict(true);
		p.setReviver((c,k,v,ctx) -> v);
		assertThrows(ParseException.class, () -> p.parse("[\"a\u0001\"]"));
		// DEL (0x7F) and non ASCII are fine
		assertEquals("a\u007f\u00e9", strict("\"a\u007f\u00e9\""));
		// Lenient mode keeps them
		assertEquals("\\n\u0001".replace("\\n", "\n"), lenient("\"\\n\u0001\""));
	}

	public void testStrictRejectsSingleQuoteEscape() throws Exception {
		assertThrows(ParseException.class, () -> strict("\"it\\'s\""));
		assertEquals("it's", lenient("\"it\\'s\""));
		assertEquals("it's", lenient("'it\\'s'"));
	}

	public void testLineContinuation() throws Exception {
		// Lenient mode only: a backslash before a line terminator is removed with it
		assertEquals("ab", lenient("\"a\\\nb\""));
		assertEquals("ab", lenient("\"a\\\r\nb\""));   // CRLF is one terminator
		assertEquals("ab", lenient("\"a\\\rb\""));
		assertEquals("a\nb", lenient("\"a\\\n\nb\""));   // only one terminator is removed
		assertThrows(ParseException.class, () -> strict("\"a\\\r\nb\""));
	}

	public void testExponentWithoutDigits() throws Exception {
		for(String s: new String[] {"1e+", "[1e+]", "1E-", "[1e]", "1.5e"}) {
			ParseException ex = assertThrows(s, ParseException.class, () -> strict(s));
			assertTrue(s, ex.getPosition()>0);
			assertThrows(s, JsonException.class, () -> lenient(s));
		}
		assertEquals(100.0, lenient("1e+2"));
	}

	public void testUnexpectedTokens() {
		ParseException ex = assertThrows(ParseException.class, () -> strict("[truex]"));
		assertEquals(ParseException.ERROR_UNEXPECTED_TOKEN, ex.getErrorType());
		assertEquals(1, ex.getPosition());
		assertEquals("truex", ex.getUnexpectedObject());
		assertTrue(ex.getMessage(), ex.getMessage().contains("truex"));
		assertThrows(ParseException.class, () -> strict("nul"));
		assertThrows(ParseException.class, () -> strict("[fals]"));
	}

	public void testEndOfInput() {
		for(String s: new String[] {"{\"a\":1", "{\"a\":1,", "{", "[1", "[1,", "\"abc", "{\"a\"", "\"a\\"}) {
			ParseException ex = assertThrows(s, ParseException.class, () -> strict(s));
			assertEquals(s, ParseException.ERROR_UNEXPECTED_EOF, ex.getErrorType());
			assertFalse(s+": "+ex.getMessage(), ex.getMessage().contains("\uffff"));
		}
		// An invalid escape reports the character, not "EOF"
		ParseException ex = assertThrows(ParseException.class, () -> strict("\"a\\qb\""));
		assertEquals(ParseException.ERROR_UNEXPECTED_CHAR, ex.getErrorType());
		assertEquals('q', ex.getUnexpectedObject());
	}

	public void testCommentBetweenKeyAndColon() throws Exception {
		JsonObject o = (JsonObject)lenient("{ a /* comment */ : 1, \"b\" // line\n : 2 }");
		assertEquals(1, o.get("a"));
		assertEquals(2, o.get("b"));
	}

	public void testNullNameIsAName() throws Exception {
		JsonObject o = (JsonObject)lenient("{null: 1, true: 2}");
		assertEquals(1, o.get("null"));
		assertEquals(2, o.get("true"));
		assertFalse(o.containsKey(null));
		assertEquals("{\"null\":1,\"true\":2}", o.stringify());
	}

	public void testNestingDepth() throws Exception {
		String deep = "[".repeat(200000);
		// Every entry point reports an error instead of a StackOverflowError
		assertThrows(JsonException.class, () -> JsonFactory.get().parse(deep));
		assertThrows(JsonException.class, () -> JsonFactory.get().parse(new StringReader(deep)));
		assertThrows(JsonException.class, () -> JsonFactory.get().parse(new java.io.ByteArrayInputStream(deep.getBytes())));
		assertThrows(JsonException.class, () -> JsonObject.parse("{\"a\":" + "{\"a\":".repeat(5000) + "1" + "}".repeat(5001)));
		// The default depth accepts reasonably deep contents
		int n = JsonParser.DEFAULT_MAX_DEPTH;
		Object ok = lenient("[".repeat(n) + "]".repeat(n));
		assertTrue(ok instanceof JsonArray);
		assertThrows(ParseException.class, () -> lenient("[".repeat(n+1) + "]".repeat(n+1)));
		// The limit is configurable
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setMaxDepth(2);
		assertEquals(2, p.getMaxDepth());
		assertTrue(p.parse("[[1],{\"a\":1}]") instanceof JsonArray);
		assertThrows(ParseException.class, () -> p.parse("[[[1]]]"));
		// ... and the parser can be reused after an error
		assertTrue(p.parse("[[2]]") instanceof JsonArray);
	}

	private static class SpyReader extends StringReader {
		boolean resetCalled;
		SpyReader(String s) {
			super(s);
		}
		@Override
		public void reset() throws IOException {
			resetCalled = true;
			super.reset();
		}
	}

	public void testErrorDoesNotConsumeTheReader() throws Exception {
		// The caller's reader is never rewound (and then read to the end) to build the message
		SpyReader r = new SpyReader("[1,]\nREST");
		JsonParser.ReaderParser p = new JsonParser.ReaderParser(JsonFactory.get());
		p.setStrict(true);
		ParseException ex = assertThrows(ParseException.class, () -> p.parse(r));
		assertNotNull(ex.getMessage());
		assertFalse(r.resetCalled);
		// A String parse shows the source around the error
		ParseException ex2 = assertThrows(ParseException.class, () -> strict("[1,\n2,\n]"));
		assertTrue(ex2.getMessage(), ex2.getMessage().contains("^^^"));
	}

	public void testSourceCodePositions() {
		SourceCode s = new SourceCode("[1,\n\n\n  x]");
		SourceCode.LineCol lc = s.findCodePosition(8);
		assertEquals(4, lc.getLine());
		assertEquals(3, lc.getCol());
		SourceCode s2 = new SourceCode("a\nbc");
		assertEquals(2, s2.findCodePosition(3).getLine());
		assertEquals(2, s2.findCodePosition(3).getCol());
		// CRLF is one line terminator, a lone CR is one too
		SourceCode s3 = new SourceCode("a\r\nb\rc");
		assertEquals(2, s3.findCodePosition(3).getLine());
		assertEquals(1, s3.findCodePosition(3).getCol());
		assertEquals(3, s3.findCodePosition(5).getLine());
		assertEquals(1, s3.findCodePosition(0).getCol());
		// The caret is under the character
		String code = new SourceCode("abc").extractSourceCode(5, new SourceCode.LineCol(1, 2));
		String[] lines = code.split("\n");
		assertEquals(lines[0].indexOf('b'), lines[1].indexOf('^'));
	}

	public void testReviverContextFromReader() throws Exception {
		// Without the source text, a string context is the JSON string literal
		List<String> contexts = new ArrayList<>();
		JsonParser.ReaderParser p = new JsonParser.ReaderParser(JsonFactory.get());
		p.setReviver((c,k,v,ctx) -> { contexts.add(ctx); return v; });
		p.parse(new StringReader("[\"a\\\"b\", 12.5, true]"));
		assertEquals("\"a\\\"b\"", contexts.get(0));
		assertEquals("12.5", contexts.get(1));
		assertEquals("true", contexts.get(2));
		// With the source text, the exact raw text
		List<String> raw = new ArrayList<>();
		JsonParser.StringParser sp = new JsonParser.StringParser(JsonFactory.get());
		sp.setReviver((c,k,v,ctx) -> { raw.add(ctx); return v; });
		sp.parse("[\"\\u0061\", 1e2]");
		assertEquals("\"\\u0061\"", raw.get(0));
		assertEquals("1e2", raw.get(1));
	}

	public void testInternStrings() throws Exception {
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setInternStrings(true);
		assertTrue(p.isInternStrings());
		JsonArray a = (JsonArray)p.parse("[{\"key\":1},{\"key\":2}]");
		String k1 = a.getObject(0).keySet().iterator().next();
		String k2 = a.getObject(1).keySet().iterator().next();
		assertSame(k1, k2);
		p.setInternStrings(false);
		assertFalse(p.isInternStrings());
	}
}
