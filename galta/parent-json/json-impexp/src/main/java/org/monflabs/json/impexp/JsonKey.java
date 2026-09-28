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

public final class JsonKey {
	
	public static final String KEY_SEPARATOR = "!!";
	
	public static JsonKey of(String collection, String id) {
		return new JsonKey(collection, id);
	}
	
	public static JsonKey parse(String key) {
		int pos = key.indexOf(KEY_SEPARATOR);
		if(pos>=0) {
			String collection = key.substring(0,pos);
			String id = key.substring(pos+KEY_SEPARATOR.length());
			return of(collection,id);
		} else {
			return of(null,key);
		}
	}


	private String collection;
	private String id;
	
	private JsonKey(String collection, String id) {
		this.collection = collection!=null ? collection : "";
		this.id = id!=null ? id : "";
	}
	
	@Override
	public int hashCode() {
		return collection.hashCode() + id.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof JsonKey k) {
			return collection.equals(k.getCollection()) && id.equals(k.getId()); 
		}
		return false;
	}
	
	@Override
	public String toString() {
		return StringFormat.format("{0}:{1}", getCollection(), getId() );
	}

	public String keyString() {
		if(this.collection.isEmpty()) {
			return id;
		} else {
			return collection + KEY_SEPARATOR + id;
		}
	}

	public String getCollection() {
		return collection;
	}

	public String getId() {
		return id;
	}
}
