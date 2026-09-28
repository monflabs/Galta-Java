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

import java.lang.reflect.Array;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

public class ObjectArrayClassAdapter extends BaseClassAdapter {
	
	private ClassAdapter adapter;
	
	public ObjectArrayClassAdapter(ClassAdapter adapter) {
		this(Array.newInstance(adapter.getAdaptedClazz(),0).getClass(), adapter);
	}
	
	public ObjectArrayClassAdapter(Class<?> arrayClass, ClassAdapter adapter) {
		super(arrayClass);
		this.adapter = adapter;
	}
	
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value!=null) {
			int len = Array.getLength(value);
			CycleGuard.enter(value);
			try {
				JsonArray a = JsonArray.create(len);
				for(int i=0; i<len; i++) {
					Object item = adapter.serialize(Array.get(value, i));
					a.add(item);
				}
				return a;
			} finally {
				CycleGuard.exit(value);
			}
		}
		return null;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue instanceof JsonArray a) {
			int len = a.size();
			Object v = Array.newInstance(getAdaptedClazz().getComponentType(), len);
			for(int i=0; i<len; i++) {
				Object item = adapter.deserialize(a.get(i));
				Array.set(v, i, item);
			}
			return v;
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"JsonValue is not an array");
		}
	}
}
