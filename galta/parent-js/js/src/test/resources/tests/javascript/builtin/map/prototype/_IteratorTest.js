const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);

const it = map3[Symbol.iterator]();

assertEquals([1,"one"],it.next().value);
assertEquals([2,"two"],it.next().value);
assertEquals([3,"three"],it.next().value);
assertEquals(undefined,it.next().value);

assertSame(Map.prototype[Symbol.iterator], Map.prototype.entries);
