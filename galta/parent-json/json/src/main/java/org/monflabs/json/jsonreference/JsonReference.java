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
package org.monflabs.json.jsonreference;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpointer.JsonPointer;
import org.monflabs.util.StringUtil;

/**
 * JSON reference ("$ref") resolution.
 * <p>
 * References are resolved in the scope of the document holding them:
 * <ul>
 * <li>the URL part of a reference is resolved against the URL of the document that
 * contains it (the root document's URL is {@link Resolver#getBaseUrl()}, possibly none),
 * so "b.json" inside "dir/a.json" designates "dir/b.json"
 * <li>each external document is loaded once per {@link #resolve} call, whatever the
 * number of references to it, so its objects keep their identity and cross-document
 * recursion produces a cyclic graph instead of an infinite expansion
 * <li>the fragment is either empty (the whole document) or a percent-encoded JSON pointer
 * starting with '/'. A plain name fragment ("#foo") is an error, except for schemas
 * where it designates a "$anchor"
 * <li>a JSON pointer can go through other references: they are followed along the way
 * </ul>
 *
 * https://json-spec.readthedocs.io/reference.html
 * https://redocly.com/learn/openapi/ref-guide
 * https://datatracker.ietf.org/doc/html/draft-pbryan-zyp-json-ref-03
 *
 * @author priand
 */
public class JsonReference {

	public static final String REF_PROP = "$ref";

	/**
	 * Loads the documents designated by references.
	 * <p>
	 * {@link #apply(JsonFactory, String)} is called with the URL of a document, already
	 * resolved against the URL of the referring document (see {@link #resolveUrl}), and
	 * at most once per distinct URL during a resolution. The empty string designates the
	 * root document.
	 */
	public static class Resolver implements BiFunction<JsonFactory,String,Object> {
		private Object root;
		private String baseUrl;
		public Resolver(Object root) {
			this(root, null);
		}
		/**
		 * @param baseUrl the URL of the root document, against which its relative references
		 * are resolved. It can be null when the root document has no URL.
		 */
		public Resolver(Object root, String baseUrl) {
			this.root = root;
			this.baseUrl = baseUrl;
		}
		public Object getRoot() {
			return root;
		}
		public String getBaseUrl() {
			return baseUrl;
		}
		@Override
		public Object apply(JsonFactory t, String u) {
			if(StringUtil.isEmpty(u) || u.equals(baseUrl)) {
				return root;
			}
			throw new JsonException(null,"Cannot resolve $ref '{0}'", u);
		}
		/**
		 * Resolve the URL of a reference against the URL of the document holding it.
		 * The default implementation follows RFC 3986 when both are URIs, keeps the URL
		 * as is when the document has no URL, and otherwise replaces the last segment
		 * of the document path.
		 */
		public String resolveUrl(String documentUrl, String url) {
			if(StringUtil.isEmpty(url)) {
				return documentUrl;
			}
			if(StringUtil.isEmpty(documentUrl)) {
				return url;
			}
			try {
				URI r = new URI(url);
				if(r.isAbsolute()) {
					return url;
				}
				URI b = new URI(documentUrl);
				if(!b.isOpaque()) {
					return b.resolve(r).toString();
				}
			} catch(URISyntaxException ex) {
				// Not URIs: resolve them as plain paths below
			}
			if(url.indexOf(':')>0 || url.startsWith("/")) {
				return url;
			}
			int slash = Math.max(documentUrl.lastIndexOf('/'), documentUrl.lastIndexOf('\\'));
			return slash>=0 ? documentUrl.substring(0,slash+1)+url : url;
		}
	}

	/**
	 * Resolve the references in a JSON value.
	 * <p>
	 * An object with a string "$ref" property is replaced by the value it refers to
	 * (other properties of that object are ignored, as the specification requires).
	 * Chained references are followed, a reference that cannot be resolved throws a
	 * {@link JsonException}, as does a cycle made only of references ("#/a" -> "#/b" -> "#/a").
	 * Recursive structures (a schema node referring to one of its parents, or two documents
	 * referring to each other) are legal and produce a cyclic graph.
	 * <p>
	 * When keepReferences is true, a container reached through a reference remembers it
	 * ({@link JsonContainer#getReference()}). A container being the target of several
	 * references keeps the first one encountered (in document order).
	 */
	public static Object resolve(JsonFactory factory, Object json, Resolver resolver, boolean keepReferences) {
		return resolve(factory, json, resolver, keepReferences, false);
	}

