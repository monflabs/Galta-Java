const d = new Date(Date.UTC(1993, 6, 25, 14, 39, 7));

assertEquals("Sun, 25 Jul 1993 14:39:07 GMT", d.toUTCString());
