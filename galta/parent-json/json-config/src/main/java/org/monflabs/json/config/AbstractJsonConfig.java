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
package org.monflabs.json.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpointer.JsonPointer;
import org.monflabs.json.jsonreference.JsonReference;
import org.monflabs.json.jsonreference.JsonReference.Resolver;
import org.monflabs.json.stringifier.JsonStringifier;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.StringUtil;
import org.monflabs.util.config.ConfigException;
import org.monflabs.util.iterators.Iterators;

/**
 * 
 */ 
public abstract class AbstractJsonConfig implements JsonConfig {

	public static abstract class ConfigBuilder<C extends AbstractJsonConfig, T extends ConfigBuilder<C,?>> extends ObjectBuilder<C> {
		private ValueEncryptor encryptor;
		private boolean autoSave=true;
		
		protected ConfigBuilder() {}
		
		@SuppressWarnings("unchecked")
		public T encryptor(ValueEncryptor encryptor) {
			this.encryptor = encryptor;
			return (T)this;
		}
		@SuppressWarnings("unchecked")
		public T autoSave(boolean autoSave) {
			this.autoSave = autoSave;
			return (T)this;
		}
	}

	
	private class JsonUpdater implements Updater {
		private JsonObject content;
		private boolean hasChanges;
		private boolean cancelled;
		
		JsonUpdater(JsonObject content) {
			this.content = content;
		}
		
		@Override
		public boolean put(String key, String value) {
			return putValue(key, value);
		}
		// Typed values are stored as JSON booleans/numbers, not as their string form
		@Override
		public boolean put(String key, boolean value) {
			return putValue(key, value);
		}
		@Override
		public boolean put(String key, int value) {
			return putValue(key, value);
		}
		@Override
		public boolean put(String key, long value) {
			return putValue(key, value);
		}
		@Override
		public boolean put(String key, double value) {
			return putValue(key, value);
		}
		
		private boolean putValue(String key, Object value) {
			if(StringUtil.isEmpty(key)) {
				return false;
			}
			JsonObject o = content;
			String[] parts = keyParts(key);
			for(int i=0; i<parts.length-1; i++) {
				String k = parts[i];
				if(o.containsKey(k)) {
					Object v = o.get(k);
					if(v instanceof JsonObject jo) {
						o = jo;
					} else {
						return false;
					}
				} else {
					JsonObject v = JsonObject.create();
					o.put(k, v);
					o = v;
				}
			}
			
			String k = parts[parts.length-1];
			Object v = o.get(k);
			if(v instanceof JsonObject) {
				return false;
			}
			o.put(k, value);
			hasChanges = true;
			return true;
		}
		
		@Override
		public boolean remove(String key) {
			if(StringUtil.isEmpty(key)) {
				return false;
			}
			JsonObject o = content;
			String[] parts = keyParts(key);
			for(int i=0; i<parts.length-1; i++) {
				String k = parts[i];
				if(o.containsKey(k)) {
					Object v = o.get(k);
					if(v instanceof JsonObject jo) {
						o = jo;
					} else {
						return false;
					}
				} else {
					return false;
				}
			}
			
			String k = parts[parts.length-1];
			if(!o.containsKey(k)) {
				return false;
			}
			o.remove(k);
			hasChanges = true;
			return true;
		}
		
		@Override
		public void cancel() {
			cancelled = true;
		}
	}
	
	/**
	 * A cycle of $ref between resources.
	 */
	private static class CircularReferenceException extends ConfigException {
		private static final long serialVersionUID = 1L;
		CircularReferenceException(String resource, Deque<String> chain) {
			super(null, "Circular $ref: resource '{0}' references itself (through {1})", resource, chain);
		}
	}

