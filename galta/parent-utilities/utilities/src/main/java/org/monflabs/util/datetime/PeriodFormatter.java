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
package org.monflabs.util.datetime;

import org.monflabs.util.StringUtil;

/**
 * Period Formatter.
 */
public final class PeriodFormatter {

    /**
     * Parses a time period.
     * A period should be formated as follow:
     *   (d+[yYnMwWdDhms]|d+ms)+ - a trailing number without a unit is milliseconds.
     * Whitespace between the parts is ignored.
     * @param period
     * @return the period in milliseconds, or -1 if invalid (empty, unknown unit, a unit
     *   without a number, a sign, or a value that overflows a long)
     */
	public static long parsePeriod(String period) {
		if(StringUtil.isEmpty(period)) {
			return -1;
		}
		try {
			long result = 0;
			boolean empty = true;
			int len = period.length();
			int i = 0;
			while(i<len) {
				char c = period.charAt(i);
				if(Character.isWhitespace(c)) {
					i++;
					continue;
				}
				if(c<'0' || c>'9') {
					return -1; // A unit without a number, a sign or any other character
				}
				long number = 0;
				while(i<len && (c=period.charAt(i))>='0' && c<='9') {
					number = Math.addExact(Math.multiplyExact(number,10L),c-'0');
					i++;
				}
				empty = false;
				if(i==len || Character.isWhitespace(c)) {
					// No unit: milliseconds
					result = Math.addExact(result,number);
					continue;
				}
				if(c=='m' && i+1<len && period.charAt(i+1)=='s') {
					// "ms", as produced by formatPeriod(): milliseconds, not minutes
					result = Math.addExact(result,number);
					i += 2;
					continue;
				}
				long unit = unitMillis(c);
				if(unit<0) {
					return -1;
				}
				result = Math.addExact(result,Math.multiplyExact(number,unit));
				i++;
			}
			return empty ? -1 : result;
		} catch(ArithmeticException ex) {
			return -1;
		}
	}
	private static long unitMillis(char unit) {
		switch (unit) {
			case 'Y':		
			case 'y':		return 1000L*60L*60L*24L*365L;
			case 'M':		
			case 'n':		return 1000L*60L*60L*24L*30L;
			case 'W':		
			case 'w':		return 1000L*60L*60L*24L*7L;
			case 'D':		
			case 'd':		return 1000L*60L*60L*24L;
			
			case 'h':		return 1000L*60L*60L;
			case 'm':		return 1000L*60L;
			case 's':		return 1000L;
			default:		return -1;
		}
	}

	
	public static String formatPeriod(long period) {
		return formatPeriod(period, (char)0);
	}
	public static String formatPeriod(long period, char precision) {
		if(period<0) {
			return "";
		}
		if(period==0) {
			return "0";
		}
		StringBuilder b = new StringBuilder();
		long days = period / (1000L*60L*60L*24L);
		if(days>0) {
			if(days%365==0) {
				b.append(Long.toString(days/365));
				b.append('Y');
			} else if(days%30==0) {
				b.append(Long.toString(days/30));
				b.append('M');
			} else if(days%7==0) {
				b.append(Long.toString(days/7));
				b.append('W');
			} else {
				b.append(Long.toString(days));
				b.append('D');
			}
			period -= days*(1000L*60L*60L*24L);
		}
		if(precision!='d' && precision!='D') {
			long hours = period / (1000L*60L*60L);
			if(hours>0) {
				b.append(Long.toString(hours));
				b.append('h');
				period -= hours*(1000L*60L*60L);
			}
			if(precision!='h') {
				long mins = period / (1000L*60L);
				if(mins>0) {
					b.append(Long.toString(mins));
					b.append('m');
					period -= mins*(1000L*60L);
				}
				if(precision!='m') {
					long secs = period / (1000L);
					if(secs>0) {
						b.append(Long.toString(secs));
						b.append('s');
						period -= secs*(1000L);
					}
					if(precision!='s') {
						if(period>0) {
							b.append(Long.toString(period));
							if(secs==0) {
								b.append("ms");
							}
						}
					}
				}
			}
		}
		return b.length()!=0 ? b.toString() : "0";
	}
}
