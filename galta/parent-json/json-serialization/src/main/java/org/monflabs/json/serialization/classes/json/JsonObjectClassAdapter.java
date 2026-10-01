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
package org.monflabs.json.serialization.classes.json;

import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter of the <code>JsonObject</code> values (fields declared as <code>JsonObject</code>).
 * <p>
 * The value is deep copied in both directions, so the JSON produced by a serialization and
 * the objects produced by a deserialization do not share mutable state.
 */
public class JsonObjectClassAdapter extends BaseClassAdapter {

	public JsonObjectClassAdapter() {
		super(JsonObject.class);
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		return copy(check(value));
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		return copy(check(jsonValue));
	}

	private static Object check(Object value) {
		if(value==null || value instanceof JsonObject) {
			return value;
		}
		throw new JsonException(null, "Cannot convert {0} to a JsonObject: a JSON object is expected", SerializationException.describe(value));
	}

	/**
	 * A deep copy of a JSON value: the JSON objects and arrays are copied, the other values
	 * are immutable.
	 */
	public static Object copy(Object value) {
		if(value instanceof JsonObject o) {
			CycleGuard.enterRead();
			try {
				JsonObject c = JsonObject.create();
				for(Map.Entry<String,Object> e: o.entrySet()) {
					c.put(e.getKey(), copy(e.getValue()));
				}
				return c;
			} finally {
				CycleGuard.exitRead();
			}
		}
		if(value instanceof JsonArray a) {
			CycleGuard.enterRead();
			try {
				int len = a.size();
				JsonArray c = JsonArray.create(len);
				for(int i=0; i<len; i++) {
					c.add(copy(a.get(i)));
				}
				return c;
			} finally {
				CycleGuard.exitRead();
			}
		}
		return value;
	}
}