	private static class ConfigResolver extends Resolver {
		private final AbstractJsonConfig config;
		// The resources loaded by this resolution: a resource referenced several times is
		// loaded once, and shared, so an update through one reference is not overwritten by
		// a stale copy when the configuration is saved
		private final Map<String,JsonObject> loaded = new HashMap<>();
		// The resources being loaded, to detect the cycles
		private final Deque<String> loading = new ArrayDeque<>();
		public ConfigResolver(AbstractJsonConfig config, Object root) {
			super(root);
			this.config = config;
		}
		@Override
		public Object apply(JsonFactory factory, String resource) {
			if(StringUtil.isNotEmpty(resource)) {
				try {
					return loadResource(resource);
				} catch(CircularReferenceException e) {
					throw e;
				} catch(Exception e) {
					throw new JsonException(e,"Cannot load $ref resource '{0}'", resource);
				}
			}
			return super.apply(factory, resource);
		}
		protected JsonObject loadResource(String resource) {
			JsonObject json = loaded.get(resource);
			if(json!=null) {
				return json;
			}
			if(loading.contains(resource)) {
				throw new CircularReferenceException(resource, loading);
			}
			// Loaded as stored: load() decrypts the resolved content as a whole
			json = config.loadJson(resource);
			if(json==null) {
				throw new ConfigException(null, "Resource '{0}' not found", resource);
			}
			// Load the resources it references first, to detect the cycles (they would make
			// the resolution endless)
			loading.push(resource);
			try {
				for(String r: referencedResources(json, new LinkedHashSet<>())) {
					try {
						loadResource(r);
					} catch(CircularReferenceException e) {
						throw e;
					} catch(RuntimeException e) {
						// Reported when the reference is resolved
					}
				}
			} finally {
				loading.pop();
			}
			loaded.put(resource, json);
			return json;
		}
		// The resources referenced by a (raw) JSON value
		private static Set<String> referencedResources(Object json, Set<String> refs) {
			if(json instanceof JsonObject o) {
				if(o.get(JsonReference.REF_PROP) instanceof String r) {
					String res = resourceOf(r);
					if(!res.isEmpty()) {
						refs.add(res);
					}
					return refs;
				}
				for(Object v: o.values()) {
					referencedResources(v, refs);
				}
			} else if(json instanceof JsonArray a) {
				for(Object v: a) {
					referencedResources(v, refs);
				}
			}
			return refs;
		}
	}

	// The resource part of a reference ("db.json#/a" -> "db.json"), empty for a local reference
	private static String resourceOf(String ref) {
		int idx = ref.indexOf('#');
		return (idx>=0 ? ref.substring(0,idx) : ref).trim();
	}
	// The fragment (JSON pointer) of a reference, or null when there is none
	private static JsonPointer fragmentOf(String ref) {
		int idx = ref.indexOf('#');
		if(idx<0) {
			return null;
		}
		String ptr = ref.substring(idx+1).trim();
		// The fragment is percent-encoded (RFC 6901 section 6), like JsonReference reads it
		if(ptr.indexOf('%')>=0) {
			ptr = java.net.URLDecoder.decode(ptr.replace("+", "%2B"), StandardCharsets.UTF_8);
		}
		return ptr.isEmpty() ? JsonPointer.EMPTY : JsonPointer.of(ptr);
	}


	// Replaced as a whole (never mutated once published), so it can be read without the lock
	private volatile JsonObject content;
	private boolean autoSave;
	private ValueEncryptor encryptor;
	
	protected AbstractJsonConfig(ConfigBuilder<?,?> b) {
		this.encryptor = b.encryptor;
		this.autoSave = b.autoSave;
	}

