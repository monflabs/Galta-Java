package doc_examples.json;

import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.json.java.JavaJsonFactoryChecked;
import org.monflabs.json.parser.JsonParser;
import org.monflabs.json.parser.ParseException;
import org.monflabs.json.stringifier.JsonStringifier;

import tests.ProjectTestCase;

/**
 * Samples of docs/GaltaJSON/Parsing.md
 */
public class ParsingExamples extends ProjectTestCase {

	public void testParseSources() throws Exception {
		JsonFactory factory = JsonFactory.get();

		Object fromString = factory.parse("{\"name\":\"Ada\"}");
		Object fromReader;
		try(Reader r = new StringReader("[1,2,3]")) {
			fromReader = factory.parse(r);
		}
		Object fromStream;
		try(InputStream is = new ByteArrayInputStream("\"Zoë\"".getBytes(StandardCharsets.UTF_8))) {
			fromStream = factory.parse(is);                         // UTF-8 unless a Charset is passed
		}
		assertTrue(fromString instanceof JsonObject);
		assertTrue(fromReader instanceof JsonArray);
		assertEquals("Zoë", fromStream);

		// Any JSON value can be at the top level
		assertEquals(42, factory.parse("42"));
		assertNull(factory.parse("null"));

		// The typed shortcuts check the type of the result
		JsonObject o = JsonObject.parse("{}");
		JsonArray a = JsonArray.parse("[]");
		assertTrue(o.isEmpty() && a.isEmpty());
		JsonException e = assertThrows(JsonException.class, () -> JsonObject.parse("[]"));
		assertEquals("The JSON text is not an object but array", e.getMessage());
		assertNull(JsonObject.parse("null"));
	}

	public void testLenientSyntax() {
		JsonObject o = JsonObject.parse("""
			{
			  // line comment
			  /* block comment */
			  unquoted: 1,
			  $dollar_key: 2,
			  'single': 'quoted',
			  "hex": 0x1F,
			  "plus": +5,
			  "dot": .5,
			  "trailingDot": 5.,
			  "leadingZeros": 007,
			  "nan": NaN,
			  "inf": -Infinity,
			  "escape": "\\x41",
			  "trailing": [1, 2, ],
			}
			""");
		assertEquals(1, o.get("unquoted"));
		assertEquals(2, o.get("$dollar_key"));
		assertEquals("quoted", o.get("single"));
		assertEquals(31, o.get("hex"));
		assertEquals(5, o.get("plus"));
		assertEquals(0.5, o.get("dot"));
		assertEquals(5.0, o.get("trailingDot"));
		assertEquals(7, o.get("leadingZeros"));
		assertTrue(Double.isNaN(o.getDouble("nan")));
		assertEquals(Double.NEGATIVE_INFINITY, o.get("inf"));
		assertEquals("A", o.get("escape"));
		assertEquals(2, o.getArray("trailing").size());

		// Raw control characters are kept in strings
		assertEquals("a\nb", JsonFactory.get().parse("\"a\nb\""));

		assertEquals(1, JsonObject.parse("{@type: 1}").get("@type"));
		assertTrue(JsonObject.parse("{null: 1}").containsKey("null"));   // an unquoted name, not a null key
		assertEquals(Double.POSITIVE_INFINITY, JsonFactory.get().parse("+Infinity"));
		assertEquals("it's", JsonFactory.get().parse("\"it\\'s\""));
	}

	public void testStrictMode() throws Exception {
		JsonParser.StringParser parser = new JsonParser.StringParser(JsonFactory.get());
		parser.setStrict(true);

		assertEquals(JsonArray.of(1, 2), parser.parse("[1, 2]"));
		assertThrows(ParseException.class, () -> parser.parse("[1, 2, ]"));
		assertThrows(ParseException.class, () -> parser.parse("{a: 1}"));
		assertThrows(ParseException.class, () -> parser.parse("['a']"));
		assertThrows(ParseException.class, () -> parser.parse("[1] // comment"));
		assertThrows(ParseException.class, () -> parser.parse("[NaN]"));
	}

