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
 * Converters to/from long.
 *
 * @author priand
 */
public interface LongConverter extends LongToValueConverter, ValueToLongConverter{

	public class CLong implements LongConverter {

		private long defaultValue;
		
		public CLong() {
		}
		
		public CLong(long defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public Object longToValue(long value) {
			return Long.valueOf(value);
		}
		
		@Override
		public long valueToLong(Object value) {
			if(value instanceof Number) {
				return ((Number)value).longValue();
			}
			return defaultValue;
		}
	}
	public static final CLong longConverter = new CLong();
	
	public class CString implements LongConverter {
		
		private long defaultValue;
		
		public CString() {
			this(0L);
		}
		public CString(long defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public Object longToValue(long value) {
			return Long.toString(value);
		}
		
		/**
		 * Parse the string, returning the default value when it is not a valid long.
		 */
		@Override
		public long valueToLong(Object value) {
			if(value instanceof String s) {
				try {
					return Long.parseLong(s.trim());
				} catch(NumberFormatException e) {
					return defaultValue;
				}
			}
			return defaultValue;
		}
	}
	public static final CString stringConverter = new CString();	
}
