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
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.util.StringUtil;

/**
 * 
 * @author priand
 *
 */
public class PathSchemaFactory extends SchemaFactory {
	
	private Path schemaLocation;
	private String baseUri;
	
	public PathSchemaFactory(Path schemaLocation, String baseUri) {
		this.schemaLocation = schemaLocation;
		if(baseUri!=null) {
			if(!baseUri.endsWith("/")) {
				baseUri = baseUri + "/";
			}
			this.baseUri = baseUri;
		}
	}
	
	public Path getSchemaLocation() {
		return schemaLocation;
	}

	public String getBaseUri() {
		return baseUri;
	}

	@Override
	protected JsonObject loadSchemaAsSchema(String uri) {
		if(StringUtil.isNotEmpty(baseUri)) {
			if(uri.startsWith(baseUri)) {
				uri = uri.substring(baseUri.length());
			} else {
				return null;
			}
		}
		if(uri.contains("://")){
			return null;
		}
		// The schema must stay inside the schema location: no "../x.json" or absolute path
		Path root = schemaLocation.toAbsolutePath().normalize();
		Path f;
		try {
			f = root.resolve(uri).normalize();
		} catch(java.nio.file.InvalidPathException ex) {
			return null;
		}
		if(!f.startsWith(root) || f.equals(root)) {
			return null;
		}
		if(!Files.isRegularFile(f)) {
			return null;
		}
		try(Reader r=Files.newBufferedReader(f)) {
			return JsonObject.parse(r);
		} catch(IOException ex) {
			throw new JsonException(ex,"error while reading JSON Schema {0}",uri);
		}
	}
}