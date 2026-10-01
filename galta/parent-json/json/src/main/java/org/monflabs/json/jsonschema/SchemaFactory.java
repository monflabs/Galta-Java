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
package org.monflabs.json.jsonschema;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonreference.JsonReference;

/**
 * 
 * TODO: add a layer of cache.
 * 
 * @author priand
 *
 */
public abstract class SchemaFactory {
	
	public SchemaFactory() {
	}
	
	public SchemaNode getSchema(String uri) {
		return getSchema(uri,true);
	}
	
	/**
	 * Load a schema.
	 * @param resolve if true, the "$ref" references inside the schema are resolved: a
	 * relative reference is resolved against the URI of the document holding it, each
	 * external schema is loaded once (using this factory), and a recursive schema gives a
	 * cyclic graph of nodes. The data keywords ("const", "enum", "default", "examples") are
	 * not processed, and a reference to the "$id" of a loaded (sub)schema, or to a "$anchor",
	 * is resolved without loading anything.
	 * @throws JsonException if a reference cannot be resolved
	 */
	public SchemaNode getSchema(String uri, boolean resolve) {
		JsonObject o = loadSchemaAsSchema(uri);
		if(o==null) {
			return null;
		}
		if(resolve) {
			Object resolved = JsonReference.resolve(JsonFactory.get(), o, new JsonReference.Resolver(o, uri) {
				@Override
				public Object apply(JsonFactory f, String u) {
					if(u==null || u.isEmpty() || u.equals(uri)) {
						return getRoot();
					}
					// u is already resolved against the referring document
					JsonObject ext = loadSchemaAsSchema(u);
					if(ext==null) {
						throw new JsonException(null,"Cannot resolve schema '{0}'", u);
					}
					return ext;
				}
			}, true, true);
			if(!(resolved instanceof JsonObject ro)) {
				throw new JsonException(null,"The schema '{0}' does not resolve to an object", uri);
			}
			o = ro;
		}
		return schemaFactory(o);
	}
	
	protected SchemaNode schemaFactory(JsonObject o) {
		return new SchemaNode(o);
	}
	
	protected abstract JsonObject loadSchemaAsSchema(String uri);
}
