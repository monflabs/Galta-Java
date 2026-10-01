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
package org.monflabs.ui.converters;

import java.util.Locale;

import org.monflabs.json.JsonUtil;

/**
 * Converters between typed values and text (an input field).
 * <p>
 * The contract of the typed converters (boolean, int, long, double):
 * <ul>
 * <li>a null value converts to a null text, and back;</li>
 * <li>the text is trimmed, and a blank text (an emptied field) is read as
 * null;</li>
 * <li>booleans are read case-insensitively ({@code true}, {@code TRUE},
 * {@code False}...);</li>
 * <li>a text that is not a valid value throws an
 * {@link IllegalArgumentException} (a {@link NumberFormatException} for
 * the numbers): there is no silent default value.</li>
 * </ul>
 * The string converter keeps the text as it is.
 *
 * @author priand
 */
public interface TextConverter<T> extends ValueToStringConverter<T> {

	/**
	 * Converts a text to a value.
	 *
	 * @throws IllegalArgumentException when the text is not a valid value
	 */
	public T stringToValue(String value);

	/**
	 * The trimmed text, or null when it is null or blank.
	 */
	private static String trimToNull(String str) {
		if(str==null) {
			return null;
		}
		String s = str.strip();
		return s.isEmpty() ? null : s;
	}

	public static class CString implements TextConverter<String> {

		@Override
		public String valueToString(String value) {
			return value;
		}

		@Override
		public String stringToValue(String str) {
			return str;
		}
	}
	public static final CString stringConverter = new CString();

	public static class CBoolean implements TextConverter<Boolean> {

		@Override
		public String valueToString(Boolean value) {
			return value!=null ? Boolean.toString(value.booleanValue()) : null;
		}

		@Override
		public Boolean stringToValue(String str) {
			String s = trimToNull(str);
			if(s==null) {
				return null;
			}
			switch(s.toLowerCase(Locale.ROOT)) {
				case "true": return Boolean.TRUE;
				case "false": return Boolean.FALSE;
				default: throw new IllegalArgumentException("Not a boolean: "+str);
			}
		}
	}
	public static final CBoolean booleanConverter = new CBoolean();

	public static class CInteger implements TextConverter<Integer> {

		@Override
		public String valueToString(Integer value) {
			return value!=null ? JsonUtil.toString(value.intValue()) : null;
		}

		@Override
		public Integer stringToValue(String str) {
			String s = trimToNull(str);
			return s!=null ? Integer.valueOf(s) : null;
		}
	}
	public static final CInteger intConverter = new CInteger();

	public static class CLong implements TextConverter<Long> {

		@Override
		public String valueToString(Long value) {
			return value!=null ? JsonUtil.toString(value.longValue()) : null;
		}

		@Override
		public Long stringToValue(String str) {
			String s = trimToNull(str);
			return s!=null ? Long.valueOf(s) : null;
		}
	}
	public static final CLong longConverter = new CLong();

	public static class CDouble implements TextConverter<Double> {

		@Override
		public String valueToString(Double value) {
			return value!=null ? JsonUtil.toString(value.doubleValue()) : null;
		}

		@Override
		public Double stringToValue(String str) {
			String s = trimToNull(str);
			return s!=null ? Double.valueOf(s) : null;
		}
	}
	public static final CDouble doubleConverter = new CDouble();
}
