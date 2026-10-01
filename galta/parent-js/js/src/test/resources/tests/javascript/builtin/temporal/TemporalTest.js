// Temporal, ISO 8601 calendar

// Durations
const d = Temporal.Duration.from("P1Y2M3W4DT5H6M7.008009010S");
assertEquals("P1Y2M3W4DT5H6M7.00800901S", d.toString());
assertEquals(-1, d.negated().sign);
assertEquals("PT1H30M", Temporal.Duration.from({ minutes: 90 }).round({ largestUnit: "hour" }).toString());
assertEquals(1.5, Temporal.Duration.from({ minutes: 90 }).total("hour"));
assertEquals("P1M", Temporal.Duration.from({ days: 31 }).round({ largestUnit: "month", relativeTo: "2020-01-01" }).toString());
assertThrows(RangeError, () => new Temporal.Duration(1, -1));
assertThrows(RangeError, () => Temporal.Duration.from({ years: 1 }).add({ days: 1 }));

// Plain dates and times
const date = Temporal.PlainDate.from("2020-02-29");
assertEquals("2021-02-28", date.add({ years: 1 }).toString());
assertEquals(6, date.dayOfWeek);
assertEquals(60, date.dayOfYear);
assertEquals(9, date.weekOfYear);
assertEquals("P1Y1D", date.until("2021-03-01", { largestUnit: "year" }).toString());
assertEquals("M02", date.monthCode);
assertThrows(RangeError, () => Temporal.PlainDate.from({ year: 2021, month: 2, day: 29 }, { overflow: "reject" }));
assertEquals("2021-02-28", Temporal.PlainDate.from({ year: 2021, month: 2, day: 29 }).toString());
const time = new Temporal.PlainTime(23, 59, 59, 999);
assertEquals("00:00:00", time.add({ milliseconds: 1 }).toString());
assertEquals("23:59:59.999", time.toString());
assertEquals("12:35", Temporal.PlainTime.from("12:34:56").round({ smallestUnit: "minute" }).toString({ smallestUnit: "minute" }));
const dt = Temporal.PlainDateTime.from("2020-01-31T10:00");
assertEquals("2020-02-29T10:00:00", dt.add({ months: 1 }).toString());
assertEquals("2020-01", dt.toPlainDate().toPlainYearMonth().toString());
assertEquals("01-31", dt.toPlainDate().toPlainMonthDay().toString());
assertEquals(-1, Temporal.PlainDateTime.compare(dt, dt.add({ nanoseconds: 1 })));

// Instants and zoned date-times
const epoch = Temporal.Instant.fromEpochMilliseconds(0);
assertEquals("1970-01-01T00:00:00Z", epoch.toString());
assertEquals(0n, epoch.epochNanoseconds);
assertEquals("1970-01-01T01:00:00+01:00", epoch.toString({ timeZone: "+01:00" }));
const zdt = Temporal.ZonedDateTime.from("2020-03-08T01:30-08:00[America/Los_Angeles]");
assertEquals("2020-03-08T03:30:00-07:00[America/Los_Angeles]", zdt.add({ hours: 1 }).toString());
assertEquals(23, zdt.hoursInDay);
assertEquals("-08:00", zdt.offset);
assertEquals("2020-03-08T03:00:00-07:00[America/Los_Angeles]", zdt.getTimeZoneTransition("next").toString());
assertEquals("America/Los_Angeles", zdt.timeZoneId);
assertEquals("2020-03-08T10:30:00+01:00[Europe/Paris]", zdt.withTimeZone("Europe/Paris").toString());
assertEquals("UTC", Temporal.ZonedDateTime.from("2020-01-01T00:00[utc]").timeZoneId);

// Misuse
assertThrows(TypeError, () => Temporal.PlainDate.prototype.year);
assertThrows(TypeError, () => date < date);
assertThrows(TypeError, () => Temporal.PlainDate(2020, 1, 1));
assertEquals("[object Temporal.PlainDate]", Object.prototype.toString.call(date));
assertEquals("2020-02-29T00:00:00Z", new Date(Date.UTC(2020, 1, 29)).toTemporalInstant().toString());
