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
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter for an enum, serialized as the name of the constant.
 */
public class EnumClassAdapter extends BaseClassAdapter {
	
	public EnumClassAdapter(Class<?> enumClass) {
		super(enumClass);
		if(!enumClass.isEnum()) {
			throw new IllegalArgumentException(enumClass.getName()+" is not an enum");
		}
	}
	
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value==null) {
			return null;
		}
		return ((Enum<?>)value).name();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		if(jsonValue instanceof String s) {
			try {
				return Enum.valueOf((Class)getAdaptedClazz(), s);
			} catch(IllegalArgumentException ex) {
				throw new JsonException(ex,"{0} is not a constant of {1}",s,getAdaptedClazz().getName());
			}
		}
		throw new JsonException(null,"JSON value {0} is not a string, cannot convert it to {1}",jsonValue,getAdaptedClazz().getName());
	}
}
