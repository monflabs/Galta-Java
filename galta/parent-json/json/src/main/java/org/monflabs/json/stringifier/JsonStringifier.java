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
package org.monflabs.json.stringifier;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JsonObjectAsLinkedMap;
import org.monflabs.json.jsonreference.JsonReference;
import org.monflabs.util.DtoA;
import org.monflabs.util.StringUtil;


/**
 * Simple, but fast, JSON stringifier.
 *
 * @author Philippe Riand
 */
public abstract class JsonStringifier {
	
	@FunctionalInterface
	public static interface Replacer {
		public static final Object IGNORE = new Object();
		
		public Object replace(Object container, String key, Object value);
	}
	public static interface ReplacerRawJSON {
		public String getRawContent();
	}
	
	public static class StringSerializer extends JsonStringifier {
		protected StringBuilder sb;
		public StringSerializer() {
			// The plain StringSerializer keeps the whole text in its buffer, growing it as
			// needed, and builds the String from it at the end (no StringBuilder copies).
			// Subclasses keep the flushBuffer() contract: the text is appended to sb.
			if(getClass()==StringSerializer.class) {
				useGrowableBuffer(256);
			}
		}
	    public String stringify(Object o) throws IOException {
	    	if(isGrowableBuffer()) {
	    		write(o);
	    		return getBufferedText();
	    	}
	    	this.sb = new StringBuilder(512);
	    	write(o);
	    	return sb.toString();
	    }
	    @Override
	    protected void flushBuffer(char[] buffer, int len) throws IOException {
	    	sb.append(buffer, 0, len);
	    }
	}

	/**
	 * Write at most maxCharacters characters, then stop walking the value. The text is cut
	 * before an escape sequence or a surrogate pair that would not fit entirely, so it can
	 * be a few characters shorter than the limit.
	 */
	public static class LimitedStringSerializer extends StringSerializer {
		// Thrown to stop the walk once the limit is reached (no stack trace: preallocated)
		@SuppressWarnings("serial")
		private static final class LimitReached extends RuntimeException {
			LimitReached() {
				super(null, null, false, false);
			}
		}
		private static final LimitReached LIMIT_REACHED = new LimitReached();
		// The text is flushed by blocks of this size, so the walk stops soon after the limit
		private static final int LIMITED_BUFFER_SIZE = 256;

		private int maxCharacters;
		private boolean truncated;
		public LimitedStringSerializer(int maxCharacters) {
			this.maxCharacters = Math.max(0, maxCharacters);
			useBufferSize(LIMITED_BUFFER_SIZE);
		}
		public boolean isTruncated() {
			return truncated;
		}
	    @Override
		public String stringify(Object o) throws IOException {
	    	this.truncated = false;
	    	this.sb = new StringBuilder(Math.min(maxCharacters, 512));
	    	try {
	    		write(o);
	    	} catch(LimitReached e) {
	    		// The text is complete up to the limit
	    	}
	    	return sb.toString();
	    }
	    @Override
	    protected void flushBuffer(char[] buffer, int len) throws IOException {
	    	if(truncated) {
	    		throw LIMIT_REACHED;
	    	}
	    	int l = Math.min(len, maxCharacters-sb.length());
	    	if(l>0) {
	    		sb.append(buffer, 0, l);
	    	}
	    	if(l<len) {
	    		truncated = true;
	    		cutIncompleteSequence(sb);
	    		throw LIMIT_REACHED;
	    	}
	    }
	    // Remove from the end of the text a lone high surrogate (the first half of a pair)
	    // or the beginning of an escape sequence cut by the limit
	    private static void cutIncompleteSequence(StringBuilder sb) {
	    	int len = sb.length();
	    	if(len>0 && Character.isHighSurrogate(sb.charAt(len-1))) {
	    		sb.setLength(--len);
	    	}
	    	// An escape sequence is at most 6 characters (backslash u and 4 hex digits): look
	    	// for a backslash in the last 5 characters. The backslashes come in pairs ("\\")
	    	// when escaped: an odd count of consecutive backslashes ending at index k means
	    	// that the one at k starts an escape sequence
	    	for(int k=len-1; k>=Math.max(0, len-5); k--) {
	    		if(sb.charAt(k)=='\\') {
	    			int run = 0;
	    			for(int j=k; j>=0 && sb.charAt(j)=='\\'; j--) {
	    				run++;
	    			}
	    			if((run&1)==1) {
	    				int escapeLength = k+1<len && sb.charAt(k+1)=='u' ? 6 : 2;
	    				if(k+escapeLength>len) {
	    					sb.setLength(k);
	    				}
	    			}
	    			break;
	    		}
	    	}
	    }
	}

	public static class WriterSerializer extends JsonStringifier {
		Writer writer;
		public WriterSerializer() {
		}
	    public void stringify(Writer w, Object o) throws IOException {
	    	this.writer = w;
	    	write(o);
	    }	
	    @Override
	    protected void flushBuffer(char[] buffer, int len) throws IOException {
	    	writer.write(buffer, 0, len);
	    }
	}
	
    private boolean compact = true;
    private boolean serializeNulls = true;
    private boolean sortProperties;
    private boolean outputReferences;
    private boolean escapeNonAscii;
    // When not null, a circular reference is written as this string instead of throwing
    private String circularReferenceMarker;

