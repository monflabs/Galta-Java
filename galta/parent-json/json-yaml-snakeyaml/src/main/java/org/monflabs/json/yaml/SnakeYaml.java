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
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.RepresentToNode;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;

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
 * aliases (a collection containing itself), keys that collide once converted to strings
 * (<code>1</code> and <code>"1"</code>), collections used as keys, unknown tags and other non
 * JSON values such as binary data. Any snakeyaml error is reported as a {@link JsonException}.
 * <p>
 * Non-recursive aliases are fine: the aliased value is shared. As a document made of nested
 * aliases can expand to a huge number of values ("billion laughs") once it is copied or
 * stringified, a document whose expanded size exceeds {@link Options#getMaxExpandedSize()}
 * is rejected. The input is limited to {@link Options#getCodePointLimit()} code points.
 * <p>
 * A decimal number that a double cannot hold exactly is loaded as a {@link BigDecimal}.
 */
public class SnakeYaml {

	/**
	 * Load options.
	 */
	public static final class Options {
		/** The default maximum number of values of a document, aliases expanded */
		public static final long DEFAULT_MAX_EXPANDED_SIZE = 10_000_000L;
		/** The default maximum size of a document, in code points (the snakeyaml-engine default) */
		public static final int DEFAULT_CODE_POINT_LIMIT = 3 * 1024 * 1024;
		/** The default maximum number of aliases to collections (the snakeyaml-engine default) */
		public static final int DEFAULT_MAX_ALIASES_FOR_COLLECTIONS = 50;

		private long maxExpandedSize = DEFAULT_MAX_EXPANDED_SIZE;
		private int codePointLimit = DEFAULT_CODE_POINT_LIMIT;
		private int maxAliasesForCollections = DEFAULT_MAX_ALIASES_FOR_COLLECTIONS;

		public long getMaxExpandedSize() {
			return maxExpandedSize;
		}
		/**
		 * The maximum number of values (scalars and collections) of the document once its
		 * aliases are expanded, as a copy or a stringification would.
		 */
		public Options setMaxExpandedSize(long maxExpandedSize) {
			this.maxExpandedSize = maxExpandedSize;
			return this;
		}
		public int getCodePointLimit() {
			return codePointLimit;
		}
		/**
		 * The maximum size of the YAML input, in code points.
		 */
		public Options setCodePointLimit(int codePointLimit) {
			this.codePointLimit = codePointLimit;
			return this;
		}
		public int getMaxAliasesForCollections() {
			return maxAliasesForCollections;
		}
		/**
		 * The maximum number of aliases to collections in the document.
		 */
		public Options setMaxAliasesForCollections(int maxAliasesForCollections) {
			this.maxAliasesForCollections = maxAliasesForCollections;
			return this;
		}
		private boolean isDefault() {
			return maxExpandedSize==DEFAULT_MAX_EXPANDED_SIZE && codePointLimit==DEFAULT_CODE_POINT_LIMIT
					&& maxAliasesForCollections==DEFAULT_MAX_ALIASES_FOR_COLLECTIONS;
		}
	}

	private static final Options DEFAULT_OPTIONS = new Options();

	public static Object parse(String json) {
		return parse(JsonFactory.get(), json);
	}
	public static Object parse(JsonFactory factory, String json) {
		return parse(factory, json, DEFAULT_OPTIONS);
	}
	public static Object parse(JsonFactory factory, String json, Options options) {
		return load(factory, options, l -> l.loadFromString(json));
	}

	public static Object parse(Reader json) {
		return parse(JsonFactory.get(), json);
	}
	public static Object parse(JsonFactory factory, Reader json) {
		return parse(factory, json, DEFAULT_OPTIONS);
	}
	public static Object parse(JsonFactory factory, Reader json, Options options) {
		return load(factory, options, l -> l.loadFromReader(json));
	}

	public static Object parse(InputStream json) {
		return parse(JsonFactory.get(), json);
	}
	public static Object parse(JsonFactory factory, InputStream json) {
		return parse(factory, json, DEFAULT_OPTIONS);
	}
	public static Object parse(JsonFactory factory, InputStream json, Options options) {
		return load(factory, options, l -> l.loadFromInputStream(json));
	}

	private static Object load(JsonFactory factory, Options options, Function<Load,Object> loader) {
		Object value;
		try {
			value = loader.apply(newLoad(factory, options));
		} catch(JsonException ex) {
			throw ex;
		} catch(YamlEngineException ex) {
			throw new JsonException(ex, "Invalid YAML: {0}", ex.getMessage());
		}
		Object json = toJson(factory, value);
		long size = expandedSize(json, new IdentityHashMap<>(), options.getMaxExpandedSize());
		if(size>options.getMaxExpandedSize()) {
			throw new JsonException(null, "The YAML document expands to more than {0} values through its aliases", options.getMaxExpandedSize());
		}
		return json;
	}

	/**
	 * The number of values of a (non recursive) document once its shared values are
	 * expanded, memoized per container, and capped just above the limit.
	 */
	private static long expandedSize(Object value, Map<Object,Long> memo, long limit) {
		if(!(value instanceof JsonObject || value instanceof JsonArray)) {
			return 1;
		}
		Long known = memo.get(value);
		if(known!=null) {
			return known;
		}
		long size = 1;
		Collection<?> values = value instanceof JsonObject o ? o.values() : (JsonArray)value;
		for(Object v: values) {
			size += expandedSize(v, memo, limit);
			if(size>limit) {
				size = limit+1;
				break;
			}
		}
		memo.put(value, size);
		return size;
	}

	/**
	 * Stringify a JSON value as YAML (block style).
	 * <p>
	 * The strings that a YAML 1.1 parser would read as another type (<code>yes</code>,
	 * <code>off</code>, <code>~</code>, <code>0x1F</code>, <code>2001-12-14</code>...)
	 * are quoted. NaN and infinite numbers, which JSON cannot represent, are rejected with
	 * a {@link JsonException}.
	 */
	public static String stringify(Object json) {
		DumpSettings settings = DumpSettings.builder()
				.setDefaultFlowStyle(FlowStyle.BLOCK)
				.build();
		return stringify(json,settings);
	}
	public static String stringify(Object json, DumpSettings settings) {
		try {
			Dump dump = new Dump(settings, new JsonRepresenter(settings));
			return dump.dumpToString(json);
		} catch(YamlEngineException ex) {
			throw new JsonException(ex, "Cannot stringify as YAML: {0}", ex.getMessage());
		}
	}

	// Plain scalars that YAML 1.1 (or the YAML 1.2 core schema) resolves to something else than a string
	private static final Pattern AMBIGUOUS = Pattern.compile(
		  "(?:yes|Yes|YES|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF)"  // bool
		+ "|(?:~|null|Null|NULL)"                                                                   // null
		+ "|[-+]?0b[0-1_]+|[-+]?0o?[0-7_]+|[-+]?(?:0|[1-9][0-9_]*)|[-+]?0x[0-9a-fA-F_]+"         // int
		+ "|[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+"                                                   // sexagesimal int
		+ "|[-+]?(?:[0-9][0-9_]*)?\\.[0-9_]*(?:[eE][-+]?[0-9]+)?|[-+]?[0-9][0-9_]*[eE][-+]?[0-9]+" // float
		+ "|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\\.[0-9_]*"                                        // sexagesimal float
		+ "|[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN)"
		+ "|[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}(?:(?:[Tt]|[ \\t]+)[0-9]{1,2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]*)?(?:[ \\t]*(?:Z|[-+][0-9]{1,2}(?::[0-9]{2})?))?)?" // timestamp
		+ "|<<|=");

	/**
	 * Quotes the ambiguous strings, rejects NaN and infinite numbers.
	 */
	private static class JsonRepresenter extends StandardRepresenter {
		JsonRepresenter(DumpSettings settings) {
			super(settings);
			RepresentToNode strings = representers.get(String.class);
			representers.put(String.class, data -> {
				String s = data.toString();
				if(AMBIGUOUS.matcher(s).matches() && defaultScalarStyle==ScalarStyle.PLAIN) {
					return representScalar(Tag.STR, s, ScalarStyle.SINGLE_QUOTED);
				}
				return strings.representData(data);
			});
			RepresentToNode numbers = parentClassRepresenters.get(Number.class);
			parentClassRepresenters.put(Number.class, data -> {
				if(data instanceof Double || data instanceof Float) {
					double d = ((Number)data).doubleValue();
					if(Double.isNaN(d) || Double.isInfinite(d)) {
						throw new JsonException(null, "The value {0} cannot be represented in JSON", data);
					}
				}
				if(data instanceof BigDecimal bd) {
					// A plain decimal, read back exactly (as a BigDecimal when a double cannot hold it)
					String s = bd.toString();
					if(s.indexOf('.')<0 && s.indexOf('E')<0) {
						s = s+".0";
					}
					return representScalar(Tag.FLOAT, s);
				}
				return numbers.representData(data);
			});
		}
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
		if(value instanceof Number) {
			// The YAML infinities and NaN were rejected when constructed: an infinity here
			// is a number the factory converts so (an overflowing decimal parsed as a
			// double), as when it parses JSON
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

	private static Load newLoad(JsonFactory factory, Options options) {
		LoadSettings settings = options.isDefault() ? getSettings(factory) : createSettings(factory, options);
		return new Load(settings, new StringKeyConstructor(settings, factory));
	}

	private static LoadSettings getSettings(JsonFactory factory) {
		return factory.getCapability(LoadSettings.class, () -> createSettings(factory, DEFAULT_OPTIONS));
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static LoadSettings createSettings(JsonFactory factory, Options options) {
		return LoadSettings.builder()
				.setLabel("Monflabs factory")
				.setDefaultList(initSize -> factory.createArray(initSize))
				.setDefaultMap(initSize -> (Map)factory.createObject())
				.setCodePointLimit(options.getCodePointLimit())
				.setMaxAliasesForCollections(options.getMaxAliasesForCollections())
				.build();
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
	 * <p>
	 * The numbers are created by the factory, from their text, with the same rules as when it
	 * parses JSON: its integer and decimal types ({@link JsonFactory#defaultInteger()},
	 * {@link JsonFactory#defaultDecimal()}...) apply, and a decimal a double cannot hold is
	 * a {@link BigDecimal} by default.
	 */
	private static class StringKeyConstructor extends StandardConstructor {

		StringKeyConstructor(LoadSettings settings, JsonFactory factory) {
			super(settings);
			ConstructNode ints = tagConstructors.get(Tag.INT);
			tagConstructors.put(Tag.INT, node -> factoryNumber(factory, node, true, ints));
			ConstructNode floats = tagConstructors.get(Tag.FLOAT);
			tagConstructors.put(Tag.FLOAT, node -> factoryNumber(factory, node, false, floats));
		}

		@Override
		protected void constructMapping2ndStep(MappingNode node, Map<Object, Object> mapping) {
			super.constructMapping2ndStep(node, new StringKeyMap(mapping));
		}
	}

	/**
	 * A YAML number, created by the factory from its text. The JSON schema of YAML 1.2 only
	 * resolves the JSON spellings of the numbers; an explicit tag can give the YAML ones too
	 * ({@code !!int 0x1F}, {@code !!int 0o17}, {@code !!float .5}). What the factory doesn't
	 * read ({@code .inf}, {@code .nan}) is left to SnakeYAML, and the infinities and NaN are
	 * rejected, as JSON can't represent them.
	 */
	private static Object factoryNumber(JsonFactory factory, Node node, boolean integer, ConstructNode yaml) {
		if(node instanceof ScalarNode sn) {
			String s = sn.getValue();
			try {
				if(!integer) {
					return factory.parseDecimal(s);
				}
				if(s.startsWith("0x")) {
					return factory.parseInteger(s.substring(2), 16);
				}
				if(s.startsWith("0o")) {
					return factory.parseInteger(s.substring(2), 8);
				}
				return factory.parseInteger(s, 10);
			} catch(JsonException ex) {
				// Not a number the factory reads: SnakeYAML's value
			}
		}
		Object value = yaml!=null ? yaml.construct(node) : null;
		if(value instanceof Double d && (d.isNaN() || d.isInfinite())) {
			throw new JsonException(null, "The YAML value {0} cannot be represented in JSON", value);
		}
		return value;
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
			if(key instanceof String) {
				return key;
			}
			if(key instanceof Map || key instanceof List || key instanceof Set || key instanceof byte[]) {
				throw new JsonException(null, "A YAML collection used as a key cannot be represented in JSON");
			}
			return String.valueOf(key);
		}

		@Override
		public Object put(Object key, Object value) {
			Object k = toKey(key);
			// The duplicate keys are rejected by the parser: a key already here is another
			// key with the same string form (1 and "1")
			if(target.containsKey(k)) {
				throw new JsonException(null, "The YAML key {0} collides with another key once converted to a string", key);
			}
			return target.put(k, value);
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
