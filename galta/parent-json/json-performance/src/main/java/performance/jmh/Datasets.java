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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Benchmark datasets, generated deterministically (fixed seeds) so every run and every
 * library works on exactly the same text.
 */
public final class Datasets {

	public static final String[] NAMES = {
		"small", "medium", "large", "numbers", "strings", "deep", "pretty", "worldcup"
	};

	private Datasets() {
	}

	public static String get(String name) {
		switch(name) {
			case "small":		return small();
			case "medium":		return records(250, 1);		// ~50KB
			case "large":		return records(25000, 2);	// ~5MB
			case "numbers":		return numbers(20000, 3);
			case "strings":		return strings(2000, 4);
			case "deep":		return deep(500);
			case "pretty":		return pretty(records(250, 1));
			case "worldcup":	return resource("/json/worldcup-2018/worldcup.json");
		}
		throw new IllegalArgumentException("Unknown dataset "+name);
	}

	public static byte[] bytes(String name) {
		return get(name).getBytes(StandardCharsets.UTF_8);
	}

	// ~200 bytes
	private static String small() {
		return "{\"id\":12345,\"name\":\"Jane Doe\",\"email\":\"jane.doe@example.com\",\"active\":true,"
			+ "\"score\":98.6,\"tags\":[\"admin\",\"user\"],\"address\":{\"city\":\"Boston\",\"zip\":\"02110\"},"
			+ "\"manager\":null,\"logins\":1024}";
	}

	// API-like array of records (~200 bytes each)
	private static String records(int count, long seed) {
		Random r = new Random(seed);
		StringBuilder b = new StringBuilder(count*220);
		b.append('[');
		for(int i=0; i<count; i++) {
			if(i>0) b.append(',');
			b.append("{\"id\":").append(100000+i)
			 .append(",\"guid\":\"").append(Long.toHexString(r.nextLong())).append(Long.toHexString(r.nextLong())).append('"')
			 .append(",\"isActive\":").append(r.nextBoolean())
			 .append(",\"balance\":").append(Math.round(r.nextDouble()*1000000)/100.0)
			 .append(",\"age\":").append(18+r.nextInt(60))
			 .append(",\"name\":\"").append(word(r, 6)).append(' ').append(word(r, 8)).append('"')
			 .append(",\"company\":\"").append(word(r, 10).toUpperCase()).append('"')
			 .append(",\"latitude\":").append(r.nextDouble()*180-90)
			 .append(",\"tags\":[\"").append(word(r,5)).append("\",\"").append(word(r,7)).append("\",\"").append(word(r,4)).append("\"]")
			 .append(",\"friend\":{\"id\":").append(r.nextInt(1000)).append(",\"name\":\"").append(word(r, 9)).append("\"}")
			 .append('}');
		}
		b.append(']');
		return b.toString();
	}

	// Mix of ints, longs, decimals and exponents
	private static String numbers(int count, long seed) {
		Random r = new Random(seed);
		StringBuilder b = new StringBuilder(count*12);
		b.append('[');
		for(int i=0; i<count; i++) {
			if(i>0) b.append(',');
			switch(i%5) {
				case 0: b.append(r.nextInt(1000)); break;
				case 1: b.append(r.nextInt()); break;
				case 2: b.append(r.nextLong()); break;
				case 3: b.append(r.nextDouble()*1000); break;
				default: b.append(r.nextInt(900)+100).append('.').append(r.nextInt(100)).append("e").append(r.nextInt(40)-20);
			}
		}
		b.append(']');
		return b.toString();
	}

	// Long strings, escapes, \\u escapes, non ASCII and surrogate pairs
	private static String strings(int count, long seed) {
		Random r = new Random(seed);
		StringBuilder b = new StringBuilder(count*300);
		b.append('[');
		for(int i=0; i<count; i++) {
			if(i>0) b.append(',');
			b.append('"');
			switch(i%4) {
				case 0: // long plain text
					for(int k=0; k<40; k++) b.append(word(r, 3+r.nextInt(8))).append(' ');
					break;
				case 1: // escapes
					b.append("line1\\nline2\\ttab \\\"quoted\\\" back\\\\slash \\/ ").append(word(r, 20));
					break;
				case 2: // unicode escapes
					b.append("caf\\u00e9 \\u4e2d\\u6587 \\ud83d\\ude00 ").append(word(r, 12));
					break;
				default: // raw non ASCII and surrogate pairs
					b.append("Größe naïve 日本語 🚀 ").append(word(r, 15));
			}
			b.append('"');
		}
		b.append(']');
		return b.toString();
	}

	private static String deep(int depth) {
		StringBuilder b = new StringBuilder(depth*16);
		for(int i=0; i<depth; i++) {
			b.append((i&1)==0 ? "{\"a\":" : "[1,");
		}
		b.append("true");
		for(int i=depth-1; i>=0; i--) {
			b.append((i&1)==0 ? '}' : ']');
		}
		return b.toString();
	}

	// Re-indent a compact JSON text (2 spaces, one value per line)
	private static String pretty(String compact) {
		StringBuilder b = new StringBuilder(compact.length()*2);
		int indent = 0;
		boolean inString = false;
		for(int i=0; i<compact.length(); i++) {
			char c = compact.charAt(i);
			if(inString) {
				b.append(c);
				if(c=='\\') { b.append(compact.charAt(++i)); }
				else if(c=='"') inString = false;
				continue;
			}
			switch(c) {
				case '"': inString = true; b.append(c); break;
				case '{': case '[': b.append(c).append('\n'); indent++; pad(b, indent); break;
				case '}': case ']': b.append('\n'); indent--; pad(b, indent); b.append(c); break;
				case ',': b.append(",\n"); pad(b, indent); break;
				case ':': b.append(": "); break;
				default: b.append(c);
			}
		}
		return b.toString();
	}
	private static void pad(StringBuilder b, int indent) {
		for(int i=0; i<indent; i++) b.append("  ");
	}

	private static String word(Random r, int len) {
		char[] c = new char[len];
		for(int i=0; i<len; i++) c[i] = (char)('a'+r.nextInt(26));
		return new String(c);
	}

	private static String resource(String path) {
		try(InputStream is = Datasets.class.getResourceAsStream(path)) {
			if(is==null) {
				throw new IllegalStateException("Missing resource "+path);
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
