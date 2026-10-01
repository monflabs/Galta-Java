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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;

import net.jimblackler.jsonschemafriend.CacheLoader;
import net.jimblackler.jsonschemafriend.Loader;
import net.jimblackler.jsonschemafriend.Schema;
import net.jimblackler.jsonschemafriend.SchemaStore;

/**
 * Creates {@link JsonSchema}s from either an in-memory schema document or a schema URI.
 * <p>
 * Design notes (jsonschemafriend's {@link SchemaStore} is a bundle of plain {@code HashMap}s, is not
 * thread-safe, and has no eviction API):
 * <ul>
 * <li>Schemas addressed by URI are legitimately cached by that URI, so they go through one store
 * per factory, guarded by the factory's lock.</li>
 * <li>Ad-hoc {@link JsonObject} documents are loaded through a throwaway store instead.
 * {@code SchemaStore.loadSchema(Object)} registers every document under a fresh synthetic URI
 * ({@code ""}, {@code "1"}, {@code "2"}, ...) and never removes it, so routing them through the
 * shared store would leak one entry in six maps per call for the lifetime of the (singleton)
 * factory. The built {@link Schema} keeps a reference to its own store, so {@code $ref}s inside the
 * document still resolve, and since nothing is shared no locking is needed on that path.</li>
 * <li>Fail closed: jsonschemafriend replaces a schema it cannot load or find (a missing file, a
 * dangling {@code $ref}) by a schema that accepts everything, and only logs a warning. The
 * factory records these failures (through its {@link Loader} and the library's warnings) and
 * throws a {@link JsonException} instead, unless {@link #setFailOnUnresolvedReferences(boolean)}
 * is set to false. A failed URI load also discards the shared store, so that the permissive
 * schema it built is not served from the cache later.</li>
 * </ul>
 * <p>
 * <b>Schemas must be trusted.</b> jsonschemafriend resolves a <code>$ref</code> (and a schema
 * URI) by loading it, whatever its scheme: <code>http:</code>/<code>https:</code> (a network
 * request), <code>file:</code> or <code>jar:</code> (any readable local file). A schema coming
 * from an untrusted source can then make the application read local files or reach internal
 * hosts. To validate with such schemas, create the factory with a restricted {@link Loader},
 * such as {@link #NO_LOADING} (only the local <code>#/...</code> references and the bundled
 * meta-schemas are then available). Note that jsonschemafriend replaces a document it cannot
 * load by a schema that accepts everything (it only logs a warning).
 */
public class JsonSchemaFactory {

	private static final JsonSchemaFactory instance = new JsonSchemaFactory();
	public static JsonSchemaFactory get() {
		return instance;
	}

	/**
	 * A loader that loads nothing: only the schemas given in memory, the documents they
	 * embed and the bundled meta-schemas are available.
	 */
	public static final Loader NO_LOADING = (uri, cacheSchema) -> {
		throw new IOException("Loading is disabled: "+uri);
	};

	// The load failures of the current thread's getJsonSchema() call
	private static final ThreadLocal<List<String>> FAILURES = new ThreadLocal<>();
	// jsonschemafriend logs, rather than reports, the schemas it cannot find. The loggers are
	// kept referenced so that their handler (and level) is not lost.
	private static final Logger[] LIBRARY_LOGGERS = {
		Logger.getLogger(Schema.class.getName()),
		Logger.getLogger(SchemaStore.class.getName()),
	};
	static {
		Handler h = new Handler() {
			@Override
			public void publish(LogRecord r) {
				List<String> failures = FAILURES.get();
				if(failures!=null && r.getLevel().intValue()>=Level.WARNING.intValue()) {
					String m = r.getMessage();
					if(m!=null && (m.startsWith("No match for") || m.startsWith("Failed attempt") || m.startsWith("Was not valid JSON"))) {
						failures.add(m);
					}
				}
			}
			@Override
			public void flush() {
			}
			@Override
			public void close() {
			}
		};
		for(Logger l: LIBRARY_LOGGERS) {
			l.addHandler(h);
		}
	}

	private final Loader loader;
	private final Object lock = new Object();
	/** Store for URI-addressed schemas only; always accessed under the lock. */
	private SchemaStore schemaStore;
	private volatile boolean failOnUnresolvedReferences = true;

	/**
	 * A factory loading the documents with the default jsonschemafriend loader (any URI,
	 * see the class comment).
	 */
	public JsonSchemaFactory() {
		this(new CacheLoader());
	}

	/**
	 * @param loader loads the documents designated by URIs ({@link #NO_LOADING} to forbid it)
	 */
	public JsonSchemaFactory(Loader loader) {
		Loader base = loader!=null ? loader : new CacheLoader();
		this.loader = (uri, cacheSchema) -> {
			try {
				return base.load(uri, cacheSchema);
			} catch(IOException | RuntimeException ex) {
				List<String> failures = FAILURES.get();
				if(failures!=null) {
					failures.add("Cannot load "+uri+": "+ex.getMessage());
				}
				throw ex;
			}
		};
		this.schemaStore = new SchemaStore(null, false, this.loader);
	}

	public boolean isFailOnUnresolvedReferences() {
		return failOnUnresolvedReferences;
	}

	/**
	 * When true (the default), a schema that cannot be loaded, or a "$ref" that designates
	 * nothing, makes {@code getJsonSchema()} throw a {@link JsonException}. When false, the
	 * jsonschemafriend behavior applies: the missing schema accepts everything.
	 */
	public void setFailOnUnresolvedReferences(boolean failOnUnresolvedReferences) {
		this.failOnUnresolvedReferences = failOnUnresolvedReferences;
	}

	public JsonSchema getJsonSchema(JsonObject schema) {
		List<String> failures = new ArrayList<>();
		List<String> previous = FAILURES.get();
		FAILURES.set(failures);
		Schema sc;
		try {
			// We don't validate the schema itself
			// Else, it loads the schema's schema and this slows down the loading
			// A private store: see the class comment - the shared one would retain the document forever.
			sc = new SchemaStore(null, false, loader).loadSchema(schema,null);
		} catch(Exception ex) {
			throw new JsonException(ex,ex.getLocalizedMessage());
		} finally {
			FAILURES.set(previous);
		}
		checkFailures(failures, null);
		return new JsonSchema(sc);
	}
	public JsonSchema getJsonSchema(String uri) {
		try {
			return getJsonSchema(URI.create(uri));
		} catch(IllegalArgumentException ex) {
			throw new JsonException(ex,ex.getLocalizedMessage());
		}
	}
	public JsonSchema getJsonSchema(URI uri) {
		List<String> failures = new ArrayList<>();
		List<String> previous = FAILURES.get();
		FAILURES.set(failures);
		Schema sc;
		try {
			synchronized(lock) {
				try {
					sc = schemaStore.loadSchema(uri,null);
				} finally {
					if(!failures.isEmpty()) {
						// Don't keep the permissive schemas built for what could not be loaded
						schemaStore = new SchemaStore(null, false, loader);
					}
				}
			}
		} catch(Exception ex) {
			throw new JsonException(ex,ex.getLocalizedMessage());
		} finally {
			FAILURES.set(previous);
		}
		checkFailures(failures, uri);
		return new JsonSchema(sc);
	}

	private void checkFailures(List<String> failures, URI uri) {
		if(!failures.isEmpty() && failOnUnresolvedReferences) {
			throw new JsonException(null,"Cannot load the schema{0}: {1}", uri!=null ? " "+uri : "", String.join("; ", failures));
		}
	}
}
