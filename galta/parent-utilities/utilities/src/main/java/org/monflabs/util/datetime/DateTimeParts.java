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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.monflabs.util.StringFormat;

public class DateTimeParts {
	
	public static final int NANOS_TO_MILLIS = 1000000;

	private int year;
	private int month;
	private int day;
	private int hour;
	private int minute;
	private int second;
	private int millis;
	private ZoneOffset offset;
	
	public DateTimeParts(int year, int month, int day, int hour, int minute, int second, int millis, ZoneOffset offset) {
		this.year = year;
		this.month = month;
		this.day = day;
		this.hour = hour;
		this.minute = minute;
		this.second = second;
		this.millis = millis;
		this.offset = offset;
	}
	public DateTimeParts(LocalDate date) {
		this.year = date.getYear();
		this.month = date.getMonthValue();
		this.day = date.getDayOfMonth();
	}
	public DateTimeParts(LocalTime date) {
		this.hour = date.getHour();
		this.minute = date.getMinute();
		this.second = date.getSecond();
		this.millis = date.getNano()/NANOS_TO_MILLIS;
	}
	public DateTimeParts(LocalDateTime date) {
		this.year = date.getYear();
		this.month = date.getMonthValue();
		this.day = date.getDayOfMonth();
		this.hour = date.getHour();
		this.minute = date.getMinute();
		this.second = date.getSecond();
		this.millis = date.getNano()/NANOS_TO_MILLIS;
	}
	public DateTimeParts(OffsetDateTime date) {
		this.year = date.getYear();
		this.month = date.getMonthValue();
		this.day = date.getDayOfMonth();
		this.hour = date.getHour();
		this.minute = date.getMinute();
		this.second = date.getSecond();
		this.millis = date.getNano()/NANOS_TO_MILLIS;
		this.offset = date.getOffset();
	}
	public DateTimeParts(ZonedDateTime date) {
		this.year = date.getYear();
		this.month = date.getMonthValue();
		this.day = date.getDayOfMonth();
		this.hour = date.getHour();
		this.minute = date.getMinute();
		this.second = date.getSecond();
		this.millis = date.getNano()/NANOS_TO_MILLIS;
		this.offset = date.getOffset();
	}
	
	/**
	 * The parts of the calendar's instant in the proleptic Gregorian calendar (ISO 8601),
	 * in the calendar's time zone. A GregorianCalendar uses the Julian calendar before
	 * October 15, 1582: its own fields are not used, they would be off by several days.
	 */
	public DateTimeParts(GregorianCalendar cal) {
		this(cal.toZonedDateTime());
	}
	
	
	@Override
	public String toString() {
		return StringFormat.format("{0},{1},{2},{3},{4},{5},{6},{7}",year,month,day,hour,minute,second,millis,offset!=null?offset.toString():"");
	}
	
	public int getYear() {
		return year;
	}
	public void setYear(int year) {
		this.year = year;
	}
	public int getMonth() {
		return month;
	}
	public void setMonth(int month) {
		this.month = month;
	}
	public int getDay() {
		return day;
	}
	public void setDay(int day) {
		this.day = day;
	}
	public int getHour() {
		return hour;
	}
	public void setHour(int hour) {
		this.hour = hour;
	}
	public int getMinute() {
		return minute;
	}
	public void setMinute(int minute) {
		this.minute = minute;
	}
	public int getSecond() {
		return second;
	}
	public void setSecond(int second) {
		this.second = second;
	}
	public int getMillis() {
		return millis;
	}
	public void setMillis(int millis) {
		this.millis = millis;
	}
	public ZoneOffset getOffset() {
		return offset;
	}
	public void setOffset(ZoneOffset offset) {
		this.offset = offset;
	}

	public LocalDate toLocalDate() {
		return LocalDate.of(getYear(),getMonth(),getDay());
	}
	public LocalTime toLocalTime() {
		return LocalTime.of(getHour(),getMinute(),getSecond(),getMillis()*NANOS_TO_MILLIS);
	}
	public LocalDateTime toLocalDateTime() {
		return LocalDateTime.of(getYear(),getMonth(),getDay(),getHour(),getMinute(),getSecond(),getMillis()*NANOS_TO_MILLIS);
	}
	