    /**
     * Default maximum nesting depth of the objects and arrays written: a deeper value is
     * rejected with a NestingTooDeepException rather than overflowing the Java stack (the
     * writer is recursive). Same as the parser's default.
     */
    public static final int DEFAULT_MAX_DEPTH = 1000;
    /**
     * The highest depth setMaxDepth() accepts.
     */
    public static final int MAX_DEPTH_LIMIT = 2000;
    private int maxDepth = DEFAULT_MAX_DEPTH;

    /**
     * Thrown when the value to write nests objects and arrays deeper than the maximum depth.
     */
    @SuppressWarnings("serial")
    public static class NestingTooDeepException extends JsonException {
    	public NestingTooDeepException(String msg, Object... params) {
    		super(null, msg, params);
    	}
    }
    
    private int initialIndentLevel;
    private int indentLevel;

    private Replacer replacer;
    // When set, drives object-property enumeration order/membership instead
    // of a container's own key order - used by JSON.stringify's array-form
    // replacer (its PropertyList), which fixes both which keys are included
    // and the order they're output in, for every plain object encountered
    // anywhere in the tree. Never applies to arrays (their own elements are
    // always serialized in full, index order, regardless of any replacer).
    private List<String> propertyList;
    // Containers being written (the current path from the root), to detect cycles. A
    // plain array scanned linearly for the first levels, which is much cheaper than an
    // identity hash map for usual depths; the deeper levels also go to a set so a very
    // deep content doesn't make the check quadratic.
    private static final int PROCESSED_SCAN_DEPTH = 16;
    private Object[] processed = new Object[16];
    private int processedCount;
    private Map<Object,Void> processedDeep;
    // With outputReferences: the keys of the current path from the root, to recognize
    // the location of the target of a local ("#/...") reference
    private String[] refPath;
    private int refPathDepth;
    
	// Serialization buffer
	private static final int BUFFER_SIZE = 8192;
	private int bufferLength;
	private char[] buffer;
	// A growable buffer grows up to GROWABLE_MAX, then each full buffer is kept as a segment
	// and a new one is used: the String is built once at the end, from all the segments,
	// without the copies of a growing StringBuilder - flushBuffer() is not called
	private static final int GROWABLE_MAX = 64*1024;
	private boolean growableBuffer;
	private char[][] segments;
	private int[] segmentLengths;
	private int segmentCount;
	// Growable buffers kept for the next serializers once their text is in its String: a
	// stringify() then starts with a buffer already grown, instead of growing (copying and
	// zeroing) a new one from 256 characters. A few slots, so concurrent serializers mostly
	// find one; at most SPARE_SLOTS*GROWABLE_MAX characters are kept.
	private static final int SPARE_SLOTS = 4;
	private static final int SPARE_MIN = 1024;
	private static final AtomicReferenceArray<char[]> SPARE_BUFFERS = new AtomicReferenceArray<>(SPARE_SLOTS);
	private static char[] takeSpareBuffer() {
		int start = (int)Thread.currentThread().threadId();
		for(int i=0; i<SPARE_SLOTS; i++) {
			int k = (start+i) & (SPARE_SLOTS-1);
			char[] b = SPARE_BUFFERS.get(k);
			if(b!=null && SPARE_BUFFERS.compareAndSet(k, b, null)) {
				return b;
			}
		}
		return null;
	}
	private static void releaseSpareBuffer(char[] b) {
		if(b.length<SPARE_MIN) {
			return;
		}
		int start = (int)Thread.currentThread().threadId();
		for(int i=0; i<SPARE_SLOTS; i++) {
			int k = (start+i) & (SPARE_SLOTS-1);
			if(SPARE_BUFFERS.get(k)==null && SPARE_BUFFERS.compareAndSet(k, null, b)) {
				return;
			}
		}
	}
	
	// Indentation of the current levels, see indent()
	private char[] indentChars;
	private int indentCharsLevels;
	private String indentCharsString;

    private String indentString = "  ";


    protected JsonStringifier() {
    }
    
    /**
     * Keep the whole text in memory, in a buffer starting with the given size and growing
     * as needed: flushBuffer() is not called and the text is read with getBufferedText().
     */
    /**
     * The size of the buffer flushed with flushBuffer().
     */
    protected void useBufferSize(int size) {
    	this.buffer = new char[Math.max(16, size)];
    }
    protected void useGrowableBuffer(int initialSize) {
    	this.growableBuffer = true;
    	this.growableInitialSize = Math.max(16, initialSize);
    	this.buffer = null; // taken by write(), a spare one when there is one
    }
    private int growableInitialSize;
    protected boolean isGrowableBuffer() {
    	return growableBuffer;
    }
    /**
     * The text written by the last write(), with a growable buffer.
     */
    protected String getBufferedText() {
    	// The buffers are not used anymore: they are kept for the next serializers
    	char[] last = buffer;
    	buffer = null;
    	if(segmentCount==0) {
    		String text = new String(last, 0, bufferLength);
    		releaseSpareBuffer(last);
    		return text;
    	}
    	int total = bufferLength;
    	for(int i=0; i<segmentCount; i++) {
    		total += segmentLengths[i];
    	}
    	char[] text = new char[total];
    	int pos = 0;
    	for(int i=0; i<segmentCount; i++) {
    		System.arraycopy(segments[i], 0, text, pos, segmentLengths[i]);
    		pos += segmentLengths[i];
    		releaseSpareBuffer(segments[i]);
    		segments[i] = null;
    	}
    	System.arraycopy(last, 0, text, pos, bufferLength);
    	releaseSpareBuffer(last);
    	segmentCount = 0;
    	return new String(text);
    }
    
    public boolean isCompact() {
		return compact;
	}

