const map3 = new Map([
  [undefined, "one"],
  [2, "two"],
  [3, "three"],
]);

assertEquals("one", map3.get(undefined))
assertEquals("two", map3.get(2))
assertEquals("three", map3.get(3))

assertEquals(undefined, map3.get(4))
