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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonreference.JsonReference;
import org.monflabs.json.stringifier.JsonStringifier;
import org.monflabs.json.jsonreference.JsonReference.Resolver;
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
	
	private static class ConfigResolver extends Resolver {
		private AbstractJsonConfig config;
		public ConfigResolver(AbstractJsonConfig config, Object root) {
			super(root);
			this.config = config;
		}
		@Override
		public Object apply(JsonFactory factory, String resource) {
			if(StringUtil.isNotEmpty(resource)) {
				try { 
					return loadResource(factory, resource);
				} catch(Exception e) {
					throw new JsonException(e,"Cannot load $ref resource '{0}'", resource);
				}
			}
			return super.apply(factory, resource);
		}
		protected JsonObject loadResource(JsonFactory factory, String resource) {
			// Loaded as stored: load() decrypts the resolved content as a whole
			JsonObject json = config.loadJson(resource);
			if(json==null) {
				throw new ConfigException(null, "Resource '{0}' not found", resource);
			}
			return json;
		}
	}

	
	private JsonObject content;
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
	 */
	protected void saveJson(String path, JsonObject json) {
		setResource(path, os -> {
			try {
				JsonObject o = json;
				if(o.getReference()!=null && path!=null) {
					// A referenced resource: write its content, not a reference to itself
					JsonObject c = JsonObject.create();
					c.putAll(o);
					o = c;
				}
				Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
				// Nested $ref are written back as references, their content being
				// saved in its own resource
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
	 * Write the main resource and the referenced resources of an (already encrypted) content.
	 * Whole resources only: a reference with a JSON pointer fragment is not written back.
	 */
	private void saveResources(JsonObject o) {
		saveJson(null,o);
		JsonReference.findReferences(o, (c,r) -> {
			if(r.indexOf('#')<0 && c instanceof JsonObject jo) {
				saveJson(r,jo);
			}
		});
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
	

	public final void load() {
		content = null;
		try {
			JsonObject main = loadJson(null);
			if(main==null) {
				main = JsonObject.create();
			}

			JsonReference.Resolver r = new ConfigResolver(this, main);
			content = (JsonObject)JsonReference.resolve(JsonFactory.get(), main, r, true);
			if(encryptor!=null) {
				if(!isReadOnly()) {
					// Autosave if some values need encryption
					JsonObject enc = encrypt(content);
					if(enc!=null) {
						saveResources(enc);
					}
				}
				decrypt(content);
			}
		} finally {
			// in case of an error...
			if(content==null) {
				content = JsonObject.create();
			}
		}
	}
	/**
	 * Save the configuration and its referenced resources.
	 * @return true if the configuration was saved, false if it is read-only
	 * @throws ConfigException if the configuration cannot be saved
	 */
	public final boolean save() {
		if(!isReadOnly()) {
			try {
				JsonObject enc = encrypt(content);
				saveResources(enc!=null ? enc : content);
				return true;
			} catch(ConfigException ex) {
				throw ex;
			} catch(Exception ex) {
				throw new ConfigException(ex, "Error while saving the configuration");
			}
		}
		return false;
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

	@Override
	public synchronized boolean updateValues(Consumer<Updater> updater) {
		if(isReadOnly()) {
			throw new ConfigException(null, "Storage is readonly");
		}
		if(updater!=null) {
			JsonUpdater u = new JsonUpdater((JsonObject)cloneWithReferences(content));
			updater.accept(u);
			if(!u.cancelled && u.hasChanges) {
				content = u.content;
				if(isAutoSave()) {
					save();
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Deep clone that keeps the $ref of the resolved containers, so they are still
	 * saved to their own resource.
	 */
	private static Object cloneWithReferences(Object v) {
		if(v instanceof JsonObject o) {
			JsonObject c = JsonObject.create();
			for(Map.Entry<String,Object> e: o.entrySet()) {
				c.put(e.getKey(), cloneWithReferences(e.getValue()));
			}
			if(o.getReference()!=null) {
				c.setReference(o.getReference());
			}
			return c;
		}
		if(v instanceof JsonArray a) {
			JsonArray c = JsonArray.create(a.size());
			for(Object item: a) {
				c.add(cloneWithReferences(item));
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
	public JsonObject encrypt(JsonObject content) {
		if(encryptor!=null) {
			JsonObject clone = (JsonObject)cloneWithReferences(content);
			if(encrypt(clone, null)) {
				return clone;
			}
		}
		return null;
	}
	private boolean encrypt(JsonObject o, String[] path) {
		boolean res = false;
		String[] a;
		if(path!=null) {
			a = new String[path.length+1];
			System.arraycopy(path, 0, a, 0, path.length);
		} else {
			a = new String[1];
		}
		for(Map.Entry<String,Object> e: o.entrySet()) {
			a[a.length-1] = e.getKey(); 
			if(e.getValue() instanceof JsonObject jo) {
				if(encrypt(jo,a)) {
					res = true;
				}
			} else {
				if(encryptor.shouldEncrypt(a)) {
					Object v = e.getValue(); 
					if(v instanceof String s) {
						if(!encryptor.isEncrypted(s)) {
							String enc = encryptor.encrypt(a,s);
							e.setValue(enc);
							res = true;
						}
					}
				}
			}
		}
		return res;
	}
	
	public boolean decrypt(JsonObject o) {
		if(encryptor!=null) {
			return decrypt(o, null);
		}
		return false;
	}
	private boolean decrypt(JsonObject o, String[] path) {
		boolean res = false;
		String[] a;
		if(path!=null) {
			a = new String[path.length+1];
			System.arraycopy(path, 0, a, 0, path.length);
		} else {
			a = new String[1];
		}
		for(Map.Entry<String,Object> e: o.entrySet()) {
			a[a.length-1] = e.getKey(); 
			if(e.getValue() instanceof JsonObject jo) {
				if(decrypt(jo,a)) {
					res = true;
				}
			} else {
				if(encryptor.shouldEncrypt(a)) {
					Object v = e.getValue(); 
					if(v instanceof String s) {
						if(encryptor.isEncrypted(s)) {
							String enc = encryptor.decrypt(a,s);
							e.setValue(enc);
							res = true;
						}
					}
				}
			}
		}
		return res;
	}
}
