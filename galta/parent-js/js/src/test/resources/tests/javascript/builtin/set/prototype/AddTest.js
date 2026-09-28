const s = new Set();
assertEquals(0, s.size)

s.add(1)
assertEquals(1, s.size)
assertTrue(s.has(1))

s.add(2)
assertEquals(2, s.size)
assertTrue(s.has(2))

// add() returns the Set itself (for chaining)
assertSame(s, s.add(3));
assertSame(s, s.add(4).add(5));
assertEquals(5, s.size);

// Adding duplicate has no effect
const s2 = new Set([1, 2, 3]);
s2.add(2);
assertEquals(3, s2.size);

// NaN deduplication (SameValueZero: NaN === NaN for Set)
const s3 = new Set();
s3.add(NaN);
s3.add(NaN);
assertEquals(1, s3.size);
assertTrue(s3.has(NaN));

// -0 and +0 are the same element
const s4 = new Set();
s4.add(-0);
s4.add(+0);
assertEquals(1, s4.size);
