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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.monflabs.json.serialization.FieldAdapter;
import org.monflabs.json.serialization.fields.json.ObjectFieldAdapter;
import org.monflabs.json.serialization.fields.json.TypedFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.BigDecimalFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.BigIntegerFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.BooleanFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.ByteFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.DoubleFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.FloatFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.IntFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.LongFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.ShortFieldAdapter;
import org.monflabs.json.serialization.fields.primitives.StringFieldAdapter;

//
// http://www.java2s.com/Code/Java/Reflection/Awrapperaroundreflectiontoresolvegenerics.htm
//
public abstract class ReflectionFieldAdapter extends BaseFieldAdapter {
	
	private static final Map<Class<?>,Function<Field,ReflectionFieldAdapter>> adapterFactory = new HashMap<>();
	static {
		adapterFactory.put(Boolean.TYPE, (f) -> new BooleanFieldAdapter(f));
		adapterFactory.put(Byte.TYPE, (f) -> new ByteFieldAdapter(f));
		adapterFactory.put(Short.TYPE, (f) -> new ShortFieldAdapter(f));
		adapterFactory.put(Integer.TYPE, (f) -> new IntFieldAdapter(f));
		adapterFactory.put(Long.TYPE, (f) -> new LongFieldAdapter(f));
		adapterFactory.put(Float.TYPE, (f) -> new FloatFieldAdapter(f));
		adapterFactory.put(Double.TYPE, (f) -> new DoubleFieldAdapter(f));
		adapterFactory.put(BigInteger.class, (f) -> new BigIntegerFieldAdapter(f));
		adapterFactory.put(BigDecimal.class, (f) -> new BigDecimalFieldAdapter(f));
		adapterFactory.put(String.class, (f) -> new StringFieldAdapter(f));
	}
	
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
		
		Function<Field,ReflectionFieldAdapter> factory = adapterFactory.get(type);
		if(factory!=null) {
			ad = factory.apply(f);
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