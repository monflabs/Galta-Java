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
package org.monflabs.json.serialization.classes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.FieldAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.LambdaFieldAdapter;
import org.monflabs.json.serialization.fields.BaseFieldAdapter;
import org.monflabs.json.serialization.fields.ReflectionFieldAdapter;
import org.monflabs.util.ObjectBuilder;

/**
 * Java POJO adapter.
 *  
 * @author priand
 *
 */
public class SimpleClassAdapter<C> extends BaseClassAdapter {
	
	/**
	 * Add the fields of a class and its superclasses, in declaration order, the superclass
	 * fields first. A field hidden by a field with the same name in a subclass is ignored, as
	 * well as the names already defined in the map.
	 */
	private static void readReflection(Class<?> clazz, Map<String,FieldAdapter> map, Predicate<Field> filter) {
		List<Class<?>> chain = new ArrayList<>();
		for(Class<?> c=clazz; c!=null; c=c.getSuperclass()) {
			chain.add(c);
		}
		// The most derived eligible field wins for a given name
		Map<String,Field> winners = new HashMap<>();
		for(Class<?> c: chain) {
			for(Field f: c.getDeclaredFields()) {
				int mod = f.getModifiers();
				if(Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
					// serialVersionUID, constants, outer class references (this$0)...
					continue;
				}
				if(!map.containsKey(f.getName()) && !winners.containsKey(f.getName())) {
					if(filter==null || filter.test(f)) {
						winners.put(f.getName(), f);
					}
				}
			}
		}
		for(int i=chain.size()-1; i>=0; i--) {
			for(Field f: chain.get(i).getDeclaredFields()) {
				if(f.equals(winners.get(f.getName()))) {
					map.put(f.getName(), ReflectionFieldAdapter.create(f));
				}
			}
		}
	}
	
	public static class Builder<C> extends ObjectBuilder<SimpleClassAdapter<C>> {
		
		private Class<C> clazz;
		private Supplier<Object> factory;
		// Declaration order is the JSON key order
		private Map<String,FieldAdapter> fields = new LinkedHashMap<>();
		
		private Builder(Class<C> clazz) {
			this.clazz = clazz;
		}
		public Builder<C> factory(Supplier<Object> factory) {
			this.factory = factory;
			return this;
		}
		public Builder<C> reflection() {
			reflection(null);
			return this;
		}
		public Builder<C> reflection(Predicate<Field> filter) {
			readReflection(clazz,fields,filter);
			return this;
		}
		public Builder<C> add(String name, FieldAdapter adapter) {
			fields.put(name, adapter);
			return this;
		}
		public <V> Builder<C> add(String name, LambdaFieldAdapter.PropertyReader<C,V> reader, LambdaFieldAdapter.PropertyWriter<C,V> writer) {
			fields.put(name, new LambdaFieldAdapter<C,V>(reader, writer));
			return this;
		}
		
		@Override
		public SimpleClassAdapter<C> _build() {
			return new SimpleClassAdapter<C>(this);
		}
	}
	
	public static <C> Builder<C> newBuilder(Class<C> clazz) {
		return new Builder<C>(clazz);
	}
	
	private Supplier<Object> factory;
	private Map<String,FieldAdapter> fields;
	private JsonRegistry registry;

	public SimpleClassAdapter(Builder<C> b) {
		super(b.clazz);
		
		this.factory = b.factory;
		this.fields = b.fields;
	}
	
	/**
	 * Bind the field adapters to a registry. An adapter is bound to the first registry that
	 * initializes it: to use the same class in several registries, build one adapter for each.
	 */
	@Override
	public synchronized void init(JsonRegistry registry) {
		super.init(registry);
		if(this.registry!=null) {
			if(this.registry!=registry) {
				throw new JsonException(null,"The adapter of {0} is already used by another registry",getAdaptedClazz().getName());
			}
			return;
		}
		this.registry = registry;
		
		for(FieldAdapter a: fields.values()) {
			if(a instanceof BaseFieldAdapter ba) {
				ba.init(registry,this);
			}
		}
	}
	
	@Override
	public Object serialize(Object _this, ClassAdapter[] genericParams) {
		if(_this==null) {
			return null;
		}
		CycleGuard.enter(_this);
		try {
			JsonObject o = JsonObject.create();
			for(Map.Entry<String,FieldAdapter> e: fields.entrySet()) {
				Object v = e.getValue().readProperty(_this, genericParams);
				o.put(e.getKey(), v);
			}
			return o;
		} finally {
			CycleGuard.exit(_this);
		}
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		if(jsonValue instanceof JsonObject o) {
			Object _this = create();
			for(Map.Entry<String,Object> e: o.entrySet()) {
				Object value = e.getValue();
				if(value==null) {
					continue;
				}
				
				FieldAdapter fa = fields.get(e.getKey()); 
				if(fa==null) {
					throw new JsonException(null,"Object doesn't have a field named {0}",e.getKey());
				}

				fa.writeProperty(_this,  value, genericParams);
			}
			return finalize(_this);
		}
		throw new IllegalStateException();
	}
	
	protected Object create() {
		if(factory!=null) {
			return factory.get();
		}
		try {
			Constructor<?> ctor = getAdaptedClazz().getDeclaredConstructor();
			ctor.setAccessible(true);
			return ctor.newInstance();
		} catch (Exception e) {
			throw new JsonException(e);
		}
	}
	protected Object finalize(Object o) {
		return o;
	}
}
