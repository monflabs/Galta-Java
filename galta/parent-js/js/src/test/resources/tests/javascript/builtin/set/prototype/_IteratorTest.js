const set3 = new Set([1,2,3]);

const it = set3[Symbol.iterator]();

assertEquals(1,it.next().value);
assertEquals(2,it.next().value);
assertEquals(3,it.next().value);
assertEquals(undefined,it.next().value);

assertSame(Set.prototype[Symbol.iterator], Set.prototype.values);
