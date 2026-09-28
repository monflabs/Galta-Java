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
package org.monflabs.json.yaml;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.nodes.MappingNode;

//
// https://github.com/asomov/snakeyaml-engine
// https://bitbucket.org/asomov/snakeyaml-engine/wiki/Documentation
// https://mvnrepository.com/artifact/org.snakeyaml/snakeyaml-engine
//

/**
 * YAML support, based on snakeyaml-engine.
 * <p>
 * A YAML document is loaded as JSON values: mappings become JSON objects (with string keys),
 * sequences and sets (<code>!!set</code>) become JSON arrays. Values that JSON cannot represent
 * are rejected with a {@link JsonException}: <code>.nan</code>/<code>.inf</code>, recursive
 * aliases (a collection containing itself) and other non JSON values such as binary data.
 * Non-recursive aliases are fine: the aliased value is shared.
 */
public class SnakeYaml {
	
	public static Object parse(String json) {
		return parse(JsonFactory.get(), json);
	}
	public static Object parse(JsonFactory factory, String json) {
		return toJson(factory, newLoad(factory).loadFromString(json));
	}

	public static Object parse(Reader json) {
		return parse(JsonFactory.get(), json);
	}
	public static Object parse(JsonFactory factory, Reader json) {
		return toJson(factory, newLoad(factory).loadFromReader(json));
	}

	public static Object parse(InputStream json) {
		return parse(JsonFactory.get(), json);
	}
	public static Object parse(JsonFactory factory, InputStream json) {
		return toJson(factory, newLoad(factory).loadFromInputStream(json));
	}

	public static String stringify(Object json) {
		DumpSettings settings = DumpSettings.builder()
				.setDefaultFlowStyle(FlowStyle.BLOCK)
				.build();
		return stringify(json,settings);
	}
	public static String stringify(Object json, DumpSettings settings) {
		Dump dump = new Dump(settings);
		return dump.dumpToString(json);
	}

	/**
	 * Check the loaded values, and convert the ones that have a JSON equivalent.
	 */
	private static Object toJson(JsonFactory factory, Object value) {
		Set<Object> path = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Object> done = Collections.newSetFromMap(new IdentityHashMap<>());
		return toJson(factory, value, path, done);
	}
	private static Object toJson(JsonFactory factory, Object value, Set<Object> path, Set<Object> done) {
		if(value==null || value instanceof String || value instanceof Boolean
				|| value instanceof Integer || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal) {
			return value;
		}
		if(value instanceof Double || value instanceof Float) {
			double d = ((Number)value).doubleValue();
			if(Double.isNaN(d) || Double.isInfinite(d)) {
				throw new JsonException(null, "The YAML value {0} cannot be represented in JSON", value);
			}
			return value;
		}
		if(value instanceof Number) {
			return value;
		}
		if(value instanceof JsonObject || value instanceof JsonArray || value instanceof Set) {
			if(done.contains(value)) {
				return value; // Shared through an alias, already checked
			}
			if(!path.add(value)) {
				throw new JsonException(null, "Recursive YAML aliases cannot be represented in JSON");
			}
			try {
				if(value instanceof JsonObject o) {
					for(Map.Entry<String,Object> e: o.entrySet()) {
						Object v = e.getValue();
						Object c = toJson(factory, v, path, done);
						if(c!=v) {
							e.setValue(c);
						}
					}
					done.add(value);
					return value;
				}
				if(value instanceof JsonArray a) {
					for(int i=0; i<a.size(); i++) {
						Object v = a.get(i);
						Object c = toJson(factory, v, path, done);
						if(c!=v) {
							a.set(i, c);
						}
					}
					done.add(value);
					return value;
				}
				// A YAML set (!!set) is a JSON array of its members, in order
				Set<?> set = (Set<?>)value;
				JsonArray a = factory.createArray(set.size());
				for(Object v: set) {
					a.add(toJson(factory, v, path, done));
				}
				return a;
			} finally {
				path.remove(value);
			}
		}
		throw new JsonException(null, "The YAML value of type {0} cannot be represented in JSON", value.getClass().getName());
	}

	private static Load newLoad(JsonFactory factory) {
		LoadSettings settings = getSettings(factory);
		return new Load(settings, new StringKeyConstructor(settings));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static LoadSettings getSettings(JsonFactory factory) {
		return factory.getCapability(LoadSettings.class, () -> 
			LoadSettings.builder()
				.setLabel("Monflabs factory")
				.setDefaultList(initSize -> factory.createArray(initSize))
				.setDefaultMap(initSize -> (Map)factory.createObject())
				.build()
		);
	}

	/**
	 * YAML mapping keys can be any scalar ({@code 1: a}, {@code true: b}, {@code ~: c}), but the
	 * mappings are materialized as {@link org.monflabs.json.JsonObject}s, i.e. {@code Map<String,Object>}.
	 * The default constructor would put the raw {@code Integer}/{@code Boolean}/{@code null} key into
	 * that map through the raw {@code Map} view (heap pollution), and the first {@code keySet()}
	 * iteration typed as {@code String} would then fail with a {@code ClassCastException}.
	 * <p>
	 * Keys are converted with {@code String.valueOf}, which is how JSON (and the JS engine) treat
	 * non-string property names: {@code 1} becomes {@code "1"}, {@code null} becomes {@code "null"}.
	 */
	private static class StringKeyConstructor extends StandardConstructor {

		StringKeyConstructor(LoadSettings settings) {
			super(settings);
		}

		@Override
		protected void constructMapping2ndStep(MappingNode node, Map<Object, Object> mapping) {
			super.constructMapping2ndStep(node, new StringKeyMap(mapping));
		}
	}

	/**
	 * A view over the real mapping that stringifies every key on the way in.
	 * Only the fill-side operations are needed by the constructor.
	 */
	private static class StringKeyMap extends AbstractMap<Object, Object> {

		private final Map<Object, Object> target;

		StringKeyMap(Map<Object, Object> target) {
			this.target = target;
		}

		private static Object toKey(Object key) {
			return key instanceof String ? key : String.valueOf(key);
		}

		@Override
		public Object put(Object key, Object value) {
			return target.put(toKey(key), value);
		}

		@Override
		public Object get(Object key) {
			return target.get(toKey(key));
		}

		@Override
		public boolean containsKey(Object key) {
			return target.containsKey(toKey(key));
		}

		@Override
		public Object remove(Object key) {
			return target.remove(toKey(key));
		}

		@Override
		public int size() {
			return target.size();
		}

		@Override
		public Set<Map.Entry<Object, Object>> entrySet() {
			return target.entrySet();
		}
	}
}
