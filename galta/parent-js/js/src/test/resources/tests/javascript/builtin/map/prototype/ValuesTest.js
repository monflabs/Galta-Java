const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);

const it = map3.values();

assertEquals("one",it.next().value);
assertEquals("two",it.next().value);
assertEquals("three",it.next().value);
assertEquals(undefined,it.next().value);
