// Temporal Dead Zone: a let/const binding exists (in the TDZ) from its
// container's entry, but any read/write/typeof before its own declaration
// statement runs must throw ReferenceError.

// 1. Own initializer.
assertThrows(ReferenceError, () => { let x = x; });
assertThrows(ReferenceError, () => { const c = c; });

// 2. typeof before declaration.
assertThrows(ReferenceError, () => { typeof x; let x; });

// 3. Write before declaration (plain assignment, compound assignment, inc/dec).
assertThrows(ReferenceError, () => { y = 5; let y; });
assertThrows(ReferenceError, () => { y += 1; let y; });
assertThrows(ReferenceError, () => { y++; let y; });
assertThrows(ReferenceError, () => { ++y; let y; });

// 4. Shadowing: an inner let/const not yet initialized must NOT fall through
// to an outer same-named binding.
{
	var outerFn = 'outer';
	assertThrows(ReferenceError, () => {
		(function(){ var r = outerFn; let outerFn = 'inner'; return r; })();
	});
}
{
	var outerBlock = 'outer';
	assertThrows(ReferenceError, () => {
		{ var r = outerBlock; let outerBlock = 'inner'; }
	});
}

// 5. Sanity: ordinary declared-then-used let/const still work, both at
// program scope and nested scopes.
{
	let a = 1;
	const b = 2;
	assertEquals(3, a + b);
}
(function() {
	let a = 10;
	const b = 20;
	assertEquals(30, a + b);
})();

// 6. `let x;` (no initializer) still clears TDZ to real `undefined` - a
// dedicated regression guard for the transpiled-mode codegen gap where a
// bare `let` with no initializer previously emitted nothing at all.
{
	let c;
	assertEquals(undefined, c);
	c = 5;
	assertEquals(5, c);
}

// 7. for-of/for-in head evaluation: the collection/object expression
// evaluates with the loop's own let/const bound name(s) already in TDZ, so a
// self-referencing collection must throw rather than resolving to an outer
// same-named binding.
{
	let hx = 1;
	assertThrows(ReferenceError, () => {
		for (let hx of [hx]) {}
	});
}
{
	let hy = { a: 1 };
	assertThrows(ReferenceError, () => {
		for (let hy in hy) {}
	});
}

// 8. Plain `for (let i=...)` has no TDZ issue - the loop's own init always
// runs before test/body/increment.
{
	let total = 0;
	for (let i = 0; i < 5; i++) {
		total += i;
	}
	assertEquals(10, total);
}

// 9. Straight-line reads AFTER the declaration (the case the transpiler's
// linear-flow analysis may drop the checkTDZ guard for). These must still
// produce the right value - both a same-block read and one nested inside a
// later block/loop/branch, which all run only after the earlier declaration.
(function() {
	const d = 3;
	assertEquals(3, d);           // same-statement-list, later statement
	let sum = 0;
	if (d > 0) { sum += d; }      // read inside a later branch
	for (let i = 0; i < d; i++) { sum += d; }  // read inside a later loop
	assertEquals(12, sum);        // 3 + 3*3
})();

// 10. Closure defined BEFORE the declaration but CALLED after: the guard must
// stay - the arrow body reads `e` which is still in TDZ when g() runs.
assertThrows(ReferenceError, () => {
	const g = () => e;
	g();
	const e = 1;
	return e;
});

// 11. Hoisted function declaration called before a textually-later const:
// the function runs first, while the const is still in TDZ - must throw even
// though the read is textually AFTER the declaration.
assertThrows(ReferenceError, () => {
	h();
	const f = 1;
	function h() { return f; }
	return f;
});

// 12. switch jump-in: a read in a later case is textually after the earlier
// case's const declaration, but entering case 2 skips case 1's initializer -
// must throw.
assertThrows(ReferenceError, () => {
	function s(x) {
		switch (x) {
			case 1: { const w = 1; return w; }
			case 2: return w;   // w declared in case 1, never run for x===2
		}
	}
	return s(2);
});

// 13. A read textually BEFORE the declaration in the same block still throws
// (the guard must not be dropped just because they share a statement list).
assertThrows(ReferenceError, () => {
	const r = q;   // q read before its own declaration
	const q = 1;
	return r;
});

// 14. C-style `for(let i = 0; ...)` loop variable: the init clause always
// runs to completion before test/increment/body, so reads of `i` in any of
// those are past the TDZ (the transpiler drops their checkTDZ guard). This
// must still compute the right values - including a body-created closure,
// which sees the per-iteration binding, never TDZ.
{
	let sum = 0;
	const seen = [];
	for (let i = 0; i < 4; i++) {   // test read + increment read of `i`
		sum += i;                    // body read of `i`
		seen.push(() => i);          // closure capturing `i` (per-iteration)
	}
	assertEquals(6, sum);            // 0+1+2+3
	assertEquals(0, seen[0]());      // each closure kept its own iteration's i
	assertEquals(3, seen[3]());
}

// 15. But a self/forward reference inside the for-loop's OWN init expression
// is still in the TDZ and must throw - the guard must NOT be dropped there.
assertThrows(ReferenceError, () => {
	for (let i = i; i < 1; i++) {}
});
