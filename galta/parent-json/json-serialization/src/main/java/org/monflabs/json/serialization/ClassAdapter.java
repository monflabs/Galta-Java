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
package org.monflabs.json.serialization;

/**
 * Json Adapter.
 * 
 * A JSON adapter is responsible for serializing and de-serializing objects.
 * 
 * @author priand
 *
 */
public interface ClassAdapter {
	
	/**
	 * Get the adapted class (the Java one tobe serialized).
	 * 
	 * @return
	 */
	public Class<?> getAdaptedClazz();
	
	/**
	 * Serialize an existing object and return a JSON value.
	 * @param value the object to serialize
	 * @param genericParams the adapters of the generic parameters, or null
	 * 
	 * @return
	 */
	public Object serialize(Object value, ClassAdapter[] genericParams);
	public default Object serialize(Object value) {
		return serialize(value, null);
	}

	/**
	 * Deserialize a JSON value and return an actual Java value.
	 * 
	 * If _this is not null, then any value will be added to it and it will be returned
	 * If it is null, then a new Java value is created.
	 * 
	 * @param jsonValue
	 * @param genericParams TODO
	 * @return
	 */
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams);
	public default Object deserialize(Object jsonValue) {
		return deserialize(jsonValue, null);
	}
}
