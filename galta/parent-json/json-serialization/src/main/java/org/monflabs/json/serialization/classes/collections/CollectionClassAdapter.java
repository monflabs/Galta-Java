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

import java.util.Collection;
import java.util.EnumSet;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.fields.RuntimeAdapters;

/**
 * Adapter of a <code>Collection</code> (or an <code>Iterable</code>), serialized as a JSON
 * array.
 * <p>
 * The element adapter comes from the generic parameters; without them (a raw collection, or
 * a collection serialized at the top level), the elements are kept as is. An element whose
 * class is a subclass of the element type is serialized with the adapter of its own class,
 * when the registry has one.
 * <p>
 * The collection is read back as the implementation class given to the constructor, see
 * {@link CollectionUtil#implementation(Class, boolean)}.
 */
public class CollectionClassAdapter extends BaseClassAdapter {

	private final Class<?> implClass;
	private JsonRegistry registry;
	private CollectionUtil.Params params;

	/**
	 * Create an adapter for a declared collection type, read back as an instance of
	 * <code>implClass</code> (null if it cannot be read back).
	 */
	public CollectionClassAdapter(Class<?> declaredClass, Class<?> implClass) {
		super(declaredClass);
		this.implClass = implClass;
	}

	/**
	 * The class read back, or null if the declared type cannot be instantiated.
	 */
	public Class<?> getImplementationClass() {
		return implClass;
	}

	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.params = new CollectionUtil.Params(getAdaptedClazz(), Iterable.class, registry);
		this.registry = registry;
	}

	private ClassAdapter elementAdapter(ClassAdapter[] genericParams) {
		if(params==null) {
			throw new JsonException(null, "The adapter of {0} is not initialized by a registry", getAdaptedClazz().getName());
		}
		return params.resolve(genericParams)[0];
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value==null) {
			return null;
		}
		ClassAdapter item = elementAdapter(genericParams);
		CycleGuard.enter(value);
		try {
			JsonArray a = value instanceof Collection<?> c ? JsonArray.create(c.size()) : JsonArray.create();
			int i = 0;
			for(Object v: (Iterable<?>)value) {
				try {
					a.add(v!=null ? RuntimeAdapters.forValue(registry, item, v).serialize(v) : null);
				} catch(RuntimeException ex) {
					throw SerializationException.atIndex(ex, i);
				}
				i++;
			}
			return a;
		} finally {
			CycleGuard.exit(value);
		}
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue instanceof JsonArray a) {
			ClassAdapter item = elementAdapter(genericParams);
			int len = a.size();
			Collection<Object> c = createCollection(item);
			CycleGuard.enterRead();
			try {
				for(int i=0; i<len; i++) {
					Object v;
					try {
						v = item.deserialize(a.get(i));
					} catch(RuntimeException ex) {
						throw SerializationException.atIndex(ex, i);
					}
					try {
						c.add(v);
					} catch(NullPointerException|ClassCastException|IllegalArgumentException ex) {
						throw SerializationException.atIndex(new JsonException(ex, "Cannot add {0} to a {1}: {2}", v, c.getClass().getName(), ex.toString()), i);
					}
				}
			} finally {
				CycleGuard.exitRead();
			}
			return c;
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"Cannot deserialize {0} into a {1}: a JSON array is expected",SerializationException.describe(jsonValue),getAdaptedClazz().getName());
		}
	}

	/**
	 * Create the collection to fill.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Collection<Object> createCollection(ClassAdapter itemAdapter) {
		if(implClass==null) {
			throw new JsonException(null, "Cannot create an instance of {0}: register an adapter for it, or declare a concrete collection type", getAdaptedClazz().getName());
		}
		if(implClass==EnumSet.class) {
			return (Collection)EnumSet.noneOf((Class)CollectionUtil.enumClass(itemAdapter, EnumSet.class));
		}
		return CollectionUtil.newInstance(implClass);
	}
}
