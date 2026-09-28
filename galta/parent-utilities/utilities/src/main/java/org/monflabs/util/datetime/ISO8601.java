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

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.regex.Pattern;

import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 * Date/time ISO 8801 handlers.
 */
public final class ISO8601 {
	
	
	/////////////////////////////////////////////////////////////
	//
	// Date/Time Formatting
	//
	/////////////////////////////////////////////////////////////
	
    public static String formatISO8601(LocalDate date) {
    	DateTimeParts parts = new DateTimeParts(date);
    	return parts.toDateString();
    }
    public static String formatISO8601(LocalTime date) {
    	return formatISO8601(date,true);
    }
    public static String formatISO8601(LocalTime date, boolean millis) {
    	DateTimeParts parts = new DateTimeParts(date);
    	return parts.toTimeString(millis, false);
    }
    public static String formatISO8601(LocalDateTime date) {
    	return formatISO8601(date,true);
    }
    public static String formatISO8601(LocalDateTime date, boolean millis) {
    	DateTimeParts parts = new DateTimeParts(date);
    	return parts.toDateTimeString(millis, false);
    }

    public static String formatISO8601(OffsetDateTime date) {
    	return formatISO8601(date,true);
    }
    public static String formatISO8601(OffsetDateTime date, boolean millis) {
    	DateTimeParts parts = new DateTimeParts(date);
    	return parts.toDateTimeString(millis, true);
    }

    public static String formatISO8601(ZonedDateTime date) {
    	return formatISO8601(date,true);
    }
    public static String formatISO8601(ZonedDateTime date, boolean millis) {
    	DateTimeParts parts = new DateTimeParts(date);
    	return parts.toDateTimeString(millis, true);
    }


    public static String formatISO8601(GregorianCalendar date) {
    	return formatISO8601(date,true);
    }
    public static String formatISO8601(GregorianCalendar date, boolean millis) {
    	DateTimeParts parts = new DateTimeParts(date);
    	return parts.toDateTimeString(millis, true);
    }
    
    
	/////////////////////////////////////////////////////////////
    //
    // Date/Time parsing
    //
	/////////////////////////////////////////////////////////////
    
    private static final Pattern ISO_OFFSET = Pattern.compile("[+-]\\d\\d(?::\\d\\d(?::\\d\\d)?|\\d\\d(?:\\d\\d)?)?");

    private static class ISO8601Parser {
    	String date;
    	int pos;
    	Boolean dateSep;
    	Boolean timeSep;
    	
    	ISO8601Parser(String date) {
    		this.date = date;
    	}
    	
    	public DateTimeParts parseDate() throws ParseException {
        	if(StringUtil.isEmpty(date)) {
    			return null;
        	}

        	int year = matchYear();
        	// Month and day are optional (ECMAScript's Date Time String
        	// Format allows the reduced-precision "yyyy" and "yyyy-MM"
        	// calendar date forms, defaulting the missing field(s) to 1).
        	int month = 1;
        	int day = 1;
        	if(pos<date.length()) {
	        	skipDateSep();
	        	month = matchInt(2);
	        	if(pos<date.length()) {
		        	skipDateSep();
		        	day = matchInt(2);
	        	}
        	}

            if(pos<date.length()) {
                throw new ParseException(StringFormat.format("Invalid extra character at position {0}",pos),pos);
            }

            return new DateTimeParts(year, month, day, 0, 0, 0, 0, null);
    	}
    	
    	public DateTimeParts parseTime() throws ParseException {
        	if(StringUtil.isEmpty(date)) {
    			return null;
        	}

        	int hour = matchInt(2);
        	skipTimeSep();
        	int minutes = matchInt(2);
        	int seconds = matchOptionalSeconds();
    		int millis = matchMillisSep() ? extractMillis() :0;
    		ZoneOffset offset = extractOffset();

            if(pos<date.length()) {
                throw new ParseException(StringFormat.format("Invalid extra character at position {0}",pos),pos);
            }

            return new DateTimeParts(0, 0, 0, hour, minutes, seconds, millis, offset);
    	}
    	
    	public DateTimeParts parseDateTime() throws ParseException {
        	if(StringUtil.isEmpty(date)) {
    			return null;
        	}

        	int year = matchYear();
        	skipDateSep();
        	int month = matchInt(2);
        	skipDateSep();
        	int day = matchInt(2);
        	
        	skipSep();

        	int hour = matchInt(2);
        	skipTimeSep();
        	int minutes = matchInt(2);
        	int seconds = matchOptionalSeconds();
    		int millis = matchMillisSep() ? extractMillis() :0;
    		ZoneOffset offset = extractOffset();

            if(pos<date.length()) {
                throw new ParseException(StringFormat.format("Invalid extra character at position {0}",pos),pos);
            }

            return new DateTimeParts(year, month, day, hour, minutes, seconds, millis, offset);
    	}
    	
