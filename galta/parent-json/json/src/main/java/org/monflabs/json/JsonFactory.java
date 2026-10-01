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
package org.monflabs.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.json.parser.JsonParser;
import org.monflabs.json.parser.ParseException;
import org.monflabs.json.stringifier.JsonStringifier;
import org.monflabs.util.StringUtil;

/**
 * Helpers to handle JSON values.
 */
public abstract class JsonFactory {
	
	public static final class StaticFactory implements JsonFactoryService {
		private final JsonFactory instance;
		StaticFactory(JsonFactory instance) {
			this.instance = instance;
		}
		@Override
		public JsonFactory get() {
			return instance;
		}
	}

	private static final class DefaultFactory implements JsonFactoryService {
		@Override
		public JsonFactory get() {
			JsonFactoryService f;
			// Same lock as set()/setFactory(): a factory set while the default one is being
			// looked up is not overwritten by it
			synchronized(FACTORY_LOCK) {
				if(factory==this) {
					factory = new StaticFactory(findFactory());
				}
				f = factory;
			}
			return f.get();
		}
		private JsonFactory findFactory() {
			// 1- Look if there is a registered service
			// For now we only look for a JsonFactory instance, but we can look for a JsonFactpry.Factory if necessary
			ServiceLoader<JsonFactoryService> loader = ServiceLoader.load(JsonFactoryService.class);
			Optional<JsonFactoryService> factory = loader.findFirst();
			if(factory.isPresent()) {
				return factory.get().get();
			}
			
			// 2- Return the java factory...
			return JavaJsonFactory.instance;
		}
	}
	
	// volatile: set() from one thread must be seen by get() in the others
	private static volatile JsonFactoryService factory = new DefaultFactory();
	private static final Object FACTORY_LOCK = new Object();
	
	public static JsonFactory get() {
		return factory.get();
	}
	public static void set(JsonFactory instance) {
		setFactory(new StaticFactory(instance));
	}
	
	public static JsonFactoryService getFactory() {
		return factory;
	}
	public static void setFactory(JsonFactoryService factory) {
		synchronized(FACTORY_LOCK) {
			JsonFactory.factory = factory;
		}
	}	
	
	/**
	 * Convert from a native JSON library to a Java value.
	 * 
	 * The returned value can be one of:
	 * <ul>
	 *   <li>null</li>
	 *   <li>Boolean</li>
	 *   <li>Number</li>
	 *   <li>String</li>
	 *   <li>JsonObject</li>
	 *   <li>JsonArray</li>
	 * </ul>
	 * 
	 * @param jsonValue
	 * @return the java value
	 */
	public abstract Object toJavaPrimitive(Object jsonValue);

	
	/**
	 * Convert a Java value back to the native JSON one.
	 * 
	 * @param javaValue
	 * @return
	 */
	public abstract Object toNativeJsonPrimitive(Object javaValue);

	public abstract Object toNativeNull();
	public abstract Object toNativeBoolean(boolean value);
	public abstract Object toNativeNumber(Number value);
	public abstract Object toNativeByte(byte value);
	public abstract Object toNativeShort(short value);
	public abstract Object toNativeInt(int value);
	public abstract Object toNativeLong(long value);
	public abstract Object toNativeFloat(float value);
	public abstract Object toNativeDouble(double value);
	public abstract Object toNativeBigInteger(BigInteger value);
	public abstract Object toNativeBigDecimal(BigDecimal value);
	public abstract Object toNativeString(String value);
	public abstract Object toNativeObject(JsonObject value);
	public abstract Object toNativeArray(JsonArray value);

	public Object toNativeBoolean(Boolean value) {
		if(value==null) {
			return toNativeNull();
		} else {
			return toNativeBoolean(value.booleanValue());
		}
	}
	public Object toNativeByte(Byte value) {
		if(value==null) {
			return toNativeNull();
		} else {
			return toNativeByte(value.byteValue());
		}
	}
	public Object toNativeShort(Short value) {
		if(value==null) {
			return toNativeNull();
		} else {
			return toNativeShort(value.shortValue());
		}
	}
	public Object toNativeInt(Integer value) {
		if(value==null) {
			return toNativeNull();
		} else {
			return toNativeInt(value.intValue());
		}
	}
	public Object toNativeLong(Long value) {
		if(value==null) {
			return toNativeNull();
		} else {
			return toNativeLong(value.longValue());
		}
	}
	public Object toNativeFloat(Float value) {
		if(value==null) {
			return toNativeNull();
		} else {
			return toNativeFloat(value.floatValue());
		}
	}
	public Object toNativeDouble(Double value) {
		if(value==null) {
			return toNativeNull();
		} else {
			return toNativeDouble(value.doubleValue());
		}
	}

