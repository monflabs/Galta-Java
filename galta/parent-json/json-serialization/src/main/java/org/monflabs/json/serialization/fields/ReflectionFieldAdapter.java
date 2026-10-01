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
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.FieldAdapter;
import org.monflabs.json.serialization.fields.json.ObjectFieldAdapter;
import org.monflabs.json.serialization.fields.json.TypedFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.ScalarFieldAdapter;

/**
 * Base class of the adapters of the fields read by reflection.
 * <p>
 * The fields are made accessible, <code>private</code> and <code>final</code> fields
 * included: the values read from JSON are assigned directly, bypassing the constructors,
 * the setters and their checks.
 */
public abstract class ReflectionFieldAdapter extends BaseFieldAdapter {

	/**
	 * Create the adapter of a field. A new adapter is created at each call: adapters are bound
	 * to the registry of the class adapter that owns them, and are not shared (a JVM-wide cache
	 * keyed by Field would also keep the classes, and their class loader, alive).
	 */
	public static FieldAdapter create(Field f) {
		ReflectionFieldAdapter ad;
		Class<?> type = f.getType();

		// A type variable (T value) or a generic array (T[] values, List<String>[] lists)
		Type gtype = f.getGenericType();
		if(gtype instanceof TypeVariable<?> tv) {
			return new TypedFieldAdapter(f, tv.getName());
		}
		if(gtype instanceof GenericArrayType ga) {
			return new TypedFieldAdapter(f, ga.getTypeName());
		}

		// The primitives, String, BigInteger and BigDecimal are converted directly; the
		// boxed primitives go through the registry, like any class
		if(type.isPrimitive() || type==String.class || type==BigInteger.class || type==BigDecimal.class) {
			ad = new ScalarFieldAdapter(f);
		} else {
			ad = new ObjectFieldAdapter(f);
		}

		if(gtype instanceof ParameterizedType pt) {
			Type[] types = pt.getActualTypeArguments();
			TypeVariable<?>[] all = GenericTypeResolver.typeParameters(type);
			if(types.length<all.length) {
				// An inner class of a generic class: Outer<String>.Inner
				return new TypedFieldAdapter(f, pt.getTypeName());
			}
			return new ParameterizedFieldAdapter(ad,types);
		}
		TypeVariable<?>[] params = GenericTypeResolver.typeParameters(type);
		if(params.length>0) {
			// Raw use of a generic type (List, Map...): the parameters resolve to their bounds
			return new ParameterizedFieldAdapter(ad,params);
		}

		return ad;
	}

	protected Field field;

	public ReflectionFieldAdapter(Field field) {
		this.field = field;
		try {
			field.setAccessible(true);
		} catch(RuntimeException ex) {
			// InaccessibleObjectException: a field of a JDK class
			throw new JsonException(ex, "Cannot access the field {0} of {1}: register an adapter for this class", field.getName(), field.getDeclaringClass().getName());
		}
	}

	public Field getField() {
		return field;
	}

	/**
	 * Read the value of the field.
	 */
	protected Object get(Object _this) {
		try {
			return field.get(_this);
		} catch(IllegalAccessException|IllegalArgumentException ex) {
			throw new JsonException(ex, "Cannot read the field {0} of {1}", field.getName(), _this!=null ? _this.getClass().getName() : "null");
		}
	}

	/**
	 * Assign a value read from JSON to the field, checking its type.
	 */
	protected void set(Object _this, Object value) {
		Class<?> t = field.getType();
		if(value==null) {
			if(t.isPrimitive()) {
				throw new JsonException(null, "A null JSON value cannot be assigned to the {0} field {1}", t.getName(), field.getName());
			}
			if(t==Optional.class) {
				value = Optional.empty();
			}
		} else {
			Class<?> boxed = t.isPrimitive() ? MethodType.methodType(t).wrap().returnType() : t;
			if(!boxed.isInstance(value)) {
				throw new JsonException(null, "Cannot assign a {0} to the field {1} of type {2}", value.getClass().getName(), field.getName(), t.getName());
			}
		}
		try {
			field.set(_this, value);
		} catch(IllegalAccessException|IllegalArgumentException ex) {
			throw new JsonException(ex, "Cannot assign the field {0} of {1}", field.getName(), field.getDeclaringClass().getName());
		}
	}
}