	public void setCompact(boolean compact) {
		this.compact = compact;
	}

	public boolean isEscapeNonAscii() {
		return escapeNonAscii;
	}

	/**
	 * When true, every character above 126 is written as a backslash-u escape (a surrogate
	 * pair as two escapes), so the output is pure ASCII. By default, only the characters
	 * JSON.stringify() escapes are: the control characters, '"', '\\' and the lone
	 * surrogates.
	 */
	public void setEscapeNonAscii(boolean escapeNonAscii) {
		this.escapeNonAscii = escapeNonAscii;
	}

	public int getMaxDepth() {
		return maxDepth;
	}

	/**
	 * Set the maximum nesting depth of the objects and arrays, capped at MAX_DEPTH_LIMIT.
	 */
	public void setMaxDepth(int maxDepth) {
		this.maxDepth = Math.max(0, Math.min(maxDepth, MAX_DEPTH_LIMIT));
	}

	public String getCircularReferenceMarker() {
		return circularReferenceMarker;
	}

	/**
	 * Write a container that contains itself as this string (like "[circular]") instead
	 * of throwing a JsonException.CircularReference. Meant for display (toString()), the
	 * output doesn't round trip.
	 */
	public void setCircularReferenceMarker(String circularReferenceMarker) {
		this.circularReferenceMarker = circularReferenceMarker;
	}

	public boolean isOutputReferences() {
		return outputReferences;
	}

	public void setOutputReferences(boolean outputReferences) {
		this.outputReferences = outputReferences;
	}

	public boolean isSerializeNulls() {
		return serializeNulls;
	}

	public void setSerializeNulls(boolean serializeNulls) {
		this.serializeNulls = serializeNulls;
	}

	public boolean isSortProperties() {
		return sortProperties;
	}

	public void setSortProperties(boolean sortProperties) {
		this.sortProperties = sortProperties;
	}

	public Replacer getReplacer() {
		return replacer;
	}

	public void setReplacer(Replacer replacer) {
		this.replacer = replacer;
	}

	// Per spec (JSON.stringify steps 9-10), the top-level replacer call's
	// `this`/`container` argument is never the bare root value's own holder
	// (there isn't one) but a synthetic "wrapper" object - ObjectCreate(
	// %ObjectPrototype%) with a single own "" data property set to the root
	// value via CreateDataProperty. Defaults to null (this class's own
	// previous, spec-non-conformant behavior) so every OTHER caller of
	// write()/stringify() (which never exposes this value to any JS-visible
	// code) is unaffected; only a caller that constructs a real wrapper
	// object (see GaltaJS's JSON.stringify) needs to set this. See test262
	// built-ins/JSON/stringify/replacer-function-wrapper.js.
	private Object rootContainer;

	public Object getRootContainer() {
		return rootContainer;
	}

	public void setRootContainer(Object rootContainer) {
		this.rootContainer = rootContainer;
	}

	public List<String> getPropertyList() {
		return propertyList;
	}

	public void setPropertyList(List<String> propertyList) {
		this.propertyList = propertyList;
	}

	public int getInitialIndentLevel() {
		return initialIndentLevel;
	}

	public void setInitialIndentLevel(int initialIndentLevel) {
		this.initialIndentLevel = initialIndentLevel;
	}

	public String getIndentString() {
		return indentString;
	}

	public void setIndentString(String indentString) {
		this.indentString = indentString;
	}

	protected void write(Object o) throws IOException {
    	indentLevel = initialIndentLevel;
    	bufferLength = 0;
    	if(segmentCount>0) {
    		Arrays.fill(segments, 0, segmentCount, null);
    		segmentCount = 0;
    	}
    	if(buffer==null) {
    		if(growableBuffer) {
    			char[] spare = takeSpareBuffer();
    			buffer = spare!=null ? spare : new char[growableInitialSize];
    		} else {
    			buffer = new char[BUFFER_SIZE];
    		}
    	}
    	try {
	        indent();
	    	// First transform the object if necessary
	        if(replacer!=null) {
	        	o = replacer.replace(rootContainer,"",o);
	        	if(o==Replacer.IGNORE) {
	        		return;
	        	}
	        }
	    	try {
	    		outLiteral(o);
	    	} catch(StackOverflowError e) {
	    		// Safety net: the depth limit should prevent it, unless the thread stack is
	    		// very small or a replacer recurses
	    		throw new NestingTooDeepException("Value nested too deeply for the stringifier stack");
	    	}
	    	if(!growableBuffer) {
	    		flushBuffer(buffer, bufferLength);
	    	}
    	} finally {
    		// A failed write (a circular reference...) must not leave containers marked
    		// as being processed: the next write would report a false cycle
    		Arrays.fill(processed, 0, processedCount, null);
    		processedCount = 0;
    		refPathDepth = 0;
    		if(processedDeep!=null && !processedDeep.isEmpty()) {
    			processedDeep.clear(); // kept (already sized) for the next write
    		}
    	}
    }
	
