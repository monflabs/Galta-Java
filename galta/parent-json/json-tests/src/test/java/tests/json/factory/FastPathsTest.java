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

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonFactory.INTEGER;
import org.monflabs.json.JsonFactory.OVERFLOW_DECIMAL;
import org.monflabs.json.JsonFactory.OVERFLOW_INTEGER;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.json.stringifier.JsonStringifier;

import tests.ProjectTestCase;

/**
 * The performance fast paths of the parser and the stringifier must give exactly the
 * results of the general code they bypass: checked here against reference
 * implementations (the original algorithms) and the slow paths, on randomized data.
 */
public class FastPathsTest extends ProjectTestCase {

	private static class OptionsFactory extends JavaJsonFactory {
		INTEGER defaultInteger = INTEGER.INT;
		OVERFLOW_INTEGER overflowInteger = OVERFLOW_INTEGER.BIGINT;
		boolean useLongIntegers = true;
		@Override
		public INTEGER defaultInteger() { return defaultInteger; }
		@Override
		public OVERFLOW_INTEGER overflowInteger() { return overflowInteger; }
		@Override
		public boolean useLongIntegers() { return useLongIntegers; }
	}

	// A factory that customizes integer parsing: the parser must not bypass it
	private static class CustomIntegerFactory extends JavaJsonFactory {
		@Override
		public Number parseInteger(String s) {
			return new BigDecimal(s).add(BigDecimal.ONE);
		}
	}

	//
	// Parser: integers
	//

	private static final String[] INTEGER_LITERALS = {
		"0", "-0", "-000", "1", "-1", "7", "42", "-42",
		"2147483647", "2147483648", "-2147483648", "-2147483649",
		"99999999999999999", "999999999999999999", "-999999999999999999",	// 17 and 18 digits
		"1000000000000000000", "9223372036854775807", "-9223372036854775808",	// 19 digits: slow path
		"9223372036854775808", "123456789012345678901234567890",
	};

	public void testIntegerFastPathMatchesParseInteger() {
		OptionsFactory f = new OptionsFactory();
		for(INTEGER def: INTEGER.values()) {
			for(OVERFLOW_INTEGER overflow: OVERFLOW_INTEGER.values()) {
				for(boolean useLong: new boolean[] {true, false}) {
					f.defaultInteger = def;
					f.overflowInteger = overflow;
					f.useLongIntegers = useLong;
					for(String s: INTEGER_LITERALS) {
						Object expected = f.parseInteger(s);
						Object parsed = f.parse(s);
						String ctx = s+" "+def+"/"+overflow+"/"+useLong;
						assertEquals(ctx, expected.getClass(), parsed.getClass());
						assertEquals(ctx, expected, parsed);
						// In a container too (the literal is followed by another character)
						Object inArray = ((JsonArray)f.parse("["+s+"]")).get(0);
						assertEquals(ctx, expected, inArray);
					}
				}
			}
		}
		// -0 is a double
		assertEquals(Double.valueOf(-0.0), JsonFactory.get().parse("-0"));
		// Non strict literals with a sign or leading zeros
		assertEquals(5, JsonFactory.get().parse("+5"));
		assertEquals(7, JsonFactory.get().parse("007"));
	}

	public void testIntegerFastPathRandom() {
		JsonFactory f = JsonFactory.get();
		Random r = new Random(11);
		StringBuilder b = new StringBuilder("[");
		String[] literals = new String[5000];
		for(int i=0; i<literals.length; i++) {
			long v = switch(i%4) {
				case 0 -> r.nextInt(1000);
				case 1 -> r.nextInt();
				case 2 -> r.nextLong();
				default -> r.nextLong() % 1_000_000_000_000_000_000L;
			};
			literals[i] = Long.toString(v);
			if(i>0) b.append(',');
			b.append(literals[i]);
		}
		b.append(']');
		JsonArray a = (JsonArray)f.parse(b.toString());
		for(int i=0; i<literals.length; i++) {
			Object expected = f.parseInteger(literals[i]);
			assertEquals(literals[i], expected.getClass(), a.get(i).getClass());
			assertEquals(literals[i], expected, a.get(i));
		}
	}

	public void testCustomIntegerParsingIsUsed() {
		CustomIntegerFactory f = new CustomIntegerFactory();
		assertEquals(new BigDecimal("43"), f.parse("42"));
		assertEquals(new BigDecimal("2"), ((JsonArray)f.parse("[1]")).get(0));
	}

	//
	// Parser: decimals
	//

