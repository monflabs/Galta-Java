const f = function () {}
assertEquals( "f", f.name)

const g = function xy() {}
assertEquals( "xy", g.name)

let h;
h = function() {}
assertEquals( "h", h.name)


const a = class {}
assertEquals( "a", a.name)

const b = class xy {}
assertEquals( "xy", b.name)

let c;
c = class xy {}
assertEquals( "xy", c.name)

// Parentheses around the right-hand side are transparent for NamedEvaluation
// (only being part of a multi-expression comma sequence disqualifies it,
// per IsFunctionDefinition/HasName's static-semantics propagation).
const d = (function () {})
assertEquals( "d", d.name)

let e;
e = (function () {})
assertEquals( "e", e.name)

const notNamed = (0, function () {})
assertEquals( "", notNamed.name)

let i;
i ||= function () {}
assertEquals( "i", i.name)

let j;
j ??= function () {}
assertEquals( "j", j.name)

let k = true;
k &&= function () {}
assertEquals( "k", k.name)