	private void out(char c) throws IOException {
    	if(bufferLength==buffer.length) {
    		bufferFull(1);
    	}
    	buffer[bufferLength++] = c;
    }
	private void out(String s) throws IOException {
		out(s, 0, s.length());
	}
	private void out(String s, int start, int end) throws IOException {
    	int pos = start;
    	while(pos<end) {
        	if(bufferLength==buffer.length) {
        		bufferFull(end-pos);
        	}
    		int max = Math.min(end-pos, buffer.length-bufferLength);
    		s.getChars(pos, pos+max, buffer, bufferLength);
    		pos += max;
    		bufferLength += max;
    	}
    }
	private void out(char[] c, int start, int end) throws IOException {
    	int pos = start;
    	while(pos<end) {
        	if(bufferLength==buffer.length) {
        		bufferFull(end-pos);
        	}
    		int max = Math.min(end-pos, buffer.length-bufferLength);
    		System.arraycopy(c, pos, buffer, bufferLength, max);
    		pos += max;
    		bufferLength += max;
    	}
    }
	// The buffer is full: grow it, or flush it to the output
	private void bufferFull(int needed) throws IOException {
		if(growableBuffer) {
			if(buffer.length<GROWABLE_MAX) {
				buffer = Arrays.copyOf(buffer, Math.min(GROWABLE_MAX, buffer.length*2));
			} else {
				if(segments==null) {
					segments = new char[16][];
					segmentLengths = new int[16];
				} else if(segmentCount==segments.length) {
					segments = Arrays.copyOf(segments, segmentCount*2);
					segmentLengths = Arrays.copyOf(segmentLengths, segmentCount*2);
				}
				segments[segmentCount] = buffer;
				segmentLengths[segmentCount++] = bufferLength;
				char[] spare = takeSpareBuffer();
				if(spare!=null && spare.length<GROWABLE_MAX) {
					releaseSpareBuffer(spare);
					spare = null;
				}
				buffer = spare!=null ? spare : new char[GROWABLE_MAX];
				bufferLength = 0;
			}
		} else {
    		flushBuffer(buffer, bufferLength);
    		bufferLength = 0;
		}
	}
	protected abstract void flushBuffer(char[] buffer, int size) throws IOException;
    
  
    
    private void indent() throws IOException {
        if(!compact && indentLevel>0) {
        	// The indentation of a level is written at once, from a cache holding the
        	// indent string repeated
        	int len = indentString.length();
        	if(indentLevel>indentCharsLevels || indentCharsString!=indentString) {
        		int levels = Math.max(indentLevel, Math.max(8, indentCharsLevels*2));
        		indentChars = new char[levels*len];
        		for(int i=0; i<levels; i++) {
        			indentString.getChars(0, len, indentChars, i*len);
        		}
        		indentCharsLevels = levels;
        		indentCharsString = indentString;
        	}
        	out(indentChars, 0, indentLevel*len);
        }
    }
    
    private void nl() throws IOException {
        if(!compact) {
            out('\n');
        }
    }
    
    
    
