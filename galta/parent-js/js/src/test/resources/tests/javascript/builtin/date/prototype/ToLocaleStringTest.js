const d = new Date(1993, 6, 25, 14, 39, 7);

assertEquals("Sun Jul 25 1993 14:39:07 GMT-04:00", d.toLocaleString());
assertEquals("So. Juli 25 1993 14:39:07 GMT-04:00", d.toLocaleString('de-DE'));
