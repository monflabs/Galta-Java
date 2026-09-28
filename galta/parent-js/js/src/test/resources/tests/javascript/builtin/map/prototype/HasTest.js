const map3 = new Map([
  [undefined, "one"],
  [2, "two"],
  [3, "three"],
]);

assertTrue(map3.has(undefined))
assertTrue(map3.has(2))
assertTrue(map3.has(3))

assertFalse(map3.has(4))

// has() with NaN key (SameValueZero: NaN equals NaN)
const m2 = new Map([[NaN, 'value']]);
assertTrue(m2.has(NaN));
assertFalse(m2.has(0));

// has() with -0 and +0 (SameValueZero: -0 equals +0)
const m3 = new Map([[-0, 'zero']]);
assertTrue(m3.has(-0));
assertTrue(m3.has(+0));

// has() returns false for non-existent key
assertFalse(new Map().has('missing'));
assertFalse(new Map().has(null));

// has() with null key
const m4 = new Map([[null, 42]]);
assertTrue(m4.has(null));
assertFalse(m4.has(undefined));
