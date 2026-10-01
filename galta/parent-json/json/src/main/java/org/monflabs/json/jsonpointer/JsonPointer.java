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
package org.monflabs.json.jsonpointer;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.BaseException;
import org.monflabs.util.StringMatcher;
import org.monflabs.util.StringUtil;

/**
 * JsonPointer &amp; extension.
 * RFC 6901
 *     https://www.rfc-editor.org/rfc/rfc6901
 *     
 * The extension deals with a JSON path like string, where we clearly distinguish between
 * integer indexes and strings.
 * <p>
 * Parts are either member names ({@link String}) or array indexes ({@link Integer}).
 * A string part that is a canonical array index ("0", "12", but not "01" or "-0") also
 * addresses an array element, as the RFC defines it. Two extensions are supported:
 * <ul>
 * <li>a leading '/' is optional ("a/b" is the same as "/a/b")
 * <li>negative indexes address an array from its end ("/-1" is the last element)
 * </ul>
 * The "-" token designates the (non existent) element after the last one: it never
 * exists for read, exists, replace and remove, and appends for add and setValue.
 * <p>
 * A pointer created from a JSON Path ({@link #ofJsonPath(String)}, {@link #ofJsonPathParts(Object...)})
 * keeps the JSON Path distinction: a member name ($['0']) never addresses an array element.
 * <p>
 * Pointers are immutable. {@link #getChild(String)} is O(1): the parts array is only built
 * when needed.
 *  
 * @author priand
 */
public class JsonPointer {
	
	// This is a JSON pointer extension
	private static final boolean NEGATIVE_INDEXES = true;

	public static final JsonPointer EMPTY = new JsonPointer(new Object[]{}, false);

	private static Object LASTKEY = new Number() {
		@Override
		public int hashCode() {
			return -1;
		}
		@Override
		public boolean equals(Object o) {
			return this==o; // Singleton
		}
		@Override
		public String toString() {
			return "-";
		}
		@Override
		public int intValue() {
			return -1;
		}
		@Override
		public long longValue() {
			return -1L;
		}
		@Override
		public float floatValue() {
			return -1.0f;
		}
		@Override
		public double doubleValue() {
			return -1.0;
		}
	};
	
	/**
	 * Parse a JSON pointer (RFC 6901).
	 * "" is the whole document, "/" is the member "" of the root, and a trailing
	 * '/' adds an empty member name ("/a/" is ["a",""]).
	 */
	public static JsonPointer of(String path) {
		if(path==null || path.isEmpty()) {
			return EMPTY;
		}
		if(path.charAt(0)=='/') {
			path = path.substring(1);
		}
		return new JsonPointer(parse(path), false);
	}

	/**
	 * Parse the URI fragment representation of a JSON pointer (RFC 6901 section 6):
	 * "#/a%20b" is the member "a b". The leading '#' is optional, and the fragment is
	 * percent-decoded (UTF-8) before being parsed as a pointer.
	 * @throws JsonException for a malformed percent-encoding
	 */
	public static JsonPointer ofFragment(String fragment) {
		if(fragment==null) {
			return EMPTY;
		}
		if(fragment.startsWith("#")) {
			fragment = fragment.substring(1);
		}
		return of(decodeFragment(fragment));
	}

