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

import java.lang.invoke.MethodType;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.classes.SealedClassAdapter;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;
import org.monflabs.json.serialization.classes.arrays.ObjectArrayClassAdapter;
import org.monflabs.json.serialization.classes.arrays.PrimitiveArrayClassAdapter;
import org.monflabs.json.serialization.classes.collections.CollectionClassAdapter;
import org.monflabs.json.serialization.classes.collections.CollectionUtil;
import org.monflabs.json.serialization.classes.collections.ListClassAdapter;
import org.monflabs.json.serialization.classes.collections.MapClassAdapter;
import org.monflabs.json.serialization.classes.collections.OptionalClassAdapter;
import org.monflabs.json.serialization.classes.collections.SetClassAdapter;
import org.monflabs.json.serialization.classes.json.JsonArrayClassAdapter;
import org.monflabs.json.serialization.classes.json.JsonObjectClassAdapter;
import org.monflabs.json.serialization.classes.json.ObjectClassAdapter;
import org.monflabs.json.serialization.classes.primitives.EnumClassAdapter;
import org.monflabs.json.serialization.classes.primitives.NumberClassAdapter;
import org.monflabs.json.serialization.classes.primitives.ScalarClassAdapter;
import org.monflabs.json.serialization.classes.primitives.StringValueClassAdapter;
import org.monflabs.util.ObjectBuilder;

/**
 * Simple Json Registry.
 * <p>
 * The registry is thread safe: the adapters are found without locking once they are created,
 * and are created and initialized under a lock.
 */
public class SimpleRegistry implements JsonRegistry {

	/**
	 * The default name of the discriminator property of the sealed types, see
	 * {@link Builder#sealedTypes()}.
	 */
	public static final String DEFAULT_TYPE_PROPERTY = "@type";

	public static class Builder extends ObjectBuilder<SimpleRegistry> {

		private boolean defaultRegistry = false;
		private boolean defaultAdapters = true;
		// The adapters added explicitly; the built-in ones are created by each build()
		private Map<Class<?>,ClassAdapter> adapters = new LinkedHashMap<>();
		private Function<Class<?>,ClassAdapter> classFactory;
		private int maxDepth = CycleGuard.DEFAULT_MAX_DEPTH;
		private boolean nonFiniteNumbersAsStrings;
		private String typeProperty;

		private Builder() {
		}

		public Builder defaultRegistry(boolean defaultRegistry) {
			this.defaultRegistry = defaultRegistry;
			return this;
		}

		/**
		 * Set the factory called for a class without an adapter. It returns the adapter of the
		 * class, or null to refuse it. It is called once per class, under the registry lock.
		 */
		public Builder classFactory(Function<Class<?>,ClassAdapter> classFactory) {
			this.classFactory = classFactory;
			return this;
		}

		/**
		 * Use the built-in adapters (the default). They are created by each
		 * <code>build()</code>, and the adapters added with <code>add()</code> replace them,
		 * whatever the order of the calls: calling this method again does not undo an
		 * <code>add()</code>.
		 */
		public Builder addDefaultAdapters() {
			this.defaultAdapters = true;
			return this;
		}

		/**
		 * Use the built-in adapters or not. Without them, only the added adapters (and the
		 * class factory) are available, apart from the arrays, enums, collections and maps.
		 */
		public Builder defaultAdapters(boolean defaultAdapters) {
			this.defaultAdapters = defaultAdapters;
			return this;
		}

		/**
		 * The maximum nesting depth of the values serialized or deserialized, 1000 by
		 * default. A deeper value throws a {@link JsonException} instead of overflowing the
		 * stack.
		 */
		public Builder maxDepth(int maxDepth) {
			if(maxDepth<1) {
				throw new IllegalArgumentException("The maximum depth must be positive");
			}
			this.maxDepth = maxDepth;
			return this;
		}

		/**
		 * Write the NaN and infinite <code>float</code>/<code>double</code> values as the
		 * strings "NaN", "Infinity" and "-Infinity" (they are always read back from these
		 * strings). By default they are written as is, and become null when stringified.
		 */
		public Builder nonFiniteNumbersAsStrings(boolean nonFiniteNumbersAsStrings) {
			this.nonFiniteNumbersAsStrings = nonFiniteNumbersAsStrings;
			return this;
		}

		/**
		 * Serialize the sealed classes and interfaces with a discriminator property,
		 * <code>"@type"</code>, see {@link SealedClassAdapter}.
		 */
		public Builder sealedTypes() {
			return sealedTypes(DEFAULT_TYPE_PROPERTY);
		}

