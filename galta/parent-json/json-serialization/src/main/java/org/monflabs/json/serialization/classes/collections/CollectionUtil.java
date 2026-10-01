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
package org.monflabs.json.serialization.classes.collections;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.fields.GenericTypeResolver;

/**
 * Helpers shared by the collection adapters.
 */
public final class CollectionUtil {

	private CollectionUtil() {
	}

	// The standard implementations, in order of preference, for a declared type that cannot
	// be instantiated (an interface, an abstract class...)
	private static final Class<?>[] COLLECTIONS = { ArrayList.class, LinkedHashSet.class, TreeSet.class, ArrayDeque.class };
	private static final Class<?>[] MAPS = { LinkedHashMap.class, TreeMap.class, ConcurrentHashMap.class, ConcurrentSkipListMap.class };

	/**
	 * The class instantiated to read back a collection or a map declared with a type: the type
	 * itself when it can be instantiated, else the first standard implementation compatible
	 * with it (<code>ArrayList</code>, <code>LinkedHashSet</code>, <code>TreeSet</code>,
	 * <code>ArrayDeque</code>, <code>LinkedHashMap</code>, <code>TreeMap</code>,
	 * <code>ConcurrentHashMap</code>, <code>ConcurrentSkipListMap</code>). <code>EnumSet</code>
	 * and <code>EnumMap</code> are created from the class of their elements/keys. Returns null
	 * when no implementation fits.
	 */
	public static Class<?> implementation(Class<?> declared, boolean map) {
		if(declared==EnumSet.class || declared==EnumMap.class) {
			return declared;
		}
		if(CONSTRUCTORS.get(declared)!=null) {
			return declared;
		}
		for(Class<?> c: map ? MAPS : COLLECTIONS) {
			if(declared.isAssignableFrom(c)) {
				return c;
			}
		}
		return null;
	}

	// The usable no-arg constructors, looked up once per class (null if there is none)
	private static final ClassValue<Constructor<?>> CONSTRUCTORS = new ClassValue<>() {
		@Override
		protected Constructor<?> computeValue(Class<?> type) {
			if(type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.isArray() || type.isPrimitive()) {
				return null;
			}
			try {
				Constructor<?> c = type.getDeclaredConstructor();
				// A JDK class must be public, with a public constructor; the package of another
				// class must be open to this module (always true on the class path)
				if(c.trySetAccessible() || (Modifier.isPublic(type.getModifiers()) && Modifier.isPublic(c.getModifiers()))) {
					return c;
				}
				return null;
			} catch(NoSuchMethodException|SecurityException ex) {
				return null;
			}
		}
	};

	@SuppressWarnings("unchecked")
	static <T> T newInstance(Class<?> clazz) {
		Constructor<?> c = CONSTRUCTORS.get(clazz);
		if(c==null) {
			throw new JsonException(null, "Cannot create an instance of {0}: it has no usable no-arg constructor", clazz.getName());
		}
		try {
			return (T)c.newInstance();
		} catch(InvocationTargetException ex) {
			throw new JsonException(ex.getCause(), "Error while creating an instance of {0}", clazz.getName());
		} catch(ReflectiveOperationException ex) {
			throw new JsonException(ex, "Cannot create an instance of {0}", clazz.getName());
		}
	}

	/**
	 * The enum class handled by an adapter, for an <code>EnumSet</code> or an
	 * <code>EnumMap</code>.
	 */
	static Class<?> enumClass(ClassAdapter adapter, Class<?> collection) {
		Class<?> c = adapter.getAdaptedClazz();
		if(c==null || !c.isEnum()) {
			throw new JsonException(null, "Cannot create a {0} of {1}: its generic parameter must be an enum", collection.getSimpleName(), c!=null ? c.getName() : "null");
		}
		return c;
	}

	/**
	 * The generic parameters of a collection or map class, as the element (or key/value) types
	 * of its supertype. <code>class Props&lt;V&gt; extends HashMap&lt;String,V&gt;</code> has
	 * one generic parameter, and maps to <code>[String, V]</code>.
	 */
	static final class Params {
		private final Class<?> clazz;
		private final int count;
		private final GenericTypeResolver.ResolvedType[] types;
		private final ClassAdapter[] defaults;
		// The parameters of the class are the ones of the supertype, in the same order
		private final boolean identity;

		Params(Class<?> clazz, Class<?> supertype, JsonRegistry registry) {
			this.clazz = clazz;
			TypeVariable<?>[] tp = GenericTypeResolver.typeParameters(clazz);
			this.count = tp.length;
			Type[] args = GenericTypeResolver.typeArgumentsOf(clazz, supertype);
			if(args==null) {
				// Not a subtype (a custom adapter): untyped
				args = supertype.getTypeParameters();
			}
			this.types = GenericTypeResolver.resolve(args, clazz, registry);
			this.defaults = new ClassAdapter[types.length];
			for(int i=0; i<types.length; i++) {
				defaults[i] = types[i].resolve(null);
			}
			boolean id = count==args.length;
			for(int i=0; id && i<args.length; i++) {
				id = args[i].equals(tp[i]);
			}
			this.identity = id;
		}

		/**
		 * The adapters of the elements (or keys and values) given the generic parameters of the
		 * class. The parameters must match the generic parameters of the class.
		 */
		ClassAdapter[] resolve(ClassAdapter[] genericParams) {
			if(genericParams==null) {
				return defaults;
			}
			if(genericParams.length!=count) {
				throw new JsonException(null, "{0} has {1} generic parameter(s), {2} were given", clazz.getName(), count, genericParams.length);
			}
			if(identity) {
				boolean complete = true;
				for(int i=0; i<genericParams.length; i++) {
					if(genericParams[i]==null) {
						complete = false;
						break;
					}
				}
				if(complete) {
					return genericParams;
				}
			}
			ClassAdapter[] p = new ClassAdapter[types.length];
			for(int i=0; i<types.length; i++) {
				p[i] = types[i].resolve(genericParams);
				if(p[i]==null) {
					p[i] = defaults[i];
				}
			}
			return p;
		}
	}
}
