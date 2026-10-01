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
package org.monflabs.util;

import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple String matcher.
 * 
 * @author priand
 */
public class StringMatcher {

	//private static Pattern INTEGER_PATTERN = Pattern.compile("-?(?:0|(?:[1-9][0-9]*))");
	// Integer part (0 or non-zero-leading), optional fraction, or a bare fraction; optional signed exponent
	private static final Pattern NUMBER_PATTERN = Pattern.compile("-?(?:(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?|\\.[0-9]+)(?:[Ee][+-]?[0-9]+)?");

	protected String seq;
	protected int length;
	protected int ptr;
	
	public StringMatcher() {
		this.seq = "";
		this.length = 0;
	}
	public StringMatcher(String seq) {
		this.seq = seq;
		this.length = seq.length();
	}
	public StringMatcher(String seq, int start) {
		this.seq = seq;
		this.length = seq.length();
		this.ptr = start;
	}
	
	public StringMatcher(String seq, int start, int end) {
		this.seq = seq;
		this.length = Math.min(end, seq.length());
		this.ptr = start;
	}
	
	public StringMatcher set(String seq) {
		this.seq = seq;
		this.length = seq.length();
		this.ptr = 0;
		return this;
	}
	
	public StringMatcher set(String seq, int start) {
		this.seq = seq;
		this.length = seq.length();
		this.ptr = start;
		return this;
	}
	
	public StringMatcher set(String seq, int start, int end) {
		this.seq = seq;
		this.length = Math.min(end, seq.length());
		this.ptr = start;
		return this;
	}
	
	public String getString() {
		return seq;
	}
	
	public int getPtr() {
		return ptr;
	}
	
	public int getLength() {
		return length;
	}
	
	public int getRemaining() {
		return length-ptr;
	}
	
	public boolean isEmpty() {
		return ptr==length;
	}

	public void skip() {
		if(ptr<length) {
			ptr++;
		}
	}

	public void skip(int count) {
		if(count>0) {
			ptr = Math.min(ptr+count, length);
		}
	}

	public boolean skipIf(char c) {
		if(startsWith(c)) {
			skip(1);
			return true;
		}
		return false;
	}

	public void skipSpaces() {
		while(ptr<length) {
			if(!isSpace(seq.charAt(ptr))) {
				break;
			}
			ptr++;
		}
	}
	protected boolean isSpace(char ch) {
		return ch==' ' || ch=='\t' || ch=='\n' || ch=='\r';
	}

	public String upto(char c) {
		int start = ptr;
		while(ptr<length) {
			if(seq.charAt(ptr)==c) {
				break;
			}
			ptr++;
		}
		return seq.substring(start,ptr);
	}

	public String upto(IntPredicate stopChar) {
		int start = ptr;
		while(ptr<length) {
			if(stopChar.test(seq.charAt(ptr))) {
				break;
			}
			ptr++;
		}
		return ptr>start ? seq.substring(start,ptr) : "";
	}

	public boolean startsWith(char c) {
		if(ptr<length && seq.charAt(ptr)==c) {
			return true;
		}
		return false;
	}

	public boolean startsWith(String s) {
		if(ptr<length && ptr+s.length()<=length && seq.startsWith(s,ptr)) {
			return true;
		}
		return false;
	}

	public boolean startsWithSpace() {
		if(ptr<length && isSpace(seq.charAt(ptr))) {
			return true;
		}
		return false;
	}

	public boolean startsWith(IntPredicate charFilter) {
		if(ptr<length && charFilter.test(seq.charAt(ptr))) {
			return true;
		}
		return false;
	}
	
