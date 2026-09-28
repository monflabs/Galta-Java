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
package org.monflabs.json;

public enum JsonType {
	UNKNOWN,
	NULL,
	BOOLEAN,
	NUMBER,
	STRING,
	OBJECT,
	ARRAY
	;
	public static JsonType typeOf(Object value) {
		if(value==null) {
			return JsonType.NULL;
		}
		Class<?> c = value.getClass();
		if(c==String.class) {
			return JsonType.STRING;
		}
		if(c==Boolean.class) {
			return JsonType.BOOLEAN;
		}
		if(value instanceof Number) {
			return JsonType.NUMBER;
		}
		if(value instanceof JsonObject) {
			return JsonType.OBJECT;
		}
		if(value instanceof JsonArray) {
			return JsonType.ARRAY;
		}
		return JsonType.UNKNOWN; // Native object
	}
}