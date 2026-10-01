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

import java.lang.reflect.Type;

import org.monflabs.json.serialization.fields.BaseFieldAdapter;
import org.monflabs.json.serialization.fields.RuntimeAdapters;

/**
 * A property defined by a reader and a writer lambda.
 * <p>
 * Untyped (no value type): the reader returns a JSON value, or a value the registry can
 * serialize (a POJO with an adapter, a Java collection...), converted like a value declared
 * as <code>Object</code>; the writer receives the raw JSON value (numbers as
 * <code>Number</code>, null included).
 * <p>
 * Typed (a value type is given): the reader returns a Java value of that type, serialized
 * with the registry adapter of the type, and the writer receives the Java value
 * deserialized by this adapter (null for a JSON null).
 */
public class LambdaFieldAdapter<T,V> extends BaseFieldAdapter {

	@FunctionalInterface
	public interface PropertyReader<T,V> {
		public V readProperty(T _this);
	}

	@FunctionalInterface
	public interface PropertyWriter<T,V> {
		public void writeProperty(T _this, V jsonValue);
	}

	private PropertyReader<T,V> reader;
	private PropertyWriter<T,V> writer;
	private Type type;
	private JsonRegistry registry;
	private ClassAdapter adapter;

	/**
	 * An untyped property: the reader returns JSON values, the writer receives JSON values.
	 */
	public LambdaFieldAdapter(PropertyReader<T,V> reader, PropertyWriter<T,V> writer) {
		this(null, reader, writer);
	}

	/**
	 * A typed property: the values are converted with the adapter of the type (a class, or a
	 * generic type like <code>List&lt;Item&gt;</code>). A null type is an untyped property.
	 */
	public LambdaFieldAdapter(Type type, PropertyReader<T,V> reader, PropertyWriter<T,V> writer) {
		this.type = type;
		this.reader = reader;
		this.writer = writer;
	}

	@Override
	protected void _init(JsonRegistry registry, ClassAdapter parent) {
		this.adapter = type!=null ? registry.findAdapter(type) : registry.findAdapter(Object.class);
		this.registry = registry;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object readProperty(Object _this, ClassAdapter[] genericParams) {
		if(reader==null) {
			return null;
		}
		Object v = reader.readProperty((T)_this);
		if(v==null || adapter==null) {
			return v;
		}
		return RuntimeAdapters.forValue(registry, adapter, v).serialize(v);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		if(writer==null) {
			return;
		}
		Object v = jsonValue;
		if(type!=null && jsonValue!=null) {
			if(adapter==null) {
				throw new IllegalStateException("The property is not initialized by a registry");
			}
			v = adapter.deserialize(jsonValue);
		}
		writer.writeProperty((T)_this,(V)v);
	}
}
