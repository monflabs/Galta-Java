assertEquals( [], Array.from(11) );
assertEquals( ['a','b','c'], Array.from("abc") );
assertEquals( [1,2,3], Array.from([1,2,3]) );

const Vector = Java.type("java.util.Vector")
const l = new Vector(); //new Java.type("java.util.Vector")()
l.push(1);
l.push(2);
l.$add(3);
assertEquals( [1,2,3], Array.from(l) );

const HashMap =Java.type("java.util.HashMap")
const m = new HashMap()
m.set(1,"A");
m.set(2,"B");
m.set(3,"C");
assertEquals( [ [1,"A"],[2,"B"],[3,"C"] ], Array.from(m) );

const int = Java.type("int")
const ai = new int[3];
ai[0] = 3
ai[1] = 4
ai[2] = 5
assertEquals( [3,4,5], Array.from(ai) );

assertEquals( [2, 4, 6], Array.from([1, 2, 3], x => x + x) );


// Array Like
assertEquals( [23, undefined, undefined], Array.from({0: 23, length:3}) );

// Set (iterable) → Array
const s = new Set([10, 20, 30]);
assertEquals([10, 20, 30], Array.from(s));

// Map (iterable) → Array of [key, value] pairs (JS Map)
const jsMap = new Map([['a', 1], ['b', 2]]);
const fromMap = Array.from(jsMap);
assertEquals(2, fromMap.length);
assertEquals(['a', 1], fromMap[0]);
assertEquals(['b', 2], fromMap[1]);

// mapFn with thisArg
const ctx = { mult: 3 };
const result = Array.from([1, 2, 3], function(x) { return x * this.mult; }, ctx);
assertEquals([3, 6, 9], result);

// Generator function
function* gen() { yield 1; yield 2; yield 3; }
assertEquals([1, 2, 3], Array.from(gen()));

// Empty iterable
assertEquals([], Array.from(new Set()));
assertEquals([], Array.from(""));
