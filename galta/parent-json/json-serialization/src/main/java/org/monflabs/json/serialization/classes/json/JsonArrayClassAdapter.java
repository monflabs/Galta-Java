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

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter of the <code>JsonArray</code> values (fields declared as <code>JsonArray</code>).
 * <p>
 * The value is deep copied in both directions, so the JSON produced by a serialization and
 * the objects produced by a deserialization do not share mutable state.
 */
public class JsonArrayClassAdapter extends BaseClassAdapter {

	public JsonArrayClassAdapter() {
		super(JsonArray.class);
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		return JsonObjectClassAdapter.copy(check(value));
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		return JsonObjectClassAdapter.copy(check(jsonValue));
	}

	private static Object check(Object value) {
		if(value==null || value instanceof JsonArray) {
			return value;
		}
		throw new JsonException(null, "Cannot convert {0} to a JsonArray: a JSON array is expected", SerializationException.describe(value));
	}
}