	/**
	 * Load a JSON resource, as stored. Encrypted values are not decrypted here: the
	 * encryption is applied to the whole configuration, once its references are resolved,
	 * so the encryption predicate always sees the full key path of a value, even when
	 * the value lives in a referenced resource.
	 */
	protected JsonObject loadJson(String path) {
		InputStream is = getResource(path);
		if(is!=null) {
			try {
				Object json = JsonFactory.get().parse(is);
				if(json instanceof JsonObject jo) {
					return jo;
				}
			} finally {
				IOStreamUtil.close(is);
			}
			throw new ConfigException(null, "JSON resource is not an object");
		}
		return null;
	}
	/**
	 * Write a JSON resource, as is (the values must already be encrypted).
	 * <p>
	 * The nested containers resolved from a reference to another resource are written as
	 * references (<code>{ "$ref": ... }</code>), their content being saved in their own resource.
	 */
	protected void saveJson(String path, JsonObject json) {
		// The main resource can be a reference to another one ({ "$ref": "all.json" }): it is
		// written as such
		boolean rootRef = path==null && json.getReference()!=null && !resourceOf(json.getReference()).isEmpty();
		Object o = rootRef ? json : writableCopy(json, path, JsonPointer.EMPTY);
		setResource(path, os -> {
			try {
				Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
				JsonStringifier.WriterSerializer s = new JsonStringifier.WriterSerializer();
				s.setCompact(true);
				s.setOutputReferences(true);
				s.stringify(w, o);
				w.flush();
			} catch(IOException ex) {
				throw new ConfigException(ex, "Error while writing resource '{0}'",path);
			}
		} );
	}

	/**
	 * A copy of the content of a resource to write. A nested container resolved from a
	 * reference is kept as is (written as a reference), unless it is the target of a
	 * reference to this very place of the resource (a part of the resource referenced from
	 * elsewhere), which is part of the content.
	 */
	private static Object writableCopy(Object v, String resource, JsonPointer at) {
		if(v instanceof JsonObject o) {
			JsonObject c = JsonObject.create();
			for(Map.Entry<String,Object> e: o.entrySet()) {
				c.put(e.getKey(), writableChild(e.getValue(), resource, at.getChild(e.getKey())));
			}
			return c;
		}
		if(v instanceof JsonArray a) {
			JsonArray c = JsonArray.create(a.size());
			for(int i=0; i<a.size(); i++) {
				c.add(writableChild(a.get(i), resource, at.getChild(i)));
			}
			return c;
		}
		return v;
	}
	private static Object writableChild(Object v, String resource, JsonPointer at) {
		if(v instanceof JsonContainer c && c.getReference()!=null) {
			String ref = c.getReference();
			String res = resourceOf(ref);
			JsonPointer frag = fragmentOf(ref);
			boolean here = (res.isEmpty() || res.equals(resource)) && frag!=null && frag.equals(at);
			if(!here) {
				return v;
			}
		}
		return writableCopy(v, resource, at);
	}

	private record Fragment(JsonPointer pointer, JsonContainer content) {}

	/**
	 * Write the main resource and the referenced resources of an (already encrypted) content.
	 * <p>
	 * A resource referenced as a whole is written from its content. A resource of which only
	 * parts are referenced (<code>secrets.json#/prod</code>) is read again, and the referenced
	 * parts are replaced before it is written back.
	 */
	private void saveResources(JsonObject o) {
		saveJson(null,o);
		Map<String,JsonObject> whole = new LinkedHashMap<>();
		Map<String,List<Fragment>> fragments = new LinkedHashMap<>();
		JsonReference.findReferences(o, (c,r) -> {
			String res = resourceOf(r);
			if(res.isEmpty()) {
				// A local reference is part of its document
				return;
			}
			JsonPointer frag = fragmentOf(r);
			if(frag==null || frag.isEmpty()) {
				if(c instanceof JsonObject jo) {
					whole.putIfAbsent(res, jo);
				}
			} else {
				fragments.computeIfAbsent(res, (k) -> new ArrayList<>()).add(new Fragment(frag, c));
			}
		});
		whole.forEach( (r,jo) -> saveJson(r,jo) );
		fragments.forEach( (r,list) -> {
			// A resource also referenced as a whole already holds its parts
			if(!whole.containsKey(r)) {
				saveFragments(r, list);
			}
		});
	}
	private void saveFragments(String resource, List<Fragment> fragments) {
		JsonObject doc = loadJson(resource);
		if(doc==null) {
			throw new ConfigException(null, "Resource '{0}' not found", resource);
		}
		// The enclosing parts first, so a part nested in another one is applied last
		fragments.sort(Comparator.comparingInt((f) -> f.pointer().size()));
		for(Fragment f: fragments) {
			Object part = writableCopy(f.content(), resource, f.pointer());
			if(!f.pointer().setValue(doc, part)) {
				throw new ConfigException(null, "Cannot write the $ref '{0}#{1}' back to its resource", resource, f.pointer().toJsonPointerString());
			}
		}
		saveJson(resource, doc);
	}

