const set3 = new Set([undefined,2,3]);

assertTrue(set3.has(undefined))
assertTrue(set3.has(2))
assertTrue(set3.has(3))

assertFalse(set3.has(4))

// has() with NaN (SameValueZero: NaN equals NaN)
const s2 = new Set([NaN]);
assertTrue(s2.has(NaN));
assertFalse(s2.has(0));

// has() with -0 and +0 (SameValueZero: -0 equals +0)
const s3 = new Set([-0]);
assertTrue(s3.has(-0));
assertTrue(s3.has(+0));
assertFalse(s3.has(1));

// has() with null and other falsy values
const s4 = new Set([null, 0, false, '']);
assertTrue(s4.has(null));
assertTrue(s4.has(0));
assertTrue(s4.has(false));
assertTrue(s4.has(''));
assertFalse(s4.has(undefined));
assertFalse(s4.has(NaN));
