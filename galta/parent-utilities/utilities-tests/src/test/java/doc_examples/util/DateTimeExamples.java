package doc_examples.util;

import static org.junit.Assert.assertThrows;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.monflabs.util.datetime.DateTimeParts;
import org.monflabs.util.datetime.ISO8601;
import org.monflabs.util.datetime.PeriodFormatter;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/DateTime.md
 */
public class DateTimeExamples extends ProjectTestCase {

	public void testFormat() throws Exception {
		LocalDateTime ldt = LocalDateTime.of(2024, 1, 15, 10, 30, 5, 123_456_789);
		assertEquals("2024-01-15", ISO8601.formatISO8601(ldt.toLocalDate()));
		assertEquals("10:30:05.123", ISO8601.formatISO8601(ldt.toLocalTime()));          // milliseconds, truncated
		assertEquals("10:30:05", ISO8601.formatISO8601(ldt.toLocalTime(), false));
		assertEquals("2024-01-15T10:30:05.123", ISO8601.formatISO8601(ldt));             // no offset for local types
		assertEquals("2024-01-15T10:30:05.123+05:30", ISO8601.formatISO8601(ldt.atOffset(ZoneOffset.ofHoursMinutes(5, 30))));
		assertEquals("2024-01-15T10:30:05Z", ISO8601.formatISO8601(ldt.atOffset(ZoneOffset.UTC), false));
		// A zoned value is written with its offset; the region id is not kept
		assertEquals("2024-01-15T10:30:05.123+01:00", ISO8601.formatISO8601(ldt.atZone(ZoneId.of("Europe/Paris"))));
	}

	public void testExtendedYears() throws Exception {
		assertEquals("-000005-01-01", ISO8601.formatISO8601(LocalDate.of(-5, 1, 1)));
		assertEquals("+012345-06-30", ISO8601.formatISO8601(LocalDate.of(12345, 6, 30)));
		assertEquals(LocalDate.of(-5, 1, 1), ISO8601.parseLocalDate("-000005-01-01"));
	}

	public void testParse() throws Exception {
		OffsetDateTime odt = ISO8601.parseOffsetDateTime("2024-01-15T10:30:05.123456789+0530");
		assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 5, 123_000_000, ZoneOffset.ofHoursMinutes(5, 30)), odt);

		// Basic (compact) format, space separator, optional seconds
		assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 5), ISO8601.parseLocalDateTime("20240115T103005Z"));
		assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), ISO8601.parseLocalDateTime("2024-01-15 10:30"));

		// Reduced-precision dates
		assertEquals(LocalDate.of(2024, 3, 1), ISO8601.parseLocalDate("2024-03"));
		assertEquals(LocalTime.of(14, 36, 11), ISO8601.parseLocalTime("143611"));

		assertNull(ISO8601.parseLocalDate(""));   // null or empty input gives null
	}

	public void testParseOffsets() throws Exception {
		// No offset in the text: the supplied default is used, then UTC
		assertEquals(ZoneOffset.ofHours(2), ISO8601.parseOffsetDateTime("2024-01-15T10:30", ZoneOffset.ofHours(2)).getOffset());
		assertEquals(ZoneOffset.UTC, ISO8601.parseOffsetDateTime("2024-01-15T10:30").getOffset());
		// An offset in the text wins over the default
		assertEquals(ZoneOffset.ofHours(-1), ISO8601.parseOffsetDateTime("2024-01-15T10:30-01", ZoneOffset.ofHours(2)).getOffset());

		// A local result drops the offset, it does not convert
		assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), ISO8601.parseLocalDateTime("2024-01-15T10:30+05:00"));

		ZonedDateTime zdt = ISO8601.parseZonedDateTime("2024-01-15T10:30:00Z");
		assertEquals(ZoneOffset.UTC, zdt.getZone());
	}

	public void testParseErrors() throws Exception {
		assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15T10:30:05.1234567890Z")); // 10 digits
		assertThrows(ParseException.class, () -> ISO8601.parseLocalDateTime("2024-01-15"));     // time required
		assertThrows(ParseException.class, () -> ISO8601.parseLocalDateTime("2024-01-15T10:30:05+25:00"));
		assertThrows(ParseException.class, () -> ISO8601.parseLocalDate("2024-0115"));          // mixed separators
	}

	public void testDateTimeParts() throws Exception {
		DateTimeParts p = ISO8601.parseDateTimeParts("1999-12-17T14:36:11.5+02:00");
		assertEquals(1999, p.getYear());
		assertEquals(500, p.getMillis());
		assertEquals(ZoneOffset.ofHours(2), p.getOffset());
		assertEquals(945434171500L, p.toEpochMilli());

		p.setOffset(null);                               // no offset: conversions assume UTC
		assertEquals("1999-12-17T14:36:11.500Z", p.toDateTimeString(true, true));
		assertEquals("14:36:11", p.toTimeString(false, false));
	}

	public void testPeriodFormatter() throws Exception {
		long ms = PeriodFormatter.parsePeriod("1d 5h 30m");
		assertEquals((24 + 5) * 3_600_000L + 30 * 60_000L, ms);
		assertEquals("1D5h30m", PeriodFormatter.formatPeriod(ms));
		assertEquals("1D5h", PeriodFormatter.formatPeriod(ms, 'h'));  // precision: stop at hours

		assertEquals("2W", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("14d")));
		assertEquals("1s500", PeriodFormatter.formatPeriod(1500));     // milliseconds after seconds have no unit
		assertEquals(1500, PeriodFormatter.parsePeriod("1s500"));
		assertEquals(250, PeriodFormatter.parsePeriod("250ms"));
		assertEquals(-1, PeriodFormatter.parsePeriod(""));
	}
}
