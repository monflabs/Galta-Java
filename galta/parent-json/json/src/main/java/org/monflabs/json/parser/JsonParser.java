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
package org.monflabs.json.parser;

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
	private static final int BUFFER_SIZE = 1024;
	private static final int MAX_BUFFERPOOL_SIZE = 16;

	/**
	 * Default maximum nesting depth of objects and arrays. A deeper content is rejected
	 * with a ParseException rather than overflowing the Java stack.
	 */
	public static final int DEFAULT_MAX_DEPTH = 1000;
	private int maxDepth = DEFAULT_MAX_DEPTH;
	private int depth;
		
	private JsonFactory jsonFactory;
	private boolean strict;
	
	private int c;
	private Reader in;
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

	public void setMaxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
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

	private void checkStrict() {
		if(strict) {
			throw new ParseException(this, ParseException.ERROR_UNEXPECTED_STRICT, getPosition(), null);
		}
	}
	
	//
	// It appears that char[] allocation is costly when the buffer is large and when the parser
	// is heavily invoked (ex: micro benchmarks). That stresses the GC and leads to bad performance.
	// A buffer pool makes it far better here.
	// 

	private static char[][] bufferPool = new char[MAX_BUFFERPOOL_SIZE][];
	private static int bufferPoolSize = 0;
	private static synchronized char[] acquireBuffer() {
		if(bufferPoolSize==0) {
			return new char[BUFFER_SIZE];
		}
		return bufferPool[--bufferPoolSize];
	}
	private static synchronized void recycleBuffer(char[] buffer) {
		if(bufferPoolSize<MAX_BUFFERPOOL_SIZE) {
			bufferPool[bufferPoolSize++] = buffer;
		}
	}

	
	
	//
	// Parse JSON content
	//
	
	protected Object _parse(Reader in) throws JsonException, IOException {
		this.in = in;
		this.bufferRead = 0;
		this.buffer = acquireBuffer();
		char[] msbBuffer = acquireBuffer();
		this.sb = new MSB(msbBuffer);
		try {
			// We don't support BOM right as we already deal with characters
			this.bufferPos = 0;
			this.bufferLength = 0;
			this.depth = 0;
			read();
			skipSpacesAndComments();
			if(strict && c==EOI) {
				throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition(), c);
			}
			Object v = readValue();
			if(reviver!=null) {
				String context = captureContext(lastValueStart, v);
				v = reviver.transform(null,"",v,context);
				if(v==Reviver.IGNORE) {
					v = null;
				}
			}
			skipSpacesAndComments();
			if(c!=EOI) {
				throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition(), c);
			}
			return v;
		} finally {
			recycleBuffer(msbBuffer);
			recycleBuffer(buffer);
		}
	}

	
	final private void read() throws IOException {
		if(bufferPos==bufferLength) {
			bufferRead += bufferPos;
			bufferLength = in.read(buffer, 0, BUFFER_SIZE);
			bufferPos = 0;
			if(bufferLength<0) {
				c = -1;
				return;
			}
		}
		c = buffer[bufferPos++];
	}


