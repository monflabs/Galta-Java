const d1 = new Date(1993, 6, 25, 14, 39, 7);
const d2 = new Date(1993, 6, 26, 14, 39, 7);
const d3 = new Date(1993, 6, 27, 14, 39, 7);
const d4 = new Date(1993, 6, 28, 14, 39, 7);
const d5 = new Date(1993, 6, 29, 14, 39, 7);
const d6 = new Date(1993, 6, 30, 14, 39, 7);
const d7 = new Date(1993, 6, 31, 14, 39, 7);

assertEquals("Sun Jul 25 1993", d1.toDateString());
assertEquals("Mon Jul 26 1993", d2.toDateString());
assertEquals("Tue Jul 27 1993", d3.toDateString());
assertEquals("Wed Jul 28 1993", d4.toDateString());
assertEquals("Thu Jul 29 1993", d5.toDateString());
assertEquals("Fri Jul 30 1993", d6.toDateString());
assertEquals("Sat Jul 31 1993", d7.toDateString());
