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
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.fields.RuntimeAdapters;

/**
 * Adapter of an array of objects (<code>String[]</code>, <code>Item[]</code>,
 * <code>int[][]</code>...), serialized as a JSON array. An item whose class is a subclass of
 * the component type is serialized with the adapter of its own class, when the registry has
 * one.
 */
public class ObjectArrayClassAdapter extends BaseClassAdapter {
	
	private ClassAdapter adapter;
	private JsonRegistry registry;
	
	public ObjectArrayClassAdapter(ClassAdapter adapter) {
		this(Array.newInstance(adapter.getAdaptedClazz(),0).getClass(), adapter);
	}
	
	public ObjectArrayClassAdapter(Class<?> arrayClass, ClassAdapter adapter) {
		super(arrayClass);
		this.adapter = adapter;
	}
	
	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.registry = registry;
	}
	
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value!=null) {
			Object[] array = (Object[])value;
			int len = array.length;
			CycleGuard.enter(value);
			try {
				JsonArray a = JsonArray.create(len);
				for(int i=0; i<len; i++) {
					Object v = array[i];
					try {
						a.add(v!=null ? RuntimeAdapters.forValue(registry, adapter, v).serialize(v) : null);
					} catch(RuntimeException ex) {
						throw SerializationException.atIndex(ex, i);
					}
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
			Object[] v = (Object[])Array.newInstance(getAdaptedClazz().getComponentType(), len);
			CycleGuard.enterRead();
			try {
				for(int i=0; i<len; i++) {
					try {
						v[i] = adapter.deserialize(a.get(i));
					} catch(ArrayStoreException ex) {
						throw SerializationException.atIndex(new JsonException(ex, "Cannot store a {0} in a {1} array", ex.getMessage(), getAdaptedClazz().getComponentType().getName()), i);
					} catch(RuntimeException ex) {
						throw SerializationException.atIndex(ex, i);
					}
				}
			} finally {
				CycleGuard.exitRead();
			}
			return v;
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"Cannot deserialize {0} into a {1}: a JSON array is expected",SerializationException.describe(jsonValue),getAdaptedClazz().getSimpleName());
		}
	}
}
