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


/**	
 * Converters to/from boolean.
 *
 * @author priand
 */
public interface BooleanConverter extends BooleanToValueConverter, ValueToBooleanConverter {

	public class CBoolean implements BooleanConverter {

		private boolean defaultValue;
		
		public CBoolean() {
		}
		
		public CBoolean(boolean defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public Object booleanToValue(boolean value) {
			return Boolean.valueOf(value);
		}
		
		@Override
		public boolean valueToBoolean(Object value) {
			if(value instanceof Boolean) {
				return ((Boolean)value).booleanValue();
			}
			return defaultValue;
		}
	}
	public static final CBoolean booleanConverter = new CBoolean();
	
	public class CString implements BooleanConverter {
		
		private String checkedValue;
		private String uncheckedValue;
		private boolean defaultValue;
		
		public CString() {
			this(Boolean.toString(true),Boolean.toString(false));
		}
		public CString(String checkedValue, String uncheckedValue) {
			this(checkedValue, uncheckedValue, false);
		}
		public CString(String checkedValue, String uncheckedValue, boolean defaultValue) {
			this.checkedValue = checkedValue;
			this.uncheckedValue = uncheckedValue;
			this.defaultValue = defaultValue;
		}

		@Override
		public Object booleanToValue(boolean value) {
			return value ? checkedValue : uncheckedValue;
		}
		
		@Override
		public boolean valueToBoolean(Object value) {
			if(value instanceof String) {
				String s = (String)value;
				if(s.equals(checkedValue)) {
					return true;
				}
				if(s.equals(uncheckedValue)) {
					return false;
				}
			}
			return defaultValue;
		}
	}
	public static final CString stringConverter = new CString();	
}
