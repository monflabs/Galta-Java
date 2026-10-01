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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.fields.RuntimeAdapters;

/**
 * Adapter for <code>java.util.Map</code>, serialized as a JSON object.
 * <p>
 * JSON keys are strings, so the map keys are converted: strings are kept, numbers, booleans,
 * characters and enums (by name) use their string form and are parsed back to the key type
 * when deserializing. Other key types must serialize to a string. Two keys with the same
 * string form are an error. A value whose class is a subclass of the value type is
 * serialized with the adapter of its own class, when the registry has one.
 * <p>
 * The map is read back as a <code>LinkedHashMap</code> by default, so the JSON order is kept,
 * or as the implementation of the declared map type, see
 * {@link CollectionUtil#implementation(Class, boolean)}.
 */
public class MapClassAdapter extends BaseClassAdapter {

	private final Class<?> implClass;
	private JsonRegistry registry;
	private CollectionUtil.Params params;

	/**
	 * The adapter of the <code>Map</code> interface, read back as a <code>LinkedHashMap</code>.
	 */
	public MapClassAdapter() {
		this(null);
	}

	/**
	 * The adapter of a map class, registered for this class only and read back as an
	 * instance of it if it can be instantiated (or else a compatible standard map). A null
	 * class is the <code>Map</code> interface.
	 */
	public MapClassAdapter(Class<? extends Map<?,?>> mapClass) {
		this(mapClass!=null ? mapClass : Map.class, CollectionUtil.implementation(mapClass!=null ? mapClass : Map.class, true));
	}

	/**
	 * Create an adapter for a declared map type, read back as an instance of
	 * <code>implClass</code> (null if it cannot be read back).
	 */
	public MapClassAdapter(Class<?> declaredClass, Class<?> implClass) {
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
		this.params = new CollectionUtil.Params(getAdaptedClazz(), Map.class, registry);
		this.registry = registry;
	}

	private ClassAdapter[] params(ClassAdapter[] genericParams) {
		if(params==null) {
			throw new JsonException(null, "The adapter of {0} is not initialized by a registry", getAdaptedClazz().getName());
		}
		return params.resolve(genericParams);
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value!=null) {
			ClassAdapter[] p = params(genericParams);
			Map<?,?> m = (Map<?,?>)value;
			CycleGuard.enter(value);
			try {
				JsonObject o = JsonObject.create();
				for(Map.Entry<?,?> e: m.entrySet()) {
					String k = keyToString(e.getKey(), p[0]);
					if(o.containsKey(k)) {
						throw new JsonException(null,"Two keys of the map have the same JSON form {0}",k);
					}
					Object v = e.getValue();
					try {
						o.put(k, v!=null ? RuntimeAdapters.forValue(registry, p[1], v).serialize(v) : null);
					} catch(RuntimeException ex) {
						throw SerializationException.atProperty(ex, k);
					}
				}
				return o;
			} finally {
				CycleGuard.exit(value);
			}
		}
		return null;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue instanceof JsonObject o) {
			ClassAdapter[] p = params(genericParams);
			Map<Object,Object> m = createCollection(p[0]);
			CycleGuard.enterRead();
			try {
				for(Map.Entry<String,Object> e: o.entrySet()) {
					Object k, v;
					try {
						k = stringToKey(e.getKey(), p[0]);
						v = p[1].deserialize(e.getValue());
					} catch(RuntimeException ex) {
						throw SerializationException.atProperty(ex, e.getKey());
					}
					try {
						m.put(k,v);
					} catch(NullPointerException|ClassCastException|IllegalArgumentException ex) {
						throw SerializationException.atProperty(new JsonException(ex, "Cannot add {0} to a {1}: {2}", v, m.getClass().getName(), ex.toString()), e.getKey());
					}
				}
			} finally {
				CycleGuard.exitRead();
			}
			return m;
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"Cannot deserialize {0} into a {1}: a JSON object is expected",SerializationException.describe(jsonValue),getAdaptedClazz().getName());
		}
	}

	protected String keyToString(Object key, ClassAdapter keyAdapter) {
		if(key==null) {
			throw new JsonException(null,"A map with a null key cannot be serialized to JSON");
		}
		if(key instanceof String s) {
			return s;
		}
		if(key instanceof Enum<?> e) {
			return e.name();
		}
		if(key instanceof Number || key instanceof Boolean || key instanceof Character) {
			return key.toString();
		}
		Object k = keyAdapter.serialize(key);
		if(k instanceof String ks) {
			return ks;
		}
		throw new JsonException(null,"Map key must be a string while it resolves to {0}",k!=null ? k.getClass().getName() : "null");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Object stringToKey(String key, ClassAdapter keyAdapter) {
		Class<?> c = keyAdapter.getAdaptedClazz();
		try {
			if(c==String.class || c==Object.class) {
				return key;
			}
			if(c==Integer.class || c==int.class) return Integer.valueOf(key);
			if(c==Long.class || c==long.class) return Long.valueOf(key);
			if(c==Short.class || c==short.class) return Short.valueOf(key);
			if(c==Byte.class || c==byte.class) return Byte.valueOf(key);
			if(c==Double.class || c==double.class) return Double.valueOf(key);
			if(c==Float.class || c==float.class) return Float.valueOf(key);
			if(c==BigInteger.class) return new BigInteger(key);
			if(c==BigDecimal.class) return new BigDecimal(key);
		} catch(NumberFormatException ex) {
			throw new JsonException(ex,"Map key {0} is not a valid {1}",key,c.getName());
		}
		if(c==Boolean.class || c==boolean.class) {
			if(key.equals("true")) return Boolean.TRUE;
			if(key.equals("false")) return Boolean.FALSE;
			throw new JsonException(null,"Map key {0} is not a valid boolean",key);
		}
		if(c==Character.class || c==char.class) {
			if(key.length()==1) return key.charAt(0);
			throw new JsonException(null,"Map key {0} is not a valid character",key);
		}
		if(c!=null && c.isEnum()) {
			try {
				return Enum.valueOf((Class)c, key);
			} catch(IllegalArgumentException ex) {
				throw new JsonException(ex,"Map key {0} is not a constant of {1}",key,c.getName());
			}
		}
		return keyAdapter.deserialize(key);
	}

	/**
	 * Create the map to fill.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Map<Object,Object> createCollection(ClassAdapter keyAdapter) {
		if(implClass==null) {
			throw new JsonException(null, "Cannot create an instance of {0}: register an adapter for it, or declare a concrete map type", getAdaptedClazz().getName());
		}
		if(implClass==EnumMap.class) {
			return new EnumMap(CollectionUtil.enumClass(keyAdapter, EnumMap.class));
		}
		return CollectionUtil.newInstance(implClass);
	}
}
