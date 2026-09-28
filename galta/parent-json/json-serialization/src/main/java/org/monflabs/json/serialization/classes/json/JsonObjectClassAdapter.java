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

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

public class JsonObjectClassAdapter extends BaseClassAdapter {
	
	public JsonObjectClassAdapter() {
		super(JsonObject.class);
	}
	
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		// Cast to ensure the proper type
		return (JsonObject)value;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		return (JsonObject)jsonValue;
	}
}
