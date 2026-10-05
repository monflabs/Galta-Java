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
		if(path.length()>MAX_CACHED_LENGTH) {
			return parse(path, path.charAt(0)=='/' ? 1 : 0);
		}
		int h = path.hashCode();
		int slot = (h ^ (h>>>16)) & (CACHE_SIZE-1);
		Parsed e = PARSED[slot];
		if(e!=null && e.text().equals(path)) {
			return e.pointer();
		}
		JsonPointer pointer = parse(path, path.charAt(0)=='/' ? 1 : 0);
		PARSED[slot] = new Parsed(path, pointer);
		return pointer;
	}

	// The last parsed pointers: the same pointer strings come back often (configuration,
	// $ref, patches). Pointers are immutable, so they can be shared. Direct-mapped and
	// lock-free: an entry has final fields only, and a collision replaces it.
	private static final int CACHE_SIZE = 256;
	private static final int MAX_CACHED_LENGTH = 256;
	private record Parsed(String text, JsonPointer pointer) {}
	private static final Parsed[] PARSED = new Parsed[CACHE_SIZE];

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
	// The parts of a pointer created from its parts or parsed (final fields are safely
	// published without the cost of a volatile write), else built on demand in cache
	private final Object[] fixedParts;
	private volatile Object[] cache;
	// How each part addresses an array (see arrayAddress()): known when parsed, else
	// computed on demand
	private final int[] fixedIndexes;
	private volatile int[] indexes;
	// Computed on demand (0: not computed yet), a benign race
	private int hash;
	private String string;

	private JsonPointer(Object[] parts, boolean memberNames) {
		this(parts, null, memberNames);
	}
	private JsonPointer(Object[] parts, int[] indexes, boolean memberNames) {
		this.parent = null;
		this.fixedParts = parts;
		this.fixedIndexes = indexes;
		this.size = parts.length;
		this.last = size>0 ? parts[size-1] : null;
		this.memberNames = memberNames;
	}
	private JsonPointer(JsonPointer parent, Object last) {
		this.parent = parent;
		this.fixedParts = null;
		this.fixedIndexes = null;
		this.last = last;
		this.size = parent.size+1;
		this.memberNames = parent.memberNames;
	}

	private Object[] parts() {
		Object[] p = fixedParts;
		if(p!=null) {
			return p;
		}
		p = cache;
		if(p==null) {
			p = new Object[size];
			JsonPointer c = this;
			int i = size-1;
			Object[] cc;
			while((cc=c.fixedParts!=null ? c.fixedParts : c.cache)==null) {
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

	// The array addressing of a part: an index (negative: from the end, extension),
	// APPEND for "-" (past the last element) or NONE (not an index)
	private static final int NONE = Integer.MIN_VALUE;
	private static final int APPEND = Integer.MIN_VALUE+1;

	private int[] knownIndexes() {
		int[] idx = fixedIndexes;
		return idx!=null ? idx : indexes;
	}

	private int[] indexes() {
		int[] idx = fixedIndexes;
		if(idx!=null) {
			return idx;
		}
		idx = indexes;
		if(idx==null) {
			Object[] parts = parts();
			idx = new int[parts.length];
			for(int i=0; i<parts.length; i++) {
				idx[i] = arrayAddress(parts[i], memberNames);
			}
			indexes = idx;
		}
		return idx;
	}

	// The member name of a part: the reference token
	private static String name(Object part) {
		return part instanceof String s ? s : part.toString();
	}

	// The hash of the reference token of a part, the same as name(part).hashCode()
	// without creating the string of an index
	private static int tokenHash(Object part) {
		if(part instanceof String s) {
			return s.hashCode();
		}
		if(part==LASTKEY) {
			return '-';
		}
		int n = (Integer)part;
		if(n==0) {
			return '0';
		}
		long v = n;
		boolean negative = v<0;
		if(negative) {
			v = -v;
		}
		int h = 0;
		int power = 1;
		while(v>0) {
			h += ('0'+(int)(v%10))*power;
			power *= 31;
			v /= 10;
		}
		if(negative) {
			h += '-'*power;
		}
		return h;
	}

	// Whether two parts are the same reference token: the index 0 and the member "0",
	// "-" and the past-the-end part
	private static boolean sameToken(Object p1, Object p2) {
		if(p1==p2) {
			return true;
		}
		if(p1 instanceof String s1) {
			return p2 instanceof String s2 ? s1.equals(s2) : sameToken(s1, p2);
		}
		if(p2 instanceof String s2) {
			return sameToken(s2, p1);
		}
		return p1.equals(p2);
	}
	private static boolean sameToken(String s, Object nonString) {
		if(nonString==LASTKEY) {
			return s.length()==1 && s.charAt(0)=='-';
		}
		// A canonical integer, as an index is written
		if(s.isEmpty()) {
			return false;
		}
		char c = s.charAt(0);
		if((c<'0' || c>'9') && c!='-') {
			return false;
		}
		Integer i = parseInteger(s);
		return i!=null && i.equals(nonString);
	}

	// How a part addresses an array. For a JSON pointer, it only depends on the text of
	// the reference token: "0", "-" or "-1" behave the same as a String or as parsed.
	// For a pointer from a JSON Path, only the Integer parts are indexes.
	private static int arrayAddress(Object p, boolean memberNames) {
		if(p==LASTKEY) {
			return APPEND;
		}
		if(p instanceof Integer n) {
			return n>APPEND ? n : NONE;
		}
		if(!memberNames && p instanceof String s && !s.isEmpty()) {
			char c = s.charAt(0);
			if(c=='-' && s.length()==1) {
				return APPEND;
			}
			if((c>='0' && c<='9') || (NEGATIVE_INDEXES && c=='-')) {
				Integer i = parseInteger(s);
				if(i!=null && i>APPEND) {
					return i;
				}
			}
		}
		return NONE;
	}

	// The index of an existing element of an array of that size, or -1
	private static int existingIndex(int address, int size) {
		if(address<0) {
			if(address<=APPEND || !NEGATIVE_INDEXES) {
				return -1;
			}
			address += size;
			if(address<0) {
				return -1;
			}
		}
		return address<size ? address : -1;
	}
	// The insertion index in an array of that size (size appends), or -1
	private static int insertionIndex(int address, int size) {
		if(address==APPEND) {
			return size;
		}
		if(address<0) {
			if(address==NONE || !NEGATIVE_INDEXES) {
				return -1;
			}
			address += size;
			if(address<0) {
				return -1;
			}
		}
		return address<=size ? address : -1;
	}

	public boolean contains(JsonPointer p) {
		if(size>=p.size) {
			Object[] parts = parts();
			Object[] pparts = p.parts();
			for(int i=0; i<pparts.length; i++) {
				if(!sameToken(pparts[i], parts[i])) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	// Two pointers are equal when they have the same reference tokens: the index 0 and
	// the member "0" are the same token
	@Override
	public int hashCode() {
		int h = hash;
		if(h==0) {
			// Order dependent: /a/b and /b/a must not collide systematically
			h = 1;
			Object[] parts = parts();
			for(int i=0; i<parts.length; i++) {
				h = 31*h + tokenHash(parts[i]);
			}
			hash = h;
		}
		return h;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o==this) {
			return true;
		}
		if(o instanceof JsonPointer p && size==p.size) {
			int h1 = hash, h2 = p.hash;
			if(h1!=0 && h2!=0 && h1!=h2) {
				return false;
			}
			Object[] parts = parts();
			Object[] pparts = p.parts();
			for(int i=size-1; i>=0; i--) {
				if(!sameToken(parts[i], pparts[i])) {
					return false;
				}
			}
			return true;
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
		String str = string;
		if(str==null) {
			Object[] parts = parts();
			StringBuilder b = new StringBuilder(size*8);
			for(int i=0; i<parts.length; i++) {
				b.append('/');
				Object p = parts[i];
				if(p instanceof String name) {
					escape(b, name);
				} else if(p instanceof Integer n) {
					b.append(n.intValue());
				} else {
					b.append('-');
				}
			}
			string = str = b.toString();
		}
		return str;
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
		// A pointer built child by child is often read once: its array addressing is
		// computed on the way rather than stored
		int[] indexes = knownIndexes();
		for(int i=0; i<parts.length; i++) {
			if(v instanceof JsonObject o) {
				String k = name(parts[i]);
				Object n = o.get(k);
				if(n==null && !o.containsKey(k)) {
					return false;
				}
				v = n;
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(indexes!=null ? indexes[i] : arrayAddress(parts[i], memberNames), a.size());
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
		// A pointer built child by child is often read once: its array addressing is
		// computed on the way rather than stored
		int[] indexes = knownIndexes();
		for(int i=0; i<parts.length; i++) {
			if(v instanceof JsonObject o) {
				// A missing member and a null value both read as null: one lookup
				v = o.get(name(parts[i]));
				if(v==null) {
					return null;
				}
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(indexes!=null ? indexes[i] : arrayAddress(parts[i], memberNames), a.size());
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
		int[] indexes = indexes();
		int lastIndex = indexes.length-1;
		for(int i=0; i<=lastIndex; i++) {
			if(v instanceof JsonObject o) {
				if(i==lastIndex) {
					o.put(name(parts[i]), value);
					return true;
				}
				v = o.get(name(parts[i]));
			} else if(v instanceof JsonArray a) {
				if(i==lastIndex) {
					// index == size appends (RFC 6902)
					int index = insertionIndex(indexes[i], a.size());
					if(index<0) {
						return false;
					}
					if(index==a.size()) {
						a.add(value);
					} else {
						a.add(index,value);
					}
					return true;
				}
				int index = existingIndex(indexes[i], a.size());
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
	 * than padding the array with nulls (RFC 6902 'add' semantics). Nothing is changed
	 * when the value can't be set.
	 */
	public boolean setValue(Object v, Object value) {
		Object[] parts = parts();
		int[] indexes = indexes();
		int lastIndex = indexes.length-1;
		for(int i=0; i<=lastIndex; i++) {
			if(v instanceof JsonObject o) {
				String k = name(parts[i]);
				if(i==lastIndex) {
					o.put(k, value);
					return true;
				}
				v = o.get(k);
				if(v==null) {
					if(!canCreate(parts, indexes, i)) {
						return false;
					}
					v = createPart(parts, i);
					o.put(k, v);
				}
			} else if(v instanceof JsonArray a) {
				int index = insertionIndex(indexes[i], a.size());
				if(index<0) {
					return false;
				}
				if(i==lastIndex) {
					if(index==a.size()) {
						a.add(value);
					} else {
						a.set(index, value);
					}
					return true;
				}
				if(index==a.size()) {
					if(!canCreate(parts, indexes, i)) {
						return false;
					}
					v = createPart(parts, i);
					a.add(v);
				} else {
					v = a.get(index);
					if(v==null) {
						if(!canCreate(parts, indexes, i)) {
							return false;
						}
						v = createPart(parts, i);
						a.set(index, v);
					}
				}
			} else {
				return false;
			}
		}
		return false;
	}
	// The container created for the part at index: the next part determines its type
	private static Object createPart(Object[] parts, int index) {
		if(parts[index+1] instanceof Number) {
			return JsonArray.create();
		} else {
			return JsonObject.create();
		}
	}
	// Whether the containers created from the part at index on can hold the value: a new
	// array is empty, so the part addressing it must be 0 or "-"
	private static boolean canCreate(Object[] parts, int[] indexes, int index) {
		for(int j=index+1; j<parts.length; j++) {
			if(parts[j] instanceof Number && indexes[j]!=0 && indexes[j]!=APPEND) {
				return false;
			}
		}
		return true;
	}
	
	
	/**
	 * Replace the value for the given pointer
	 * The path must exist, and it return false if the path did not exist.
	 */
	public boolean replace(Object v, Object value) {
		Object[] parts = parts();
		int[] indexes = indexes();
		int lastIndex = indexes.length-1;
		for(int i=0; i<=lastIndex; i++) {
			if(v instanceof JsonObject o) {
				String k = name(parts[i]);
				Object n = o.get(k);
				if(n==null && !o.containsKey(k)) {
					return false;
				}
				if(i==lastIndex) {
					o.put(k, value);
					return true;
				}
				v = n;
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(indexes[i], a.size());
				if(index<0) {
					return false;
				}
				if(i==lastIndex) {
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
		int[] indexes = indexes();
		int lastIndex = indexes.length-1;
		for(int i=0; i<=lastIndex; i++) {
			if(v instanceof JsonObject o) {
				String k = name(parts[i]);
				Object n = o.get(k);
				if(n==null && !o.containsKey(k)) {
					return false;
				}
				if(i==lastIndex) {
					o.remove(k);
					return true;
				}
				v = n;
			} else if(v instanceof JsonArray a) {
				int index = existingIndex(indexes[i], a.size());
				if(index<0) {
					return false;
				}
				if(i==lastIndex) {
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
	
	
	//
	// JsonPointer parser
	//
	
	// Parses the reference tokens from start: each token is scanned once, and the
	// array addressing of the parts is computed with them
	private static JsonPointer parse(String s, int start) {
		int length = s.length();
		int count = 1;
		boolean escapes = false;
		for(int i=start; i<length; i++) {
			char c = s.charAt(i);
			if(c=='/') {
				count++;
			} else if(c=='~') {
				escapes = true;
			}
		}
		Object[] parts = new Object[count];
		int[] indexes = new int[count];
		int pos = start;
		for(int p=0; p<count; p++) {
			// The end of the token, and the hash of its text for the token cache
			int end = pos;
			int h = 0;
			for(char c; end<length && (c=s.charAt(end))!='/'; end++) {
				h = 31*h + c;
			}
			Object part;
			int address = NONE;
			int tokenLength = end-pos;
			char c = tokenLength>0 ? s.charAt(pos) : 0;
			if(tokenLength==1 && c=='-') {
				part = LASTKEY;
				address = APPEND;
			} else if((c>='0' && c<='9') || (NEGATIVE_INDEXES && c=='-')) {
				// An escaped token is never a number: parseInteger() rejects the '~'
				Integer i = parseInteger(s, pos, end);
				if(i!=null) {
					part = i;
					address = i>APPEND ? i : NONE;
				} else {
					part = escapes ? unescape(s, pos, end) : token(s, pos, end, h);
				}
			} else if(tokenLength==0) {
				part = "";
			} else {
				part = escapes ? unescape(s, pos, end) : token(s, pos, end, h);
			}
			parts[p] = part;
			indexes[p] = address;
			pos = end+1;
		}
		return new JsonPointer(parts, indexes, false);
	}
	// The last member names seen in parsed pointers: the same names come back (the keys of
	// a document), and reusing them saves creating the strings and computing their hash
	// when they are looked up. Direct-mapped and lock-free: strings are immutable.
	private static final int TOKEN_CACHE_SIZE = 512;
	private static final int MAX_CACHED_TOKEN = 32;
	private static final String[] TOKENS = new String[TOKEN_CACHE_SIZE];

	// h is the hash of the token, as String.hashCode() computes it
	private static String token(String s, int start, int end, int h) {
		int length = end-start;
		if(length>MAX_CACHED_TOKEN) {
			return s.substring(start, end);
		}
		int slot = (h ^ (h>>>16)) & (TOKEN_CACHE_SIZE-1);
		String t = TOKENS[slot];
		if(t!=null && t.length()==length && s.regionMatches(start, t, 0, length)) {
			return t;
		}
		t = s.substring(start, end);
		TOKENS[slot] = t;
		return t;
	}

	/**
	 * Parse a canonical integer: no leading zeros, no "-0". Anything else is a
	 * member name ("01", "-0", "-01").
	 */
	private static Integer parseInteger(String s) {
		return parseInteger(s, 0, s.length());
	}
	private static Integer parseInteger(String s, int start, int l) {
		char c = s.charAt(start);
		boolean neg = false;
		int firstIndex = start;
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
			return (!neg && l-start==1) ? 0 : null;
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
	private static String unescape(String str, int start, int end) {
		int pos = str.indexOf('~', start);
		if(pos<0 || pos>=end) {
			return str.substring(start, end);
		}
		String s = str.substring(start, end);
		int length = s.length();
		StringBuilder b = new StringBuilder(length);
		b.append(s, 0, pos-start);
		for(int i=pos-start; i<length; i++) {
			char c = s.charAt(i);
			if(c=='~') {
				if(i+1<length) {
					char c2 = s.charAt(++i);
					if(c2=='0') {
						b.append('~');
					} else if(c2=='1') {
						b.append('/');
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
	private static void escape(StringBuilder b, String s) {
		if(s.indexOf('~')<0 && s.indexOf('/')<0) {
			b.append(s);
			return;
		}
		int length = s.length();
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
