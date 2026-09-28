const set3 = new Set([1,2,3]);

const it = set3.entries();

assertEquals([1,1],it.next().value);
assertEquals([2,2],it.next().value);
assertEquals([3,3],it.next().value);
assertEquals(undefined,it.next().value);
