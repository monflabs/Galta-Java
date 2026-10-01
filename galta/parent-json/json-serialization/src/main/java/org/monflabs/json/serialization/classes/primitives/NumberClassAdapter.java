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
package org.monflabs.json.serialization.classes.primitives;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.NumberConverter;
import org.monflabs.json.serialization.SerializationException;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter of the values declared as <code>Number</code>: any number is written as is, and
 * read back as the number produced by the JSON parser (<code>Integer</code>,
 * <code>Long</code>, <code>Double</code>, <code>BigDecimal</code>...).
 */
public class NumberClassAdapter extends BaseClassAdapter {

	private boolean nonFiniteAsStrings;

	public NumberClassAdapter() {
		super(Number.class);
	}

	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.nonFiniteAsStrings = registry.isNonFiniteNumbersAsStrings();
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		return ScalarClassAdapter.toJson(value, nonFiniteAsStrings);
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null || jsonValue instanceof Number) {
			return jsonValue;
		}
		Double nf = NumberConverter.nonFinite(jsonValue);
		if(nf!=null) {
			return nf;
		}
		throw new JsonException(null, "Cannot deserialize {0} into a Number: a JSON number is expected", SerializationException.describe(jsonValue));
	}
}
