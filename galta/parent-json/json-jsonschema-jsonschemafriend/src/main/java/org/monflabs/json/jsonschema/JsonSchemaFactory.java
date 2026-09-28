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

import java.net.URI;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;

import net.jimblackler.jsonschemafriend.Schema;
import net.jimblackler.jsonschemafriend.SchemaStore;

/**
 * Creates {@link JsonSchema}s from either an in-memory schema document or a schema URI.
 * <p>
 * Design notes (jsonschemafriend's {@link SchemaStore} is a bundle of plain {@code HashMap}s, is not
 * thread-safe, and has no eviction API):
 * <ul>
 * <li>Schemas addressed by URI are legitimately cached by that URI, so they go through one store
 * per factory, guarded by the store's monitor.</li>
 * <li>Ad-hoc {@link JsonObject} documents are loaded through a throwaway store instead.
 * {@code SchemaStore.loadSchema(Object)} registers every document under a fresh synthetic URI
 * ({@code ""}, {@code "1"}, {@code "2"}, ...) and never removes it, so routing them through the
 * shared store would leak one entry in six maps per call for the lifetime of the (singleton)
 * factory. The built {@link Schema} keeps a reference to its own store, so {@code $ref}s inside the
 * document still resolve, and since nothing is shared no locking is needed on that path.</li>
 * </ul>
 */
public class JsonSchemaFactory {
	
	private static final JsonSchemaFactory instance = new JsonSchemaFactory();
	public static JsonSchemaFactory get() {
		return instance;
	}

	/** Store for URI-addressed schemas only; always accessed under its own monitor. */
	private final SchemaStore schemaStore = new SchemaStore();

	public JsonSchemaFactory() {
	}
	
	public JsonSchema getJsonSchema(JsonObject schema) {
		try {
			// We don't validate the schema itself
			// Else, it loads the schema's schema and this slows down the loading
			// A private store: see the class comment - the shared one would retain the document forever.
			Schema sc = new SchemaStore().loadSchema(schema,null);
			return new JsonSchema(sc);
		} catch(Exception ex) {
			throw new JsonException(ex,ex.getLocalizedMessage());
		}
	}
	public JsonSchema getJsonSchema(String uri) {
		try {
			return getJsonSchema(URI.create(uri));
		} catch(IllegalArgumentException ex) {
			throw new JsonException(ex,ex.getLocalizedMessage());
		}
	}
	public JsonSchema getJsonSchema(URI uri) {
		try {
			Schema sc;
			synchronized(schemaStore) {
				sc = schemaStore.loadSchema(uri,null);
			}
			return new JsonSchema(sc);
		} catch(Exception ex) {
			throw new JsonException(ex,ex.getLocalizedMessage());
		}
	}
}
