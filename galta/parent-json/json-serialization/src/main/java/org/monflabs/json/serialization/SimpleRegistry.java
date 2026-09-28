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
package org.monflabs.json.serialization;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;
import org.monflabs.json.serialization.classes.arrays.BooleanArrayClassAdapter;
import org.monflabs.json.serialization.classes.arrays.ByteArrayClassAdapter;
import org.monflabs.json.serialization.classes.arrays.DoubleArrayClassAdapter;
import org.monflabs.json.serialization.classes.arrays.FloatArrayClassAdapter;
import org.monflabs.json.serialization.classes.arrays.IntegerClassAdapter;
import org.monflabs.json.serialization.classes.arrays.LongArrayClassAdapter;
import org.monflabs.json.serialization.classes.arrays.ObjectArrayClassAdapter;
import org.monflabs.json.serialization.classes.arrays.ShortArrayClassAdapter;
import org.monflabs.json.serialization.classes.collections.ListClassAdapter;
import org.monflabs.json.serialization.classes.collections.MapClassAdapter;
import org.monflabs.json.serialization.classes.collections.SetClassAdapter;
import org.monflabs.json.serialization.classes.json.JsonArrayClassAdapter;
import org.monflabs.json.serialization.classes.json.JsonObjectClassAdapter;
import org.monflabs.json.serialization.classes.json.ObjectClassAdapter;
import org.monflabs.json.serialization.classes.primitives.BigDecimalClassAdapter;
import org.monflabs.json.serialization.classes.primitives.BigIntegerClassAdapter;
import org.monflabs.json.serialization.classes.primitives.BooleanClassAdapter;
import org.monflabs.json.serialization.classes.primitives.ByteClassAdapter;
import org.monflabs.json.serialization.classes.primitives.DoubleClassAdapter;
import org.monflabs.json.serialization.classes.primitives.EnumClassAdapter;
import org.monflabs.json.serialization.classes.primitives.FloatClassAdapter;
import org.monflabs.json.serialization.classes.primitives.IntArrayClassAdapter;
import org.monflabs.json.serialization.classes.primitives.LongClassAdapter;
import org.monflabs.json.serialization.classes.primitives.ShortClassAdapter;
import org.monflabs.json.serialization.classes.primitives.StringClassAdapter;
import org.monflabs.util.ObjectBuilder;

/**
 * Simple Json Registry.
 *
 */
public class SimpleRegistry implements JsonRegistry {
	
	public static class Builder extends ObjectBuilder<SimpleRegistry> {

		private boolean defaultRegistry = false;
		private Map<Class<?>,Function<Class<?>,ClassAdapter>> adapters = new HashMap<>();
		private Function<Class<?>,ClassAdapter> classFactory;
		
		private Builder() {
			// Should we?
			addDefaultAdapters();
		}

		public Builder defaultRegistry(boolean defaultRegistry) {
			this.defaultRegistry = defaultRegistry;
			return this;
		}
		
		public Builder classFactory(Function<Class<?>,ClassAdapter> classFactory) {
			this.classFactory = classFactory;
			return this;
		}
		
		public Builder addDefaultAdapters() {
			add(new ObjectClassAdapter());
			add(new JsonObjectClassAdapter());
			add(new JsonArrayClassAdapter());

			add(new BooleanClassAdapter());
			add(new ByteClassAdapter());
			add(new ShortClassAdapter());
			add(new IntegerClassAdapter());
			add(new LongClassAdapter());
			add(new FloatClassAdapter());
			add(new DoubleClassAdapter());
			add(new BigIntegerClassAdapter());
			add(new BigDecimalClassAdapter());
			add(new StringClassAdapter());
			
			add(new BooleanArrayClassAdapter());
			add(new ByteArrayClassAdapter());
			add(new ShortArrayClassAdapter());
			add(new IntArrayClassAdapter());
			add(new LongArrayClassAdapter());
			add(new FloatArrayClassAdapter());
			add(new DoubleArrayClassAdapter());
			
			add(new ListClassAdapter());
			add(new MapClassAdapter());
			add(new SetClassAdapter());
			
			return this;
		}
		
		public Builder add(ClassAdapter adapter) {
			adapters.put(adapter.getAdaptedClazz(), (c) -> adapter );
			return this;
		}

		public Builder add(Class<?> clazz) {
			ClassAdapter ad = SimpleClassAdapter.newBuilder(clazz).reflection().build();
			return add(ad);
		}

		public <C> Builder add(Class<C> clazz, Consumer<SimpleClassAdapter.Builder<C>> build) {
			if(build==null) {
				throw new JsonException(null,"");
			}
			SimpleClassAdapter.Builder<C> b = SimpleClassAdapter.newBuilder(clazz);
			build.accept(b);
			return add(b.build());
		}

