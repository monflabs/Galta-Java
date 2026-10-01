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
package tests.datetime;

import static org.junit.Assert.assertThrows;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.monflabs.util.datetime.DateTimeParts;
import org.monflabs.util.datetime.ISO8601;

import tests.ProjectTestCase;

public class ISO8601Test extends ProjectTestCase {
	
    public void testParts() throws Exception {
    	DateTimeParts p = new DateTimeParts(2022, 8, 28, 4, 54, 38, 789, ZoneOffset.of("+02:00"));
    	assertEquals(2022,p.getYear());
    	assertEquals(8,p.getMonth());
    	assertEquals(28,p.getDay());
    	assertEquals(4,p.getHour());
    	assertEquals(54,p.getMinute());
    	assertEquals(38,p.getSecond());
    	assertEquals(789,p.getMillis());
    	assertEquals("+02:00",p.getOffset().toString());
    	assertEquals("2022,8,28,4,54,38,789,+02:00",p.toString());

    	assertEquals(LocalDateTime.of(2022,8,28,4,54,38,789*DateTimeParts.NANOS_TO_MILLIS),p.toLocalDateTime());
    	assertEquals(OffsetDateTime.of(2022,8,28,4,54,38,789*DateTimeParts.NANOS_TO_MILLIS,ZoneOffset.of("+02:00")),p.toOffsetDateTime());
    	assertEquals(ZonedDateTime.of(2022,8,28,4,54,38,789*DateTimeParts.NANOS_TO_MILLIS,ZoneOffset.of("+02:00")),p.toZonedDateTime());

    	p.setYear(1999);
    	p.setMonth(12);
    	p.setDay(17);
    	p.setHour(14);
    	p.setMinute(36);
    	p.setSecond(11);
    	p.setMillis(0);
    	p.setOffset(ZoneOffset.UTC);
    	assertEquals(1999,p.getYear());
    	assertEquals(12,p.getMonth());
    	assertEquals(17,p.getDay());
    	assertEquals(14,p.getHour());
    	assertEquals(36,p.getMinute());
    	assertEquals(11,p.getSecond());
    	assertEquals(0,p.getMillis());
    	assertEquals("Z",p.getOffset().toString());
    	assertEquals("1999,12,17,14,36,11,0,Z",p.toString());
    }