	private void outLiteral(Object value) throws IOException, JsonException {
        if(value==null) {
            outNullLiteral();
        } else if(value instanceof String s) {
            outStringLiteral(s);
        } else if(value instanceof Number n) {
            outNumberLiteral(n);
        } else if(value instanceof Boolean b) {
            outBooleanLiteral(b);
        } else if(value instanceof ReplacerRawJSON rv) {
        	// Before JsonObject: a raw JSON value can be an object (JSON.rawJSON() in GaltaJS)
        	out(rv.getRawContent());
        } else if(value instanceof JsonObject o) {
            outObjectLiteral(o);
        } else if(value instanceof JsonArray a) {
            outArrayLiteral(a);
        } else if(value instanceof java.util.Date d) {
        	// ISO-8601 (UTC), not the locale/time zone dependent Date.toString()
        	outStringLiteral(d.toInstant().toString());
        } else {
        	// Other values (java.time values, enums, characters...) are written as their
        	// string representation
        	outStringLiteral(value.toString());
        }
    }
    private void outNullLiteral() throws IOException{
        out("null"); 
    }
    // Characters written as is in a string literal: printable ASCII, but '"' and '\\'. DEL
    // is written as is too, unless escapeNonAscii is set (PLAIN_CHAR_STRICT).
    private static final boolean[] PLAIN_CHAR = new boolean[128];
    private static final boolean[] PLAIN_CHAR_STRICT = new boolean[128];
    // The two characters escapes ('n' for '\n'...), 0 for the characters written as \\uXXXX
    private static final char[] SHORT_ESCAPE = new char[128];
    // How each character is written in a string (without escapeNonAscii): as is (PLAIN), as
    // a two characters escape, as \\uXXXX, or as is in a valid surrogate pair only. A
    // lookup per character is much faster than range tests on text mixing ASCII and non
    // ASCII (2.5x), as the JIT then has a single branch in the scan loop.
    private static final byte PLAIN = 0;
    private static final byte ESCAPE_SHORT = 1;
    private static final byte ESCAPE_UNICODE = 2;
    private static final byte SURROGATE = 3;
    private static final byte[] CHAR_CLASS = new byte[65536];
    static {
    	for(int c=32; c<127; c++) {
    		PLAIN_CHAR[c] = PLAIN_CHAR_STRICT[c] = c!='"' && c!='\\';
    	}
    	PLAIN_CHAR[127] = true;
    	SHORT_ESCAPE['"'] = '"';
    	SHORT_ESCAPE['\\'] = '\\';
    	SHORT_ESCAPE['\b'] = 'b';
    	SHORT_ESCAPE['\f'] = 'f';
    	SHORT_ESCAPE['\n'] = 'n';
    	SHORT_ESCAPE['\r'] = 'r';
    	SHORT_ESCAPE['\t'] = 't';
    	for(int c=0; c<CHAR_CLASS.length; c++) {
    		if(c<128) {
    			CHAR_CLASS[c] = PLAIN_CHAR[c] ? PLAIN : SHORT_ESCAPE[c]!=0 ? ESCAPE_SHORT : ESCAPE_UNICODE;
    		} else {
    			CHAR_CLASS[c] = Character.isSurrogate((char)c) ? SURROGATE : PLAIN;
    		}
    	}
    }
    private void outStringLiteral(String s) throws IOException {
        int len = s.length();
        // Fast path, when the buffer has room for the whole literal: the string is copied at
        // once, then checked in place. Most strings need no escape: they are then written.
        final char[] b = buffer;
        final int start = bufferLength;
        if(len+2 <= b.length-start) {
        	b[start] = '\"';
        	s.getChars(0, len, b, start+1);
        	final int end = start+1+len;
        	int i = start+1;
        	if(!escapeNonAscii) {
        		for(;;) {
        			// The characters written as is (U+2028 and U+2029 included, like
        			// JSON.stringify() does), up to a character to escape or a surrogate
        			for(; i<end; i++) {
        				if(CHAR_CLASS[b[i]]!=PLAIN) {
        					break;
        				}
        			}
        			// A valid surrogate pair is written as is too
        			if(i+1<end && Character.isHighSurrogate(b[i]) && Character.isLowSurrogate(b[i+1])) {
        				i += 2;
        				continue;
        			}
        			break;
        		}
        	} else {
        		for(; i<end; i++) {
        			char c = b[i];
        			if(c>=128 || !PLAIN_CHAR_STRICT[c]) {
        				break;
        			}
        		}
        	}
        	if(i==end) {
        		b[end] = '\"';
        		bufferLength = end+1;
        		return;
        	}
        	// A character to escape: the copied text before it is kept, the general path
        	// writes the rest
        	bufferLength = i;
        	outStringRest(s, i-(start+1));
        	return;
        }
        out('\"');
        outStringRest(s, 0);
    }
    // Characters of a string read at once by outStringRest()
    private static final int STRING_CHUNK = 512;
    private char[] stringChunk;
    // Writes a string from an index, escaped, and the closing quote. The string is read by
    // chunks, each written directly in the buffer once it has room for the longest result
    // (all the characters as \\uXXXX): no check per character.
    private void outStringRest(String s, int from) throws IOException {
        final int len = s.length();
        final boolean escapeNonAscii = this.escapeNonAscii;
        char[] src = stringChunk;
        if(src==null) {
        	src = stringChunk = new char[STRING_CHUNK];
        }
        while(from<len) {
        	int room = buffer.length-bufferLength;
        	if(room<12) {
        		bufferFull(12);
        		room = buffer.length-bufferLength;
        	}
        	int n = Math.min(Math.min(len-from, room/6), STRING_CHUNK);
        	if(n<2 && len-from>=2) {
        		// A buffer too small for the worst case (a tiny custom buffer)
        		outStringRestSlow(s, from);
        		return;
        	}
        	// A surrogate pair is not split between two chunks
        	if(n>1 && from+n<len && Character.isHighSurrogate(s.charAt(from+n-1))) {
        		n--;
        	}
        	s.getChars(from, from+n, src, 0);
        	final char[] b = buffer;
        	int p = bufferLength;
        	if(!escapeNonAscii) {
        		for(int i=0; i<n; i++) {
        			char c = src[i];
        			byte k = CHAR_CLASS[c];
        			if(k==PLAIN) {
        				b[p++] = c;
        			} else if(k==ESCAPE_SHORT) {
        				b[p++] = '\\';
        				b[p++] = SHORT_ESCAPE[c];
        			} else if(k==SURROGATE && c<=Character.MAX_HIGH_SURROGATE && i+1<n && Character.isLowSurrogate(src[i+1])) {
        				// Per spec's QuoteJSONString: a complete, valid surrogate pair (a
        				// well-formed astral character) is written as its raw UTF-16 code
        				// units, only a lone surrogate half is escaped
        				b[p++] = c;
        				b[p++] = src[++i];
        			} else {
        				p = unicodeEscape(c, b, p);
        			}
        		}
        		bufferLength = p;
        		from += n;
        		continue;
        	}
        	// escapeNonAscii: all the characters but printable ASCII are escaped
        	for(int i=0; i<n; i++) {
        		char c = src[i];
        		if(c<128 && PLAIN_CHAR_STRICT[c]) {
        			b[p++] = c;
        		} else if(c<128 && SHORT_ESCAPE[c]!=0) {
        			b[p++] = '\\';
        			b[p++] = SHORT_ESCAPE[c];
        		} else {
        			p = unicodeEscape(c, b, p);
        		}
        	}
        	bufferLength = p;
        	from += n;
        }
        out('\"');
    }
    // The same, a character at a time, for a buffer with less than 12 characters
    private void outStringRestSlow(String s, int from) throws IOException {
    	char[] e = new char[12];
    	int len = s.length();
    	for(int i=from; i<len; i++) {
    		char c = s.charAt(i);
    		int p;
    		if(c<128) {
    			if(escapeNonAscii ? PLAIN_CHAR_STRICT[c] : PLAIN_CHAR[c]) {
    				out(c);
    				continue;
    			}
    			if(SHORT_ESCAPE[c]!=0) {
    				e[0] = '\\';
    				e[1] = SHORT_ESCAPE[c];
    				p = 2;
    			} else {
    				p = unicodeEscape(c, e, 0);
    			}
    		} else if(!escapeNonAscii && (c<Character.MIN_SURROGATE || c>Character.MAX_SURROGATE)) {
    			out(c);
    			continue;
    		} else if(!escapeNonAscii && Character.isHighSurrogate(c) && i+1<len && Character.isLowSurrogate(s.charAt(i+1))) {
    			e[0] = c;
    			e[1] = s.charAt(++i);
    			p = 2;
    		} else {
    			p = unicodeEscape(c, e, 0);
    		}
    		out(e, 0, p);
    	}
    	out('\"');
    }
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static int unicodeEscape(char c, char[] b, int p) {
    	b[p] = '\\';
    	b[p+1] = 'u';
    	b[p+2] = HEX_DIGITS[(c>>12)&0xF];
    	b[p+3] = HEX_DIGITS[(c>>8)&0xF];
    	b[p+4] = HEX_DIGITS[(c>>4)&0xF];
    	b[p+5] = HEX_DIGITS[c&0xF];
    	return p+6;
    }
    private void outNumberLiteral(Number n) throws IOException {
    	// Integer values are written directly, without an intermediate String. The other
    	// numbers use JsonUtil.toString() (JavaScript Number::toString for the doubles).
    	// NaN and the infinities are not JSON numbers: written as null, like
    	// JSON.stringify() does (writing NaN produced a text the strict parser rejects).
    	if(n instanceof Integer i) {
    		outLong(i.intValue());
    		return;
    	}
    	if(n instanceof Double d) {
    		double v = d.doubleValue();
    		if(!Double.isFinite(v)) {
   	    		outNullLiteral();
    			return;
    		}
    		// Same as DtoA.toStandard() for an integral value: 0 (-0 too), or the digits of
    		// the long when it holds the value exactly
    		if(v==0.0) {
    			out('0');
    			return;
    		}
    		if(Math.abs(v)<0x1p53) {
    			long l = (long)v;
    			if((double)l==v) {
    				outLong(l);
    				return;
    			}
    		}
    		// Written into the buffer, without a String
    		if(buffer.length-bufferLength < DtoA.MAX_STANDARD_LENGTH) {
    			bufferFull(DtoA.MAX_STANDARD_LENGTH);
    		}
    		if(buffer.length-bufferLength >= DtoA.MAX_STANDARD_LENGTH) {
    			bufferLength = DtoA.toStandard(v, buffer, bufferLength);
    			return;
    		}
    	} else if(n instanceof Long l) {
    		outLong(l.longValue());
    		return;
    	} else if(n instanceof Float f) {
    		if(!Float.isFinite(f)) {
   	    		outNullLiteral();
    			return;
    		}
    	}
    	if(n instanceof java.math.BigInteger || n instanceof java.math.BigDecimal
    			|| n instanceof Short || n instanceof Byte) {
    		out(JsonUtil.toString(n));
    		return;
    	}
    	// Another Number class: its toString() may not be a JSON number ("NaN", "1,5"...)
    	String text = JsonUtil.toString(n);
    	if(!isJsonNumber(text)) {
    		double v = n.doubleValue();
    		if(Double.isNaN(v) || Double.isInfinite(v)) {
    			outNullLiteral();
    			return;
    		}
    		text = JsonUtil.toString(Double.valueOf(v));
    	}
		out(text);
    }
    /**
     * Check a text against the JSON number grammar:
     * -?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?
     */
    static boolean isJsonNumber(String s) {
    	int len = s.length();
    	int i = 0;
    	if(i<len && s.charAt(i)=='-') {
    		i++;
    	}
    	if(i>=len) {
    		return false;
    	}
    	if(s.charAt(i)=='0') {
    		i++;
    	} else if(s.charAt(i)>='1' && s.charAt(i)<='9') {
    		while(i<len && s.charAt(i)>='0' && s.charAt(i)<='9') {
    			i++;
    		}
    	} else {
    		return false;
    	}
    	if(i<len && s.charAt(i)=='.') {
    		i++;
    		int start = i;
    		while(i<len && s.charAt(i)>='0' && s.charAt(i)<='9') {
    			i++;
    		}
    		if(i==start) {
    			return false;
    		}
    	}
    	if(i<len && (s.charAt(i)=='e' || s.charAt(i)=='E')) {
    		i++;
    		if(i<len && (s.charAt(i)=='+' || s.charAt(i)=='-')) {
    			i++;
    		}
    		int start = i;
    		while(i<len && s.charAt(i)>='0' && s.charAt(i)<='9') {
    			i++;
    		}
    		if(i==start) {
    			return false;
    		}
    	}
    	return i==len;
    }
    private static final char[] DIGIT_PAIRS = new char[200];
    static {
    	for(int i=0; i<100; i++) {
    		DIGIT_PAIRS[i*2] = (char)('0'+i/10);
    		DIGIT_PAIRS[i*2+1] = (char)('0'+i%10);
    	}
    }
    // Written directly in the buffer, from the last digit
    private void outLong(long v) throws IOException {
    	if(v>=0 && v<10) {
    		out((char)('0'+v));
    		return;
    	}
    	if(v==Long.MIN_VALUE) {
    		out("-9223372036854775808");
    		return;
    	}
    	boolean negative = v<0;
    	if(negative) {
    		v = -v;
    	}
    	// The number of digits, by comparisons (a division per digit is much slower)
    	int size = 19;
    	long limit = 10;
    	for(int d=1; d<19; d++) {
    		if(v<limit) {
    			size = d;
    			break;
    		}
    		limit *= 10;
    	}
    	if(negative) {
    		size++;
    	}
    	if(buffer.length-bufferLength<size) {
    		bufferFull(size);
    		if(buffer.length-bufferLength<size) {
    			out(Long.toString(negative ? -v : v));
    			return;
    		}
    	}
    	final char[] d = buffer;
    	int start = bufferLength;
    	int pos = start+size;
    	bufferLength = pos;
    	// The low 8 digits at a time, with a single long division, then int arithmetic
    	while(v>Integer.MAX_VALUE) {
    		long q = v/100_000_000;
    		int low = (int)(v-q*100_000_000);
    		for(int k=0; k<4; k++) {
    			int r = low%100;
    			low /= 100;
    			d[--pos] = DIGIT_PAIRS[r*2+1];
    			d[--pos] = DIGIT_PAIRS[r*2];
    		}
    		v = q;
    	}
    	int i = (int)v;
    	while(i>=100) {
    		int r = i%100;
    		i /= 100;
    		d[--pos] = DIGIT_PAIRS[r*2+1];
    		d[--pos] = DIGIT_PAIRS[r*2];
    	}
    	if(i>=10) {
    		d[--pos] = DIGIT_PAIRS[i*2+1];
    		d[--pos] = DIGIT_PAIRS[i*2];
    	} else {
    		d[--pos] = (char)('0'+i);
    	}
    	if(negative) {
    		d[--pos] = '-';
    	}
    }
    private void outBooleanLiteral(boolean b) throws IOException {
        out(b?"true":"false"); 
    }    
    private void outObjectLiteral(JsonObject container) throws IOException, JsonException {
    	if(processedCount>=maxDepth) {
    		throw new NestingTooDeepException("Objects and arrays nested deeper than {0} levels",maxDepth);
    	}
    	// Checked before the cycles: a reference to a parent (a recursive structure) is
    	// written as a reference, not a cycle, and the same target may be referenced twice
    	if(outputReferences) {
    		String ref = container.getReference();
    		if(StringUtil.isNotEmpty(ref) && !isReferenceLocation(ref)) {
    	    	out('{');
    	    	outProperty(JsonReference.REF_PROP,ref);
                out('}');
                return;
    		}
    	}
    	if(isProcessed(container)) {
    		if(circularReferenceMarker!=null) {
    			outStringLiteral(circularReferenceMarker);
    			return;
    		}
    		// Don't call container.toString() here: a genuinely circular container's
    		// own toString() recurses into itself just as infinitely, causing a
    		// StackOverflowError while merely trying to describe the error.
    		throw new JsonException.CircularReference(null,"Circular reference detected in object of type {0}",container.getClass().getName());
    	}
    	pushProcessed(container);

    	out('{');

        if(container.isEmpty()) {
            out('}');
            popProcessed(); // the same empty object may legitimately appear twice
    		return;
    	}
        
        boolean coma = false;

        if(propertyList!=null) {
        	// Fixed, ordered, de-duped key list (built once up front by the
        	// caller) drives both membership and order here - not the
        	// container's own key order, and not affected by sortProperties.
        	for(String prop : propertyList) {
        		if(!container.containsKey(prop)) {
        			continue;
        		}
	            Object value = container.get(prop);
	            if(replacer!=null) {
	            	value = replacer.replace(container,prop,value);
	            	if(value==Replacer.IGNORE) {
	            		continue;
	            	}
	            }
	        	if(value!=null || serializeNulls) {
		        	if(coma) {
		                out(',');
		        	} else {
		        		coma = true;
		        	}
		            outProperty(prop, value);
	        	}
        	}
        } else if(sortProperties && container.size()>=2) {
			String[] keys = container.keySet().toArray(new String[container.size()]);
			Arrays.sort(keys);
			for (String prop : keys) {
	            Object value = container.get(prop);
	            if(replacer!=null) {
	            	value = replacer.replace(container,prop,value);
	            	if(value==Replacer.IGNORE) {
	            		continue;
	            	}
	            }
	        	if(value!=null || serializeNulls) {
		        	if(coma) {
		                out(',');
		        	} else {
		        		coma = true;
		        	}
		            outProperty(prop, value);
	        	}
			}        	
        } else {
        	// Snapshot the key list BEFORE enumerating (mirrors the
        	// sortProperties branch above) rather than iterating
        	// container.entrySet() live: per spec (EnumerableOwnProperties /
        	// SerializeJSONObject), the property list is fixed once, up
        	// front - a getter's own side effect of adding a NEW property
        	// to the same container mid-enumeration must not be observable
        	// in this same stringify pass. Each key's VALUE is still fetched
        	// one at a time inside the loop (via container.get(prop), not
        	// pre-fetched here), so a getter CAN still affect a
        	// not-yet-processed EXISTING key's value - only the key list
        	// itself is frozen.
        	//
        	// Snapshotted from entrySet(), NOT keySet(): the JsonObject
        	// interface only exposes the generic (no-arg) Map forms of each,
        	// and GaltaJS's own CustomLinkedMap gives those two DIFFERENT
        	// enumerable-only defaults (entrySet() -> enumerable-only;
        	// keySet() -> includes non-enumerable) - keySet() here would
        	// silently leak non-enumerable own properties into the output.
        	//
        	// A plain JsonObjectAsLinkedMap without a replacer has no getter and nothing can
        	// change it while it is written: its entries are snapshot and read directly, no
        	// need to look each key up again.
        	if(replacer==null && container.getClass()==JsonObjectAsLinkedMap.class) {
        		for(Map.Entry<String,Object> e: container.entrySet()) {
        			Object value = e.getValue();
		        	if(value!=null || serializeNulls) {
			        	if(coma) {
			                out(',');
			        	} else {
			        		coma = true;
			        	}
			            outProperty(e.getKey(), value);
		        	}
        		}
        	} else {
			String[] keys = new String[container.size()];
			int count = 0;
			for(Map.Entry<?,?> e : container.entrySet()) {
				if(count==keys.length) {
					keys = Arrays.copyOf(keys, count*2+1);
				}
				keys[count++] = (String)e.getKey();
			}
			for (int k=0; k<count; k++) {
				String prop = keys[k];
	            Object value = container.get(prop);
	            if(replacer!=null) {
	            	value = replacer.replace(container,prop,value);
	            	if(value==Replacer.IGNORE) {
	            		continue;
	            	}
	            }
	        	if(value!=null || serializeNulls) {
		        	if(coma) {
		                out(',');
		        	} else {
		        		coma = true;
		        	}
		            outProperty(prop, value);
	        	}
	        }
        	}
        }
        
        // When every member was skipped (null values, replacer), the object is just {}
        if(coma) {
	        nl();
	        indent();
        }
        out('}');
        
    	popProcessed();
    }
    
