const d = new Date(1993, 6, 25, 14, 39, 7);

assertEquals("Sun Jul 25 1993", d.toLocaleDateString());
assertEquals("So. Juli 25 1993", d.toLocaleDateString('de-DE'));
