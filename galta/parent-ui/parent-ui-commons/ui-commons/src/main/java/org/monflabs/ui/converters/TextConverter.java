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
 *
 * @author priand
 */
public interface TextConverter<T> extends StringToValueConverter<T>, ValueToStringConverter<T> {
	
	public static class CString implements TextConverter<String> {
		
		@Override
		public String valueToString(String value) {
			return (String)value;
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
			return Boolean.toString(((Boolean)value).booleanValue());
		}
		
		@Override
		public Boolean stringToValue(String str) {
			return JsonUtil.parseBoolean(str);
		}
	}
	public static final CBoolean booleanConverter = new CBoolean();

	public static class CInteger implements TextConverter<Integer> {

		@Override
		public String valueToString(Integer value) {
			return JsonUtil.toString(((Number)value).intValue());
		}
		
		@Override
		public Integer stringToValue(String str) {
			return JsonUtil.parseInt(str);
		}
	}	
	public static final CInteger intConverter = new CInteger();

	public static class CLong implements TextConverter<Long> {

		@Override
		public String valueToString(Long value) {
			return JsonUtil.toString(((Number)value).longValue());
		}
		
		@Override
		public Long stringToValue(String str) {
			return JsonUtil.parseLong(str);
		}
	}	
	public static final CLong longConverter = new CLong();

	public static class CDouble implements TextConverter<Double> {

		@Override
		public String valueToString(Double value) {
			return JsonUtil.toString(((Number)value).doubleValue());
		}
		
		@Override
		public Double stringToValue(String str) {
			return JsonUtil.parseDouble(str);
		}
	}	
	public static final CDouble doubleConverter = new CDouble();
}