	public boolean startsWithInteger() {
		if(ptr<length) {
			char c = seq.charAt(ptr);
			if(c>='0' && c<='9') {
				return true;
			}
			if(c=='-' && ptr<length-1) {
				c = seq.charAt(ptr+1);
				if(c>='0' && c<='9') {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean startsWithNumber() {
		return startsWith(NUMBER_PATTERN);
	}

	public boolean startsWith(Pattern regExp) {
		if(ptr<length) {
			Matcher m = regExp.matcher(seq);
			m.region(ptr, length);
			if(m.lookingAt()) {
				return true;
			}
			return false;
		}
		return false;
	}

	public boolean matchAndSkipSpaces(char c) {
		if(match(c)) {
			skipSpaces();
			return true;
		}
		return false;
	}
	public boolean match(char c) {
		if(ptr<length && seq.charAt(ptr)==c) {
			ptr++;
			return true;
		}
		return false;
	}

	public boolean matchAndSkipSpaces(String s) {
		if(match(s)) {
			skipSpaces();
			return true;
		}
		return false;
	}
	public boolean match(String s) {
		if(ptr<length && ptr+s.length()<=length && seq.startsWith(s,ptr)) {
			ptr+=s.length();
			return true;
		}
		return false;
	}

	public boolean matchAndSkipSpaces(IntPredicate charFilter) {
		if(match(charFilter)) {
			skipSpaces();
			return true;
		}
		return false;
	}
	public boolean match(IntPredicate charFilter) {
		if(ptr<length && charFilter.test(seq.charAt(ptr))) {
			ptr++;
			return true;
		}
		return false;
	}

	public boolean matchAndSkipSpaces(Pattern regExp) {
		if(match(regExp)) {
			skipSpaces();
			return true;
		}
		return false;
	}
	public boolean match(Pattern regExp) {
		Matcher m = regExp.matcher(seq);
		m.region(ptr, length);
		if(m.lookingAt()) {
			ptr += m.end()-m.start();
			return true;
		}
		return false;
	}

	/**
	 * Return the next character but don't consume it.
	 * @return
	 */
	public int lookupChar() {
		if(ptr<length) {
			return seq.charAt(ptr);
		}
		return -1;
	}

	/**
	 * Return the next character and consume it.
	 * @return
	 */
	public int readChar() {
		if(ptr<length) {
			return seq.charAt(ptr++);
		}
		return -1;
	}
	
	public String readRegExp(Pattern regExp) {
		Matcher m = regExp.matcher(seq);
		m.region(ptr, length);
		if(m.lookingAt()) {
			ptr += m.end()-m.start();
			String s = m.group();
			return s;
		}
		return null;
	}
	
	public String readQuotedString() {
		int start = ptr; 
		char sep = (char)readChar();
		String str = readEscapedString(sep);
		if(!match(sep)) {
			throw createException(null, start, "Missing closing quote {0} at position {1}", sep, getPtr());
		}
		return str;
	}

	public String readEscapedString(char sep) {
		int start = ptr; 
		StringBuilder sb = null;
		for (;;) {
			if(ptr==length) {
				throw createException(null, start, "Unterminated string");
			}
			char c = seq.charAt(ptr);
			switch (c) {
				case '"', '\'' -> {
					if (sep == c) {
						if(sb!=null) {
							return sb.toString();
						}
						return seq.substring(start,ptr);
					}
					if(sb!=null) {
						sb.append(c);
					}
					ptr++;
				}
				case '\\' -> {
					if(sb==null) {
						sb = new StringBuilder(seq.substring(start,ptr));
					}
					ptr++;
					if(ptr==length) {
						throw createException(null, start, "Unterminated string");
					}
					c = seq.charAt(ptr);
					switch (c) {
						case 't' ->		sb.append('\t');
						case 'n' ->		sb.append('\n');
						case 'r' -> 	sb.append('\r');
						case 'f' -> 	sb.append('\f');
						case 'b' -> 	sb.append('\b');
						case '\\' ->	sb.append('\\');
						case '/' -> 	sb.append('/');
						case '\'' -> 	sb.append('\'');
						case '"' -> 	sb.append('"');
						case 'u' -> 	sb.append(readUnicode(4));
						case 'x' ->		sb.append(readUnicode(2));
						default -> 
							throw createException(null, start, "Invalid escape sequence");
					}
					ptr++;
				}
				case '\b', '\t', '\f', '\r', '\n' -> {
					throw createException(null, start, "Invalid character {0} in string", Integer.toString(c));
				}
				default -> {
					if(sb!=null) {
						sb.append(c);
					}
					ptr++;
				}
			}
		}
	}
	private char readUnicode(int totalChars) {
		int start = ptr;
		int value = 0;
		for (int i = 0; i < totalChars; i++) {
			value = value * 16;
			if(ptr+1>=length) {
				throw createException(null, start, "Unterminated unicode escape sequence");
			}
			char c = seq.charAt(++ptr);
			if (c <= '9' && c >= '0')
				value += c - '0';
			else if (c <= 'F' && c >= 'A')
				value += (c - 'A') + 10;
			else if (c >= 'a' && c <= 'f')
				value += (c - 'a') + 10;
			else
				throw createException(null, start, "Invalid unicode escape sequence");
		}
		return (char) value;
	}
	
	public String readIdentifier() {
		int start = ptr;
		if(ptr==length) {
			throw createException(null, start, "Empty identifier");
		}
		char c = seq.charAt(ptr++);
		if(!isIdentifierStart(c)) {
			throw createException(null, start, "Invalid identifier start character {0}", c);
		}
		while(ptr<length) {
			c = seq.charAt(ptr);
			if(!isIdentifierPart(c)) {
				break;  
			}
			ptr++;
		}
		return seq.substring(start, ptr);  
	}
	protected boolean isIdentifierStart(char ch) {
		//return Character.isLetter(ch) || ch=='$' || ch=='_';
		return Character.isJavaIdentifierStart(ch);
	}
	protected boolean isIdentifierPart(char ch) {
		//return Character.isLetterOrDigit(ch) || ch=='$' || ch=='_';
		return Character.isJavaIdentifierPart(ch);
	}
	
	// Integer is used for indexed, bound a number that is not in range
	// Using regexp proved to be slow - let's avoif them when we can!
//	public int readIntegerregExp() {
//		String s = readRegExp(INTEGER_PATTERN);
//		if(s!=null) {
//			try {
//				long l = Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,Long.parseLong(s)));
//				return (int)l;
//			} catch(Exception e) {
//				throw createException(e, "Invalid integer {0}", s);
//			}
//		}
//		throw createException(null, "Invalid integer at position {0}", ptr);
//	}
	public int readInteger() {
		int start = ptr;
		if(ptr==length) {
			throw createException(null, start, "Invalid integer");
		}

		char firstDigit = seq.charAt(ptr++);
		boolean neg = false;
		if(firstDigit=='-') {
			neg = true;
			if(ptr==length) {
				throw createException(null, start, "Invalid integer");
			}
			firstDigit = seq.charAt(ptr++);
		}
		if(firstDigit<'0' || firstDigit>'9') {
			throw createException(null, start, "Invalid integer");
		}
		long value = firstDigit-'0';
		while(ptr<length) {
			char nc = seq.charAt(ptr);
			if(nc<'0' || nc>'9') {
				break;
			}
			if(firstDigit=='0') { // no '001'
				throw createException(null, start, "Invalid integer at position {0}", start);
			}
			value = value*10 + (nc-'0');
			// The magnitude of Integer.MIN_VALUE is one more than Integer.MAX_VALUE
			if(value>(neg ? -(long)Integer.MIN_VALUE : Integer.MAX_VALUE)) {
				throw createException(null, start, "Integer overflow at position {0}", start);
			}
			ptr++;
		}
		return (int)(neg ? -value : value);  
	}
	
	public Number readNumber() {
		int start = ptr;
		String s = readRegExp(NUMBER_PATTERN);
		if(s==null) {
			throw createException(null, start, "Invalid number at position {0}", start);
		}
		// "0" is a number, but a leading zero must not be followed by more digits ("010")
		if(ptr<length) {
			char nc = seq.charAt(ptr);
			if(nc>='0' && nc<='9') {
				throw createException(null, start, "Invalid number {0}", s+nc);
			}
		}
		try {
			double d = Double.parseDouble(s);
			int i = (int)d;
			// -0 == 0 as a double comparison: keep negative zero as a double
			if( (double)i == d && !(d==0 && 1/d<0) ) {
				return i;
			}
			return d;
		} catch(Exception e) {
			throw createException(null, start, "Invalid number {0}", s);
		}
	}

	protected BaseException createException(Throwable cause, int position, String message, Object...params) {
		StringBuilder b = new StringBuilder();
		b.append(StringFormat.format(message, params));
		b.append('\n');
		// The line holding the position, with the marker starting right under it (the
		// whole input used to be printed, with the marker 2 columns too far left and
		// counted from the start of the input rather than of the line)
		int pos = Math.max(0, Math.min(position, seq.length()));
		int lineStart = pos;
		while(lineStart>0 && seq.charAt(lineStart-1)!='\n' && seq.charAt(lineStart-1)!='\r') {
			lineStart--;
		}
		int lineEnd = pos;
		while(lineEnd<seq.length() && seq.charAt(lineEnd)!='\n' && seq.charAt(lineEnd)!='\r') {
			lineEnd++;
		}
		b.append(seq, lineStart, lineEnd);
		b.append('\n');
        for(int i=lineStart; i<pos; i++) {
        	// A tab is kept so that the marker stays aligned
        	b.append(seq.charAt(i)=='\t' ? '\t' : ' ');
        }
        b.append("^^^");
		return _createException(cause, b.toString());
	}
	protected BaseException _createException(Throwable cause, String message) {
		return new UtilException(cause, message);
	}
}
