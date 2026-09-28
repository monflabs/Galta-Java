const target = { a: 1, b: 2 };
const source = { b: 4, c: 5 };
const returnedTarget = Object.assign(target, source);
assertEquals( { a: 1, b: 4, c: 5 }, target);
assertEquals( { a: 1, b: 4, c: 5 }, returnedTarget);


assertEquals( {a:1, b:2, c:3}, Object.assign({}, {a:1, b:2, c:3}) );
assertEquals( {a:1, b:2, c:3}, Object.assign({a:1}, {b:2, c:3}) );
assertEquals( {a:1, b:2, c:3}, Object.assign({a:1, b:2}, {c:3}) );
assertEquals( {a:1, b:2, c:3}, Object.assign({a:1}, null, {b:2, c:3}) );

try {
	assertEquals( {a:1, b:2, c:3}, Object.assign(null, {a:1, b:2, c:3}) );
	fail();
} catch(e){}

// Symbol keys are copied
const sym = Symbol('key');
const src = { [sym]: 42, str: "hello" };
const dst = Object.assign({}, src);
assertEquals(42, dst[sym]);
assertEquals("hello", dst.str);

// Multiple sources, later values override earlier
const merged = Object.assign({}, {a:1}, {a:2, b:3}, {c:4});
assertEquals(2, merged.a);
assertEquals(3, merged.b);
assertEquals(4, merged.c);

// Returns target object
const t = {};
assertSame(t, Object.assign(t, {x: 1}));

// String source: own enumerable indexed properties
const fromString = Object.assign({}, "ab");
assertEquals("a", fromString[0]);
assertEquals("b", fromString[1]);
