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
package org.monflabs.json.serialization.fields;

import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;

/**
 * Adapter of a record component.
 * <p>
 * The value is read with the accessor of the component. A record is immutable: the
 * values read from JSON are passed to its canonical constructor by the class adapter,
 * see {@link #toJava(Object, ClassAdapter[])}, and {@link #writeProperty} is not supported.
 * The generic type of the component is resolved like the type of a field.
 */
public class RecordComponentAdapter extends BaseFieldAdapter {

	private final RecordComponent component;
	private final int index;
	private final Method accessor;
	private JsonRegistry registry;
	private GenericTypeResolver.ResolvedType resolvedType;

	public RecordComponentAdapter(RecordComponent component, int index) {
		this.component = component;
		this.index = index;
		this.accessor = component.getAccessor();
		accessor.setAccessible(true);
	}

	public RecordComponent getComponent() {
		return component;
	}

	/**
	 * The position of the component, and of its parameter in the canonical constructor.
	 */
	public int getIndex() {
		return index;
	}

	@Override
	protected void _init(JsonRegistry registry, ClassAdapter parent) {
		this.registry = registry;
		Type type = component.getGenericType();
		if(type instanceof Class<?> c && c.isPrimitive()) {
			// The values are boxed anyway (accessor result, constructor arguments)
			type = MethodType.methodType(c).wrap().returnType();
		}
		Class<?> context = parent!=null ? parent.getAdaptedClazz() : null;
		resolvedType = GenericTypeResolver.resolve(type, context, registry);
	}

	@Override
	public Object readProperty(Object _this, ClassAdapter[] genericParams) {
		Object value;
		try {
			value = accessor.invoke(_this);
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		} catch(InvocationTargetException ex) {
			throw new JsonException(ex.getCause(), "Error while reading the record component {0}", component.getName());
		}
		if(value==null) {
			return null;
		}
		ClassAdapter declared = resolvedType.resolve(genericParams);
		return RuntimeAdapters.forValue(registry, declared, value).serialize(value);
	}

	/**
	 * Convert a JSON value to the value of the component.
	 */
	public Object toJava(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		return resolvedType.resolve(genericParams).deserialize(jsonValue);
	}

	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		throw new JsonException(null, "The component {0} of a record cannot be assigned, it is set by the constructor", component.getName());
	}
}
