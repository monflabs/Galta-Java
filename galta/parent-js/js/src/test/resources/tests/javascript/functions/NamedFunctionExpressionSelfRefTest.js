// A named function expression's own name is an immutable binding inside its
// body (spec: enforced at assignment time, not a parse-time SyntaxError like
// const - sloppy mode silently no-ops the write, strict mode throws
// TypeError). A DECLARATION's own name binding stays ordinarily mutable, and
// a same-named PARAMETER shadows the immutable binding entirely. The
// transpiler doesn't implement the immutability itself yet (KnownGaps.md) -
// only the interpreter-only assertions below (guarded with "#if TRANSPILER",
// which - despite the name - means interpreter-only; see DestructuredArrayTest.js
// for the same established convention) exercise that.

{
	var outerSelfRef = "outer";
	var selfRefFn = function outerSelfRef() {
		var before = outerSelfRef;
		// #if TRANSPILER
		outerSelfRef = 1;
		assertEquals("function", typeof outerSelfRef);
		// #endif
		return before;
	};
	assertEquals("function", typeof selfRefFn());
	assertEquals("outer", outerSelfRef);
}

// #if TRANSPILER
{
	var strictSelfRefFn = function strictSelfRef() {
		"use strict";
		strictSelfRef = 1;
	};
	assertThrows(TypeError, () => strictSelfRefFn());
}
// The assignment EXPRESSION itself still evaluates to the assigned value,
// even though the store into the immutable binding is suppressed.
{
	var exprValueFn = function exprSelfRef() {
		return (exprSelfRef = 42);
	};
	assertEquals(42, exprValueFn());
}
// ++ / -- on the self-binding: sloppy no-op on the store, still evaluates.
{
	var incDecFn = function incDecSelf() {
		var before = incDecSelf;
		incDecSelf++;
		return [typeof before, typeof incDecSelf];
	};
	assertEquals(["function", "function"], incDecFn());
}
// #endif

// A same-named parameter shadows the expression's own immutable self-binding
// entirely - an ordinary, mutable parameter binding (works in both modes).
{
	var shadowedFn = function shadowSelf(shadowSelf) {
		shadowSelf = 99;
		return shadowSelf;
	};
	assertEquals(99, shadowedFn("param"));
}

// A function DECLARATION's own name (not an expression) stays ordinarily
// mutable - only named EXPRESSIONS get the immutable self-binding.
{
	function declSelfRef() {
		declSelfRef = "reassigned";
		return declSelfRef;
	}
	assertEquals("reassigned", declSelfRef());
}

// Named generator expression self-reference is immutable too.
{
	var genSelfRefFn = function* genSelfRef() {
		// #if TRANSPILER
		genSelfRef = 1;
		// #endif
		yield genSelfRef;
	};
	assertEquals("function", typeof genSelfRefFn().next().value);
}