	/**
	 * Resolve the references in a JSON value, possibly a JSON schema.
	 * <p>
	 * When schema is true, the value is processed as a JSON Schema: the values of the
	 * data keywords ("const", "enum", "default", "examples") are left untouched (a "$ref"
	 * there is data, not a reference), the keys of the "properties"-like keywords are not
	 * mistaken for keywords, a "$id" identifies a (sub)schema that a reference can target by
	 * its absolute URL, and a "#name" fragment designates a "$anchor". Relative references
	 * are still resolved against the URL of the document holding them.
	 */
	public static Object resolve(JsonFactory factory, Object json, Resolver resolver, boolean keepReferences, boolean schema) {
		Context ctx = new Context(factory, resolver, keepReferences, schema);
		ctx.register(ctx.rootKey, json);
		return ctx.resolveNode(json, ctx.rootKey, false);
	}

	private static boolean isReference(Object json) {
		return json instanceof JsonObject o && o.get(REF_PROP) instanceof String;
	}

	// Schema keywords whose values are data, not schemas
	private static final Set<String> DATA_KEYWORDS = Set.of("const", "enum", "default", "examples");
	// Schema keywords whose values map names (not keywords) to schemas
	private static final Set<String> NAME_MAP_KEYWORDS = Set.of("properties", "patternProperties", "$defs", "definitions", "dependentSchemas", "dependencies");

	private record Located(Object value, String url) {
	}

	private static final class Context {
		final JsonFactory factory;
		final Resolver resolver;
		final boolean keepReferences;
		final boolean schema;
		final String rootKey;
		// Loaded documents (and, for schemas, identified subschemas), by normalized URL
		final Map<String,Object> documents = new HashMap<>();
		// Schema anchors, by "url#name"
		final Map<String,Object> anchors = new HashMap<>();
		final Set<Object> walked = Collections.newSetFromMap(new IdentityHashMap<>());
		// The references being followed (normalized url#fragment), to detect cycles
		final Set<String> inProgress = new HashSet<>();

		Context(JsonFactory factory, Resolver resolver, boolean keepReferences, boolean schema) {
			this.factory = factory;
			this.resolver = resolver;
			this.keepReferences = keepReferences;
			this.schema = schema;
			this.rootKey = normalize(resolver.getBaseUrl());
		}

		void register(String key, Object doc) {
			documents.put(key, doc);
			if(schema) {
				scanIdentifiers(doc, key, key, false, Collections.newSetFromMap(new IdentityHashMap<>()));
			}
		}

		Object resolveNode(Object json, String url, boolean nameMap) {
			if(!nameMap && isReference(json)) {
				String sref = (String)((JsonObject)json).get(REF_PROP);
				Located l = deref(json, url);
				if(keepReferences && l.value instanceof JsonContainer jc && jc.getReference()==null) {
					jc.setReference(sref);
				}
				// The target can itself hold references (e.g. when it comes from another document)
				walk(l.value, l.url, false);
				return l.value;
			}
			walk(json, url, nameMap);
			return json;
		}

		private void walk(Object json, String url, boolean nameMap) {
			if(!(json instanceof JsonObject || json instanceof JsonArray) || !walked.add(json)) {
				return;
			}
			if(json instanceof JsonObject o) {
				Map<String,Object> updates = null;
				for(Map.Entry<String,Object> e: o.entrySet()) {
					String key = e.getKey();
					if(schema && !nameMap && DATA_KEYWORDS.contains(key)) {
						continue;
					}
					Object value = e.getValue();
					// Arrays nested in objects hold references too
					if(value instanceof JsonObject || value instanceof JsonArray) {
						boolean childNameMap = schema && !nameMap && NAME_MAP_KEYWORDS.contains(key);
						Object c = resolveNode(value, url, childNameMap);
						if(c!=value) {
							if(updates==null) {
								updates = new LinkedHashMap<>();
							}
							updates.put(key, c);
						}
					}
				}
				if(updates!=null) {
					o.putAll(updates);
				}
			} else if(json instanceof JsonArray a) {
				int length = a.size();
				for(int i=0; i<length; i++) {
					Object o = a.get(i);
					if(o instanceof JsonObject || o instanceof JsonArray) {
						Object c = resolveNode(o, url, false);
						if(c!=o) {
							a.set(i, c);
						}
					}
				}
			}
		}