	public void testDecimalFastPathMatchesParseDecimal() {
		JsonFactory f = JsonFactory.get();
		Random r = new Random(13);
		String[] fixed = {
			"0.0", "-0.0", "0.5", "-0.5", "1.5", "123.456", "0.001", "1.500", "100.0", "1e5", "1E5", "1e-5",
			"-1.25e+3", "0e10", "9.99999999999999", "0.000000000000000000000123", "123456789012345.6",
			"12345678901234.5", "1e22", "1e23", "1e-22", "1e-23", "3.14159", "2.2250738585072014E-308",
			"1.7976931348623157e308", "4.9e-324", "0.1", "0.2", "0.30000000000000004",
		};
		java.util.List<String> literals = new java.util.ArrayList<>(java.util.List.of(fixed));
		for(int i=0; i<20000; i++) {
			switch(i%4) {
				case 0: literals.add(Double.toString(r.nextDouble()*Math.pow(10, r.nextInt(30)-15))); break;
				case 1: literals.add((r.nextBoolean()?"-":"")+r.nextInt(100000)+"."+r.nextInt(10000)); break;
				case 2: literals.add(r.nextInt(1000)+"."+r.nextInt(1000)+"e"+(r.nextInt(50)-25)); break;
				default: literals.add(String.format(java.util.Locale.ROOT, "%.3f", r.nextDouble()*1000));
			}
		}
		StringBuilder b = new StringBuilder("[");
		for(String s: literals) {
			if(b.length()>1) b.append(',');
			b.append(s);
		}
		JsonArray a = (JsonArray)f.parse(b.append(']').toString());
		for(int i=0; i<literals.size(); i++) {
			String s = literals.get(i);
			Object expected = f.parseDecimal(s);
			assertEquals(s, expected.getClass(), a.get(i).getClass());
			if(expected instanceof Double d) {
				// Same bits, -0.0 included
				assertEquals(s, Double.doubleToRawLongBits(d), Double.doubleToRawLongBits((Double)a.get(i)));
			} else {
				assertEquals(s, expected, a.get(i));
			}
			assertEquals(s, expected, f.parse(s));
		}
	}

	//
	// Parser: key cache
	//

	// A Reader returning a few characters at a time, so the keys cross the buffer refills
	private static class SlowReader extends java.io.Reader {
		private final String s;
		private int pos;
		private final int chunk;
		SlowReader(String s, int chunk) { this.s = s; this.chunk = chunk; }
		@Override
		public int read(char[] cbuf, int off, int len) {
			if(pos>=s.length()) return -1;
			int n = Math.min(Math.min(len, chunk), s.length()-pos);
			s.getChars(pos, pos+n, cbuf, off);
			pos += n;
			return n;
		}
		@Override
		public void close() {}
	}

	public void testKeyCache() {
		JsonFactory f = JsonFactory.get();
		StringBuilder b = new StringBuilder("[");
		Random r = new Random(17);
		java.util.List<String> keys = new java.util.ArrayList<>();
		for(int i=0; i<3000; i++) {
			String k = switch(i%6) {
				case 0 -> "k"+(i%50);						// repeated keys
				case 1 -> "key\\u00e9\\n"+(i%7);			// escapes: not cached
				case 2 -> "long".repeat(10)+i;				// longer than the cached keys
				case 3 -> Integer.toString(r.nextInt());	// many distinct keys: cache slot collisions
				case 4 -> "";								// empty key
				default -> "same";
			};
			keys.add(k);
		}
		for(int i=0; i<keys.size(); i++) {
			if(i>0) b.append(',');
			b.append("{\"").append(keys.get(i)).append("\":").append(i).append('}');
		}
		String text = b.append(']').toString();
		for(int round=0; round<2; round++) {
			JsonArray fromString = (JsonArray)f.parse(text);
			for(int chunk: new int[] {1, 3, 7, 1000}) {
				JsonArray fromReader = (JsonArray)f.parse(new SlowReader(text, chunk));
				assertEquals(fromString, fromReader);
			}
			for(int i=0; i<keys.size(); i++) {
				JsonObject o = (JsonObject)fromString.get(i);
				String expected = (String)f.parse("\""+keys.get(i)+"\"");
				assertEquals(1, o.size());
				assertEquals(i, o.get(expected));
			}
		}
		// Control characters in a key (non strict): the regular path decides
		assertEquals(1, ((JsonObject)f.parse("{\"a\tb\":1}")).get("a\tb"));
	}

	//
	// Decimal overflow check (fitsDouble)
	//

