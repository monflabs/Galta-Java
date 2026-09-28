// Call arguments are evaluated before the check is made to see if the callee is
// actually callable - per spec, ArgumentListEvaluation happens before the
// IsCallable check.
{
	let fooCalled = false;
	function foo() { fooCalled = true; }
	const o = {};
	assertThrows(TypeError, () => o.bar(foo()));
	assertEquals(true, fooCalled);
}

{
	let fooCalled = false;
	function foo() { fooCalled = true; }
	assertThrows(TypeError, () => (undefined)(foo()));
	assertEquals(true, fooCalled);
}

// Optional call short-circuit still applies: no argument evaluation happens
// when the base itself is null/undefined.
{
	let fooCalled = false;
	function foo() { fooCalled = true; }
	const o = null;
	assertEquals(undefined, o?.bar(foo()));
	assertEquals(false, fooCalled);
}
