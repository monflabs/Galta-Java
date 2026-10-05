/*
 * Copyright 2011 JSON-SMART authors (Uriel Chemouni)
 * Copyright (c) 2019-2026 Philippe Riand (modifications)
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
 *
 * Derived from json-smart (net.minidev.json.parser).
 */
package org.monflabs.json.parser;

import static org.monflabs.json.parser.ParseException.ERROR_SYNTAX;
import static org.monflabs.json.parser.ParseException.ERROR_UNEXPECTED_CHAR;
import static org.monflabs.json.parser.ParseException.ERROR_UNEXPECTED_EOF;
import static org.monflabs.json.parser.ParseException.ERROR_UNEXPECTED_TOKEN;
import static org.monflabs.json.parser.ParseException.ERROR_UNEXPECTED_UNICODE;

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.io.FastStringReader;


/**
 * Parser for JSON text. Please note that JSONParser is NOT thread-safe.
 * 
 * Inspired from JSON smart
 * 		https://github.com/netplex/json-smart-v1
 * 		https://github.com/netplex/json-smart-v2
 *
 * @author Uriel Chemouni &lt;uchemouni@gmail.com&gt;
 * @author Philippe Riand &lt;phil@riand.com&gt;
 */
public abstract class JsonParser {
	
	@FunctionalInterface
	public static interface Reviver {
		public static final Object IGNORE = new Object();
		public Object transform(Object container, String key, Object value, String context);
	}
	
	public static class StringParser extends JsonParser {
		private String sourceText;
		public StringParser(JsonFactory jsonFactory) {
			super(jsonFactory);
		}
		public Object parse(String json) throws JsonException, IOException {
			// Retained only for the duration of the parse so a reviver (see
			// setReviver()) can recover the exact raw source text of a value
			// (json-parse-with-source proposal's context.source) via
			// getSourceText() below - a String-backed parse is the only case
			// where an exact source substring is even possible.
			this.sourceText = json;
			try {
				return _parse(new FastStringReader(json));
			} finally {
				this.sourceText = null;
			}
		}
		@Override
		protected String getSourceText(int start, int end) {
			return sourceText.substring(start, end);
		}
		@Override
		protected String getFullSourceText() {
			return sourceText;
		}
	}

	public static class ReaderParser extends JsonParser {
		public ReaderParser(JsonFactory jsonFactory) {
			super(jsonFactory);
		}
		public Object parse(Reader in) throws JsonException, IOException {
			return _parse(in);
		}
	}

	
	private static final int EOI = -1;
	private static final int BOM = 0xFEFF;
	private static final int BUFFER_SIZE = 1024;

	/**
	 * Default maximum nesting depth of objects and arrays. A deeper content is rejected
	 * with a ParseException rather than overflowing the Java stack.
	 */
	public static final int DEFAULT_MAX_DEPTH = 1000;
	/**
	 * The highest nesting depth setMaxDepth() accepts: the parser is recursive, a deeper
	 * content would overflow the Java stack of a default thread.
	 */
	public static final int MAX_DEPTH_LIMIT = 2000;
	private int maxDepth = DEFAULT_MAX_DEPTH;
	private int depth;

	/**
	 * Default maximum length, in characters, of a number literal. Converting a huge
	 * literal to a BigInteger or a BigDecimal takes a time that grows faster than its
	 * length (a million digits took tens of seconds): a longer literal is rejected with a
	 * ParseException.
	 */
	public static final int DEFAULT_MAX_NUMBER_LENGTH = 1000;
	private int maxNumberLength = DEFAULT_MAX_NUMBER_LENGTH;
	private int numberStart;
		
	private JsonFactory jsonFactory;
	private boolean strict;
	
	private int c;
	private Reader in;
	// The reader reported the end of the input
	private boolean eof;
	// Skip a leading byte order mark (a stream/reader parse)
	private int bufferRead;
	private MSB sb;
	
	// Reading buffer
	private int bufferPos;
	private int bufferLength;
	private char[] buffer;
	
