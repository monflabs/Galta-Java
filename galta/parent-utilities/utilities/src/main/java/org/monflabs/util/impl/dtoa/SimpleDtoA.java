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
package org.monflabs.util.impl.dtoa;

/**
 * Javascript like double conversion
 * 
 * @author priand
 */
public class SimpleDtoA {

/* NOT IN USE	
	//
	// Formatting double
	// 
	public static String toStandard(double value) {
		long l = (long) value;
		if (((double) l) == value) {
			return Long.toString(l);
		}

		if (value < 1e-21 || value > 1e+21) {
			return toExponential(value);
		}
		return getStandardFormat().format(value);
	}

	public static String toFixed(double value) {
		return getDefFixedFormat().format(value);
	}

	public static String toFixed(double value, int precision) {
		precision = Math.max(0, Math.min(20, precision));
		return getFixedFormat(precision).format(value);
	}

	public static String toExponential(double value) {
		return getDefExpFormat().format(value);
	}

	public static String toExponential(double value, int precision) {
		precision = Math.max(0, Math.min(20, precision));
		return getExpFormat(precision).format(value);
	}

	public static String toPrecision(double value) {
		if (value < 1e-21 || value > 1e21) {
			return toExponential(value);
		}
		return toFixed(value);
	}

	public static String toPrecision(double value, int precision) {
		if (value < 1e-21 || value > 1e21) {
			return toExponential(value, precision);
		}
		return toFixed(value, precision);
	}
	
	
	
	//
	// Formatting float
	// 
	public static String toStandard(float value) {
		long l = (long) value;
		if (((float) l) == value) {
			return Long.toString(l);
		}

		if (value < 1e-21 || value > 1e+21) {
			return toExponential(value);
		}
		return getStandardFormat().format(value);
	}

	public static String toFixed(float value) {
		return getDefFixedFormat().format(value);
	}

	public static String toFixed(float value, int precision) {
		precision = Math.max(0, Math.min(20, precision));
		return getFixedFormat(precision).format(value);
	}

	public static String toExponential(float value) {
		return getDefExpFormat().format(value);
	}

	public static String toExponential(float value, int precision) {
		precision = Math.max(0, Math.min(20, precision));
		return getExpFormat(precision).format(value);
	}

	public static String toPrecision(float value) {
		if (value < 1e-21 || value > 1e21) {
			return toExponential(value);
		}
		return toFixed(value);
	}

	public static String toPrecision(float value, int precision) {
		if (value < 1e-21 || value > 1e21) {
			return toExponential(value, precision);
		}
		return toFixed(value, precision);
	}

	
	//
	// Formats
	//
	private static DecimalFormat getStandardFormat() {
		if (_standardFormat == null) {
			_standardFormat = new java.text.DecimalFormat("0.####################", usSymbols);
		}
		return _standardFormat;
	}

	private static DecimalFormat getDefFixedFormat() {
		if (_defFixedFormat == null) {
			_defFixedFormat = new java.text.DecimalFormat("0.####################", usSymbols);
		}
		return _defFixedFormat;
	}

	private static DecimalFormat getDefExpFormat() {
		if (_defExponentialFormat == null) {
			_defExponentialFormat = new java.text.DecimalFormat("0.####################E0", usSymbols);
		}
		return _defExponentialFormat;
	}

	private static DecimalFormat getFixedFormat(int i) {
		if (_fixedFormats[i] == null) {
			if (i == 0) {
				_fixedFormats[i] = new DecimalFormat("0", usSymbols);
			} else {
				StringBuilder b = new StringBuilder(25);
				b.append("0.");
				for (int j = 0; j < i; j++) {
					b.append('0');
				}
				_fixedFormats[i] = new DecimalFormat(b.toString(), usSymbols);
			}
		}
		return _fixedFormats[i];
	}

	private static DecimalFormat getExpFormat(int i) {
		if (_expFormats[i] == null) {
			if (i == 0) {
				_expFormats[i] = new DecimalFormat("0E0", usSymbols);
			} else {
				StringBuilder b = new StringBuilder(25);
				b.append("0.");
				for (int j = 0; j < i; j++) {
					b.append('0');
				}
				b.append("E0");
				_expFormats[i] = new DecimalFormat(b.toString(), usSymbols);
			}
		}
		return _expFormats[i];
	}

	// Initialize the formats
	private static DecimalFormatSymbols usSymbols = new java.text.DecimalFormatSymbols(
			new java.util.Locale("en", "US"));
	private static DecimalFormat[] _fixedFormats = new java.text.DecimalFormat[21];
	private static DecimalFormat[] _expFormats = new java.text.DecimalFormat[21];
	private static DecimalFormat _defExponentialFormat;
	private static DecimalFormat _defFixedFormat;
	private static DecimalFormat _standardFormat;
*/
}