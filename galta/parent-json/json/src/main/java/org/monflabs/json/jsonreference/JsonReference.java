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

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
 * JsonReference.
 * 
 * https://json-spec.readthedocs.io/reference.html
 * https://redocly.com/learn/openapi/ref-guide
 * https://datatracker.ietf.org/doc/html/draft-pbryan-zyp-json-ref-03
 * 
 * @author priand
 */
public class JsonReference {
	
	public static final String REF_PROP = "$ref";
	
	public static class Resolver implements BiFunction<JsonFactory,String,Object> {
		private Object root;
		public Resolver(Object root) {
			this.root = root;
		}
		@Override
		public Object apply(JsonFactory t, String u) {
			if(StringUtil.isEmpty(u)) {
				return root;
			}
			throw new JsonException(null,"Cannot resolve $ref '{0}'", u);
		}
		/**
		 * Identify the target of a reference for cycle detection: a local "#/..."
		 * reference is qualified by the document it is relative to.
		 */
		protected String documentKey(String ref) {
			return ref;
		}
	}
	
	/**
	 * Scope of a document reached through a reference: local references resolve
	 * against that document, others are delegated.
	 */
	private static class DocumentResolver extends Resolver {
		private final Resolver parent;
		private final String url;
		DocumentResolver(Resolver parent, String url, Object document) {
			super(document);
			this.parent = parent;
			this.url = url;
		}
		@Override
		public Object apply(JsonFactory factory, String u) {
			if(StringUtil.isEmpty(u)) {
				return super.apply(factory, u);
			}
			return parent.apply(factory, u);
		}
		@Override
		protected String documentKey(String ref) {
			return ref.startsWith("#") ? url+ref : ref;
		}
	}
	
	/**
	 * Resolve the references in a JSON value.
	 * <p>
	 * An object with a string "$ref" property is replaced by the value it refers to
	 * (other properties of that object are ignored, as the specification requires).
	 * Chained references are followed, a reference that cannot be resolved throws a
	 * {@link JsonException}, as does a cycle made only of references ("#/a" -> "#/b" -> "#/a").
	 * Recursive structures (a schema node referring to one of its parents) are legal and
	 * produce a cyclic graph.
	 */
	public static Object resolve(JsonFactory factory, Object json, Resolver resolver, boolean keepReferences) {
		return resolveNode(factory, json, resolver, keepReferences, Collections.newSetFromMap(new IdentityHashMap<>()));
	}
	
	private static boolean isReference(Object json) {
		return json instanceof JsonObject o && o.get(REF_PROP) instanceof String;
	}
	
	private static Object resolveNode(JsonFactory factory, Object json, Resolver resolver, boolean keepReferences, Set<Object> visited) {
		if(isReference(json)) {
			String sref = (String)((JsonObject)json).get(REF_PROP);
			Set<String> chain = new HashSet<>();
			Object resolved = json;
			// A reference is relative to the document holding it: once a reference leads
			// to another document, "#/..." refers to that document, not to the root one
			Resolver scope = resolver;
			while(isReference(resolved)) {
				String r = (String)((JsonObject)resolved).get(REF_PROP);
				if(!chain.add(scope.documentKey(r))) {
					throw new JsonException(null,"Circular $ref '{0}'", r);
				}
				String url = r;
				String ptr = null;
				int anchor = r.indexOf('#');
				if(anchor>=0) {
					url = r.substring(0,anchor).trim();
					ptr = r.substring(anchor+1).trim();
				}
				Object doc = scope.apply(factory, url);
				if(StringUtil.isNotEmpty(url)) {
					scope = new DocumentResolver(scope, url, doc);
				}
				resolved = read(doc, ptr, r);
			}
			if(keepReferences && resolved instanceof JsonContainer jc) {
				jc.setReference(sref);
			}
			// The target can itself hold references (e.g. when it comes from another document)
			walk(factory, resolved, scope, keepReferences, visited);
			return resolved;
		}
		walk(factory, json, resolver, keepReferences, visited);
		return json;
	}
	
	private static void walk(JsonFactory factory, Object json, Resolver resolver, boolean keepReferences, Set<Object> visited) {
		if(!(json instanceof JsonObject || json instanceof JsonArray) || !visited.add(json)) {
			return;
		}
		if(json instanceof JsonObject o) {
			Map<String,Object> updates = null;
			for(Map.Entry<String,Object> e: o.entrySet()) {
				Object value = e.getValue(); 
				// Arrays nested in objects hold references too
				if(value instanceof JsonObject || value instanceof JsonArray) {
					Object c = resolveNode(factory, value, resolver, keepReferences, visited);
					if(c!=value) {
						if(updates==null) {
							updates = new LinkedHashMap<>();
						}
						updates.put(e.getKey(), c);
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
					Object c = resolveNode(factory, o, resolver, keepReferences, visited);
					if(c!=o) {
						a.set(i, c);
					}
				}
			}
		}
	}
	
	private static Object read(Object c, String ptr, String path) {
		if(StringUtil.isEmpty(ptr)) {
			return c;
		}
		// The fragment is percent-encoded (RFC 6901 section 6): "#/a%25b" is the key "a%b"
		if(ptr.indexOf('%')>=0) {
			ptr = java.net.URLDecoder.decode(ptr.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
		}
		JsonPointer p = JsonPointer.of(ptr);
		Object v = p.read(c);
		if(v==null && !p.exists(c)) {
			throw new JsonException(null,"Cannot resolve $ref '{0}'", path);
		}
		return v;
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
