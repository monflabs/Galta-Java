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

public class LambdaFieldAdapter<T,V> implements FieldAdapter {
	
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
	
	public LambdaFieldAdapter(PropertyReader<T,V> reader, PropertyWriter<T,V> writer) {
		this.reader = reader;
		this.writer = writer;
	}

	@SuppressWarnings("unchecked")
	@Override
	public V readProperty(Object _this, ClassAdapter[] genericParams) {
		return reader.readProperty((T)_this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		writer.writeProperty((T)_this,(V)jsonValue);
	}
}