	public boolean nativeEquals(Object v1, Object v2) {
		if(v1==null) return v2==null;
		if(v2==null) return false;
		return v1.equals(v2);

	}

		
	//
	// Object handling
	//
	
	public abstract JsonObject createObject();

	
	//
	// Array handling
	//

	public abstract JsonArray createArray();
	public abstract JsonArray createArray(int initalCapacity);

	
	//
	// Parsing/conversion options
	//
	public enum INTEGER { INT, LONG, BIGINT }
	public enum OVERFLOW_INTEGER { DOUBLE, BIGINT }
	public enum DECIMAL { DOUBLE, BIGDEC }
	public enum OVERFLOW_DECIMAL { DOUBLE, BIGDEC }

	public INTEGER defaultInteger() { return INTEGER.INT; }
	public OVERFLOW_INTEGER overflowInteger() { return OVERFLOW_INTEGER.BIGINT; }
	public DECIMAL defaultDecimal() { return DECIMAL.DOUBLE; }
	public OVERFLOW_DECIMAL overflowDecimal() { return OVERFLOW_DECIMAL.BIGDEC; }
	
	public boolean useLongIntegers() {return true;}

	
	//
	// Parsing
	// This default implementation uses a common parser but can be replaced by a library
	// native implementation.
	//
	
	//
	// The three sources follow the same rules: the lenient parser is used, a leading byte
	// order mark is skipped, an empty or blank input (whitespace and comments only) parses
	// as null, and a syntax error is a ParseException. parse(String) throws it as is, the
	// Reader and InputStream variants wrap it in a JsonException (with the same message)
	// whose cause is the ParseException.
	//