		/**
		 * Serialize the sealed classes and interfaces with a discriminator property holding
		 * the simple name of the concrete class, see {@link SealedClassAdapter}. A null name
		 * disables it.
		 */
		public Builder sealedTypes(String typeProperty) {
			this.typeProperty = typeProperty;
			return this;
		}

		/**
		 * Register an adapter for the class it adapts. It replaces any other adapter of this
		 * class, built-in adapters included.
		 */
		public Builder add(ClassAdapter adapter) {
			if(adapter==null) {
				throw new IllegalArgumentException("The adapter is null");
			}
			adapters.put(adapter.getAdaptedClazz(), adapter);
			return this;
		}

		/**
		 * Register a class: an enum is serialized as the name of its constants, another class
		 * by reflection.
		 */
		public Builder add(Class<?> clazz) {
			if(clazz.isEnum()) {
				return add(new EnumClassAdapter(clazz));
			}
			ClassAdapter ad = SimpleClassAdapter.newBuilder(clazz).reflection().build();
			return add(ad);
		}

		public <C> Builder add(Class<C> clazz, Consumer<SimpleClassAdapter.Builder<C>> build) {
			if(build==null) {
				throw new IllegalArgumentException("The builder consumer of "+clazz.getName()+" is null: use add(Class) for a reflection adapter");
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

	/**
	 * The built-in adapters, new instances.
	 */
	private static Map<Class<?>,ClassAdapter> defaultAdapters() {
		Map<Class<?>,ClassAdapter> m = new HashMap<>();
		Consumer<ClassAdapter> add = a -> m.put(a.getAdaptedClazz(), a);
		add.accept(new ObjectClassAdapter());
		add.accept(new JsonObjectClassAdapter());
		add.accept(new JsonArrayClassAdapter());

		// Boxed primitives, Character, BigInteger, BigDecimal, String, Number
		ScalarClassAdapter.standardAdapters().forEach(add);
		add.accept(new NumberClassAdapter());
		// Arrays of primitives (int[]...), char[] being a string
		PrimitiveArrayClassAdapter.standardAdapters().forEach(add);
		// java.time, UUID, URI, Date
		StringValueClassAdapter.standardAdapters().forEach(add);

		add.accept(new ListClassAdapter());
		add.accept(new MapClassAdapter());
		add.accept(new SetClassAdapter());
		add.accept(new OptionalClassAdapter());
		return m;
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

	private final Function<Class<?>,ClassAdapter> classFactory;
	private final int maxDepth;
	private final boolean nonFiniteNumbersAsStrings;
	private final String typeProperty;

	// The adapters ready to use (initialized): read without locking
	private final Map<Class<?>,ClassAdapter> ready = new ConcurrentHashMap<>();
	// The classes without an adapter
	private final Set<Class<?>> missing = ConcurrentHashMap.newKeySet();

	// Guarded by lock
	private final Object lock = new Object();
	// The registered adapters, initialized the first time they are found
	private final Map<Class<?>,ClassAdapter> registered;
	// The adapters being created by the current (outermost) lookup, published when it succeeds
	private final Map<Class<?>,ClassAdapter> pending = new HashMap<>();
	// The adapters already initialized for this registry
	private final Set<ClassAdapter> initialized = Collections.newSetFromMap(new IdentityHashMap<>());
	private int creating;

	public SimpleRegistry(Builder b) {
		if(b.defaultRegistry) {
			synchronized(SimpleRegistry.class) {
				if(instance!=null) {
					throw new IllegalStateException("Cannot define two default sample registry objects");
				}
				instance = this;
			}
		}

		registered = b.defaultAdapters ? defaultAdapters() : new HashMap<>();
		registered.putAll(b.adapters);
		classFactory = b.classFactory;
		maxDepth = b.maxDepth;
		nonFiniteNumbersAsStrings = b.nonFiniteNumbersAsStrings;
		typeProperty = b.typeProperty;
	}

	@Override
	public int getMaxDepth() {
		return maxDepth;
	}

	@Override
	public boolean isNonFiniteNumbersAsStrings() {
		return nonFiniteNumbersAsStrings;
	}

	/**
	 * Find the adapter of a class.
	 * <p>
	 * The adapters registered for the exact class are used first. Then, for a class without a
	 * registered adapter:
	 * <ul>
	 *   <li>a primitive type uses the adapter of its boxed type</li>
	 *   <li>an array uses an array adapter of its component type</li>
	 *   <li>a JSON object or array implementation uses the JSON object/array adapter</li>
	 *   <li>an enum is serialized as the name of the constant</li>
	 *   <li>a sealed type, if enabled, uses a discriminator property; the classes of a sealed
	 *       hierarchy get a reflection adapter</li>
	 *   <li>a <code>Collection</code>, an <code>Iterable</code> or a <code>Map</code> uses a
	 *       collection adapter, read back as the class itself when it can be instantiated, or as
	 *       a compatible standard implementation</li>
	 *   <li>a <code>ZoneId</code>, a <code>Date</code> or a <code>Number</code> subclass uses
	 *       the adapter of this type</li>
	 *   <li>otherwise, the class factory, if any, is asked for an adapter</li>
	 * </ul>
	 * An adapter is initialized once for this registry, the first time it is found.
	 */
	@Override
	public ClassAdapter findAdapter(Class<?> clazz) {
		ClassAdapter a = findAdapterOrNull(clazz);
		if(a==null) {
			throw new JsonException(null,"Missing adapter for Java class {0}",clazz.getName());
		}
		return a;
	}

	/**
	 * Find the adapter of a class, or null if there is none. The absence of an adapter is
	 * cached: the class factory is not called again for the class.
	 */
	@Override
	public ClassAdapter findAdapterOrNull(Class<?> clazz) {
		ClassAdapter a = ready.get(clazz);
		if(a!=null) {
			return a;
		}
		if(missing.contains(clazz)) {
			return null;
		}
		synchronized(lock) {
			return create(clazz);
		}
	}

	// Called with the lock
	private ClassAdapter create(Class<?> clazz) {
		ClassAdapter a = ready.get(clazz);
		if(a==null) {
			// Being created by this lookup: a self-referencing type (class Node { Node next; })
			// finds its adapter instead of creating a new one
			a = pending.get(clazz);
		}
		if(a!=null || missing.contains(clazz)) {
			return a;
		}
		creating++;
		boolean ok = false;
		try {
			a = registered.get(clazz);
			if(a==null) {
				a = newAdapter(clazz);
			}
			if(a==null) {
				missing.add(clazz);
			} else {
				// Registered *before* it is initialized
				pending.put(clazz, a);
				initOnce(a);
			}
			ok = true;
			return a;
		} finally {
			if(!ok) {
				pending.remove(clazz);
			}
			if(--creating==0) {
				if(ok) {
					// Publish everything at once, so another thread never sees an adapter that
					// references an uninitialized one
					ready.putAll(pending);
				}
				pending.clear();
			}
		}
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
	private ClassAdapter newAdapter(Class<?> clazz) {
		if(clazz.isPrimitive()) {
			return clazz==void.class ? null : create(MethodType.methodType(clazz).wrap().returnType());
		}
		if(clazz.isArray()) {
			ClassAdapter p = create(clazz.getComponentType());
			return p!=null ? new ObjectArrayClassAdapter(clazz,p) : null;
		}
		// JSON containers are also Lists/Maps: keep them as JSON values
		if(clazz!=JsonObject.class && JsonObject.class.isAssignableFrom(clazz) && registered.containsKey(JsonObject.class)) {
			return create(JsonObject.class);
		}
		if(clazz!=JsonArray.class && JsonArray.class.isAssignableFrom(clazz) && registered.containsKey(JsonArray.class)) {
			return create(JsonArray.class);
		}
		if(clazz!=Enum.class && Enum.class.isAssignableFrom(clazz)) {
			// A constant with a body is an anonymous subclass of the enum
			Class<?> e = clazz.isEnum() ? clazz : clazz.getSuperclass();
			if(e!=clazz) {
				return create(e);
			}
			return new EnumClassAdapter(e);
		}
		if(typeProperty!=null) {
			if(clazz.isSealed() && (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()))) {
				return new SealedClassAdapter(clazz, typeProperty);
			}
			if(isPermittedSubclass(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
				// The classes of a sealed hierarchy: a closed set
				return SimpleClassAdapter.newBuilder(clazz).reflection().build();
			}
		}
		if(Map.class.isAssignableFrom(clazz)) {
			return new MapClassAdapter(clazz, CollectionUtil.implementation(clazz, true));
		}
		if(Collection.class.isAssignableFrom(clazz) || clazz==Iterable.class) {
			return new CollectionClassAdapter(clazz, CollectionUtil.implementation(clazz, false));
		}
		for(Class<?> base: new Class<?>[] { ZoneId.class, Date.class, Number.class }) {
			if(base.isAssignableFrom(clazz) && registered.containsKey(base)) {
				// Serialized as the base type (read back as the base type too)
				return create(base);
			}
		}
		return classFactory!=null ? classFactory.apply(clazz) : null;
	}

	private static boolean isPermittedSubclass(Class<?> clazz) {
		Class<?> s = clazz.getSuperclass();
		if(s!=null && s.isSealed()) {
			return true;
		}
		for(Class<?> i: clazz.getInterfaces()) {
			if(i.isSealed()) {
				return true;
			}
		}
		return false;
	}
}