	private Reviver reviver;
	private Map<String,String> internedStrings;
	// Position (in getPosition() terms) of the first character of the value
	// readValue() is about to dispatch on - refreshed every loop iteration
	// (including ones that only skip whitespace/a comment), so by the time
	// readValue() returns it holds exactly the start of the returned value's
	// source text. Only meaningful right after a readValue() call.
	private int lastValueStart;

	
	// True when the factory parses integer literals the standard way (it doesn't override
	// JsonFactory.parseInteger() or parseInt()): the parser can then convert them itself,
	// see readIntegerFast()
	private final boolean defaultIntegerParsing;
	// Same for the decimal literals (parseDecimal() and parseFloat()), see readDecimalFast()
	private final boolean defaultDecimalParsing;
	private static final ClassValue<Boolean> DEFAULT_INTEGER_PARSING = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("parseInteger", String.class).getDeclaringClass()==JsonFactory.class
					&& type.getMethod("parseInt", String.class, int.class, int.class).getDeclaringClass()==JsonFactory.class;
			} catch(NoSuchMethodException ex) {
				return Boolean.FALSE;
			}
		}
	};

	private static final ClassValue<Boolean> DEFAULT_DECIMAL_PARSING = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("parseDecimal", String.class).getDeclaringClass()==JsonFactory.class
					&& type.getMethod("parseFloat", String.class, int.class).getDeclaringClass()==JsonFactory.class;
			} catch(NoSuchMethodException ex) {
				return Boolean.FALSE;
			}
		}
	};

	public JsonParser(JsonFactory jsonFactory) {
		this.jsonFactory = jsonFactory;
		this.defaultIntegerParsing = DEFAULT_INTEGER_PARSING.get(jsonFactory.getClass());
		this.defaultDecimalParsing = DEFAULT_DECIMAL_PARSING.get(jsonFactory.getClass());
	}
	
	public int getPosition() {
		return bufferRead+bufferPos;
	}
	
	public Reader getReader() {
		return in;
	}
	
	
	public boolean isStrict() {
		return strict;
	}

	public int getMaxDepth() {
		return maxDepth;
	}

	/**
	 * Set the maximum nesting depth of objects and arrays, capped at MAX_DEPTH_LIMIT.
	 */
	public void setMaxDepth(int maxDepth) {
		this.maxDepth = Math.min(maxDepth, MAX_DEPTH_LIMIT);
	}

	public int getMaxNumberLength() {
		return maxNumberLength;
	}

	/**
	 * Set the maximum length, in characters, of a number literal (sign, digits, '.' and
	 * exponent included). 0 or a negative value removes the limit, which is only safe when
	 * the factory converts the numbers to doubles: see DEFAULT_MAX_NUMBER_LENGTH.
	 */
	public void setMaxNumberLength(int maxNumberLength) {
		this.maxNumberLength = maxNumberLength>0 ? maxNumberLength : Integer.MAX_VALUE;
	}

	public void setStrict(boolean strict) {
		this.strict = strict;
	}

	public Reviver getReviver() {
		return reviver;
	}

	// Exact raw source text of the value spanning [start,end) (positions per
	// getPosition()), for context.source (json-parse-with-source proposal).
	// Only a String-backed parse (see StringParser) can answer this; other
	// parsers return null, and callers fall back to a reconstructed (not
	// necessarily byte-exact) text.
	protected String getSourceText(int start, int end) {
		return null;
	}

	/**
	 * The whole source text, when the parser owns it (a String parse), else null.
	 * Used to display the error location in a ParseException: the parser never
	 * rewinds or reads further a Reader it was given.
	 */
	protected String getFullSourceText() {
		return null;
	}

	// context.source for a reviver call on `v`, per InternalizeJSONProperty:
	// populated only for primitive JSON values (String/Number/null/bool),
	// left null (no "source" property) for Object/Array values. `start` is
	// the position of v's first character, captured by readValue() via
	// lastValueStart; the end position is wherever the parser cursor now
	// sits, immediately after readValue() returned.
	private String captureContext(int start, Object v) {
		if(v instanceof String || v instanceof Number) {
			// getPosition()-1 normally IS the index of the current char 'c'
			// (the standard convention used for error reporting throughout
			// this class) - but at true end-of-input, read() leaves bufferPos
			// un-incremented when it sets c=EOI (see read()), so getPosition()
			// itself (not -1) already equals the correct exclusive end index
			// when the value runs all the way to the end of the source text
			// (e.g. JSON.parse('1', reviver) with no trailing characters).
			int end = c==EOI ? getPosition() : getPosition()-1;
			String raw = getSourceText(start, end);
			if(raw!=null) {
				return raw;
			}
			// No source text (Reader parse): the buffer holds the number's text, or the
			// decoded string, which is quoted back to a JSON string literal
			return v instanceof String s ? JsonUtil.encodeString(s, '"') : sb.toString();
		} else if(v==null) {
			return "null";
		} else if(v==Boolean.TRUE) {
			return "true";
		} else if(v==Boolean.FALSE) {
			return "false";
		}
		return null;
	}

	public void setReviver(Reviver reviver) {
		this.reviver = reviver;
	}

	public boolean isInternStrings() {
		return internedStrings!=null;
	}

	public void setInternStrings(boolean intern) {
		internedStrings = intern ? new HashMap<>() : null;
	}

	public void setInternStringMap(Map<String,String> internMap) {
		internedStrings = internMap;
	}

	/**
	 * Reject a lenient-only construct in strict mode, reported at the current character.
	 */
	private void checkStrict(String construct) {
		if(strict) {
			throw new ParseException(this, ParseException.ERROR_UNEXPECTED_STRICT, currentPosition(), construct);
		}
	}
	private void checkStrict(String construct, int position) {
		if(strict) {
			throw new ParseException(this, ParseException.ERROR_UNEXPECTED_STRICT, position, construct);
		}
	}
	/**
	 * The position of the current character c, or the input length at the end of input.
	 */
	private int currentPosition() {
		return c==EOI ? getPosition() : getPosition()-1;
	}
	/**
	 * The end of input was reached where more content was expected: reported at the input
	 * length.
	 */
	private ParseException unexpectedEOF() {
		return new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition(), null);
	}
	
	//
	// char[] allocation is costly when the parser is heavily invoked (ex: micro benchmarks),
	// as it stresses the GC: the 2 buffers of the last parse are kept per thread. A nested
	// parse (from a reviver...) finds them in use and allocates its own.
	//
	private static final ThreadLocal<char[][]> BUFFERS = ThreadLocal.withInitial(() -> new char[2][]);
	private static char[] acquireBuffer(char[][] cache) {
		for(int i=0; i<cache.length; i++) {
			char[] b = cache[i];
			if(b!=null) {
				cache[i] = null;
				return b;
			}
		}
		return new char[BUFFER_SIZE];
	}
	private static void recycleBuffer(char[][] cache, char[] buffer) {
		for(int i=0; i<cache.length; i++) {
			if(cache[i]==null) {
				cache[i] = buffer;
				return;
			}
		}
	}

	
	//
	// Parse JSON content
	//
	
	protected Object _parse(Reader in) throws JsonException, IOException {
		this.in = in;
		this.bufferRead = 0;
		this.eof = false;
		char[][] cache = BUFFERS.get();
		this.buffer = acquireBuffer(cache);
		char[] msbBuffer = acquireBuffer(cache);
		this.sb = new MSB(msbBuffer);
		try {
			this.bufferPos = 0;
			this.bufferLength = 0;
			this.depth = 0;
			read();
			// A byte order mark is not JSON whitespace: the lenient parser skips a leading
			// one (whatever the source: String, Reader or InputStream), the strict parser
			// rejects it, like JSON.parse()
			if(c==BOM && !strict) {
				read();
			}
			skipSpacesAndComments();
			if(strict && c==EOI) {
				throw unexpectedEOF();
			}
			Object v;
			try {
				v = readValue();
			} catch(StackOverflowError e) {
				// Safety net: the depth limit should prevent it, unless the thread stack is
				// very small or a reviver recurses
				throw new ParseException(this, ERROR_SYNTAX, getPosition(), "Content nested too deeply for the parser stack");
			}
			if(reviver!=null) {
				String context = captureContext(lastValueStart, v);
				v = reviver.transform(null,"",v,context);
				if(v==Reviver.IGNORE) {
					v = null;
				}
			}
			skipSpacesAndComments();
			if(c!=EOI) {
				throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, c);
			}
			return v;
		} finally {
			recycleBuffer(cache, msbBuffer);
			recycleBuffer(cache, buffer);
		}
	}

	
	/**
	 * Reads the next char into c, EOI at the end of the input. The end of the input is
	 * sticky: the reader is not read again once it reported it.
	 */
	final private void read() throws IOException {
		if(bufferPos==bufferLength) {
			if(eof) {
				c = EOI;
				return;
			}
			bufferRead += bufferPos;
			int n = in.read(buffer, 0, BUFFER_SIZE);
			bufferPos = 0;
			if(n<0) {
				bufferLength = 0;
				eof = true;
				c = EOI;
				return;
			}
			bufferLength = n;
			if(n==0) {
				// A reader is not supposed to return 0 for a non empty buffer: try again
				read();
				return;
			}
		}
		c = buffer[bufferPos++];
	}
	/**
	 * Advances to the next char: the buffered one when available, else see read().
	 */
	private void next() throws IOException {
		if(bufferPos<bufferLength) {
			c = buffer[bufferPos++];
		} else {
			read();
		}
	}


	//
	// JSON Value - any
	//
	
	private Object readValue() throws JsonException, IOException {
		for (;;) {
			// Refreshed every iteration (a whitespace/comment case below just
			// `continue`s the loop) so it always ends up as the start
			// position of whichever value this call ultimately returns.
			lastValueStart = getPosition()-1;
			switch (c) {
				//PHIL:
				case -1:	return null;
				// skip spaces
				case ' ':
				case '\r':
				case '\n':
				case '\t':
					next();
					continue;
				// PHIL: added JavaScript like comments - considered as spaces
				case '/':
					readComment();
					continue;
				// invalid stats
				case ':':
				case '}':
				case ']':
					throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, c);
				// start object
				case '{':
					return readObject();
				// start Array
				case '[':
					return readArray();
				// start string
				case '"':
					return readString();
				case '\'':
					checkStrict("A single quoted string");
					return readString();
				case 'n':
					readKeyword("null");
					return null;
				case 'f':
					readKeyword("false");
					return Boolean.FALSE;
				case 't':
					readKeyword("true");
					return Boolean.TRUE;
				// digits
				case '.':
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
				case '+':
				case '-':
				case 'I':
				case 'N':
					return readNumber();
				default:
					throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, c);
			}
		}
	}

	
	
	//
	// JSON Object
	//
	
	private void enter() {
		if(++depth>maxDepth) {
			throw new ParseException(this, ERROR_SYNTAX, getPosition()-1, "Objects and arrays nested deeper than "+maxDepth+" levels");
		}
	}

	private JsonObject readObject() throws JsonException, IOException {
		/* assert (c == '{') */
		enter();
		JsonObject obj = jsonFactory.createObject();

		next();

		// 0: initial, 1: after a value, 2: after a ','
		int state = 0;
		for (;;) {
			switch (c) {
				case ' ':
				case '\r':
				case '\t':
				case '\n':
					next();
					continue;
				// PHIL: added JavaScript like comments - considered as spaces
				case '/':
					readComment();
					continue;
				case ':':
				case ']':
				case '[':
				case '{':
					throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
				case '}':
					if(state==2) {
						checkStrict("A trailing comma");
					}
					/* unstack */
					next();
					depth--;
					return obj;
				case ',':
					if(state!=1) {
						throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char) c);
					}
					state = 2;
					next();
					continue;
				case EOI:
					throw unexpectedEOF();
				case '"':
				case '\'':
				default:
					if(state==1) {
						throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char) c);
					}
					state = 1;
					String key;
					if (c == '\"') {
						key = readKey();
					} else if (c == '\'') {
						checkStrict("A single quoted key");
						key = readString();
					} else {
						// An unquoted name is a name, even "null" (a JSON key is never null)
						checkStrict("An unquoted key");
						key = readExtendedJSIdentifier();
					}
					// Comments are allowed here as anywhere else between tokens
					skipSpacesAndComments();
					if (c != ':') {
						if (c == EOI) {
							throw unexpectedEOF();
						}
						throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
					}				
					/* skip : */
					next();
					
					if(internedStrings!=null) {
				        String exist = internedStrings.putIfAbsent(key, key);
				        if(exist!=null) {
				        	key = exist;
				        }
					}
					
					// put Opt doesn't exist here
					Object v = readValue();
					if(reviver!=null) {
						String context = captureContext(lastValueStart, v);
						v = reviver.transform(obj,key,v,context);
						if(v!=Reviver.IGNORE) {
							obj.putValue(key,v);
						}
					} else {
						obj.putValue(key,v);
					}
	
					if (c == '}') {
						/* unstack */
						next();
						depth--;
						return obj;
					} /* if c==, confinue */
					continue;
			}
		}
	}
	private String readExtendedJSIdentifier() throws JsonException, IOException {
		if(Character.isJavaIdentifierStart(c) || c=='@') {
			final MSB sb = this.sb;
			sb.clear();
			sb.append((char)c);
			next();
			while(Character.isJavaIdentifierPart(c) || c=='@' ) {
				sb.append((char)c);
				next();
			}
			return sb.toString();
		}
		throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, c);
	}
	
	
	//
	// JSON Array
	//
	
	private JsonArray readArray() throws JsonException, IOException {
		/* assert (c == '[') */
		enter();
		JsonArray obj = jsonFactory.createArray();
		next();

		// 0: initial, 1: after a value, 2: after a ','
		int state = 0;
		for (;;) {
			switch (c) {
				case ' ':
				case '\r':
				case '\n':
				case '\t':
					next();
					continue;
				// PHIL: added JavaScript like comments - considered as spaces
				case '/':
					readComment();
					continue;
				case ']':
					if(state==2) {
						checkStrict("A trailing comma");
					}
					/* unstack */
					next();
					depth--;
					return obj;
				case ':':
				case '}':
					throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char) c);
				case ',':
					if(state!=1) {
						throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char) c);
					}
					state = 2;
					next();
					continue;
				case EOI:
					throw unexpectedEOF();
				default:
					if(state==1) {
						throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char) c);
					}
					state = 1;
					Object v = readValue();
					if(reviver!=null) {
						String context = captureContext(lastValueStart, v);
						v = reviver.transform(obj,Integer.toString(obj.size()),v,context);
						if(v!=Reviver.IGNORE) {
							obj.addValue(v);
						}
					} else {
						obj.addValue(v);
					}
					continue;
			}
		}
	}
	
	

	//
	// JSON String
	//

	//
	// Object keys repeat a lot (the same properties in every record): the recent ones are
	// kept in a small per thread cache, so a key already seen is returned as the same
	// String instance instead of creating a new one (its hash code is computed only once
	// as well). Only the short keys without escapes that are entirely in the buffer go
	// through the cache, the other ones are read the regular way.
	//
	private static final int KEY_CACHE_SIZE = 512;		// A power of 2
	private static final int KEY_CACHE_MAX_LENGTH = 32;
	private static final ThreadLocal<String[]> KEY_CACHE = ThreadLocal.withInitial(() -> new String[KEY_CACHE_SIZE]);
	private String[] keyCache;
	
	private String readKey() throws JsonException, IOException {
		if(reviver==null) {
			final char[] b = buffer;
			final int start = bufferPos;
			final int l = Math.min(bufferLength, start+KEY_CACHE_MAX_LENGTH+1);
			int h = 0;
			for (int p=start; p<l; ) {
				char ch = b[p++];
				if(ch=='"') {
					String key = cachedKey(b, start, p-start-1, h);
					// Same as the end of readString()
					this.bufferPos = p;
					if(p<bufferLength) {
						this.c = b[p];
						this.bufferPos = p+1;
					} else {
						read();
					}
					return key;
				}
				if(ch=='\\' || ch<0x20) {
					break;
				}
				h = 31*h+ch;
			}
		}
		return readString();
	}
	private String cachedKey(char[] b, int start, int len, int hash) {
		String[] cache = keyCache;
		if(cache==null) {
			cache = keyCache = KEY_CACHE.get();
		}
		int index = (hash ^ (hash>>>16)) & (KEY_CACHE_SIZE-1);
		String key = cache[index];
		if(key!=null && key.length()==len) {
			int i = 0;
			while(i<len && key.charAt(i)==b[start+i]) {
				i++;
			}
			if(i==len) {
				return key;
			}
		}
		key = new String(b, start, len);
		cache[index] = key;
		return key;
	}
	
	private String readString() throws JsonException, IOException {
		final char sep = (char) c;
		if(reviver!=null) { // need to keep the original in sb
			final MSB sb = this.sb;
			sb.init();
			return readStringSlow(sep);
		}
		final char[] b = buffer;
		final int l = bufferLength;
		for (int p=bufferPos; p<l; ) {
			char c = b[p++];
			if(c==sep) {
				String s = new String(b,bufferPos,p-bufferPos-1);
				this.bufferPos = p;
				if(p<l) {
					this.c = b[p];
					this.bufferPos = p+1;
				} else {
					// The quote was the last buffered char
					read();
				}
				return s;
			}
			if(c=='\\') {
				final MSB sb = this.sb;
				sb.init(b,bufferPos,p-bufferPos-1);
				this.bufferPos = p-1;
				return readStringSlow(sep);
			}
			if(c<0x0020) {
				checkStrict("A control character in a string", bufferRead+p-1);
			}
		}
		final MSB sb = this.sb;
		sb.init(b,bufferPos,l-bufferPos);
		this.bufferPos = l;
		return readStringSlow(sep);
	}
	
	private String readStringSlow(char sep) throws JsonException, IOException {
		final MSB sb = this.sb;
		// A line continuation ends with CR: a following LF is part of it
		boolean continuationCR = false;
		for (;;) {
			next();
			if(continuationCR) {
				continuationCR = false;
				if(c=='\n') {
					continue;
				}
			}
			switch (c) {
				case EOI:
					throw unexpectedEOF();
				case '"':
				case '\'':
					if (sep == c) {
						next();
						return sb.toString();
					}
					sb.append((char) c);
					break;
				case '\\':
					next();
					switch (c) {
						case 't':
							sb.append('\t');
							break;
						case 'n':
							sb.append('\n');
							break;
						case 'r':
							sb.append('\r');
							break;
						case 'f':
							sb.append('\f');
							break;
						case 'b':
							sb.append('\b');
							break;
						case '\\':
							sb.append('\\');
							break;
						case '/':
							sb.append('/');
							break;
						case '\'':
							// Not a JSON escape (only valid in a single quoted, non strict string)
							checkStrict("The \\' escape");
							sb.append('\'');
							break;
						case '"':
							sb.append('"');
							break;
						case 'u':
							sb.append(readUnicode(4));
							break;
						case 'x':
							checkStrict("The \\x escape");
							sb.append(readUnicode(2));
							break;
						case '\n':
							checkStrict("A line continuation");
							break;
						case '\r':
							checkStrict("A line continuation");
							continuationCR = true; // CRLF is one line terminator
							break;
						case EOI:
							throw unexpectedEOF();
						default:
							throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
					}
					break;
				case '\b':
				case '\t':
				case '\f':
				case '\r':
				case '\n':
					// Same rule as the fast path: rejected in strict mode, kept otherwise
					// (this path used to silently drop the character)
					checkStrict("A control character in a string");
					sb.append((char) c);
					break;
				default:
					// The other control characters are invalid in strict mode as well
					// (the fast path checks them, this path did not)
					if(c<0x20) {
						checkStrict("A control character in a string");
					}
					sb.append((char) c);
					// The plain characters that follow in the buffer are copied at once
					final char[] b = buffer;
					final int l = bufferLength;
					final int start = bufferPos;
					int p = start;
					while(p<l) {
						char x = b[p];
						if(x==sep || x=='\\' || x<0x20) {
							break;
						}
						p++;
					}
					if(p>start) {
						sb.append(b, start, p-start);
						bufferPos = p;
					}
			}
		}
	}

	protected char readUnicode(int totalChars) throws ParseException, IOException {
		int value = 0;
		for (int i = 0; i < totalChars; i++) {
			value = value * 16;
			next();
			if (c <= '9' && c >= '0')
				value += c - '0';
			else if (c <= 'F' && c >= 'A')
				value += (c - 'A') + 10;
			else if (c >= 'a' && c <= 'f')
				value += (c - 'a') + 10;
			else if (c == EOI)
				throw unexpectedEOF();
			else
				throw new ParseException(this, ERROR_UNEXPECTED_UNICODE, getPosition()-1, (char)c);
		}
		return (char) value;
	}


	
	//
	// JSON Number
	//

	private Object readNumber() throws JsonException, IOException {
		final MSB sb = this.sb;
		sb.clear();
		final int start = getPosition()-1;
		this.numberStart = start;

		// Initial sign
		if(c=='-' || c=='+') {
			if(c=='+') {
				checkStrict("A leading '+'");
			}
			sb.append((char) c);// first char digit or +-
			next();
			// Must be followed by a digit (-.1 not permitted)
			if (c < '0' || c > '9') {
				if(c==EOI) {
					throw unexpectedEOF();
				}
				if(c!='N' && c!='I') { // -Infinity: reported below
					checkStrict("A sign not followed by a digit");
				}
			}
		}

		// Special numbers
		if(c=='N' || c=='I') {
			readSpecialNumber(sb);
			String s = sb.toString();
			if(s.equals("NaN")) {
				checkStrict("NaN", start);
				return Double.valueOf(Double.NaN);
			}
			if(s.equals("Infinity") || s.equals("+Infinity")) {
				checkStrict("Infinity", start);
				return Double.POSITIVE_INFINITY;
			}
			if(s.equals("-Infinity")) {
				checkStrict("Infinity", start);
				return Double.NEGATIVE_INFINITY;
			}
			throw new ParseException(this, ERROR_UNEXPECTED_TOKEN, start, s);
		}
		
		// Digits of the integer and the fraction parts: a number needs at least one
		int digits = 0;
		if(c=='0') {
			sb.append('0');
			digits++;
			next();
			if(c=='x' || c=='X') {
				checkStrict("A hexadecimal number", start);
				sb.append((char)c);
				next();
				// Must be an integer
				if(readHexaDigits(sb)==0) {
					if(c==EOI) {
						throw unexpectedEOF();
					}
					throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
				}
				String xs = sb.toString();
				try {
					return jsonFactory.parseIntegerWithRadix(xs);
				} catch(RuntimeException e) {
					throw invalidNumber(xs, e);
				}
			}
			if (c >= '0' && c <= '9') { // cannot be 0[0-9] in strict json
				checkStrict("A leading zero", start);
			}
		} else if(c=='.') {
			checkStrict("A number starting with '.'");
		}

		// Integer digits
		digits += readDigits(sb);
		
		// Stop here if it is an integer!
		if (c != '.' && c != 'E' && c != 'e') {
			checkNumberDigits(digits);
			if(defaultIntegerParsing) {
				Number n = readIntegerFast(sb);
				if(n!=null) {
					return n;
				}
			}
			String xs = sb.toString();
			try {
				return jsonFactory.parseInteger(xs);
			} catch(RuntimeException e) {
				throw invalidNumber(xs, e);
			}
		}

		if (c == '.') {
			sb.append((char) c);
			checkNumberLength();
			next();
			int fraction = readDigits(sb);
			if(fraction==0) { // No decimal character added (ex: 1.)
				checkStrict("A '.' not followed by a digit");
			}
			digits += fraction;
		}
		// Even lenient, ".", "-." or ".e5" are not numbers
		checkNumberDigits(digits);
		if (c != 'E' && c != 'e') {
			return toDecimal(sb);
		}
		sb.append('E');
		checkNumberLength();
		next();
		if (c == '+' || c == '-' || c >= '0' && c <= '9') {
			boolean sign = c == '+' || c == '-';
			sb.append((char) c);
			// skip first char
			next();
			if(readDigits(sb)==0 && sign) {
				// "1e+": the exponent has no digit
				if(c==EOI) {
					throw unexpectedEOF();
				}
				throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
			}
			return toDecimal(sb);
		}
		if(c==EOI) {
			throw unexpectedEOF();
		}
		throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
	}
	private Number toDecimal(MSB sb) {
		if(defaultDecimalParsing) {
			Number n = readDecimalFast(sb);
			if(n!=null) {
				return n;
			}
		}
		String num = sb.toString();
		try {
			return jsonFactory.parseDecimal(num);
		} catch(RuntimeException e) {
			throw invalidNumber(num, e);
		}
	}
	// The factory could not convert a number literal (a custom factory, or an exponent
	// out of the BigDecimal range): a parse error at the start of the literal
	private ParseException invalidNumber(String literal, RuntimeException e) {
		if(e instanceof ParseException pe) {
			return pe;
		}
		String reason = e.getMessage();
		if(e.getCause()!=null && e.getCause().getMessage()!=null) {
			reason = e.getCause().getMessage();
		}
		ParseException pe = new ParseException(this, ERROR_SYNTAX, numberStart, "Invalid number "+ParseException.clip(literal)+(reason!=null ? " ("+reason+")" : ""), e);
		return pe;
	}
	private void checkNumberLength() {
		if(sb.p>maxNumberLength) {
			throw new ParseException(this, ERROR_SYNTAX, numberStart, "Number literal longer than "+maxNumberLength+" characters");
		}
	}
	private void checkNumberDigits(int digits) {
		if(digits==0) {
			if(c==EOI) {
				throw unexpectedEOF();
			}
			throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
		}
	}
	/**
	 * Convert an integer literal the parser already validated ([+-]digits, fitting a long)
	 * without going through a String and parseInteger(): the
	 * result is the one JsonFactory.parseInteger() returns for the same literal (same
	 * number type rules, -0 is a double). Returns null when the fast path doesn't apply.
	 */
	private Number readIntegerFast(MSB sb) {
		final char[] b = sb.b;
		final int len = sb.p;
		int i = 0;
		boolean negative = false;
		if(len>0 && (b[0]=='-' || b[0]=='+')) {
			negative = b[0]=='-';
			i = 1;
		}
		int digits = len-i;
		if(digits<1 || digits>19) {
			return null;
		}
		long v = 0;
		// Up to 18 digits always fit, the 19th one may overflow: the general path then
		// decides (BigInteger or double), as well as for Long.MIN_VALUE
		int last = digits==19 ? len-1 : len;
		for(; i<last; i++) {
			v = v*10 + (b[i]-'0');
		}
		if(last<len) {
			int digit = b[last]-'0';
			if(v>(Long.MAX_VALUE-digit)/10) {
				return null;
			}
			v = v*10 + digit;
		}
		if(negative) {
			if(v==0) {
				// -0 is a double, as in JavaScript (an integer type has no negative zero)
				return -0.0;
			}
			v = -v;
		}
		// Same rules as JsonFactory._parseValidInteger() for a value that fits a long
		JsonFactory.INTEGER def = jsonFactory.defaultInteger();
		if(def==JsonFactory.INTEGER.BIGINT) {
			return BigInteger.valueOf(v);
		}
		if(def==JsonFactory.INTEGER.INT && v>=Integer.MIN_VALUE && v<=Integer.MAX_VALUE) {
			return Integer.valueOf((int)v);
		}
		if(def==JsonFactory.INTEGER.LONG || jsonFactory.useLongIntegers()) {
			return Long.valueOf(v);
		}
		if(jsonFactory.overflowInteger()!=JsonFactory.OVERFLOW_INTEGER.BIGINT) {
			return Double.valueOf((double)v);
		}
		return BigInteger.valueOf(v);
	}
	
	// Powers of ten exactly represented as doubles
	private static final double[] POW10 = {
		1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
		1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
	};
	
	/**
	 * Convert a decimal literal the parser already validated, when the factory gives
	 * doubles, without a String and Double.parseDouble(), for the literals with at most 19
	 * significant digits. With at most 15 digits and a small exponent (Clinger's fast path),
	 * the mantissa and the power of ten are exact doubles, so a single multiplication or
	 * division gives the correctly rounded value. The others use the Eisel-Lemire algorithm
	 * ({@link DecimalToDouble}), which gives the correctly rounded value too: the one
	 * Double.parseDouble() returns. The result is the one JsonFactory.parseDecimal() returns:
	 * when the factory keeps a BigDecimal for a value a double can't hold, a literal of more
	 * than 15 digits is checked against the shortest digits of the double.
	 * Returns null when the fast path doesn't apply (the general path then decides).
	 */
	private Number readDecimalFast(MSB sb) {
		if(jsonFactory.defaultDecimal()!=JsonFactory.DECIMAL.DOUBLE) {
			return null;
		}
		final char[] b = sb.b;
		final int len = sb.p;
		int i = 0;
		boolean negative = false;
		if(len>0 && (b[0]=='-' || b[0]=='+')) {
			negative = b[0]=='-';
			i = 1;
		}
		long mantissa = 0;
		int significant = 0;	// Digits in the mantissa, from the first non zero one
		int scale = 0;			// Decimal digits in the mantissa
		boolean dot = false;
		for(; i<len; i++) {
			char c = b[i];
			if(c=='.') {
				dot = true;
				continue;
			}
			if(c=='E' || c=='e') {
				break;
			}
			int digit = c-'0';
			if(dot) {
				scale--;
			}
			if(significant==0 && digit==0) {
				continue; // Leading zero
			}
			if(significant==19) {
				return null;
			}
			mantissa = mantissa*10+digit;
			significant++;
		}
		int exp = 0;
		if(i<len) {
			i++; // 'e' or 'E'
			boolean negativeExp = false;
			if(i<len && (b[i]=='-' || b[i]=='+')) {
				negativeExp = b[i]=='-';
				i++;
			}
			for(; i<len; i++) {
				exp = exp*10+(b[i]-'0');
				if(exp>1000) {
					return null;
				}
			}
			if(negativeExp) {
				exp = -exp;
			}
		}
		int e = exp+scale;
		double d;
		if(mantissa==0) {
			d = 0.0;
		} else if(significant<=15 && e==0) {
			d = mantissa;
		} else if(significant<=15 && e>0 && e<=22) {
			d = mantissa*POW10[e];
		} else if(significant<=15 && e<0 && e>=-22) {
			d = mantissa/POW10[-e];
		} else {
			d = DecimalToDouble.toDouble(mantissa, e, false);
			if(Double.isNaN(d)) {
				// A subnormal, an underflow or an overflow: the general path decides
				return null;
			}
			// A normal double holds 15 significant digits: a longer literal may lose some,
			// and the factory then keeps it as a BigDecimal
			if(significant>15 && jsonFactory.overflowDecimal()==JsonFactory.OVERFLOW_DECIMAL.BIGDEC
					&& !isShortest(mantissa, e, d)) {
				return null;
			}
		}
		return Double.valueOf(negative ? -d : d);
	}

	/**
	 * Whether w * 10^e (w unsigned) is exactly the value of the shortest decimal form of the
	 * double d (positive): the double then holds the literal without any loss. The same as
	 * comparing the literal with Double.toString(d) as BigDecimal values.
	 */
	private static boolean isShortest(long w, int e, double d) {
		while(Long.remainderUnsigned(w, 10)==0) {
			w = Long.divideUnsigned(w, 10);
			e++;
		}
		String s = Double.toString(d);
		long digits = 0;
		int fraction = 0;
		boolean dot = false;
		int len = s.length();
		int i = 0;
		for(; i<len; i++) {
			char c = s.charAt(i);
			if(c=='.') {
				dot = true;
			} else if(c=='E') {
				break;
			} else {
				digits = digits*10 + (c-'0');
				if(dot) {
					fraction++;
				}
			}
		}
		int exponent = i<len ? Integer.parseInt(s, i+1, len, 10) : 0;
		exponent -= fraction;
		if(digits==0) {
			return false;
		}
		while(digits%10==0) {
			digits /= 10;
			exponent++;
		}
		return digits==w && exponent==e;
	}
	
	private int readDigits(MSB sb) throws JsonException, IOException {
		int count = 0;
		for (;;) {
			if (c < '0' || c > '9') {
				return count;
			}
			count++;
			sb.append((char) c);
			checkNumberLength();
			next();
		}
	}
	private int readHexaDigits(MSB sb) throws JsonException, IOException {
		int count = 0;
		for (;;) {
			if ( !( (c>='0' && c<='9') || (c>='a' && c<='f')  || (c>='A' && c<='F')) ) {
				return count;
			}
			count++;
			sb.append((char) c);
			checkNumberLength();
			next();
		}
	}
	private void readSpecialNumber(MSB sb) throws JsonException, IOException {
		for (;;) {
			if (!Character.isLetter(c)) {
				return;
			}
			sb.append((char) c);
			checkNumberLength();
			next();
		}
	}

	
	
	//
	// Comments and spaces
	//
	
	private void readComment() throws JsonException, IOException {
		checkStrict("A comment");
		/* assert (c == '/') */
		next();
		if(c=='*') {
			next();
			readMultilineComment();
		} else if(c=='/') {
			next();
			readSinglelineComment();
		} else if(c==EOI) {
			throw unexpectedEOF();
		} else {
			// '/' not followed by '*' or '/'
			throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
		}
	}
	private void readSinglelineComment() throws JsonException, IOException {
		for (;;) {
			switch (c) {
				case '\r':
				case '\n':
				case EOI:
					return;
			}
			next();
		}
	}
	private void readMultilineComment() throws JsonException, IOException {
		for (;;) {
			switch (c) {
				case '*':
					read();
					if(c=='/') {
						read();
						return;
					}
					// Re-examine the character just read: it may itself be the '*' that
					// closes the comment ("**/"), which used to be skipped
					continue;
				case EOI:
					throw unexpectedEOF();
			}
			next();
		}
	}

	
	//
	// Utilities
	//
	
	/**
	 * Read the keyword starting with the current character (true, false or null), without
	 * creating a String when the keyword and the character after it are in the buffer.
	 */
	private void readKeyword(String keyword) throws JsonException, IOException {
		final char[] b = buffer;
		final int p = bufferPos;		// Position of the second character
		final int len = keyword.length();
		if(p+len-1<bufferLength) {
			int i = 1;
			while(i<len && b[p+i-1]==keyword.charAt(i)) {
				i++;
			}
			if(i==len) {
				char next = b[p+len-1];
				if(!((next>='a' && next<='z') || (next>='A' && next<='Z'))) {
					this.c = next;
					this.bufferPos = p+len;
					return;
				}
			}
		}
		int position = getPosition()-1;
		String xs = readAlphaKeyword();
		if(!keyword.equals(xs)) {
			throw new ParseException(this, ERROR_UNEXPECTED_TOKEN, position, ParseException.clip(xs));
		}
	}

	private String readAlphaKeyword() throws JsonException, IOException {
		final char[] b = buffer;
		final int l = bufferLength;
		for (int p=bufferPos; p<l; ) {
			char c = b[p++];
			if( !((c>='a' && c<='z') || (c>='A' && c<='Z')) ) { 
				String s = new String(b,bufferPos-1,p-bufferPos);
				this.bufferPos = p;
				this.c = c;
				return s;
			}
		}
		return readAlphaKeywordSlow();
	}
	private String readAlphaKeywordSlow() throws JsonException, IOException {
		final MSB sb = this.sb;
		sb.clear();
		while((c>='a' && c<='z') || (c>='A' && c<='Z')) { 
			sb.append((char)c);
			next();
		}
		return sb.toString();
	}
	
	
	private void skipSpacesAndComments() throws JsonException, IOException {
		for (;;) {
			switch(c) {
				case ' ':
				case '\r':
				case '\t':
				case '\n':
					next();
					break;
				case '/':
					readComment(); break;
				default:
					return;
			}
		}
	}
	
	
	// Micro-optimization that replaces a StringBuilder
	private static final class MSB {
		private char b[];
		private int p;

		public MSB(char[] buffer) {
			b = buffer;
		}
		public void init() {
			p = 0;
		}
		public void init(char[] b, int pos, int len) {
			p = len;
			if(len!=0) {
				System.arraycopy(b, pos, this.b, 0, len);
			}
		}
		public void append(char c) {
			if (p==b.length) {
				char[] t = new char[b.length * 2 + 1];
				System.arraycopy(b, 0, t, 0, b.length);
				b = t;
			}
			b[p++] = c;
		}
		public void append(char[] src, int pos, int len) {
			if (p+len>b.length) {
				b = java.util.Arrays.copyOf(b, Math.max(b.length*2+1, p+len));
			}
			System.arraycopy(src, pos, b, p, len);
			p += len;
		}

		@Override
		public String toString() {
			return new String(b, 0, p);
		}

		public void clear() {
			p = 0;
		}
	}

}