	public Object parse(InputStream json) {
		return parse(json,StandardCharsets.UTF_8);
	}
	public Object parse(InputStream json, Charset charSet) {
		return parse(json, charSet, false);
	}
	/**
	 * Parse a stream. In strict mode, the strict parser is used (standard JSON only, an
	 * empty input is an error) and an invalid byte sequence for the charset is an error,
	 * instead of being decoded as U+FFFD (the replacement character).
	 */
	public Object parse(InputStream json, Charset charSet, boolean strict) {
		if(json==null) {
			throw new JsonException(null,"The JSON stream to parse is null");
		}
		Charset cs = charSet!=null ? charSet : Charset.defaultCharset();
		Reader reader;
		if(strict) {
			CharsetDecoder decoder = cs.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT);
			reader = new InputStreamReader(json,decoder);
		} else {
			reader = new InputStreamReader(json,cs);
		}
		return parse(reader, strict, "stream");
	}
	public Object parse(Reader json) {
		return parse(json, false);
	}
	/**
	 * Parse the content of a Reader, with the strict parser (standard JSON only, an empty
	 * input is an error) when strict is true.
	 */
	public Object parse(Reader json, boolean strict) {
		return parse(json, strict, "reader");
	}
	private Object parse(Reader json, boolean strict, String source) {
		if(json==null) {
			throw new JsonException(null,"The JSON {0} to parse is null",source);
		}
		JsonParser.ReaderParser parser = new JsonParser.ReaderParser(this);
		parser.setStrict(strict);
		try {
			return parser.parse(json);
		} catch(ParseException ex) {
		    throw new JsonException(ex,"Error when parsing JSON "+source+": "+ex.getMessage());
		} catch(CharacterCodingException ex) {
		    throw new JsonException(ex,"Error when parsing JSON "+source+": invalid byte sequence for the charset ("+ex+")");
		} catch(IOException ex) {
		    throw new JsonException(ex,"Error when parsing JSON "+source+": "+ex.getMessage());
		}
	}
	public Object parse(String json) {
		return parse(json, false);
	}
	/**
	 * Parse a String, with the strict parser (standard JSON only, an empty input is an
	 * error) when strict is true.
	 */
	public Object parse(String json, boolean strict) {
		if(json==null) {
			throw new JsonException(null,"The JSON string to parse is null");
		}
		try {
			JsonParser.StringParser parser = new JsonParser.StringParser(this);
			parser.setStrict(strict);
			return parser.parse(json);
		} catch(JsonException ex) {
			throw ex;
		} catch(IOException | RuntimeException ex) {
			// A String is never read with an IOException
		    throw new JsonException(ex,"Error when parsing JSON string: "+ex.getMessage());
		}
	}

	
	//
	// Stringify
	// This default implementation uses a common parser but can be replaced by a library
	// native implementation.
	//
	
	public void stringify(Writer writer, Object value) {
		stringify(writer, value, true);
	}
	public void stringify(Writer writer, Object value, boolean compact) {
		try {
			JsonStringifier.WriterSerializer w = new JsonStringifier.WriterSerializer();
			// The configuration first: the explicit compact argument wins, as in the other
			// stringify methods
			configureJsonStringifier(w);
			w.setCompact(compact);
			w.stringify(writer,value);
        } catch(IOException ex) {
		    throw new JsonException(ex,"Error when serializing JSON content"); 
		}		
	}

	public String stringify(Object value) {
		return stringify(value, true);
	}
	public String stringify(Object value, boolean compact) {
		try {
			JsonStringifier.StringSerializer w = new JsonStringifier.StringSerializer();
			configureJsonStringifier(w);
			w.setCompact(compact);
			return w.stringify(value);
        } catch(IOException ex) {
		    throw new JsonException(ex,"Error when serializing JSON content"); 
		}		
	}
	
	public String stringifySorted(Object value) {
		return stringifySorted(value, true);
	}
	public String stringifySorted(Object value, boolean compact) {
		try {
			JsonStringifier.StringSerializer w = new JsonStringifier.StringSerializer();
			configureJsonStringifier(w);
			w.setCompact(compact);
			w.setSortProperties(true);
			return w.stringify(value);
        } catch(IOException ex) {
		    throw new JsonException(ex,"Error when serializing JSON content"); 
		}		
	}

	public String stringifyDebug(Object value) {
		try {
			JsonStringifier.StringSerializer w = new JsonStringifier.StringSerializer();
			configureJsonStringifier(w);
			w.setCompact(false);
			w.setSortProperties(true);
			return w.stringify(value);
        } catch(IOException ex) {
		    throw new JsonException(ex,"Error when serializing JSON content"); 
		}		
	}
	
	public String toDebugString(Object value) {		
		return stringify(value, true);
	}

	/**
	 * The pretty JSON text of a value, for display (the toString() of the containers): a
	 * container that contains itself is written as the string "[circular]" instead of
	 * throwing a JsonException.CircularReference.
	 */
	public String toDisplayString(Object value) {
		try {
			JsonStringifier.StringSerializer w = new JsonStringifier.StringSerializer();
			configureJsonStringifier(w);
			w.setCompact(false);
			w.setCircularReferenceMarker("[circular]");
			return w.stringify(value);
		} catch(IOException ex) {
		    throw new JsonException(ex,"Error when serializing JSON content");
		}
	}

	protected void configureJsonStringifier(JsonStringifier configure) {
	}

	
	//
	// Factories
	//
	
	/**
	 * An object from key/value pairs: of("a", 1, "b", 2). The keys must be strings and
	 * every key needs a value (a JsonException otherwise).
	 */
	public JsonObject of(Object...values) {
		if((values.length&1)!=0) {
			throw new JsonException(null,"JsonObject.of() takes key/value pairs, but got an odd number of arguments ({0}): the key {1} has no value",
					values.length,JsonUtil.toDebugString(values[values.length-1]));
		}
		JsonObject o = createObject();
		for(int i=0; i<values.length; i+=2) {
			if(!(values[i] instanceof String key)) {
				throw new JsonException(null,"JsonObject.of() takes key/value pairs, but the key at position {0} is not a String: {1}",
						i,JsonUtil.toDebugString(values[i]));
			}
			o.putValue(key, values[i+1]);
		}
		return o;
	}
	
	public JsonArray arrayOf(Object...values) {
		JsonArray a = createArray();
		for(int i=0; i<values.length; i++) {
			Object value = values[i];
			a.addValue(value);
		}
		return a;
	}
	
	
	//
	// Data types handling
	//
	public abstract boolean asBoolean(Object nativeValue);
	public abstract byte asByte(Object nativeValue);
	public abstract short asShort(Object nativeValue);
	public abstract int asInt(Object nativeValue);
	public abstract long asLong(Object nativeValue);
	public abstract float asFloat(Object nativeValue);
	public abstract double asDouble(Object nativeValue);
	
	public Boolean asBooleanObject(Object nativeValue) {
		return asBoolean(nativeValue);
	}
	public abstract Number asNumber(Object nativeValue);
	public Byte asByteObject(Object nativeValue) {
		return asByte(nativeValue);
	}
	public Short asShortObject(Object nativeValue) {
		return asShort(nativeValue);
	}
	public Integer asIntObject(Object nativeValue) {
		return asInt(nativeValue);
	}
	public Long asLongObject(Object nativeValue) {
		return asLong(nativeValue);
	}
	public Float asFloatObject(Object nativeValue) {
		return asFloat(nativeValue);
	}
	public Double asDoubleObject(Object nativeValue) {
		return asDouble(nativeValue);
	}
	public abstract BigInteger asBigInteger(Object nativeValue);
	public abstract BigDecimal asBigDecimal(Object nativeValue);
	public abstract String asString(Object nativeValue);
	public abstract JsonObject asObject(Object nativeValue);
	public abstract JsonArray asArray(Object nativeValue);

	
	public final boolean asBoolean(Object nativeValue, boolean defaultValue) {
		return !isNativeNull(nativeValue) ? asBoolean(nativeValue) : defaultValue;
	}
	public final Number asNumber(Object nativeValue, Number defaultValue) {
		return !isNativeNull(nativeValue) ? asNumber(nativeValue) : defaultValue;
	}
	public final byte asByte(Object nativeValue, byte defaultValue) {
		return !isNativeNull(nativeValue) ? asByte(nativeValue) : defaultValue;
	}
	public final short asShort(Object nativeValue, short defaultValue) {
		return !isNativeNull(nativeValue) ? asShort(nativeValue) : defaultValue;
	}
	public final int asInt(Object nativeValue, int defaultValue) {
		return !isNativeNull(nativeValue) ? asInt(nativeValue) : defaultValue;
	}
	public final long asLong(Object nativeValue, long defaultValue) {
		return !isNativeNull(nativeValue) ? asLong(nativeValue) : defaultValue;
	}
	public final float asFloat(Object nativeValue, float defaultValue) {
		return !isNativeNull(nativeValue) ? asFloat(nativeValue) : defaultValue;
	}
	public final double asDouble(Object nativeValue, double defaultValue) {
		return !isNativeNull(nativeValue) ? asDouble(nativeValue) : defaultValue;
	}
	
	public final Boolean asBooleanObject(Object nativeValue, Boolean defaultValue) {
		return !isNativeNull(nativeValue) ? (Boolean)asBoolean(nativeValue) : defaultValue;
	}
	public final Byte asByteObject(Object nativeValue, Byte defaultValue) {
		return !isNativeNull(nativeValue) ? (Byte)asByte(nativeValue) : defaultValue;
	}
	public final Short asShortObject(Object nativeValue, Short defaultValue) {
		return !isNativeNull(nativeValue) ? (Short)asShort(nativeValue) : defaultValue;
	}
	public final Integer asIntObject(Object nativeValue, Integer defaultValue) {
		return !isNativeNull(nativeValue) ? (Integer)asInt(nativeValue) : defaultValue;
	}
	public final Long asLongObject(Object nativeValue, Long defaultValue) {
		return !isNativeNull(nativeValue) ? (Long)asLong(nativeValue) : defaultValue;
	}
	public final Float asFloatObject(Object nativeValue, Float defaultValue) {
		return !isNativeNull(nativeValue) ? (Float)asFloat(nativeValue) : defaultValue;
	}
	public final Double asDoubleObject(Object nativeValue, Double defaultValue) {
		return !isNativeNull(nativeValue) ? (Double)asDouble(nativeValue) : defaultValue;
	}
	public final BigInteger asBigInteger(Object nativeValue, BigInteger defaultValue) {
		return !isNativeNull(nativeValue) ? asBigInteger(nativeValue) : defaultValue;
	}
	public final BigDecimal asBigDecimal(Object nativeValue, BigDecimal defaultValue) {
		return !isNativeNull(nativeValue) ? asBigDecimal(nativeValue) : defaultValue;
	}
	public final String asString(Object nativeValue, String defaultValue) {
		return !isNativeNull(nativeValue) ? asString(nativeValue) : defaultValue;
	}
	public final JsonObject asObject(Object nativeValue, JsonObject defaultValue) {
		return !isNativeNull(nativeValue) ? asObject(nativeValue) : defaultValue;
	}
	public final JsonArray asArray(Object nativeValue, JsonArray defaultValue) {
		return !isNativeNull(nativeValue) ? asArray(nativeValue) : defaultValue;
	}
	
	public abstract boolean isNativeNull(Object nativeValue);
	public abstract boolean isNativeBoolean(Object nativeValue);
	public abstract boolean isNativeNumber(Object nativeValue);
	public abstract boolean isNativeString(Object nativeValue);
	public abstract boolean isNativeContainer(Object nativeValue);
	public abstract boolean isNativeObject(Object nativeValue);
	public abstract boolean isNativeArray(Object nativeValue);
	
	
	//
	// Json Clone
	//
	
	public abstract <T> T deepClone(Object value);
	
	
	//
	// Capabilities
	//
	
	public boolean supportsNullKeys() {
		return false;
	}

	public boolean supportsNaN() {
		return false;
	}

	public boolean supportsInfinity() {
		return false;
	}

	public boolean supportsReferences() {
		return false;
	}
	
	
	//
	// Additional capabilities
	//
	
	private Map<Object, Object> capabilities = new HashMap<>();
	
	public synchronized <T> T getCapability(Class<? extends T> clazz, Supplier<T> supp) {
		@SuppressWarnings("unchecked")
		T t = (T)capabilities.get(clazz);
		if(t==null && supp!=null) {
			t = supp.get();
			if(t!=null ) {
				capabilities.put(clazz, t);
			}
		}
		return t;
	}
	
	
	//
	// Number helpers
	//
	// Parsing functions throw an exception when parsing fails.
	// A caller, like JS engine, can transform it to NaN.
	//

	public final static int PARSEINT_IGNOREEXTRACHAR 		= 0x0001;
	public final static int PARSEINT_SUPPORTOCTALPREFIX 	= 0x0002;
	public final static int PARSEINT_RETURNNAN 				= 0x0004;
	

	public Number parseNumber(String s, int options) {
		if(s==null) {
			throw new JsonException(null,"Number is null");
		}
		int len = s.length();
		if (len==0) {
			throw new JsonException(null,"Invalid number format {0}",s);
		}
    	
		// JSON 5
        if (s.equals("NaN")) {
            return Double.NaN;
        }
        if (StringUtil.equals(s, "Infinity") || StringUtil.equals(s, "+Infinity")) { 
            return Double.POSITIVE_INFINITY;
        }
        if (StringUtil.equals(s, "-Infinity")) {
            return Double.NEGATIVE_INFINITY;
        }

        try {
			int ptr=0;
			char c = s.charAt(ptr);
			boolean hasSign = (c=='+' || c=='-');
			if(hasSign) {
				ptr++;
				if(ptr==len) {
					throw new JsonException(null,"Invalid number format");
				}
				c = s.charAt(ptr);
			}
			// Non-decimal integer literals (0x/0o/0b) never take a sign
			// (StringNumericLiteral grammar: NonDecimalIntegerLiteral has no
			// sign production, unlike StrDecimalLiteral) - "+0x10"/"-0x10"
			// must be NaN, not 16/-16.
			if(!hasSign && c=='0') {
				if(ptr+1<len) {
					c = s.charAt(ptr+1);
					if(c=='x' || c=='X') {
						return parseInt(s,16,options);
					}
					if(c=='o' || c=='O' ) {
						return parseInt(s,8,options);
					}
					if(c=='b' || c=='B' ) {
						return parseInt(s,2,options);
					}
				}
			}
			while(ptr<len) {
				c = s.charAt(ptr++);
				if(c<'0' || c>'9') {
					return parseFloat(s,options);
				}
			}
			return parseInt(s,0,options);
		} catch(Exception e) {
			throw JsonException.wrap(e);
		}
	}

	public Number parseIntegerWithRadix(String s) {
		if(s==null) {
			throw new JsonException(null,"Number is null");
		}
    	s = s.trim();
		return parseInt(s,0,0);
	}

	public Number parseInteger(String s) {
		if(s==null) {
			throw new JsonException(null,"Number is null");
		}
    	s = s.trim();
		return parseInt(s,10,0);
	}
	public Number parseInteger(String s, Integer radix) {
		if(s==null) {
			throw new JsonException(null,"Number is null");
		}
    	s = s.trim();
		return parseInt(s,radix,0);
	}
	
	public Number parseDecimal(String s) {
		if(s==null) {
			throw new JsonException(null,"Number is null");
		}
    	s = s.trim();
		return parseFloat(s,0);
	}
	
	//
	// Functions that emulate JavaScript Number functions
	//
	
	// parseInt only recognizes ASCII digits/letters as digit characters -
	// unlike Character.digit(), which is Unicode-aware and would wrongly
	// accept e.g. Arabic-Indic digits (U+0660-U+0669) as decimal digits.
	// Returns a value >= any valid radix (<=36) for a non-digit character.
	private static int asciiDigitValue(char c) {
		if(c>='0' && c<='9') return c-'0';
		if(c>='A' && c<='Z') return c-'A'+10;
		if(c>='a' && c<='z') return c-'a'+10;
		return Integer.MAX_VALUE;
	}

	public Number parseInt(String str, int radix, int options) {
		boolean ignoreExtraCharacters = (options & PARSEINT_IGNOREEXTRACHAR)!=0;
		
		// radix==0 means infer...
        int len = str.length();
        int ptr = 0;
        if(ptr==len) {
        	if((options&PARSEINT_RETURNNAN)!=0) {
        		return Double.NaN;
        	}
			throw new JsonException(null,"Empty integer string");
        }
        
        char c = str.charAt(ptr);
        boolean minus = c == '-';
        if (c == '-' || c == '+') {
            ptr++;
	        if(ptr==len) {
	        	if((options&PARSEINT_RETURNNAN)!=0) {
	        		return Double.NaN;
	        	}
				throw new JsonException(null,"Invalid integer format {0}",str);
	        }
        }

    	if(str.charAt(ptr)=='0') {
            if (str.startsWith("0x",ptr) || str.startsWith("0X",ptr)) {
            	if(radix==0 || radix==16) {
            		radix = 16;
            		ptr += 2;
            	}
            } else if (str.startsWith("0o",ptr) || str.startsWith("0O",ptr)) {
            	if(radix==0 || radix==8) {
            		radix = 8;
            		ptr += 2;
            	}
            } else if (str.startsWith("0b",ptr) || str.startsWith("0B",ptr)) {
            	if(radix==0 || radix==2) {
            		radix = 2;
            		ptr += 2;
            	}
            } else {
            	// Just one '0' for octal, should we support it?
            	// Rhino supports octal, but not V8 (RunJS)
            	boolean supportOctal = (options & PARSEINT_SUPPORTOCTALPREFIX)!=0;
            	if (supportOctal) {
                	if (radix==0) { 
                   		radix = 8;
                	}
            	}
            	if(radix==0) {
            		radix = 10;
            	}
            }
            if(ptr==len) {
	        	if((options&PARSEINT_RETURNNAN)!=0) {
	        		return Double.NaN;
	        	}
    			throw new JsonException(null,"Invalid integer format {0}, radix {1}",str,radix);
            }
        } else {
        	if(radix==0) {
        		radix = 10;
        	}
        }
        
        if (radix < 2 || radix > 36) {
        	if((options&PARSEINT_RETURNNAN)!=0) {
        		return Double.NaN;
        	}
			throw new JsonException(null,"Invalid integer radix {0}",radix);
        }

        int i;
        for (i = ptr; i < len; i++) {
            if(asciiDigitValue(str.charAt(i))>=radix) {
            	if(!ignoreExtraCharacters) {
    	        	if((options&PARSEINT_RETURNNAN)!=0) {
    	        		return Double.NaN;
    	        	}
        			throw new JsonException(null,"Invalid integer format {0}, radix {1}",str,radix);
            	}
                break;
            }
        }
        String num = str.substring(ptr, i);
        if(num.isEmpty()) {
        	// No digit at all ("0x", "+")
        	if((options&PARSEINT_RETURNNAN)!=0) {
        		return Double.NaN;
        	}
			throw new JsonException(null,"Invalid integer format {0}, radix {1}",str,radix);
        }

        if(minus && isZero(num)) {
        	// -0 is a double, as in JavaScript (an integer type has no negative zero)
       		return -0.0;
        }
        // "0" goes through the same path as any other value, so it has the configured
        // integer type (a BigInteger in BIGINT mode)
       	return _parseValidInteger(minus?("-"+num):num, radix, options);
    }

    private static boolean isZero(String num) {
    	for(int i=0; i<num.length(); i++) {
    		if(num.charAt(i)!='0') {
    			return false;
    		}
    	}
    	return true;
    }

    // An invalid float: NaN if the options ask for it, else an exception
    private static Number invalidFloat(String str, int options) {
    	if((options&PARSEINT_RETURNNAN)!=0) {
    		return Double.NaN;
    	}
		throw new JsonException(null,"Invalid number format {0}",str);
    }

    public Number parseFloat(String str, int options) {
		boolean ignoreExtraCharacters = (options & PARSEINT_IGNOREEXTRACHAR)!=0;

        if (StringUtil.isEmpty(str)) {
        	return invalidFloat(str, options);
        }
        int len = str.length();
        int ptr = 0;
        if (str.startsWith("NaN")) {
        	ptr += 3;
            if(ignoreExtraCharacters || ptr==len) {
                return Double.NaN;
            }
            return invalidFloat(str, options);
        }
        if (str.startsWith("Infinity") ) {
        	ptr += 8;
            if(ignoreExtraCharacters || ptr==len) {
                return Double.POSITIVE_INFINITY;
            }
            return invalidFloat(str, options);
        }
        if (str.startsWith("+Infinity")) {
        	ptr += 9;
            if(ignoreExtraCharacters || ptr==len) {
                return Double.POSITIVE_INFINITY;
            }
            return invalidFloat(str, options);
        }
        if (str.startsWith("-Infinity")) {
        	ptr += 9;
            if(ignoreExtraCharacters || ptr==len) {
                return Double.NEGATIVE_INFINITY;
            }
            return invalidFloat(str, options);
        }
        
        int exponentIndicatorPosition = -1;
        int decimalSeparatorPosition = -1;
        int i;
        for (i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                continue;
            }
            if (c == '.') {
                if (exponentIndicatorPosition == -1) {
                    if (decimalSeparatorPosition == -1) {
                        decimalSeparatorPosition = i;
                        continue;
                    } else {
                        break; // Only one decimal '.' is allowed
                    }
                } else {
                    break; // The exponent can't be decimal
                }
            }
            if (c == '+' || c == '-') {
                if (i == 0) {
                    // The first character may be a + or a -
                    continue;
                }
                if ((exponentIndicatorPosition != -1) && (i == exponentIndicatorPosition + 1)) {
                    continue; // There may be a + or a - just after the exponent indicator
                }
                break; // Not expected in that place
            }
            if (c == 'e' || c == 'E') {
                if (exponentIndicatorPosition == -1) {
                    exponentIndicatorPosition = i;
                    continue;
                } else {
                    break; // Only zero or one exponent indicator expected
                }
            }

            break; // Stop the search in all other cases
        }
        // If the exponent has no digit following it (bare trailing "e"/"E", or
        // an exponent sign with nothing after it, e.g. "1e-x"), the whole
        // exponent part is invalid - back off to before the "e"/"E".
        if(exponentIndicatorPosition>=0) {
        	int digitsStart = exponentIndicatorPosition+1;
        	if(digitsStart<i && (str.charAt(digitsStart)=='+' || str.charAt(digitsStart)=='-')) {
        		digitsStart++;
        	}
        	if(digitsStart>=i) {
        		i = exponentIndicatorPosition;
        	}
        }
        
        if(i<len && !ignoreExtraCharacters) {
        	return invalidFloat(str, options);
        }

        // The mantissa must have at least one digit ("-", ".", "e5" and "-.e1" are not numbers)
        int mantissaEnd = exponentIndicatorPosition>=0 && exponentIndicatorPosition<i ? exponentIndicatorPosition : i;
        boolean hasDigit = false;
        for(int k=0; k<mantissaEnd; k++) {
        	char c = str.charAt(k);
        	if(c>='0' && c<='9') {
        		hasDigit = true;
        		break;
        	}
        }
        if (!hasDigit) {
        	return invalidFloat(str, options);
        }
        return _parseValidDecimal(str.substring(0, i));
    }

	
	
	
	//
	// Parse number with overflow
	//
	
	private Number _parseValidInteger(String s, int radix, int options) {
		try {
			INTEGER def = defaultInteger();
			if(def==INTEGER.BIGINT) {
				return new BigInteger(s,radix);
			}
			// Try an integer - Long
			try {
				long v = Long.parseLong(s,radix);
				if( def==INTEGER.INT) {
					if (v >= (long) Integer.MIN_VALUE && v <= (long) Integer.MAX_VALUE) {
						return Integer.valueOf((int) v);
					}
				}
				// An explicit LONG default always gives a Long
				if(def==INTEGER.LONG || useLongIntegers()) {
					return Long.valueOf(v);
				}
				if(overflowInteger()!=OVERFLOW_INTEGER.BIGINT) {
					return (double)v;
				}
			} catch (NumberFormatException e) {}
			if(radix==10 && overflowInteger()!=OVERFLOW_INTEGER.BIGINT) {
				// Same correctly rounded value as new BigInteger(s).doubleValue(), in a time
				// linear with the length (the BigInteger conversion is not)
				return Double.parseDouble(s);
			}
			// We cannot parse the int directly to a double because it has some precision issues
			// when doing d*radix+digit.
			// We go through a BigInteger, which may not the most efficient but the use cases should be
			// should be rare as well (> MAX_LONG).
			BigInteger bi = new BigInteger(s,radix);
			if(overflowInteger()==OVERFLOW_INTEGER.BIGINT) {
				return bi;
			}
			// Should convert to POSITIVE/NEGATIVE infinity
			return bi.doubleValue();
		} catch(Exception e) {
        	if((options&PARSEINT_RETURNNAN)!=0) {
        		return Double.NaN;
        	}
			throw new JsonException(e,"Value {0} is not a number of base {1}",s,radix);
		}
	}

	private Number _parseValidDecimal(String s) {
		DECIMAL def = defaultDecimal();
		if (def==DECIMAL.BIGDEC) {
			try {
				return new BigDecimal(s);
			} catch(NumberFormatException e) {
				// The exponent is out of the BigDecimal range (an int scale), like 1e3000000000
				throw new JsonException(e,"Number {0} is out of the BigDecimal range",s);
			}
		}
		double d = Double.parseDouble(s);
		if(overflowDecimal()==OVERFLOW_DECIMAL.BIGDEC && !fitsDouble(s, d)) {
			try {
				return new BigDecimal(s);
			} catch(NumberFormatException e) {
				// The exponent is out of the BigDecimal range (an int scale), like
				// 1e3000000000: the double value (Infinity or 0), like JSON.parse()
				return d;
			}
		}
		return d;
	}

	/**
	 * Check if a double holds the value of a decimal literal: not an overflow to
	 * Infinity, not an underflow to 0, and no precision loss (the double's shortest
	 * representation is the same number as the literal). The decision depends on the
	 * value, not on the literal length ("0.10000000000000000" fits, "1e400" doesn't).
	 */
	private static boolean fitsDouble(String s, double d) {
		if(Double.isInfinite(d)) {
			return false;
		}
		// Count the significant digits of the mantissa
		int digits = 0;
		boolean nonZero = false;
		int len = s.length();
		for(int i=0; i<len; i++) {
			char c = s.charAt(i);
			if(c=='e' || c=='E') {
				break;
			}
			if(c>='0' && c<='9') {
				if(c!='0') {
					nonZero = true;
				}
				if(nonZero) {
					digits++;
				}
			}
		}
		if(d==0.0) {
			return !nonZero; // "0.0" fits, "1e-400" underflows
		}
		if(digits<=15 && Math.abs(d)>=Double.MIN_NORMAL) {
			return true; // A normal double always holds 15 significant decimal digits
		}
		// Same as new BigDecimal(s).compareTo(BigDecimal.valueOf(d))==0, without the two
		// BigDecimal: BigDecimal.valueOf(d) is Double.toString(d), and two finite decimal
		// literals have the same value when their signs, significant digits and decimal
		// exponents are the same
		return sameDecimalValue(s, Double.toString(d));
	}

	/**
	 * Compare the values of two finite, non zero, decimal literals
	 * ([+-]digits[.digits][(e|E)[+-]digits]), without allocating anything.
	 */
	static boolean sameDecimalValue(String a, String b) {
		long ea = decimalShape(a);
		long eb = decimalShape(b);
		if(ea!=eb) {
			return false; // Different sign, exponent or number of significant digits
		}
		// Same shape: compare the significant digits one by one
		int ia = firstSignificant(a);
		int ib = firstSignificant(b);
		int count = (int)(ea & 0xFF);
		for(int n=0; n<count; n++) {
			char ca = a.charAt(ia++);
			if(ca=='.') {
				ca = a.charAt(ia++);
			}
			char cb = b.charAt(ib++);
			if(cb=='.') {
				cb = b.charAt(ib++);
			}
			if(ca!=cb) {
				return false;
			}
		}
		return true;
	}
	private static int firstSignificant(String s) {
		int len = s.length();
		for(int i=0; i<len; i++) {
			char c = s.charAt(i);
			if(c>='1' && c<='9') {
				return i;
			}
			if(c=='e' || c=='E') {
				break;
			}
		}
		return len;
	}
	/**
	 * The shape of a decimal literal written as sign * 0.digits * 10^exponent, the digits
	 * having no leading or trailing zero: the sign, the exponent and the number of digits
	 * packed in a long.
	 */
	private static long decimalShape(String s) {
		int len = s.length();
		int i = 0;
		long sign = 0;
		if(i<len && (s.charAt(i)=='-' || s.charAt(i)=='+')) {
			sign = s.charAt(i)=='-' ? 1 : 0;
			i++;
		}
		int intDigits = 0;			// Digits before the '.', leading zeros included
		int leadingZeros = 0;		// Zeros before the first significant digit
		int significant = 0;		// Digits from the first significant one...
		int lastNonZero = 0;		// ...to the last non zero one
		boolean seenDot = false;
		for(; i<len; i++) {
			char c = s.charAt(i);
			if(c=='.') {
				seenDot = true;
			} else if(c>='0' && c<='9') {
				if(!seenDot) {
					intDigits++;
				}
				if(significant==0 && c=='0') {
					leadingZeros++;
				} else {
					significant++;
					if(c!='0') {
						lastNonZero = significant;
					}
				}
			} else {
				break; // exponent
			}
		}
		long exp = 0;
		if(i<len) {
			i++; // 'e' or 'E'
			boolean negative = false;
			if(i<len && (s.charAt(i)=='-' || s.charAt(i)=='+')) {
				negative = s.charAt(i)=='-';
				i++;
			}
			for(; i<len; i++) {
				// Capped: the value is a finite, non zero, double here
				exp = Math.min(exp*10+(s.charAt(i)-'0'), 1_000_000);
			}
			if(negative) {
				exp = -exp;
			}
		}
		long e = intDigits-leadingZeros+exp;
		// Digits are at most a few hundreds (and compared up to 255 here, more than a
		// double ever needs), exponent within +-2_000_000
		return (sign<<40) | ((e+2_000_000L)<<8) | Math.min(lastNonZero, 255);
	}

}