    public void testDateTimePartsParser() throws Exception {
    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("1999-12-17T14:36:11Z").toString());
    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("19991217T14:36:11Z").toString());
    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("1999-12-17T143611Z").toString());
    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("19991217T143611Z").toString());

    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("1999-12-17 14:36:11Z").toString());
    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("19991217 14:36:11Z").toString());
    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("1999-12-17 143611Z").toString());
    	assertEquals("1999,12,17,14,36,11,0,Z", ISO8601.parseDateTimeParts("19991217 143611Z").toString());

    	assertEquals("1999,12,17,14,36,11,0,", ISO8601.parseDateTimeParts("1999-12-17T14:36:11").toString());
    	assertEquals("1999,12,17,14,36,11,100,", ISO8601.parseDateTimeParts("1999-12-17T14:36:11.1").toString());
    	assertEquals("1999,12,17,14,36,11,120,", ISO8601.parseDateTimeParts("1999-12-17T14:36:11.12").toString());
    	assertEquals("1999,12,17,14,36,11,123,", ISO8601.parseDateTimeParts("1999-12-17T14:36:11.123").toString());
    	assertEquals("1999,12,17,14,36,11,123,Z", ISO8601.parseDateTimeParts("1999-12-17T14:36:11.123Z").toString());

    	assertThrows( ParseException.class, () -> ISO8601.parseDateTimeParts("1999-12+17T14:36:11Z") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateTimeParts("1999-1217T14:36:11Z") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateTimeParts("199912-17T14:36:11Z") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateTimeParts("1999-12-17") );
    	// Seconds are optional in ECMAScript's Date Time String Format
    	// (ISO8601.java was intentionally changed for test262 Date parsing
    	// compliance - see commit a4ef0b1ad), so this is now valid, not an error.
    	assertEquals("1999,12,17,20,34,0,0,", ISO8601.parseDateTimeParts("1999-12-17T20:34").toString());
    }

    public void testDatePartsParser() throws Exception {
    	assertEquals("1999,12,17,0,0,0,0,", ISO8601.parseDateParts("1999-12-17").toString());
    	assertEquals("1999,12,17,0,0,0,0,", ISO8601.parseDateParts("19991217").toString());

    	assertThrows( ParseException.class, () -> ISO8601.parseDateParts("99-12-17") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateParts("1999-12+17") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateParts("1999-1217") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateParts("199912-17") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateParts("1999-12-17T") );
    	assertThrows( ParseException.class, () -> ISO8601.parseDateParts("1999-12-17T20:34") );
    }

    public void testTimePartsParser() throws Exception {
    	assertEquals("0,0,0,14,36,11,0,Z", ISO8601.parseTimeParts("14:36:11Z").toString());
    	assertEquals("0,0,0,14,36,11,123,Z", ISO8601.parseTimeParts("14:36:11.1234Z").toString());
    	assertEquals("0,0,0,14,36,11,0,", ISO8601.parseTimeParts("14:36:11").toString());
    	assertEquals("0,0,0,14,36,11,0,Z", ISO8601.parseTimeParts("143611Z").toString());
    	assertEquals("0,0,0,14,36,11,0,", ISO8601.parseTimeParts("143611").toString());

    	assertThrows( ParseException.class, () -> ISO8601.parseTimeParts("14:3611Z") );
    	assertThrows( ParseException.class, () -> ISO8601.parseTimeParts("1436:11") );
    	assertThrows( ParseException.class, () -> ISO8601.parseTimeParts("14:36:11.Z") );
    	assertThrows( ParseException.class, () -> ISO8601.parseTimeParts("14:36:11.123ZK") );
    }
    
    public void testLocalDateParser() throws Exception {
    	assertEquals("1999-12-17", ISO8601.parseLocalDate("1999-12-17").toString());
    }
    public void testLocalTimeParser() throws Exception {
    	assertEquals("14:36:11", ISO8601.parseLocalTime("14:36:11").toString());
    }
    public void testLocalDateTimeParser() throws Exception {
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11").toString());
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11Z").toString());
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11+00").toString());
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11+01").toString());
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11-01").toString());
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11+00:00").toString());
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11+01:00").toString());
    	assertEquals("1999-12-17T14:36:11", ISO8601.parseLocalDateTime("1999-12-17T14:36:11-01:00").toString());
    }
    
    public void testOffsetDateTimeParser() throws Exception {
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11").toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11Z").toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+00:00").toString());
    	assertEquals("1999-12-17T14:36:11+01:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+01:00").toString());
    	assertEquals("1999-12-17T14:36:11-01:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11-01:00").toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+00").toString());
    	assertEquals("1999-12-17T14:36:11+01:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+01").toString());
    	assertEquals("1999-12-17T14:36:11-01:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11-01").toString());

    	assertEquals("1999-12-17T14:36:11+02:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11", ZoneOffset.of("+02:00")).toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11Z", ZoneOffset.of("+02:00")).toString());
    	assertEquals("1999-12-17T14:36:11+01:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+01:00", ZoneOffset.of("+02:00")).toString());
    	assertEquals("1999-12-17T14:36:11+01:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+01", ZoneOffset.of("+02:00")).toString());
    }
    
    public void testZonedDateTimeParser() throws Exception {
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseZonedDateTime("1999-12-17T14:36:11").toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseZonedDateTime("1999-12-17T14:36:11Z").toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseZonedDateTime("1999-12-17T14:36:11+00:00").toString());
    	assertEquals("1999-12-17T14:36:11+01:00", ISO8601.parseZonedDateTime("1999-12-17T14:36:11+01:00").toString());
    	assertEquals("1999-12-17T14:36:11-01:00", ISO8601.parseZonedDateTime("1999-12-17T14:36:11-01:00").toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseZonedDateTime("1999-12-17T14:36:11+00").toString());
    	assertEquals("1999-12-17T14:36:11+01:00", ISO8601.parseZonedDateTime("1999-12-17T14:36:11+01").toString());
    	assertEquals("1999-12-17T14:36:11-01:00", ISO8601.parseZonedDateTime("1999-12-17T14:36:11-01").toString());

    	assertEquals("1999-12-17T14:36:11+02:00", ISO8601.parseZonedDateTime("1999-12-17T14:36:11", ZoneOffset.of("+02:00")).toString());
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.parseZonedDateTime("1999-12-17T14:36:11Z", ZoneOffset.of("+02:00")).toString());
    	assertEquals("1999-12-17T14:36:11+01:00", ISO8601.parseZonedDateTime("1999-12-17T14:36:11+01:00", ZoneOffset.of("+02:00")).toString());
    }
    
    public void testLocalDateString() throws Exception {
    	assertEquals("1999-12-17", ISO8601.formatISO8601(LocalDate.of(1999,12,17)));
    }
    public void testLocalTimeString() throws Exception {
    	assertEquals("14:36:11.568", ISO8601.formatISO8601(LocalTime.of(14,36,11,568*DateTimeParts.NANOS_TO_MILLIS)));
    	assertEquals("14:36:11", ISO8601.formatISO8601(LocalTime.of(14,36,11,568*DateTimeParts.NANOS_TO_MILLIS),false));
    }
    public void testLocalDateTimeString() throws Exception {
    	assertEquals("1999-12-17T14:36:11.568", ISO8601.formatISO8601(LocalDateTime.of(1999,12,17,14,36,11,568*DateTimeParts.NANOS_TO_MILLIS)));
    	assertEquals("1999-12-17T14:36:11", ISO8601.formatISO8601(LocalDateTime.of(1999,12,17,14,36,11,568*DateTimeParts.NANOS_TO_MILLIS),false));
    }
    
    public void testOffsetDateTimeString() throws Exception {
    	assertEquals("1999-12-17T14:36:11.568Z", ISO8601.formatISO8601(OffsetDateTime.of(1999,12,17,14,36,11,568*DateTimeParts.NANOS_TO_MILLIS,ZoneOffset.UTC)));
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.formatISO8601(OffsetDateTime.of(1999,12,17,14,36,11,568*DateTimeParts.NANOS_TO_MILLIS,ZoneOffset.UTC),false));
    }
    
    public void testZonedDateTimeString() throws Exception {
    	assertEquals("1999-12-17T14:36:11.568Z", ISO8601.formatISO8601(ZonedDateTime.of(1999,12,17,14,36,11,568*DateTimeParts.NANOS_TO_MILLIS,ZoneOffset.UTC)));
    	assertEquals("1999-12-17T14:36:11Z", ISO8601.formatISO8601(ZonedDateTime.of(1999,12,17,14,36,11,568*DateTimeParts.NANOS_TO_MILLIS,ZoneOffset.UTC),false));
    }

    public void testOffsetFormats() throws Exception {
    	assertEquals("1999-12-17T14:36:11+05:30", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+0530").toString());
    	assertEquals("1999-12-17T14:36:11+05:30", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+05:30").toString());
    	assertEquals("1999-12-17T14:36:11-05:30", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11-0530").toString());
    	assertEquals("1999-12-17T14:36:11+05:00", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+05").toString());
    	// A malformed offset is a parse error, not an unchecked DateTimeException
    	assertThrows(java.text.ParseException.class, () -> ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+05:3"));
    	assertThrows(java.text.ParseException.class, () -> ISO8601.parseOffsetDateTime("1999-12-17T14:36:11+99:00"));
    }

    public void testFractionDigits() throws Exception {
    	// Instant.toString() writes up to 9 digits
    	assertEquals("1999-12-17T14:36:11.123Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11.123Z").toString());
    	assertEquals("1999-12-17T14:36:11.123Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11.1234567Z").toString());
    	assertEquals("1999-12-17T14:36:11.123Z", ISO8601.parseOffsetDateTime("1999-12-17T14:36:11.123456789Z").toString());
    }

    public void testEmptyParsesToNull() throws Exception {
    	// The typed parsers used to throw a NullPointerException on null/empty input
    	assertNull(ISO8601.parseLocalDate(null));
    	assertNull(ISO8601.parseLocalTime(""));
    	assertNull(ISO8601.parseLocalDateTime(null));
    	assertNull(ISO8601.parseOffsetDateTime(""));
    	assertNull(ISO8601.parseZonedDateTime(null));
    }

	public void testOutOfRangeFieldsThrowParseException() throws Exception {
		// Syntactically valid but out of range: a ParseException, as declared, not an
		// unchecked java.time.DateTimeException
		assertThrows(ParseException.class, () -> ISO8601.parseLocalDate("2024-13-01"));
		assertThrows(ParseException.class, () -> ISO8601.parseLocalDate("2023-02-29"));
		assertThrows(ParseException.class, () -> ISO8601.parseLocalTime("25:00"));
		assertThrows(ParseException.class, () -> ISO8601.parseLocalDateTime("2024-01-01T10:61"));
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-02-30T10:00Z"));
		assertThrows(ParseException.class, () -> ISO8601.parseZonedDateTime("2024-01-32T10:00Z"));
		ParseException pe = assertThrows(ParseException.class, () -> ISO8601.parseLocalDate("2024-13-01"));
		assertTrue(pe.getCause() instanceof java.time.DateTimeException);
		// The parts parsers only check the syntax (JavaScript's Date handles 24:00 itself)
		assertEquals(24, ISO8601.parseTimeParts("24:00").getHour());
		assertEquals(LocalDate.of(2024,2,29), ISO8601.parseLocalDate("2024-02-29"));
	}

	public void testNonIsoOffsetsRejected() throws Exception {
		// ZoneOffset.of() accepts these, ISO 8601 doesn't
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15T10:30+5"));
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15T10:30+5:30"));
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15T10:30+053"));
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15T10:30+19:00"));
		// The ISO forms
		assertEquals(ZoneOffset.ofHours(5), ISO8601.parseOffsetDateTime("2024-01-15T10:30+05").getOffset());
		assertEquals(ZoneOffset.ofHoursMinutes(5,30), ISO8601.parseOffsetDateTime("2024-01-15T10:30+05:30").getOffset());
		assertEquals(ZoneOffset.ofHoursMinutes(-5,-30), ISO8601.parseOffsetDateTime("2024-01-15T10:30-0530").getOffset());
		assertEquals(ZoneOffset.ofHoursMinutesSeconds(1,2,3), ISO8601.parseOffsetDateTime("2024-01-15T10:30+01:02:03").getOffset());
		assertEquals(ZoneOffset.ofHoursMinutesSeconds(1,2,3), ISO8601.parseOffsetDateTime("2024-01-15T10:30+010203").getOffset());
	}

	public void testHugeExtendedYearFormatting() throws Exception {
		// Beyond 6 digits the year used to be written as garbage characters
		DateTimeParts p = new DateTimeParts(1234567, 1, 2, 0, 0, 0, 0, null);
		assertEquals("+1234567-01-02", p.toDateString());
		p = new DateTimeParts(-1234567, 1, 2, 0, 0, 0, 0, null);
		assertEquals("-1234567-01-02", p.toDateString());
		p = new DateTimeParts(Integer.MIN_VALUE, 1, 2, 0, 0, 0, 0, null);
		assertEquals("-2147483648-01-02", p.toDateString());
		assertEquals("+010000-01-02", new DateTimeParts(10000, 1, 2, 0, 0, 0, 0, null).toDateString());
	}

	public void testCalendarBeforeGregorianChange() throws Exception {
		// A GregorianCalendar uses the Julian calendar before 1582: its fields were used
		// as is, so 1000-01-01Z came out as 0999-12-27
		java.util.GregorianCalendar cal = new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(java.time.Instant.parse("1000-01-01T00:00:00Z").toEpochMilli());
		assertEquals("1000-01-01T00:00:00.000Z", ISO8601.formatISO8601(cal));
		cal.setTimeInMillis(java.time.Instant.parse("-0099-03-04T05:06:07.008Z").toEpochMilli());
		assertEquals("-000099-03-04T05:06:07.008Z", ISO8601.formatISO8601(cal));
		cal.setTimeInMillis(java.time.Instant.parse("2024-02-29T23:59:59.999Z").toEpochMilli());
		assertEquals("2024-02-29T23:59:59.999Z", ISO8601.formatISO8601(cal));
		// The calendar's own time zone gives the offset
		java.util.GregorianCalendar paris = new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("Europe/Paris"));
		paris.setTimeInMillis(java.time.Instant.parse("2024-07-01T10:00:00Z").toEpochMilli());
		assertEquals("2024-07-01T12:00:00.000+02:00", ISO8601.formatISO8601(paris));
	}

	public void testRfc3339Forms() throws Exception {
		// RFC 3339 allows a lowercase 't' and 'z', and ISO 8601 a comma as the decimal sign
		assertEquals(OffsetDateTime.parse("2024-01-15T10:30:15.250Z"), ISO8601.parseOffsetDateTime("2024-01-15t10:30:15.250z"));
		assertEquals(OffsetDateTime.parse("2024-01-15T10:30:15.250Z"), ISO8601.parseOffsetDateTime("2024-01-15T10:30:15,250Z"));
		assertEquals(LocalTime.parse("10:30:15.500"), ISO8601.parseLocalTime("10:30:15,5"));
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15x10:30:15Z"));
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15T10:30:15y"));
	}
}