//	private final void read() throws IOException {
//		if(bufferPos==bufferLength) {
//			fillBuffer(1);
//			if(bufferPos==bufferLength) {
//				c = EOI;
//				return;
//			}
//		}
//		c = buffer[bufferPos++];
//		pos++;
//	}
//	private final void fillBuffer(int minimum) throws IOException {
//		int size = bufferLength-bufferPos;
//		if(size<minimum) {
//			if(size>0) {
//				System.arraycopy(buffer, bufferPos, buffer, 0, size);
//			}
//			bufferPos = 0;
//			bufferLength = size;
//			int count;
//		    while( bufferLength<minimum && (count = in.read(buffer, bufferPos, BUFFER_SIZE - bufferPos)) != -1) {
//		    	bufferLength += count;
//		    	// if this is the first read, consume an optional byte order mark (BOM) if it exists
////			    if (lineNumber == 0 && lineStart == 0 && limit > 0 && buffer[0] == '\ufeff') {
////			    	pos++;
////			        lineStart++;
////			        minimum++;
////			    }
//		    }
//		}
//	}

	
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
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
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
					checkStrict();
					return readString();
				// null
				case 'n':
					int position = getPosition()-1;
					String xs = readAlphaKeyword();
					if ("null".equals(xs)) {
						return null;
					}
					throw new ParseException(this, ERROR_UNEXPECTED_TOKEN, position, xs);
				// false
				case 'f':
					position = getPosition()-1;
					xs = readAlphaKeyword();
					if ("false".equals(xs)) {
						return Boolean.FALSE;
					}
					throw new ParseException(this, ERROR_UNEXPECTED_TOKEN, position, xs);
				// true
				case 't':
					position = getPosition()-1;
					xs = readAlphaKeyword();
					if ("true".equals(xs)) {
						return Boolean.TRUE;
					}
					throw new ParseException(this, ERROR_UNEXPECTED_TOKEN, position, xs);
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
			throw new ParseException(this, ERROR_UNEXPECTED_TOKEN, getPosition()-1, "nesting deeper than "+maxDepth);
		}
	}

	private JsonObject readObject() throws JsonException, IOException {
		/* assert (c == '{') */
		enter();
		JsonObject obj = jsonFactory.createObject();

		if(bufferPos<bufferLength) {
			c = buffer[bufferPos++];
		} else {
			read();
		}

		// 0: initial, 1: after a value, 2: after a ','
		int state = 0;
		for (;;) {
			switch (c) {
				case ' ':
				case '\r':
				case '\t':
				case '\n':
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
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
						checkStrict();
					}
					/* unstack */
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
					depth--;
					return obj;
				case ',':
					if(state!=1) {
						throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char) c);
					}
					state = 2;
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
					continue;
				case EOI:
					throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition(), "EOF");
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
						checkStrict();
						key = readString();
					} else {
						// An unquoted name is a name, even "null" (a JSON key is never null)
						key = readExtendedJSIdentifier();
						checkStrict();
					}
					// Comments are allowed here as anywhere else between tokens
					skipSpacesAndComments();
					if (c != ':') {
						if (c == EOI) {
							throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition()-1, null);
						}
						throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (char)c);
					}				
					/* skip : */
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
					
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
						if(bufferPos<bufferLength) {
							c = buffer[bufferPos++];
						} else {
							read();
						}
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
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			while(Character.isJavaIdentifierPart(c) || c=='@' ) {
				sb.append((char)c);
				if(bufferPos<bufferLength) {
					c = buffer[bufferPos++];
				} else {
					read();
				}
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
		if(bufferPos<bufferLength) {
			c = buffer[bufferPos++];
		} else {
			read();
		}

		// 0: initial, 1: after a value, 2: after a ','
		int state = 0;
		for (;;) {
			switch (c) {
				case ' ':
				case '\r':
				case '\n':
				case '\t':
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
					continue;
				// PHIL: added JavaScript like comments - considered as spaces
				case '/':
					readComment();
					continue;
				case ']':
					if(state==2) {
						checkStrict();
					}
					/* unstack */
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
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
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
					continue;
				case EOI:
					throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition(), "EOF");
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
					return s;
				} else {
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
					return s;
				}
			}
			if(c=='\\') {
				final MSB sb = this.sb;
				sb.init(b,bufferPos,p-bufferPos-1);
				this.bufferPos = p-1;
				return readStringSlow(sep);
			}
			if(strict) {
				if(c<0x0020 || c>0x10FFFF) {
					checkStrict();
				}
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
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			if(continuationCR) {
				continuationCR = false;
				if(c=='\n') {
					continue;
				}
			}
			switch (c) {
				case EOI:
					throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition()-1, null);
				case '"':
				case '\'':
					if (sep == c) {
						if(bufferPos<bufferLength) {
							c = buffer[bufferPos++];
						} else {
							read();
						}
						return sb.toString();
					}
					sb.append((char) c);
					break;
				case '\\':
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
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
							checkStrict();
							sb.append('\'');
							break;
						case '"':
							sb.append('"');
							break;
						case 'u':
							sb.append(readUnicode(4));
							break;
						case 'x':
							checkStrict();
							sb.append(readUnicode(2));
							break;
						case '\n':
							checkStrict();
							break;
						case '\r':
							checkStrict();
							continuationCR = true; // CRLF is one line terminator
							break;
						case EOI:
							throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition(), "EOF");
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
					checkStrict();
					sb.append((char) c);
					break;
				default:
					// The other control characters are invalid in strict mode as well
					// (the fast path checks them, this path did not)
					if(c<0x20) {
						checkStrict();
					}
					sb.append((char) c);
			}
		}
	}

	protected char readUnicode(int totalChars) throws ParseException, IOException {
		int value = 0;
		for (int i = 0; i < totalChars; i++) {
			value = value * 16;
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			if (c <= '9' && c >= '0')
				value += c - '0';
			else if (c <= 'F' && c >= 'A')
				value += (c - 'A') + 10;
			else if (c >= 'a' && c <= 'f')
				value += (c - 'a') + 10;
			else if (c == EOI)
				throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition()-1, "EOF");
			else
				throw new ParseException(this, ERROR_UNEXPECTED_UNICODE, getPosition()-1, c);
		}
		return (char) value;
	}


	
	//
	// JSON Number
	//

	private Object readNumber() throws JsonException, IOException {
		final MSB sb = this.sb;
		sb.clear();
		
		// Initial sign
		if(c=='-' || c=='+') {
			if(c=='+') {
				checkStrict();
			}
			sb.append((char) c);// first char digit or +-
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			// Must be followed by a digit (-.1 not permitted)
			if (c < '0' || c > '9') {
				checkStrict();
			}
		}
		
		// Special numbers
		if(c=='N' || c=='I') {
			readSpecialNumber(sb);
			String s = sb.toString();
			if(s.equals("NaN")) {
				checkStrict();
				return Double.valueOf(Double.NaN);
			}
			if(s.equals("Infinity") || s.equals("+Infinity")) {
				checkStrict();
				return Double.POSITIVE_INFINITY;
			}
			if(s.equals("-Infinity")) {
				checkStrict();
				return Double.NEGATIVE_INFINITY;
			}
			throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (int)c);
		}
		
		if(c=='0') {
			sb.append('0');
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			if(c=='x' || c=='X') {
				sb.append((char)c);
				checkStrict();
				if(bufferPos<bufferLength) {
					c = buffer[bufferPos++];
				} else {
					read();
				}
				// Must be an integer
				try {
					readHexaDigits(sb);
					String xs = sb.toString();
					return jsonFactory.parseIntegerWithRadix(xs);
				} catch(Exception e) {
					throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, (int)c);
				}
			}
			if (c >= '0' && c <= '9') { // cannot be 0[0-9] in strict json
				checkStrict();
			}
		} else if(c=='.') {
			checkStrict();
		}

		// Integer digits
		readDigits(sb);
		
		// Stop here if it is an integer!
		if (c != '.' && c != 'E' && c != 'e') {
			if(defaultIntegerParsing) {
				Number n = readIntegerFast(sb);
				if(n!=null) {
					return n;
				}
			}
			String xs = sb.toString();
			return jsonFactory.parseInteger(xs);
		}
		
		if (c == '.') {
			sb.append((char) c);
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			int pos = sb.p;
			readDigits(sb);
			if(pos==sb.p) { // No decimal character added (ex: 1.)
				checkStrict();
			}
		}
		if (c != 'E' && c != 'e') {
			if(defaultDecimalParsing) {
				Number n = readDecimalFast(sb);
				if(n!=null) {
					return n;
				}
			}
			String num = sb.toString();
			return jsonFactory.parseDecimal(num);
		}
		sb.append('E');
		if(bufferPos<bufferLength) {
			c = buffer[bufferPos++];
		} else {
			read();
		}
		if (c == '+' || c == '-' || c >= '0' && c <= '9') {
			boolean sign = c == '+' || c == '-';
			sb.append((char) c);
			// skip first char
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			if(readDigits(sb)==0 && sign) {
				// "1e+": the exponent has no digit
				throw new ParseException(this, c==EOI ? ERROR_UNEXPECTED_EOF : ERROR_UNEXPECTED_CHAR, getPosition()-1, c==EOI ? "EOF" : Character.valueOf((char)c));
			}
			if(defaultDecimalParsing) {
				Number n = readDecimalFast(sb);
				if(n!=null) {
					return n;
				}
			}
			return jsonFactory.parseDecimal(sb.toString());
		}
		throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, c==EOI ? "EOF" : Character.valueOf((char)c));
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
		for(; i<len; i++) {
			int digit = b[i]-'0';
			// Up to 18 digits always fit, the 19th one may overflow: the general path then
			// decides (BigInteger or double), as well as for Long.MIN_VALUE
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
	 * doubles, without a String and Double.parseDouble(). Only for the literals with at
	 * most 15 significant digits and a small exponent (Clinger's fast path): the mantissa
	 * and the power of ten are exact doubles, so a single multiplication or division gives
	 * the correctly rounded value, the one Double.parseDouble() returns. Such a value
	 * always fits a double, so the result is the one JsonFactory.parseDecimal() returns.
	 * Returns null when the fast path doesn't apply.
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
			if(significant==15) {
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
		} else if(e==0) {
			d = mantissa;
		} else if(e>0 && e<=22) {
			d = mantissa*POW10[e];
		} else if(e<0 && e>=-22) {
			d = mantissa/POW10[-e];
		} else {
			return null;
		}
		return Double.valueOf(negative ? -d : d);
	}
	
	private int readDigits(MSB sb) throws JsonException, IOException {
		int count = 0;
		for (;;) {
			if (c < '0' || c > '9') {
				return count;
			}
			count++;
			sb.append((char) c);
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
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
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
		}
	}
	private void readSpecialNumber(MSB sb) throws JsonException, IOException {
		for (;;) {
			if (!Character.isLetter(c)) {
				return;
			}
			sb.append((char) c);
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
		}
	}

/*
	private Object readNumber() throws JsonException, IOException {
		int position = getPosition()-1;
		String s = readNumberString();
		if(s.equals("NaN")) {
			checkStrict();
			return Double.valueOf(Double.NaN);
		}
		if(s.equals("Infinity") || s.equals("+Infinity")) {
			checkStrict();
			return Double.POSITIVE_INFINITY;
		}
		if(s.equals("-Infinity")) {
			checkStrict();
			return Double.NEGATIVE_INFINITY;
		}
		try {
			if(numberIsInteger) {
				return jsonFactory.parseInteger(s);
			} else {
				return jsonFactory.parseDecimal(s);
			}
		} catch(Exception e) {
			throw new ParseException(this,ParseException.ERROR_UNEXPECTED_STRICT,position,"Invalid number literal "+s);
		}
	}
	
	private boolean numberIsInteger;
	private String readNumberString() throws JsonException, IOException {
		numberIsInteger = true;
		final char[] b = buffer;
		final int l = bufferLength;
		if(c=='+') {
			checkStrict();
		}
		for (int p=bufferPos; p<l; ) {
			char c = b[p++];
			if( !isNumberChar(c) ) { 
				String s = new String(b,bufferPos-1,p-bufferPos);
				this.bufferPos = p;
				this.c = c;
				return s;
			}
			if(c!='+' && c!='-' && (c<'0' || c>'9')) {
				numberIsInteger = false;
			}
		}
		return readNumberStringSlow();
	}
	private String readNumberStringSlow() throws JsonException, IOException {
		final MSB sb = this.sb;
		sb.clear();
		while(isNumberChar(c)) {
			if(c<'0' || c>'9') {
				numberIsInteger = false;
			}
			sb.append((char)c);
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
		}
		return sb.toString();
	}
	private static boolean isNumberChar(int c) {
		return     c=='+' 
				|| c=='-'
				|| (c>='0' && c<='9')
				|| c=='.'
				|| (c>='a' && c<='f')
				|| (c>='A' && c<='F')
			;
	}
*/
	
	
	//
	// Comments and spaces
	//
	
	private void readComment() throws JsonException, IOException {
		checkStrict();
		/* assert (c == '/') */
		if(bufferPos<bufferLength) {
			c = buffer[bufferPos++];
		} else {
			read();
		}
		if(c=='*') {
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			readMultilineComment();
		} else if(c=='/') {
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
			readSinglelineComment();
		} else {
			throw new ParseException(this, ERROR_UNEXPECTED_CHAR, getPosition()-1, "Bad comment start - should be /* or //");
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
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
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
					throw new ParseException(this, ERROR_UNEXPECTED_EOF, getPosition()-1, "EOF");
			}
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
		}
	}

	
	//
	// Utilities
	//
	
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
			if(bufferPos<bufferLength) {
				c = buffer[bufferPos++];
			} else {
				read();
			}
		}
		return sb.toString();
	}
	



	private void skipSpaces() throws JsonException, IOException {
		for (;;) {
			switch(c) {
				case ' ':
				case '\r':
				case '\t':
				case '\n':
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
					break;
				default:
					return;
			}
		}
	}
	
	private void skipSpacesAndComments() throws JsonException, IOException {
		for (;;) {
			switch(c) {
				case ' ':
				case '\r':
				case '\t':
				case '\n':
					if(bufferPos<bufferLength) {
						c = buffer[bufferPos++];
					} else {
						read();
					}
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

		@Override
		public String toString() {
			return new String(b, 0, p);
		}

		public void clear() {
			p = 0;
		}
	}

}
