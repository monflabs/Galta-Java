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
package org.monflabs.json.serialization.classes.arrays;

import java.lang.reflect.Array;
import java.util.List;
import java.util.function.Function;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.classes.BaseClassAdapter;
import org.monflabs.json.serialization.classes.primitives.ScalarClassAdapter;

/**
 * Adapter of an array of primitives (<code>int[]</code>...), serialized as a JSON array.
 * <p>
 * A <code>char[]</code> is serialized as a JSON string, and read back from a string or
 * from an array of one character strings.
 */
public class PrimitiveArrayClassAdapter extends BaseClassAdapter {

	/**
	 * The adapters of the arrays of every primitive type.
	 */
	public static List<PrimitiveArrayClassAdapter> standardAdapters() {
		return List.of(
			new PrimitiveArrayClassAdapter(boolean[].class),
			new PrimitiveArrayClassAdapter(byte[].class),
			new PrimitiveArrayClassAdapter(short[].class),
			new PrimitiveArrayClassAdapter(int[].class),
			new PrimitiveArrayClassAdapter(long[].class),
			new PrimitiveArrayClassAdapter(float[].class),
			new PrimitiveArrayClassAdapter(double[].class),
			new PrimitiveArrayClassAdapter(char[].class)
		);
	}

	private final Class<?> componentType;
	private final Function<Object,Object> converter;

	public PrimitiveArrayClassAdapter(Class<?> arrayClass) {
		super(arrayClass);
		this.componentType = arrayClass.getComponentType();
		if(componentType==null || !componentType.isPrimitive()) {
			throw new IllegalArgumentException("Not an array of primitives: "+arrayClass.getName());
		}
		this.converter = ScalarClassAdapter.converter(componentType);
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value==null) {
			return null;
		}
		if(value instanceof char[] chars) {
			return new String(chars);
		}
		int len = Array.getLength(value);
		JsonArray a = JsonArray.create(len);
		for(int i=0; i<len; i++) {
			a.add(Array.get(value, i));
		}
		return a;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		if(componentType==char.class && jsonValue instanceof String s) {
			return s.toCharArray();
		}
		if(jsonValue instanceof JsonArray a) {
			int len = a.size();
			Object v = Array.newInstance(componentType, len);
			for(int i=0; i<len; i++) {
				Object item = a.get(i);
				if(item==null) {
					throw new JsonException(null,"A {0} array cannot hold a null value",componentType.getName());
				}
				Array.set(v, i, converter.apply(item));
			}
			return v;
		}
		throw new JsonException(null,"JsonValue is not an array");
	}
}
