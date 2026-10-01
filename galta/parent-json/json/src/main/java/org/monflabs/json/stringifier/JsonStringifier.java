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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JsonObjectAsLinkedMap;
import org.monflabs.json.jsonreference.JsonReference;
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

	public static class LimitedStringSerializer extends StringSerializer {
		private int maxCharacters;
		private boolean truncated;
		public LimitedStringSerializer(int maxCharacters) {
			this.maxCharacters = maxCharacters;
		}
		public boolean isTruncated() {
			return truncated;
		}
	    @Override
		public String stringify(Object o) throws IOException {
	    	this.truncated = false;
	    	return super.stringify(o);
	    }
	    @Override
	    protected void flushBuffer(char[] buffer, int len) throws IOException {
	    	if(!truncated) {
		    	int l = Math.min(len, maxCharacters-sb.length());
		    	truncated = l<len;
		    	if(l>0) {
		    		sb.append(buffer, 0, l);
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
	// A growable buffer grows up to GROWABLE_MAX, then its content moves to a StringBuilder
	// (compact for ASCII text) - flushBuffer() is not called
	private static final int GROWABLE_MAX = 64*1024;
	private boolean growableBuffer;
	private StringBuilder growableText;
	
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
    protected void useGrowableBuffer(int initialSize) {
    	this.growableBuffer = true;
    	this.buffer = new char[Math.max(16, initialSize)];
    }
    protected boolean isGrowableBuffer() {
    	return growableBuffer;
    }
    /**
     * The text written by the last write(), with a growable buffer.
     */
    protected String getBufferedText() {
    	if(growableText==null) {
    		return new String(buffer, 0, bufferLength);
    	}
    	return growableText.append(buffer, 0, bufferLength).toString();
    }
    
    public boolean isCompact() {
		return compact;
	}

	public void setCompact(boolean compact) {
		this.compact = compact;
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
    	growableText = null;
    	if(buffer==null) {
    		buffer = new char[BUFFER_SIZE];
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
	    	outLiteral(o);
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
				if(growableText==null) {
					growableText = new StringBuilder(GROWABLE_MAX*2);
				}
				growableText.append(buffer, 0, bufferLength);
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
        } else if(value instanceof ReplacerRawJSON rv) {
        	out(rv.getRawContent());
        } else if(value instanceof String s) {
            outStringLiteral(s);
        } else if(value instanceof Number n) {
            outNumberLiteral(n);
        } else if(value instanceof Boolean b) {
            outBooleanLiteral(b);
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
    // Characters written as is in a string literal: printable ASCII, but '"' and '\\'
    private static final boolean[] PLAIN_CHAR = new boolean[128];
    static {
    	for(int c=32; c<128; c++) {
    		PLAIN_CHAR[c] = c!='"' && c!='\\';
    	}
    }
    private void outStringLiteral(String s) throws IOException {
        out('\"');
        int len = s.length();
        int run = 0; // Start of the current run of plain characters
        for(int i=0; i<len; i++) {
            char c = s.charAt(i);
            if(c<128 && PLAIN_CHAR[c]) {
            	continue;
            }
            if(run<i) {
            	out(s, run, i);
            }
            switch(c) {
                case '"': {
                    out("\\\""); 
                } break;
                case '\\': {
                    out("\\\\"); 
                } break;
                case '\b': {
                    out("\\b"); 
                } break;
                case '\f': {
                    out("\\f"); 
                } break;
                case '\n': {
                    out("\\n"); 
                } break;
                case '\r': {
                    out("\\r"); 
                } break;
                case '\t': {
                    out("\\t"); 
                } break;
                default: {
                    // Ensure that it will be transmitted correctly...
                    if(Character.isHighSurrogate(c) && i+1<len && Character.isLowSurrogate(s.charAt(i+1))) {
                        // Per spec's QuoteJSONString: a COMPLETE, valid
                        // surrogate pair (a well-formed astral character)
                        // must be output as its raw UTF-16 code units
                        // unescaped, not as two individual escape sequences -
                        // only a LONE (unpaired) surrogate half gets escaped.
                        out(c);
                        out(s.charAt(++i));
                    } else {
                        outUnicodeEscape(c);
                    }
                }
            }
            run = i+1;
        }
        if(run<len) {
        	out(s, run, len);
        }
        out('\"');
    }
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private void outUnicodeEscape(char c) throws IOException {
    	out('\\');
    	out('u');
    	out(HEX_DIGITS[(c>>12)&0xF]);
    	out(HEX_DIGITS[(c>>8)&0xF]);
    	out(HEX_DIGITS[(c>>4)&0xF]);
    	out(HEX_DIGITS[c&0xF]);
    }
    private void outNumberLiteral(Number n) throws IOException {
    	// NaN and the infinities are not JSON numbers: written as null, like
    	// JSON.stringify() does (writing NaN produced a text the strict parser rejects)
    	if(n instanceof Double d) {
    		if(Double.isInfinite(d) || Double.isNaN(d)) {
   	    		outNullLiteral();
    			return;
    		}
    	} else if(n instanceof Float f) {
    		if(Float.isInfinite(f) || Float.isNaN(f)) {
   	    		outNullLiteral();
    			return;
    		}
    	}
    	// Integer values are written directly, without an intermediate String. The other
    	// numbers use JsonUtil.toString() (JavaScript Number::toString for the doubles).
    	if(n instanceof Integer i) {
    		outLong(i.intValue());
    		return;
    	}
    	if(n instanceof Long l) {
    		outLong(l.longValue());
    		return;
    	}
    	if(n instanceof Double d) {
    		// Same as DtoA.toStandard() for an integral value: 0 (-0 too), or the digits of
    		// the long when it holds the value exactly
    		double v = d.doubleValue();
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
    	}
		out(JsonUtil.toString(n));
    }
    private final char[] digits = new char[20];
    // "00", "01", ... "99": two digits per division
    private static final char[] DIGIT_PAIRS = new char[200];
    static {
    	for(int i=0; i<100; i++) {
    		DIGIT_PAIRS[i*2] = (char)('0'+i/10);
    		DIGIT_PAIRS[i*2+1] = (char)('0'+i%10);
    	}
    }
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
    	final char[] d = digits;
    	int pos = d.length;
    	// The long divisions only while the value doesn't fit an int
    	while(v>Integer.MAX_VALUE) {
    		int r = (int)(v%100);
    		v /= 100;
    		d[--pos] = DIGIT_PAIRS[r*2+1];
    		d[--pos] = DIGIT_PAIRS[r*2];
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
    	out(d, pos, d.length);
    }
    private void outBooleanLiteral(boolean b) throws IOException {
        out(b?"true":"false"); 
    }    
    private void outObjectLiteral(JsonObject container) throws IOException, JsonException {
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