        // Seconds (and, transitively, milliseconds) are always optional in
        // ECMAScript's Date Time String Format: "THH:mm", "THH:mm:ss" and
        // "THH:mm:ss.sss" are all valid time forms, with or without a
        // trailing date part or time zone offset - so this never throws for
        // an absent seconds field, in either parseTime()'s or
        // parseDateTime()'s use.
        // Must branch on the SAME timeSep state skipTimeSep() already
        // latched from the hour/minute separator, not re-derive it from a
        // fresh ':' check: in the compact ("HHmmss") form seconds are never
        // ':'-prefixed, so that fresh check always missed them, leaving pos
        // short and causing extractOffset() to choke on a stray digit
        // instead (e.g. "143611Z" mis-parsed as if seconds were absent,
        // then failing to recognize "11" as a timezone indicator).
        int matchOptionalSeconds() throws ParseException {
        	if(timeSep!=null && timeSep) {
        		if(pos<date.length() && date.charAt(pos)==':') {
        			pos++;
        			return matchInt(2);
        		}
        		return 0;
        	}
        	if(pos<date.length() && Character.isDigit(date.charAt(pos))) {
        		return matchInt(2);
        	}
        	return 0;
        }

        // Year is normally an unsigned 4-digit field, but ECMAScript's Date
        // Time String Format also allows an "extended year": an explicit
        // sign followed by exactly 6 digits (e.g. "-271821", "+275760"),
        // used to reach years outside 0000-9999.
        int matchYear() throws ParseException {
        	if(pos<date.length()) {
        		char c = date.charAt(pos);
        		if(c=='+' || c=='-') {
        			pos++;
        			int y = matchInt(6);
        			if(c=='-' && y==0) {
        				// "-000000" is explicitly invalid: year 0 is positive
        				// and must be spelled "+000000", never "-000000".
        				throw new ParseException("Invalid extended year -000000",pos);
        			}
        			return c=='-' ? -y : y;
        		}
        	}
        	return matchInt(4);
        }

        int matchInt(int len) throws ParseException {
        	if(len>date.length()-pos) {
                throw new ParseException(StringFormat.format("Invalid {0} digits number at position {1}",len,pos),pos);
        	}
        	int res = 0;
        	for(int i=0; i<len; i++) {
        		char c = date.charAt(pos++);
        		if(c<'0' || c>'9') {
                    throw new ParseException(StringFormat.format("Invalid number character {0} at position {1}",c,pos),pos);
        		}
        		res = res*10 + (c-'0');
        	}
            return res;
        }
        int extractMillis() throws ParseException {
        	int n = 0; int res=0;
        	while(pos<date.length()) {
        		char c = date.charAt(pos);
        		if(c<'0' || c>'9') {
        			break;
        		}
       			res = res*10 + (c-'0');
        		n++; pos++;
        	}
        	switch(n) {
        		case 1: 	return res*100;
        		case 2: 	return res*10;
        		case 3: 	return res;
        		case 4: 	return res/10;
        		case 5: 	return res/100;
        		case 6: 	return res/1000;
        		// Instant.toString() emits up to 9 fractional digits (nanoseconds)
        		case 7: 	return res/10000;
        		case 8: 	return res/100000;
        		case 9: 	return res/1000000;
        		default:	throw new ParseException(StringFormat.format("Invalid milliseconds value at position {0}",pos),pos); 
        	}
        }
        
        // Supports
        //   Z
        //   +/-HH:MM, +/-HHMM
        //   +/-HH:MM:SS, +/-HHMMSS
        //   +/-HH
        ZoneOffset extractOffset() throws ParseException {
            if(pos<date.length()) {
                char timezoneIndicator = date.charAt(pos);
                if (timezoneIndicator == '+' || timezoneIndicator == '-') {
                	int start = pos;
                	int end = pos+1;
                	while(end<date.length()) {
                		char c = date.charAt(end);
                		if((c>='0' && c<='9') || c==':') {
                			end++;
                		} else {
                			break;
                		}
                	}
                	String id = date.substring(start,end);
                	// ZoneOffset.of() also accepts non-ISO forms like "+5": only +/-HH[[:]MM[[:]SS]] is valid here
                	if(!ISO_OFFSET.matcher(id).matches()) {
                		throw new ParseException(StringFormat.format("Invalid timezone {0} at position {1}",id,start),start);
                	}
                	try {
                		ZoneOffset offset = ZoneOffset.of(id);
                		pos = end;
                		return offset;
                	} catch(java.time.DateTimeException e) {
                		throw new ParseException(StringFormat.format("Invalid timezone {0} at position {1}",id,start),start);
                	}
                } else if (timezoneIndicator == 'Z') {
                	pos += 1;
                    return ZoneOffset.UTC;
                }
                throw new ParseException(StringFormat.format("Invalid timezone at position {0}",pos),pos);
            }
        	return null;
        }
        boolean matchMillisSep() throws ParseException {
    		if(pos<date.length()) {
    			char c = date.charAt(pos);
    			if(c=='.') {
    				pos++;
    				return true;
    			}
    		}
    		return false;
        }