	@Override
	public boolean isAutoSave() {
		return autoSave;
	}
	
	public ValueEncryptor getEncryptor() {
		return encryptor;
	}

	@Override
	public JsonObject getContent() {
		return content;
	}
	

	/**
	 * Runs an operation that reads and/or writes the storage, while holding the lock of the
	 * storage, if any. By default there is none: the operations are only synchronized on the
	 * configuration. A storage shared by several configurations (or processes) overrides it.
	 */
	protected <T> T withStorageLock(Supplier<T> operation) {
		return operation.get();
	}

	/**
	 * Whether the configuration is reloaded before an update, so the update applies to the
	 * current content of the storage, including the changes made by another configuration
	 * using the same storage. Only when auto save is on. Default is false.
	 */
	protected boolean reloadBeforeUpdate() {
		return false;
	}

	/**
	 * (Re)load the configuration.
	 * <p>
	 * The new content is built and decrypted before it replaces the current one, so a
	 * reader never sees a partially loaded or still encrypted content. If the load fails,
	 * the current content is kept (an empty one on the first load) and the exception is
	 * thrown.
	 */
	public final synchronized void load() {
		withStorageLock( () -> {
			doLoad();
			return null;
		});
	}
	private void doLoad() {
		JsonObject loaded = null;
		try {
			JsonObject main = loadJson(null);
			if(main==null) {
				main = JsonObject.create();
			}

			JsonReference.Resolver r = new ConfigResolver(this, main);
			JsonObject c = (JsonObject)JsonReference.resolve(JsonFactory.get(), main, r, true);
			if(encryptor!=null) {
				// Save the content again if some values are not encrypted, or have to be
				// encrypted again with the current format
				boolean save = !isReadOnly() && needsEncryption(c);
				decrypt(c);
				if(save) {
					saveResources(encryptAll(c));
				}
			}
			loaded = c;
		} finally {
			if(loaded!=null) {
				content = loaded;
			} else if(content==null) {
				// in case of an error...
				content = JsonObject.create();
			}
		}
	}
	/**
	 * Save the configuration and its referenced resources.
	 * @return true if the configuration was saved, false if it is read-only
	 * @throws ConfigException if the configuration cannot be saved
	 */
	public final synchronized boolean save() {
		if(!isReadOnly()) {
			withStorageLock( () -> {
				saveContent(content);
				return null;
			});
			return true;
		}
		return false;
	}
	private void saveContent(JsonObject c) {
		try {
			saveResources(encryptor!=null ? encryptAll(c) : c);
		} catch(ConfigException ex) {
			throw ex;
		} catch(Exception ex) {
			throw new ConfigException(ex, "Error while saving the configuration");
		}
	}


	@Override
	public synchronized Iterator<String> keysOf(String key, ENUM_KEYS type) {
		if(StringUtil.isEmpty(key)) {
			return Collections.<String>emptySet().iterator();
		}
		String[] parts = keyParts(key);
		JsonObject p = findFolder(content, parts, parts.length);
		if(p!=null) {
			Iterator<String> it = switch(type) {
				case ALL -> p.keySet().iterator();
				case VALUES -> Iterators.filter( p.keySet().iterator(), (k) -> !(p.get(k) instanceof JsonContainer) );
				case FOLDERS -> Iterators.filter( p.keySet().iterator(), (k) -> p.get(k) instanceof JsonObject );
			};
			return it;
		}
		return Collections.<String>emptySet().iterator();
	}

