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
package org.monflabs.json.jsonpath;

import java.util.List;

import org.monflabs.json.JsonException;

import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;

/**
 * Jayway Jsonpath implementation.
 * 
 * @author priand
 */
public class JaywayJsonPath {
	
	private com.jayway.jsonpath.JsonPath jsonPath;
	
	public JaywayJsonPath(com.jayway.jsonpath.JsonPath jsonPath) {
		this.jsonPath = jsonPath;
	}
	
	public String getJsonPath() {
		return jsonPath.getPath();
	}
	
	public com.jayway.jsonpath.JsonPath getNativeJsonPath() {
		return jsonPath;
	}
	
	public boolean isDefinite() {
		return jsonPath.isDefinite();
	}

	public JsonValues read(Object json) {
		return read(json,false);
	}
	/**
	 * Read the path from a JSON value. The Galta configuration is always used, whatever the
	 * Jayway JVM-wide defaults are.
	 */
	public JsonValues read(Object json, boolean pointer) {
		if(pointer) {
			throw new JsonException(null,"Jayway JsonPath does not support JsonPointers for now");
		}
		try {
			try {
				if(jsonPath.isDefinite()) {
					return new JsonValues((Object)jsonPath.read(json, MonfLabsJsonPathConfiguration.configuration()));
				} else {
					return new JsonValues((List<?>)jsonPath.read(json, MonfLabsJsonPathConfiguration.configuration()));
				}
			} catch(PathNotFoundException ex) {
				return new JsonValues();
			}
		} catch(JsonPathException ex) {
			throw new JsonException( ex, ex.getLocalizedMessage());
		}
	}
}
