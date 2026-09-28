const O1 = {a: 11, b:22, c: 33};

// Simple destructuration
{
	const {a} = O1
	assertDeclared("a")
	assertDeclaredInScope("a")
	assertNotDeclared("b")
}
{
	const {a,b} = O1
	assertEquals( a, 11 )
	assertEquals( b, 22 )
	assertDeclaredInScope("a")
	assertDeclaredInScope("b")
}
{
	const {a,b,c} = O1
	assertEquals( a, 11 )
	assertEquals( b, 22 )
	assertEquals( c, 33 )
}
{
	const {a,b,c,d=44} = O1
	assertEquals( a, 11 )
	assertEquals( b, 22 )
	assertEquals( c, 33 )
	assertEquals( d, 44 )
}


// Spread operator
{
	const {a,...x} = O1
	assertEquals( a, 11 )
	assertEquals( x, {b:22, c:33} )
}
{
	const {a,b,...x} = O1
	assertEquals( a, 11 )
	assertEquals( b, 22 )
	assertEquals( x, {c:33} )
}
{
	const {a,b,c,...x} = O1
	assertEquals( a, 11 )
	assertEquals( b, 22 )
	assertEquals( c, 33 )
	assertEquals( x, {} )
}

// Aliases
{
	const {x=8, y: YY=9, a: aa,b,c: cc} = O1
	assertEquals( aa, 11 )
	assertEquals( b, 22 )
	assertEquals( cc, 33 )
	assertEquals( x, 8 )
	assertEquals( YY, 9 )
}

// Nested
const O2 = {a: 11, b:{ c:33, d: {e: 44} } };
{
	const {a,b} = O2
	assertEquals( a, 11 )
	assertEquals( b, { c:33, d: {e: 44} } )
}
{
	const {a,b:{c}} = O2
	assertEquals( a, 11 )
	assertEquals( c, 33 )
}
{
	const {a,b:{...c}} = O2
	assertEquals( a, 11 )
	assertEquals( c, { c:33, d: {e: 44} } )
}

// Calculated properties
const O3 = {a: 11, b:22, c: 33};
{
	const p1 = "a"
	const p2 = "b"
	const {[p1]: a, [p2]:b, c} = O3
	assertEquals( a, 11 )
	assertEquals( b, 22 )
	assertEquals( c, 33 )
}

// Computed property key whose value differs from the local binding name -
// the transpiler used to silently fall back to the binding name here,
// which the case above can't catch (p1==="a", the very name "a" is bound
// to, so a wrong fallback would coincidentally still "work").
{
	const p3 = "z"
	const {[p3]: renamed} = {z: 99}
	assertEquals( renamed, 99 )
}

// A computed key's expression must actually be evaluated (and its
// exception propagated), even against a source with no matching property.
{
	function thrower() { throw new Error("boom") }
	assertThrows(Error, () => { const {[thrower()]: x} = {} })
}

// Computed keys are evaluated in source order, interleaved with each
// property's own default-value evaluation (PropertyName before
// BindingElement, per property) - not batched up front.
{
	let log = []
	function keyFnA(){ log.push("a"); return "k1" }
	function dfltFnB(){ log.push("b"); return "v1" }
	function keyFnC(){ log.push("c"); return "k2" }
	const {[keyFnA()]: x = dfltFnB(), [keyFnC()]: y} = {k2: "existing"}
	assertEquals( log.join(","), "a,b,c" )
	assertEquals( x, "v1" )
	assertEquals( y, "existing" )
}

// A computed (non-string) key must still be excluded from a trailing rest.
{
	let log = []
	function k(){ log.push("k"); return 0 }
	const {[k()]: x, ...rest} = {0: 1, bar: 2}
	assertEquals( log.join(","), "k" )
	assertEquals( x, 1 )
	assertEquals( rest, {bar: 2} )
}

{
	const a = {};
	({ b: a.b } = {b: "cb"});
	assertEquals("cb", a.b);
}

// A destructuring assignment expression evaluates to the right-hand side value.
{
	let a, b, result, vals;
	vals = {a: 1, b: 2};
	result = ({a, b} = vals);
	assertEquals(1,a);
	assertEquals(2,b);
	assertEquals(vals, result);
}

// An anonymous function/class expression used as a destructuring default
// value infers its .name from the binding target (NamedEvaluation) - both
// shorthand and "key: target = default" forms.
{
	const { g = class {} } = {};
	assertEquals("g", g.name);

	const { a: h = function(){} } = {};
	assertEquals("h", h.name);

	const { b: i = (function(){}) } = {};
	assertEquals("i", i.name);
}

// The same NamedEvaluation inference applies to a catch clause's own
// destructuring parameter (a previously separate code path that wasn't
// walked by the generic init() tree pass at all). Interpreter-only: the
// transpiler doesn't support a destructuring catch parameter at all (see
// TryTest.js's own "#if TRANSPILER" block for the same convention -
// "TRANSPILER" is true on a genuine interpreted run, per its comment there).
// #if TRANSPILER
{
	let arrow, fn;
	try {
		throw [];
	} catch ([a = () => {}, f = function(){}]) {
		arrow = a;
		fn = f;
	}
	assertEquals("a", arrow.name);
	assertEquals("f", fn.name);
}
// #endif
