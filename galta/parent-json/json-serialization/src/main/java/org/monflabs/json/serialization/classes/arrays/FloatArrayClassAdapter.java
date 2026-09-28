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
package org.monflabs.json.serialization.classes.arrays;

import org.monflabs.json.serialization.NumberConverter;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

public class FloatArrayClassAdapter extends BaseClassAdapter {
	
	public FloatArrayClassAdapter() {
		super(float[].class);
	}
	
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		float[] v = (float[])value;
		if(v!=null) {
			JsonArray a = JsonArray.create(v.length);
			for(int i=0; i<v.length; i++) {
				a.add(v[i]);
			}
			return a;
		}
		return null;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue instanceof JsonArray a) {
			float[] v = new float[a.size()];
			for(int i=0; i<v.length; i++) {
				v[i] = NumberConverter.toFloat(a.get(i));
			}
			return v;
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"JsonValue is not an array");
		}
	}
}
