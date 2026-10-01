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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.fields.BaseFieldAdapter;
import org.monflabs.json.serialization.fields.RecordComponentAdapter;
import org.monflabs.json.serialization.fields.ReflectionFieldAdapter;
import org.monflabs.util.ObjectBuilder;

/**
 * Java POJO adapter.
 * <p>
 * A regular class is created with its no-arg constructor (or the factory), then its fields
 * are assigned. A record is serialized through the accessors of its components, and
 * created with its canonical constructor: a component missing from the JSON gets null, or
 * the default value of its primitive type.
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
		if(clazz.isRecord()) {
			readRecord(clazz, map, filter);
			return;
		}
		if(!isReflectable(clazz)) {
			throw new JsonException(null, "Cannot read the fields of {0} by reflection, its package is not open (a JDK class?): register an adapter for this class", clazz.getName());
		}
		// The hierarchy walk stops at the first class that cannot be reflected (a JDK class,
		// like Exception or AbstractList): its fields are not serialized
		List<Class<?>> chain = new ArrayList<>();
		for(Class<?> c=clazz; c!=null && c!=Object.class && isReflectable(c); c=c.getSuperclass()) {
			chain.add(c);
		}
		// The most derived field wins for a given name, even when the filter rejects it: it
		// then hides the superclass fields with the same name
		Map<String,Field> winners = new HashMap<>();
		Set<String> seen = new HashSet<>();
		for(Class<?> c: chain) {
			for(Field f: c.getDeclaredFields()) {
				int mod = f.getModifiers();
				if(Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
					// serialVersionUID, constants, outer class references (this$0)...
					continue;
				}
				if(!map.containsKey(f.getName()) && seen.add(f.getName())) {
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

	/**
	 * True if the fields of a class can be made accessible: its package is open to this
	 * module (always true for a class on the class path, false for the JDK classes).
	 */
	private static boolean isReflectable(Class<?> c) {
		return c.getModule().isOpen(c.getPackageName(), SimpleClassAdapter.class.getModule());
	}

	/**
	 * Add the components of a record, in declaration order. The filter is applied to the
	 * private field of each component.
	 */
	private static void readRecord(Class<?> clazz, Map<String,FieldAdapter> map, Predicate<Field> filter) {
		RecordComponent[] components = clazz.getRecordComponents();
		for(int i=0; i<components.length; i++) {
			RecordComponent rc = components[i];
			if(map.containsKey(rc.getName())) {
				continue;
			}
			if(filter!=null) {
				try {
					if(!filter.test(clazz.getDeclaredField(rc.getName()))) {
						continue;
					}
				} catch(NoSuchFieldException ex) {
					// Cannot happen: a component has a field
				}
			}
			map.put(rc.getName(), new RecordComponentAdapter(rc, i));
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
		/**
		 * Add a property defined by lambdas. The reader returns a JSON value (or a value
		 * that the registry can serialize, converted like a value declared as
		 * <code>Object</code>) and the writer receives the raw JSON value, null included.
		 */
		public <V> Builder<C> add(String name, LambdaFieldAdapter.PropertyReader<C,V> reader, LambdaFieldAdapter.PropertyWriter<C,V> writer) {
			fields.put(name, new LambdaFieldAdapter<C,V>(reader, writer));
			return this;
		}
		/**
		 * Add a typed property defined by lambdas. The reader returns a Java value of the
		 * given type, serialized with its registry adapter; the writer receives the Java
		 * value deserialized by this adapter (null for a JSON null).
		 */
		public <V> Builder<C> add(String name, Class<V> type, LambdaFieldAdapter.PropertyReader<C,V> reader, LambdaFieldAdapter.PropertyWriter<C,V> writer) {
			if(type==null) {
				throw new IllegalArgumentException("The type of the property "+name+" is null");
			}
			fields.put(name, new LambdaFieldAdapter<C,V>(type, reader, writer));
			return this;
		}

		@Override
		public SimpleClassAdapter<C> _build() {
			if(clazz.isRecord() && factory!=null) {
				throw new JsonException(null, "{0} is a record: it is created with its canonical constructor, a factory() is not supported", clazz.getName());
			}
			return new SimpleClassAdapter<C>(this);
		}
	}

	public static <C> Builder<C> newBuilder(Class<C> clazz) {
		return new Builder<C>(clazz);
	}

	private Supplier<Object> factory;
	private Map<String,FieldAdapter> fields;
	private JsonRegistry registry;
	// Resolved once, by init(): the no-arg constructor, or the canonical one of a record
	private Constructor<?> constructor;
	private JsonException constructorError;
	// Record: the component types, in the canonical constructor order
	private Class<?>[] recordTypes;

	public SimpleClassAdapter(Builder<C> b) {
		super(b.clazz);

		this.factory = b.factory;
		// A copy: the builder can still be used (and modified) to build another adapter
		this.fields = new LinkedHashMap<>(b.fields);
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
		// The registry is bound once everything is initialized: a failure can be retried
		for(FieldAdapter a: fields.values()) {
			if(a instanceof BaseFieldAdapter ba) {
				ba.init(registry,this);
			}
		}
		resolveConstructor();
		this.registry = registry;
	}

	private void resolveConstructor() {
		if(factory!=null) {
			return;
		}
		Class<?> clazz = getAdaptedClazz();
		try {
			if(clazz.isRecord()) {
				RecordComponent[] components = clazz.getRecordComponents();
				recordTypes = new Class<?>[components.length];
				for(int i=0; i<components.length; i++) {
					recordTypes[i] = components[i].getType();
				}
				constructor = clazz.getDeclaredConstructor(recordTypes);
			} else {
				constructor = clazz.getDeclaredConstructor();
			}
			constructor.setAccessible(true);
		} catch(Exception ex) {
			// Only an error if an instance has to be created
			constructorError = new JsonException(ex, "Cannot find the constructor of {0}", clazz.getName());
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
				try {
					Object v = e.getValue().readProperty(_this, genericParams);
					o.put(e.getKey(), v);
				} catch(RuntimeException ex) {
					throw SerializationException.atProperty(ex, e.getKey());
				}
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
		if(!(jsonValue instanceof JsonObject o)) {
			throw new JsonException(null,"Cannot deserialize {0} into a {1}: a JSON object is expected",SerializationException.describe(jsonValue),getAdaptedClazz().getName());
		}
		CycleGuard.enterRead();
		try {
			if(recordTypes!=null) {
				return finalize(createRecord(o, genericParams));
			}
			Object _this = create();
			for(Map.Entry<String,Object> e: o.entrySet()) {
				Object value = e.getValue();
				FieldAdapter fa = fields.get(e.getKey());
				if(fa==null) {
					if(value==null) {
						// A null value for an unknown key is ignored
						continue;
					}
					throw new JsonException(null,"{0} doesn't have a field named {1}",getAdaptedClazz().getName(),e.getKey());
				}
				try {
					// A JSON null assigns null (a primitive field rejects it)
					fa.writeProperty(_this, value, genericParams);
				} catch(RuntimeException ex) {
					throw SerializationException.atProperty(ex, e.getKey());
				}
			}
			return finalize(_this);
		} finally {
			CycleGuard.exitRead();
		}
	}

	private Object createRecord(JsonObject o, ClassAdapter[] genericParams) {
		Object[] args = new Object[recordTypes.length];
		for(Map.Entry<String,Object> e: o.entrySet()) {
			Object value = e.getValue();
			FieldAdapter fa = fields.get(e.getKey());
			if(fa==null) {
				if(value==null) {
					continue;
				}
				throw new JsonException(null,"{0} doesn't have a field named {1}",getAdaptedClazz().getName(),e.getKey());
			}
			if(!(fa instanceof RecordComponentAdapter rc)) {
				throw new JsonException(null,"The property {0} of the record {1} is not a component",e.getKey(),getAdaptedClazz().getName());
			}
			try {
				if(value==null) {
					if(recordTypes[rc.getIndex()].isPrimitive()) {
						throw new JsonException(null, "A null JSON value cannot be assigned to the {0} component {1}", recordTypes[rc.getIndex()].getName(), e.getKey());
					}
					continue;
				}
				args[rc.getIndex()] = rc.toJava(value, genericParams);
			} catch(RuntimeException ex) {
				throw SerializationException.atProperty(ex, e.getKey());
			}
		}
		for(int i=0; i<args.length; i++) {
			if(args[i]==null) {
				if(recordTypes[i].isPrimitive()) {
					// A missing component: the default value of the primitive type (0, false...)
					args[i] = Array.get(Array.newInstance(recordTypes[i], 1), 0);
				} else if(recordTypes[i]==Optional.class) {
					args[i] = Optional.empty();
				}
			}
		}
		return newInstance(args);
	}

	protected Object create() {
		if(factory!=null) {
			return factory.get();
		}
		return newInstance();
	}
	private Object newInstance(Object...args) {
		if(constructor==null) {
			if(constructorError!=null) {
				throw constructorError;
			}
			// Not initialized by a registry
			resolveConstructor();
			if(constructor==null) {
				throw constructorError;
			}
		}
		try {
			return constructor.newInstance(args);
		} catch(InvocationTargetException e) {
			throw new JsonException(e.getCause(), "Error while creating an instance of {0}", getAdaptedClazz().getName());
		} catch(Exception e) {
			throw new JsonException(e);
		}
	}
	protected Object finalize(Object o) {
		return o;
	}
}
