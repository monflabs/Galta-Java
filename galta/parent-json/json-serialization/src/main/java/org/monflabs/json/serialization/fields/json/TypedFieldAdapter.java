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

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.fields.GenericTypeResolver;
import org.monflabs.json.serialization.fields.ReflectionFieldAdapter;

/**
 * Typed field.
 * 
 * This holds a reference to a typed field like the field 't' bellow:
 * 
 * class Container&lt;T&gt; {
 *   T t;
 * }
 * 
 * The type variable is resolved against the adapted class: it is either one of
 * its own type parameters (bound at runtime through the generic parameters), or
 * a type parameter of a superclass, bound through the generic superclass chain
 * (class Sub extends Container&lt;String&gt;).
 * 
 * @author priand
 *
 */

public class TypedFieldAdapter extends ReflectionFieldAdapter {
	
	private String typeName;
	private GenericTypeResolver.ResolvedType resolvedType;
	
	public TypedFieldAdapter(Field field, String typeName) {
		super(field);
		this.typeName = typeName;
	}
	
	public String getTypeName() {
		return typeName;
	}
	
	@Override
	protected void _init(JsonRegistry registry, ClassAdapter parent) {
		Class<?> context = parent!=null ? parent.getAdaptedClazz() : null;
		resolvedType = GenericTypeResolver.resolve(field.getGenericType(), context, registry);
	}
	
	@Override
	public Object readProperty(Object _this, ClassAdapter[] genericParams) {
		try {
			Object value = field.get(_this);
			if(value!=null) {
				Object v = resolvedType.resolve(genericParams).serialize(value);
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
				Object v = resolvedType.resolve(genericParams).deserialize(jsonValue);
				field.set(_this, v);
			}
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		}
	}
}
