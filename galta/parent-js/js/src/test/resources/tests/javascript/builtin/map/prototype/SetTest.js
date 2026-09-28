const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);

assertEquals(undefined,map3.get(undefined));
assertEquals(undefined,map3.get(null));
assertEquals(undefined,map3.get(0));
assertEquals(undefined,map3.get("abc"));
assertEquals(undefined,map3.get(true));

map3.set(undefined,100)
map3.set(null,101)
map3.set(0,102)
map3.set("abc",103);
map3.set(true,104);

assertEquals(100,map3.get(undefined));
assertEquals(101,map3.get(null));
assertEquals(102,map3.get(0));
assertEquals(103,map3.get("abc"));
assertEquals(104,map3.get(true));

// set() returns the Map itself (for chaining)
const m = new Map();
assertSame(m, m.set('a', 1));
assertSame(m, m.set('b', 2).set('c', 3));
assertEquals(3, m.size);

// Overwriting an existing key
const m2 = new Map([['x', 1]]);
m2.set('x', 99);
assertEquals(99, m2.get('x'));
assertEquals(1, m2.size);

// NaN as key (SameValueZero: NaN === NaN for Map keys)
const m3 = new Map();
m3.set(NaN, 'nan');
assertEquals('nan', m3.get(NaN));

// -0 and +0 are the same key
const m4 = new Map();
m4.set(-0, 'zero');
assertEquals('zero', m4.get(+0));
assertEquals('zero', m4.get(-0));
assertEquals(1, m4.size);
