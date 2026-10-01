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
package org.monflabs.json.serialization.fields.json;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.fields.ReflectionFieldAdapter;
import org.monflabs.json.serialization.fields.RuntimeAdapters;

public class ObjectFieldAdapter extends ReflectionFieldAdapter {
	
	private ClassAdapter adapter;
	private JsonRegistry registry;
	
	public ObjectFieldAdapter(Field field) {
		super(field);
	}
	
	@Override
	protected void _init(JsonRegistry registry, ClassAdapter parent) {
		this.registry = registry;
		this.adapter = registry.findAdapter(field.getType());
	}
	
	public Type[] getGenericParams() {
		Type gtype = field.getGenericType();
		if(gtype instanceof ParameterizedType pt) {
			return pt.getActualTypeArguments();
		}
		return null;
	}
	
	public Type getGenericParam(int index) {
		Type[] genericParams = getGenericParams();
		if(genericParams!=null && index<genericParams.length) {
			return genericParams[index];
		}
		return null;
	}
	
	@Override
	public Object readProperty(Object _this, ClassAdapter[] genericParams) {
		try {
			Object value = field.get(_this);
			if(value!=null) {
				// A subclass of the declared type is serialized with its own adapter
				Object v = RuntimeAdapters.forValue(registry, adapter, value).serialize(value, genericParams);
				return v;
			}
			return null;
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		}
	}
	
	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		try {
			if(jsonValue!=null) {
				Object v = adapter.deserialize(jsonValue, genericParams);
				field.set(_this, v);
			}
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		}
	}
}