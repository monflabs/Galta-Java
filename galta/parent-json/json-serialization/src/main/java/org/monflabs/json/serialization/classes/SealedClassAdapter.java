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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.SerializationException;

/**
 * Adapter of a sealed class or interface: the JSON object of a value is the one of its
 * concrete class, with an extra discriminator property (<code>"@type"</code> by default)
 * holding the simple name of this class.
 * <pre>
 * sealed interface Shape permits Circle, Square {}
 * record Circle(double radius) implements Shape {}
 * // {"@type":"Circle","radius":2.0}
 * </pre>
 * The candidate classes are the concrete classes of the sealed hierarchy, found with
 * {@link Class#getPermittedSubclasses()} (recursively for the sealed subclasses): the set is
 * closed, so the JSON cannot name an arbitrary class. Their registry adapter is used, or a
 * reflection adapter when the registry has none. Two candidates with the same simple name
 * are an error.
 * <p>
 * This adapter is created by the registry for the sealed types without a registered adapter,
 * when the <code>sealedTypes</code> option is enabled.
 */
public class SealedClassAdapter extends BaseClassAdapter {

	private final String typeProperty;
	private Map<String,Class<?>> byName;
	private Map<Class<?>,String> byClass;
	private Map<Class<?>,ClassAdapter> adapters;

	public SealedClassAdapter(Class<?> sealedClass, String typeProperty) {
		super(sealedClass);
		if(!sealedClass.isSealed()) {
			throw new IllegalArgumentException(sealedClass.getName()+" is not sealed");
		}
		this.typeProperty = typeProperty;
	}

	/**
	 * The name of the discriminator property.
	 */
	public String getTypeProperty() {
		return typeProperty;
	}

	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		List<Class<?>> classes = new ArrayList<>();
		collect(getAdaptedClazz(), classes);
		Map<String,Class<?>> names = new LinkedHashMap<>();
		Map<Class<?>,String> rev = new LinkedHashMap<>();
		for(Class<?> c: classes) {
			String n = c.getSimpleName();
			Class<?> prev = names.put(n, c);
			if(prev!=null && prev!=c) {
				throw new JsonException(null, "The classes {0} and {1} of the sealed type {2} have the same simple name", prev.getName(), c.getName(), getAdaptedClazz().getName());
			}
			rev.put(c, n);
		}
		Map<Class<?>,ClassAdapter> ads = new LinkedHashMap<>();
		for(Class<?> c: classes) {
			ClassAdapter a = registry.findAdapterOrNull(c);
			if(a==null) {
				SimpleClassAdapter<?> s = SimpleClassAdapter.newBuilder(c).reflection().build();
				s.init(registry);
				a = s;
			}
			ads.put(c, a);
		}
		this.byName = names;
		this.byClass = rev;
		this.adapters = ads;
	}

	private static void collect(Class<?> c, List<Class<?>> classes) {
		if(!c.isInterface() && !Modifier.isAbstract(c.getModifiers()) && !classes.contains(c)) {
			classes.add(c);
		}
		if(c.isSealed()) {
			for(Class<?> p: c.getPermittedSubclasses()) {
				collect(p, classes);
			}
		}
	}

	private void checkInit() {
		if(adapters==null) {
			throw new JsonException(null, "The adapter of {0} is not initialized by a registry", getAdaptedClazz().getName());
		}
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value==null) {
			return null;
		}
		checkInit();
		String name = byClass.get(value.getClass());
		if(name==null) {
			throw new JsonException(null, "{0} is not a class of the sealed type {1}", value.getClass().getName(), getAdaptedClazz().getName());
		}
		Object json = adapters.get(value.getClass()).serialize(value);
		if(!(json instanceof JsonObject o)) {
			throw new JsonException(null, "A {0} of the sealed type {1} must serialize to a JSON object, not {2}", value.getClass().getName(), getAdaptedClazz().getName(), SerializationException.describe(json));
		}
		if(o.containsKey(typeProperty)) {
			throw new JsonException(null, "{0} already has a property named {1}, the discriminator of the sealed type {2}", value.getClass().getName(), typeProperty, getAdaptedClazz().getName());
		}
		JsonObject r = JsonObject.create();
		r.put(typeProperty, name);
		r.putAll(o);
		return r;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		checkInit();
		if(!(jsonValue instanceof JsonObject o)) {
			throw new JsonException(null, "Cannot deserialize {0} into a {1}: a JSON object is expected", SerializationException.describe(jsonValue), getAdaptedClazz().getName());
		}
		Object type = o.get(typeProperty);
		if(!(type instanceof String name)) {
			throw new JsonException(null, "The JSON object of the sealed type {0} must have a string property {1} naming its class, one of {2}", getAdaptedClazz().getName(), typeProperty, byName.keySet());
		}
		Class<?> c = byName.get(name);
		if(c==null) {
			throw new JsonException(null, "{0} is not a class of the sealed type {1}, one of {2} is expected", name, getAdaptedClazz().getName(), byName.keySet());
		}
		JsonObject content = JsonObject.create();
		for(Map.Entry<String,Object> e: o.entrySet()) {
			if(!e.getKey().equals(typeProperty)) {
				content.put(e.getKey(), e.getValue());
			}
		}
		return adapters.get(c).deserialize(content);
	}
}
