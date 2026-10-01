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
import java.lang.reflect.Type;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.serialization.fields.GenericTypeResolver;

/**
 * Json Registry.
 *
 * Registry of JSON adapters, accessible by class.
 *
 * @author priand
 *
 */
public interface JsonRegistry {

	/**
	 * Find the adapter of a class, or throw a {@link JsonException} when there is none.
	 */
	public ClassAdapter findAdapter(Class<?> clazz);

	/**
	 * Find the adapter of a class, or return null when there is none. An error while
	 * creating the adapter is still thrown.
	 */
	public default ClassAdapter findAdapterOrNull(Class<?> clazz) {
		try {
			return findAdapter(clazz);
		} catch(JsonException ex) {
			return null;
		}
	}

	/**
	 * Find the adapter of a type, generic or not: <code>List&lt;Item&gt;</code> gives the list
	 * adapter bound to the item adapter. A type variable resolves to its bound.
	 */
	public default ClassAdapter findAdapter(Type type) {
		if(type instanceof Class<?> c) {
			return findAdapter(c);
		}
		return GenericTypeResolver.resolve(type, null, this).resolve(null);
	}

	/**
	 * The maximum nesting depth of the values serialized and deserialized by this registry.
	 */
	public default int getMaxDepth() {
		return CycleGuard.DEFAULT_MAX_DEPTH;
	}

	/**
	 * True if the NaN and infinite <code>float</code>/<code>double</code> values are written
	 * as the strings "NaN", "Infinity" and "-Infinity". Otherwise they are written as is,
	 * and become null when stringified.
	 */
	public default boolean isNonFiniteNumbersAsStrings() {
		return false;
	}

	//
	// Java -> JSON
	//

	/**
	 * Serialize a value, with the adapter of its class.
	 * <p>
	 * The result type is inferred from the call site: assigning it to a variable of the wrong
	 * type, or passing it directly to an overloaded method, throws a
	 * <code>ClassCastException</code>. {@link #toJson(Object)} returns an <code>Object</code>.
	 */
	public default <T> T serialize(Object _this) {
		return serialize(_this, null);
	}
	@SuppressWarnings("unchecked")
	public default <T> T serialize(Object _this, ClassAdapter[] genericParams) {
		if(_this==null) {
			return null;
		}
		ClassAdapter a = findAdapter(_this.getClass());
		int prev = CycleGuard.maxDepth(getMaxDepth());
		try {
			return (T)a.serialize(_this, genericParams);
		} finally {
			CycleGuard.maxDepth(prev);
		}
	}
	/**
	 * Serialize a value and write the JSON text. A null value is written as <code>null</code>.
	 */
	public default void serialize(Writer w, Object _this, ClassAdapter[] genericParams, boolean compact) {
		Object o = serialize(_this, genericParams);
		try {
			w.write(o!=null ? JsonFactory.get().stringify(o, compact) : "null");
		} catch (IOException e) {
			throw new JsonException(e,"Error while serializing object");
		}
	}

	/**
	 * Serialize a value with the adapter of its class.
	 */
	public default Object toJson(Object value) {
		return serialize(value, null);
	}

	/**
	 * Serialize a value as a given type, generic or not (<code>List&lt;Item&gt;</code>).
	 */
	public default Object toJson(Object value, Type type) {
		if(value==null) {
			return null;
		}
		ClassAdapter a = findAdapter(type);
		int prev = CycleGuard.maxDepth(getMaxDepth());
		try {
			return a.serialize(value);
		} finally {
			CycleGuard.maxDepth(prev);
		}
	}

	/**
	 * Serialize a value as a given type, generic or not.
	 */
	public default Object toJson(Object value, TypeRef<?> type) {
		return toJson(value, type.getType());
	}

	//
	// JSON -> Java
	//

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
		int prev = CycleGuard.maxDepth(getMaxDepth());
		try {
			return (T)a.deserialize(jsonValue, genericParams);
		} finally {
			CycleGuard.maxDepth(prev);
		}
	}

	/**
	 * Deserialize a JSON value as a type, generic or not (<code>List&lt;Item&gt;</code>).
	 */
	@SuppressWarnings("unchecked")
	public default <T> T deserialize(Type type, Object jsonValue) {
		if(jsonValue==null) {
			return null;
		}
		ClassAdapter a = findAdapter(type);
		int prev = CycleGuard.maxDepth(getMaxDepth());
		try {
			return (T)a.deserialize(jsonValue);
		} finally {
			CycleGuard.maxDepth(prev);
		}
	}

	/**
	 * Deserialize a JSON value as a type captured by a {@link TypeRef}.
	 */
	public default <T> T deserialize(TypeRef<T> type, Object jsonValue) {
		return deserialize(type.getType(), jsonValue);
	}

	/**
	 * Same as {@link #deserialize(Class, Object)}, named after {@link #toJson(Object)}.
	 */
	public default <T> T fromJson(Class<T> clazz, Object jsonValue) {
		return deserialize(clazz, jsonValue, null);
	}

	/**
	 * Same as {@link #deserialize(Type, Object)}.
	 */
	public default <T> T fromJson(Type type, Object jsonValue) {
		return deserialize(type, jsonValue);
	}

	/**
	 * Same as {@link #deserialize(TypeRef, Object)}.
	 */
	public default <T> T fromJson(TypeRef<T> type, Object jsonValue) {
		return deserialize(type.getType(), jsonValue);
	}
}
