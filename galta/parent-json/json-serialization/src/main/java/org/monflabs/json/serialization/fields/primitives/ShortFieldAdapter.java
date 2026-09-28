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

import org.monflabs.json.serialization.NumberConverter;
import java.lang.reflect.Field;

import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.fields.ReflectionFieldAdapter;

public class ShortFieldAdapter extends ReflectionFieldAdapter {
	
	public ShortFieldAdapter(Field field) {
		super(field);
	}
	
	@Override
	public Object readProperty(Object _this, ClassAdapter[] genericParams) {
		try {
			return field.getShort(_this);
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		}
	}
	
	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		try {
			if(jsonValue instanceof Number n) {
				field.setShort(_this, NumberConverter.toShort(n) );
			} else {
				throw new JsonException(null, "JSON Value is not a number");
			}
		} catch(IllegalAccessException ex) {
			throw new JsonException(ex);
		}
	}
}