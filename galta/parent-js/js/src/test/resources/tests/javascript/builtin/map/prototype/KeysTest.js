const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);

const it = map3.keys();

assertEquals(1,it.next().value);
assertEquals(2,it.next().value);
assertEquals(3,it.next().value);
assertEquals(undefined,it.next().value);
