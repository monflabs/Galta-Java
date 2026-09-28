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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;

/**
 * Json Registry.
 * 
 * Registry of JSON adapters, accessible by class.
 *  
 * @author priand
 *
 */
public interface JsonRegistry {
	
	public ClassAdapter findAdapter(Class<?> clazz);
	
	public default <T> T serialize(Object _this) {
		return serialize(_this, null);
	}
	@SuppressWarnings("unchecked")
	public default <T> T serialize(Object _this, ClassAdapter[] genericParams) {
		if(_this==null) {
			return null;
		}
		ClassAdapter a = findAdapter(_this.getClass());
		return (T)a.serialize(_this, genericParams);
	}
	public default void serialize(Writer w, Object _this, ClassAdapter[] genericParams, boolean compact) {
		Object o = serialize(_this, genericParams);
		if(o!=null) {
			try {
				w.write(JsonFactory.get().stringify(o, compact));
			} catch (IOException e) {
				throw new JsonException(e,"Error while serilizing object");
			}
		}
	}

	public default <T> T deserialize(Class<T> clazz, Reader r) {
		return deserialize(clazz, JsonFactory.get().parse(r), null);
	}
	public default <T> T deserialize(Class<T> clazz, Object jsonValue) {
		return deserialize(clazz, jsonValue, null);
	}
	@SuppressWarnings("unchecked")
	public default <T> T deserialize(Class<T> clazz, Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		ClassAdapter a = findAdapter(clazz);
		return (T)a.deserialize(jsonValue, genericParams);
	}
}