        void skipDateSep() throws ParseException {
        	if(dateSep==null) {
        		if(pos<date.length()) {
        			char c = date.charAt(pos);
        			if(c=='-') {
        				dateSep = Boolean.TRUE;
        				pos++;
        				return;
        			}
        		}
				dateSep = Boolean.FALSE;
        	} else {
        		if(dateSep) {
            		if(pos<date.length() && date.charAt(pos++)=='-') {
            			return;
            		}
                    throw new ParseException(StringFormat.format("Invalid date separator at position {0}",pos),pos);

        		}
        	}
        }
        void skipTimeSep() throws ParseException {
        	if(timeSep==null) {
        		if(pos<date.length()) {
        			char c = date.charAt(pos);
        			if(c==':') {
        				timeSep = Boolean.TRUE;
        				pos++;
        				return;
        			}
        		}
        		timeSep = Boolean.FALSE;
        	} else {
        		if(timeSep) {
            		if(pos<date.length() && date.charAt(pos++)==':') {
            			return;
            		}
                    throw new ParseException(StringFormat.format("Invalid time separator at position {0}",pos),pos);
        		}
        	}
        }
        void skipSep() throws ParseException {
    		if(pos<date.length()) {
    			char c = date.charAt(pos);
    			if(c=='T' || c==' ') {
    				pos++;
    				return;
    			}
                throw new ParseException(StringFormat.format("Invalid separator {0} at position {1}",c,pos),pos);
    		}
        }
    }
    

	
	public static DateTimeParts parseDateTimeParts(String date) throws ParseException {
    	if(StringUtil.isEmpty(date)) {
			return null;
    	}
    	ISO8601Parser parser = new ISO8601Parser(date);
    	return parser.parseDateTime();
	}
	public static DateTimeParts parseTimeParts(String date) throws ParseException {
    	if(StringUtil.isEmpty(date)) {
			return null;
    	}
    	ISO8601Parser parser = new ISO8601Parser(date);
    	return parser.parseTime();
	}
	public static DateTimeParts parseDateParts(String date) throws ParseException {
    	if(StringUtil.isEmpty(date)) {
			return null;
    	}
    	ISO8601Parser parser = new ISO8601Parser(date);
    	return parser.parseDate();
	}
	
	
	// The parseXxxParts() methods only check the syntax: a field out of range (month 13, hour 25,
	// February 31...) is reported by java.time when the parts are converted. Report it as the
	// declared ParseException rather than letting an unchecked DateTimeException escape.
	private interface PartsConverter<T> {
		T convert(DateTimeParts p);
	}
	private static <T> T convert(String date, DateTimeParts p, PartsConverter<T> converter) throws ParseException {
		if(p==null) {
			return null;
		}
		try {
			return converter.convert(p);
		} catch(DateTimeException ex) {
			ParseException pe = new ParseException(StringFormat.format("Invalid date/time value {0}: {1}",date,ex.getMessage()),0);
			pe.initCause(ex);
			throw pe;
		}
	}

	// A null or empty string parses to null, like the parseXxxParts() methods (it used to throw a NullPointerException)
	public static LocalDate parseLocalDate(String date) throws ParseException {
		return convert(date, parseDateParts(date), DateTimeParts::toLocalDate);
	}
	public static LocalTime parseLocalTime(String date) throws ParseException {
		return convert(date, parseTimeParts(date), DateTimeParts::toLocalTime);
	}
	public static LocalDateTime parseLocalDateTime(String date) throws ParseException {
		return convert(date, parseDateTimeParts(date), DateTimeParts::toLocalDateTime);
	}

	
	public static OffsetDateTime parseOffsetDateTime(String date) throws ParseException {
		return parseOffsetDateTime(date,null);
	}
	public static OffsetDateTime parseOffsetDateTime(String date, ZoneOffset offset) throws ParseException {
		return convert(date, parseDateTimeParts(date), p -> p.toOffsetDateTime(offset));
	}
	
	
	public static ZonedDateTime parseZonedDateTime(String date) throws ParseException {
		return parseZonedDateTime(date,null);
	}
	public static ZonedDateTime parseZonedDateTime(String date, ZoneOffset offset) throws ParseException {
		return convert(date, parseDateTimeParts(date), p -> p.toZonedDateTime(offset));
	}
}
