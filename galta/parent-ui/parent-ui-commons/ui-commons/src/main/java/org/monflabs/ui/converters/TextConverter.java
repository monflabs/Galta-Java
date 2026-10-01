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

import org.monflabs.json.JsonUtil;

/**
 * Converters to/from Text.
 * A null value converts to a null string and back; the typed converters also
 * read an empty string (an emptied field) as null.
 *
 * @author priand
 */
public interface TextConverter<T> extends StringToValueConverter<T>, ValueToStringConverter<T> {

	private static boolean isEmpty(String str) {
		return str==null || str.isEmpty();
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
			return !isEmpty(str) ? JsonUtil.parseBoolean(str) : null;
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
			return !isEmpty(str) ? JsonUtil.parseInt(str) : null;
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
			return !isEmpty(str) ? JsonUtil.parseLong(str) : null;
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
			return !isEmpty(str) ? JsonUtil.parseDouble(str) : null;
		}
	}	
	public static final CDouble doubleConverter = new CDouble();
}
