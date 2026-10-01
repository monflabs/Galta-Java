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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.NumberConverter;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter of a scalar type: the boxed primitives, <code>BigInteger</code>,
 * <code>BigDecimal</code> and <code>String</code> are JSON values as is, and are converted
 * back from the JSON value (exactly for the numbers, see {@link NumberConverter}). A
 * <code>Character</code> is a one character JSON string. A <code>float</code> or a
 * <code>double</code> is also read from the strings "NaN", "Infinity" and "-Infinity", and
 * written as these strings when the registry has the <code>nonFiniteNumbersAsStrings</code>
 * option.
 */
public class ScalarClassAdapter extends BaseClassAdapter {

	// JSON value -> Java value, by class (boxed and primitive classes)
	private static final Map<Class<?>,Function<Object,Object>> CONVERTERS = new HashMap<>();
	static {
		register(Boolean.class, boolean.class, JsonUtil::checkBoolean);
		register(Byte.class, byte.class, NumberConverter::toByte);
		register(Short.class, short.class, NumberConverter::toShort);
		register(Integer.class, int.class, NumberConverter::toInt);
		register(Long.class, long.class, NumberConverter::toLong);
		register(Float.class, float.class, NumberConverter::toFloat);
		register(Double.class, double.class, NumberConverter::toDouble);
		register(Character.class, char.class, ScalarClassAdapter::toChar);
		register(BigInteger.class, null, NumberConverter::toBigInteger);
		register(BigDecimal.class, null, NumberConverter::toBigDecimal);
		register(String.class, null, JsonUtil::checkString);
	}
	private static void register(Class<?> clazz, Class<?> primitive, Function<Object,Object> converter) {
		CONVERTERS.put(clazz, converter);
		if(primitive!=null) {
			CONVERTERS.put(primitive, converter);
		}
	}

	/**
	 * The conversion of a (non null) JSON value to a scalar class, boxed or primitive, or
	 * null if the class is not a scalar.
	 */
	public static Function<Object,Object> converter(Class<?> clazz) {
		return CONVERTERS.get(clazz);
	}

	/**
	 * The JSON value of a scalar: the value itself, except a <code>Character</code> that
	 * is a string.
	 */
	public static Object toJson(Object value) {
		return value instanceof Character c ? c.toString() : value;
	}

	/**
	 * The JSON value of a scalar, the NaN and infinite <code>float</code>/<code>double</code>
	 * values being written as the strings "NaN", "Infinity" and "-Infinity" if
	 * <code>nonFiniteAsStrings</code> is true.
	 */
	public static Object toJson(Object value, boolean nonFiniteAsStrings) {
		if(nonFiniteAsStrings) {
			if(value instanceof Double d && (d.isNaN() || d.isInfinite())) {
				return d.toString();
			}
			if(value instanceof Float f && (f.isNaN() || f.isInfinite())) {
				return f.toString();
			}
		}
		return toJson(value);
	}

	private static Object toChar(Object jsonValue) {
		if(jsonValue instanceof String s && s.length()==1) {
			return s.charAt(0);
		}
		throw new JsonException(null, "JSON value {0} is not a one character string, cannot convert it to char", jsonValue);
	}

	/**
	 * The adapters of the scalar (boxed) classes.
	 */
	public static List<ScalarClassAdapter> standardAdapters() {
		return List.of(
			new ScalarClassAdapter(Boolean.class),
			new ScalarClassAdapter(Byte.class),
			new ScalarClassAdapter(Short.class),
			new ScalarClassAdapter(Integer.class),
			new ScalarClassAdapter(Long.class),
			new ScalarClassAdapter(Float.class),
			new ScalarClassAdapter(Double.class),
			new ScalarClassAdapter(Character.class),
			new ScalarClassAdapter(BigInteger.class),
			new ScalarClassAdapter(BigDecimal.class),
			new ScalarClassAdapter(String.class)
		);
	}

	private final Function<Object,Object> converter;
	private boolean nonFiniteAsStrings;

	public ScalarClassAdapter(Class<?> clazz) {
		super(clazz);
		this.converter = converter(clazz);
		if(converter==null) {
			throw new IllegalArgumentException("Not a scalar class: "+clazz.getName());
		}
	}

	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.nonFiniteAsStrings = registry.isNonFiniteNumbersAsStrings();
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		return toJson(value, nonFiniteAsStrings);
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue==null) {
			return null;
		}
		if(getAdaptedClazz().isInstance(jsonValue)) {
			return jsonValue;
		}
		return converter.apply(jsonValue);
	}
}