	/**
	 * Percent-decode a URI fragment (UTF-8). A '+' stays a plus sign.
	 * @throws JsonException for a malformed or truncated escape, or bytes that are not UTF-8
	 */
	public static String decodeFragment(String fragment) {
		if(fragment.indexOf('%')<0) {
			return fragment;
		}
		int length = fragment.length();
		StringBuilder b = new StringBuilder(length);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		for(int i=0; i<length; ) {
			char c = fragment.charAt(i);
			if(c!='%') {
				b.append(c);
				i++;
				continue;
			}
			bytes.reset();
			while(i<length && fragment.charAt(i)=='%') {
				int h = i+2<length ? Character.digit(fragment.charAt(i+1),16) : -1;
				int l = i+2<length ? Character.digit(fragment.charAt(i+2),16) : -1;
				if(h<0 || l<0) {
					throw new JsonException(null,"Invalid percent-encoding in {0}", fragment);
				}
				bytes.write((h<<4)|l);
				i += 3;
			}
			try {
				b.append(java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())));
			} catch(java.nio.charset.CharacterCodingException ex) {
				throw new JsonException(ex,"Invalid percent-encoding in {0}", fragment);
			}
		}
		return b.toString();
	}

	/**
	 * Create a JSON pointer from its parts, each being a member name (String)
	 * or an array index (Integer).
	 */
	public static JsonPointer ofParts(Object... parts) {
		return ofParts(parts, false);
	}

	/**
	 * Create a JSON pointer from the parts of a definite JSON Path: the member names
	 * ({@link String}) never address array elements, only the indexes ({@link Integer}) do.
	 */
	public static JsonPointer ofJsonPathParts(Object... parts) {
		return ofParts(parts, true);
	}

	private static JsonPointer ofParts(Object[] parts, boolean memberNames) {
		if(parts==null || parts.length==0) {
			return memberNames ? EMPTY_JSONPATH : EMPTY;
		}
		Object[] p = new Object[parts.length];
		for(int i=0; i<parts.length; i++) {
			Object o = parts[i];
			if(o instanceof String || o instanceof Integer) {
				p[i] = o;
			} else {
				throw new JsonException(null,"Invalid JSON pointer part {0}",o);
			}
		}
		return new JsonPointer(p, memberNames);
	}
	public static JsonPointer ofJsonPath(String path) {
		if(path==null) {
			return EMPTY;
		}
		JsonPathPointerParser p = new JsonPathPointerParser(path);
		return p.createPointer();
	}

	static JsonPointer createJsonPath(Object[] parts) {
		return parts.length==0 ? EMPTY_JSONPATH : new JsonPointer(parts, true);
	}

	private static final JsonPointer EMPTY_JSONPATH = new JsonPointer(new Object[]{}, true);

	// The parts are either materialized (cache), or defined by the parent and the last part:
	// getChild() is then O(1), and the array is only built on demand
	private final JsonPointer parent;
	private final Object last;
	private final int size;
	// Member names never address array elements (JSON Path semantics)
	private final boolean memberNames;
	private volatile Object[] cache;

	private JsonPointer(Object[] parts, boolean memberNames) {
		this.parent = null;
		this.cache = parts;
		this.size = parts.length;
		this.last = size>0 ? parts[size-1] : null;
		this.memberNames = memberNames;
	}
	private JsonPointer(JsonPointer parent, Object last) {
		this.parent = parent;
		this.last = last;
		this.size = parent.size+1;
		this.memberNames = parent.memberNames;
	}

	private Object[] parts() {
		Object[] p = cache;
		if(p==null) {
			p = new Object[size];
			JsonPointer c = this;
			int i = size-1;
			Object[] cc;
			while((cc=c.cache)==null) {
				p[i--] = c.last;
				c = c.parent;
			}
			if(i>=0) {
				System.arraycopy(cc, 0, p, 0, i+1);
			}
			cache = p;
		}
		return p;
	}

	public boolean contains(JsonPointer p) {
		Object[] parts = parts();
		if(parts.length>=p.size) {
			Object[] pparts = p.parts();
			for(int i=0; i<pparts.length; i++) {
				if(!samePart(pparts[i],parts[i])) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	// A part is a reference token: the index 0 and the member "0" are the same token
	private static boolean samePart(Object p1, Object p2) {
		return p1.toString().equals(p2.toString());
	}

	@Override
	public int hashCode() {
		// Order dependent: /a/b and /b/a must not collide systematically
		int h = 1;
		Object[] parts = parts();
		for(int i=0; i<parts.length; i++) {
			h = 31*h + parts[i].toString().hashCode();
		}
		return h;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof JsonPointer p) {
			if(size==p.size) {
				Object[] parts = parts();
				Object[] pparts = p.parts();
				for(int i=0; i<parts.length; i++) {
					if(!samePart(parts[i],pparts[i])) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}
	
	public boolean isEmpty() {
		return size==0;
	}

	public int size() {
		return size;
	}

	public Object getLastPart() {
		return last;
	}

	public Object getPart(int index) {
		if(index==size-1) {
			return last;
		}
		return parts()[index];
	}

	/**
	 * The parts of the pointer, as a new array (changing it doesn't change the pointer).
	 */
	public Object[] getParts() {
		return parts().clone();
	}

	@Override
	public String toString() {
		return toJsonPointerString();
	}

	public String toJsonPointerString() {
		Object[] parts = parts();
		StringBuilder b = new StringBuilder();
		for(int i=0; i<parts.length; i++) {
			Object p = parts[i];
			b.append('/');
			if(p instanceof Number n) {
				b.append(n.toString());
			} else {
				b.append(escape(parts[i].toString()));
			}
		}
		return b.toString();
	}

	public String toJsonPathString() {
		return toJsonPathString(true);
	}
	/**
	 * The equivalent JSON Path.
	 * @throws JsonException if the pointer holds the "-" token (past the last element),
	 * that no JSON Path can express
	 */
	public String toJsonPathString(boolean dollarPrefix) {
		Object[] parts = parts();
		StringBuilder b = new StringBuilder();
		if(dollarPrefix) {
			b.append('$');
		}
		for(int i=0; i<parts.length; i++) {
			Object p = parts[i];
			if(p==LASTKEY) {
				throw new JsonException(null,"The JSON pointer {0} has no JSON Path equivalent ('-' is past the last element)", toJsonPointerString());
			}
			if(p instanceof Number n) {
				b.append('[');
				b.append(n.intValue());
				b.append(']');
			} else {
				String s = p.toString();
				if(JsonUtil.isIdentifier(s)) {
					b.append('.');
					b.append(s);
				} else {
					b.append('[');
					b.append(JsonUtil.encodeString(s, '\''));
					b.append(']');
				}
			}
		}
		return b.toString();
	}

	public JsonPointer getParent() {
		if(size==0) {
			throw new JsonException(null,"JsonPointer doesn't have a parent");
		}
		if(parent!=null) {
			return parent;
		}
		if(size==1) {
			return memberNames ? EMPTY_JSONPATH : EMPTY;
		}
		Object[] parts = parts();
		Object[] p = new Object[parts.length-1];
		System.arraycopy(parts,0,p,0,parts.length-1);
		return new JsonPointer(p, memberNames);
	}

	/**
	 * Child pointer for an object member. The member is always kept as a name,
	 * even when it looks like a number ("0") or is "-".
	 */
	public JsonPointer getChild(String member) {
		return new JsonPointer(this, member);
	}
	public JsonPointer getChild(int index) {
		if(index<0) {
			throw new JsonException(null,"Invalid negative index {0}",index);
		}
		return new JsonPointer(this, index);
	}
	
	public boolean exists(Object v) {
		Object[] parts = parts();
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(!o.containsKey(ks)) {
					return false;
				}
				v = o.get(ks);
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(a, key, memberNames);
				if(index<0) {
					return false;
				}
				v = a.get(index);
			} else {
				return false;
			}
		}
		return true;
	}
	
	public Object read(Object v) {
		Object[] parts = parts();
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(!o.containsKey(ks)) {
					return null;
				}
				v = o.get(ks);
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(a, key, memberNames);
				if(index<0) {
					return null;
				}
				v = a.get(index);
			} else {
				return null;
			}
		}
		return v;
	}
	
	/**
	 * Add a value corresponding to the path.
	 * The missing intermediate parts are NOT created, only the last one.
	 * 
	 */
	public boolean add(Object v, Object value) {
		Object[] parts = parts();
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(i==parts.length-1) {
					o.put(ks, value);
					return true;
				}
				v = o.get(ks);
			} else if(v instanceof JsonArray a) {
				if(i==parts.length-1) {
					if(key==LASTKEY) {
						a.add(value);
					} else {
						int index = partIndex(a, key, memberNames);
						if(NEGATIVE_INDEXES && index<0 && index!=Integer.MIN_VALUE) {
							index = a.size() + index;
						}
						// index == size appends (RFC 6902)
						if(index<0 || index>a.size()) {
							return false;
						}
						a.add(index,value);
					}
					return true;
				}
				int index = existingIndex(a, key, memberNames);
				if(index<0) {
					return false;
				}
				v = a.get(index);
			} else {
				return false;
			}
		}
		return false;
	}

	/**
	 * Set a value for a given pointer.
	 * It creates the necessary parts when they do not exist (properties &amp; indexes).
	 * An array index can address an existing element or the one right after the last
	 * element (its size, or "-"), which appends. Larger indexes are rejected rather
	 * than padding the array with nulls (RFC 6902 'add' semantics).
	 */
	public boolean setValue(Object v, Object value) {
		Object[] parts = parts();
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(i==parts.length-1) {
					o.put(ks, value);
					return true;
				}
				v = o.get(ks);
				if(v==null) {
					v = createPart(i);
					o.put(ks, v);
				}
			} else if(v instanceof JsonArray a) {
				int index = key==LASTKEY ? a.size() : partIndex(a, key, memberNames);
				if(NEGATIVE_INDEXES && index<0 && index!=Integer.MIN_VALUE) {
					index = a.size() + index;
				}
				if(index<0 || index>a.size()) {
					return false;
				}
				if(i==parts.length-1) {
					if(index==a.size()) {
						a.add(value);
					} else {
						a.set(index, value);
					}
					return true;
				}
				if(index==a.size()) {
					v = createPart(i);
					a.add(v);
				} else {
					v = a.get(index);
					if(v==null) {
						v = createPart(i);
						a.set(index, v);
					}
				}
			} else {
				return false;
			}
		}
		return false;
	}
	private Object createPart(int index) {
		Object[] parts = parts();
		// The next index determines the type Object/Array
		Object p = parts[index+1];
		if(p instanceof Number) {
			return JsonArray.create();
		} else {
			return JsonObject.create();
		}
	}
	
	
	/**
	 * Replace the value for the given pointer
	 * The path must exist, and it return false if the path did not exist.
	 */
	public boolean replace(Object v, Object value) {
		Object[] parts = parts();
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(!o.containsKey(ks)) {
					return false;
				}
				if(i==parts.length-1) {
					if(o.containsKey(ks)) {
						o.put(ks, value);
						return true;
					}
					return false;
				}
				v = o.get(ks);
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(a, key, memberNames);
				if(index<0) {
					return false;
				}
				if(i==parts.length-1) {
					a.set(index, value);
					return true;
				}
				v = a.get(index);
			} else {
				return false;
			}
		}
		return false;
	}
	
	public boolean remove(Object v) {
		Object[] parts = parts();
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(!o.containsKey(ks)) {
					return false;
				}
				if(i==parts.length-1) {
					o.remove(ks);
					return true;
				}
				v = o.get(ks);
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(a, key, memberNames);
				if(index<0) {
					return false;
				}
				if(i==parts.length-1) {
					a.remove(index);
					return true;
				}
				v = a.get(index);
			} else {
				return false;
			}
		}
		return false;
	}

	/**
	 * The raw index a part designates in an array, possibly negative (extension),
	 * or Integer.MIN_VALUE if the part is not an index ("-", a member name).
	 */
	private static int partIndex(JsonArray a, Object key, boolean memberNames) {
		if(key==LASTKEY) {
			return Integer.MIN_VALUE;
		}
		if(key instanceof Integer n) {
			return n;
		}
		if(!memberNames && key instanceof String s && !s.isEmpty()) {
			// RFC 6901: a reference token is an index if it is a canonical number
			char c = s.charAt(0);
			if(c>='0' && c<='9') {
				Integer i = parseInteger(s);
				if(i!=null && i>=0) {
					return i;
				}
			}
		}
		return Integer.MIN_VALUE;
	}
	/**
	 * The index of an existing element, or -1 if the part does not designate one.
	 */
	private static int existingIndex(JsonArray a, Object key, boolean memberNames) {
		int index = partIndex(a, key, memberNames);
		if(index==Integer.MIN_VALUE) {
			return -1;
		}
		if(NEGATIVE_INDEXES && index<0) {
			index = a.size() + index;
		}
		if(index<0 || index>=a.size()) {
			return -1;
		}
		return index;
	}
	
	
	//
	// JsonPointer parser
	//
	
    private static Object[] parse(String s) {
        if( s==null ) {
            return StringUtil.EMPTY_STRING_ARRAY;
        }
        // Iterative: a pointer can have a very large number of segments
        List<Object> result = new ArrayList<>();
        int pos = 0;
        for(;;) {
            int newPos = s.indexOf('/',pos);
            if(newPos<0) {
                result.add(partValue(s.substring(pos)));
                return result.toArray();
            }
            result.add(partValue(s.substring(pos, newPos)));
            pos = newPos+1;
        }
    }
    private static Object partValue(String s) {
    	int length = s.length();
    	if(length>0) {
    		char c = s.charAt(0);
	    	if(length==1 && c=='-') {
	    		return LASTKEY;
	    	}
	    	if((c>='0' && c<='9') || (NEGATIVE_INDEXES && c=='-')) {
	    		Integer i = parseInteger(s);
	    		if(i!=null) {
	    			return i;
	    		}
	    	}
	    	return unescape(s);
    	}
    	return s;
    }
	/**
	 * Parse a canonical integer: no leading zeros, no "-0". Anything else is a
	 * member name ("01", "-0", "-01").
	 */
	private static Integer parseInteger(String s) {
		int l = s.length();
		char c = s.charAt(0);
		boolean neg = false;
		int firstIndex = 0;
		if(NEGATIVE_INDEXES) {
			if(c=='-') {
				neg = true;
				firstIndex++;
			}
		}
		if(firstIndex>=l) {
			return null;
		}
		if(s.charAt(firstIndex)=='0') {
			// "0" only; "01", "-0" and "-01" are not canonical integers
			return (!neg && l==1) ? 0 : null;
		}
		long res = 0;
		for(int i=firstIndex; i<l; i++) {
			c = s.charAt(i);
			if(c<'0' || c>'9') {
				return null;
			}
			res = res*10 + (c-'0');
			if(res>Integer.MAX_VALUE) {
				return null; // too large for an index: it is an object key ("12345678901")
			}
		}
		return (int)(neg ? -res : res);
	}
	private static String unescape(String s) {
		int pos = s.indexOf('~');
		if(pos<0) {
			return s;
		}
		int length = s.length();
		StringBuilder b = new StringBuilder(length+8);
		for(int i=0; i<length; i++) {
			char c = s.charAt(i);
			if(c=='~') {
				if(i+1<length) {
					char c2 = s.charAt(++i);
					if(c2=='0') {
						b.append("~");
					} else if(c2=='1') {
						b.append("/");
					} else {
						throw new JsonException(null,"Invalid JSON pointer escape sequence ~{0} in {1}",c2,s);
					}
				} else {
					throw new JsonException(null,"Invalid JSON pointer escape sequence ~ at the end of {0}",s);
				}
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}
	private static String escape(String s) {
		int length = s.length();
		StringBuilder b = new StringBuilder(length+8);
		for(int i=0; i<length; i++) {
			char c = s.charAt(i);
			if(c=='~') {
				b.append("~0");
			} else if(c=='/') {
				b.append("~1");
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}
	
	//
	// JsonPointer JsonPath parser
	//

	private static class JsonPathPointerParser extends StringMatcher {
		
		private JsonPathPointerParser(String seq) {
			super(seq,0);
		}
		
		@Override
		protected BaseException _createException(Throwable cause, String message) {
			return new JsonException(cause, message);			
		}

		public JsonPointer createPointer() {
			if(isEmpty()) {
				return JsonPointer.EMPTY;
			}
			if(!match("$")) {
				throw createException(null,ptr,"The string does not start with $ and is not a JSON path");
			}
			if(isEmpty()) {
				return JsonPointer.EMPTY;
			}
			List<Object> parts = new ArrayList<>();
			parsePath(parts);
			return JsonPointer.createJsonPath(parts.toArray());
		}
		
		private void parsePath(List<Object> parts) {
			if(isEmpty()) {
				return;
			}
			if(match('.')) {
				// '.name' is always a member name, even when numeric
				String id = readIdentifier();
				parts.add(id);
				parsePath(parts);
				return;
			} else if(match('[')) {
				if(ptr<length) {
					char c = seq.charAt(ptr);
					if(c=='\'' || c=='\"') {
						// A quoted name is a member name, even "0"
						String s = readQuotedString();
						parts.add(s);
					} else {
						int v = readInteger();
						parts.add(v);
					}
					if(match(']')) {
						parsePath(parts);
						return;
					}
				}
			}
			throw createException(null, ptr, "Expecting '[' or '.' part");
		}

		@Override
		protected boolean isIdentifierStart(char ch) {
			return JsonUtil.isIdentifierStart(ch);  
		}
		@Override
		protected boolean isIdentifierPart(char ch) {
			return JsonUtil.isIdentifierPart(ch);  
		}
	}
}