	@Override
	public synchronized boolean has(String key) {
		if(StringUtil.isEmpty(key)) {
			return false;
		}
		String[] parts = keyParts(key);
		JsonObject p = findFolder(content, parts, parts.length-1);
		if(p!=null) {
			return p.containsKey(parts[parts.length-1]);
		}
		return false;
	}

	@Override
	public synchronized boolean isValue(String key) {
		if(StringUtil.isEmpty(key)) {
			return false;
		}
		String[] parts = keyParts(key);
		JsonObject p = findFolder(content, parts, parts.length-1);
		if(p!=null) {
			String k = parts[parts.length-1];
			return p.containsKey(k) && !(p.get(k) instanceof JsonContainer);
		}
		return false;
	}

	@Override
	public synchronized boolean isFolder(String key) {
		if(StringUtil.isEmpty(key)) {
			return false;
		}
		String[] parts = keyParts(key);
		JsonObject p = findFolder(content, parts, parts.length-1);
		if(p!=null) {
			String k = parts[parts.length-1];
			return p.containsKey(k) && p.get(k) instanceof JsonObject;
		}
		return false;
	}

	@Override
	public synchronized Object getValue(String key) {
		if(StringUtil.isEmpty(key)) {
			return null;
		}
		String[] parts = keyParts(key);
		JsonObject p = findFolder(content, parts, parts.length-1);
		if(p!=null) {
			String k = parts[parts.length-1];
			if(p.containsKey(k)) {
				Object o = p.get(k);
				if(o!=null) {
					if(o instanceof JsonContainer) {
						return null;
					}
					return o;
				}
			}
		}
		return null;
	}

	/**
	 * Updates the configuration. The updates are applied to a copy of the content, which
	 * replaces the content once it is saved (when auto save is on): if the save fails, the
	 * configuration is unchanged and the exception is thrown.
	 */
	@Override
	public synchronized boolean updateValues(Consumer<Updater> updater) {
		if(isReadOnly()) {
			throw new ConfigException(null, "Storage is readonly");
		}
		if(updater==null) {
			return false;
		}
		return withStorageLock( () -> {
			if(isAutoSave() && reloadBeforeUpdate()) {
				doLoad();
			}
			JsonUpdater u = new JsonUpdater((JsonObject)cloneWithReferences(content, new IdentityHashMap<>()));
			updater.accept(u);
			if(!u.cancelled && u.hasChanges) {
				if(isAutoSave()) {
					saveContent(u.content);
				}
				content = u.content;
				return true;
			}
			return false;
		});
	}

	/**
	 * Deep clone that keeps the $ref of the resolved containers, so they are still
	 * saved to their own resource, and the containers shared by several references (a
	 * resource referenced several times) stay shared.
	 */
	private static Object cloneWithReferences(Object v, Map<Object,Object> clones) {
		if(v instanceof JsonObject o) {
			Object done = clones.get(o);
			if(done!=null) {
				return done;
			}
			JsonObject c = JsonObject.create();
			clones.put(o, c);
			for(Map.Entry<String,Object> e: o.entrySet()) {
				c.put(e.getKey(), cloneWithReferences(e.getValue(), clones));
			}
			if(o.getReference()!=null) {
				c.setReference(o.getReference());
			}
			return c;
		}
		if(v instanceof JsonArray a) {
			Object done = clones.get(a);
			if(done!=null) {
				return done;
			}
			JsonArray c = JsonArray.create(a.size());
			clones.put(a, c);
			for(Object item: a) {
				c.add(cloneWithReferences(item, clones));
			}
			if(a.getReference()!=null) {
				c.setReference(a.getReference());
			}
			return c;
		}
		return v;
	}