		/**
		 * Follow a chain of references, up to a value that is not a reference.
		 */
		Located deref(Object value, String url) {
			if(!isReference(value)) {
				return new Located(value, url);
			}
			List<String> added = new ArrayList<>();
			try {
				while(isReference(value)) {
					String r = (String)((JsonObject)value).get(REF_PROP);
					String u = r;
					String fragment = "";
					int anchor = r.indexOf('#');
					if(anchor>=0) {
						u = r.substring(0,anchor).trim();
						fragment = decodeFragment(r.substring(anchor+1).trim(), r);
					}
					String docUrl = StringUtil.isEmpty(u) ? url : resolver.resolveUrl(url, u);
					String key = normalize(docUrl);
					String chainKey = key+"#"+fragment;
					if(!inProgress.add(chainKey)) {
						throw new JsonException(null,"Circular $ref '{0}'", r);
					}
					added.add(chainKey);
					Object doc = document(docUrl, key, r);
					Located l = locate(doc, key, fragment, r);
					value = l.value;
					url = l.url;
				}
				return new Located(value, url);
			} finally {
				inProgress.removeAll(added);
			}
		}

		private Object document(String docUrl, String key, String ref) {
			if(documents.containsKey(key)) {
				return documents.get(key);
			}
			Object doc;
			try {
				doc = resolver.apply(factory, docUrl);
			} catch(JsonException ex) {
				throw ex;
			} catch(RuntimeException ex) {
				throw new JsonException(ex,"Cannot resolve $ref '{0}'", ref);
			}
			if(doc==null) {
				throw new JsonException(null,"Cannot resolve $ref '{0}'", ref);
			}
			register(key, doc);
			return doc;
		}

		private Located locate(Object doc, String url, String fragment, String ref) {
			if(fragment.isEmpty()) {
				return new Located(doc, url);
			}
			if(fragment.charAt(0)!='/') {
				Object a = schema ? anchors.get(url+"#"+fragment) : null;
				if(a!=null) {
					return new Located(a, url);
				}
				throw new JsonException(null,"Cannot resolve $ref '{0}': the fragment must be empty or a JSON pointer starting with '/'", ref);
			}
			Object[] parts = JsonPointer.of(fragment).getParts();
			Object cur = doc;
			for(int i=0; i<parts.length; i++) {
				Object part = parts[i];
				// A reference along the way is followed (the document itself is navigated
				// as is: its "$ref" member, if any, is a sibling of the pointed definitions)
				if(i>0) {
					Located l = deref(cur, url);
					cur = l.value;
					url = l.url;
				}
				if(cur instanceof JsonObject o) {
					String ks = part.toString();
					if(!o.containsKey(ks)) {
						throw new JsonException(null,"Cannot resolve $ref '{0}'", ref);
					}
					cur = o.get(ks);
				} else if(cur instanceof JsonArray a) {
					int index = arrayIndex(part);
					if(index<0 || index>=a.size()) {
						throw new JsonException(null,"Cannot resolve $ref '{0}'", ref);
					}
					cur = a.get(index);
				} else {
					throw new JsonException(null,"Cannot resolve $ref '{0}'", ref);
				}
			}
			return new Located(cur, url);
		}

