var f1 = () => { return 1 };
var f2 = () => 2
var f3 = (p) => {
	return p;
}
var f4 = p => p

var call = (f) => {
	return f();
}
var callb = (f) => f();
var f5 = call( () => {
	return 5;
}) 
var f5b = callb( () => {
	return 5;
}) 

var f6 = call( () => 6 )

var call2 = (f,p) => {
	return f(p);
}
var call2b = (f,p) => f(p);
var f7 = call2( (p) => 8+p, 4 )
var f7b = call2b( (p) => 8+p, 4 )

assertEquals(1, f1())
assertEquals(2, f2())
assertEquals(3, f3(3))
assertEquals(4, f4(4))
assertEquals(5, f5)
assertEquals(5, f5b)
assertEquals(6, f6)
assertEquals(12, f7)
assertEquals(12, f7b)


//
// Arrow functions and this
globalThis.vv = 1;
const f = () => {
  return this.vv
}
const o = { vv: 2, f }

assertEquals(1, f() )
assertEquals(1, f.call(o) )
assertEquals(1, o.f() )

// Arrow functions and "arguments" (an arrow inherits the lexically enclosing arguments
// object rather than having its own) is covered by
// tests.javascript.functions.ArrowArgumentsTest (interpreted-mode only -- the
// transpiler gives every function, arrow or not, its own arguments object).

// An anonymous arrow's "name" is "" (not null/undefined) when it isn't assigned
// to a variable/property that would infer one.
assertEquals("", (p => p).name);
assertEquals("", (() => {}).name);

// NamedEvaluation: an anonymous arrow assigned to a plain identifier (var/let/const,
// or a later plain "=" assignment) infers its .name from that identifier.
var namedArrow1 = p => p;
assertEquals("namedArrow1", namedArrow1.name);
let namedArrow2;
namedArrow2 = () => {};
assertEquals("namedArrow2", namedArrow2.name);
