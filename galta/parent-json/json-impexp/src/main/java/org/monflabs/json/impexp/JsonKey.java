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
package org.monflabs.json.impexp;

import org.monflabs.util.StringFormat;

/**
 * Key of a {@link JsonContent}: an optional collection and an id.
 * <p>
 * A null collection or id is normalized to an empty string.
 * <p>
 * {@link #keyString()} encodes the key as a single string, {@code collection!!id} (or
 * just {@code id} without a collection), that {@link #parse(String)} decodes back. To
 * keep it unambiguous, a {@code !} or a {@code \} in the collection or the id is
 * escaped with a {@code \}, so a key made of plain identifiers is encoded as is.
 */
public record JsonKey(String collection, String id) {
	
	public static final String KEY_SEPARATOR = "!!";
	
	public JsonKey {
		collection = collection!=null ? collection : "";
		id = id!=null ? id : "";
	}
	
	public static JsonKey of(String collection, String id) {
		return new JsonKey(collection, id);
	}
	
	/**
	 * Decodes a key encoded by {@link #keyString()}. A string without an (unescaped)
	 * separator is an id without a collection.
	 */
	public static JsonKey parse(String key) {
		StringBuilder b = new StringBuilder(key.length());
		String collection = null;
		int len = key.length();
		for(int i=0; i<len; i++) {
			char c = key.charAt(i);
			if(c=='\\' && i+1<len) {
				b.append(key.charAt(++i));
			} else if(c=='!' && collection==null && key.startsWith(KEY_SEPARATOR, i)) {
				collection = b.toString();
				b.setLength(0);
				i++;
			} else {
				b.append(c);
			}
		}
		return of(collection,b.toString());
	}

	@Override
	public String toString() {
		return StringFormat.format("{0}:{1}", collection, id );
	}

	public String keyString() {
		if(collection.isEmpty()) {
			return escape(id);
		} else {
			return escape(collection) + KEY_SEPARATOR + escape(id);
		}
	}
	
	private static String escape(String s) {
		if(s.indexOf('!')<0 && s.indexOf('\\')<0) {
			return s;
		}
		StringBuilder b = new StringBuilder(s.length()+8);
		for(int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if(c=='!' || c=='\\') {
				b.append('\\');
			}
			b.append(c);
		}
		return b.toString();
	}

	public String getCollection() {
		return collection;
	}

	public String getId() {
		return id;
	}
}