	public OffsetDateTime toOffsetDateTime() {
		return toOffsetDateTime(null);
	}
	public OffsetDateTime toOffsetDateTime(ZoneOffset offset) {
		if(this.offset!=null) {
			offset = this.offset;
		}
		if(offset==null) {
			offset = ZoneOffset.UTC;
		}
		return OffsetDateTime.of(getYear(),getMonth(),getDay(),getHour(),getMinute(),getSecond(),getMillis()*NANOS_TO_MILLIS,offset);
	}
	
	public ZonedDateTime toZonedDateTime() {
		return toZonedDateTime(null);
	}
	public ZonedDateTime toZonedDateTime(ZoneOffset offset) {
		if(this.offset!=null) {
			offset = this.offset;
		}
		if(offset==null) {
			offset = ZoneOffset.UTC;
		}
		return ZonedDateTime.of(getYear(),getMonth(),getDay(),getHour(),getMinute(),getSecond(),getMillis()*NANOS_TO_MILLIS,offset);
	}

	public Instant toInstant() {
		return toZonedDateTime().toInstant();
	}

	public long toEpochMilli() {
		// Is there a better way?
		return toInstant().toEpochMilli();
	}

	
	public String toDateTimeString(boolean millis, boolean timezone) {
		StringBuilder b = new StringBuilder();
    	appendDate(b);
        b.append('T');
		appendTime(b,millis,timezone);
		return b.toString();
	}
	
	public String toDateString() {
		StringBuilder b = new StringBuilder();
    	appendDate(b);
		return b.toString();
	}
	
	public String toTimeString(boolean millis, boolean timezone) {
		StringBuilder b = new StringBuilder();
		appendTime(b,millis,timezone);
		return b.toString();
	}
	

    private void appendDate(StringBuilder b) {
        // ECMAScript's extended year format: years within 0000-9999 render
        // as a plain unsigned 4-digit field; years outside that range render
        // as a mandatory sign followed by 6 digits (e.g. "-271821",
        // "+275760") rather than being truncated/wrapped to 4 digits.
        if(year>=0 && year<=9999) {
            appendInt4(b, year);
        } else {
            b.append(year<0 ? '-' : '+');
            // A long: Math.abs(Integer.MIN_VALUE) is still negative
            long abs = Math.abs((long)year);
            if(abs>999999) {
                // Beyond the 6-digit extended year: print all the digits rather than garbage
                b.append(abs);
            } else {
                appendInt6(b, (int)abs);
            }
        }
        b.append('-');
        appendInt2(b, month);
        b.append('-');
        appendInt2(b, day);
    }
    private void appendTime(StringBuilder b, boolean millis, boolean timezone) {
        appendInt2(b, hour);
        b.append(':');
        appendInt2(b, minute);
        b.append(':');
        appendInt2(b, second);
        if (millis) {
            b.append('.');
            appendInt3(b, this.millis);
        }
        if(timezone) {
        	if(offset!=null) {
        		b.append(offset.toString());
        	} else {
        		b.append('Z');
        	}
        }
    }

    private void appendInt2(StringBuilder b, int value) {
		b.append((char)('0' + (value / 10)));
		value = value % 10;
        b.append((char)('0' + value));
    }
    private static void appendInt3(StringBuilder b, int value) {
		b.append((char)('0' + (value / 100)));
		value = value % 100;
		b.append((char)('0' + (value / 10)));
		value = value % 10;
        b.append((char)('0' + value));
    }
    private static void appendInt4(StringBuilder b, int value) {
		b.append((char)('0' + (value / 1000)));
		value = value % 1000;
		b.append((char)('0' + (value / 100)));
		value = value % 100;
		b.append((char)('0' + (value / 10)));
		value = value % 10;
        b.append((char)('0' + value));
    }
    private static void appendInt6(StringBuilder b, int value) {
		b.append((char)('0' + (value / 100000)));
		value = value % 100000;
		b.append((char)('0' + (value / 10000)));
		value = value % 10000;
		appendInt4(b, value);
    }

}