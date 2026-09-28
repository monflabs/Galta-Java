# Date & Time

`org.monflabs.util.datetime` reads and writes ISO 8601 text for the `java.time` types without going through `DateTimeFormatter`. It is the date layer of Galta's JSON support, so it accepts the forms found in JSON documents and in JavaScript's `Date` strings. `PeriodFormatter` handles short durations such as `1d 5h 30m`.

| Class | Role |
|---|---|
| `ISO8601` | Static `formatISO8601(...)` and `parseXxx(...)` methods |
| `DateTimeParts` | The broken-down value (year ... millisecond, optional offset) both directions go through |
| `PeriodFormatter` | Durations in milliseconds to and from `1D5h30m` |

## Formatting

`ISO8601.formatISO8601` has an overload for `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime` and `GregorianCalendar`. The time part always has seconds and, unless the `millis` argument is `false`, exactly three fraction digits: the value is truncated to milliseconds. The local types are written without an offset; the others end with their offset, `Z` for UTC. A `ZonedDateTime` keeps its offset at that instant, not its region.

Sample: `doc_examples/util/DateTimeExamples.java` (`testFormat`)

```java
LocalDateTime ldt = LocalDateTime.of(2024, 1, 15, 10, 30, 5, 123_456_789);
assertEquals("2024-01-15", ISO8601.formatISO8601(ldt.toLocalDate()));
assertEquals("10:30:05.123", ISO8601.formatISO8601(ldt.toLocalTime()));          // milliseconds, truncated
assertEquals("10:30:05", ISO8601.formatISO8601(ldt.toLocalTime(), false));
assertEquals("2024-01-15T10:30:05.123", ISO8601.formatISO8601(ldt));             // no offset for local types
assertEquals("2024-01-15T10:30:05.123+05:30", ISO8601.formatISO8601(ldt.atOffset(ZoneOffset.ofHoursMinutes(5, 30))));
assertEquals("2024-01-15T10:30:05Z", ISO8601.formatISO8601(ldt.atOffset(ZoneOffset.UTC), false));
// A zoned value is written with its offset; the region id is not kept
assertEquals("2024-01-15T10:30:05.123+01:00", ISO8601.formatISO8601(ldt.atZone(ZoneId.of("Europe/Paris"))));
```

Years from 0 to 9999 use four digits. Outside that range the year is written in the extended form, a sign and six digits, and the parser accepts it back:

Sample: `doc_examples/util/DateTimeExamples.java` (`testExtendedYears`)

```java
assertEquals("-000005-01-01", ISO8601.formatISO8601(LocalDate.of(-5, 1, 1)));
assertEquals("+012345-06-30", ISO8601.formatISO8601(LocalDate.of(12345, 6, 30)));
assertEquals(LocalDate.of(-5, 1, 1), ISO8601.parseLocalDate("-000005-01-01"));
```

## Parsing

| Method | Input | Result |
|---|---|---|
| `parseLocalDate(s)` | date | `LocalDate` |
| `parseLocalTime(s)` | time, optional offset | `LocalTime` (offset ignored) |
| `parseLocalDateTime(s)` | date and time, optional offset | `LocalDateTime` (offset ignored) |
| `parseOffsetDateTime(s[, defaultOffset])` | date and time, optional offset | `OffsetDateTime` |
| `parseZonedDateTime(s[, defaultOffset])` | date and time, optional offset | `ZonedDateTime` with a `ZoneOffset` zone |
| `parseDateParts`, `parseTimeParts`, `parseDateTimeParts` | as above | `DateTimeParts` |

All of them return `null` for a `null` or empty string and throw `java.text.ParseException` for anything they cannot read, including a field out of range such as month 13 or February 30 (the `parseXxxParts` methods only check the syntax, so those values reach `DateTimeParts` as is). The accepted syntax:

| Part | Accepted |
|---|---|
| Date | `yyyy-MM-dd` or `yyyyMMdd`; `yyyy` and `yyyy-MM` for a date alone (missing fields are 1); a year may be `+yyyyyy`/`-yyyyyy` (`-000000` is rejected) |
| Separator | `T` or a space |
| Time | `HH:mm`, `HH:mm:ss` or `HHmm`, `HHmmss`; seconds are optional |
| Fraction | `.` and 1 to 9 digits, truncated to milliseconds |
| Offset | `Z`, `+HH`, `+HH:mm`, `+HHmm`, `+HH:mm:ss`, `+HHmmss` (or `-`), within `ZoneOffset`'s range; a single-digit hour like `+5` is rejected |

The separator style must be consistent within the date (`2024-0115` fails) and within the time. A date-time needs a time part: `parseLocalDateTime("2024-01-15")` fails.

Sample: `doc_examples/util/DateTimeExamples.java` (`testParse`)

```java
OffsetDateTime odt = ISO8601.parseOffsetDateTime("2024-01-15T10:30:05.123456789+0530");
assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 5, 123_000_000, ZoneOffset.ofHoursMinutes(5, 30)), odt);

// Basic (compact) format, space separator, optional seconds
assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 5), ISO8601.parseLocalDateTime("20240115T103005Z"));
assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), ISO8601.parseLocalDateTime("2024-01-15 10:30"));

// Reduced-precision dates
assertEquals(LocalDate.of(2024, 3, 1), ISO8601.parseLocalDate("2024-03"));
assertEquals(LocalTime.of(14, 36, 11), ISO8601.parseLocalTime("143611"));

assertNull(ISO8601.parseLocalDate(""));   // null or empty input gives null
```

