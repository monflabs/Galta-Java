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
package org.monflabs.json.serialization.fields.primitives;

import java.lang.reflect.Field;
import java.util.function.Function;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.primitives.ScalarClassAdapter;
import org.monflabs.json.serialization.fields.ReflectionFieldAdapter;

/**
 * Adapter of a field holding a scalar: a primitive, <code>String</code>,
 * <code>BigInteger</code> or <code>BigDecimal</code>, see {@link ScalarClassAdapter}.
 */
public class ScalarFieldAdapter extends ReflectionFieldAdapter {

	private final Function<Object,Object> converter;
	private boolean nonFiniteAsStrings;

	public ScalarFieldAdapter(Field field) {
		super(field);
		this.converter = ScalarClassAdapter.converter(field.getType());
		if(converter==null) {
			throw new IllegalArgumentException("Not a scalar field: "+field);
		}
	}

	@Override
	protected void _init(JsonRegistry registry, ClassAdapter parent) {
		this.nonFiniteAsStrings = registry.isNonFiniteNumbersAsStrings();
	}

	@Override
	public Object readProperty(Object _this, ClassAdapter[] genericParams) {
		try {
			return ScalarClassAdapter.toJson(field.get(_this), nonFiniteAsStrings);
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		}
	}

	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		try {
			if(jsonValue==null) {
				Class<?> t = field.getType();
				if(t.isPrimitive()) {
					String hint = t==double.class || t==float.class ? " (NaN and the infinities are written as null, unless the registry has the nonFiniteNumbersAsStrings option)" : "";
					throw new JsonException(null, "A null JSON value cannot be assigned to the {0} field {1}{2}", t.getName(), field.getName(), hint);
				}
				field.set(_this, null);
			} else {
				field.set(_this, converter.apply(jsonValue));
			}
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		}
	}
}
