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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.serialization.FieldAdapter;
import org.monflabs.json.serialization.fields.json.ObjectFieldAdapter;
import org.monflabs.json.serialization.fields.json.TypedFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.ScalarFieldAdapter;

//
// http://www.java2s.com/Code/Java/Reflection/Awrapperaroundreflectiontoresolvegenerics.htm
//
public abstract class ReflectionFieldAdapter extends BaseFieldAdapter {
	
	/**
	 * Create the adapter of a field. A new adapter is created at each call: adapters are bound
	 * to the registry of the class adapter that owns them, and are not shared (a JVM-wide cache
	 * keyed by Field would also keep the classes, and their class loader, alive).
	 */
	public static FieldAdapter create(Field f) {
		ReflectionFieldAdapter ad;
		Class<?> type = f.getType();

		// If the type is a y
		Type gtype = f.getGenericType();
		if(gtype instanceof TypeVariable<?> tv) {
			String typeName = tv.getName();
			return new TypedFieldAdapter(f, typeName);
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
			return new ParameterizedFieldAdapter(ad,types);
		}
		if(type.getTypeParameters().length>0) {
			// Raw use of a generic type (List, Map...): the parameters resolve to Object
			return new ParameterizedFieldAdapter(ad,type.getTypeParameters());
		}
		
		return ad;
	}
	
	protected Field field;
	
	public ReflectionFieldAdapter(Field field) {
		this.field = field;
		field.setAccessible(true);
	}
	
	public Field getField() {
		return field;
	}
}