	public void testNumberMapping() {
		JsonArray a = JsonArray.parse("""
			[ 42, 3000000000, 123456789012345678901234567890,
			  1.5, 1e3, 3.141592653589793, -0, 3.14159265358979323846, 1e400 ]
			""");
		assertEquals(Integer.valueOf(42), a.get(0));
		assertEquals(Long.valueOf(3000000000L), a.get(1));
		assertEquals(new BigInteger("123456789012345678901234567890"), a.get(2));
		assertEquals(Double.valueOf(1.5), a.get(3));
		assertEquals(Double.valueOf(1000), a.get(4));
		// A double holds this value exactly: a Double
		assertEquals(Double.valueOf(3.141592653589793), a.get(5));
		assertEquals(Double.valueOf(-0.0), a.get(6));
		// A double would lose digits, or overflow: kept exact as a BigDecimal
		assertEquals(new BigDecimal("3.14159265358979323846"), a.get(7));
		assertEquals(new BigDecimal("1e400"), a.get(8));
	}

	public void testNumberOptions() {
		JsonFactory exact = new JavaJsonFactory() {
			@Override
			public INTEGER defaultInteger() { return INTEGER.LONG; }
			@Override
			public DECIMAL defaultDecimal() { return DECIMAL.BIGDEC; }
		};
		JsonArray a = (JsonArray)exact.parse("[42, 1.5]");
		assertEquals(Long.valueOf(42), a.get(0));
		assertEquals(new BigDecimal("1.5"), a.get(1));

		JsonFactory doubles = new JavaJsonFactory() {
			@Override
			public OVERFLOW_INTEGER overflowInteger() { return OVERFLOW_INTEGER.DOUBLE; }
			@Override
			public OVERFLOW_DECIMAL overflowDecimal() { return OVERFLOW_DECIMAL.DOUBLE; }
		};
		JsonArray b = (JsonArray)doubles.parse("[123456789012345678901234567890, 3.141592653589793]");
		assertEquals(Double.valueOf(1.2345678901234568E29), b.get(0));
		assertEquals(Double.valueOf(Math.PI), b.get(1));

		JsonFactory noLongs = new JavaJsonFactory() {
			@Override
			public boolean useLongIntegers() { return false; }
		};
		JsonArray c = (JsonArray)noLongs.parse("[42, 3000000000]");
		assertEquals(Integer.valueOf(42), c.get(0));
		assertEquals(new BigInteger("3000000000"), c.get(1));     // int overflow goes to overflowInteger()

		// The containers still belong to the shared default factory
		assertSame(JavaJsonFactory.instance, c.factory());
	}

	public void testParseErrors() throws Exception {
		ParseException e = assertThrows(ParseException.class,
				() -> JsonFactory.get().parse("{\"a\":}"));
		assertEquals(5, e.getPosition());
		assertEquals(ParseException.ERROR_UNEXPECTED_CHAR, e.getErrorType());
		assertTrue(e.getMessage().startsWith("JsonParser: Unexpected character '}' (125) at position 5."));

		// Reader and InputStream parsing wrap it in a JsonException
		JsonException je = assertThrows(JsonException.class,
				() -> JsonFactory.get().parse(new StringReader("{\"a\":}")));
		assertTrue(je.getCause() instanceof ParseException);
		assertEquals("Error when parsing JSON reader: JsonParser: Unexpected character '}' (125) at position 5.", je.getMessage());

		// Empty or blank input is null for the lenient parser, an error for the strict one
		assertNull(JsonFactory.get().parse(""));
		assertNull(JsonFactory.get().parse("  \n "));
		assertNull(JsonFactory.get().parse(new StringReader("")));
		assertThrows(ParseException.class, () -> JsonFactory.get().parse("", true));

		// The end of input is reported at the input length
		ParseException eof = assertThrows(ParseException.class, () -> JsonFactory.get().parse("[1, 2"));
		assertEquals(5, eof.getPosition());
		assertTrue(eof.getMessage().startsWith("JsonParser: Unexpected end of input at position 5."));

		// Strict mode names the construct
		ParseException strict = assertThrows(ParseException.class, () -> JsonFactory.get().parse("[1, 2, ]", true));
		assertTrue(strict.getMessage().startsWith("JsonParser: A trailing comma is not allowed in strict mode, at position 7."));
	}