		/**
		 * Register the "$id" and "$anchor" of a schema document.
		 */
		private void scanIdentifiers(Object json, String docKey, String base, boolean nameMap, Set<Object> visited) {
			if(!(json instanceof JsonObject || json instanceof JsonArray) || !visited.add(json)) {
				return;
			}
			if(json instanceof JsonObject o) {
				if(!nameMap) {
					if(o.get("$id") instanceof String id && !id.isEmpty()) {
						int hash = id.indexOf('#');
						String idUrl = hash>=0 ? id.substring(0,hash) : id;
						String idFragment = hash>=0 ? id.substring(hash+1) : "";
						if(!idUrl.isEmpty()) {
							base = normalize(resolver.resolveUrl(base, idUrl));
							documents.putIfAbsent(base, o);
						}
						// draft 6/7 "$id": "#name" is an anchor
						if(!idFragment.isEmpty() && idFragment.charAt(0)!='/') {
							addAnchor(docKey, base, idFragment, o);
						}
					}
					if(o.get("$anchor") instanceof String name && !name.isEmpty()) {
						addAnchor(docKey, base, name, o);
					}
				}
				for(Map.Entry<String,Object> e: o.entrySet()) {
					String key = e.getKey();
					if(!nameMap && DATA_KEYWORDS.contains(key)) {
						continue;
					}
					scanIdentifiers(e.getValue(), docKey, base, !nameMap && NAME_MAP_KEYWORDS.contains(key), visited);
				}
			} else {
				for(Object v: (JsonArray)json) {
					scanIdentifiers(v, docKey, base, false, visited);
				}
			}
		}
		// An anchor is known in its resource (by "$id"), and in the document holding it as
		// relative references are resolved against the document URL
		private void addAnchor(String docKey, String base, String name, Object o) {
			anchors.putIfAbsent(base+"#"+name, o);
			anchors.putIfAbsent(docKey+"#"+name, o);
		}
	}

	// RFC 6901 array index: a canonical non negative integer
	private static int arrayIndex(Object part) {
		if(part instanceof Integer i) {
			return i;
		}
		String s = part.toString();
		int l = s.length();
		if(l==0 || l>10 || (l>1 && s.charAt(0)=='0')) {
			return -1;
		}
		long v = 0;
		for(int i=0; i<l; i++) {
			char c = s.charAt(i);
			if(c<'0' || c>'9') {
				return -1;
			}
			v = v*10 + (c-'0');
		}
		return v>Integer.MAX_VALUE ? -1 : (int)v;
	}

	private static String normalize(String url) {
		if(StringUtil.isEmpty(url)) {
			return "";
		}
		try {
			return new URI(url).normalize().toString();
		} catch(URISyntaxException ex) {
			return url;
		}
	}

	/**
	 * Decode a percent-encoded URI fragment (RFC 6901 section 6): "#/a%25b" is the key "a%b".
	 * A '+' is a plus sign, not a space.
	 */
	static String decodeFragment(String fragment, String ref) {
		if(fragment.indexOf('%')<0) {
			return fragment;
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(fragment.length());
		StringBuilder b = new StringBuilder(fragment.length());
		int length = fragment.length();
		for(int i=0; i<length; ) {
			char c = fragment.charAt(i);
			if(c=='%') {
				bytes.reset();
				while(i<length && fragment.charAt(i)=='%') {
					if(i+2>=length) {
						throw new JsonException(null,"Invalid percent-encoding in $ref '{0}'", ref);
					}
					int h = Character.digit(fragment.charAt(i+1),16);
					int l = Character.digit(fragment.charAt(i+2),16);
					if(h<0 || l<0) {
						throw new JsonException(null,"Invalid percent-encoding in $ref '{0}'", ref);
					}
					bytes.write((h<<4)|l);
					i += 3;
				}
				byte[] bs = bytes.toByteArray();
				try {
					b.append(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bs)));
				} catch(java.nio.charset.CharacterCodingException ex) {
					throw new JsonException(ex,"Invalid percent-encoding in $ref '{0}'", ref);
				}
			} else {
				b.append(c);
				i++;
			}
		}
		return b.toString();
	}

	/**
	 * Find the containers that were resolved from a reference.
	 * Each container is reported once, even in a cyclic (recursive) graph.
	 */
	public static void findReferences(JsonContainer c, BiConsumer<JsonContainer,String> cb) {
		findReferences(c, cb, Collections.newSetFromMap(new IdentityHashMap<>()));
	}
	private static void findReferences(JsonContainer c, BiConsumer<JsonContainer,String> cb, Set<Object> visited) {
		if(!visited.add(c)) {
			return;
		}
		String ref = c.getReference();
		if(ref!=null) {
			cb.accept(c, ref);
		}
		for(Object v: c.values()) {
			if(v instanceof JsonContainer cc) {
				findReferences(cc, cb, visited);
			}
		}
	}
}
