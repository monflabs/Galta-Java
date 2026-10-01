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
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.SerializationException;
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
	private boolean nonFiniteAsStrings;

	public PrimitiveArrayClassAdapter(Class<?> arrayClass) {
		super(arrayClass);
		this.componentType = arrayClass.getComponentType();
		if(componentType==null || !componentType.isPrimitive()) {
			throw new IllegalArgumentException("Not an array of primitives: "+arrayClass.getName());
		}
		this.converter = ScalarClassAdapter.converter(componentType);
	}

	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.nonFiniteAsStrings = registry.isNonFiniteNumbersAsStrings();
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value==null) {
			return null;
		}
		// One loop per type: no reflective access, no boxing through Array.get()
		if(value instanceof char[] chars) {
			return new String(chars);
		}
		if(value instanceof int[] v) {
			JsonArray a = JsonArray.create(v.length);
			for(int x: v) {
				a.add(x);
			}
			return a;
		}
		if(value instanceof long[] v) {
			JsonArray a = JsonArray.create(v.length);
			for(long x: v) {
				a.add(x);
			}
			return a;
		}
		if(value instanceof double[] v) {
			JsonArray a = JsonArray.create(v.length);
			for(double x: v) {
				a.add(nonFiniteAsStrings ? ScalarClassAdapter.toJson(x, true) : (Object)x);
			}
			return a;
		}
		if(value instanceof float[] v) {
			JsonArray a = JsonArray.create(v.length);
			for(float x: v) {
				a.add(nonFiniteAsStrings ? ScalarClassAdapter.toJson(x, true) : (Object)x);
			}
			return a;
		}
		if(value instanceof boolean[] v) {
			JsonArray a = JsonArray.create(v.length);
			for(boolean x: v) {
				a.add(x);
			}
			return a;
		}
		if(value instanceof byte[] v) {
			JsonArray a = JsonArray.create(v.length);
			for(byte x: v) {
				a.add(x);
			}
			return a;
		}
		if(value instanceof short[] v) {
			JsonArray a = JsonArray.create(v.length);
			for(short x: v) {
				a.add(x);
			}
			return a;
		}
		throw new JsonException(null, "Value {0} is not a {1}", value.getClass().getName(), getAdaptedClazz().getSimpleName());
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
				try {
					if(item==null) {
						throw new JsonException(null,"A {0} array cannot hold a null value",componentType.getName());
					}
					Object x = converter.apply(item);
					switch(v) {
						case int[] t -> t[i] = (Integer)x;
						case long[] t -> t[i] = (Long)x;
						case double[] t -> t[i] = (Double)x;
						case float[] t -> t[i] = (Float)x;
						case boolean[] t -> t[i] = (Boolean)x;
						case byte[] t -> t[i] = (Byte)x;
						case short[] t -> t[i] = (Short)x;
						case char[] t -> t[i] = (Character)x;
						default -> Array.set(v, i, x);
					}
				} catch(RuntimeException ex) {
					throw SerializationException.atIndex(ex, i);
				}
			}
			return v;
		}
		throw new JsonException(null,"Cannot deserialize {0} into a {1}: a JSON array is expected",SerializationException.describe(jsonValue),getAdaptedClazz().getSimpleName());
	}
}