	private static JsonObject findFolder(JsonObject o, String[] parts, int length) {
		for(int i=0; i<length; i++) {
			String k = parts[i];
			if(!o.containsKey(k)) {
				return null;
			}
			Object v = o.get(k);
			if(!(v instanceof JsonObject)) {
				return null;
			}
			o = (JsonObject)v;
		}
		return o;
	}
	private static String[] keyParts(String key) {
		return StringUtil.splitString(key, SEPARATOR, true);
	}
	
	
	//
	// Serialization encryption/decryption
	//
	/**
	 * Returns an encrypted copy of the content, or null if no value had to be encrypted
	 * (or encrypted again, see {@link ValueEncryptor#needsReencryption(String)}).
	 */
	public JsonObject encrypt(JsonObject content) {
		if(encryptor!=null && needsEncryption(content)) {
			return encryptAll(content);
		}
		return null;
	}
	/**
	 * Returns a copy of a (decrypted) content where every value selected by the encryptor is
	 * encrypted. As the content is decrypted, a plain value that looks like an encrypted one is
	 * encrypted too.
	 */
	private JsonObject encryptAll(JsonObject content) {
		JsonObject clone = (JsonObject)cloneWithReferences(content, new IdentityHashMap<>());
		transform(clone, new String[0], Mode.ENCRYPT, Collections.newSetFromMap(new IdentityHashMap<>()));
		return clone;
	}
	/**
	 * Whether a content (as stored) has values to encrypt, or to encrypt again.
	 */
	private boolean needsEncryption(JsonObject content) {
		return transform(content, new String[0], Mode.CHECK, Collections.newSetFromMap(new IdentityHashMap<>()));
	}
	/**
	 * Decrypts, in place, the encrypted values of a content.
	 * @return true if a value was decrypted
	 */
	public boolean decrypt(JsonObject o) {
		if(encryptor!=null) {
			return transform(o, new String[0], Mode.DECRYPT, Collections.newSetFromMap(new IdentityHashMap<>()));
		}
		return false;
	}

	private enum Mode {
		// Encrypt every selected value
		ENCRYPT,
		// Decrypt the selected values that are encrypted
		DECRYPT,
		// Check if a selected value is not encrypted, or must be encrypted again
		CHECK
	}

	/**
	 * Transforms, in place, the values selected by the encryptor. The value of a property is
	 * checked with the key path of the property; the items of an array (at any depth) with the
	 * key path of the array. A container shared by several references is transformed once.
	 */
	private boolean transform(Object container, String[] path, Mode mode, Set<Object> visited) {
		if(!visited.add(container)) {
			return false;
		}
		boolean res = false;
		if(container instanceof JsonObject o) {
			String[] a = Arrays.copyOf(path, path.length+1);
			for(Map.Entry<String,Object> e: o.entrySet()) {
				a[path.length] = e.getKey();
				Object v = e.getValue();
				if(v instanceof JsonContainer) {
					// The path array is reused: the nested call copies it
					res |= transform(v, a, mode, visited);
				} else {
					Object t = transformValue(a, v, mode);
					if(t!=v) {
						if(mode!=Mode.CHECK) {
							e.setValue(t);
						}
						res = true;
					}
				}
				if(res && mode==Mode.CHECK) {
					return true;
				}
			}
		} else if(container instanceof JsonArray arr) {
			String[] a = path.clone();
			for(int i=0; i<arr.size(); i++) {
				Object v = arr.get(i);
				if(v instanceof JsonContainer) {
					res |= transform(v, a, mode, visited);
				} else {
					Object t = transformValue(a, v, mode);
					if(t!=v) {
						if(mode!=Mode.CHECK) {
							arr.set(i, t);
						}
						res = true;
					}
				}
				if(res && mode==Mode.CHECK) {
					return true;
				}
			}
		}
		return res;
	}
	private Object transformValue(String[] path, Object v, Mode mode) {
		if(v instanceof String s && encryptor.shouldEncrypt(path)) {
			switch(mode) {
				case ENCRYPT -> {
					return encryptor.encrypt(path,s);
				}
				case DECRYPT -> {
					if(encryptor.isEncrypted(s)) {
						return encryptor.decrypt(path,s);
					}
				}
				case CHECK -> {
					if(!encryptor.isEncrypted(s) || encryptor.needsReencryption(s)) {
						// Any other object, to report a change
						return Boolean.TRUE;
					}
				}
			}
		}
		return v;
	}
}