	public void testByteOrderMark() throws Exception {
		JsonFactory f = JsonFactory.get();
		assertEquals(JsonArray.of(1), f.parse("\uFEFF[1]"));                       // skipped by the lenient parser
		assertEquals(JsonArray.of(1), f.parse(new StringReader("\uFEFF[1]")));
		byte[] utf8 = {(byte)0xEF, (byte)0xBB, (byte)0xBF, '[', '1', ']'};
		assertEquals(JsonArray.of(1), f.parse(new ByteArrayInputStream(utf8)));
		assertThrows(ParseException.class, () -> f.parse("\uFEFF[1]", true));     // rejected in strict mode
	}

	public void testStrictStream() throws Exception {
		byte[] invalid = {'"', (byte)0xFF, '"'};                        // not UTF-8
		Object lenient = JsonFactory.get().parse(new ByteArrayInputStream(invalid));
		assertEquals("\uFFFD", lenient);                                // the replacement character
		assertThrows(JsonException.class,
				() -> JsonFactory.get().parse(new ByteArrayInputStream(invalid), StandardCharsets.UTF_8, true));
	}

	public void testLimits() throws Exception {
		JsonFactory f = JsonFactory.get();
		// Number literals: at most 1000 characters by default
		ParseException tooLong = assertThrows(ParseException.class, () -> f.parse("[" + "9".repeat(1001) + "]"));
		assertEquals(1, tooLong.getPosition());
		JsonParser.StringParser parser = new JsonParser.StringParser(f);
		parser.setMaxNumberLength(5000);
		assertEquals(new BigInteger("9".repeat(1001)), parser.parse("9".repeat(1001)));

		// An exponent out of the BigDecimal range: the double value
		assertEquals(Double.POSITIVE_INFINITY, f.parse("1e3000000000"));
		assertEquals(0.0, f.parse("1e-3000000000"));

		// Nesting depth, parsing and writing
		assertThrows(ParseException.class, () -> f.parse("[".repeat(1001) + "]".repeat(1001)));
		JsonArray deep = JsonArray.create();
		JsonArray last = deep;
		for(int i=0; i<1000; i++) {
			JsonArray child = JsonArray.create();
			last.add(child);
			last = child;
		}
		assertThrows(JsonStringifier.NestingTooDeepException.class, () -> deep.stringify());
	}

	public void testReviver() throws Exception {
		JsonParser.StringParser parser = new JsonParser.StringParser(JsonFactory.get());
		parser.setReviver((container, key, value, source) -> {
			if(key.startsWith("_")) {
				return JsonParser.Reviver.IGNORE;          // drop the property
			}
			if(value instanceof Double) {
				return new BigDecimal(source);             // source: the raw text of a primitive
			}
			return value;
		});
		JsonObject o = (JsonObject)parser.parse("{\"_id\":7,\"price\":0.1,\"qty\":3}");
		assertEquals("{\"price\":0.1,\"qty\":3}", o.stringify());
		assertEquals(new BigDecimal("0.1"), o.get("price"));
	}

	public void testStringify() {
		JsonObject o = JsonObject.parse("{\"a\":1,\"b\":[1,2],\"c\":{}}");

		assertEquals("{\"a\":1,\"b\":[1,2],\"c\":{}}", o.stringify());   // compact
		assertEquals("""
			{
			  "a": 1,
			  "b": [
			    1,
			    2
			  ],
			  "c": {}
			}""", o.stringify(false));                                          // pretty
		assertEquals(o.stringify(false), o.toString());

		JsonFactory f = JsonFactory.get();
		assertEquals("{\"a\":1,\"b\":[1,2],\"c\":{}}", f.stringify(o));
		assertEquals("\"text\"", f.stringify("text"));
		assertEquals("{\"a\":2,\"b\":1}", f.stringifySorted(JsonObject.of("b", 1, "a", 2)));

		StringWriter w = new StringWriter();
		f.stringify(w, o, false);
		assertEquals(o.stringify(false), w.toString());
	}

