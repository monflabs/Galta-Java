//
// Variable
let x;

x = null
x ??= 'here'
assertEquals('here', x);

x = ''
x ??= 'here again'
assertEquals('', x);

x = true
x ??= 'is it'
assertEquals(true, x);

x = false
x ??= 'was false'
assertEquals(false, x);


//
// Object member
const a = { duration: 50, title: '', tiltle3: null };

a.duration ??= 10;
assertEquals(50, a.duration);

a.title ??= 'title is empty.';
assertEquals('', a.title);

a.title2 ??= 'new title';
assertEquals('new title', a.title2);

a.title3 ??= 'another new title';
assertEquals('another new title', a.title3);

// Short-circuit: when the LHS is already non-null/undefined, the RHS must not be
// evaluated and no write (PutValue) is attempted at all.
{
	let rhsEvaluated = false;
	const o = { defined: 0 };
	o.defined ??= (rhsEvaluated = true, 2);
	assertEquals(false, rhsEvaluated);
	assertEquals(0, o.defined);

	// A non-writable but defined (non-null) property: since the write is never
	// attempted, this must not throw even though the property couldn't be assigned.
	const nonWritable = {};
	Object.defineProperty(nonWritable, "prop", { value: 0, writable: false, configurable: true });
	assertEquals(0, nonWritable.prop ??= 1);
	assertEquals(0, nonWritable.prop);
}

// NamedEvaluation
{
	let f = null;
	f ??= function() {};
	assertEquals("f", f.name);
}
