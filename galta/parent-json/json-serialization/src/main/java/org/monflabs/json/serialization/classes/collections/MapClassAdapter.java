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
import java.util.LinkedHashMap;
import java.util.Map;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter for <code>java.util.Map</code>, serialized as a JSON object.
 * <p>
 * JSON keys are strings, so the map keys are converted: strings are kept, numbers, booleans,
 * characters and enums (by name) use their string form and are parsed back to the key type
 * when deserializing. Other key types must serialize to a string. The map is read back as a
 * <code>LinkedHashMap</code> by default, so the JSON order is kept.
 */
public class MapClassAdapter extends BaseClassAdapter {
	
	private Class<? extends Map<?,?>> mapClass;
	private ClassAdapter objectAdapter;
	
	public MapClassAdapter() {
		this(null);
	}

	public MapClassAdapter(Class<? extends Map<?,?>> mapClass) {
		super(Map.class);
		this.mapClass = mapClass;
	}
	
	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.objectAdapter = registry.findAdapter(Object.class);
	}
	
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value!=null) {
			ClassAdapter[] p = CollectionUtil.params(genericParams, 2, objectAdapter);
			Map<?,?> m = (Map<?,?>)value;
			CycleGuard.enter(value);
			try {
				JsonObject o = JsonObject.create();
				for(Map.Entry<?,?> e: m.entrySet()) {
					String k = keyToString(e.getKey(), p[0]);
					Object v = p[1].serialize(e.getValue());
					o.put(k,v);
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
			ClassAdapter[] p = CollectionUtil.params(genericParams, 2, objectAdapter);
			try {
				Map<Object,Object> m = createCollection();
				for(Map.Entry<String,Object> e: o.entrySet()) {
					Object k = stringToKey(e.getKey(), p[0]);
					Object v = p[1].deserialize(e.getValue());
					m.put(k,v);
				}
				return m;
			} catch(InstantiationException|IllegalAccessException e) {
				throw new JsonException(e,"Cannot instanciate class {0}",mapClass);
			}
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"JsonValue is not an object");
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
		throw new JsonException(null,"Map key must be a string while it resolves to {0}",k!=null ? k.getClass() : "null");
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
		if(c.isEnum()) {
			try {
				return Enum.valueOf((Class)c, key);
			} catch(IllegalArgumentException ex) {
				throw new JsonException(ex,"Map key {0} is not a constant of {1}",key,c.getName());
			}
		}
		return keyAdapter.deserialize(key);
	}
	
	protected Map<Object,Object> createCollection() throws InstantiationException, IllegalAccessException {
		return mapClass!=null ? CollectionUtil.newInstance(mapClass) 
				               : new LinkedHashMap<Object,Object>();
	}
}