		@Override
		public SimpleRegistry _build() {
			return new SimpleRegistry(this);
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	
	private static volatile SimpleRegistry instance;
	
	/**
	 * Get the default registry, the one built with <code>defaultRegistry(true)</code>, or null.
	 */
	public static SimpleRegistry get() {
		return instance;
	}
	
	/**
	 * Forget the default registry, so another one can be defined.
	 */
	public static synchronized void clearDefault() {
		instance = null;
	}
	
	private Map<Class<?>,Function<Class<?>,ClassAdapter>> adapters;
	private Function<Class<?>,ClassAdapter> classFactory;
	// The adapters already initialized for this registry
	private Set<ClassAdapter> initialized = Collections.newSetFromMap(new IdentityHashMap<>());
	
	public SimpleRegistry(Builder b) {
		if(b.defaultRegistry) {
			synchronized(SimpleRegistry.class) {
				if(instance!=null) {
					throw new IllegalStateException("Cannot define two default sample registry objects");
				}
				instance = this;
			}
		}
		
		adapters = new HashMap<>(b.adapters);
		classFactory = b.classFactory;
	}
	
	/**
	 * Find the adapter of a class.
	 * <p>
	 * The adapters registered for the exact class are used first. Then, for a class without a
	 * registered adapter:
	 * <ul>
	 *   <li>an array uses an array adapter of its component type</li>
	 *   <li>a JSON object or array implementation uses the JSON object/array adapter</li>
	 *   <li>an enum is serialized as the name of the constant</li>
	 *   <li>a <code>List</code>, <code>Set</code> or <code>Map</code> implementation uses the
	 *       adapter of the interface (read back as the class itself when it can be instantiated)</li>
	 *   <li>otherwise, the class factory, if any, is asked for an adapter</li>
	 * </ul>
	 * An adapter is initialized once for this registry, the first time it is found.
	 */
	@Override
	public synchronized ClassAdapter findAdapter(Class<?> clazz) {
		Function<Class<?>,ClassAdapter> f = adapters.get(clazz);
		if(f!=null) {
			ClassAdapter a = f.apply(clazz);
			initOnce(a);
			return a;
		}
		if(clazz.isArray()) {
			ClassAdapter p = findAdapter(clazz.getComponentType());
			// No need to initialize an array
			ObjectArrayClassAdapter arrayAdapter = new ObjectArrayClassAdapter(clazz,p);
			adapters.put(clazz, (c) -> arrayAdapter );
			return arrayAdapter;
		}
		ClassAdapter a = defaultAdapter(clazz);
		if(a==null && classFactory!=null) {
			a = classFactory.apply(clazz);
		}
		if(a!=null) {
			// Register the adapter *before* initializing it, so a self-referencing
			// type (class Node { Node next; }) finds it instead of creating a new one
			ClassAdapter found = a;
			adapters.put(clazz, (c) -> found );
			try {
				initOnce(found);
			} catch(RuntimeException ex) {
				adapters.remove(clazz);
				throw ex;
			}
			return found;
		}
		throw new JsonException(null,"Missing adapter for Java class {0}",clazz);
	}
	
	private void initOnce(ClassAdapter a) {
		if(a instanceof BaseClassAdapter ba && initialized.add(a)) {
			try {
				ba.init(this);
			} catch(RuntimeException ex) {
				initialized.remove(a);
				throw ex;
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private ClassAdapter defaultAdapter(Class<?> clazz) {
		// JSON containers are also Lists/Maps: keep them as JSON values
		if(clazz!=JsonObject.class && JsonObject.class.isAssignableFrom(clazz) && adapters.containsKey(JsonObject.class)) {
			return findAdapter(JsonObject.class);
		}
		if(clazz!=JsonArray.class && JsonArray.class.isAssignableFrom(clazz) && adapters.containsKey(JsonArray.class)) {
			return findAdapter(JsonArray.class);
		}
		if(clazz!=Enum.class && Enum.class.isAssignableFrom(clazz)) {
			// A constant with a body is an anonymous subclass of the enum
			Class<?> e = clazz.isEnum() ? clazz : clazz.getSuperclass();
			if(e!=clazz) {
				return findAdapter(e);
			}
			return new EnumClassAdapter(e);
		}
		if(List.class.isAssignableFrom(clazz)) {
			return new ListClassAdapter(isInstantiable(clazz) ? (Class)clazz : null);
		}
		if(Set.class.isAssignableFrom(clazz)) {
			return new SetClassAdapter(isInstantiable(clazz) ? (Class)clazz : null);
		}
		if(Map.class.isAssignableFrom(clazz)) {
			return new MapClassAdapter(isInstantiable(clazz) ? (Class)clazz : null);
		}
		return null;
	}
	
	private static boolean isInstantiable(Class<?> clazz) {
		if(clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) || !Modifier.isPublic(clazz.getModifiers())) {
			return false;
		}
		try {
			return Modifier.isPublic(clazz.getConstructor().getModifiers());
		} catch(NoSuchMethodException ex) {
			return false;
		}
	}
}
