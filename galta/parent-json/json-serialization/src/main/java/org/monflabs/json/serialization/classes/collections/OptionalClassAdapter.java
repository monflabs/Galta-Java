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
package org.monflabs.json.serialization.classes.collections;

import java.util.Optional;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.fields.RuntimeAdapters;

/**
 * Adapter of <code>Optional&lt;T&gt;</code>: an empty optional is written as null, a present
 * one as its value. A JSON null is read back as an empty optional.
 */
public class OptionalClassAdapter extends BaseClassAdapter {

	private JsonRegistry registry;
	private CollectionUtil.Params params;

	public OptionalClassAdapter() {
		super(Optional.class);
	}

	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.params = new CollectionUtil.Params(Optional.class, Optional.class, registry);
		this.registry = registry;
	}

	private ClassAdapter valueAdapter(ClassAdapter[] genericParams) {
		if(params==null) {
			throw new JsonException(null, "The adapter of {0} is not initialized by a registry", getAdaptedClazz().getName());
		}
		return params.resolve(genericParams)[0];
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value==null) {
			return null;
		}
		Object v = ((Optional<?>)value).orElse(null);
		if(v==null) {
			return null;
		}
		return RuntimeAdapters.forValue(registry, valueAdapter(genericParams), v).serialize(v);
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return Optional.empty();
		}
		return Optional.ofNullable(valueAdapter(genericParams).deserialize(jsonValue));
	}
}
