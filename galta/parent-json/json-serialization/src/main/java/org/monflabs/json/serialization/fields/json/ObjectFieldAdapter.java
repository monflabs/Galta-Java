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

import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.fields.GenericTypeResolver;
import org.monflabs.json.serialization.fields.ReflectionFieldAdapter;
import org.monflabs.json.serialization.fields.RuntimeAdapters;

/**
 * Adapter of a field holding an object, converted by the registry adapter of the field type.
 * A JSON null assigns null (an empty <code>Optional</code> to an <code>Optional</code> field).
 */
public class ObjectFieldAdapter extends ReflectionFieldAdapter {

	private ClassAdapter adapter;
	private JsonRegistry registry;
	// Only the adapter of a generic type receives generic parameters
	private final boolean generic;

	public ObjectFieldAdapter(Field field) {
		super(field);
		this.generic = GenericTypeResolver.typeParameters(field.getType()).length>0;
	}

	@Override
	protected void _init(JsonRegistry registry, ClassAdapter parent) {
		this.adapter = registry.findAdapter(field.getType());
		this.registry = registry;
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
		Object value = get(_this);
		if(value!=null) {
			// A subclass of the declared type is serialized with its own adapter
			return RuntimeAdapters.forValue(registry, adapter, value).serialize(value, generic ? genericParams : null);
		}
		return null;
	}

	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		set(_this, jsonValue!=null ? adapter.deserialize(jsonValue, generic ? genericParams : null) : null);
	}
}