    /**
     * Whether the container being written is at the location a local reference points to:
     * it is then the definition, written as is, and not a reference to itself.
     */
    private boolean isReferenceLocation(String ref) {
    	if(ref.charAt(0)!='#') {
    		return false;
    	}
    	String ptr = ref.substring(1);
    	if(ptr.indexOf('%')>=0) {
    		// The fragment is percent-encoded, see JsonReference
    		ptr = java.net.URLDecoder.decode(ptr.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
    	}
    	StringBuilder b = new StringBuilder(ptr.length());
    	for(int i=0; i<refPathDepth; i++) {
    		b.append('/').append(refPath[i].replace("~", "~0").replace("/", "~1"));
    	}
    	return b.toString().equals(ptr);
    }
    private void pushRefPath(String key) {
    	if(refPath==null) {
    		refPath = new String[16];
    	} else if(refPathDepth==refPath.length) {
    		refPath = Arrays.copyOf(refPath, refPathDepth*2);
    	}
    	refPath[refPathDepth++] = key;
    }
    private boolean isProcessed(Object container) {
    	int scan = Math.min(processedCount, PROCESSED_SCAN_DEPTH);
    	for(int i=0; i<scan; i++) {
    		if(processed[i]==container) {
    			return true;
    		}
    	}
    	return processedDeep!=null && processedDeep.containsKey(container);
    }
    private void pushProcessed(Object container) {
    	if(processedCount==processed.length) {
    		processed = Arrays.copyOf(processed, processedCount*2);
    	}
    	if(processedCount>=PROCESSED_SCAN_DEPTH) {
    		if(processedDeep==null) {
    			processedDeep = new IdentityHashMap<>();
    		}
    		processedDeep.put(container, null);
    	}
    	processed[processedCount++] = container;
    }
    private void popProcessed() {
    	Object container = processed[--processedCount];
    	processed[processedCount] = null;
    	if(processedCount>=PROCESSED_SCAN_DEPTH) {
    		processedDeep.remove(container);
    	}
    }
    
    private void outProperty(String prop, Object value) throws IOException, JsonException {
        nl();
        indentLevel++;
        indent();
        if(prop==null) {
        	out("null");
        } else {
        	outStringLiteral(prop);
        }
        out(':');
        if(!compact) {
        	out(' ');
        }
        if(outputReferences) {
        	pushRefPath(prop);
        	outLiteral(value);
        	refPathDepth--;
        } else {
        	outLiteral(value);
        }
        indentLevel--;
    }
    
    private void outArrayLiteral(JsonArray container) throws IOException, JsonException {
    	if(processedCount>=maxDepth) {
    		throw new NestingTooDeepException("Objects and arrays nested deeper than {0} levels",maxDepth);
    	}
    	// Checked before the cycles: a reference to a parent (a recursive structure) is
    	// written as a reference, not a cycle, and the same target may be referenced twice
    	if(outputReferences) {
    		String ref = container.getReference();
    		if(StringUtil.isNotEmpty(ref) && !isReferenceLocation(ref)) {
    	    	out('{');
    	    	outProperty(JsonReference.REF_PROP,ref);
                out('}');
                return;
    		}
    	}
    	if(isProcessed(container)) {
    		if(circularReferenceMarker!=null) {
    			outStringLiteral(circularReferenceMarker);
    			return;
    		}
    		// See outObjectLiteral() above for why toString() isn't used here.
    		throw new JsonException.CircularReference(null,"Circular reference detected in array of type {0}",container.getClass().getName());
    	}
    	pushProcessed(container);

    	out('[');
    	
        if(container.isEmpty()) {
            out(']');
            popProcessed(); // the same empty array may legitimately appear twice
    		return;
    	}
    	
        boolean coma = false;
        int count = container.size();
        for(int i=0; i<count; i++) {
            Object propValue = container.get(i);
            if(replacer!=null) {
            	propValue = replacer.replace(container,Integer.toString(i),propValue);
            	if(propValue==Replacer.IGNORE) {
            		continue;
            	}
            }
            indentLevel++;
            if(coma) {
                out(',');
            } else {
                coma = true;
            }
            nl();
            indent();
            if(outputReferences) {
            	pushRefPath(Integer.toString(i));
            	outLiteral(propValue);
            	refPathDepth--;
            } else {
            	outLiteral(propValue);
            }
            indentLevel--;
        }
        
        // When every item was skipped by the replacer, the array is just []
        if(coma) {
	        nl();
	        indent();
        }
        out(']');

    	popProcessed();
    }    

}