### Offsets

When the text has no offset, `parseOffsetDateTime` and `parseZonedDateTime` use the `defaultOffset` argument, and UTC when that is `null` or omitted. An offset present in the text always wins. The local variants discard the offset without converting the time.

Sample: `doc_examples/util/DateTimeExamples.java` (`testParseOffsets`)

```java
// No offset in the text: the supplied default is used, then UTC
assertEquals(ZoneOffset.ofHours(2), ISO8601.parseOffsetDateTime("2024-01-15T10:30", ZoneOffset.ofHours(2)).getOffset());
assertEquals(ZoneOffset.UTC, ISO8601.parseOffsetDateTime("2024-01-15T10:30").getOffset());
// An offset in the text wins over the default
assertEquals(ZoneOffset.ofHours(-1), ISO8601.parseOffsetDateTime("2024-01-15T10:30-01", ZoneOffset.ofHours(2)).getOffset());

// A local result drops the offset, it does not convert
assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), ISO8601.parseLocalDateTime("2024-01-15T10:30+05:00"));

ZonedDateTime zdt = ISO8601.parseZonedDateTime("2024-01-15T10:30:00Z");
assertEquals(ZoneOffset.UTC, zdt.getZone());
```

Sample: `doc_examples/util/DateTimeExamples.java` (`testParseErrors`)

```java
assertThrows(ParseException.class, () -> ISO8601.parseOffsetDateTime("2024-01-15T10:30:05.1234567890Z")); // 10 digits
assertThrows(ParseException.class, () -> ISO8601.parseLocalDateTime("2024-01-15"));     // time required
assertThrows(ParseException.class, () -> ISO8601.parseLocalDateTime("2024-01-15T10:30:05+25:00"));
assertThrows(ParseException.class, () -> ISO8601.parseLocalDate("2024-0115"));          // mixed separators
```

## DateTimeParts

`DateTimeParts` is a mutable holder for year, month, day, hour, minute, second, millisecond and a `ZoneOffset` (possibly `null`). It has a constructor for each `java.time` type and `GregorianCalendar` (BC years become negative proleptic years), getters and setters, and conversions: `toLocalDate()`, `toLocalTime()`, `toLocalDateTime()`, `toOffsetDateTime([default])`, `toZonedDateTime([default])`, `toInstant()`, `toEpochMilli()`. The conversions that need an offset use UTC when there is none. `toDateString()`, `toTimeString(millis, timezone)` and `toDateTimeString(millis, timezone)` write the parts as ISO text, with `Z` for a missing offset.

Sample: `doc_examples/util/DateTimeExamples.java` (`testDateTimeParts`)

```java
DateTimeParts p = ISO8601.parseDateTimeParts("1999-12-17T14:36:11.5+02:00");
assertEquals(1999, p.getYear());
assertEquals(500, p.getMillis());
assertEquals(ZoneOffset.ofHours(2), p.getOffset());
assertEquals(945434171500L, p.toEpochMilli());

p.setOffset(null);                               // no offset: conversions assume UTC
assertEquals("1999-12-17T14:36:11.500Z", p.toDateTimeString(true, true));
assertEquals("14:36:11", p.toTimeString(false, false));
```

## PeriodFormatter

`PeriodFormatter.parsePeriod(text)` adds up `<number><unit>` groups and returns milliseconds. Whitespace between groups is ignored, and a number without a unit counts as milliseconds. The unit must follow its number directly. It returns `-1` for invalid text: a null, empty or blank string, an unknown unit, a unit without a number (`5 h`), a sign (`-5s`), or a total that overflows a `long`.

| Unit | Meaning |
|---|---|
| `y`, `Y` | year of 365 days |
| `n`, `M` | month of 30 days |
| `w`, `W` | week |
| `d`, `D` | day |
| `h` | hour |
| `m` | minute |
| `s` | second |
| `ms`, or no unit | millisecond |

Units are case-sensitive where the table says so: `H` is not an hour, so `5H` is invalid (`-1`).

`formatPeriod(ms[, precision])` writes the largest unit that divides the whole number of days exactly (`Y`, then `M`, then `W`, then `D`), then `h`, `m`, `s`, and the remaining milliseconds: with an `ms` suffix when there are no seconds, as a bare number after `s` otherwise. Both forms parse back to the same value. The optional precision (`'D'`/`'d'`, `'h'`, `'m'`, `'s'`) stops after that unit. `0` formats as `"0"` and a negative period as `""`.

Sample: `doc_examples/util/DateTimeExamples.java` (`testPeriodFormatter`)

```java
long ms = PeriodFormatter.parsePeriod("1d 5h 30m");
assertEquals((24 + 5) * 3_600_000L + 30 * 60_000L, ms);
assertEquals("1D5h30m", PeriodFormatter.formatPeriod(ms));
assertEquals("1D5h", PeriodFormatter.formatPeriod(ms, 'h'));  // precision: stop at hours

assertEquals("2W", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("14d")));
assertEquals("1s500", PeriodFormatter.formatPeriod(1500));     // milliseconds after seconds have no unit
assertEquals(1500, PeriodFormatter.parsePeriod("1s500"));
assertEquals(250, PeriodFormatter.parsePeriod("250ms"));
assertEquals(-1, PeriodFormatter.parsePeriod(""));
```
