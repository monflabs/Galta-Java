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
package org.monflabs.json.serialization.fields;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;

/**
 * Selection of the adapter that serializes a value, given the adapter of its declared type.
 */
public final class RuntimeAdapters {

	private RuntimeAdapters() {
	}

	/**
	 * The adapter to serialize a value with. A value whose declared type is a POJO (a field
	 * declared as <code>Shape</code> holding a <code>Circle</code>) is serialized with the
	 * adapter of its actual class, so the data of the subclass is not lost. The declared
	 * adapter is used when the actual class has no adapter in the registry. The JSON is not
	 * typed: the value is read back as the declared type.
	 */
	public static ClassAdapter forValue(JsonRegistry registry, ClassAdapter declared, Object value) {
		if(registry!=null && declared instanceof SimpleClassAdapter<?> && value.getClass()!=declared.getAdaptedClazz()) {
			try {
				return registry.findAdapter(value.getClass());
			} catch(JsonException ex) {
				// No adapter for the actual class
			}
		}
		return declared;
	}
}