	public void testStringifierOptions() throws Exception {
		JsonObject user = JsonObject.parse("{\"name\":\"Ada\",\"password\":\"secret\",\"email\":null,\"age\":36}");

		JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
		s.setSortProperties(true);
		s.setSerializeNulls(false);
		s.setReplacer((container, key, value) ->
			"password".equals(key) ? JsonStringifier.Replacer.IGNORE : value);
		assertEquals("{\"age\":36,\"name\":\"Ada\"}", s.stringify(user));

		JsonStringifier.StringSerializer pretty = new JsonStringifier.StringSerializer();
		pretty.setCompact(false);
		pretty.setIndentString("\t");
		assertEquals("[\n\t1\n]", pretty.stringify(JsonArray.of(1)));

		JsonStringifier.StringSerializer ordered = new JsonStringifier.StringSerializer();
		ordered.setPropertyList(List.of("age", "name"));       // which properties, in which order
		assertEquals("{\"age\":36,\"name\":\"Ada\"}", ordered.stringify(user));

		JsonStringifier.LimitedStringSerializer limited = new JsonStringifier.LimitedStringSerializer(5);
		assertEquals("[1,2,", limited.stringify(JsonArray.of(1, 2, 3, 4)));
		assertTrue(limited.isTruncated());

		JsonStringifier.StringSerializer nested = new JsonStringifier.StringSerializer();
		nested.setCompact(false);
		nested.setInitialIndentLevel(1);
		nested.setSerializeNulls(false);
		assertEquals("  [\n    null\n  ]", nested.stringify(JsonArray.of((Object)null)));   // array items are kept

		JsonStringifier.StringSerializer root = new JsonStringifier.StringSerializer();
		root.setReplacer((container, key, value) -> key.isEmpty() ? "root" : value);
		assertEquals("\"root\"", root.stringify(JsonArray.of(1)));
	}

	public void testEscaping() throws Exception {
		String s = "Line\n\"q\" é / \u0001 😀";
		assertEquals("\"Line\\n\\\"q\\\" é / \\u0001 😀\"", JsonFactory.get().stringify(s));
		assertEquals(s, JsonFactory.get().parse(JsonFactory.get().stringify(s)));
		assertEquals("\"\\ud800\"", JsonFactory.get().stringify("\ud800"));    // a lone surrogate

		JsonStringifier.StringSerializer ascii = new JsonStringifier.StringSerializer();
		ascii.setEscapeNonAscii(true);
		assertEquals("\"\\u00e9 \\ud83d\\ude00\"", ascii.stringify("é 😀"));    // pure ASCII output
	}

	public void testNaNAndInfinity() {
		JsonArray a = JsonArray.of(Double.NaN, Double.POSITIVE_INFINITY, 1.0);
		// Stored and parsed (lenient mode), but never written: not JSON numbers
		assertEquals("[null,null,1]", a.stringify());
		assertEquals(a, JsonArray.parse("[NaN,Infinity,1]"));
	}

	public void testCircularReference() {
		JsonArray a = JsonArray.create();
		a.add(a);
		assertThrows(JsonException.CircularReference.class, () -> a.stringify());
		assertEquals("[\n  \"[circular]\"\n]", a.toString());        // toString() doesn't throw
	}

	public void testCheckedFactory() {
		JsonObject lenient = JsonObject.create();
		lenient.putValue("text", new StringBuilder("abc"));   // accepted: any Java object...
		assertEquals("{\"text\":\"abc\"}", lenient.stringify());   // ...written as a string

		JsonFactory previous = JsonFactory.get();
		JsonFactory.set(JavaJsonFactoryChecked.instance);
		try {
			JsonObject checked = JsonObject.create();
			assertThrows(JsonException.class, () -> checked.put("when", (Object)new Date(0)));
			checked.put("when", "1970-01-01");               // JSON values are fine
			JsonObject child = checked.getOrCreateObject("child");
			assertThrows(JsonException.class, () -> child.putValue("when", new Date(0)));   // children too
			// Every way to store a value is checked, the Map/List bulk methods included
			assertThrows(JsonException.class, () -> checked.putAll(java.util.Map.of("when", new Date(0))));
			JsonArray list = checked.getOrCreateArray("list");
			assertThrows(JsonException.class, () -> list.addAll(java.util.List.of(new Date(0))));
		} finally {
			JsonFactory.set(previous);
		}
	}
}
