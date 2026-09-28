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
 * 
 * @author priand
 */
public class JsonPointer {
	
	// This is a JSON pointer extension
	private static final boolean NEGATIVE_INDEXES = true;

	public static final JsonPointer EMPTY = new JsonPointer(new Object[]{});

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
		return new JsonPointer(parse(path));
	}

	/**
	 * Create a JSON pointer from its parts, each being a member name (String)
	 * or an array index (Integer).
	 */
	public static JsonPointer ofParts(Object... parts) {
		if(parts==null || parts.length==0) {
			return EMPTY;
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
		return new JsonPointer(p);
	}
	public static JsonPointer ofJsonPath(String path) {
		if(path==null) {
			return EMPTY;
		}
		JsonPathPointerParser p = new JsonPathPointerParser(path);
		return p.createPointer();
	}

	static JsonPointer createJsonPath(Object[] parts) {
		return new JsonPointer(parts);
	}
	
	private Object[] parts;

	private JsonPointer(Object[] parts) {
		this.parts = parts;
	}
	
	public boolean contains(JsonPointer p) {
		if(parts.length>=p.parts.length) {
			for(int i=0; i<p.parts.length; i++) {
				if(!samePart(p.parts[i],parts[i])) {
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
		for(int i=0; i<parts.length; i++) {
			h = 31*h + parts[i].toString().hashCode();
		}
		return h;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof JsonPointer p) {
			if(parts.length==p.parts.length) {
				for(int i=0; i<parts.length; i++) {
					if(!samePart(parts[i],p.parts[i])) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}
	
	public boolean isEmpty() {
		return parts.length==0;
	}
	
	public int size() {
		return parts.length;
	}
	
	public Object getLastPart() {
		if(parts.length>0) {
			return parts[parts.length-1];
		}
		return null;
	}
	
	public Object getPart(int index) {
		return parts[index];
	}
	
	public Object[] getParts() {
		return parts;
	}

	@Override
	public String toString() {
		return toJsonPointerString();
	}

	public String toJsonPointerString() {
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
	public String toJsonPathString(boolean dollarPrefix) {
		StringBuilder b = new StringBuilder();
		if(dollarPrefix) {
			b.append('$');
		}
		for(int i=0; i<parts.length; i++) {
			Object p = parts[i];
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
		if(parts.length==0) {
			throw new JsonException(null,"JsonPointer doesn't have a parent");
		}
		if(parts.length==1) {
			return EMPTY;
		}
		Object[] p = new Object[parts.length-1];
		System.arraycopy(parts,0,p,0,parts.length-1);
		return new JsonPointer(p);
	}

	/**
	 * Child pointer for an object member. The member is always kept as a name,
	 * even when it looks like a number ("0") or is "-".
	 */
	public JsonPointer getChild(String member) {
		Object[] p = new Object[parts.length+1];
		System.arraycopy(parts,0,p,0,parts.length);
		p[parts.length] = member;
		return new JsonPointer(p);
	}
	public JsonPointer getChild(int index) {
		if(index<0) {
			throw new JsonException(null,"Invalid negative index {0}",index);
		}
		Object[] p = new Object[parts.length+1];
		System.arraycopy(parts,0,p,0,parts.length);
		p[parts.length] = index;
		return new JsonPointer(p);
	}
	
	public boolean exists(Object v) {
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(!o.containsKey(ks)) {
					return false;
				}
				v = o.get(ks);
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(a, key);
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
		for(int i=0; i<parts.length; i++) {
			Object key = parts[i];
			if(v instanceof JsonObject o) {
				String ks = key.toString();
				if(!o.containsKey(ks)) {
					return null;
				}
				v = o.get(ks);
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(a, key);
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
						int index = partIndex(a, key);
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
				int index = existingIndex(a, key);
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
				int index = key==LASTKEY ? a.size() : partIndex(a, key);
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
				int index = existingIndex(a, key);
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
				int index = existingIndex(a, key);
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
	private static int partIndex(JsonArray a, Object key) {
		if(key==LASTKEY) {
			return Integer.MIN_VALUE;
		}
		if(key instanceof Integer n) {
			return n;
		}
		if(key instanceof String s && !s.isEmpty()) {
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
	private static int existingIndex(JsonArray a, Object key) {
		int index = partIndex(a, key);
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
        return splitString( null, 0, s, 0 );
    }
    private static Object[] splitString(Object[] result, int count, String s, int pos) {
        int newPos = s.indexOf('/',pos);
        if( newPos>=0 ) {
            result = splitString( null, count+1, s, newPos+1 );
            result[count] = partValue(s.substring( pos, newPos));
        } else {
            result = new Object[count+1];
            result[count] = partValue(s.substring( pos ));
        }
        return result;
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
