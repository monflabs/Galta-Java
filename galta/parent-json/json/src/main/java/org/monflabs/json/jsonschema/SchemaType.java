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

import org.monflabs.json.JsonType;

/**
 * Data types handled by a schema
 */
public enum SchemaType {
	// Any
	UNKNOWN(JsonType.UNKNOWN),

	// Boolean
	BOOLEAN(JsonType.BOOLEAN),
	
	// Number and subtypes
	NUMBER(JsonType.NUMBER),
	BYTE(JsonType.NUMBER),
	SHORT(JsonType.NUMBER),
	INTEGER(JsonType.NUMBER),
	LONG(JsonType.NUMBER),
	FLOAT(JsonType.NUMBER),
	DOUBLE(JsonType.NUMBER),
	BIGINTEGER(JsonType.NUMBER),
	BIGDECIMAL(JsonType.NUMBER),
	
	// String and subtype
	STRING(JsonType.STRING),
	LOCALDATE(JsonType.STRING),
	LOCALTIME(JsonType.STRING),
	LOCALDATETIME(JsonType.STRING),
	OFFSETTIME(JsonType.STRING),
	OFFSETDATETIME(JsonType.STRING),
	ZONEDDATETIME(JsonType.STRING),
	
	// Generic Object and Array
	OBJECT(JsonType.OBJECT),
	ARRAY(JsonType.ARRAY),
	;
	
	private JsonType type;
	private SchemaType(JsonType type) {
		this.type = type;
	}
	
	public JsonType getJsonType() {
		return type;
	}
	
	public boolean isUnknown() {
		return type==JsonType.UNKNOWN;
	}
	
	public boolean isBoolean() {
		return type==JsonType.BOOLEAN;
	}
	
	public boolean isNumber() {
		return type==JsonType.NUMBER;
	}
	
	public boolean isString() {
		return type==JsonType.STRING;
	}
	
	public boolean isObject() {
		return type==JsonType.OBJECT;
	}
	
	public boolean isArray() {
		return type==JsonType.ARRAY;
	}
}