	public void testDecimalFitsMatchesBigDecimal() {
		JsonFactory f = JsonFactory.get(); // Double, BIGDEC on precision loss
		Random r = new Random(5);
		for(int i=0; i<20000; i++) {
			String s;
			switch(i%6) {
				case 0: s = Double.toString(r.nextDouble()*Math.pow(10, r.nextInt(40)-20)); break;
				case 1: s = new BigDecimal(r.nextDouble()).round(new java.math.MathContext(17)).toPlainString(); break;
				case 2: s = new BigDecimal(r.nextDouble()).round(new java.math.MathContext(16+r.nextInt(6))).toString(); break;
				case 3: s = (r.nextBoolean()?"-":"")+r.nextInt(100000)+"."+Long.toString(Math.abs(r.nextLong())); break;
				case 4: s = r.nextInt(10)+"."+Long.toString(Math.abs(r.nextLong())).substring(0, 5)+"e"+(r.nextInt(600)-300); break;
				default: s = "0.1"+"0".repeat(r.nextInt(20))+(r.nextBoolean() ? "" : "1");
			}
			Object parsed = f.parseDecimal(s);
			double d = Double.parseDouble(s);
			boolean fits = !Double.isInfinite(d) && (d!=0.0 ? new BigDecimal(s).compareTo(BigDecimal.valueOf(d))==0 : new BigDecimal(s).signum()==0);
			if(fits) {
				assertEquals(s, Double.class, parsed.getClass());
			} else {
				assertEquals(s, BigDecimal.class, parsed.getClass());
			}
		}
	}

	//
	// Stringifier: strings and numbers
	//

	// The original string literal output, kept as the reference
	private static String referenceString(String s) {
		StringBuilder b = new StringBuilder("\"");
		int len = s.length();
		for(int i=0; i<len; i++) {
			char c = s.charAt(i);
			switch(c) {
				case '"': b.append("\\\""); break;
				case '\\': b.append("\\\\"); break;
				case '\b': b.append("\\b"); break;
				case '\f': b.append("\\f"); break;
				case '\n': b.append("\\n"); break;
				case '\r': b.append("\\r"); break;
				case '\t': b.append("\\t"); break;
				default:
					if(c>=32 && c<128) {
						b.append(c);
					} else if(Character.isHighSurrogate(c) && i+1<len && Character.isLowSurrogate(s.charAt(i+1))) {
						b.append(c).append(s.charAt(++i));
					} else {
						String h = Integer.toHexString(c);
						b.append("\\u").append("0000".substring(h.length())).append(h);
					}
			}
		}
		return b.append('"').toString();
	}

	private static String randomString(Random r) {
		int len = r.nextInt(40);
		StringBuilder b = new StringBuilder();
		for(int i=0; i<len; i++) {
			switch(r.nextInt(8)) {
				case 0: b.append((char)r.nextInt(32)); break;				// controls
				case 1: b.append("\"\\/".charAt(r.nextInt(3))); break;
				case 2: b.append((char)(0x80+r.nextInt(0x780))); break;	// latin, greek...
				case 3: b.append((char)(0xD800+r.nextInt(0x800))); break;	// lone surrogates
				case 4: b.appendCodePoint(0x10000+r.nextInt(0x10000)); break;	// pairs
				case 5: b.append((char)(0x4E00+r.nextInt(0x5000))); break;
				case 6: b.append((char)127); break;
				default: b.append((char)(32+r.nextInt(95)));
			}
		}
		return b.toString();
	}

	public void testStringOutputMatchesReference() {
		JsonFactory f = JsonFactory.get();
		Random r = new Random(7);
		for(int i=0; i<20000; i++) {
			String s = randomString(r);
			assertEquals(referenceString(s), f.stringify(s));
		}
		// Long strings (runs longer than the buffers)
		for(int i=0; i<20; i++) {
			StringBuilder b = new StringBuilder();
			for(int k=0; k<2000; k++) {
				b.append(randomString(r)).append("plain text run ");
			}
			String s = b.toString();
			assertEquals(referenceString(s), f.stringify(s));
		}
	}

	public void testNumberOutputMatchesJsonUtil() {
		JsonFactory f = JsonFactory.get();
		Number[] numbers = {
			0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 0L, Long.MAX_VALUE, Long.MIN_VALUE, -10L,
			0.0, -0.0, 1.0, -1.0, 0x1p53, -0x1p53, 0x1p53-1, -(0x1p53-1), 0x1p53+2, 1e21, 1e22, -1e300,
			0.5, -2.25, 1e-7, 123456789.125, 4.9e-324, Double.MAX_VALUE, (float)1.5, 12.5f,
			(short)12, (byte)-3, new BigInteger("123456789012345678901234567890"), new BigDecimal("1.50"),
		};
		for(Number n: numbers) {
			assertEquals(n.toString(), JsonUtil.toString(n), f.stringify(n));
		}
		Random r = new Random(9);
		for(int i=0; i<20000; i++) {
			Number n = switch(i%5) {
				case 0 -> r.nextInt();
				case 1 -> r.nextLong();
				case 2 -> (double)r.nextLong();
				case 3 -> (double)r.nextInt(1000000);
				default -> r.nextDouble()*Math.pow(10, r.nextInt(60)-30);
			};
			assertEquals(n.toString(), JsonUtil.toString(n), f.stringify(n));
		}
	}

