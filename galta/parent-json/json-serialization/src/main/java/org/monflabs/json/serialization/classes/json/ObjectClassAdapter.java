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
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter of the values declared as <code>Object</code> (a raw <code>List</code>, a
 * <code>Map&lt;String,Object&gt;</code>...).
 * <p>
 * A JSON value (null, string, boolean, number, JSON object or array) is kept as is. Another
 * value (a POJO, a Java collection...) is serialized with the adapter of its class, when the
 * registry has one, rather than being passed as is (and later written with its
 * <code>toString()</code>). A JSON value is read back as is.
 */
public class ObjectClassAdapter extends BaseClassAdapter {

	private JsonRegistry registry;

	public ObjectClassAdapter() {
		super(Object.class);
	}

	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.registry = registry;
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value==null || registry==null || isJsonValue(value)) {
			return value;
		}
		ClassAdapter a;
		try {
			a = registry.findAdapter(value.getClass());
		} catch(JsonException ex) {
			// No adapter for this class: kept as is
			return value;
		}
		return a==this ? value : a.serialize(value);
	}

	private static boolean isJsonValue(Object value) {
		return value instanceof String || value instanceof Number || value instanceof Boolean
			|| value instanceof JsonObject || value instanceof JsonArray;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		return jsonValue;
	}
}
