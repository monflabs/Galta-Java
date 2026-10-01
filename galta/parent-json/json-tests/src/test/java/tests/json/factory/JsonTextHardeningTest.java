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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.json.parser.JsonParser;
import org.monflabs.json.parser.ParseException;
import org.monflabs.json.stringifier.JsonStringifier;

import tests.ProjectTestCase;

/**
 * JSON text processing hardening: limits, error messages and positions, end of input,
 * string escaping, truncated output.
 */
public class JsonTextHardeningTest extends ProjectTestCase {

	private static Object strict(String json) throws IOException {
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setStrict(true);
		return p.parse(json);
	}
	private static Object lenient(String json) throws IOException {
		return new JsonParser.StringParser(JsonFactory.get()).parse(json);
	}
	private static Object reader(String json) throws IOException {
		return new JsonParser.ReaderParser(JsonFactory.get()).parse(new StringReader(json));
	}

	//
	// End of input
	//

	// A Reader failing the test when it is read again after it returned the end of input
	private static final class OnceReader extends Reader {
		private final Reader r;
		private boolean ended;
		OnceReader(String s) {
			this.r = new StringReader(s);
		}
		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			if(ended) {
				throw new AssertionError("Read again after the end of input");
			}
			int n = r.read(cbuf, off, len);
			ended = n<0;
			return n;
		}
		@Override
		public void close() {
		}
	}

	public void testStickyEndOfInput() throws Exception {
		String[] invalid = {"[1", "{\"a\":1", "{\"a\"", "\"abc", "\"\\", "\"\\u12", "[1 /* x", "[1 /* x *", "[1 /", "1e", "1e+", "-", "0x", "tru", "{a"};
		for(String s: invalid) {
			JsonParser.ReaderParser p = new JsonParser.ReaderParser(JsonFactory.get());
			ParseException e = assertThrows(s, ParseException.class, () -> p.parse(new OnceReader(s)));
			assertTrue(s, e.getPosition()<=s.length());
		}
		String[] valid = {"1", "null", "true", "\"a\"", "[]", "{}", "1.5e3", "[1] // c", "[1] /* c */"};
		for(String s: valid) {
			JsonParser.ReaderParser p = new JsonParser.ReaderParser(JsonFactory.get());
			p.parse(new OnceReader(s));
		}
	}

	public void testEndOfInputPositions() throws Exception {
		// Always the input length, the type is always ERROR_UNEXPECTED_EOF, in every context
		String[] cases = {"[1", "[1,", "{\"a\":1", "{\"a\"", "{\"a\":1,", "\"abc", "\"a\\", "\"\\u12", "[1 /* x", "[1 /* x *", "[1 /", "1e", "1e+", "-", "{"};
		for(String s: cases) {
			ParseException e = assertThrows(s, ParseException.class, () -> lenient(s));
			assertEquals(s, ParseException.ERROR_UNEXPECTED_EOF, e.getErrorType());
			assertEquals(s, s.length(), e.getPosition());
			assertTrue(s, e.getMessage().startsWith("JsonParser: Unexpected end of input at position "+s.length()+"."));
			ParseException re = assertThrows(s, ParseException.class, () -> reader(s));
			assertEquals(s, s.length(), re.getPosition());
		}
		ParseException e = assertThrows(ParseException.class, () -> strict(" "));
		assertEquals(1, e.getPosition());
		assertEquals("JsonParser: Unexpected end of input at position 1.\n   1:  \n       ^^^", e.getMessage());
	}

	//
	// Error messages
	//

	public void testErrorMessages() throws Exception {
		ParseException e = assertThrows(ParseException.class, () -> lenient("[1 /x]"));
		assertEquals(ParseException.ERROR_UNEXPECTED_CHAR, e.getErrorType());
		assertEquals(Character.valueOf('x'), e.getUnexpectedObject());
		assertTrue(e.getMessage(), e.getMessage().startsWith("JsonParser: Unexpected character 'x' (120) at position 4."));

		e = assertThrows(ParseException.class, () -> lenient("\"\\u12x4\""));
		assertEquals(ParseException.ERROR_UNEXPECTED_UNICODE, e.getErrorType());
		assertTrue(e.getMessage(), e.getMessage().startsWith("JsonParser: Invalid hexadecimal digit 'x' in an escape sequence at position 5."));

		e = assertThrows(ParseException.class, () -> lenient("[tru]"));
		assertTrue(e.getMessage(), e.getMessage().startsWith("JsonParser: Unexpected token 'tru' at position 1."));
		// A huge word is clipped in the message
		e = assertThrows(ParseException.class, () -> lenient("n"+"x".repeat(100_000)));
		assertTrue(e.getMessage().length()<1000);

		e = assertThrows(ParseException.class, () -> lenient("[".repeat(1001)));
		assertEquals(ParseException.ERROR_SYNTAX, e.getErrorType());
		assertTrue(e.getMessage(), e.getMessage().startsWith("JsonParser: Objects and arrays nested deeper than 1000 levels, at position 1000."));

		// Trailing content is reported at its first character
		e = assertThrows(ParseException.class, () -> lenient("[1] x"));
		assertEquals(4, e.getPosition());
	}

	public void testStrictMessagesAndPositions() throws Exception {
		Object[][] cases = {
			{"[1,]", "A trailing comma", 3},
			{"{\"a\":1,}", "A trailing comma", 7},
			{"{a:1}", "An unquoted key", 1},
			{"{'a':1}", "A single quoted key", 1},
			{"['a']", "A single quoted string", 1},
			{"[1] // c", "A comment", 4},
			{"[+1]", "A leading '+'", 1},
			{"[NaN]", "NaN", 1},
			{"[-Infinity]", "Infinity", 1},
			{"[0x1F]", "A hexadecimal number", 1},
			{"[007]", "A leading zero", 1},
			{"[.5]", "A number starting with '.'", 1},
			{"[5.]", "A '.' not followed by a digit", 3},
			{"[\"a\u0001\"]", "A control character in a string", 3},
			{"[\"\\x41\"]", "The \\x escape", 3},
			{"[\"\\'\"]", "The \\' escape", 3},
		};
		for(Object[] c: cases) {
			String json = (String)c[0];
			ParseException e = assertThrows(json, ParseException.class, () -> strict(json));
			assertEquals(json, ParseException.ERROR_UNEXPECTED_STRICT, e.getErrorType());
			assertEquals(json, c[1], e.getUnexpectedObject());
			assertEquals(json, c[2], e.getPosition());
			assertTrue(json+": "+e.getMessage(), e.getMessage().startsWith("JsonParser: "+c[1]+" is not allowed in strict mode, at position "+c[2]+"."));
		}
	}

	public void testGiantLineMessageIsClipped() throws Exception {
		// A 4 million characters line: the message only quotes the part around the error
		String json = "[" + "1,".repeat(2_000_000) + "x]";
		ParseException e = assertThrows(ParseException.class, () -> lenient(json));
		assertEquals(4_000_001, e.getPosition());
		String msg = e.getMessage();
		assertTrue(Integer.toString(msg.length()), msg.length()<500);
		String[] lines = msg.split("\n");
		assertEquals(3, lines.length);
		assertTrue(lines[1], lines[1].startsWith("   1: ...1,1,"));
		assertTrue(lines[1], lines[1].endsWith("1,x]"));
		// The caret is under the error character
		assertEquals(lines[1].indexOf("x]"), lines[2].indexOf('^'));

		// The error in the middle of a long line: clipped on both sides
		String mid = "[" + "1,".repeat(1000) + "x," + "1,".repeat(1000) + "1]";
		e = assertThrows(ParseException.class, () -> lenient(mid));
		lines = e.getMessage().split("\n");
		assertTrue(lines[1], lines[1].startsWith("   1: ...") && lines[1].endsWith("..."));
		assertEquals(lines[1].indexOf('x'), lines[2].indexOf('^'));
	}

	//
	// Numbers
	//

	public void testNumberLengthLimit() throws Exception {
		assertEquals(JsonParser.DEFAULT_MAX_NUMBER_LENGTH, new JsonParser.StringParser(JsonFactory.get()).getMaxNumberLength());
		// 1000 characters are fine, 1001 are not, whatever the part (digits, fraction, exponent)
		lenient("9".repeat(1000));
		ParseException e = assertThrows(ParseException.class, () -> lenient("[0, " + "9".repeat(1001) + "]"));
		assertEquals(ParseException.ERROR_SYNTAX, e.getErrorType());
		assertEquals(4, e.getPosition());
		assertTrue(e.getMessage(), e.getMessage().startsWith("JsonParser: Number literal longer than 1000 characters, at position 4."));
		assertThrows(ParseException.class, () -> lenient("1." + "5".repeat(1000)));
		assertThrows(ParseException.class, () -> lenient("1e" + "0".repeat(1000)));
		assertThrows(ParseException.class, () -> reader("-" + "1".repeat(1000)));
		assertThrows(JsonException.class, () -> JsonFactory.get().parse(new StringReader("1".repeat(2000))));

		// A million digits are rejected right away instead of taking tens of seconds
		long start = System.nanoTime();
		assertThrows(ParseException.class, () -> lenient("1".repeat(1_000_000)));
		assertTrue((System.nanoTime()-start)/1_000_000<5000);

		// Configurable, 0 means no limit
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setMaxNumberLength(0);
		assertEquals(new BigDecimal("1." + "5".repeat(2000)), p.parse("1." + "5".repeat(2000)));
		p.setMaxNumberLength(3);
		assertEquals(123, p.parse("123"));
		assertThrows(ParseException.class, () -> p.parse("1234"));
	}

	public void testHugeIntegerToDoubleIsLinear() throws Exception {
		// When integers overflow to doubles, a huge literal is converted without a BigInteger
		JsonFactory doubles = new JavaJsonFactory() {
			@Override
			public OVERFLOW_INTEGER overflowInteger() { return OVERFLOW_INTEGER.DOUBLE; }
		};
		JsonParser.StringParser p = new JsonParser.StringParser(doubles);
		p.setMaxNumberLength(0);
		long start = System.nanoTime();
		assertEquals(Double.POSITIVE_INFINITY, p.parse("1".repeat(1_000_000)));
		assertTrue((System.nanoTime()-start)/1_000_000<5000);
		assertEquals(1.2345678901234568E29, p.parse("123456789012345678901234567890"));
	}

	public void testExponentOverflow() throws Exception {
		JsonFactory f = JsonFactory.get();
		// The double value, like JSON.parse(): Infinity or 0
		assertEquals(Double.POSITIVE_INFINITY, f.parse("1e3000000000"));
		assertEquals(Double.NEGATIVE_INFINITY, f.parse("-1e3000000000"));
		assertEquals(0.0, f.parse("1e-3000000000"));
		assertEquals(Double.POSITIVE_INFINITY, f.parse(new StringReader("1.5e+99999999999")));
		// Within the BigDecimal range: still a BigDecimal
		assertEquals(new BigDecimal("1e400"), f.parse("1e400"));

		// A factory with BigDecimal decimals cannot hold it: a ParseException with a position
		JsonFactory bigdec = new JavaJsonFactory() {
			@Override
			public DECIMAL defaultDecimal() { return DECIMAL.BIGDEC; }
		};
		ParseException e = assertThrows(ParseException.class, () -> bigdec.parse("[1, 1e3000000000]"));
		assertEquals(ParseException.ERROR_SYNTAX, e.getErrorType());
		assertEquals(4, e.getPosition());
		assertTrue(e.getMessage(), e.getMessage().startsWith("JsonParser: Invalid number 1E3000000000"));
	}

	//
	// Entry points
	//

	public void testEntryPoints() throws Exception {
		JsonFactory f = JsonFactory.get();
		// One empty input rule: null in lenient mode, whatever the source
		assertNull(f.parse(""));
		assertNull(f.parse(" \n\t"));
		assertNull(f.parse("/* only a comment */"));
		assertNull(f.parse(new StringReader("")));
		assertNull(f.parse(new ByteArrayInputStream(new byte[0])));
		// An error in strict mode
		assertThrows(ParseException.class, () -> f.parse("", true));
		assertThrows(JsonException.class, () -> f.parse(new StringReader(" "), true));
		assertThrows(JsonException.class, () -> f.parse(new ByteArrayInputStream(new byte[0]), StandardCharsets.UTF_8, true));

		// Null sources: a real message
		JsonException e = assertThrows(JsonException.class, () -> f.parse((String)null));
		assertEquals("The JSON string to parse is null", e.getMessage());
		e = assertThrows(JsonException.class, () -> f.parse((Reader)null));
		assertEquals("The JSON reader to parse is null", e.getMessage());
		e = assertThrows(JsonException.class, () -> f.parse((java.io.InputStream)null));
		assertEquals("The JSON stream to parse is null", e.getMessage());

		// The wrapping exception carries the parse error message
		e = assertThrows(JsonException.class, () -> f.parse(new ByteArrayInputStream("[1,".getBytes(StandardCharsets.UTF_8))));
		assertEquals("Error when parsing JSON stream: JsonParser: Unexpected end of input at position 3.", e.getMessage());
		assertTrue(e.getCause() instanceof ParseException);

		// BOM: skipped by the lenient parser for every source, rejected in strict mode
		assertEquals(JsonArray.of(1), f.parse("\uFEFF[1]"));
		assertEquals(JsonArray.of(1), f.parse(new StringReader("\uFEFF [1]")));
		assertNull(f.parse("\uFEFF"));
		assertThrows(ParseException.class, () -> strict("\uFEFF[1]"));
		assertThrows(JsonException.class, () -> f.parse(new ByteArrayInputStream(new byte[] {(byte)0xEF,(byte)0xBB,(byte)0xBF,'1'}), StandardCharsets.UTF_8, true));
		// Only one
		assertThrows(ParseException.class, () -> f.parse("\uFEFF\uFEFF[1]"));
	}

	public void testStrictUtf8() throws Exception {
		JsonFactory f = JsonFactory.get();
		byte[][] invalid = {
			{'"', (byte)0xFF, '"'},						// invalid byte
			{'"', (byte)0xC3, '"'},						// truncated sequence
			{'"', (byte)0xED, (byte)0xA0, (byte)0x80, '"'},	// encoded surrogate
			{'"', (byte)0xC0, (byte)0xAF, '"'},			// overlong encoding
		};
		for(byte[] b: invalid) {
			// Lenient: replaced by U+FFFD
			Object v = f.parse(new ByteArrayInputStream(b));
			assertTrue(v.toString(), v.toString().contains("\uFFFD"));
			// Strict: an error
			JsonException e = assertThrows(JsonException.class, () -> f.parse(new ByteArrayInputStream(b), StandardCharsets.UTF_8, true));
			assertTrue(e.getMessage(), e.getMessage().startsWith("Error when parsing JSON stream: invalid byte sequence"));
		}
		// Valid multi-byte UTF-8 is fine in strict mode, across the decoder buffers too
		String s = "é€😀".repeat(5000);
		assertEquals(s, f.parse(new ByteArrayInputStream(("\""+s+"\"").getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8, true));
	}

	//
	// Stringify
	//

	public void testNonAsciiOutput() throws Exception {
		JsonFactory f = JsonFactory.get();
		// Written as is, like JSON.stringify(): JSON.stringify('é').length is 3
		assertEquals("\"é\"", f.stringify("é"));
		assertEquals(3, f.stringify("é").length());
		assertEquals("\"日本語\"", f.stringify("日本語"));
		assertEquals("\"\u2028\u2029\"", f.stringify("\u2028\u2029"));
		assertEquals("\"\u007f\"", f.stringify("\u007f"));
		assertEquals("\"😀\"", f.stringify("😀"));
		// Escaped: controls, quote, backslash, lone surrogates (lowercase hexadecimal)
		assertEquals("\"\\u0000\\u001f\\b\\t\\n\\f\\r\\\"\\\\/\"", f.stringify("\u0000\u001f\b\t\n\f\r\"\\/"));
		assertEquals("\"\\ud834\"", f.stringify("\ud834"));
		assertEquals("\"\\udf06\\ud834\"", f.stringify("\udf06\ud834"));
		assertEquals("\"\\ud834𝌆\\ud834\"", f.stringify("\ud834\ud834\udf06\ud834"));
		// Round trip, strict parser
		String all = "aé日😀\u2028\u007f\u0001\"\\";
		assertEquals(all, strict(f.stringify(all)));
		// Keys too
		assertEquals("{\"clé\":1}", f.stringify(JsonObject.of("clé", 1)));

		// Opt-in pure ASCII output
		JsonStringifier.StringSerializer ascii = new JsonStringifier.StringSerializer();
		ascii.setEscapeNonAscii(true);
		assertEquals("\"\\u00e9\\u65e5\\ud83d\\ude00\\u2028\\u007f\\ud834\"", ascii.stringify("é日😀\u2028\u007f\ud834"));
		assertEquals(all, strict(ascii.stringify(all)));
	}

	public void testStringifyDepth() throws Exception {
		JsonArray root = JsonArray.create();
		JsonArray last = root;
		for(int i=0; i<20_000; i++) {
			JsonArray child = JsonArray.create();
			last.add(child);
			last = child;
		}
		// Used to be a StackOverflowError
		JsonException e = assertThrows(JsonStringifier.NestingTooDeepException.class, () -> JsonFactory.get().stringify(root));
		assertTrue(e.getMessage(), e.getMessage().contains("1000"));
		assertThrows(JsonStringifier.NestingTooDeepException.class, () -> root.stringify());
		assertThrows(JsonStringifier.NestingTooDeepException.class, () -> JsonFactory.get().stringify(new java.io.StringWriter(), root));

		// 1000 levels are fine, and the limit is configurable (capped)
		JsonArray ok = JsonArray.create();
		last = ok;
		for(int i=1; i<1000; i++) {
			JsonArray child = JsonArray.create();
			last.add(child);
			last = child;
		}
		assertEquals("[".repeat(1000)+"]".repeat(1000), ok.stringify());
		JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
		s.setMaxDepth(2);
		assertEquals("[[]]", s.stringify(JsonArray.parse("[[]]")));
		assertThrows(JsonStringifier.NestingTooDeepException.class, () -> s.stringify(JsonArray.parse("[[[]]]")));
		assertThrows(JsonStringifier.NestingTooDeepException.class, () -> s.stringify(JsonObject.parse("{\"a\":{\"b\":{}}}")));
		s.setMaxDepth(Integer.MAX_VALUE);
		assertEquals(JsonStringifier.MAX_DEPTH_LIMIT, s.getMaxDepth());
		// The stringifier can be used again after the error
		assertEquals("[1]", s.stringify(JsonArray.of(1)));
	}

	public void testParserDepthCap() throws Exception {
		JsonParser.StringParser p = new JsonParser.StringParser(JsonFactory.get());
		p.setMaxDepth(Integer.MAX_VALUE);
		assertEquals(JsonParser.MAX_DEPTH_LIMIT, p.getMaxDepth());
		int n = JsonParser.MAX_DEPTH_LIMIT;
		assertNotNull(p.parse("[".repeat(n)+"]".repeat(n)));
		assertThrows(ParseException.class, () -> p.parse("[".repeat(n+1)+"]".repeat(n+1)));

		// A thread stack too small for the depth: still a ParseException (and the
		// stringifier's NestingTooDeepException), not a StackOverflowError
		Object[] result = new Object[2];
		JsonArray deep = (JsonArray)p.parse("[".repeat(n)+"]".repeat(n));
		Thread t = new Thread(null, () -> {
			try {
				p.parse("[".repeat(n)+"]".repeat(n));
			} catch(Throwable e) {
				result[0] = e;
			}
			try {
				JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
				s.setMaxDepth(n);
				s.stringify(deep);
			} catch(Throwable e) {
				result[1] = e;
			}
		}, "small-stack", 64*1024);
		t.start();
		t.join();
		assertTrue(String.valueOf(result[0]), result[0] instanceof ParseException);
		assertTrue(String.valueOf(result[1]), result[1] instanceof JsonStringifier.NestingTooDeepException);
	}

	public void testCustomNumberOutput() throws Exception {
		// A Number whose toString() is not a JSON number
		Number nan = new Number() {
			private static final long serialVersionUID = 1L;
			@Override public int intValue() { return 0; }
			@Override public long longValue() { return 0; }
			@Override public float floatValue() { return Float.NaN; }
			@Override public double doubleValue() { return Double.NaN; }
			@Override public String toString() { return "NaN"; }
		};
		assertEquals("[null]", JsonFactory.get().stringify(JsonArray.of(nan)));
		Number comma = new Number() {
			private static final long serialVersionUID = 1L;
			@Override public int intValue() { return 1; }
			@Override public long longValue() { return 1; }
			@Override public float floatValue() { return 1.5f; }
			@Override public double doubleValue() { return 1.5; }
			@Override public String toString() { return "1,5"; }
		};
		assertEquals("[1.5]", JsonFactory.get().stringify(JsonArray.of(comma)));
		// A valid text is kept as is
		Number exact = new Number() {
			private static final long serialVersionUID = 1L;
			@Override public int intValue() { return 0; }
			@Override public long longValue() { return 0; }
			@Override public float floatValue() { return 0; }
			@Override public double doubleValue() { return 0.1; }
			@Override public String toString() { return "0.10000000000000000000001"; }
		};
		assertEquals("0.10000000000000000000001", JsonFactory.get().stringify(exact));
		assertEquals("[1,2,-3,1.5,1E+400]", JsonFactory.get().stringify(JsonArray.of((short)1, (byte)2, java.math.BigInteger.valueOf(-3), new BigDecimal("1.5"), new BigDecimal("1e400"))));
	}

	public void testLimitedStringSerializer() throws Exception {
		// Stops walking the value once the limit is reached
		JsonArray big = JsonArray.create();
		for(int i=0; i<100_000; i++) {
			big.add(i);
		}
		AtomicInteger calls = new AtomicInteger();
		JsonStringifier.LimitedStringSerializer l = new JsonStringifier.LimitedStringSerializer(10);
		l.setReplacer((c, k, v) -> {
			calls.incrementAndGet();
			return v;
		});
		assertEquals("[0,1,2,3,4", l.stringify(big));
		assertTrue(l.isTruncated());
		assertTrue(Integer.toString(calls.get()), calls.get()<1000);

		// Not truncated when it fits, even exactly
		JsonStringifier.LimitedStringSerializer exact = new JsonStringifier.LimitedStringSerializer(7);
		assertEquals("[1,2,3]", exact.stringify(JsonArray.of(1, 2, 3)));
		assertFalse(exact.isTruncated());
		assertEquals("[1,2,3,", exact.stringify(JsonArray.of(1, 2, 3, 4)));
		assertTrue(exact.isTruncated());

		// Never cut inside an escape sequence or a surrogate pair
		String s = "\u0001\u0002\\😀\"\n";
		String full = JsonFactory.get().stringify(s);
		for(int max=0; max<=full.length(); max++) {
			JsonStringifier.LimitedStringSerializer ls = new JsonStringifier.LimitedStringSerializer(max);
			String out = ls.stringify(s);
			assertTrue(max+": "+out, out.length()<=max);
			assertTrue(max+": "+out, full.startsWith(out));
			assertEquals(max+": "+out, max<full.length(), ls.isTruncated());
			// The cut is on a boundary: a following escape or pair is either complete or absent
			assertFalse(max+": "+out, out.length()>0 && Character.isHighSurrogate(out.charAt(out.length()-1)));
			assertTrue(max+": "+out, isCompleteEscapes(out));
		}
		// Escaped backslashes before the cut are complete escapes
		String bs = JsonFactory.get().stringify("\\\\\\\\");	// "\\\\\\\\"
		for(int max=0; max<=bs.length(); max++) {
			String out = new JsonStringifier.LimitedStringSerializer(max).stringify("\\\\\\\\");
			assertTrue(max+": "+out, isCompleteEscapes(out));
		}
	}
	// Each backslash starts a complete escape sequence
	private static boolean isCompleteEscapes(String s) {
		for(int i=0; i<s.length(); i++) {
			if(s.charAt(i)=='\\') {
				if(i+1>=s.length()) {
					return false;
				}
				int len = s.charAt(i+1)=='u' ? 6 : 2;
				if(i+len>s.length()) {
					return false;
				}
				i += len-1;
			}
		}
		return true;
	}

	public void testContainerStringifyUsesTheFactory() throws Exception {
		// toString() of a cyclic container doesn't throw
		JsonObject o = JsonObject.create();
		o.put("self", o);
		assertEquals("{\n  \"self\": \"[circular]\"\n}", o.toString());
		assertThrows(JsonException.CircularReference.class, () -> o.stringify());
		// The same container twice is not a cycle
		JsonArray shared = JsonArray.of(1);
		JsonArray twice = JsonArray.of(shared, shared);
		assertEquals("[[1],[1]]", twice.stringify());
		assertFalse(twice.toString().contains("circular"));
	}
}
