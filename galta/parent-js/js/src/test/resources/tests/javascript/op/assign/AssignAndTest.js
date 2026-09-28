//
// Variable
let x = 0;
let y = 1;

x &&= 0; // 0
assertEquals(0,x);

x &&= 1; // 0
assertEquals(0,x);

y &&= 1; // 1
assertEquals(1,y);

y &&= 0; // 0
assertEquals(0,y);


//
// Object member
const a = { a:1, b:0 };

a.a &&= 2;
assertEquals(2, a.a);

a.b &&= 2;
assertEquals(0, a.b);

// Short-circuit: when the LHS is already falsy, the RHS must not be evaluated
// and no write (PutValue) is attempted at all.
{
	let rhsEvaluated = false;
	const o = { falsy: 0 };
	o.falsy &&= (rhsEvaluated = true, 2);
	assertEquals(false, rhsEvaluated);
	assertEquals(0, o.falsy);

	// A non-extensible object with a missing property: since the write is never
	// attempted (falsy undefined short-circuits &&=), this must not throw.
	const nonExtensible = {};
	Object.preventExtensions(nonExtensible);
	nonExtensible.prop &&= 1;
	assertEquals(undefined, nonExtensible.prop);
}

// NamedEvaluation
{
	let f = 1;
	f &&= function() {};
	assertEquals("f", f.name);
}
