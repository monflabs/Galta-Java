const heroes = [
  { name: 'Batman' },
  { name: 'Joker' }
];
const a=[]
for (const { name } of heroes) {
  a.push(name)
}
assertEquals(['Batman', 'Joker'], a)
assertNotEquals(['Batman2', 'Joker2'], a)

// A bare destructuring pattern (no var/let/const) as the for-of loop
// variable is an assignment target, not a declaration - it must assign
// into already-existing bindings, same as `[x, y] = value`.
var x, y, z;
var pairs = [];
for ([x, y] of [[1, 2], [3, 4]]) {
	pairs.push(x + y);
}
assertEquals([3, 7], pairs);

for ({z} of [{z: 5}, {z: 6}]) {
}
assertEquals(6, z);
 