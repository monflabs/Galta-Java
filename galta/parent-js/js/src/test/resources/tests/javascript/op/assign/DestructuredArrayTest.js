const a = [11,22,33]

const [a1] = a
assertEquals( a1, 11 )

const [a2,b2] = a
assertEquals( a2, 11 )
assertEquals( b2, 22 )

const [a3,b3,c3] = a
assertEquals( a3, 11 )
assertEquals( b3, 22 )
assertEquals( c3, 33 )

const [a4,b4,c4,d4] = a
assertEquals( a4, 11 )
assertEquals( b4, 22 )
assertEquals( c4, 33 )
assertEquals( d4, null )

const [a5,b5,c5,d5=44] = a
assertEquals( a5, 11 )
assertEquals( b5, 22 )
assertEquals( c5, 33 )
assertEquals( d5, 44 )

const [a6,,c6] = a
assertEquals( a6, 11 )
assertEquals( c6, 33 )

const [...a7] = a
assertEquals( a7, [11,22,33] )

const [a8,...b8] = a
assertEquals( a8, 11 )
assertEquals( b8, [22,33] )

const [a9,b9,c9,...d9] = a
assertEquals( a9, 11 )
assertEquals( b9, 22 )
assertEquals( c9, 33 )
assertEquals( d9, [] )


const b = [1,[2,[3,4]],5]

const [e1,e2,e3] = b
assertEquals( e1, 1 )
assertEquals( e2, [2,[3,4]] )
assertEquals( e3, 5 )

const [f1,[f2,f3],f4] = b
assertEquals( f1, 1 )
assertEquals( f2, 2 )
assertEquals( f3, [3,4] )
assertEquals( f4, 5 )

const [g1,[g2,[g3,g4]],g5] = b
assertEquals( g1, 1 )
assertEquals( g2, 2 )
assertEquals( g3, 3 )
assertEquals( g4, 4 )
assertEquals( g5, 5 )

const [h1,[h2,[,h4]],h5,h6=8] = b
assertEquals( h1, 1 )
assertEquals( h2, 2 )
assertEquals( h4, 4 )
assertEquals( h5, 5 )
assertEquals( h6, 8 )

const [i1,[i2, ...i3], ...i4] = b
assertEquals( i1, 1 )
assertEquals( i2, 2 )
assertEquals( i3, [[3,4]] )
assertEquals( i4, [5] )


// Variable swap
{
  let a=1, b=2;
  assertEquals(a,1);
  assertEquals(b,2);
  [a,b] = [b,a]
  assertEquals(a,2);
  assertEquals(b,1);
  
  let c=1, d=2, e=3,f=4;
  [c,d] = [e,f] = [d,c];
  assertEquals(c,2);
  assertEquals(d,1);
  assertEquals(e,2);
  assertEquals(f,1);
}

// Default values
{
	let a, b;
	[a=5, b=7] = [1];
  	assertEquals(1,a);
  	assertEquals(7,b);
}

// A destructuring assignment expression evaluates to the right-hand side value.
{
	let a, b, result;
	result = [a, b] = [1, 2];
	assertEquals(1,a);
	assertEquals(2,b);
	assertEquals([1,2], result);
}

// An anonymous function/class expression used as a destructuring default
// value infers its .name from the binding target (NamedEvaluation) - both
// in a declaration and in a plain assignment expression, and whether the
// default expression is parenthesized or not (parens are transparent for
// this check per spec, unlike a multi-expression comma sequence which is not).
{
	const [f = function () {}] = [];
	assertEquals("f", f.name);

	const [g = (function () {})] = [];
	assertEquals("g", g.name);

	const [h = (0, function () {})] = [];
	assertEquals("", h.name);

	const [i = class {}] = [];
	assertEquals("i", i.name);

	let j;
	[j = function () {}] = [];
	assertEquals("j", j.name);

	// A default with an already-named function must not be overridden.
	const [k = function named() {}] = [];
	assertEquals("named", k.name);

	// A default that isn't actually used (value present) must not run at all.
	const [l = function () {}] = [42];
	assertEquals(42, l);
}

// An empty array pattern in an ASSIGNMENT context (no declaration keyword)
// must still call GetIterator+IteratorClose even though it binds nothing -
// unlike an empty BINDING pattern (declaration/function-parameter/catch
// parameter), which is a true no-op that never touches the iterator.
{
	function makeIterable() {
		let calls = 0;
		const iterable = {
			[Symbol.iterator]() {
				calls++;
				return { next: () => ({ done: true }) };
			}
		};
		return { iterable, getCalls: () => calls };
	}

	// The ASSIGNMENT-context cases (expect the iterator to be touched) are
	// interpreter-only: the transpiler generates destructuring-assignment
	// code via a completely separate mechanism (ASTArrayLiteral.
	// transpileJavaAssignment()'s VarAccessor.destruct() loop over
	// fieldInitializers), which - unlike the interpreter's assign() - has no
	// isAssignmentContext concept at all; for an empty pattern the generated
	// loop is simply zero iterations, a silent no-op regardless of context.
	// Correct for the BINDING-context cases below (still a no-op either way),
	// but not yet fixed for assignment context - a separate, still-open gap
	// (see KnownGaps.md), not covered by test262 (which only exercises the
	// interpreter).
	// #if TRANSPILER
	{
		const { iterable, getCalls } = makeIterable();
		[] = iterable;
		assertEquals(1, getCalls());
	}
	{
		const { iterable, getCalls } = makeIterable();
		for ([] of [iterable]) {}
		assertEquals(1, getCalls());
	}
	{
		const { iterable, getCalls } = makeIterable();
		({ a: [] } = { a: iterable });
		assertEquals(1, getCalls());
	}
	// #endif

	{
		const { iterable, getCalls } = makeIterable();
		const [] = iterable;
		assertEquals(0, getCalls());
	}
	{
		const { iterable, getCalls } = makeIterable();
		(function([]) {})(iterable);
		assertEquals(0, getCalls());
	}
	// Destructuring catch parameters aren't supported by the transpiler at
	// all (see TryTest.js's own "#if TRANSPILER" convention) - interpreter-only.
	// #if TRANSPILER
	{
		const { iterable, getCalls } = makeIterable();
		try { throw iterable; } catch([]) {}
		assertEquals(0, getCalls());
	}
	// #endif
	{
		const { iterable, getCalls } = makeIterable();
		for (let [] of [iterable]) {}
		assertEquals(0, getCalls());
	}
	// The assignment-vs-binding context propagates through nesting: an empty
	// array pattern nested inside an object pattern still reflects whichever
	// context the OUTER pattern is in (binding-context case only here - see
	// the transpiler-gated assignment-context case above).
	{
		const { iterable, getCalls } = makeIterable();
		const { a: [] } = { a: iterable };
		assertEquals(0, getCalls());
	}
}