	//
	// Stringifier: buffers, indentation, cycles
	//

	private static JsonArray bigArray(JsonFactory f, int count) {
		JsonArray a = f.createArray();
		for(int i=0; i<count; i++) {
			JsonObject o = f.createObject();
			o.putValue("id", i);
			o.putValue("name", "name \"quoted\" é "+i);
			o.putValue("value", i*1.5);
			a.addValue(o);
		}
		return a;
	}

	public void testBuffersGiveTheSameText() throws IOException {
		JsonFactory f = JsonFactory.get();
		for(int count: new int[] {0, 1, 3, 10, 200, 5000}) {
			JsonArray a = bigArray(f, count);
			for(boolean compact: new boolean[] {true, false}) {
				String s = f.stringify(a, compact);
				// Writer: flushed in chunks
				StringWriter w = new StringWriter();
				f.stringify(w, a, compact);
				assertEquals(s, w.toString());
				// Subclass of StringSerializer: the StringBuilder path
				JsonStringifier.StringSerializer sub = new JsonStringifier.StringSerializer() {};
				sub.setCompact(compact);
				assertEquals(s, sub.stringify(a));
				// Reused serializer
				JsonStringifier.StringSerializer ser = new JsonStringifier.StringSerializer();
				ser.setCompact(compact);
				assertEquals(s, ser.stringify(a));
				assertEquals(s, ser.stringify(a));
				// Round trip
				assertEquals(a, f.parse(s));
			}
		}
	}

	public void testLimitedSerializer() throws IOException {
		JsonFactory f = JsonFactory.get();
		String full = f.stringify(bigArray(f, 2000));
		for(int max: new int[] {10, 1000, 8192, 8193, 50000}) {
			JsonStringifier.LimitedStringSerializer ser = new JsonStringifier.LimitedStringSerializer(max);
			String s = ser.stringify(bigArray(f, 2000));
			assertEquals(full.substring(0, Math.min(max, full.length())), s);
			assertEquals(full.length()>max, ser.isTruncated());
		}
	}

	public void testIndentation() throws IOException {
		JsonFactory f = JsonFactory.get();
		Object v = f.parse("{\"a\":[1,{\"b\":[[[[[[[[[[2]]]]]]]]]]}],\"c\":{}}");
		JsonStringifier.StringSerializer ser = new JsonStringifier.StringSerializer();
		ser.setCompact(false);
		String two = ser.stringify(v);
		assertTrue(two.contains("\n  "));
		// Another indent string of the same length: the cached indentation is rebuilt
		ser.setIndentString("\t\t");
		String tabs = ser.stringify(v);
		assertEquals(two.replace("  ", "\t\t"), tabs);
		ser.setIndentString(" ");
		assertEquals(two.replace("  ", " "), ser.stringify(v));
		ser.setInitialIndentLevel(2);
		assertTrue(ser.stringify(v).startsWith("  {"));
	}

	public void testCyclesAtAnyDepth() {
		JsonFactory f = JsonFactory.get();
		for(int depth: new int[] {1, 10, 63, 64, 65, 200}) {
			JsonObject root = f.createObject();
			JsonObject o = root;
			for(int i=0; i<depth; i++) {
				JsonObject c = f.createObject();
				o.putValue("c", c);
				o = c;
			}
			// No cycle: the same object twice as siblings
			JsonArray shared = f.createArray();
			shared.addValue(1);
			o.putValue("x", shared);
			o.putValue("y", shared);
			String s = f.stringify(root);
			assertTrue(s.contains("\"x\":[1],\"y\":[1]"));
			// A cycle to the root or to an intermediate level
			o.putValue("loop", root);
			assertThrows(JsonException.CircularReference.class, () -> f.stringify(root));
			o.remove("loop");
			assertEquals(s, f.stringify(root)); // no false cycle after the failure
		}
	}

	private static void assertThrows(Class<? extends Throwable> type, Runnable r) {
		try {
			r.run();
		} catch(Throwable t) {
			if(type.isInstance(t)) {
				return;
			}
			throw new AssertionError("Expected "+type.getSimpleName()+" but got "+t, t);
		}
		fail("Expected "+type.getSimpleName());
	}
}
